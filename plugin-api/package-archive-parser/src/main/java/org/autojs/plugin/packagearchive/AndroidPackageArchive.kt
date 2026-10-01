package org.autojs.plugin.packagearchive

import android.content.Context
import android.os.Build
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.abs

/**
 * A bounded, read-only description of an Android package file or package container.
 *
 * Container metadata is treated as a hint. Installation decisions are based on the manifests of
 * the APK entries themselves.
 */
data class AndroidPackageArchive(
    val sourceFile: File,
    val format: AndroidPackageFormat,
    val subtype: AndroidPackageSubtype,
    val apkEntries: List<ArchiveApkEntry>,
    val apkEntryCount: Int = apkEntries.size,
    val selectedApks: List<ArchiveApkEntry>,
    val obbEntries: List<ArchiveAssetEntry>,
    val aabModules: List<String>,
    val baseManifest: ManifestSummary?,
    val problems: List<ArchiveProblem>,
) {

    val baseApk: ArchiveApkEntry?
        get() = selectedApks.firstOrNull { it.manifest.splitName.isNullOrBlank() }

    val canInstall: Boolean
        get() = format != AndroidPackageFormat.AAB &&
            selectedApks.isNotEmpty() &&
            problems.none { it.blocking }

    val installationState: ArchiveInstallationState
        get() = when {
            format == AndroidPackageFormat.AAB -> ArchiveInstallationState.AAB_REQUIRES_CONVERSION
            canInstall && obbEntries.isNotEmpty() -> ArchiveInstallationState.READY_WITH_UNDEPLOYED_OBB
            canInstall -> ArchiveInstallationState.READY
            problems.any { it.code == ArchiveProblemCode.INCOMPATIBLE_DEVICE } -> ArchiveInstallationState.INCOMPATIBLE
            else -> ArchiveInstallationState.INVALID
        }

    fun decodeDisplayManifest(): String = when (format) {
        AndroidPackageFormat.AAB -> AabManifestDisplayDecoder.decode(sourceFile)
        AndroidPackageFormat.APK -> ApkManifestDisplayDecoder.decode(sourceFile)
        else -> {
            val base = baseApk ?: throw IOException("A base APK is unavailable")
            ZipFile(sourceFile).use { zip ->
                val entry = zip.getEntry(base.archivePath)
                    ?.takeUnless { it.isDirectory }
                    ?: throw IOException("Base APK entry is missing")
                zip.getInputStream(entry).use(ApkManifestDisplayDecoder::decodeApk)
            }
        }
    }

    /**
     * Copies the selected APK set to an app-private, caller-owned directory. Generated names are
     * independent of untrusted archive paths.
     */
    fun stageSelectedApks(parentDirectory: File): List<StagedApk> {
        if (!canInstall) throw IOException("The package archive is not installable")
        if (!parentDirectory.exists() && !parentDirectory.mkdirs()) {
            throw IOException("Unable to create the package staging directory")
        }
        val declaredBytes = selectedApks.fold(0L) { total, entry ->
            Math.addExact(total, entry.size.coerceAtLeast(0L))
        }
        if (declaredBytes > MAX_STAGED_APK_BYTES) {
            throw IOException("Package data exceeds the staging size limit")
        }
        val usableSpace = parentDirectory.usableSpace
        val storageBound = if (usableSpace > 0L) {
            (usableSpace - MINIMUM_FREE_CACHE_BYTES).coerceAtLeast(0L)
        } else {
            MAX_STAGED_APK_BYTES
        }
        if (declaredBytes > storageBound) {
            throw IOException("Insufficient storage space to stage the package")
        }
        val stagingLimit = minOf(MAX_STAGED_APK_BYTES, storageBound)
        val staged = mutableListOf<StagedApk>()
        var totalWritten = 0L

        fun stage(entry: ArchiveApkEntry, input: InputStream) {
            val generatedName = when {
                entry.manifest.splitName.isNullOrBlank() -> "base.apk"
                else -> {
                    val safeSplit = entry.manifest.splitName
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .take(120)
                    "${staged.size.toString().padStart(3, '0')}-$safeSplit.apk"
                }
            }
            val outputFile = File(parentDirectory, generatedName)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                BufferedOutputStream(FileOutputStream(outputFile)).use { output ->
                    DigestInputStream(input, digest).use { verifiedInput ->
                        totalWritten = verifiedInput.copyBoundedTo(
                            output = output,
                            alreadyWritten = totalWritten,
                            maxTotal = stagingLimit,
                        )
                    }
                }
                val expectedDigest = entry.sha256
                    ?: throw IOException("Selected APK was not prepared for installation")
                if (!digest.digest().toHexString().equals(expectedDigest, ignoreCase = true)) {
                    throw IOException("Package archive changed after it was inspected")
                }
            } catch (error: Throwable) {
                runCatching { outputFile.delete() }
                throw error
            }
            staged += StagedApk(outputFile, entry.manifest)
        }

        try {
            when (format) {
                AndroidPackageFormat.APK -> {
                    BufferedInputStream(sourceFile.inputStream()).use { input ->
                        stage(selectedApks.single(), input)
                    }
                }
                AndroidPackageFormat.AAB -> throw IOException("AAB files cannot be installed directly")
                else -> ZipFile(sourceFile).use { zip ->
                    selectedApks.forEach { selected ->
                        val entry = zip.getEntry(selected.archivePath)
                            ?.takeUnless { it.isDirectory }
                            ?: throw IOException("APK entry is missing: ${selected.archivePath}")
                        if (entry.size > MAX_SINGLE_APK_BYTES) {
                            throw IOException("APK entry exceeds the staging size limit")
                        }
                        zip.getInputStream(entry).use { input -> stage(selected, input) }
                    }
                }
            }
            return staged
        } catch (error: Throwable) {
            staged.forEach { runCatching { it.file.delete() } }
            throw error
        }
    }

    fun createDisplayApk(cacheDirectory: File): File? {
        val base = baseApk ?: return null
        if (format == AndroidPackageFormat.AAB) return null
        if (format != AndroidPackageFormat.APK) {
            val available = (cacheDirectory.usableSpace - MINIMUM_FREE_CACHE_BYTES).coerceAtLeast(0L)
            if (base.size > MAX_DISPLAY_APK_BYTES || base.size > available) {
                return null
            }
        }
        val directory = File(cacheDirectory, "package-info-${UUID.randomUUID()}").apply {
            if (!mkdirs()) throw IOException("Unable to create the package information directory")
        }
        val copyLimit = if (format == AndroidPackageFormat.APK) {
            MAX_SINGLE_APK_BYTES
        } else {
            MAX_DISPLAY_APK_BYTES
        }
        return try {
            val output = File(directory, "base.apk")
            when (format) {
                AndroidPackageFormat.APK -> sourceFile.inputStream().use { input ->
                    output.outputStream().buffered().use { target ->
                        input.copyBoundedTo(target, 0L, copyLimit)
                    }
                }
                AndroidPackageFormat.AAB -> error("AAB files do not contain an installable base APK")
                else -> ZipFile(sourceFile).use { zip ->
                    val entry = zip.getEntry(base.archivePath)
                        ?.takeUnless { it.isDirectory }
                        ?: throw IOException("Base APK entry is missing")
                    zip.getInputStream(entry).use { input ->
                        output.outputStream().buffered().use { target ->
                            input.copyBoundedTo(target, 0L, copyLimit)
                        }
                    }
                }
            }
            output
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    companion object {
        private const val MAX_SINGLE_APK_BYTES = PackageInspectionLimits.PACKAGE_BYTES
        private const val MAX_STAGED_APK_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
        private const val MAX_DISPLAY_APK_BYTES = PackageInspectionLimits.PACKAGE_BYTES
        private const val MINIMUM_FREE_CACHE_BYTES = 128L * 1024L * 1024L

        private fun InputStream.copyBoundedTo(
            output: java.io.OutputStream,
            alreadyWritten: Long,
            maxTotal: Long,
        ): Long {
            var total = alreadyWritten
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > maxTotal) throw IOException("Package data exceeds the staging size limit")
                output.write(buffer, 0, read)
            }
            return total
        }

        private fun ByteArray.toHexString(): String =
            joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}

enum class AndroidPackageFormat {
    APK,
    APKS,
    XAPK,
    APKM,
    APKZ,
    AAB,
}

enum class AndroidPackageSubtype {
    SINGLE_APK,
    BUNDLETOOL_APKS,
    SAI_APKS,
    GENERIC_APKS,
    XAPK,
    APKM,
    APKZ,
    ANDROID_APP_BUNDLE,
}

enum class ArchiveInstallationState {
    READY,
    READY_WITH_UNDEPLOYED_OBB,
    AAB_REQUIRES_CONVERSION,
    INCOMPATIBLE,
    INVALID,
}

enum class ArchiveProblemCode {
    INVALID_ARCHIVE,
    NO_BASE_APK,
    AMBIGUOUS_BASE_APK,
    INCOMPATIBLE_DEVICE,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    DUPLICATE_SPLIT,
    MISSING_SPLIT_DEPENDENCY,
    MALFORMED_APK,
}

data class ArchiveProblem(
    val code: ArchiveProblemCode,
    val detail: String,
    val blocking: Boolean = true,
)

data class ArchiveAssetEntry(
    val archivePath: String,
    val size: Long,
)

data class ArchiveApkEntry(
    val archivePath: String,
    val size: Long,
    val manifest: ManifestSummary,
    val sha256: String? = null,
)

data class StagedApk(
    val file: File,
    val manifest: ManifestSummary,
)

data class ManifestSummary @JvmOverloads constructor(
    val packageName: String?,
    val splitName: String?,
    val configForSplit: String?,
    val featureSplit: Boolean,
    val splitRequired: Boolean,
    val versionCode: Long?,
    val versionName: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val maxSdk: Int?,
    val applicationLabel: String?,
    val requestedPermissions: List<String>,
    val usesSplits: List<String>,
    /** Null only when the verified manifest root has no Android sharedUserId declaration. */
    val sharedUserId: String? = null,
)

data class PackageDeviceSpec(
    val sdk: Int,
    val abis: List<String>,
    val densityDpi: Int,
    val locales: List<String>,
) {
    companion object {
        fun from(context: Context): PackageDeviceSpec {
            val configuration = context.resources.configuration
            val locales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
            } else {
                @Suppress("DEPRECATION")
                listOf(configuration.locale.toLanguageTag())
            }
            return PackageDeviceSpec(
                sdk = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS.toList(),
                densityDpi = context.resources.displayMetrics.densityDpi,
                locales = locales,
            )
        }
    }
}

object AndroidPackageArchiveInspector {

    private const val MAX_ARCHIVE_ENTRIES = PackageInspectionLimits.ARCHIVE_ENTRIES
    private const val MAX_GENERIC_APK_ENTRIES = PackageInspectionLimits.GENERIC_APKS
    private const val MAX_ENTRY_NAME_CHARS = PackageInspectionLimits.ENTRY_NAME_CHARS
    private const val MAX_DECLARED_ENTRY_BYTES = PackageInspectionLimits.PACKAGE_BYTES
    private const val MAX_DECLARED_TOTAL_BYTES = PackageInspectionLimits.TOTAL_DECLARED_BYTES
    private const val MAX_NESTED_APK_SCAN_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
    private const val MAX_SINGLE_SELECTED_APK_BYTES = PackageInspectionLimits.PACKAGE_BYTES
    private const val MAX_SELECTED_APK_BYTES = PackageInspectionLimits.TOTAL_SCAN_BYTES
    private const val MAX_METADATA_BYTES = PackageInspectionLimits.METADATA_BYTES

    fun inspect(
        file: File,
        device: PackageDeviceSpec,
        prepareForInstallation: Boolean = false,
    ): AndroidPackageArchive {
        if (!file.isFile) throw IOException("Package file does not exist")
        return ZipFile(file).use { zip ->
            // A standalone APK only needs its bounded manifest for inspection. Its resources are
            // neither enumerated nor extracted here; container entry budgets apply below instead.
            val rootManifest = zip.getEntry("AndroidManifest.xml")?.takeUnless { it.isDirectory }
            if (rootManifest != null) {
                val xml = ApkManifestDisplayDecoder.decode(file)
                val summary = ManifestSummaryParser.parse(xml)
                val entry = ArchiveApkEntry(
                    archivePath = file.name,
                    size = file.length(),
                    manifest = summary,
                    sha256 = if (prepareForInstallation) {
                        file.inputStream().buffered().use { input ->
                            input.sha256Bounded(MAX_SELECTED_APK_BYTES).sha256
                        }
                    } else {
                        null
                    },
                )
                return AndroidPackageArchive(
                    sourceFile = file,
                    format = AndroidPackageFormat.APK,
                    subtype = AndroidPackageSubtype.SINGLE_APK,
                    apkEntries = listOf(entry),
                    selectedApks = listOf(entry),
                    obbEntries = emptyList(),
                    aabModules = emptyList(),
                    baseManifest = summary,
                    problems = validateSelected(listOf(entry), device),
                )
            }

            val entries = validateAndCollectEntries(zip)
            val entryNames = entries.asSequence().map { it.name }.toSet()
            val extension = file.extension.lowercase(Locale.ROOT)
            val format = detectContainerFormat(extension, entryNames)
            if (format == AndroidPackageFormat.AAB) {
                val xml = AabManifestDisplayDecoder.decode(file)
                val summary = ManifestSummaryParser.parse(xml)
                val modules = entries.asSequence()
                    .mapNotNull { entry ->
                        entry.name
                            .takeIf { it.endsWith("/manifest/AndroidManifest.xml", ignoreCase = false) }
                            ?.substringBefore('/')
                            ?.takeIf(String::isNotBlank)
                    }
                    .distinct()
                    .sorted()
                    .toList()
                return AndroidPackageArchive(
                    sourceFile = file,
                    format = format,
                    subtype = AndroidPackageSubtype.ANDROID_APP_BUNDLE,
                    apkEntries = emptyList(),
                    selectedApks = emptyList(),
                    obbEntries = emptyList(),
                    aabModules = modules,
                    baseManifest = summary,
                    problems = emptyList(),
                )
            }

            val subtype = detectSubtype(format, entryNames)
            val apkZipEntries = entries.filter {
                !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true)
            }
            if (subtype != AndroidPackageSubtype.BUNDLETOOL_APKS &&
                apkZipEntries.size > MAX_GENERIC_APK_ENTRIES
            ) {
                throw IOException("APK entry count exceeds the inspection limit")
            }

            val problems = mutableListOf<ArchiveProblem>()
            val tocSelection = if (subtype == AndroidPackageSubtype.BUNDLETOOL_APKS) {
                try {
                    BundletoolTocDecoder.select(file, device)
                } catch (error: Exception) {
                    problems += ArchiveProblem(
                        ArchiveProblemCode.INVALID_ARCHIVE,
                        "Unable to select APKs from toc.pb: ${error.message.orEmpty()}",
                    )
                    null
                }
            } else {
                null
            }
            val entriesByPath = entries.associateBy(ZipEntry::getName)
            val entriesToInspect = if (subtype == AndroidPackageSubtype.BUNDLETOOL_APKS) {
                tocSelection
                    ?.apkPaths
                    .orEmpty()
                    .distinct()
                    .mapNotNull(entriesByPath::get)
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            } else {
                apkZipEntries
            }
            val scanBudget = InspectionBudget(MAX_NESTED_APK_SCAN_BYTES)
            val apkEntries = entriesToInspect.mapNotNull { entry ->
                try {
                    if (entry.size > MAX_DECLARED_ENTRY_BYTES) {
                        throw IOException("Nested APK exceeds the inspection limit")
                    }
                    val xml = zip.getInputStream(entry).use { input ->
                        ApkManifestDisplayDecoder.decodeApk(BudgetedInputStream(input, scanBudget))
                    }
                    ArchiveApkEntry(entry.name, entry.size.coerceAtLeast(0L), ManifestSummaryParser.parse(xml))
                } catch (error: Exception) {
                    problems += ArchiveProblem(
                        code = ArchiveProblemCode.MALFORMED_APK,
                        detail = "${entry.name}: ${error.message.orEmpty()}",
                        blocking = false,
                    )
                    null
                }
            }
            val obbEntries = entries.asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".obb", ignoreCase = true) }
                .map { ArchiveAssetEntry(it.name, it.size.coerceAtLeast(0L)) }
                .toList()

            val packageHint = readContainerPackageHint(zip, entries)
            val selection = when (subtype) {
                AndroidPackageSubtype.BUNDLETOOL_APKS ->
                    tocSelection?.let {
                        selectBundletoolApks(
                            tocSelection = it,
                            apks = apkEntries,
                            archiveEntriesByPath = entriesByPath,
                        )
                    } ?: Selection(emptyList(), emptyList())
                else -> selectApks(apkEntries, device, packageHint)
            }
            problems += selection.problems
            val selectedApks = if (prepareForInstallation) {
                attachSelectedDigests(zip, selection.entries)
            } else {
                selection.entries
            }
            problems += validateSelected(selectedApks, device)

            AndroidPackageArchive(
                sourceFile = file,
                format = format,
                subtype = subtype,
                apkEntries = apkEntries,
                apkEntryCount = apkZipEntries.size,
                selectedApks = selectedApks,
                obbEntries = obbEntries,
                aabModules = emptyList(),
                baseManifest = selectedApks.firstOrNull { it.manifest.splitName.isNullOrBlank() }?.manifest,
                problems = problems.distinctBy { Triple(it.code, it.detail, it.blocking) },
            )
        }
    }

    private fun validateAndCollectEntries(zip: ZipFile): List<ZipEntry> {
        val result = ArrayList<ZipEntry>()
        val entryNames = HashSet<String>()
        var declaredTotal = 0L
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (result.size >= MAX_ARCHIVE_ENTRIES) {
                throw IOException("Archive entry count exceeds the inspection limit")
            }
            validateEntryName(entry.name)
            if (!entryNames.add(entry.name)) {
                throw IOException("Archive contains duplicate entry names")
            }
            if (entry.size > MAX_DECLARED_ENTRY_BYTES) {
                throw IOException("Archive entry exceeds the inspection size limit")
            }
            if (entry.size > 0L) {
                declaredTotal = Math.addExact(declaredTotal, entry.size)
                if (declaredTotal > MAX_DECLARED_TOTAL_BYTES) {
                    throw IOException("Archive contents exceed the inspection size limit")
                }
            }
            result += entry
        }
        return result
    }

    private fun attachSelectedDigests(
        zip: ZipFile,
        selected: List<ArchiveApkEntry>,
    ): List<ArchiveApkEntry> {
        var totalRead = 0L
        val digests = selected
            .distinctBy(ArchiveApkEntry::archivePath)
            .associate { apk ->
                val entry = zip.getEntry(apk.archivePath)
                    ?.takeUnless { it.isDirectory }
                    ?: throw IOException("Selected APK entry is missing: ${apk.archivePath}")
                val digest = zip.getInputStream(entry).buffered().use { input ->
                    input.sha256Bounded(
                        maxBytes = minOf(
                            MAX_SINGLE_SELECTED_APK_BYTES,
                            MAX_SELECTED_APK_BYTES - totalRead,
                        ),
                    ).also { result ->
                        totalRead = Math.addExact(totalRead, result.bytesRead)
                    }
                }
                apk.archivePath to digest.sha256
            }
        return selected.map { apk ->
            apk.copy(sha256 = digests.getValue(apk.archivePath))
        }
    }

    private fun InputStream.sha256Bounded(maxBytes: Long): DigestResult {
        if (maxBytes < 0L) throw IOException("Selected APK data exceeds the inspection limit")
        val digest = MessageDigest.getInstance("SHA-256")
        val counting = CountingInputStream(this)
        DigestInputStream(counting, digest).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (counting.count > maxBytes) {
                    throw IOException("Selected APK data exceeds the inspection limit")
                }
            }
        }
        return DigestResult(
            sha256 = digest.digest().toHexString(),
            bytesRead = counting.count,
        )
    }

    private data class DigestResult(
        val sha256: String,
        val bytesRead: Long,
    )

    private class CountingInputStream(
        input: InputStream,
    ) : FilterInputStream(input) {

        var count: Long = 0L
            private set

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) count++
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { read ->
                if (read > 0) count += read
            }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun validateEntryName(name: String) {
        if (
            name.isBlank() ||
            name.length > MAX_ENTRY_NAME_CHARS ||
            '\u0000' in name ||
            '\\' in name ||
            name.startsWith('/') ||
            Regex("^[A-Za-z]:").containsMatchIn(name) ||
            name.split('/').any { it == ".." }
        ) {
            throw IOException("Archive contains an unsafe entry name")
        }
    }

    private fun detectContainerFormat(extension: String, entryNames: Set<String>): AndroidPackageFormat {
        return when {
            "BundleConfig.pb" in entryNames -> AndroidPackageFormat.AAB
            "toc.pb" in entryNames ||
                "meta.sai_v1.json" in entryNames ||
                "meta.sai_v2.json" in entryNames -> AndroidPackageFormat.APKS
            "apkz.json" in entryNames -> AndroidPackageFormat.APKZ
            entryNames.any { it.startsWith("META-INF/APKMIRRO", true) } -> AndroidPackageFormat.APKM
            "manifest.json" in entryNames -> AndroidPackageFormat.XAPK
            extension == "aab" -> AndroidPackageFormat.AAB
            extension == "apks" -> AndroidPackageFormat.APKS
            extension == "xapk" -> AndroidPackageFormat.XAPK
            extension == "apkm" -> AndroidPackageFormat.APKM
            extension == "apkz" -> AndroidPackageFormat.APKZ
            entryNames.any { it.endsWith(".apk", ignoreCase = true) } -> AndroidPackageFormat.APKS
            else -> throw IOException("Unsupported Android package container")
        }
    }

    private fun detectSubtype(
        format: AndroidPackageFormat,
        entryNames: Set<String>,
    ): AndroidPackageSubtype = when (format) {
        AndroidPackageFormat.APK -> AndroidPackageSubtype.SINGLE_APK
        AndroidPackageFormat.APKS -> when {
            "toc.pb" in entryNames -> AndroidPackageSubtype.BUNDLETOOL_APKS
            "meta.sai_v1.json" in entryNames || "meta.sai_v2.json" in entryNames -> AndroidPackageSubtype.SAI_APKS
            else -> AndroidPackageSubtype.GENERIC_APKS
        }
        AndroidPackageFormat.XAPK -> AndroidPackageSubtype.XAPK
        AndroidPackageFormat.APKM -> AndroidPackageSubtype.APKM
        AndroidPackageFormat.APKZ -> AndroidPackageSubtype.APKZ
        AndroidPackageFormat.AAB -> AndroidPackageSubtype.ANDROID_APP_BUNDLE
    }

    private data class Selection(
        val entries: List<ArchiveApkEntry>,
        val problems: List<ArchiveProblem>,
    )

    private fun selectBundletoolApks(
        tocSelection: BundletoolTocDecoder.Selection,
        apks: List<ArchiveApkEntry>,
        archiveEntriesByPath: Map<String, ZipEntry>,
    ): Selection {
        val readableApksByPath = apks.associateBy(ArchiveApkEntry::archivePath)
        val problems = mutableListOf<ArchiveProblem>()
        val selected = tocSelection.apkPaths.mapNotNull { path ->
            readableApksByPath[path] ?: run {
                val archiveEntry = archiveEntriesByPath[path]
                val detail = when {
                    archiveEntry == null || archiveEntry.isDirectory ->
                        "toc.pb selected a missing component: $path"
                    !path.endsWith(".apk", ignoreCase = true) ->
                        "toc.pb selected a component that is not an APK: $path"
                    else -> "toc.pb selected an unreadable APK: $path"
                }
                problems += ArchiveProblem(
                    if (archiveEntry == null || archiveEntry.isDirectory) {
                        ArchiveProblemCode.INVALID_ARCHIVE
                    } else {
                        ArchiveProblemCode.MALFORMED_APK
                    },
                    detail,
                )
                null
            }
        }
        tocSelection.packageName?.let { expected ->
            selected.filter { it.manifest.packageName != expected }.forEach { apk ->
                problems += ArchiveProblem(
                    ArchiveProblemCode.PACKAGE_MISMATCH,
                    "toc.pb package mismatch: ${apk.archivePath}",
                )
            }
        }
        return Selection(selected, problems)
    }

    private class InspectionBudget(
        private var remaining: Long,
    ) {
        fun consume(byteCount: Long) {
            if (byteCount <= 0L) return
            remaining -= byteCount
            if (remaining < 0L) {
                throw IOException("Nested APK manifest scans exceed the inspection limit")
            }
        }
    }

    private class BudgetedInputStream(
        input: InputStream,
        private val budget: InspectionBudget,
    ) : FilterInputStream(input) {

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) budget.consume(1L)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { read ->
                if (read > 0) budget.consume(read.toLong())
            }

        override fun skip(byteCount: Long): Long =
            super.skip(byteCount).also(budget::consume)
    }

    private fun selectApks(
        apks: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
        packageHint: String?,
    ): Selection {
        val problems = mutableListOf<ArchiveProblem>()
        if (apks.isEmpty()) {
            return Selection(
                emptyList(),
                listOf(ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "No readable APK entries were found")),
            )
        }
        var bases = apks.filter { it.manifest.splitName.isNullOrBlank() }
        packageHint?.let { hint ->
            bases.filter { it.manifest.packageName == hint }.takeIf { it.isNotEmpty() }?.let { bases = it }
        }
        if (bases.isEmpty()) {
            return Selection(
                emptyList(),
                listOf(ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "No base APK was found")),
            )
        }

        val base = chooseBase(bases, device)
        if (base == null) {
            return Selection(
                emptyList(),
                listOf(
                    ArchiveProblem(
                        ArchiveProblemCode.AMBIGUOUS_BASE_APK,
                        "More than one base APK matches this device",
                    ),
                ),
            )
        }
        if (base.manifest.minSdk?.let { it > device.sdk } == true ||
            base.manifest.maxSdk?.let { it < device.sdk } == true
        ) {
            problems += ArchiveProblem(
                ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                "The base APK does not support Android API ${device.sdk}",
            )
        }

        val lowerPath = base.archivePath.lowercase(Locale.ROOT)
        if ("universal" in lowerPath || lowerPath.startsWith("standalones/")) {
            return Selection(listOf(base), problems)
        }
        val family = apks.filter { candidate ->
            candidate !== base &&
                candidate.manifest.packageName == base.manifest.packageName &&
                candidate.manifest.versionCode == base.manifest.versionCode &&
                when {
                    lowerPath.startsWith("splits/") -> candidate.archivePath.startsWith("splits/", true)
                    else -> !candidate.archivePath.startsWith("standalones/", true)
                }
        }

        val selected = mutableListOf(base)
        val targets = family.groupBy { it.manifest.configForSplit.orEmpty() }
        targets.values.forEach { group ->
            val classified = group.groupBy(::classifyTarget)
            classified[TargetKind.OTHER].orEmpty().let(selected::addAll)

            val abiCandidates = classified[TargetKind.ABI].orEmpty()
            if (abiCandidates.isNotEmpty()) {
                val chosen = abiCandidates.minByOrNull { candidate ->
                    abiIndex(candidate.archivePath, candidate.manifest.splitName, device.abis)
                }
                if (chosen == null ||
                    abiIndex(chosen.archivePath, chosen.manifest.splitName, device.abis) == Int.MAX_VALUE
                ) {
                    problems += ArchiveProblem(
                        ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                        "No APK matches the device ABI",
                    )
                } else {
                    selected += chosen
                }
            }

            classified[TargetKind.DENSITY].orEmpty().minByOrNull {
                abs(densityOf(it) - device.densityDpi)
            }?.let(selected::add)

            classified[TargetKind.LOCALE].orEmpty()
                .filter { localeMatches(it, device.locales) }
                .let(selected::addAll)

            classified[TargetKind.SDK].orEmpty()
                .mapNotNull { entry -> sdkOf(entry)?.let { sdk -> entry to sdk } }
                .filter { (_, sdk) -> sdk <= device.sdk }
                .maxByOrNull { (_, sdk) -> sdk }
                ?.first
                ?.let(selected::add)
        }

        return Selection(selected.distinctBy { it.archivePath }, problems)
    }

    private fun chooseBase(
        bases: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
    ): ArchiveApkEntry? {
        if (bases.size == 1) return bases.single()
        bases.singleOrNull { it.archivePath.substringAfterLast('/').equals("base.apk", true) }?.let { return it }
        bases.singleOrNull { "universal" in it.archivePath.lowercase(Locale.ROOT) }?.let { return it }

        val ranked = bases.mapNotNull { candidate ->
            val abi = abiIndex(candidate.archivePath, null, device.abis)
            if (containsKnownAbi(candidate.archivePath) && abi == Int.MAX_VALUE) return@mapNotNull null
            val sdk = sdkOf(candidate)
            if (sdk != null && sdk > device.sdk) return@mapNotNull null
            val densityPenalty = densityOf(candidate)
                .takeIf { it > 0 }
                ?.let { abs(it - device.densityDpi) }
                ?: 0
            candidate to (abi.coerceAtMost(1_000) * 100_000 + densityPenalty)
        }.sortedBy { it.second }
        return ranked.firstOrNull()?.takeIf { ranked.size == 1 || ranked[0].second < ranked[1].second }?.first
    }

    private enum class TargetKind {
        ABI,
        DENSITY,
        LOCALE,
        SDK,
        OTHER,
    }

    private fun classifyTarget(entry: ArchiveApkEntry): TargetKind {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return when {
            containsKnownAbi(value) -> TargetKind.ABI
            DENSITIES.keys.any { qualifier -> qualifierIn(value, qualifier) } -> TargetKind.DENSITY
            SDK_QUALIFIER.find(value) != null -> TargetKind.SDK
            localeQualifier(entry) != null -> TargetKind.LOCALE
            else -> TargetKind.OTHER
        }
    }

    private fun containsKnownAbi(value: String): Boolean =
        ABI_ALIASES.keys.any { qualifier -> qualifierIn(value.lowercase(Locale.ROOT), qualifier) }

    private fun abiIndex(path: String, splitName: String?, supportedAbis: List<String>): Int {
        val value = "$path ${splitName.orEmpty()}".lowercase(Locale.ROOT)
        val archiveAbi = ABI_ALIASES.entries.firstOrNull { (qualifier) -> qualifierIn(value, qualifier) }?.value
            ?: return 999
        return supportedAbis.indexOfFirst { supported ->
            ABI_ALIASES[supported.lowercase(Locale.ROOT)] == archiveAbi ||
                supported.equals(archiveAbi, ignoreCase = true)
        }.takeIf { it >= 0 } ?: Int.MAX_VALUE
    }

    private fun densityOf(entry: ArchiveApkEntry): Int {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return DENSITIES.entries.firstOrNull { (qualifier) -> qualifierIn(value, qualifier) }?.value ?: -1
    }

    private fun sdkOf(entry: ArchiveApkEntry): Int? {
        val value = "${entry.archivePath} ${entry.manifest.splitName.orEmpty()}".lowercase(Locale.ROOT)
        return SDK_QUALIFIER.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun localeMatches(entry: ArchiveApkEntry, deviceLocales: List<String>): Boolean {
        val qualifier = localeQualifier(entry) ?: return false
        val normalized = qualifier.replace("-r", "-", ignoreCase = true).replace('+', '-')
        val wanted = Locale.forLanguageTag(normalized)
        return deviceLocales.any { tag ->
            val actual = Locale.forLanguageTag(tag)
            actual.language.equals(wanted.language, true) &&
                (wanted.country.isBlank() || actual.country.equals(wanted.country, true)) &&
                (wanted.script.isBlank() || actual.script.equals(wanted.script, true))
        }
    }

    private fun localeQualifier(entry: ArchiveApkEntry): String? {
        val split = entry.manifest.splitName.orEmpty()
        val suffix = split.substringAfter("config.", "")
        if (LOCALE_QUALIFIER.matches(suffix)) return suffix
        val file = entry.archivePath.substringAfterLast('/').substringBeforeLast('.')
        return LOCALE_FROM_FILE.find(file)?.groupValues?.getOrNull(1)
    }

    private fun qualifierIn(value: String, qualifier: String): Boolean =
        Regex("(^|[._/+\\-])${Regex.escape(qualifier)}($|[._/+\\-])", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)

    private fun validateSelected(
        selected: List<ArchiveApkEntry>,
        device: PackageDeviceSpec,
    ): List<ArchiveProblem> {
        if (selected.isEmpty()) return emptyList()
        val problems = mutableListOf<ArchiveProblem>()
        val bases = selected.count { it.manifest.splitName.isNullOrBlank() }
        if (bases != 1) {
            problems += ArchiveProblem(ArchiveProblemCode.NO_BASE_APK, "The APK set must contain exactly one base APK")
        }
        val base = selected.firstOrNull { it.manifest.splitName.isNullOrBlank() } ?: return problems
        if (base.manifest.splitRequired && selected.size == 1) {
            problems += ArchiveProblem(
                ArchiveProblemCode.MISSING_SPLIT_DEPENDENCY,
                "The base APK requires split APK components",
            )
        }
        selected.filter { it.manifest.packageName != base.manifest.packageName }.forEach {
            problems += ArchiveProblem(
                ArchiveProblemCode.PACKAGE_MISMATCH,
                "APK package mismatch: ${it.archivePath}",
            )
        }
        selected.filter { it.manifest.versionCode != base.manifest.versionCode }.forEach {
            problems += ArchiveProblem(
                ArchiveProblemCode.VERSION_MISMATCH,
                "APK version mismatch: ${it.archivePath}",
            )
        }
        selected.mapNotNull { it.manifest.splitName }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { split ->
                problems += ArchiveProblem(ArchiveProblemCode.DUPLICATE_SPLIT, "Duplicate split APK: $split")
            }
        val availableSplits = selected.mapNotNull { it.manifest.splitName }.toSet()
        selected.flatMap { it.manifest.usesSplits }
            .filterNot { it in availableSplits }
            .distinct()
            .forEach { dependency ->
                problems += ArchiveProblem(
                    ArchiveProblemCode.MISSING_SPLIT_DEPENDENCY,
                    "Missing required split APK: $dependency",
                )
            }
        if (base.manifest.minSdk?.let { it > device.sdk } == true ||
            base.manifest.maxSdk?.let { it < device.sdk } == true
        ) {
            problems += ArchiveProblem(
                ArchiveProblemCode.INCOMPATIBLE_DEVICE,
                "Package SDK range does not include Android API ${device.sdk}",
            )
        }
        return problems
    }

    private fun readContainerPackageHint(zip: ZipFile, entries: List<ZipEntry>): String? {
        val metadataNames = listOf("manifest.json", "apkz.json", "info.json", "meta.sai_v2.json", "meta.sai_v1.json")
        val entry = metadataNames.firstNotNullOfOrNull { name ->
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) && !it.isDirectory }
        } ?: return null
        if (entry.size > MAX_METADATA_BYTES) return null
        return try {
            val bytes = zip.getInputStream(entry).use { it.readBounded(MAX_METADATA_BYTES) }
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            sequenceOf("package_name", "packageName", "package", "pname")
                .mapNotNull { key -> root.get(key)?.takeIf { it.isJsonPrimitive }?.asString }
                .firstOrNull(String::isNotBlank)
        } catch (_: Exception) {
            null
        }
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Container metadata exceeds the inspection limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to "arm64-v8a",
        "arm64_v8a" to "arm64-v8a",
        "armeabi-v7a" to "armeabi-v7a",
        "armeabi_v7a" to "armeabi-v7a",
        "armeabi" to "armeabi",
        "x86_64" to "x86_64",
        "x86-64" to "x86_64",
        "x86" to "x86",
        "riscv64" to "riscv64",
    )

    private val DENSITIES = mapOf(
        "ldpi" to 120,
        "mdpi" to 160,
        "tvdpi" to 213,
        "hdpi" to 240,
        "xhdpi" to 320,
        "xxhdpi" to 480,
        "xxxhdpi" to 640,
    )

    private val SDK_QUALIFIER = Regex("""(?:^|[._/+\-])(?:sdk|api)[._-]?(\d{1,3})(?:$|[._/+\-])""")
    private val LOCALE_QUALIFIER = Regex("""(?:b\+)?[a-z]{2,3}(?:(?:-r|\+)[A-Z]{2}|\+[A-Z][a-z]{3})?""")
    private val LOCALE_FROM_FILE = Regex("""(?:^|[._+\-])((?:b\+)?[a-z]{2,3}(?:(?:-r|\+)[A-Z]{2})?)(?:$|[._+\-])""")
}

object ManifestSummaryParser {

    private val applicationTag = Regex("""<application\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val usesSdkTag = Regex("""<uses-sdk\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val permissionTag = Regex("""<uses-permission(?:-sdk-\d+)?\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val usesSplitTag = Regex("""<uses-split\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val attribute = Regex("""(?:^|\s)([A-Za-z_][A-Za-z0-9_.-]*(?::[A-Za-z_][A-Za-z0-9_.-]*)?)\s*=\s*("([^"]*)"|'([^']*)')""")

    fun parse(xml: String): ManifestSummary {
        val root = ManifestRootAttributes.read(xml)
        val rootAttributes = root.attributes
        val sdkAttributes = usesSdkTag.find(xml)?.groupValues?.get(1)?.let(::parseAttributes).orEmpty()
        val applicationAttributes = applicationTag.find(xml)
            ?.groupValues
            ?.get(1)
            ?.let(::parseAttributes)
            .orEmpty()
        val lowVersion = rootAttributes.value("versionCode")?.toLongFlexible()
        val highVersion = rootAttributes.value("versionCodeMajor")?.toLongFlexible()
        val versionCode = when {
            lowVersion == null -> null
            highVersion == null -> lowVersion
            else -> (highVersion shl 32) or (lowVersion and 0xFFFF_FFFFL)
        }
        return ManifestSummary(
            packageName = rootAttributes["package"]?.takeIf(String::isNotBlank),
            splitName = rootAttributes["split"]?.takeIf(String::isNotBlank),
            configForSplit = rootAttributes.value("configForSplit")?.takeIf(String::isNotBlank),
            featureSplit = rootAttributes.value("isFeatureSplit").toBooleanFlexible(),
            splitRequired = rootAttributes.value("isSplitRequired").toBooleanFlexible(),
            versionCode = versionCode,
            versionName = rootAttributes.value("versionName")?.takeIf(String::isNotBlank),
            minSdk = sdkAttributes.value("minSdkVersion")?.toIntFlexible(),
            targetSdk = sdkAttributes.value("targetSdkVersion")?.toIntFlexible(),
            maxSdk = sdkAttributes.value("maxSdkVersion")?.toIntFlexible(),
            applicationLabel = applicationAttributes.value("label")?.takeIf(String::isNotBlank),
            requestedPermissions = permissionTag.findAll(xml)
                .mapNotNull { match -> parseAttributes(match.groupValues[1]).value("name") }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList(),
            usesSplits = usesSplitTag.findAll(xml)
                .mapNotNull { match -> parseAttributes(match.groupValues[1]).value("name") }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
                .toList(),
            sharedUserId = root.sharedUserId,
        )
    }

    private fun parseAttributes(source: String): Map<String, String> = buildMap {
        attribute.findAll(source).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            put(name, decodeXmlEntities(value))
        }
    }

    private fun Map<String, String>.value(localName: String): String? =
        entries.firstOrNull { (name) -> name.substringAfter(':') == localName }?.value

    private fun String?.toBooleanFlexible(): Boolean =
        this.equals("true", true) || this == "1" || this.equals("0xffffffff", true)

    private fun String.toIntFlexible(): Int? = toLongFlexible()?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()

    private fun String.toLongFlexible(): Long? {
        val value = trim()
        return when {
            value.startsWith("0x", true) -> value.substring(2).toLongOrNull(16)
            else -> value.toLongOrNull()
        }
    }

    private fun decodeXmlEntities(value: String): String =
        ENTITY.replace(value) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> {
                    val codePoint = when {
                        entity.startsWith("#x", true) -> entity.substring(2).toIntOrNull(16)
                        entity.startsWith('#') -> entity.substring(1).toIntOrNull()
                        else -> null
                    }
                    codePoint?.takeIf(Character::isValidCodePoint)
                        ?.let(Character::toChars)
                        ?.concatToString()
                        ?: match.value
                }
            }
        }

    private val ENTITY = Regex("""&(#x?[0-9A-Fa-f]+|amp|lt|gt|quot|apos);""")
}
