package org.autojs.plugin.apkbuilder.template.impl

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal object RemoteZipExtractor {

    private const val BUFFER_SIZE = 256 * 1_024
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")

    fun extract(
        archiveFile: File,
        targetDir: File,
        budget: RemoteZipBudget,
        isCancelled: () -> Boolean = { false },
        cleanupTargetOnFailure: Boolean = true,
        entryValidator: (normalizedName: String, isDirectory: Boolean) -> Unit = { _, _ -> },
    ) {
        if (!archiveFile.isFile) {
            throw IOException("Remote build ${budget.label} does not exist: ${archiveFile.path}")
        }
        if (archiveFile.length() > budget.maxArchiveBytes) {
            throw IOException(
                "Remote build ${budget.label} exceeds the ${budget.maxArchiveBytes}-byte archive limit."
            )
        }

        val targetExisted = targetDir.exists()
        try {
            ZipFile(archiveFile).use { zip ->
                val plans = validateEntries(zip, targetDir, budget, isCancelled, entryValidator)
                ensureActive(isCancelled)
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    throw IOException("Failed to create ${budget.label} target dir: ${targetDir.path}")
                }
                extractEntries(zip, plans, budget, isCancelled)
            }
        } catch (error: Throwable) {
            if (cleanupTargetOnFailure && !targetExisted) {
                deleteRecursivelyBestEffort(targetDir)
            }
            throw error
        }
    }

    private fun validateEntries(
        zip: ZipFile,
        targetDir: File,
        budget: RemoteZipBudget,
        isCancelled: () -> Boolean,
        entryValidator: (String, Boolean) -> Unit,
    ): List<EntryPlan> {
        val canonicalRoot = targetDir.canonicalFile
        val rootPath = canonicalRoot.path
        val rootPrefix = "$rootPath${File.separator}"
        val names = hashSetOf<String>()
        val plans = ArrayList<EntryPlan>(minOf(zip.size(), budget.maxEntryCount))
        var totalUncompressedBytes = 0L
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            ensureActive(isCancelled)
            val entry = entries.nextElement()
            if (plans.size >= budget.maxEntryCount) {
                throw IOException(
                    "Remote build ${budget.label} exceeds the ${budget.maxEntryCount}-entry limit."
                )
            }
            val normalized = normalizeEntryName(entry, budget)
            if (!names.add(normalized.name)) {
                throw IOException("Remote build ${budget.label} contains a duplicate entry: ${normalized.name}")
            }
            entryValidator(normalized.name, normalized.isDirectory)

            val output = File(canonicalRoot, normalized.name).canonicalFile
            if (output.path == rootPath || !output.path.startsWith(rootPrefix)) {
                throw IOException(
                    "Remote build ${budget.label} entry escapes the target directory: ${entry.name}"
                )
            }

            val uncompressedSize = entry.size
            val compressedSize = entry.compressedSize
            if (uncompressedSize < 0L || compressedSize < 0L) {
                throw IOException(
                    "Remote build ${budget.label} entry has unknown size metadata: ${entry.name}"
                )
            }
            if (normalized.isDirectory && uncompressedSize != 0L) {
                throw IOException(
                    "Remote build ${budget.label} directory entry has data: ${entry.name}"
                )
            }
            if (uncompressedSize > budget.maxEntryUncompressedBytes) {
                throw IOException(
                    "Remote build ${budget.label} entry exceeds the " +
                            "${budget.maxEntryUncompressedBytes}-byte uncompressed limit: ${entry.name}"
                )
            }
            if (totalUncompressedBytes > budget.maxTotalUncompressedBytes - uncompressedSize) {
                throw IOException(
                    "Remote build ${budget.label} exceeds the " +
                            "${budget.maxTotalUncompressedBytes}-byte cumulative uncompressed limit."
                )
            }
            totalUncompressedBytes += uncompressedSize
            validateCompressionRatio(entry, uncompressedSize, compressedSize, budget)
            plans += EntryPlan(entry, normalized.name, normalized.isDirectory, output, uncompressedSize)
        }
        return plans
    }

    private fun normalizeEntryName(entry: ZipEntry, budget: RemoteZipBudget): NormalizedEntry {
        val slashNormalized = entry.name.replace('\\', '/')
        if (
            slashNormalized.isBlank() ||
            slashNormalized.indexOf('\u0000') >= 0 ||
            slashNormalized.startsWith('/') ||
            WINDOWS_ABSOLUTE_PATH.containsMatchIn(slashNormalized)
        ) {
            throw IOException("Unsafe remote build ${budget.label} entry: ${entry.name}")
        }
        val withoutTrailingSlashes = slashNormalized.trimEnd('/')
        val segments = withoutTrailingSlashes.split('/')
        if (
            withoutTrailingSlashes.isBlank() ||
            segments.any { it.isBlank() || it == "." || it == ".." } ||
            withoutTrailingSlashes.length > budget.maxPathLength ||
            segments.size > budget.maxPathDepth
        ) {
            throw IOException("Unsafe remote build ${budget.label} entry: ${entry.name}")
        }
        return NormalizedEntry(
            name = segments.joinToString("/"),
            isDirectory = entry.isDirectory || slashNormalized.endsWith('/'),
        )
    }

    private fun validateCompressionRatio(
        entry: ZipEntry,
        uncompressedSize: Long,
        compressedSize: Long,
        budget: RemoteZipBudget,
    ) {
        if (uncompressedSize < budget.compressionRatioThresholdBytes || uncompressedSize == 0L) {
            return
        }
        if (
            compressedSize == 0L ||
            exceedsCompressionRatio(uncompressedSize, compressedSize, budget.maxCompressionRatio)
        ) {
            throw IOException(
                "Remote build ${budget.label} entry exceeds the ${budget.maxCompressionRatio}:1 " +
                        "compression-ratio limit: ${entry.name}"
            )
        }
    }

    private fun exceedsCompressionRatio(
        uncompressedSize: Long,
        compressedSize: Long,
        maxCompressionRatio: Long,
    ): Boolean {
        val quotient = uncompressedSize / compressedSize
        val remainder = uncompressedSize % compressedSize
        return quotient > maxCompressionRatio ||
                (quotient == maxCompressionRatio && remainder > 0L)
    }

    private fun extractEntries(
        zip: ZipFile,
        plans: List<EntryPlan>,
        budget: RemoteZipBudget,
        isCancelled: () -> Boolean,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalUncompressedBytes = 0L
        plans.forEach { plan ->
            ensureActive(isCancelled)
            if (plan.isDirectory) {
                if (!plan.output.exists() && !plan.output.mkdirs()) {
                    throw IOException("Failed to create ${budget.label} directory: ${plan.output.path}")
                }
                return@forEach
            }
            plan.output.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IOException("Failed to create ${budget.label} parent directory: ${parent.path}")
                }
            }
            var entryBytes = 0L
            zip.getInputStream(plan.entry).use { input ->
                FileOutputStream(plan.output, false).use { output ->
                    while (true) {
                        ensureActive(isCancelled)
                        val read = input.read(buffer)
                        if (read < 0) {
                            break
                        }
                        if (read == 0) {
                            continue
                        }
                        val readBytes = read.toLong()
                        if (
                            entryBytes > budget.maxEntryUncompressedBytes - readBytes ||
                            entryBytes > plan.declaredSize - readBytes
                        ) {
                            throw IOException(
                                "Remote build ${budget.label} entry expanded beyond its declared limit: ${plan.name}"
                            )
                        }
                        if (totalUncompressedBytes > budget.maxTotalUncompressedBytes - readBytes) {
                            throw IOException(
                                "Remote build ${budget.label} exceeded its cumulative uncompressed limit."
                            )
                        }
                        output.write(buffer, 0, read)
                        entryBytes += readBytes
                        totalUncompressedBytes += readBytes
                    }
                    output.flush()
                }
            }
            if (entryBytes != plan.declaredSize) {
                throw IOException(
                    "Remote build ${budget.label} entry size mismatch: " +
                            "entry=${plan.name} declared=${plan.declaredSize} actual=$entryBytes"
                )
            }
        }
    }

    private fun ensureActive(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Remote APK build session was cancelled.")
        }
    }

    private fun deleteRecursivelyBestEffort(file: File) {
        runCatching {
            if (file.exists()) {
                file.deleteRecursively()
            }
        }
    }

    private data class NormalizedEntry(
        val name: String,
        val isDirectory: Boolean,
    )

    private data class EntryPlan(
        val entry: ZipEntry,
        val name: String,
        val isDirectory: Boolean,
        val output: File,
        val declaredSize: Long,
    )
}
