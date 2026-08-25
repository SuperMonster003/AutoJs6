package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCapabilities
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8InputBundleCodec
import org.autojs.plugin.r8compiler.api.R8InputBundleSummary
import org.autojs.plugin.r8compiler.api.R8InputIdentity
import org.autojs.plugin.r8compiler.api.R8InputRole
import org.autojs.plugin.r8compiler.api.R8InputSource
import org.autojs.plugin.r8compiler.api.R8Sha256
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal data class R8CompilerInputSnapshot(
    val inputBundleFile: File,
    val summary: R8InputBundleSummary,
)

/** Materializes caller sources before any descriptor is opened or remote method is invoked. */
internal object R8CompilerInputSnapshotter {
    fun snapshot(
        inputs: R8CompilerHostInputs,
        capabilities: R8CompilerCapabilities,
        workspace: R8CompilerTransactionWorkspace,
    ): R8CompilerInputSnapshot {
        R8CompilerValidation.validateCapabilities(capabilities)
        val snapshots = mutableListOf<SnapshotSource>()
        snapshots += snapshotOne(
            inputs.program,
            R8InputRole.PROGRAM_JAR,
            ordinal = 0,
            ownerClasspathOrdinal = -1,
            ownerClasspathSha256 = R8Sha256.ZERO,
            maximumBytes = capabilities.limits.maxProgramBytes,
            workspace = workspace,
            index = snapshots.size,
        )
        val classpath = inputs.classpath.mapIndexed { ordinal, source ->
            snapshotOne(
                source,
                R8InputRole.CLASSPATH_JAR,
                ordinal,
                -1,
                R8Sha256.ZERO,
                capabilities.limits.maxClasspathJarBytes,
                workspace,
                snapshots.size,
            ).also(snapshots::add)
        }
        inputs.keepRules.forEachIndexed { ordinal, source ->
            snapshots += snapshotOne(
                source,
                R8InputRole.KEEP_RULES,
                ordinal,
                -1,
                R8Sha256.ZERO,
                capabilities.limits.maxRuleFileBytes,
                workspace,
                snapshots.size,
            )
        }
        inputs.consumerRules.forEachIndexed { ordinal, source ->
            val owner = classpath[source.ownerClasspathOrdinal].identity
            snapshots += snapshotOne(
                source.source,
                R8InputRole.CONSUMER_RULES,
                ordinal,
                source.ownerClasspathOrdinal,
                owner.contentSha256,
                capabilities.limits.maxRuleFileBytes,
                workspace,
                snapshots.size,
            )
        }

        val ruleBytes = snapshots.asSequence()
            .filter { it.identity.role == R8InputRole.KEEP_RULES || it.identity.role == R8InputRole.CONSUMER_RULES }
            .map { it.identity.role to it.file.readBytes() }
            .toList()
        R8CompilerValidation.validateExtractedRules(ruleBytes, capabilities)

        val sources = snapshots.map { snapshot ->
            R8InputSource(snapshot.identity) { FileInputStream(snapshot.file) }
        }
        val bundle = workspace.inputBundleFile
        val summary = FileOutputStream(bundle, false).use { output ->
            R8InputBundleCodec.write(output, sources, capabilities).also { output.fd.sync() }
        }
        return R8CompilerInputSnapshot(bundle, summary)
    }

    private fun snapshotOne(
        source: R8CompilerHostSource,
        role: R8InputRole,
        ordinal: Int,
        ownerClasspathOrdinal: Int,
        ownerClasspathSha256: R8Sha256,
        maximumBytes: Long,
        workspace: R8CompilerTransactionWorkspace,
        index: Int,
    ): SnapshotSource {
        require(maximumBytes > 0L)
        val destination = workspace.sourceFile(index)
        val digest = MessageDigest.getInstance("SHA-256")
        val size = source.openInputStream().use { input ->
            FileOutputStream(destination, false).use { output ->
                copyBounded(input, output, maximumBytes, digest).also { output.fd.sync() }
            }
        }
        require(size > 0L) { "R8 input source must not be empty" }
        return SnapshotSource(
            R8InputIdentity(
                role,
                ordinal,
                ownerClasspathOrdinal,
                size,
                R8Sha256.fromBytes(digest.digest()),
                ownerClasspathSha256,
            ),
            destination,
        )
    }

    private fun copyBounded(
        input: InputStream,
        output: FileOutputStream,
        maximumBytes: Long,
        digest: MessageDigest,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val remaining = maximumBytes - total
            val requested = minOf(buffer.size.toLong(), remaining + 1L).toInt()
            val read = input.read(buffer, 0, requested)
            if (read < 0) return total
            if (read == 0) continue
            if (read.toLong() > remaining) throw IllegalArgumentException("R8 input source exceeds its limit")
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            total = Math.addExact(total, read.toLong())
        }
    }

    private data class SnapshotSource(
        val identity: R8InputIdentity,
        val file: File,
    )
}
