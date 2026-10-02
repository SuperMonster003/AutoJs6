package org.autojs.autojs.core.plugin.installer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import java.nio.charset.StandardCharsets

/**
 * Host-side codec of the JSON documents carried by the installer contract (plugin roadmap P1.3,
 * protocol document `docs/dev/installer-plugin-protocol-v1.md`). Pure Kotlin on top of Gson, so
 * the JVM tests cover it without Android.
 */
object InstallerJson {

    /** One descriptor of an install request: `item` groups the split set it belongs to. */
    data class SourceEntry(val item: Int, val displayName: String, val size: Long)

    /** The options of an install request; every field has the contract default. */
    data class InstallOptions(
        val authorizer: String = InstallerContract.AUTHORIZER_AUTO,
        val allowDowngrade: Boolean = false,
        val allowTestOnly: Boolean = false,
        val bypassLowTargetSdk: Boolean = false,
        val installer: String? = null,
        val user: String = InstallerContract.USER_CURRENT,
        val deleteSource: Boolean = false,
        val continueOnError: Boolean = true,
        val timeoutMillis: Long = InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS,
        /** Null means not provided, unlike an explicit false or none, which still requires V3. */
        val grantAllRequestedPermissions: Boolean? = null,
        val requestUpdateOwnership: Boolean? = null,
        val dexopt: String? = null,
        val installReason: String? = null,
        val packageSource: String? = null,
        /** Host-only snapshot of explicit wire option names. Null preserves unknown native callers' legacy behavior. */
        val providedOptions: Set<String>? = null,
    ) {
        val hasAdvancedOptions: Boolean get() = grantAllRequestedPermissions != null || requestUpdateOwnership != null ||
            dexopt != null || installReason != null || packageSource != null || providedOptions.orEmpty().any { it in ADVANCED_OPTION_FIELDS }

        val explicitDeleteSource: Boolean? get() = if (providedOptions == null || InstallerContract.FIELD_DELETE_SOURCE in providedOptions) deleteSource else null

        fun validate(): String? = when {
            !InstallerContract.isAuthorizer(authorizer) -> "unknown authorizer: $authorizer"
            installer != null && installer.length > InstallerContract.MAX_INSTALLER_PACKAGE_LENGTH -> "installer package name is too long"
            installer != null && installer.isBlank() -> "installer package name must not be blank"
            !isUser(user) -> "unknown user: $user"
            timeoutMillis <= 0L || timeoutMillis > InstallerContract.MAX_SESSION_TIMEOUT_MILLIS -> "timeoutMillis out of range"
            dexopt != null && !InstallerContract.isDexoptMode(dexopt) -> "unknown dexopt mode: $dexopt"
            installReason != null && !InstallerContract.isInstallReason(installReason) -> "unknown install reason: $installReason"
            packageSource != null && !InstallerContract.isPackageSource(packageSource) -> "unknown package source: $packageSource"
            providedOptions?.any { it !in InstallerContract.PROFILE_OPTION_FIELDS && it !in SESSION_OPTION_FIELDS } == true -> "unknown provided installation option"
            InstallerContract.FIELD_GRANT_ALL_REQUESTED_PERMISSIONS in providedOptions.orEmpty() && grantAllRequestedPermissions == null -> "explicit grantAllRequestedPermissions cannot be null"
            InstallerContract.FIELD_REQUEST_UPDATE_OWNERSHIP in providedOptions.orEmpty() && requestUpdateOwnership == null -> "explicit requestUpdateOwnership cannot be null"
            InstallerContract.FIELD_DEXOPT in providedOptions.orEmpty() && dexopt == null -> "explicit dexopt cannot be null"
            else -> null
        }

        private companion object {
            val ADVANCED_OPTION_FIELDS = setOf(InstallerContract.FIELD_GRANT_ALL_REQUESTED_PERMISSIONS,
                InstallerContract.FIELD_REQUEST_UPDATE_OWNERSHIP, InstallerContract.FIELD_DEXOPT,
                InstallerContract.FIELD_INSTALL_REASON, InstallerContract.FIELD_PACKAGE_SOURCE)
            val SESSION_OPTION_FIELDS = setOf(InstallerContract.FIELD_TIMEOUT_MILLIS, InstallerContract.FIELD_CONTINUE_ON_ERROR)
        }
    }

    data class UninstallOptions(
        val authorizer: String = InstallerContract.AUTHORIZER_AUTO,
        val interaction: String = InstallerContract.INTERACTION_AUTO,
        val keepData: Boolean = false,
        val user: String = InstallerContract.USER_CURRENT,
        val timeoutMillis: Long = InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS,
    ) {
        fun validate(): String? = when {
            !InstallerContract.isAuthorizer(authorizer) -> "unknown authorizer: $authorizer"
            !InstallerContract.isInteraction(interaction) -> "unknown interaction: $interaction"
            interaction == InstallerContract.INTERACTION_NOTIFICATION -> "notification is an installation-only interaction"
            !isUser(user) -> "unknown user: $user"
            timeoutMillis <= 0L || timeoutMillis > InstallerContract.MAX_SESSION_TIMEOUT_MILLIS -> "timeoutMillis out of range"
            else -> null
        }
    }

    /** One InstallResult document; [json] keeps the encoded document for the script layer. */
    data class InstallResult(
        val ok: Boolean,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val previousVersionCode: Long?,
        val authorizer: String?,
        val interaction: String?,
        val durationMillis: Long?,
        val sourceDeleted: Boolean?,
        val notes: List<String>,
        val error: InstallerError?,
        val json: String,
        val sourceDeleteRequested: Boolean? = null,
    )

    /** `onCompleted` payload: one result, or the item results of a batch. */
    data class Completion(val results: List<InstallResult>, val json: String,
        /** Local Binder-negotiation fact, never decoded from a plugin-controlled JSON field. */
        val sourceProfilesEnabled: Boolean = false) {
        val single: InstallResult get() = results.single()
        val allOk: Boolean get() = results.all { it.ok }
    }

    data class StageDetail(val index: Int?, val packageName: String?, val json: String)

    data class ProgressDetail(val index: Int?, val bytesWritten: Long?, val totalBytes: Long?, val json: String)

    data class SessionStatus(val state: String, val index: Int?, val progress: Float?, val json: String)

    data class AuthorizerState(val available: Boolean, val running: Boolean, val granted: Boolean, val reason: String?, val json: String)

    data class DefaultInstallerState(val component: String?, val isSelf: Boolean, val method: String?, val requiresClear: Boolean, val json: String,
        /** Local receipt only; absence is false, never proof that the system has no persistent policy. */
        val persistentConfigured: Boolean = false) {
        fun satisfies(enabled: Boolean, mode: String): Boolean = when (mode) {
            InstallerContract.DEFAULT_MODE_PREFERRED -> isSelf == enabled
            InstallerContract.DEFAULT_MODE_PERSISTENT -> {
                check(method in setOf(InstallerContract.DEFAULT_METHOD_PREFERRED, InstallerContract.DEFAULT_METHOD_PERSISTENT,
                    InstallerContract.DEFAULT_METHOD_NONE)) { "Persistent default result has no recognized method" }
                (isSelf && method == InstallerContract.DEFAULT_METHOD_PERSISTENT) == enabled
            }
            else -> throw IllegalArgumentException("unknown default-installer mode: $mode")
        }
    }

    data class DefaultInstallerOptions(
        val authorizer: String = InstallerContract.AUTHORIZER_AUTO,
        val mode: String = InstallerContract.DEFAULT_MODE_PREFERRED,
    )

    data class DeviceUser(val id: Int, val name: String?, val primary: Boolean, val running: Boolean)

    data class UninstallResult(val ok: Boolean, val packageName: String?, val authorizer: String?, val error: InstallerError?, val json: String)

    fun isUser(value: String): Boolean =
        value == InstallerContract.USER_CURRENT || value == InstallerContract.USER_ALL || value.toIntOrNull()?.let { it >= 0 } == true

    // Encoding.

    fun encodeInstallRequest(
        id: String,
        sources: List<SourceEntry>,
        interaction: String,
        options: InstallOptions,
        isBatch: Boolean = sources.map { it.item }.distinct().size > 1,
        origin: String = InstallerContract.SOURCE_HOST,
        applySourceProfiles: Boolean = false,
    ): String {
        require(id.isNotBlank()) { "request id must not be blank" }
        require(origin in setOf(InstallerContract.SOURCE_HOST, InstallerContract.SOURCE_SCRIPT)) { "unknown source origin: $origin" }
        require(InstallerContract.isInteraction(interaction)) { "unknown interaction: $interaction" }
        options.validate()?.let { throw IllegalArgumentException(it) }
        require(!applySourceProfiles || options.providedOptions != null) { "source profiles require an explicit option-presence snapshot" }
        require(sources.isNotEmpty()) { "at least one package source is required" }
        val items = sources.map { it.item }.distinct()
        require(isBatch || items.size == 1) { "a non-batch request must contain exactly one package" }
        require(items.size <= InstallerContract.MAX_BATCH_SOURCES) { "at most ${InstallerContract.MAX_BATCH_SOURCES} packages per request" }
        items.forEach { item ->
            val count = sources.count { it.item == item }
            require(count <= InstallerContract.MAX_SPLITS_PER_PACKAGE) { "at most ${InstallerContract.MAX_SPLITS_PER_PACKAGE} splits per package" }
        }
        val document = JsonObject().apply {
            addProperty(InstallerContract.FIELD_ID, id)
            addProperty(InstallerContract.FIELD_BATCH, isBatch)
            addProperty(InstallerContract.FIELD_SOURCE_ORIGIN, origin)
            if (applySourceProfiles) addProperty(InstallerContract.FIELD_APPLY_SOURCE_PROFILES, true)
            add(InstallerContract.FIELD_SOURCES, JsonArray().apply {
                sources.forEach { source ->
                    add(JsonObject().apply {
                        addProperty(InstallerContract.FIELD_ITEM, source.item)
                        addProperty(InstallerContract.FIELD_DISPLAY_NAME, source.displayName.take(InstallerContract.MAX_DISPLAY_NAME_LENGTH))
                        addProperty(InstallerContract.FIELD_SIZE, source.size)
                    })
                }
            })
            addProperty(InstallerContract.FIELD_INTERACTION, interaction)
            add(InstallerContract.FIELD_OPTIONS, JsonObject().apply {
                addProperty(InstallerContract.FIELD_AUTHORIZER, options.authorizer)
                addProperty(InstallerContract.FIELD_ALLOW_DOWNGRADE, options.allowDowngrade)
                addProperty(InstallerContract.FIELD_ALLOW_TEST_ONLY, options.allowTestOnly)
                addProperty(InstallerContract.FIELD_BYPASS_LOW_TARGET_SDK, options.bypassLowTargetSdk)
                options.installer?.let { addProperty(InstallerContract.FIELD_INSTALLER, it) }
                addProperty(InstallerContract.FIELD_USER, options.user)
                addProperty(InstallerContract.FIELD_DELETE_SOURCE, options.deleteSource)
                addProperty(InstallerContract.FIELD_CONTINUE_ON_ERROR, options.continueOnError)
                addProperty(InstallerContract.FIELD_TIMEOUT_MILLIS, options.timeoutMillis)
                options.grantAllRequestedPermissions?.let { addProperty(InstallerContract.FIELD_GRANT_ALL_REQUESTED_PERMISSIONS, it) }
                options.requestUpdateOwnership?.let { addProperty(InstallerContract.FIELD_REQUEST_UPDATE_OWNERSHIP, it) }
                options.dexopt?.let { addProperty(InstallerContract.FIELD_DEXOPT, it) }
                options.installReason?.let { addProperty(InstallerContract.FIELD_INSTALL_REASON, it) }
                options.packageSource?.let { addProperty(InstallerContract.FIELD_PACKAGE_SOURCE, it) }
                if (applySourceProfiles) {
                    val provided = requireNotNull(options.providedOptions)
                    InstallerContract.PROFILE_OPTION_FIELDS.filterNot { it in provided }.forEach { remove(it) }
                    // Null is a deliberate reset only when the caller explicitly supplied a
                    // nullable field. Legacy peers never receive these JSON nulls or the marker.
                    for (field in listOf(InstallerContract.FIELD_INSTALLER, InstallerContract.FIELD_INSTALL_REASON, InstallerContract.FIELD_PACKAGE_SOURCE)) {
                        if (field in provided && !has(field)) add(field, com.google.gson.JsonNull.INSTANCE)
                    }
                }
            })
        }
        return checkSize(document.toString(), "install request")
    }

    fun encodeInspectRequest(displayName: String, size: Long): String = checkSize(
        JsonObject().apply {
            addProperty(InstallerContract.FIELD_DISPLAY_NAME, displayName.take(InstallerContract.MAX_DISPLAY_NAME_LENGTH))
            addProperty(InstallerContract.FIELD_SIZE, size)
        }.toString(),
        "inspect request",
    )

    fun encodeUninstallRequest(packageName: String, options: UninstallOptions): String {
        require(packageName.isNotBlank() && packageName.length <= InstallerContract.MAX_INSTALLER_PACKAGE_LENGTH) { "invalid package name" }
        options.validate()?.let { throw IllegalArgumentException(it) }
        return checkSize(
            JsonObject().apply {
                addProperty(InstallerContract.FIELD_PACKAGE_NAME, packageName)
                addProperty(InstallerContract.FIELD_KEEP_DATA, options.keepData)
                addProperty(InstallerContract.FIELD_USER, options.user)
                addProperty(InstallerContract.FIELD_AUTHORIZER, options.authorizer)
                addProperty(InstallerContract.FIELD_INTERACTION, options.interaction)
                addProperty(InstallerContract.FIELD_TIMEOUT_MILLIS, options.timeoutMillis)
            }.toString(),
            "uninstall request",
        )
    }

    fun encodeAuthorizerRequest(authorizer: String): String {
        require(InstallerContract.isAuthorizer(authorizer)) { "unknown authorizer: $authorizer" }
        return JsonObject().apply { addProperty(InstallerContract.FIELD_AUTHORIZER, authorizer) }.toString()
    }

    fun encodeDefaultInstallerRequest(authorizer: String, mode: String): String {
        require(InstallerContract.isAuthorizer(authorizer)) { "unknown authorizer: $authorizer" }
        require(InstallerContract.isDefaultMode(mode)) { "unknown default-installer mode: $mode" }
        return JsonObject().apply {
            addProperty(InstallerContract.FIELD_AUTHORIZER, authorizer)
            // Preferred requests retain the exact V1 shape, including when a newer peer is selected.
            if (mode != InstallerContract.DEFAULT_MODE_PREFERRED) addProperty(InstallerContract.FIELD_MODE, mode)
        }.toString()
    }

    // Decoding.

    fun decodeCompletion(json: String?): Completion {
        val root = parseObject(json, "install result")
        val results = root.getAsJsonArrayOrNull(InstallerContract.FIELD_RESULTS)
            ?.map { element -> decodeInstallResult(element.asObjectOrThrow("install result item")) }
            ?: listOf(decodeInstallResult(root))
        require(results.isNotEmpty()) { "install result must contain at least one item" }
        return Completion(results, root.toString())
    }

    fun decodeInstallResult(root: JsonObject): InstallResult = InstallResult(
        ok = root.boolean(InstallerContract.FIELD_OK) ?: false,
        packageName = root.string(InstallerContract.FIELD_PACKAGE_NAME),
        versionName = root.string(InstallerContract.FIELD_VERSION_NAME),
        versionCode = root.long(InstallerContract.FIELD_VERSION_CODE),
        previousVersionCode = root.long(InstallerContract.FIELD_PREVIOUS_VERSION_CODE),
        authorizer = root.string(InstallerContract.FIELD_AUTHORIZER),
        interaction = root.string(InstallerContract.FIELD_INTERACTION),
        durationMillis = root.long(InstallerContract.FIELD_DURATION_MILLIS),
        sourceDeleted = root.boolean(InstallerContract.FIELD_SOURCE_DELETED),
        sourceDeleteRequested = root.boolean(InstallerContract.FIELD_SOURCE_DELETE_REQUESTED),
        notes = root.getAsJsonArrayOrNull(InstallerContract.FIELD_NOTES)?.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }.orEmpty(),
        error = root.getAsJsonObjectOrNull(InstallerContract.FIELD_ERROR)?.let(::decodeErrorObject)?.let { error ->
            // A failed batch item names its package on the item; the nested error may omit it.
            if (error.packageName == null) error.copy(packageName = root.string(InstallerContract.FIELD_PACKAGE_NAME)) else error
        },
        json = root.toString(),
    )

    fun decodeError(json: String?): InstallerError {
        val root = runCatching { parseObject(json, "error") }.getOrElse {
            return InstallerErrorMapper.error(InstallerErrorCodes.INTERNAL, "installer plugin sent an unreadable error document", retryable = false)
        }
        return decodeErrorObject(root)
    }

    fun decodeErrorObject(root: JsonObject): InstallerError = InstallerErrorMapper.normalize(
        code = root.string(InstallerContract.FIELD_ERROR_CODE),
        message = root.string(InstallerContract.FIELD_ERROR_MESSAGE),
        retryable = root.boolean(InstallerContract.FIELD_ERROR_RETRYABLE),
        status = root.long(InstallerContract.FIELD_ERROR_STATUS)?.toInt(),
        systemMessage = root.string(InstallerContract.FIELD_ERROR_SYSTEM_MESSAGE),
        packageName = root.string(InstallerContract.FIELD_PACKAGE_NAME),
        authorizer = root.string(InstallerContract.FIELD_AUTHORIZER),
    )

    fun decodeStageDetail(json: String?): StageDetail {
        val root = parseObjectOrEmpty(json)
        return StageDetail(root.long(InstallerContract.FIELD_INDEX)?.toInt(), root.string(InstallerContract.FIELD_PACKAGE_NAME), root.toString())
    }

    fun decodeProgressDetail(json: String?): ProgressDetail {
        val root = parseObjectOrEmpty(json)
        return ProgressDetail(
            index = root.long(InstallerContract.FIELD_INDEX)?.toInt(),
            bytesWritten = root.long(InstallerContract.FIELD_BYTES_WRITTEN),
            totalBytes = root.long(InstallerContract.FIELD_TOTAL_BYTES),
            json = root.toString(),
        )
    }

    fun decodeSessionStatus(json: String?): SessionStatus {
        val root = parseObject(json, "session status")
        val state = root.string(InstallerContract.FIELD_STATE)?.takeIf(InstallerContract::isStage)
            ?: throw IllegalArgumentException("session status has no known state")
        return SessionStatus(state, root.long(InstallerContract.FIELD_INDEX)?.toInt(), root.double(InstallerContract.FIELD_PROGRESS)?.toFloat(), root.toString())
    }

    fun decodeAuthorizerState(json: String?): AuthorizerState {
        val root = parseObject(json, "authorizer state")
        return AuthorizerState(
            available = root.boolean(InstallerContract.FIELD_AVAILABLE) ?: false,
            running = root.boolean(InstallerContract.FIELD_RUNNING) ?: false,
            granted = root.boolean(InstallerContract.FIELD_GRANTED) ?: false,
            reason = root.string(InstallerContract.FIELD_REASON),
            json = root.toString(),
        )
    }

    fun decodeGranted(json: String?): Boolean = parseObject(json, "authorizer result").boolean(InstallerContract.FIELD_GRANTED) ?: false

    fun decodeDefaultInstallerState(json: String?): DefaultInstallerState {
        val root = parseObject(json, "default installer state")
        return DefaultInstallerState(
            component = root.string(InstallerContract.FIELD_COMPONENT),
            isSelf = root.boolean(InstallerContract.FIELD_IS_SELF) ?: false,
            method = root.string(InstallerContract.FIELD_METHOD),
            requiresClear = root.boolean(InstallerContract.FIELD_REQUIRES_CLEAR) ?: false,
            json = root.toString(),
            persistentConfigured = root.boolean(InstallerContract.FIELD_PERSISTENT_CONFIGURED) ?: false,
        )
    }

    fun decodeUsers(json: String?): List<DeviceUser> {
        val root = parseObject(json, "users")
        return root.getAsJsonArrayOrNull(InstallerContract.FIELD_USERS)?.map { element: JsonElement ->
            val user = element.asObjectOrThrow("user")
            DeviceUser(
                id = user.long(InstallerContract.FIELD_USER_ID)?.toInt() ?: throw IllegalArgumentException("user without id"),
                name = user.string(InstallerContract.FIELD_USER_NAME),
                primary = user.boolean(InstallerContract.FIELD_USER_PRIMARY) ?: false,
                running = user.boolean(InstallerContract.FIELD_USER_RUNNING) ?: false,
            )
        }.orEmpty()
    }

    fun decodeUninstallResult(json: String?): UninstallResult {
        val root = parseObject(json, "uninstall result")
        return UninstallResult(
            ok = root.boolean(InstallerContract.FIELD_OK) ?: false,
            packageName = root.string(InstallerContract.FIELD_PACKAGE_NAME),
            authorizer = root.string(InstallerContract.FIELD_AUTHORIZER),
            error = root.getAsJsonObjectOrNull(InstallerContract.FIELD_ERROR)?.let(::decodeErrorObject),
            json = root.toString(),
        )
    }

    /** Validates and normalizes a package archive info document; the fields are consumed by the script layer as JSON. */
    fun decodePackageArchiveInfo(json: String?): String {
        val root = parseObject(json, "package archive info")
        val format = root.string(InstallerContract.FIELD_FORMAT)
        require(format != null && format in InstallerContract.FORMATS) { "package archive info has no known format" }
        return root.toString()
    }

    // Helpers.

    fun checkSize(json: String, label: String): String {
        require(json.toByteArray(StandardCharsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES) { "$label exceeds ${InstallerContract.MAX_JSON_BYTES} bytes" }
        return json
    }

    private fun parseObject(json: String?, label: String): JsonObject {
        require(!json.isNullOrBlank()) { "$label document is missing" }
        checkSize(json, label)
        val element = runCatching { JsonParser.parseString(json) }.getOrElse { throw IllegalArgumentException("$label document is not valid JSON", it) }
        return element.asObjectOrThrow(label)
    }

    private fun parseObjectOrEmpty(json: String?): JsonObject =
        if (json.isNullOrBlank()) JsonObject() else runCatching { parseObject(json, "detail") }.getOrDefault(JsonObject())

    private fun JsonElement.asObjectOrThrow(label: String): JsonObject {
        require(isJsonObject) { "$label document must be a JSON object" }
        return asJsonObject
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.boolean(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

    private fun JsonObject.long(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun JsonObject.double(name: String): Double? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonObject.getAsJsonArrayOrNull(name: String): JsonArray? = get(name)?.takeIf(JsonElement::isJsonArray)?.asJsonArray

    private fun JsonObject.getAsJsonObjectOrNull(name: String): JsonObject? = get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
}
