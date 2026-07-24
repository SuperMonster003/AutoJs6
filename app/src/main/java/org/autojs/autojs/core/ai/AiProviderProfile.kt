package org.autojs.autojs.core.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI
import java.util.UUID

data class AiProviderProfile(
    val id: String,
    val name: String,
    val provider: AiProviderDefinition,
    val baseUrl: String,
    val model: String = "",
) {

    val providerId: String
        get() = provider.id

    internal fun normalized(): AiProviderProfile {
        val normalizedId = normalizeProfileId(id)
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.PROFILE_NAME,
                code = AiProviderValidationCode.REQUIRED,
                message = "Profile name must not be blank",
            )
        }
        if (normalizedName.length > MAX_PROFILE_NAME_LENGTH) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.PROFILE_NAME,
                code = AiProviderValidationCode.TOO_LONG,
                message = "Profile name must not exceed $MAX_PROFILE_NAME_LENGTH characters",
            )
        }
        if (normalizedName.hasControlCharacter()) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.PROFILE_NAME,
                code = AiProviderValidationCode.CONTROL_CHARACTER,
                message = "Profile name must not contain control characters",
            )
        }

        val normalizedModel = model.trim()
        if (normalizedModel.length > MAX_MODEL_LENGTH) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.MODEL,
                code = AiProviderValidationCode.TOO_LONG,
                message = "Model name must not exceed $MAX_MODEL_LENGTH characters",
            )
        }
        if (normalizedModel.hasControlCharacter()) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.MODEL,
                code = AiProviderValidationCode.CONTROL_CHARACTER,
                message = "Model name must not contain control characters",
            )
        }
        return copy(
            id = normalizedId,
            name = normalizedName,
            baseUrl = AiProviderUrl.normalize(baseUrl),
            model = normalizedModel,
        )
    }

    companion object {

        private const val MAX_PROFILE_NAME_LENGTH = 100
        private const val MAX_MODEL_LENGTH = 256

        @JvmStatic
        @JvmOverloads
        fun create(
            name: String,
            provider: AiProviderDefinition,
            baseUrl: String = provider.defaultBaseUrl.orEmpty(),
            model: String = "",
        ): AiProviderProfile = AiProviderProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            provider = provider,
            baseUrl = baseUrl,
            model = model,
        ).normalized()

        internal fun normalizeProfileId(id: String): String {
            val normalized = id.trim().lowercase()
            val parsed = try {
                UUID.fromString(normalized)
            } catch (e: IllegalArgumentException) {
                throw AiProviderValidationException(
                    field = AiProviderValidationField.PROFILE_ID,
                    code = AiProviderValidationCode.INVALID_FORMAT,
                    message = "Profile ID must be a UUID",
                    cause = e,
                )
            }
            if (parsed.toString() != normalized) {
                throw AiProviderValidationException(
                    field = AiProviderValidationField.PROFILE_ID,
                    code = AiProviderValidationCode.INVALID_FORMAT,
                    message = "Profile ID must be a canonical UUID",
                )
            }
            return normalized
        }

        private fun String.hasControlCharacter(): Boolean =
            any { character -> character.code < 0x20 || character.code == 0x7F }
    }
}

data class AiProviderProfileSummary(
    val id: String,
    val name: String,
    val providerId: String,
    val baseUrl: String,
    val model: String,
    val hasApiKey: Boolean,
    val isDefault: Boolean,
) {

    /**
     * Credential state is intentionally omitted so logging a summary cannot reveal key material
     * or whether a particular log entry had a credential attached.
     */
    override fun toString(): String =
        "AiProviderProfileSummary(id=$id, providerId=$providerId, " +
            "credential=[REDACTED], isDefault=$isDefault)"
}

enum class AiProviderProfileIssueCode(
    val id: String,
) {
    MISSING_ENTRY("missing_entry"),
    DECRYPTION_FAILED("decryption_failed"),
    INVALID_DATA("invalid_data"),
}

enum class AiProviderProfileStatus(
    val id: String,
) {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
}

sealed class AiProviderProfileInspection {

    abstract val id: String
    abstract val status: AiProviderProfileStatus
    abstract val isDefault: Boolean
    abstract val errorCode: AiProviderProfileIssueCode?

    data class Available(
        val profile: AiProviderProfileSummary,
    ) : AiProviderProfileInspection() {

        override val id: String
            get() = profile.id

        override val status: AiProviderProfileStatus
            get() = AiProviderProfileStatus.AVAILABLE

        override val isDefault: Boolean
            get() = profile.isDefault

        override val errorCode: AiProviderProfileIssueCode?
            get() = null
    }

    /**
     * An index entry that is preserved but cannot currently be read.
     */
    data class Unavailable(
        override val id: String,
        override val isDefault: Boolean,
        override val errorCode: AiProviderProfileIssueCode,
    ) : AiProviderProfileInspection() {

        override val status: AiProviderProfileStatus
            get() = AiProviderProfileStatus.UNAVAILABLE
    }
}

/**
 * A non-mutating inspection result. Unavailable entries remain encrypted and indexed until the
 * user explicitly deletes or replaces them.
 */
data class AiProviderVaultInspection(
    val entries: List<AiProviderProfileInspection>,
) {

    val profiles: List<AiProviderProfileSummary>
        get() = entries.mapNotNull { entry ->
            (entry as? AiProviderProfileInspection.Available)?.profile
        }

    val issues: List<AiProviderProfileInspection.Unavailable>
        get() = entries.filterIsInstance<AiProviderProfileInspection.Unavailable>()
}

sealed class SecretUpdate {

    object Keep : SecretUpdate()

    class Replace internal constructor(
        internal val value: String,
    ) : SecretUpdate() {

        override fun toString(): String = "Replace([REDACTED])"

        companion object {

            @JvmStatic
            fun with(value: String): Replace = Replace(value)
        }
    }

    object Clear : SecretUpdate()

    companion object {

        /**
         * Empty input preserves an existing key. Clearing always requires [Clear].
         */
        @JvmStatic
        fun fromInput(value: CharSequence?): SecretUpdate {
            val input = value?.toString().orEmpty()
            return if (input.isBlank()) Keep else Replace(input)
        }
    }
}

/**
 * Controls how [AiProviderVault.saveProfile] updates the default profile in the same commit.
 */
sealed class DefaultProfileUpdate {

    /**
     * Preserves the current default. The legacy first-profile auto-selection remains in effect.
     */
    object Keep : DefaultProfileUpdate()

    /**
     * Makes the profile being saved the default.
     */
    object Set : DefaultProfileUpdate()

    /**
     * Ensures the profile being saved is not the default without changing another default.
     */
    object Unset : DefaultProfileUpdate()
}

sealed class AiProviderSelector {

    object Default : AiProviderSelector()

    data class Id(val id: String) : AiProviderSelector()

    data class Name(val name: String) : AiProviderSelector()

    data class ProviderId(val providerId: String) : AiProviderSelector()
}

class AiProviderSecret internal constructor(value: String) {

    private val characters = value.toCharArray()

    internal fun reveal(): String = String(characters)

    internal fun clear() {
        characters.fill('\u0000')
    }

    override fun toString(): String = "[REDACTED]"
}

class AiResolvedProviderProfile internal constructor(
    val profile: AiProviderProfile,
    val apiKey: AiProviderSecret?,
) {

    val id: String
        get() = profile.id

    val name: String
        get() = profile.name

    val providerId: String
        get() = profile.providerId

    val baseUrl: String
        get() = profile.baseUrl

    val model: String
        get() = profile.model

    internal fun clearSecret() {
        apiKey?.clear()
    }

    override fun toString(): String =
        "AiResolvedProviderProfile(id=$id, providerId=$providerId, apiKey=[REDACTED])"
}

enum class AiProviderValidationField {
    PROFILE_ID,
    PROFILE_NAME,
    PROVIDER,
    BASE_URL,
    MODEL,
    API_KEY,
}

enum class AiProviderValidationCode {
    REQUIRED,
    INVALID_FORMAT,
    TOO_LONG,
    CONTROL_CHARACTER,
    DUPLICATE,
    UNSUPPORTED,
    USER_INFO_NOT_ALLOWED,
    QUERY_NOT_ALLOWED,
    FRAGMENT_NOT_ALLOWED,
    INSECURE_TRANSPORT,
    SECRET_REENTRY_REQUIRED,
}

class AiProviderValidationException(
    val field: AiProviderValidationField,
    val code: AiProviderValidationCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

class AiProviderSelectionException(message: String) : IllegalStateException(message)

internal object AiProviderUrl {

    private const val MAX_BASE_URL_LENGTH = 2_048

    fun normalize(value: String): String {
        val input = value.trim()
        if (input.isEmpty()) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.REQUIRED,
                message = "Base URL must not be blank",
            )
        }
        if (input.length > MAX_BASE_URL_LENGTH) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.TOO_LONG,
                message = "Base URL must not exceed $MAX_BASE_URL_LENGTH characters",
            )
        }

        val uri = try {
            URI(input)
        } catch (e: Exception) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.INVALID_FORMAT,
                message = "Invalid base URL",
                cause = e,
            )
        }
        if (uri.isOpaque) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.INVALID_FORMAT,
                message = "Base URL must be hierarchical",
            )
        }
        if (uri.rawUserInfo != null) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.USER_INFO_NOT_ALLOWED,
                message = "Base URL must not contain user information",
            )
        }
        if (uri.rawQuery != null) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.QUERY_NOT_ALLOWED,
                message = "Base URL must not contain a query",
            )
        }
        if (uri.rawFragment != null) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.FRAGMENT_NOT_ALLOWED,
                message = "Base URL must not contain a fragment",
            )
        }

        val url = input.toHttpUrlOrNull()
            ?: throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.INVALID_FORMAT,
                message = "Invalid HTTP base URL",
            )
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.USER_INFO_NOT_ALLOWED,
                message = "Base URL must not contain user information",
            )
        }
        when (url.scheme) {
            "https" -> Unit
            "http" -> if (!isLoopbackHost(url.host)) {
                throw AiProviderValidationException(
                    field = AiProviderValidationField.BASE_URL,
                    code = AiProviderValidationCode.INSECURE_TRANSPORT,
                    message = "Plain HTTP is permitted only for loopback hosts",
                )
            }
            else -> throw AiProviderValidationException(
                field = AiProviderValidationField.BASE_URL,
                code = AiProviderValidationCode.INSECURE_TRANSPORT,
                message = "Base URL must use HTTPS",
            )
        }
        return url.toString().trimEnd('/')
    }

    fun hasSameOrigin(first: String, second: String): Boolean {
        val firstUrl = first.toHttpUrlOrNull() ?: return false
        val secondUrl = second.toHttpUrlOrNull() ?: return false
        return firstUrl.scheme == secondUrl.scheme &&
            firstUrl.host == secondUrl.host &&
            firstUrl.port == secondUrl.port
    }

    private fun isLoopbackHost(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase()
        if (normalized == "localhost" || normalized == "::1" ||
            normalized == "0:0:0:0:0:0:0:1"
        ) {
            return true
        }
        val parts = normalized.split('.')
        return parts.size == 4 &&
            parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } &&
            parts.first() == "127"
    }
}
