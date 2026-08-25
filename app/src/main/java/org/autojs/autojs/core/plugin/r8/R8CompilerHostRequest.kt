package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8ArtifactRole
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import java.io.InputStream
import java.util.Collections

/** Re-openable host input. The transaction runner snapshots every source before dispatch. */
internal class R8CompilerHostSource(
    val openInputStream: () -> InputStream,
)

internal data class R8CompilerConsumerRuleSource(
    val ownerClasspathOrdinal: Int,
    val source: R8CompilerHostSource,
) {
    init {
        require(ownerClasspathOrdinal >= 0)
    }
}

/** Canonical source groups; ordinals are assigned only by this host-side representation. */
internal class R8CompilerHostInputs(
    val program: R8CompilerHostSource,
    classpath: Collection<R8CompilerHostSource> = emptyList(),
    keepRules: Collection<R8CompilerHostSource>,
    consumerRules: Collection<R8CompilerConsumerRuleSource> = emptyList(),
) {
    val classpath: List<R8CompilerHostSource> = immutableBoundedCopy(
        classpath,
        R8CompilerContract.MAX_CLASSPATH_JARS,
        "R8 classpath",
    )
    val keepRules: List<R8CompilerHostSource> = immutableBoundedCopy(
        keepRules,
        R8CompilerContract.MAX_KEEP_RULE_FILES,
        "R8 keep rules",
    )
    val consumerRules: List<R8CompilerConsumerRuleSource> = immutableBoundedCopy(
        consumerRules,
        R8CompilerContract.MAX_CONSUMER_RULE_FILES,
        "R8 consumer rules",
    )

    init {
        require(this.keepRules.isNotEmpty()) { "At least one explicit R8 keep-rule source is required" }
        require(this.consumerRules.all { it.ownerClasspathOrdinal in this.classpath.indices }) {
            "R8 consumer-rule owner is outside the canonical classpath"
        }
    }
}

/** Explicit R8-only request policy. No field can express a D8/dx fallback. */
internal class R8CompilerHostCompileSpec(
    val inputs: R8CompilerHostInputs,
    val minApi: Int,
    val timeoutMillis: Long? = null,
    val terminalGraceMillis: Long = DEFAULT_TERMINAL_GRACE_MILLIS,
    requestedArtifactMaximums: Map<R8ArtifactRole, Long> = emptyMap(),
    val maximumOutputBundleBytes: Long? = null,
    val diagnosticByteLimit: Int? = null,
) {
    val requestedArtifactMaximums: Map<R8ArtifactRole, Long> = immutableArtifactMaximums(
        requestedArtifactMaximums,
    )

    init {
        require(minApi in R8CompilerContract.MIN_SUPPORTED_API..R8CompilerContract.MAX_SUPPORTED_API)
        timeoutMillis?.let { require(it in 1..R8CompilerContract.MAX_TIMEOUT_MILLIS) }
        require(terminalGraceMillis in 0..MAX_TERMINAL_GRACE_MILLIS)
        maximumOutputBundleBytes?.let {
            require(it in R8CompilerContract.MIN_OUTPUT_BUNDLE_BYTES..R8CompilerContract.MAX_OUTPUT_BUNDLE_BYTES)
        }
        diagnosticByteLimit?.let { require(it in 1..R8CompilerContract.MAX_DIAGNOSTIC_BYTES) }
    }

    companion object {
        const val DEFAULT_TERMINAL_GRACE_MILLIS = 5_000L
        const val MAX_TERMINAL_GRACE_MILLIS = 30_000L
    }
}

private fun <T> immutableBoundedCopy(values: Collection<T>, maximum: Int, label: String): List<T> {
    val copy = ArrayList<T>(minOf(maximum, 16))
    val iterator = values.iterator()
    while (iterator.hasNext()) {
        require(copy.size < maximum) { "$label count exceeds $maximum" }
        copy += iterator.next()
    }
    return Collections.unmodifiableList(copy)
}

private fun immutableArtifactMaximums(values: Map<R8ArtifactRole, Long>): Map<R8ArtifactRole, Long> {
    val copy = linkedMapOf<R8ArtifactRole, Long>()
    val iterator = values.entries.iterator()
    while (iterator.hasNext()) {
        require(copy.size < R8ArtifactRole.values().size) { "R8 artifact limit map is oversized" }
        val entry = iterator.next()
        require(entry.value > 0L) { "R8 artifact limit must be positive" }
        require(copy.put(entry.key, entry.value) == null) { "R8 artifact limit is duplicated" }
    }
    return Collections.unmodifiableMap(copy)
}
