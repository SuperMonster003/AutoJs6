package org.autojs.plugin.packagearchive

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Minimal, bounded reader for bundletool's toc.pb.
 *
 * Only persistent split or standalone APKs and install-time asset slices are returned. Instant,
 * system, APEX, archived, on-demand, and fast-follow artifacts are deliberately outside this
 * install plan.
 */
object BundletoolTocDecoder {

    data class Limits(
        val maxInputBytes: Int = PackageInspectionLimits.TOC_BYTES,
        val maxZipEntries: Int = PackageInspectionLimits.ARCHIVE_ENTRIES,
        val maxFields: Int = 4_000_000,
        val maxVariants: Int = 2_048,
        val maxModules: Int = 8_192,
        val maxApks: Int = PackageInspectionLimits.ARCHIVE_ENTRIES,
        val maxTargetingValues: Int = 4_096,
        val maxDecodedStringChars: Int = PackageInspectionLimits.TOC_BYTES,
        val maxSingleStringChars: Int = 4 * 1024,
    )

    data class Selection(
        val packageName: String?,
        val variantNumber: Long,
        val modules: List<SelectedModule>,
    ) {
        val apkPaths: List<String>
            get() = modules.flatMap { module -> module.apks.map(SelectedApk::path) }
    }

    data class SelectedModule(
        val name: String,
        val apks: List<SelectedApk>,
        val assetModule: Boolean = false,
    )

    data class SelectedApk(
        val path: String,
        val splitId: String?,
        val master: Boolean,
    )

    private val displayLimits = Limits()

    fun select(apksFile: File, device: PackageDeviceSpec): Selection =
        select(apksFile, device, displayLimits)

    fun select(
        apksFile: File,
        device: PackageDeviceSpec,
        limits: Limits,
    ): Selection {
        if (!apksFile.isFile) throw IOException("APKS file does not exist")
        validateLimits(limits)
        return ZipFile(apksFile).use { zip ->
            var entryCount = 0
            var tocEntry: java.util.zip.ZipEntry? = null
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > limits.maxZipEntries) {
                    throw IOException("APKS entry count exceeds the toc inspection limit")
                }
                if (!entry.isDirectory && entry.name == TOC_ENTRY) {
                    if (tocEntry != null) throw IOException("APKS contains duplicate toc.pb entries")
                    tocEntry = entry
                }
            }
            val entry = tocEntry ?: throw IOException("APKS toc.pb is missing")
            if (entry.size > limits.maxInputBytes) {
                throw IOException("APKS toc.pb exceeds the inspection size limit")
            }
            val bytes = zip.getInputStream(entry).use { input ->
                input.readBounded(limits.maxInputBytes)
            }
            select(bytes, device, limits)
        }
    }

    fun select(
        tocBytes: ByteArray,
        device: PackageDeviceSpec,
        limits: Limits = displayLimits,
    ): Selection {
        if (tocBytes.isEmpty()) throw malformed("toc.pb is empty")
        if (tocBytes.size > limits.maxInputBytes) {
            throw IOException("APKS toc.pb exceeds the inspection size limit")
        }
        validateLimits(limits)
        return TocParser(tocBytes, limits).parse().select(device)
    }

    private class TocParser(
        private val bytes: ByteArray,
        private val limits: Limits,
    ) {

        private var fieldCount = 0
        private var variantCount = 0
        private var moduleCount = 0
        private var apkCount = 0
        private var targetingValueCount = 0
        private var decodedStringChars = 0
        private var unsupportedTargetingFieldCount = 0

        fun parse(): Toc {
            var packageName: String? = null
            var deliverySemantics = DeliverySemantics.UNKNOWN
            var bundletoolSeen = false
            var unsupportedDefaultTargeting = false
            val variants = ArrayList<Variant>()
            val assetModules = ArrayList<Module>()
            val reader = reader(0, bytes.size)
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    BUILD_APKS_VARIANT_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        variantCount++
                        if (variantCount > limits.maxVariants) {
                            throw malformed("variant count exceeds the inspection limit")
                        }
                        variants += readVariant(reader.readMessage())
                    }
                    BUILD_APKS_BUNDLETOOL_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (bundletoolSeen) {
                            throw malformed("BuildApksResult contains duplicate bundletool metadata")
                        }
                        bundletoolSeen = true
                        deliverySemantics = readBundletoolDeliverySemantics(reader.readMessage())
                    }
                    BUILD_APKS_ASSET_SLICE_SET_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        moduleCount++
                        if (moduleCount > limits.maxModules) {
                            throw malformed("module count exceeds the inspection limit")
                        }
                        assetModules += readAssetSliceSet(reader.readMessage())
                    }
                    BUILD_APKS_PACKAGE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        packageName = reader.readString("package name")
                    }
                    BUILD_APKS_DEFAULT_TARGETING_VALUE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        unsupportedDefaultTargeting =
                            unsupportedDefaultTargeting || !reader.readMessage().isAtEnd()
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (variants.isEmpty()) throw malformed("toc.pb contains no variants")
            return Toc(
                packageName = packageName?.takeIf(String::isNotBlank),
                deliverySemantics = deliverySemantics,
                unsupportedDefaultTargeting = unsupportedDefaultTargeting,
                variants = variants,
                assetModules = assetModules,
            )
        }

        private fun readBundletoolDeliverySemantics(reader: Reader): DeliverySemantics {
            var version: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    BUNDLETOOL_VERSION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        version = reader.readString("bundletool version")
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return DeliverySemantics.fromVersion(version)
        }

        private fun readVariant(reader: Reader): Variant {
            var targeting = Targeting()
            var targetingSeen = false
            var variantNumber = 0L
            val modules = ArrayList<Module>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    VARIANT_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (targetingSeen) {
                            throw malformed("variant contains duplicate targeting messages")
                        }
                        targetingSeen = true
                        targeting = readVariantTargeting(reader.readMessage())
                    }
                    VARIANT_APK_SET_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        moduleCount++
                        if (moduleCount > limits.maxModules) {
                            throw malformed("module count exceeds the inspection limit")
                        }
                        modules += readApkSet(reader.readMessage())
                    }
                    VARIANT_NUMBER_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        val number = reader.readVarint()
                        if (number !in 0L..UINT32_MAX) {
                            throw malformed("variant number is outside uint32 range")
                        }
                        variantNumber = number
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            if (modules.isEmpty()) throw malformed("variant contains no APK sets")
            return Variant(variantNumber, targeting, modules)
        }

        private fun readApkSet(reader: Reader): Module {
            var metadata: ModuleMetadata? = null
            var metadataSeen = false
            val apks = ArrayList<ApkDescription>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    APK_SET_MODULE_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (metadataSeen) {
                            throw malformed("APK set contains duplicate module metadata")
                        }
                        metadataSeen = true
                        metadata = readModuleMetadata(reader.readMessage())
                    }
                    APK_SET_APK_DESCRIPTION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        apkCount++
                        if (apkCount > limits.maxApks) {
                            throw malformed("APK description count exceeds the inspection limit")
                        }
                        apks += readApkDescription(reader.readMessage())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val moduleMetadata = metadata ?: throw malformed("APK set module metadata is missing")
            if (apks.isEmpty()) throw malformed("APK set ${moduleMetadata.name} contains no APKs")
            return Module(moduleMetadata, apks)
        }

        private fun readAssetSliceSet(reader: Reader): Module {
            var metadata: ModuleMetadata? = null
            var metadataSeen = false
            val apks = ArrayList<ApkDescription>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    ASSET_SLICE_SET_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (metadataSeen) {
                            throw malformed("asset slice set contains duplicate module metadata")
                        }
                        metadataSeen = true
                        metadata = readAssetModuleMetadata(reader.readMessage())
                    }
                    ASSET_SLICE_SET_DESCRIPTION_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        apkCount++
                        if (apkCount > limits.maxApks) {
                            throw malformed("APK description count exceeds the inspection limit")
                        }
                        apks += readApkDescription(reader.readMessage())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val moduleMetadata = metadata
                ?: throw malformed("asset slice module metadata is missing")
            if (apks.isEmpty()) {
                throw malformed("asset slice set ${moduleMetadata.name} contains no slices")
            }
            return Module(moduleMetadata, apks)
        }

        private fun readModuleMetadata(reader: Reader): ModuleMetadata {
            var name: String? = null
            var legacyOnDemand = false
            var deliveryType = DELIVERY_UNKNOWN
            var targeting = Targeting()
            var targetingSeen = false
            val dependencies = ArrayList<String>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    MODULE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("module name")
                    }
                    MODULE_ON_DEMAND_DEPRECATED_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        legacyOnDemand = reader.readVarint() != 0L
                    }
                    MODULE_DEPENDENCY_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        dependencies += reader.readString("module dependency")
                    }
                    MODULE_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (targetingSeen) {
                            throw malformed("module metadata contains duplicate targeting messages")
                        }
                        targetingSeen = true
                        targeting = readModuleTargeting(reader.readMessage())
                    }
                    MODULE_DELIVERY_TYPE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        deliveryType = reader.readVarint().toInt()
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val moduleName = name?.takeIf(String::isNotBlank)
                ?: throw malformed("module name is missing")
            return ModuleMetadata(
                name = moduleName,
                deliveryType = deliveryType,
                legacyOnDemand = legacyOnDemand,
                targeting = targeting,
                dependencies = dependencies.distinct(),
            )
        }

        private fun readAssetModuleMetadata(reader: Reader): ModuleMetadata {
            var name: String? = null
            var legacyOnDemand = false
            var deliveryType = DELIVERY_UNKNOWN
            var targeting = Targeting()
            var targetingSeen = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    ASSET_MODULE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        name = reader.readString("asset module name")
                    }
                    ASSET_MODULE_ON_DEMAND_DEPRECATED_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        legacyOnDemand = reader.readVarint() != 0L
                    }
                    ASSET_MODULE_DELIVERY_TYPE_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        deliveryType = reader.readVarint().toInt()
                    }
                    ASSET_MODULE_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (targetingSeen) {
                            throw malformed(
                                "asset module metadata contains duplicate targeting messages",
                            )
                        }
                        targetingSeen = true
                        targeting = readAssetModuleTargeting(reader.readMessage())
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return ModuleMetadata(
                name = name?.takeIf(String::isNotBlank)
                    ?: throw malformed("asset module name is missing"),
                deliveryType = deliveryType,
                legacyOnDemand = legacyOnDemand,
                targeting = targeting,
                dependencies = emptyList(),
            )
        }

        private fun readApkDescription(reader: Reader): ApkDescription {
            var targeting = Targeting()
            var targetingSeen = false
            var path: String? = null
            var kind = ApkKind.NONE
            var splitId: String? = null
            var master = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    APK_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        if (targetingSeen) {
                            throw malformed("APK description contains duplicate targeting messages")
                        }
                        targetingSeen = true
                        targeting = readApkTargeting(reader.readMessage())
                    }
                    APK_PATH_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        path = reader.readString("APK path")
                    }
                    APK_SPLIT_METADATA_FIELD, APK_INSTANT_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val metadata = readSplitMetadata(reader.readMessage())
                        setApkKind(
                            current = kind,
                            replacement = if (tag.fieldNumber == APK_SPLIT_METADATA_FIELD) {
                                ApkKind.SPLIT
                            } else {
                                ApkKind.INSTANT
                            },
                        )
                        kind = if (tag.fieldNumber == APK_SPLIT_METADATA_FIELD) {
                            ApkKind.SPLIT
                        } else {
                            ApkKind.INSTANT
                        }
                        splitId = metadata.first
                        master = metadata.second
                    }
                    APK_STANDALONE_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        setApkKind(kind, ApkKind.STANDALONE)
                        kind = ApkKind.STANDALONE
                        splitId = readStandaloneMetadata(reader.readMessage())
                        master = splitId == null
                    }
                    APK_SYSTEM_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        setApkKind(kind, ApkKind.SYSTEM)
                        kind = ApkKind.SYSTEM
                        reader.readMessage().consume()
                    }
                    APK_ASSET_SLICE_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        setApkKind(kind, ApkKind.ASSET_SLICE)
                        kind = ApkKind.ASSET_SLICE
                        reader.readMessage().consume()
                    }
                    APK_APEX_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        setApkKind(kind, ApkKind.APEX)
                        kind = ApkKind.APEX
                        reader.readMessage().consume()
                    }
                    APK_ARCHIVED_METADATA_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        setApkKind(kind, ApkKind.ARCHIVED)
                        kind = ApkKind.ARCHIVED
                        reader.readMessage().consume()
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            val apkPath = path?.takeIf(String::isNotBlank)
                ?: throw malformed("APK description path is missing")
            val requiredExtension = when (kind) {
                ApkKind.ASSET_SLICE -> null
                ApkKind.APEX -> ".apex"
                else -> ".apk"
            }
            validateArchivePath(apkPath, requiredExtension)
            return ApkDescription(apkPath, kind, splitId, master, targeting)
        }

        private fun readSplitMetadata(reader: Reader): Pair<String?, Boolean> {
            var splitId: String? = null
            var master = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    SPLIT_ID_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        splitId = reader.readString("split ID").takeIf(String::isNotBlank)
                    }
                    SPLIT_IS_MASTER_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        master = reader.readVarint() != 0L
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return splitId to master
        }

        private fun readStandaloneMetadata(reader: Reader): String? {
            var splitId: String? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    STANDALONE_FUSED_MODULE_NAME_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        reader.readString("standalone fused module name")
                    }
                    STANDALONE_SPLIT_ID_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        splitId = reader.readString("standalone split ID")
                            .takeIf(String::isNotBlank)
                    }
                    else -> reader.skip(tag.wireType)
                }
            }
            return splitId
        }

        private fun readVariantTargeting(reader: Reader): Targeting {
            val unsupportedAtStart = unsupportedTargetingFieldCount
            var result = Targeting()
            val seenFields = HashSet<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    VARIANT_SDK_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant SDK")
                        result = result.copy(sdk = readSdkTargeting(reader.readMessage()))
                    }
                    VARIANT_ABI_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant ABI")
                        result = result.copy(abi = readAbiTargeting(reader.readMessage()))
                    }
                    VARIANT_DENSITY_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant density")
                        result = result.copy(density = readDensityTargeting(reader.readMessage()))
                    }
                    VARIANT_MULTI_ABI_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant multi-ABI")
                        result = result.copy(multiAbi = readMultiAbiTargeting(reader.readMessage()))
                    }
                    VARIANT_TEXTURE_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant texture")
                        result = result.copy(unsupported = result.unsupported || reader.readMessage().hasFields())
                    }
                    VARIANT_SDK_RUNTIME_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "variant SDK runtime")
                        val sdkRuntime = readSdkRuntimeTargeting(reader.readMessage())
                        result = result.copy(
                            requiresSdkRuntime = sdkRuntime.required,
                            unsupported = result.unsupported || sdkRuntime.unsupported,
                        )
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return result.copy(
                unsupported = result.unsupported ||
                    unsupportedTargetingFieldCount != unsupportedAtStart,
            )
        }

        private fun readApkTargeting(reader: Reader): Targeting {
            val unsupportedAtStart = unsupportedTargetingFieldCount
            var result = Targeting()
            val seenFields = HashSet<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    APK_ABI_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "APK ABI")
                        result = result.copy(abi = readAbiTargeting(reader.readMessage()))
                    }
                    APK_LANGUAGE_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "APK language")
                        result = result.copy(language = readLanguageTargeting(reader.readMessage()))
                    }
                    APK_DENSITY_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "APK density")
                        result = result.copy(density = readDensityTargeting(reader.readMessage()))
                    }
                    APK_SDK_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "APK SDK")
                        result = result.copy(sdk = readSdkTargeting(reader.readMessage()))
                    }
                    APK_MULTI_ABI_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(seenFields, tag, "APK multi-ABI")
                        result = result.copy(multiAbi = readMultiAbiTargeting(reader.readMessage()))
                    }
                    APK_TEXTURE_TARGETING_FIELD,
                    APK_SANITIZER_TARGETING_FIELD,
                    APK_DEVICE_TIER_TARGETING_FIELD,
                    APK_COUNTRY_SET_TARGETING_FIELD,
                    APK_DEVICE_GROUP_TARGETING_FIELD,
                    -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(
                            seenFields,
                            tag,
                            "APK unsupported dimension",
                        )
                        result = result.copy(unsupported = result.unsupported || reader.readMessage().hasFields())
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return result.copy(
                unsupported = result.unsupported ||
                    unsupportedTargetingFieldCount != unsupportedAtStart,
            )
        }

        private fun readModuleTargeting(reader: Reader): Targeting {
            val unsupportedAtStart = unsupportedTargetingFieldCount
            var result = Targeting()
            val seenSingularFields = HashSet<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    MODULE_SDK_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(
                            seenSingularFields,
                            tag,
                            "module SDK",
                        )
                        result = result.copy(sdk = readSdkTargeting(reader.readMessage()))
                    }
                    MODULE_DEVICE_FEATURE_TARGETING_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        result = result.copy(unsupported = result.unsupported || reader.readMessage().hasFields())
                    }
                    MODULE_USER_COUNTRIES_TARGETING_FIELD,
                    MODULE_DEVICE_GROUP_TARGETING_FIELD,
                    -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(
                            seenSingularFields,
                            tag,
                            "module unsupported dimension",
                        )
                        result = result.copy(unsupported = result.unsupported || reader.readMessage().hasFields())
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return result.copy(
                unsupported = result.unsupported ||
                    unsupportedTargetingFieldCount != unsupportedAtStart,
            )
        }

        private fun readAssetModuleTargeting(reader: Reader): Targeting {
            val unsupportedAtStart = unsupportedTargetingFieldCount
            var unsupported = false
            val seenFields = HashSet<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    ASSET_MODULE_USER_COUNTRIES_TARGETING_FIELD,
                    ASSET_MODULE_DEVICE_GROUP_TARGETING_FIELD,
                    -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        requireSingularTargetingField(
                            seenFields,
                            tag,
                            "asset module dimension",
                        )
                        unsupported = unsupported || reader.readMessage().hasFields()
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return Targeting(
                unsupported = unsupported ||
                    unsupportedTargetingFieldCount != unsupportedAtStart,
            )
        }

        private fun readSdkTargeting(reader: Reader): SdkTargeting {
            val values = ArrayList<Int>()
            val alternatives = ArrayList<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TARGETING_VALUE_FIELD, TARGETING_ALTERNATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val value = readSdkVersion(reader.readMessage())
                        addTargetingValue(if (tag.fieldNumber == TARGETING_VALUE_FIELD) values else alternatives)
                        if (tag.fieldNumber == TARGETING_VALUE_FIELD) {
                            values += value
                        } else {
                            alternatives += value
                        }
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            if (values.size > 1) throw malformed("SDK targeting contains multiple values")
            return SdkTargeting(values, alternatives)
        }

        private fun readSdkVersion(reader: Reader): Int {
            var minimum = 0
            var minimumSeen = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == SDK_VERSION_MIN_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    if (minimumSeen) {
                        throw malformed("SDK version contains duplicate minimum messages")
                    }
                    minimumSeen = true
                    minimum = readInt32Wrapper(reader.readMessage())
                } else {
                    skipUnsupportedTargetingField(reader, tag)
                }
            }
            if (minimum >= PREVIEW_SDK_VERSION) {
                unsupportedTargetingFieldCount++
            }
            return minimum
        }

        private fun readInt32Wrapper(reader: Reader): Int {
            var value = 0
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == WRAPPER_VALUE_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    val decoded = reader.readVarint()
                    if (decoded !in 0L..Int.MAX_VALUE.toLong()) {
                        throw malformed("SDK version is outside the supported integer range")
                    }
                    value = decoded.toInt()
                } else {
                    skipUnsupportedTargetingField(reader, tag)
                }
            }
            if (value < 0) throw malformed("SDK version is negative")
            return value
        }

        private fun readAbiTargeting(reader: Reader): ValueAlternatives<Int> {
            val values = ArrayList<Int>()
            val alternatives = ArrayList<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TARGETING_VALUE_FIELD, TARGETING_ALTERNATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val alias = readAbi(reader.readMessage())
                        val target = if (tag.fieldNumber == TARGETING_VALUE_FIELD) values else alternatives
                        addTargetingValue(target)
                        target += alias
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            ensureDisjoint(values, alternatives, "ABI")
            return ValueAlternatives(values, alternatives)
        }

        private fun readAbi(reader: Reader): Int {
            var alias = ABI_UNSPECIFIED
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == ABI_ALIAS_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    val decoded = reader.readVarint()
                    if (decoded !in ABI_UNSPECIFIED.toLong()..ABI_RISCV64.toLong()) {
                        unsupportedTargetingFieldCount++
                    } else {
                        alias = decoded.toInt()
                    }
                } else {
                    skipUnsupportedTargetingField(reader, tag)
                }
            }
            return alias
        }

        private fun readDensityTargeting(reader: Reader): ValueAlternatives<Int> {
            val values = ArrayList<Int>()
            val alternatives = ArrayList<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TARGETING_VALUE_FIELD, TARGETING_ALTERNATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val density = readDensity(reader.readMessage())
                        val target = if (tag.fieldNumber == TARGETING_VALUE_FIELD) values else alternatives
                        addTargetingValue(target)
                        target += density
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return ValueAlternatives(values, alternatives)
        }

        private fun readDensity(reader: Reader): Int {
            var density: Int? = null
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                val decoded = when (tag.fieldNumber) {
                    DENSITY_ALIAS_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        val alias = reader.readVarint()
                        if (alias !in 1L..8L) {
                            throw malformed("unknown density alias $alias")
                        }
                        densityAliasToDpi(alias.toInt())
                    }
                    DENSITY_DPI_FIELD -> {
                        requireWire(tag, WIRE_VARINT)
                        val value = reader.readVarint()
                        if (value !in 1L..Int.MAX_VALUE.toLong()) {
                            throw malformed("screen density DPI is invalid")
                        }
                        value.toInt()
                    }
                    else -> {
                        skipUnsupportedTargetingField(reader, tag)
                        continue
                    }
                }
                if (density != null) throw malformed("screen density contains multiple values")
                density = decoded
            }
            return density ?: throw malformed("screen density has no value")
        }

        private fun readLanguageTargeting(reader: Reader): ValueAlternatives<String> {
            val values = ArrayList<String>()
            val alternatives = ArrayList<String>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TARGETING_VALUE_FIELD, TARGETING_ALTERNATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val target = if (tag.fieldNumber == TARGETING_VALUE_FIELD) values else alternatives
                        addTargetingValue(target)
                        target += reader.readString("language").lowercase(Locale.ROOT)
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return ValueAlternatives(values, alternatives)
        }

        private fun readMultiAbiTargeting(reader: Reader): ValueAlternatives<Set<Int>> {
            val values = ArrayList<Set<Int>>()
            val alternatives = ArrayList<Set<Int>>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                when (tag.fieldNumber) {
                    TARGETING_VALUE_FIELD, TARGETING_ALTERNATIVE_FIELD -> {
                        requireWire(tag, WIRE_LENGTH_DELIMITED)
                        val target = if (tag.fieldNumber == TARGETING_VALUE_FIELD) values else alternatives
                        addTargetingValue(target)
                        target += readMultiAbi(reader.readMessage())
                    }
                    else -> skipUnsupportedTargetingField(reader, tag)
                }
            }
            return ValueAlternatives(values, alternatives)
        }

        private fun readMultiAbi(reader: Reader): Set<Int> {
            val values = LinkedHashSet<Int>()
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == MULTI_ABI_ABI_FIELD) {
                    requireWire(tag, WIRE_LENGTH_DELIMITED)
                    addTargetingValue(values)
                    values += readAbi(reader.readMessage())
                } else {
                    skipUnsupportedTargetingField(reader, tag)
                }
            }
            return values
        }

        private fun readSdkRuntimeTargeting(reader: Reader): SdkRuntimeTargeting {
            var required = false
            var unsupported = false
            while (!reader.isAtEnd()) {
                val tag = reader.readTag()
                if (tag.fieldNumber == SDK_RUNTIME_REQUIRED_FIELD) {
                    requireWire(tag, WIRE_VARINT)
                    required = reader.readVarint() != 0L
                } else {
                    unsupported = skipUnsupportedTargetingField(reader, tag) || unsupported
                }
            }
            return SdkRuntimeTargeting(required, unsupported)
        }

        private fun setApkKind(current: ApkKind, replacement: ApkKind) {
            if (current != ApkKind.NONE) {
                throw malformed("APK description contains multiple metadata kinds")
            }
            if (replacement == ApkKind.NONE) error("Invalid replacement kind")
        }

        private fun validateArchivePath(path: String, requiredExtension: String?) {
            if (
                path.length > limits.maxSingleStringChars ||
                path.isBlank() ||
                '\u0000' in path ||
                '\\' in path ||
                path.startsWith('/') ||
                Regex("^[A-Za-z]:").containsMatchIn(path) ||
                path.split('/').any { it == ".." } ||
                requiredExtension != null &&
                !path.endsWith(requiredExtension, ignoreCase = true)
            ) {
                throw malformed("unsafe artifact path in toc.pb")
            }
        }

        private fun <T> addTargetingValue(target: Collection<T>) {
            targetingValueCount++
            if (targetingValueCount > limits.maxTargetingValues) {
                throw malformed("targeting value count exceeds the inspection limit")
            }
            if (target.size >= limits.maxTargetingValues) {
                throw malformed("targeting list exceeds the inspection limit")
            }
        }

        private fun <T> ensureDisjoint(values: List<T>, alternatives: List<T>, label: String) {
            if (values.any { it in alternatives }) {
                throw malformed("$label targeting values overlap alternatives")
            }
        }

        private fun requireSingularTargetingField(
            seenFields: MutableSet<Int>,
            tag: Tag,
            label: String,
        ) {
            if (!seenFields.add(tag.fieldNumber)) {
                throw malformed("$label targeting field is repeated")
            }
        }

        private fun skipUnsupportedTargetingField(reader: Reader, tag: Tag): Boolean {
            val nonEmpty = if (tag.wireType == WIRE_LENGTH_DELIMITED) {
                !reader.readMessage().isAtEnd()
            } else {
                reader.skip(tag.wireType)
                true
            }
            if (nonEmpty) unsupportedTargetingFieldCount++
            return nonEmpty
        }

        private fun reader(start: Int, end: Int): Reader = Reader(bytes, start, end)

        private fun requireWire(tag: Tag, expected: Int) {
            if (tag.wireType != expected) {
                throw malformed(
                    "field ${tag.fieldNumber} has wire type ${tag.wireType}, expected $expected",
                )
            }
        }

        private inner class Reader(
            private val source: ByteArray,
            start: Int,
            private val end: Int,
        ) {

            private var position = start

            init {
                if (start < 0 || end < start || end > source.size) {
                    throw malformed("message bounds exceed toc.pb")
                }
            }

            fun isAtEnd(): Boolean = position == end

            fun hasFields(): Boolean {
                val present = !isAtEnd()
                consume()
                return present
            }

            fun consume() {
                while (!isAtEnd()) {
                    val tag = readTag()
                    skip(tag.wireType)
                }
            }

            fun readTag(): Tag {
                fieldCount++
                if (fieldCount > limits.maxFields) {
                    throw malformed("protobuf field count exceeds the inspection limit")
                }
                val raw = readVarint()
                if (raw <= 0L || raw > Int.MAX_VALUE) throw malformed("invalid protobuf field tag")
                val value = raw.toInt()
                val fieldNumber = value ushr 3
                if (fieldNumber == 0) throw malformed("invalid protobuf field number")
                return Tag(fieldNumber, value and 0x7)
            }

            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                repeat(MAX_VARINT_BYTES) {
                    val byte = readByte()
                    if (shift == 63 && byte and 0xFE != 0) {
                        throw malformed("protobuf varint overflows")
                    }
                    result = result or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
                throw malformed("unterminated protobuf varint")
            }

            fun readString(label: String): String {
                val bounds = readBounds()
                val value = try {
                    Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(source, bounds.first, bounds.second - bounds.first))
                        .toString()
                } catch (_: Exception) {
                    throw malformed("$label is not valid UTF-8")
                }
                if (value.length > limits.maxSingleStringChars) {
                    throw malformed("$label exceeds the single-string inspection limit")
                }
                if (value.length > limits.maxDecodedStringChars - decodedStringChars) {
                    throw malformed("decoded strings exceed the inspection limit")
                }
                decodedStringChars += value.length
                return value
            }

            fun readMessage(): Reader {
                val bounds = readBounds()
                return reader(bounds.first, bounds.second)
            }

            fun skip(wireType: Int) {
                when (wireType) {
                    WIRE_VARINT -> readVarint()
                    WIRE_FIXED64 -> skipBytes(Long.SIZE_BYTES)
                    WIRE_LENGTH_DELIMITED -> readBounds()
                    WIRE_FIXED32 -> skipBytes(Int.SIZE_BYTES)
                    else -> throw malformed("unsupported protobuf wire type $wireType")
                }
            }

            private fun readBounds(): Pair<Int, Int> {
                val rawLength = readVarint()
                if (rawLength < 0L || rawLength > Int.MAX_VALUE) {
                    throw malformed("invalid length-delimited field size")
                }
                val length = rawLength.toInt()
                if (length > end - position) {
                    throw EOFException("Unexpected end of APKS toc.pb field")
                }
                val start = position
                position += length
                return start to position
            }

            private fun readByte(): Int {
                if (position >= end) throw EOFException("Unexpected end of APKS toc.pb")
                return source[position++].toInt() and 0xFF
            }

            private fun skipBytes(count: Int) {
                if (count < 0 || count > end - position) {
                    throw EOFException("Unexpected end of APKS toc.pb")
                }
                position += count
            }
        }
    }

    private data class Toc(
        val packageName: String?,
        val deliverySemantics: DeliverySemantics,
        val unsupportedDefaultTargeting: Boolean,
        val variants: List<Variant>,
        val assetModules: List<Module>,
    ) {
        fun select(device: PackageDeviceSpec): Selection {
            if (unsupportedDefaultTargeting) {
                throw IOException(
                    "APKS default targeting values are not supported by this installer",
                )
            }
            val candidateVariants = variants.filterNot(Variant::isInstantOnly)
                .filter(Variant::hasInstallableApks)
            if (candidateVariants.isEmpty() && variants.any(Variant::hasApexArtifacts)) {
                throw IOException("APKS contains APEX artifacts, which this installer does not support")
            }
            if (candidateVariants.any { it.targeting.unsupported }) {
                throw IOException(
                    "APKS variant uses targeting that PackageDeviceSpec cannot evaluate",
                )
            }
            val matchingVariants = candidateVariants
                .filter { it.targeting.matches(device, TargetLevel.VARIANT) }
            val persistent = if (device.supportsSdkRuntime()) {
                matchingVariants.filter { it.targeting.requiresSdkRuntime }
                    .takeIf(List<Variant>::isNotEmpty)
                    ?: matchingVariants.filterNot { it.targeting.requiresSdkRuntime }
            } else {
                matchingVariants.filterNot { it.targeting.requiresSdkRuntime }
            }
            if (persistent.isEmpty()) {
                throw IOException("APKS toc.pb has no persistent APK variant matching this device")
            }
            val highestNumber = persistent.maxOf(Variant::number)
            val preferred = persistent.filter { it.number == highestNumber }
            if (preferred.size != 1) {
                throw IOException("APKS toc.pb matches multiple persistent APK variants")
            }
            val variant = preferred.single()
            val installTimeModules = variant.modules.filter {
                it.metadata.isInstallTime(deliverySemantics)
            }
            installTimeModules.forEach { module ->
                if (module.metadata.targeting.unsupported) {
                    throw IOException(
                        "APKS module ${module.metadata.name} uses unsupported conditional targeting",
                    )
                }
            }
            val eligibleModules = installTimeModules.filter { module ->
                module.metadata.targeting.matches(device, TargetLevel.MODULE)
            }
            eligibleModules.forEach { module ->
                module.apks.filter {
                    it.kind == ApkKind.SPLIT || it.kind == ApkKind.STANDALONE
                }.forEach { apk ->
                    if (apk.targeting.unsupported) {
                        throw IOException(
                            "APKS artifact ${apk.path} uses targeting that PackageDeviceSpec cannot evaluate",
                        )
                    }
                }
            }
            val moduleNames = eligibleModules.mapTo(HashSet()) { it.metadata.name }
            eligibleModules.forEach { module ->
                val missing = module.metadata.dependencies.filterNot { it in moduleNames }
                if (missing.isNotEmpty()) {
                    throw IOException(
                        "APKS install-time module ${module.metadata.name} has missing dependencies: " +
                            missing.joinToString(),
                    )
                }
            }
            val hasSplitApks = eligibleModules.any { module ->
                module.apks.any { it.kind == ApkKind.SPLIT }
            }
            val hasStandaloneApks = eligibleModules.any { module ->
                module.apks.any { it.kind == ApkKind.STANDALONE }
            }
            if (hasSplitApks && hasStandaloneApks) {
                throw IOException("APKS variant mixes split and standalone APK artifacts")
            }
            val selectedModules = when {
                hasStandaloneApks -> selectStandaloneModules(eligibleModules, device)
                hasSplitApks -> selectSplitModules(eligibleModules, moduleNames, device)
                else -> throw IOException(
                    "APKS toc.pb has no matching persistent APK artifacts",
                )
            }.toMutableList()
            assetModules.filter { it.metadata.isAssetInstallTime() }.forEach { module ->
                if (module.metadata.targeting.unsupported) {
                    throw IOException(
                        "APKS asset module ${module.metadata.name} uses unsupported conditional targeting",
                    )
                }
                val assetSlices = module.apks.filter { it.kind == ApkKind.ASSET_SLICE }
                assetSlices.forEach { apk ->
                    if (apk.targeting.unsupported) {
                        throw IOException(
                            "APKS asset slice ${apk.path} uses targeting that PackageDeviceSpec cannot evaluate",
                        )
                    }
                }
                val selectedSlices = assetSlices.asSequence()
                    .filter { it.targeting.matches(device, TargetLevel.APK) }
                    .map { apk -> SelectedApk(apk.path, apk.splitId, apk.master) }
                    .distinctBy(SelectedApk::path)
                    .sortedBy(SelectedApk::path)
                    .toList()
                if (selectedSlices.isEmpty()) {
                    throw IOException(
                        "APKS install-time asset module ${module.metadata.name} has no matching slices",
                    )
                }
                selectedModules += SelectedModule(
                    name = module.metadata.name,
                    apks = selectedSlices,
                    assetModule = true,
                )
            }
            selectedModules.sortWith(
                compareBy<SelectedModule> { it.assetModule }
                    .thenBy { it.name != "base" }
                    .thenBy { it.name },
            )
            val duplicatePath = selectedModules.flatMap(SelectedModule::apks)
                .groupingBy(SelectedApk::path)
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
            if (duplicatePath != null) {
                throw IOException("APKS toc.pb selects duplicate APK path ${duplicatePath.key}")
            }
            return Selection(packageName, variant.number, selectedModules)
        }

        private fun selectSplitModules(
            eligibleModules: List<Module>,
            moduleNames: Set<String>,
            device: PackageDeviceSpec,
        ): List<SelectedModule> {
            if ("base" !in moduleNames) {
                throw IOException("APKS toc.pb has no matching install-time base module")
            }
            return eligibleModules.map { module ->
                val apks = module.apks.asSequence()
                    .filter { it.kind == ApkKind.SPLIT }
                    .filter { it.targeting.matches(device, TargetLevel.APK) }
                    .map { apk -> SelectedApk(apk.path, apk.splitId, apk.master) }
                    .distinctBy(SelectedApk::path)
                    .sortedWith(compareByDescending<SelectedApk> { it.master }.thenBy { it.path })
                    .toList()
                if (apks.isEmpty()) {
                    throw IOException(
                        "APKS module ${module.metadata.name} has no split APK matching this device",
                    )
                }
                if (apks.count(SelectedApk::master) != 1) {
                    throw IOException(
                        "APKS module ${module.metadata.name} must select exactly one master split",
                    )
                }
                SelectedModule(module.metadata.name, apks)
            }
        }

        private fun selectStandaloneModules(
            eligibleModules: List<Module>,
            device: PackageDeviceSpec,
        ): List<SelectedModule> {
            val selected = eligibleModules.mapNotNull { module ->
                val standaloneDescriptions = module.apks.filter {
                    it.kind == ApkKind.STANDALONE
                }
                if (standaloneDescriptions.isEmpty()) return@mapNotNull null
                val apks = standaloneDescriptions.asSequence()
                    .filter { it.targeting.matches(device, TargetLevel.APK) }
                    .map { apk ->
                        SelectedApk(
                            path = apk.path,
                            splitId = apk.splitId,
                            master = apk.splitId == null,
                        )
                    }
                    .distinctBy(SelectedApk::path)
                    .sortedWith(compareByDescending<SelectedApk> { it.master }.thenBy { it.path })
                    .toList()
                if (apks.isEmpty()) {
                    throw IOException(
                        "APKS module ${module.metadata.name} has no standalone APK matching this device",
                    )
                }
                SelectedModule(module.metadata.name, apks)
            }
            val allApks = selected.flatMap(SelectedModule::apks)
            if (allApks.count(SelectedApk::master) != 1) {
                throw IOException(
                    "APKS standalone variant must select exactly one APK without a split ID",
                )
            }
            val duplicateSplitId = allApks.asSequence()
                .mapNotNull(SelectedApk::splitId)
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
            if (duplicateSplitId != null) {
                throw IOException(
                    "APKS standalone variant selects duplicate split ID ${duplicateSplitId.key}",
                )
            }
            return selected
        }
    }

    private data class Variant(
        val number: Long,
        val targeting: Targeting,
        val modules: List<Module>,
    ) {
        fun isInstantOnly(): Boolean {
            val apks = modules.flatMap(Module::apks)
            return apks.isNotEmpty() && apks.all { it.kind == ApkKind.INSTANT }
        }

        fun hasInstallableApks(): Boolean = modules.any { module ->
            module.apks.any {
                it.kind == ApkKind.SPLIT || it.kind == ApkKind.STANDALONE
            }
        }

        fun hasApexArtifacts(): Boolean = modules.any { module ->
            module.apks.any { it.kind == ApkKind.APEX }
        }
    }

    private data class Module(
        val metadata: ModuleMetadata,
        val apks: List<ApkDescription>,
    )

    private data class ModuleMetadata(
        val name: String,
        val deliveryType: Int,
        val legacyOnDemand: Boolean,
        val targeting: Targeting,
        val dependencies: List<String>,
    ) {
        fun isInstallTime(deliverySemantics: DeliverySemantics): Boolean =
            when (deliverySemantics) {
                DeliverySemantics.LEGACY -> !legacyOnDemand
                DeliverySemantics.MODERN -> deliveryType == DELIVERY_INSTALL_TIME
                DeliverySemantics.UNKNOWN -> isAssetInstallTime()
            }

        fun isAssetInstallTime(): Boolean = when (deliveryType) {
            DELIVERY_INSTALL_TIME -> true
            DELIVERY_ON_DEMAND, DELIVERY_FAST_FOLLOW -> false
            DELIVERY_UNKNOWN -> !legacyOnDemand
            else -> false
        }
    }

    private data class ApkDescription(
        val path: String,
        val kind: ApkKind,
        val splitId: String?,
        val master: Boolean,
        val targeting: Targeting,
    )

    private enum class ApkKind {
        NONE,
        SPLIT,
        STANDALONE,
        INSTANT,
        SYSTEM,
        ASSET_SLICE,
        APEX,
        ARCHIVED,
    }

    private enum class TargetLevel {
        VARIANT,
        MODULE,
        APK,
    }

    private data class Targeting(
        val sdk: SdkTargeting = SdkTargeting(),
        val abi: ValueAlternatives<Int> = ValueAlternatives(),
        val density: ValueAlternatives<Int> = ValueAlternatives(),
        val language: ValueAlternatives<String> = ValueAlternatives(),
        val multiAbi: ValueAlternatives<Set<Int>> = ValueAlternatives(),
        val requiresSdkRuntime: Boolean = false,
        val unsupported: Boolean = false,
    ) {
        fun matches(device: PackageDeviceSpec, level: TargetLevel): Boolean {
            if (unsupported) return false
            if (requiresSdkRuntime && !device.supportsSdkRuntime()) return false
            return sdk.matches(device.sdk) &&
                abiMatches(abi, device.abis) &&
                densityMatches(density, device.densityDpi) &&
                multiAbiMatches(multiAbi, device.abis) &&
                (level != TargetLevel.APK || languageMatches(language, device.locales))
        }
    }

    private data class SdkTargeting(
        val values: List<Int> = emptyList(),
        val alternatives: List<Int> = emptyList(),
    ) {
        fun matches(deviceSdk: Int): Boolean {
            if (deviceSdk <= 0) return true
            val value = values.singleOrNull() ?: 0
            if (value > deviceSdk) return false
            return alternatives.none { alternative ->
                alternative <= deviceSdk && alternative > value
            }
        }
    }

    private data class ValueAlternatives<T>(
        val values: List<T> = emptyList(),
        val alternatives: List<T> = emptyList(),
    )

    private data class SdkRuntimeTargeting(
        val required: Boolean,
        val unsupported: Boolean,
    )

    private enum class DeliverySemantics {
        LEGACY,
        MODERN,
        UNKNOWN,
        ;

        companion object {
            fun fromVersion(rawVersion: String?): DeliverySemantics {
                val match = rawVersion
                    ?.let { SEMANTIC_VERSION_PREFIX.matchEntire(it.trim()) }
                    ?: return UNKNOWN
                val major = match.groupValues[1].toIntOrNull() ?: return UNKNOWN
                val minor = match.groupValues[2].toIntOrNull() ?: return UNKNOWN
                val patch = match.groupValues[3].toIntOrNull() ?: return UNKNOWN
                return if (
                    major > MODERN_DELIVERY_MAJOR ||
                    major == MODERN_DELIVERY_MAJOR && (
                        minor > MODERN_DELIVERY_MINOR ||
                            minor == MODERN_DELIVERY_MINOR && patch >= MODERN_DELIVERY_PATCH
                        )
                ) {
                    MODERN
                } else {
                    LEGACY
                }
            }
        }
    }

    private data class Tag(val fieldNumber: Int, val wireType: Int)

    private fun abiMatches(targeting: ValueAlternatives<Int>, supportedAbis: List<String>): Boolean {
        if (targeting.values.isEmpty() && targeting.alternatives.isEmpty()) return true
        if (supportedAbis.isEmpty()) return true
        val values = targeting.values.toSet()
        val alternatives = targeting.alternatives.toSet()
        supportedAbis.forEach { platformName ->
            val alias = platformAbiToAlias(platformName)
                ?: throw IOException("Unrecognized device ABI $platformName")
            if (alias in values) return true
            if (alias in alternatives) return false
        }
        return values.isEmpty()
    }

    private fun densityMatches(
        targeting: ValueAlternatives<Int>,
        deviceDensity: Int,
    ): Boolean {
        val all = targeting.values + targeting.alternatives
        if (all.isEmpty() || deviceDensity <= 0) return true
        val best = all.reduce { current, candidate ->
            if (compareDensity(candidate, current, deviceDensity) > 0) candidate else current
        }
        return best in targeting.values
    }

    private fun compareDensity(first: Int, second: Int, desired: Int): Int {
        if (first == second) return 0
        if (first == DENSITY_ANY) return 1
        if (second == DENSITY_ANY) return -1
        return if (first < second) {
            compareOrderedDensity(first, second, desired)
        } else {
            -compareOrderedDensity(second, first, desired)
        }
    }

    private fun compareOrderedDensity(lower: Int, higher: Int, desired: Int): Int {
        if (desired >= higher) return -1
        if (desired <= lower) return 1
        return if (
            ((2L * lower) - desired) * higher > desired.toLong() * desired
        ) {
            1
        } else {
            -1
        }
    }

    private fun languageMatches(
        targeting: ValueAlternatives<String>,
        deviceLocales: List<String>,
    ): Boolean {
        if (targeting.values.isEmpty() && targeting.alternatives.isEmpty()) return true
        if (deviceLocales.isEmpty()) return true
        val languages = deviceLocales.mapNotNull(::localeLanguage).toSet()
        if (targeting.values.isNotEmpty()) {
            return targeting.values.any { it.lowercase(Locale.ROOT) in languages }
        }
        val alternatives = targeting.alternatives
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        return !alternatives.containsAll(languages)
    }

    private fun PackageDeviceSpec.supportsSdkRuntime(): Boolean =
        sdk <= 0 || sdk >= SDK_RUNTIME_MIN_API

    private fun localeLanguage(tag: String): String? {
        val normalized = tag.replace("-r", "-", ignoreCase = true).replace('_', '-')
        return Locale.forLanguageTag(normalized)
            .language
            .takeIf(String::isNotBlank)
            ?.lowercase(Locale.ROOT)
    }

    private fun multiAbiMatches(
        targeting: ValueAlternatives<Set<Int>>,
        supportedAbis: List<String>,
    ): Boolean {
        if (targeting.values.isEmpty() && targeting.alternatives.isEmpty()) return true
        if (supportedAbis.isEmpty()) return true
        val device = supportedAbis.map { platformName ->
            platformAbiToAlias(platformName)
                ?: throw IOException("Unrecognized device ABI $platformName")
        }.toSet()
        val matchingValues = targeting.values.filter(device::containsAll)
        if (matchingValues.isEmpty()) return false
        return targeting.alternatives.none { alternative ->
            device.containsAll(alternative) &&
                matchingValues.all { value -> compareMultiAbi(alternative, value) > 0 }
        }
    }

    private fun compareMultiAbi(first: Set<Int>, second: Set<Int>): Int {
        val left = first.sortedDescending()
        val right = second.sortedDescending()
        val common = minOf(left.size, right.size)
        repeat(common) { index ->
            if (left[index] != right[index]) return left[index].compareTo(right[index])
        }
        return left.size.compareTo(right.size)
    }

    private fun platformAbiToAlias(value: String): Int? = when (
        value.lowercase(Locale.ROOT).replace('_', '-')
    ) {
        "armeabi" -> ABI_ARMEABI
        "armeabi-v7a" -> ABI_ARMEABI_V7A
        "arm64-v8a" -> ABI_ARM64_V8A
        "x86" -> ABI_X86
        "x86-64" -> ABI_X86_64
        "mips" -> ABI_MIPS
        "mips64" -> ABI_MIPS64
        "riscv64" -> ABI_RISCV64
        else -> null
    }

    private fun densityAliasToDpi(alias: Int): Int = when (alias) {
        1 -> DENSITY_NONE
        2 -> 120
        3 -> 160
        4 -> 213
        5 -> 240
        6 -> 320
        7 -> 480
        8 -> 640
        else -> throw malformed("unknown density alias $alias")
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read > maxBytes - total) {
                throw IOException("APKS toc.pb exceeds the inspection size limit")
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun validateLimits(limits: Limits) {
        if (
            listOf(
                limits.maxInputBytes,
                limits.maxZipEntries,
                limits.maxFields,
                limits.maxVariants,
                limits.maxModules,
                limits.maxApks,
                limits.maxTargetingValues,
                limits.maxDecodedStringChars,
                limits.maxSingleStringChars,
            ).any { it < 0 }
        ) {
            throw IllegalArgumentException("Bundletool toc decoder limits must be non-negative")
        }
    }

    private fun malformed(detail: String): IOException =
        IOException("Invalid APKS toc.pb: $detail")

    private const val TOC_ENTRY = "toc.pb"
    private const val MAX_VARINT_BYTES = 10
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    private const val BUILD_APKS_VARIANT_FIELD = 1
    private const val BUILD_APKS_BUNDLETOOL_FIELD = 2
    private const val BUILD_APKS_ASSET_SLICE_SET_FIELD = 3
    private const val BUILD_APKS_PACKAGE_NAME_FIELD = 4
    private const val BUILD_APKS_DEFAULT_TARGETING_VALUE_FIELD = 7
    private const val BUNDLETOOL_VERSION_FIELD = 2
    private const val VARIANT_TARGETING_FIELD = 1
    private const val VARIANT_APK_SET_FIELD = 2
    private const val VARIANT_NUMBER_FIELD = 3
    private const val APK_SET_MODULE_METADATA_FIELD = 1
    private const val APK_SET_APK_DESCRIPTION_FIELD = 2
    private const val ASSET_SLICE_SET_METADATA_FIELD = 1
    private const val ASSET_SLICE_SET_DESCRIPTION_FIELD = 2
    private const val MODULE_NAME_FIELD = 1
    private const val MODULE_ON_DEMAND_DEPRECATED_FIELD = 2
    private const val MODULE_DEPENDENCY_FIELD = 4
    private const val MODULE_TARGETING_FIELD = 5
    private const val MODULE_DELIVERY_TYPE_FIELD = 6
    private const val ASSET_MODULE_NAME_FIELD = 1
    private const val ASSET_MODULE_ON_DEMAND_DEPRECATED_FIELD = 2
    private const val ASSET_MODULE_DELIVERY_TYPE_FIELD = 4
    private const val ASSET_MODULE_TARGETING_FIELD = 6
    private const val APK_TARGETING_FIELD = 1
    private const val APK_PATH_FIELD = 2
    private const val APK_SPLIT_METADATA_FIELD = 3
    private const val APK_STANDALONE_METADATA_FIELD = 4
    private const val APK_INSTANT_METADATA_FIELD = 5
    private const val APK_SYSTEM_METADATA_FIELD = 6
    private const val APK_ASSET_SLICE_METADATA_FIELD = 7
    private const val APK_APEX_METADATA_FIELD = 8
    private const val APK_ARCHIVED_METADATA_FIELD = 9
    private const val SPLIT_ID_FIELD = 1
    private const val SPLIT_IS_MASTER_FIELD = 2
    private const val STANDALONE_FUSED_MODULE_NAME_FIELD = 1
    private const val STANDALONE_SPLIT_ID_FIELD = 3

    private const val VARIANT_SDK_TARGETING_FIELD = 1
    private const val VARIANT_ABI_TARGETING_FIELD = 2
    private const val VARIANT_DENSITY_TARGETING_FIELD = 3
    private const val VARIANT_MULTI_ABI_TARGETING_FIELD = 4
    private const val VARIANT_TEXTURE_TARGETING_FIELD = 5
    private const val VARIANT_SDK_RUNTIME_TARGETING_FIELD = 6
    private const val APK_ABI_TARGETING_FIELD = 1
    private const val APK_LANGUAGE_TARGETING_FIELD = 3
    private const val APK_DENSITY_TARGETING_FIELD = 4
    private const val APK_SDK_TARGETING_FIELD = 5
    private const val APK_TEXTURE_TARGETING_FIELD = 6
    private const val APK_MULTI_ABI_TARGETING_FIELD = 7
    private const val APK_SANITIZER_TARGETING_FIELD = 8
    private const val APK_DEVICE_TIER_TARGETING_FIELD = 9
    private const val APK_COUNTRY_SET_TARGETING_FIELD = 10
    private const val APK_DEVICE_GROUP_TARGETING_FIELD = 11
    private const val MODULE_SDK_TARGETING_FIELD = 1
    private const val MODULE_DEVICE_FEATURE_TARGETING_FIELD = 2
    private const val MODULE_USER_COUNTRIES_TARGETING_FIELD = 3
    private const val MODULE_DEVICE_GROUP_TARGETING_FIELD = 5
    private const val ASSET_MODULE_USER_COUNTRIES_TARGETING_FIELD = 1
    private const val ASSET_MODULE_DEVICE_GROUP_TARGETING_FIELD = 2
    private const val TARGETING_VALUE_FIELD = 1
    private const val TARGETING_ALTERNATIVE_FIELD = 2
    private const val SDK_VERSION_MIN_FIELD = 1
    private const val WRAPPER_VALUE_FIELD = 1
    private const val ABI_ALIAS_FIELD = 1
    private const val DENSITY_ALIAS_FIELD = 1
    private const val DENSITY_DPI_FIELD = 2
    private const val MULTI_ABI_ABI_FIELD = 1
    private const val SDK_RUNTIME_REQUIRED_FIELD = 1

    private const val DELIVERY_UNKNOWN = 0
    private const val DELIVERY_INSTALL_TIME = 1
    private const val DELIVERY_ON_DEMAND = 2
    private const val DELIVERY_FAST_FOLLOW = 3
    private const val MODERN_DELIVERY_MAJOR = 0
    private const val MODERN_DELIVERY_MINOR = 10
    private const val MODERN_DELIVERY_PATCH = 2
    private const val SDK_RUNTIME_MIN_API = 34
    private const val PREVIEW_SDK_VERSION = 10_000
    private const val UINT32_MAX = 0xFFFF_FFFFL

    private const val ABI_UNSPECIFIED = 0
    private const val ABI_ARMEABI = 1
    private const val ABI_ARMEABI_V7A = 2
    private const val ABI_ARM64_V8A = 3
    private const val ABI_X86 = 4
    private const val ABI_X86_64 = 5
    private const val ABI_MIPS = 6
    private const val ABI_MIPS64 = 7
    private const val ABI_RISCV64 = 8

    private const val DENSITY_DEFAULT = 0
    private const val DENSITY_ANY = 0xFFFE
    private const val DENSITY_NONE = 0xFFFF

    private val SEMANTIC_VERSION_PREFIX =
        Regex("""(\d+)\.(\d+)\.(\d+)(?:[-+].*)?""")
}
