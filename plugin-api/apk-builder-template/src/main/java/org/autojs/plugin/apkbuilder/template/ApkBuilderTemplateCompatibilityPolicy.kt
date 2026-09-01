package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Evaluates the Runtime Kit host-version declaration shared by host and plugin.
 *
 * Missing interval metadata remains backward compatible, but only as the exact
 * version declared by
 * [ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_CODE]. A non-exact
 * host is accepted solely when the closed interval is valid and patch mismatch
 * was explicitly enabled by the Runtime Kit producer.
 */
object ApkBuilderTemplateCompatibilityPolicy {

    data class Declaration(
        val builtForHostVersionCode: Long,
        val minHostVersionCode: Long,
        val maxHostVersionCode: Long,
        val allowPatchVersionMismatch: Boolean,
    )

    enum class Level {
        EXACT,
        PATCH_COMPATIBLE,
        BLOCKED,
    }

    enum class Reason {
        EXACT_HOST,
        DECLARED_PATCH_RANGE,
        HOST_BELOW_MINIMUM,
        HOST_ABOVE_MAXIMUM,
        PATCH_MISMATCH_NOT_ALLOWED,
        INVALID_DECLARATION,
        INVALID_HOST_VERSION,
    }

    data class Decision(
        val declaration: Declaration,
        val actualHostVersionCode: Long,
        val level: Level,
        val reason: Reason,
    ) {
        val isCompatible: Boolean
            get() = level != Level.BLOCKED
    }

    fun evaluate(
        declaration: Declaration,
        actualHostVersionCode: Long,
    ): Decision {
        val declarationValid = declaration.builtForHostVersionCode > 0L &&
                declaration.minHostVersionCode > 0L &&
                declaration.maxHostVersionCode >= declaration.minHostVersionCode &&
                declaration.builtForHostVersionCode in
                declaration.minHostVersionCode..declaration.maxHostVersionCode
        if (!declarationValid) {
            return Decision(declaration, actualHostVersionCode, Level.BLOCKED, Reason.INVALID_DECLARATION)
        }
        if (actualHostVersionCode <= 0L) {
            return Decision(declaration, actualHostVersionCode, Level.BLOCKED, Reason.INVALID_HOST_VERSION)
        }
        if (actualHostVersionCode == declaration.builtForHostVersionCode) {
            return Decision(declaration, actualHostVersionCode, Level.EXACT, Reason.EXACT_HOST)
        }
        if (actualHostVersionCode < declaration.minHostVersionCode) {
            return Decision(declaration, actualHostVersionCode, Level.BLOCKED, Reason.HOST_BELOW_MINIMUM)
        }
        if (actualHostVersionCode > declaration.maxHostVersionCode) {
            return Decision(declaration, actualHostVersionCode, Level.BLOCKED, Reason.HOST_ABOVE_MAXIMUM)
        }
        if (!declaration.allowPatchVersionMismatch) {
            return Decision(
                declaration,
                actualHostVersionCode,
                Level.BLOCKED,
                Reason.PATCH_MISMATCH_NOT_ALLOWED,
            )
        }
        return Decision(
            declaration,
            actualHostVersionCode,
            Level.PATCH_COMPATIBLE,
            Reason.DECLARED_PATCH_RANGE,
        )
    }

    fun evaluate(
        info: ApkBuilderTemplateInfo,
        actualHostVersionCode: Long,
    ): Decision = evaluate(
        declaration = declarationFromCapabilities(
            capabilities = info.capabilities,
            fallbackBuiltForHostVersionCode = info.hostVersionCode,
        ),
        actualHostVersionCode = actualHostVersionCode,
    )

    fun declarationFromCapabilities(
        capabilities: Bundle?,
        fallbackBuiltForHostVersionCode: Long,
    ): Declaration {
        val builtFor = capabilities.value(ApkBuilderTemplateCapabilityKeys.BUILT_FOR_HOST_VERSION_CODE)
            .coerceLongOrNull()
            ?: fallbackBuiltForHostVersionCode
        val minimum = capabilities.value(ApkBuilderTemplateCapabilityKeys.COMPATIBILITY_MIN_HOST_VERSION_CODE)
            .coerceLongOrNull()
            ?: capabilities.value(PluginCapabilityKeys.REQUIRES_HOST_VERSION).coerceLongOrNull()
            ?: builtFor
        val maximum = capabilities.value(ApkBuilderTemplateCapabilityKeys.COMPATIBILITY_MAX_HOST_VERSION_CODE)
            .coerceLongOrNull()
            ?: builtFor
        val allowPatchMismatch = capabilities.value(ApkBuilderTemplateCapabilityKeys.ALLOW_PATCH_VERSION_MISMATCH)
            .coerceBoolean()
        return Declaration(
            builtForHostVersionCode = builtFor,
            minHostVersionCode = minimum,
            maxHostVersionCode = maximum,
            allowPatchVersionMismatch = allowPatchMismatch,
        )
    }

    @Suppress("DEPRECATION")
    private fun Bundle?.value(key: String): Any? = this?.get(key)

    private fun Any?.coerceLongOrNull(): Long? = when (this) {
        is Number -> toLong()
        is String -> trim().toLongOrNull()
        else -> null
    }

    private fun Any?.coerceBoolean(): Boolean = when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> trim().lowercase() in setOf("true", "1", "yes", "on")
        else -> false
    }
}
