package org.autojs.autojs.runtime.api.installer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes

/** Script options checked before any descriptor is opened or request reaches the plugin. */
data class InstallerScriptOptions(
    val interaction: String,
    val install: InstallerJson.InstallOptions,
) {

    companion object {

        private val INSTALL_KEYS = setOf(
            "authorizer", "interaction", "allowDowngrade", "allowTestOnly", "bypassLowTargetSdk",
            "installer", "user", "deleteSource", "continueOnError", "timeout",
            "grantAllRequestedPermissions", "requestUpdateOwnership", "dexopt", "installReason", "packageSource",
        )
        private val UNINSTALL_KEYS = setOf("authorizer", "interaction", "keepData", "user", "timeout")
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

        /** Only an omitted argument selects defaults; an explicit JSON null is invalid. */
        fun install(value: JsonElement?): InstallerScriptOptions {
            val options = options(value, INSTALL_KEYS)
            return InstallerScriptOptions(
                interaction = interaction(options.get("interaction")),
                install = InstallerJson.InstallOptions(
                    authorizer = authorizer(options.get("authorizer")),
                    allowDowngrade = boolean(options.get("allowDowngrade"), "options.allowDowngrade", false),
                    allowTestOnly = boolean(options.get("allowTestOnly"), "options.allowTestOnly", false),
                    bypassLowTargetSdk = boolean(options.get("bypassLowTargetSdk"), "options.bypassLowTargetSdk", false),
                    installer = options.get("installer")?.let {
                        val name = string(it, "options.installer")
                        if (name.isEmpty()) null else packageName(name, "options.installer")
                    },
                    user = user(options.get("user")),
                    deleteSource = boolean(options.get("deleteSource"), "options.deleteSource", false),
                    continueOnError = boolean(options.get("continueOnError"), "options.continueOnError", true),
                    timeoutMillis = timeout(options.get("timeout")),
                    grantAllRequestedPermissions = options.get("grantAllRequestedPermissions")?.let {
                        boolean(it, "options.grantAllRequestedPermissions", false)
                    },
                    requestUpdateOwnership = options.get("requestUpdateOwnership")?.let {
                        boolean(it, "options.requestUpdateOwnership", false)
                    },
                    dexopt = optionalEnum(options.get("dexopt"), "options.dexopt", InstallerContract.DEXOPT_MODES),
                    installReason = optionalEnum(options.get("installReason"), "options.installReason", InstallerContract.INSTALL_REASONS),
                    packageSource = optionalEnum(options.get("packageSource"), "options.packageSource", InstallerContract.PACKAGE_SOURCES),
                ),
            )
        }

        fun uninstall(value: JsonElement?): InstallerJson.UninstallOptions {
            val options = options(value, UNINSTALL_KEYS)
            val interaction = interaction(options.get("interaction"))
            if (interaction == InstallerContract.INTERACTION_NOTIFICATION) throw invalid("notification is an installation-only interaction")
            return InstallerJson.UninstallOptions(
                authorizer = authorizer(options.get("authorizer")),
                interaction = interaction,
                keepData = boolean(options.get("keepData"), "options.keepData", false),
                user = user(options.get("user")),
                timeoutMillis = timeout(options.get("timeout")),
            )
        }

        /** Preferred is the V1-compatible default; persistent is negotiated before its V2 call. */
        fun setDefault(value: JsonElement?): InstallerJson.DefaultInstallerOptions {
            val options = options(value, setOf("authorizer", "mode"))
            val mode = options.get("mode")?.let { string(it, "options.mode") } ?: InstallerContract.DEFAULT_MODE_PREFERRED
            if (!InstallerContract.isDefaultMode(mode)) throw invalid("'options.mode' must be preferred or persistent")
            return InstallerJson.DefaultInstallerOptions(authorizer(options.get("authorizer")), mode)
        }

        fun enabled(value: JsonElement?): Boolean = boolean(value, "enabled", true)

        fun authorizer(value: JsonElement?, default: String = InstallerContract.AUTHORIZER_AUTO): String {
            if (value == null) return default
            val name = string(value, "authorizer")
            if (!InstallerContract.isAuthorizer(name)) throw invalid("'authorizer' must be one of auto, none, shizuku, root, dhizuku")
            return name
        }

        fun timeout(value: JsonElement?, default: Long = InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS): Long =
            if (value == null) default else integer(value, "timeout", 1L..InstallerContract.MAX_SESSION_TIMEOUT_MILLIS)

        internal fun packageName(value: String, label: String): String {
            if (value.toByteArray(Charsets.UTF_8).size > InstallerContract.MAX_INSTALLER_PACKAGE_LENGTH) {
                throw invalid("'$label' exceeds ${InstallerContract.MAX_INSTALLER_PACKAGE_LENGTH} UTF-8 bytes")
            }
            if (!PACKAGE_NAME.matches(value)) throw invalid("'$label' must be a package name")
            return value
        }

        internal fun invalid(message: String): InstallerPluginException =
            InstallerPluginException(InstallerError(InstallerErrorCodes.INVALID_ARGUMENT, message, retryable = false))

        private fun options(value: JsonElement?, knownKeys: Set<String>): JsonObject {
            if (value == null) return JsonObject()
            if (!value.isJsonObject) throw invalid("'options' must be an object")
            if (value.toString().toByteArray(Charsets.UTF_8).size > InstallerContract.MAX_JSON_BYTES) {
                throw invalid("'options' exceeds ${InstallerContract.MAX_JSON_BYTES} UTF-8 bytes")
            }
            return value.asJsonObject.also { options ->
                options.keySet().firstOrNull { it !in knownKeys }?.let { throw invalid("unknown option 'options.$it'") }
            }
        }

        private fun interaction(value: JsonElement?): String {
            if (value == null) return InstallerContract.INTERACTION_AUTO
            val name = string(value, "options.interaction")
            if (!InstallerContract.isInteraction(name)) throw invalid("'options.interaction' must be one of auto, dialog, silent, notification")
            return name
        }

        private fun user(value: JsonElement?): String {
            if (value == null) return InstallerContract.USER_CURRENT
            if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                return value.asString.takeIf { it == InstallerContract.USER_CURRENT || it == InstallerContract.USER_ALL }
                    ?: throw invalid("'options.user' must be a non-negative integer, 'current' or 'all'")
            }
            return integer(value, "options.user", 0L..Int.MAX_VALUE.toLong()).toString()
        }

        private fun string(value: JsonElement, label: String): String {
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label' must be a string")
            return value.asString
        }

        private fun boolean(value: JsonElement?, label: String, default: Boolean): Boolean {
            if (value == null) return default
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) throw invalid("'$label' must be a boolean")
            return value.asBoolean
        }

        private fun optionalEnum(value: JsonElement?, label: String, allowed: List<String>): String? {
            if (value == null) return null
            return string(value, label).also { if (it !in allowed) throw invalid("'$label' must be one of ${allowed.joinToString()}") }
        }

        private fun integer(value: JsonElement, label: String, range: LongRange): Long {
            if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) throw invalid("'$label' must be a number")
            val number = try {
                value.asBigDecimal.longValueExact()
            } catch (_: NumberFormatException) {
                throw invalid("'$label' must be a finite integer in ${range.first}..${range.last}")
            } catch (_: ArithmeticException) {
                throw invalid("'$label' must be an integer in ${range.first}..${range.last}")
            }
            if (number !in range) throw invalid("'$label' must be an integer in ${range.first}..${range.last}")
            return number
        }
    }
}
