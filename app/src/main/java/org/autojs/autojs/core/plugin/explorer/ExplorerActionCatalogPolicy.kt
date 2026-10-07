package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.util.Locale

internal object ExplorerActionCatalogPolicy {

    private val actionIdPattern = Regex("[a-z0-9][a-z0-9._-]*")
    private val resourceNamePattern = Regex("[a-z][a-z0-9_]*")
    private val activitySegmentPattern = Regex("[A-Za-z_\$][A-Za-z0-9_\$]*")
    private val extensionPattern = Regex("[a-z0-9][a-z0-9+_-]*")
    private val fileNameSuffixPattern = Regex(
        "[a-z0-9][a-z0-9+_-]*(?:\\.[a-z0-9][a-z0-9+_-]*)+",
    )
    private val relatedFileSuffixPattern = Regex(
        "\\.[a-z0-9][a-z0-9+_-]*(?:\\.[a-z0-9][a-z0-9+_-]*)*",
    )
    private val mimePartPattern = Regex("[a-z0-9][a-z0-9!#$&^_.+-]*")

    @Suppress("LongParameterList")
    fun normalizeDescriptor(
        protocolVersion: Int,
        packageName: String,
        id: String?,
        labelResourceName: String?,
        labelFallback: String?,
        activityClassName: String?,
        priority: Int,
        targetKind: Int,
        cardinality: Int? = null,
        accessMode: Int,
        placement: Int,
        presentation: Int? = null,
        mimeTypes: Collection<String>?,
        extensions: Collection<String>?,
        fileNameSuffixes: Collection<String>? = null,
        probeForActionId: String? = null,
        outputMode: Int? = null,
        outputMimeTypes: Collection<String>? = null,
        outputNameSuffix: String? = null,
        maxOutputBytes: Long? = null,
        readSiblings: Boolean? = null,
        playbackProgress: Boolean? = null,
        relatedFileSuffixes: Collection<String>? = null,
        filesystemPath: Boolean? = null,
    ): ExplorerActionDescriptor? {
        if (
            protocolVersion !in
            ExplorerActionProtocol.MIN_SUPPORTED_VERSION..ExplorerActionProtocol.MAX_SUPPORTED_VERSION
        ) {
            return null
        }
        val normalizedId = normalizeActionId(id) ?: return null
        val normalizedProbeForActionId = when {
            probeForActionId == null -> null
            protocolVersion < UNMATCHED_FILE_PROBE_PROTOCOL_VERSION -> return null
            else -> normalizeActionId(probeForActionId) ?: return null
        }
        if (normalizedProbeForActionId == normalizedId) return null
        val normalizedLabelResourceName = labelResourceName
            ?.trim()
            ?.takeIf { resourceNamePattern.matches(it) }
        val normalizedLabelFallback = sanitizeDisplayText(labelFallback, ExplorerActionProtocol.MAX_LABEL_LENGTH)
        if (normalizedLabelResourceName == null && normalizedLabelFallback == null) return null
        val normalizedActivityClassName = normalizeActivityClassName(packageName, activityClassName) ?: return null
        val normalizedCardinality = when {
            protocolVersion < MULTI_TARGET_PROTOCOL_VERSION -> {
                if (cardinality != null && cardinality != ExplorerActionValues.CARDINALITY_SINGLE) return null
                ExplorerActionValues.CARDINALITY_SINGLE
            }
            cardinality == ExplorerActionValues.CARDINALITY_SINGLE ||
                cardinality == ExplorerActionValues.CARDINALITY_MULTIPLE -> cardinality
            else -> return null
        }
        val targetKindSupported = when {
            protocolVersion < MULTI_TARGET_PROTOCOL_VERSION -> targetKind == ExplorerActionValues.TARGET_FILE
            else -> targetKind == ExplorerActionValues.TARGET_FILE ||
                targetKind == ExplorerActionValues.TARGET_DIRECTORY ||
                targetKind == ExplorerActionValues.TARGET_MIXED
        }
        if (!targetKindSupported) return null
        val accessModeSupported = when (accessMode) {
            ExplorerActionValues.ACCESS_READ_ONLY -> true
            ExplorerActionValues.ACCESS_CREATE_IN_PARENT ->
                protocolVersion >= MULTI_TARGET_PROTOCOL_VERSION
            ExplorerActionValues.ACCESS_REPLACE_TARGET ->
                protocolVersion >= TARGET_REPLACEMENT_PROTOCOL_VERSION
            ExplorerActionValues.ACCESS_MANAGE_TARGET ->
                protocolVersion >= MANAGED_TARGET_PROTOCOL_VERSION
            else -> false
        }
        if (!accessModeSupported) return null
        val placementSupported = when (placement) {
            ExplorerActionValues.PLACEMENT_OVERFLOW -> true
            ExplorerActionValues.PLACEMENT_PRIMARY ->
                protocolVersion >= PRIMARY_PLACEMENT_PROTOCOL_VERSION &&
                    normalizedCardinality == ExplorerActionValues.CARDINALITY_SINGLE
            ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR ->
                protocolVersion >= MULTI_TARGET_PROTOCOL_VERSION &&
                    normalizedCardinality == ExplorerActionValues.CARDINALITY_MULTIPLE
            else -> false
        }
        if (!placementSupported) return null
        val normalizedPresentation = when {
            protocolVersion < HOST_EXPLORER_PROTOCOL_VERSION -> {
                if (presentation != null && presentation != ExplorerActionValues.PRESENTATION_ACTIVITY) return null
                ExplorerActionValues.PRESENTATION_ACTIVITY
            }
            presentation == null || presentation == ExplorerActionValues.PRESENTATION_ACTIVITY ->
                ExplorerActionValues.PRESENTATION_ACTIVITY
            presentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER ->
                ExplorerActionValues.PRESENTATION_HOST_EXPLORER
            else -> return null
        }

        val normalizedMimeTypes = normalizeMatchers(mimeTypes, ::normalizeMimeType)
        val normalizedExtensions = normalizeMatchers(extensions, ::normalizeExtension)
        val normalizedFileNameSuffixes = normalizeMatchers(
            fileNameSuffixes,
            ::normalizeFileNameSuffix,
        )
        if (
            normalizedMimeTypes == null ||
            normalizedExtensions == null ||
            normalizedFileNameSuffixes == null
        ) {
            return null
        }
        if (
            normalizedFileNameSuffixes.isNotEmpty() &&
            (
                protocolVersion < COMPOUND_SUFFIX_PROTOCOL_VERSION ||
                    targetKind != ExplorerActionValues.TARGET_FILE
                )
        ) {
            return null
        }
        if (
            targetKind != ExplorerActionValues.TARGET_DIRECTORY &&
            normalizedMimeTypes.isEmpty() &&
            normalizedExtensions.isEmpty() &&
            normalizedFileNameSuffixes.isEmpty() &&
            normalizedProbeForActionId == null
        ) {
            return null
        }
        if (
            normalizedProbeForActionId != null &&
            (
                normalizedMimeTypes.isNotEmpty() ||
                    normalizedExtensions.isNotEmpty() ||
                    normalizedFileNameSuffixes.isNotEmpty()
                )
        ) {
            return null
        }
        val normalizedOutput = ExplorerActionOutputPolicy.normalize(
            protocolVersion = protocolVersion,
            outputMode = outputMode,
            outputMimeTypes = outputMimeTypes,
            outputNameSuffix = outputNameSuffix,
            maxOutputBytes = maxOutputBytes,
        ) ?: return null
        val replacementProtocolVersion = if (
            accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET
        ) {
            MANAGED_TARGET_PROTOCOL_VERSION
        } else {
            TARGET_REPLACEMENT_PROTOCOL_VERSION
        }
        if (
            (accessMode == ExplorerActionValues.ACCESS_REPLACE_TARGET ||
                accessMode == ExplorerActionValues.ACCESS_MANAGE_TARGET) &&
            (
                protocolVersion < replacementProtocolVersion ||
                    targetKind != ExplorerActionValues.TARGET_FILE ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE ||
                    placement != ExplorerActionValues.PLACEMENT_OVERFLOW ||
                    normalizedPresentation != ExplorerActionValues.PRESENTATION_ACTIVITY ||
                    normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE
                )
        ) {
            return null
        }
        if (
            normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE &&
            (
                placement != ExplorerActionValues.PLACEMENT_OVERFLOW ||
                    targetKind != ExplorerActionValues.TARGET_FILE ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE
                )
        ) {
            return null
        }
        if (
            normalizedPresentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER &&
            (
                protocolVersion < HOST_EXPLORER_PROTOCOL_VERSION ||
                    targetKind != ExplorerActionValues.TARGET_FILE ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE ||
                    accessMode != ExplorerActionValues.ACCESS_READ_ONLY ||
                    normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE ||
                    when {
                        normalizedProbeForActionId == null ->
                            placement != ExplorerActionValues.PLACEMENT_PRIMARY
                        protocolVersion < UNMATCHED_FILE_PROBE_PROTOCOL_VERSION -> true
                        else -> placement != ExplorerActionValues.PLACEMENT_OVERFLOW
                    }
                )
        ) {
            return null
        }
        if (
            normalizedProbeForActionId != null &&
            normalizedPresentation != ExplorerActionValues.PRESENTATION_HOST_EXPLORER
        ) {
            return null
        }
        val normalizedReadSiblings = readSiblings == true
        val normalizedPlaybackProgress = playbackProgress == true
        val normalizedRelatedFileSuffixes = normalizeRelatedFileSuffixes(relatedFileSuffixes)
            ?: return null
        if (
            (normalizedReadSiblings || normalizedPlaybackProgress) &&
            (
                protocolVersion < SESSION_MEDIA_EXTENSION_PROTOCOL_VERSION ||
                    targetKind != ExplorerActionValues.TARGET_FILE ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE ||
                    accessMode != ExplorerActionValues.ACCESS_READ_ONLY ||
                    normalizedPresentation != ExplorerActionValues.PRESENTATION_ACTIVITY ||
                    normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE
                )
        ) {
            return null
        }
        if (
            normalizedRelatedFileSuffixes.isNotEmpty() &&
            (
                protocolVersion < RELATED_FILE_PROTOCOL_VERSION ||
                    normalizedReadSiblings ||
                    normalizedPlaybackProgress ||
                    targetKind != ExplorerActionValues.TARGET_FILE ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE ||
                    accessMode != ExplorerActionValues.ACCESS_READ_ONLY ||
                    normalizedPresentation != ExplorerActionValues.PRESENTATION_ACTIVITY ||
                    normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE
                )
        ) {
            return null
        }

        // v23 path disclosure: one selected directory, read-only, launched as an Activity, with no
        // output, matcher, probe or session capability, so the path is the whole authority shown.
        // zh-CN: v23 路径披露仅限单个所选目录, 只读, 以 Activity 启动, 且不带输出, 匹配器, 探测或会话能力.
        val normalizedFilesystemPath = filesystemPath == true
        if (
            normalizedFilesystemPath &&
            (
                protocolVersion < FILESYSTEM_PATH_PROTOCOL_VERSION ||
                    targetKind != ExplorerActionValues.TARGET_DIRECTORY ||
                    normalizedCardinality != ExplorerActionValues.CARDINALITY_SINGLE ||
                    accessMode != ExplorerActionValues.ACCESS_READ_ONLY ||
                    placement != ExplorerActionValues.PLACEMENT_OVERFLOW ||
                    normalizedPresentation != ExplorerActionValues.PRESENTATION_ACTIVITY ||
                    normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE ||
                    normalizedMimeTypes.isNotEmpty() ||
                    normalizedExtensions.isNotEmpty() ||
                    normalizedFileNameSuffixes.isNotEmpty() ||
                    normalizedProbeForActionId != null ||
                    normalizedReadSiblings ||
                    normalizedPlaybackProgress ||
                    normalizedRelatedFileSuffixes.isNotEmpty()
                )
        ) {
            return null
        }

        return ExplorerActionDescriptor(
            protocolVersion = protocolVersion,
            id = normalizedId,
            labelResourceName = normalizedLabelResourceName,
            labelFallback = normalizedLabelFallback,
            activityClassName = normalizedActivityClassName,
            priority = priority.coerceIn(MIN_PRIORITY, MAX_PRIORITY),
            targetKind = targetKind,
            cardinality = normalizedCardinality,
            accessMode = accessMode,
            placement = placement,
            presentation = normalizedPresentation,
            mimeTypes = normalizedMimeTypes,
            extensions = normalizedExtensions,
            fileNameSuffixes = normalizedFileNameSuffixes,
            probeForActionId = normalizedProbeForActionId,
            outputMode = normalizedOutput.mode,
            outputMimeTypes = normalizedOutput.mimeTypes,
            outputNameSuffix = normalizedOutput.nameSuffix,
            maxOutputBytes = normalizedOutput.maxBytes,
            readSiblings = normalizedReadSiblings,
            playbackProgress = normalizedPlaybackProgress,
            relatedFileSuffixes = normalizedRelatedFileSuffixes,
            filesystemPath = normalizedFilesystemPath,
        )
    }

    fun matches(descriptor: ExplorerActionDescriptor, target: ExplorerActionTarget): Boolean =
        matches(descriptor, listOf(target))

    fun validateDescriptorReferences(
        descriptors: List<ExplorerActionDescriptor>,
    ): List<ExplorerActionDescriptor> {
        val descriptorsById = descriptors.associateBy(ExplorerActionDescriptor::id)
        val claimedProbeTargets = HashSet<String>()
        return descriptors.filter { descriptor ->
            val targetId = descriptor.probeForActionId ?: return@filter true
            val target = descriptorsById[targetId] ?: return@filter false
            isValidProbeTarget(target) && claimedProbeTargets.add(targetId)
        }
    }

    fun matches(descriptor: ExplorerActionDescriptor, targets: List<ExplorerActionTarget>): Boolean {
        if (targets.isEmpty() || targets.size > ExplorerActionProtocol.MAX_TARGETS_PER_REQUEST) return false
        if (
            descriptor.cardinality == ExplorerActionValues.CARDINALITY_SINGLE && targets.size != 1 ||
            descriptor.cardinality != ExplorerActionValues.CARDINALITY_SINGLE &&
                descriptor.cardinality != ExplorerActionValues.CARDINALITY_MULTIPLE
        ) {
            return false
        }
        if (
            descriptor.accessMode != ExplorerActionValues.ACCESS_READ_ONLY &&
            descriptor.accessMode != ExplorerActionValues.ACCESS_CREATE_IN_PARENT &&
            descriptor.accessMode != ExplorerActionValues.ACCESS_REPLACE_TARGET &&
            descriptor.accessMode != ExplorerActionValues.ACCESS_MANAGE_TARGET
        ) {
            return false
        }
        if (
            descriptor.placement != ExplorerActionValues.PLACEMENT_OVERFLOW &&
            descriptor.placement != ExplorerActionValues.PLACEMENT_PRIMARY &&
            descriptor.placement != ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR
        ) {
            return false
        }
        return targets.all { target ->
            targetKindMatches(descriptor.targetKind, target.kind) && matchType(descriptor, target)
        }
    }

    fun normalizeActivityClassName(packageName: String, activityClassName: String?): String? {
        val raw = activityClassName?.trim()?.takeIf {
            it.isNotEmpty() && it.length <= ExplorerActionProtocol.MAX_ACTIVITY_CLASS_NAME_LENGTH
        } ?: return null
        if (raw.any(Char::isWhitespace) || '/' in raw) return null
        val normalized = when {
            raw.startsWith('.') -> packageName + raw
            '.' !in raw -> "$packageName.$raw"
            else -> raw
        }
        val segments = normalized.split('.')
        if (segments.size < 2 || segments.any { !activitySegmentPattern.matches(it) }) return null
        return normalized
    }

    fun sanitizeDisplayText(value: String?, maxLength: Int = ExplorerActionProtocol.MAX_LABEL_LENGTH): String? {
        return value
            ?.replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.take(maxLength)
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeMatchers(
        values: Collection<String>?,
        normalizer: (String) -> String?,
    ): Set<String>? {
        if (values == null) return emptySet()
        if (values.size > ExplorerActionProtocol.MAX_MATCHERS_PER_KIND) return null
        val result = LinkedHashSet<String>()
        values.forEach { value ->
            val normalized = normalizer(value) ?: return null
            result += normalized
        }
        return result
    }

    private fun normalizeActionId(value: String?): String? = value?.trim()?.takeIf {
        it.length <= ExplorerActionProtocol.MAX_ACTION_ID_LENGTH && actionIdPattern.matches(it)
    }

    private fun normalizeExtension(value: String): String? {
        val normalized = value.trim().removePrefix(".").lowercase(Locale.ROOT)
        return normalized.takeIf { it.length <= MAX_EXTENSION_LENGTH && extensionPattern.matches(it) }
    }

    private fun normalizeFileNameSuffix(value: String): String? {
        val normalized = value.trim().removePrefix(".").lowercase(Locale.ROOT)
        return normalized.takeIf {
            it.length <= ExplorerActionProtocol.MAX_FILE_NAME_SUFFIX_LENGTH &&
                fileNameSuffixPattern.matches(it)
        }
    }

    private fun normalizeRelatedFileSuffixes(values: Collection<String>?): Set<String>? {
        if (values == null) return emptySet()
        if (values.size > ExplorerActionProtocol.MAX_RELATED_FILE_SUFFIXES) return null
        val result = LinkedHashSet<String>()
        values.forEach { value ->
            val normalized = value.trim().lowercase(Locale.ROOT)
            if (
                normalized.length !in 2..ExplorerActionProtocol.MAX_RELATED_FILE_SUFFIX_LENGTH ||
                !relatedFileSuffixPattern.matches(normalized)
            ) {
                return null
            }
            if (!result.add(normalized)) return null
        }
        return result
    }

    internal fun normalizeMimeType(value: String): String? {
        val normalized = value.substringBefore(';').trim().lowercase(Locale.ROOT)
        val parts = normalized.split('/')
        if (parts.size != 2) return null
        val type = parts[0]
        val subtype = parts[1]
        if (type == "*" && subtype != "*") return null
        if (type != "*" && !mimePartPattern.matches(type)) return null
        if (subtype != "*" && !mimePartPattern.matches(subtype)) return null
        return "$type/$subtype"
    }

    private fun mimeMatches(pattern: String, actual: String): Boolean {
        val patternParts = pattern.split('/')
        val actualParts = actual.split('/')
        if (patternParts.size != 2 || actualParts.size != 2) return false
        return (patternParts[0] == "*" || patternParts[0] == actualParts[0]) &&
                (patternParts[1] == "*" || patternParts[1] == actualParts[1])
    }

    private fun targetKindMatches(declaredKind: Int, actualKind: Int): Boolean = when (declaredKind) {
        ExplorerActionValues.TARGET_FILE -> actualKind == ExplorerActionValues.TARGET_FILE
        ExplorerActionValues.TARGET_DIRECTORY -> actualKind == ExplorerActionValues.TARGET_DIRECTORY
        ExplorerActionValues.TARGET_MIXED ->
            actualKind == ExplorerActionValues.TARGET_FILE ||
                actualKind == ExplorerActionValues.TARGET_DIRECTORY
        else -> false
    }

    private fun matchType(descriptor: ExplorerActionDescriptor, target: ExplorerActionTarget): Boolean {
        if (
            target.kind == ExplorerActionValues.TARGET_DIRECTORY &&
            descriptor.mimeTypes.isEmpty() &&
            descriptor.extensions.isEmpty() &&
            descriptor.fileNameSuffixes.isEmpty()
        ) {
            return true
        }
        val normalizedTargetMimeType = normalizeMimeType(target.mimeType)
        val normalizedTargetExtension = normalizeExtension(target.extension)
        val mimeMatches = normalizedTargetMimeType != null && descriptor.mimeTypes.any { pattern ->
            mimeMatches(pattern, normalizedTargetMimeType)
        }
        val extensionMatches = normalizedTargetExtension != null && normalizedTargetExtension in descriptor.extensions
        val normalizedTargetFileName = target.fileName
            .takeIf {
                it.length in 1..ExplorerActionProtocol.MAX_TARGET_DISPLAY_NAME_LENGTH &&
                    it.none(::isInvalidFileNameCharacter)
            }
            ?.lowercase(Locale.ROOT)
        val fileNameSuffixMatches = normalizedTargetFileName != null &&
            descriptor.fileNameSuffixes.any { suffix ->
                normalizedTargetFileName.endsWith(".$suffix")
            }
        return mimeMatches || extensionMatches || fileNameSuffixMatches
    }

    private fun isValidProbeTarget(descriptor: ExplorerActionDescriptor): Boolean =
        descriptor.probeForActionId == null &&
            descriptor.targetKind == ExplorerActionValues.TARGET_FILE &&
            descriptor.cardinality == ExplorerActionValues.CARDINALITY_SINGLE &&
            descriptor.accessMode == ExplorerActionValues.ACCESS_READ_ONLY &&
            descriptor.placement == ExplorerActionValues.PLACEMENT_PRIMARY &&
            descriptor.presentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER

    private fun isInvalidFileNameCharacter(character: Char): Boolean =
        character == '/' ||
            character == '\\' ||
            character == '\u0000' ||
            character.code < 0x20 ||
            character.code == 0x7F

    private const val MIN_PRIORITY = -10_000
    private const val MAX_PRIORITY = 10_000
    private const val MAX_EXTENSION_LENGTH = 64
    private const val PRIMARY_PLACEMENT_PROTOCOL_VERSION = 2
    private const val MULTI_TARGET_PROTOCOL_VERSION = 4
    private const val HOST_EXPLORER_PROTOCOL_VERSION = 5
    private const val TARGET_REPLACEMENT_PROTOCOL_VERSION = 8
    private const val MANAGED_TARGET_PROTOCOL_VERSION = 9
    private const val SESSION_MEDIA_EXTENSION_PROTOCOL_VERSION = 12
    private const val COMPOUND_SUFFIX_PROTOCOL_VERSION = 14
    private const val UNMATCHED_FILE_PROBE_PROTOCOL_VERSION = 17
    private const val RELATED_FILE_PROTOCOL_VERSION = 22
    private const val FILESYSTEM_PATH_PROTOCOL_VERSION = 23
}
