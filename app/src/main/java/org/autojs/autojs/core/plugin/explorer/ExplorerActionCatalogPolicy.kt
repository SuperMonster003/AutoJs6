package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import java.util.Locale

internal object ExplorerActionCatalogPolicy {

    private val actionIdPattern = Regex("[a-z0-9][a-z0-9._-]*")
    private val resourceNamePattern = Regex("[a-z][a-z0-9_]*")
    private val activitySegmentPattern = Regex("[A-Za-z_\$][A-Za-z0-9_\$]*")
    private val extensionPattern = Regex("[a-z0-9][a-z0-9+_-]*")
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
        accessMode: Int,
        placement: Int,
        mimeTypes: Collection<String>?,
        extensions: Collection<String>?,
        outputMode: Int? = null,
        outputMimeTypes: Collection<String>? = null,
        outputNameSuffix: String? = null,
        maxOutputBytes: Long? = null,
    ): ExplorerActionDescriptor? {
        if (
            protocolVersion !in
            ExplorerActionProtocol.MIN_SUPPORTED_VERSION..ExplorerActionProtocol.MAX_SUPPORTED_VERSION
        ) {
            return null
        }
        val normalizedId = id?.trim()?.takeIf {
            it.length <= ExplorerActionProtocol.MAX_ACTION_ID_LENGTH && actionIdPattern.matches(it)
        } ?: return null
        val normalizedLabelResourceName = labelResourceName
            ?.trim()
            ?.takeIf { resourceNamePattern.matches(it) }
        val normalizedLabelFallback = sanitizeDisplayText(labelFallback, ExplorerActionProtocol.MAX_LABEL_LENGTH)
        if (normalizedLabelResourceName == null && normalizedLabelFallback == null) return null
        val normalizedActivityClassName = normalizeActivityClassName(packageName, activityClassName) ?: return null
        if (targetKind != ExplorerActionValues.TARGET_FILE) return null
        if (accessMode != ExplorerActionValues.ACCESS_READ_ONLY) return null
        val placementSupported = when (placement) {
            ExplorerActionValues.PLACEMENT_OVERFLOW -> true
            ExplorerActionValues.PLACEMENT_PRIMARY -> protocolVersion >= PRIMARY_PLACEMENT_PROTOCOL_VERSION
            else -> false
        }
        if (!placementSupported) return null

        val normalizedMimeTypes = normalizeMatchers(mimeTypes, ::normalizeMimeType)
        val normalizedExtensions = normalizeMatchers(extensions, ::normalizeExtension)
        if (normalizedMimeTypes == null || normalizedExtensions == null) return null
        if (normalizedMimeTypes.isEmpty() && normalizedExtensions.isEmpty()) return null
        val normalizedOutput = ExplorerActionOutputPolicy.normalize(
            protocolVersion = protocolVersion,
            outputMode = outputMode,
            outputMimeTypes = outputMimeTypes,
            outputNameSuffix = outputNameSuffix,
            maxOutputBytes = maxOutputBytes,
        ) ?: return null
        if (
            normalizedOutput.mode != ExplorerActionValues.OUTPUT_NONE &&
            placement != ExplorerActionValues.PLACEMENT_OVERFLOW
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
            accessMode = accessMode,
            placement = placement,
            mimeTypes = normalizedMimeTypes,
            extensions = normalizedExtensions,
            outputMode = normalizedOutput.mode,
            outputMimeTypes = normalizedOutput.mimeTypes,
            outputNameSuffix = normalizedOutput.nameSuffix,
            maxOutputBytes = normalizedOutput.maxBytes,
        )
    }

    fun matches(descriptor: ExplorerActionDescriptor, target: ExplorerActionTarget): Boolean {
        if (descriptor.targetKind != ExplorerActionValues.TARGET_FILE) return false
        if (descriptor.accessMode != ExplorerActionValues.ACCESS_READ_ONLY) return false
        if (
            descriptor.placement != ExplorerActionValues.PLACEMENT_OVERFLOW &&
            descriptor.placement != ExplorerActionValues.PLACEMENT_PRIMARY
        ) {
            return false
        }

        val normalizedTargetMimeType = normalizeMimeType(target.mimeType)
        val normalizedTargetExtension = normalizeExtension(target.extension)
        val mimeMatches = normalizedTargetMimeType != null && descriptor.mimeTypes.any { pattern ->
            mimeMatches(pattern, normalizedTargetMimeType)
        }
        val extensionMatches = normalizedTargetExtension != null && normalizedTargetExtension in descriptor.extensions
        return mimeMatches || extensionMatches
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

    private fun normalizeExtension(value: String): String? {
        val normalized = value.trim().removePrefix(".").lowercase(Locale.ROOT)
        return normalized.takeIf { it.length <= MAX_EXTENSION_LENGTH && extensionPattern.matches(it) }
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

    private const val MIN_PRIORITY = -10_000
    private const val MAX_PRIORITY = 10_000
    private const val MAX_EXTENSION_LENGTH = 64
    private const val PRIMARY_PLACEMENT_PROTOCOL_VERSION = 2
}
