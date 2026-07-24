package org.autojs.autojs.core.ai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.core.security.AndroidKeyStoreSecretCipher
import org.autojs.autojs.core.security.SecretCipher
import java.nio.charset.StandardCharsets

/**
 * Encrypted storage for named AI provider profiles.
 *
 * Every profile, including its API key, is encrypted as a single authenticated entry. The
 * profile index, default profile ID, and changed entry are committed atomically.
 *
 * This is encryption at rest, not a sandbox boundary. Rhino scripts run under the application's
 * UID and retain Java interoperability, so callers must continue to treat executed scripts as
 * trusted code.
 */
class AiProviderVault private constructor(
    private val preferences: VaultPreferences,
    private val secretCipher: SecretCipher,
    private val gson: Gson,
) {

    constructor(context: Context) : this(
        preferences = SharedPreferencesVaultPreferences(
            (context.applicationContext ?: context).getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ),
        ),
        secretCipher = AndroidKeyStoreSecretCipher(),
        gson = Gson(),
    )

    internal constructor(
        preferences: VaultPreferences,
        secretCipher: SecretCipher,
    ) : this(preferences, secretCipher, Gson())

    val defaultProfileId: String?
        get() = synchronized(vaultLock) {
            val profileId = preferences.getString(KEY_DEFAULT_PROFILE) ?: return@synchronized null
            val normalizedId = normalizeStoredProfileId(profileId)
            normalizedId.takeIf { it in readProfileIndex() }
        }

    /**
     * Inspects every indexed profile without mutating storage. A damaged entry is represented by
     * [AiProviderProfileInspection.Unavailable], while readable entries remain usable.
     */
    fun inspectProfiles(): AiProviderVaultInspection = synchronized(vaultLock) {
        val profileIds = readProfileIndex()
        val defaultId = readDefaultProfileId(profileIds)
        AiProviderVaultInspection(
            entries = profileIds.map { profileId ->
                try {
                    AiProviderProfileInspection.Available(
                        loadStoredProfile(profileId).toSummary(
                            isDefault = profileId == defaultId,
                        ),
                    )
                } catch (error: StoredProfileLoadException) {
                    AiProviderProfileInspection.Unavailable(
                        id = profileId,
                        isDefault = profileId == defaultId,
                        errorCode = error.issueCode,
                    )
                }
            },
        )
    }

    /**
     * Returns readable profiles only. Use [inspectProfiles] when unavailable-entry diagnostics are
     * needed.
     */
    fun listProfiles(): List<AiProviderProfileSummary> {
        return inspectProfiles().profiles
    }

    fun listProfileIssues(): List<AiProviderProfileInspection.Unavailable> {
        return inspectProfiles().issues
    }

    fun getProfile(id: String): AiProviderProfile? = synchronized(vaultLock) {
        val profileId = AiProviderProfile.normalizeProfileId(id)
        if (profileId !in readProfileIndex()) {
            return@synchronized null
        }
        loadStoredProfile(profileId).toPublicProfile()
    }

    fun saveProfile(
        profile: AiProviderProfile,
        secretUpdate: SecretUpdate = SecretUpdate.Keep,
        defaultUpdate: DefaultProfileUpdate = DefaultProfileUpdate.Keep,
    ): AiProviderProfileSummary = synchronized(vaultLock) {
        val normalizedProfile = profile.normalized()
        val profileIds = readProfileIndex().toMutableList()
        val currentDefaultId = readDefaultProfileId(profileIds)
        val entryKey = entryKey(normalizedProfile.id)
        val existing = if (normalizedProfile.id in profileIds) {
            loadStoredProfile(normalizedProfile.id)
        } else {
            null
        }
        val duplicateName = profileIds.asSequence()
            .filterNot { it == normalizedProfile.id }
            .mapNotNull(::loadStoredProfileIfAvailable)
            .any { stored ->
                stored.name.equals(normalizedProfile.name, ignoreCase = true)
            }
        if (duplicateName) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.PROFILE_NAME,
                code = AiProviderValidationCode.DUPLICATE,
                message = "Profile name already exists",
            )
        }
        if (
            existing?.apiKey != null &&
            secretUpdate.keepsExistingSecret() &&
            existing.hasDifferentCredentialDestination(normalizedProfile)
        ) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.API_KEY,
                code = AiProviderValidationCode.SECRET_REENTRY_REQUIRED,
                message = "Re-enter or clear the API key after changing the provider or origin",
            )
        }
        val apiKey = resolveUpdatedSecret(existing?.apiKey, secretUpdate)
        val storedProfile = StoredProfileV1.from(normalizedProfile, apiKey)
        val encryptedProfile = encryptStoredProfile(entryKey, storedProfile)

        val isNewProfile = normalizedProfile.id !in profileIds
        if (isNewProfile) {
            profileIds += normalizedProfile.id
        }
        val defaultId = when (defaultUpdate) {
            DefaultProfileUpdate.Keep ->
                currentDefaultId
                    ?: normalizedProfile.id.takeIf { isNewProfile && profileIds.size == 1 }
            DefaultProfileUpdate.Set -> normalizedProfile.id
            DefaultProfileUpdate.Unset ->
                currentDefaultId.takeUnless { it == normalizedProfile.id }
        }
        val editor = preferences.edit()
            .putString(entryKey, encryptedProfile)
            .putString(KEY_PROFILE_INDEX, encodeProfileIndex(profileIds))
        if (defaultId == null) {
            editor.remove(KEY_DEFAULT_PROFILE)
        } else {
            editor.putString(KEY_DEFAULT_PROFILE, defaultId)
        }
        commitOrThrow(editor, "save AI provider profile")
        storedProfile.toSummary(isDefault = normalizedProfile.id == defaultId)
    }

    fun deleteProfile(id: String): Boolean = synchronized(vaultLock) {
        val profileId = AiProviderProfile.normalizeProfileId(id)
        val profileIds = readProfileIndex().toMutableList()
        if (!profileIds.remove(profileId)) {
            return@synchronized false
        }
        val currentDefaultId = readDefaultProfileId(profileIds + profileId)
        val nextDefaultId = when {
            currentDefaultId == profileId ->
                profileIds.firstOrNull { candidateId ->
                    loadStoredProfileIfAvailable(candidateId) != null
                }
            currentDefaultId in profileIds -> currentDefaultId
            else -> null
        }

        val editor = preferences.edit()
            .remove(entryKey(profileId))
            .putString(KEY_PROFILE_INDEX, encodeProfileIndex(profileIds))
        if (nextDefaultId == null) {
            editor.remove(KEY_DEFAULT_PROFILE)
        } else {
            editor.putString(KEY_DEFAULT_PROFILE, nextDefaultId)
        }
        commitOrThrow(editor, "delete AI provider profile")
        true
    }

    fun setDefaultProfile(id: String?) = synchronized(vaultLock) {
        val editor = preferences.edit()
        if (id == null) {
            editor.remove(KEY_DEFAULT_PROFILE)
        } else {
            val profileId = AiProviderProfile.normalizeProfileId(id)
            if (profileId !in readProfileIndex()) {
                throw AiProviderSelectionException("AI provider profile does not exist")
            }
            loadStoredProfile(profileId)
            editor.putString(KEY_DEFAULT_PROFILE, profileId)
        }
        commitOrThrow(editor, "set default AI provider profile")
    }

    fun <T> withResolvedProfile(
        selector: AiProviderSelector = AiProviderSelector.Default,
        block: (AiResolvedProviderProfile) -> T,
    ): T {
        val resolvedProfile = synchronized(vaultLock) {
            resolveStoredProfile(selector).toResolvedProfile()
        }
        return try {
            block(resolvedProfile)
        } finally {
            resolvedProfile.clearSecret()
        }
    }

    private fun resolveStoredProfile(selector: AiProviderSelector): StoredProfileV1 {
        val profileIds = readProfileIndex()
        if (profileIds.isEmpty()) {
            throw AiProviderSelectionException("No AI provider profile is configured")
        }
        return when (selector) {
            AiProviderSelector.Default -> {
                val profileId = readDefaultProfileId(profileIds)
                    ?: throw AiProviderSelectionException(
                        "No default AI provider profile is configured",
                    )
                loadStoredProfile(profileId)
            }
            is AiProviderSelector.Id -> {
                val profileId = AiProviderProfile.normalizeProfileId(selector.id)
                if (profileId !in profileIds) {
                    throw AiProviderSelectionException("AI provider profile does not exist")
                }
                loadStoredProfile(profileId)
            }
            is AiProviderSelector.Name -> {
                val expectedName = selector.name.trim()
                if (expectedName.isEmpty()) {
                    throw AiProviderSelectionException("AI provider profile name is blank")
                }
                selectExactlyOne(
                    profileIds.mapNotNull(::loadStoredProfileIfAvailable)
                        .filter { it.name.equals(expectedName, ignoreCase = true) },
                    "AI provider profile name is ambiguous",
                )
            }
            is AiProviderSelector.ProviderId -> {
                val providerId = AiProviderDefinition.requireById(selector.providerId).id
                val candidates = profileIds.mapNotNull(::loadStoredProfileIfAvailable)
                    .filter { it.providerId == providerId }
                val defaultId = readDefaultProfileId(profileIds)
                candidates.firstOrNull { it.id == defaultId }
                    ?: selectExactlyOne(
                        candidates,
                        "Multiple profiles use AI provider $providerId; select one by ID or name",
                    )
            }
        }
    }

    private fun selectExactlyOne(
        candidates: List<StoredProfileV1>,
        ambiguousMessage: String,
    ): StoredProfileV1 {
        if (candidates.isEmpty()) {
            throw AiProviderSelectionException("AI provider profile does not exist")
        }
        if (candidates.size != 1) {
            throw AiProviderSelectionException(ambiguousMessage)
        }
        return candidates.single()
    }

    private fun resolveUpdatedSecret(
        existingApiKey: String?,
        secretUpdate: SecretUpdate,
    ): String? = when (secretUpdate) {
        SecretUpdate.Keep -> existingApiKey
        is SecretUpdate.Replace -> {
            val value = secretUpdate.value.trim()
            if (value.isEmpty()) {
                existingApiKey
            } else {
                validateApiKey(value)
            }
        }
        SecretUpdate.Clear -> null
    }

    private fun SecretUpdate.keepsExistingSecret(): Boolean = when (this) {
        SecretUpdate.Keep -> true
        is SecretUpdate.Replace -> value.trim().isEmpty()
        SecretUpdate.Clear -> false
    }

    private fun StoredProfileV1.hasDifferentCredentialDestination(
        profile: AiProviderProfile,
    ): Boolean {
        val existingProfile = toPublicProfile()
        return existingProfile.provider != profile.provider ||
            !AiProviderUrl.hasSameOrigin(existingProfile.baseUrl, profile.baseUrl)
    }

    private fun validateApiKey(value: String): String {
        if (value.any { character -> character.code !in 0x21..0x7E }) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.API_KEY,
                code = AiProviderValidationCode.INVALID_FORMAT,
                message = "API key must contain only visible ASCII characters",
            )
        }
        if (value.length > MAX_API_KEY_LENGTH) {
            throw AiProviderValidationException(
                field = AiProviderValidationField.API_KEY,
                code = AiProviderValidationCode.TOO_LONG,
                message = "API key must not exceed $MAX_API_KEY_LENGTH characters",
            )
        }
        return value
    }

    private fun loadStoredProfileIfAvailable(profileId: String): StoredProfileV1? {
        return try {
            loadStoredProfile(profileId)
        } catch (_: StoredProfileLoadException) {
            null
        }
    }

    private fun loadStoredProfile(profileId: String): StoredProfileV1 {
        val entryKey = entryKey(profileId)
        val encryptedProfile = preferences.getString(entryKey)
            ?: throw StoredProfileLoadException(
                issueCode = AiProviderProfileIssueCode.MISSING_ENTRY,
                message = "Encrypted AI provider profile is missing",
            )
        val plaintext = try {
            secretCipher.decrypt(encryptedProfile, associatedData(entryKey))
        } catch (e: Exception) {
            throw StoredProfileLoadException(
                issueCode = AiProviderProfileIssueCode.DECRYPTION_FAILED,
                message = "Unable to decrypt AI provider profile",
                cause = e,
            )
        }
        return try {
            val storedProfile = gson.fromJson(
                String(plaintext, StandardCharsets.UTF_8),
                StoredProfileV1::class.java,
            ) ?: throw IllegalArgumentException("Encrypted AI provider profile is empty")
            storedProfile.validated(profileId)
        } catch (e: Exception) {
            throw StoredProfileLoadException(
                issueCode = AiProviderProfileIssueCode.INVALID_DATA,
                message = "Invalid encrypted AI provider profile",
                cause = e,
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private fun encryptStoredProfile(
        entryKey: String,
        storedProfile: StoredProfileV1,
    ): String {
        val plaintext = gson.toJson(storedProfile).toByteArray(StandardCharsets.UTF_8)
        return try {
            secretCipher.encrypt(plaintext, associatedData(entryKey))
        } catch (e: Exception) {
            throw AiProviderVaultException("Unable to encrypt AI provider profile", e)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun readProfileIndex(): List<String> {
        val encodedIndex = preferences.getString(KEY_PROFILE_INDEX) ?: return emptyList()
        return try {
            val ids = gson.fromJson(encodedIndex, Array<String>::class.java)
                ?: throw AiProviderVaultException("AI provider profile index is empty")
            ids.map(::normalizeStoredProfileId).distinct()
        } catch (e: AiProviderVaultException) {
            throw e
        } catch (e: Exception) {
            throw AiProviderVaultException("Invalid AI provider profile index", e)
        }
    }

    private fun encodeProfileIndex(profileIds: List<String>): String =
        gson.toJson(profileIds)

    private fun readDefaultProfileId(profileIds: List<String>): String? {
        val storedDefaultId = preferences.getString(KEY_DEFAULT_PROFILE) ?: return null
        val defaultId = normalizeStoredProfileId(storedDefaultId)
        return defaultId.takeIf { it in profileIds }
    }

    private fun normalizeStoredProfileId(id: String): String {
        return try {
            AiProviderProfile.normalizeProfileId(id)
        } catch (e: AiProviderValidationException) {
            throw AiProviderVaultException("Invalid stored AI provider profile ID", e)
        }
    }

    private fun entryKey(profileId: String): String = ENTRY_PREFIX + profileId

    private fun associatedData(entryKey: String): ByteArray =
        entryKey.toByteArray(StandardCharsets.UTF_8)

    private fun commitOrThrow(editor: VaultPreferences.Editor, operation: String) {
        if (!editor.commit()) {
            throw AiProviderVaultException("Failed to $operation")
        }
    }

    private data class StoredProfileV1(
        @SerializedName("schema_version")
        val schemaVersion: Int? = null,
        @SerializedName("id")
        val id: String? = null,
        @SerializedName("name")
        val name: String? = null,
        @SerializedName("provider_id")
        val providerId: String? = null,
        @SerializedName("base_url")
        val baseUrl: String? = null,
        @SerializedName("model")
        val model: String? = null,
        @SerializedName("api_key")
        val apiKey: String? = null,
    ) {

        fun validated(expectedId: String): StoredProfileV1 {
            // Null is accepted only as the pre-release V1 representation. Every new write emits
            // an explicit version, so current development profiles remain recoverable.
            if (schemaVersion != null && schemaVersion != SCHEMA_VERSION) {
                throw AiProviderVaultException(
                    "Encrypted AI provider profile has an unsupported schema version",
                )
            }
            val profile = toPublicProfile().normalized()
            if (profile.id != expectedId) {
                throw AiProviderVaultException(
                    "Encrypted AI provider profile does not match its entry",
                )
            }
            apiKey?.let { key ->
                if (key.isEmpty() ||
                    key.any { character -> character.code !in 0x21..0x7E } ||
                    key.length > MAX_API_KEY_LENGTH
                ) {
                    throw AiProviderVaultException(
                        "Encrypted AI provider profile contains an invalid API key",
                    )
                }
            }
            return from(profile, apiKey)
        }

        fun toPublicProfile(): AiProviderProfile = AiProviderProfile(
            id = id ?: throw AiProviderVaultException(
                "Encrypted AI provider profile has no ID",
            ),
            name = name ?: throw AiProviderVaultException(
                "Encrypted AI provider profile has no name",
            ),
            provider = AiProviderDefinition.requireById(
                providerId ?: throw AiProviderVaultException(
                    "Encrypted AI provider profile has no provider",
                ),
            ),
            baseUrl = baseUrl ?: throw AiProviderVaultException(
                "Encrypted AI provider profile has no base URL",
            ),
            model = model.orEmpty(),
        )

        fun toSummary(isDefault: Boolean): AiProviderProfileSummary {
            val profile = toPublicProfile()
            return AiProviderProfileSummary(
                id = profile.id,
                name = profile.name,
                providerId = profile.providerId,
                baseUrl = profile.baseUrl,
                model = profile.model,
                hasApiKey = apiKey != null,
                isDefault = isDefault,
            )
        }

        fun toResolvedProfile(): AiResolvedProviderProfile = AiResolvedProviderProfile(
            profile = toPublicProfile(),
            apiKey = apiKey?.let(::AiProviderSecret),
        )

        override fun toString(): String =
            "StoredProfileV1(schemaVersion=$schemaVersion, id=$id, " +
                "providerId=$providerId, apiKey=[REDACTED])"

        companion object {

            fun from(profile: AiProviderProfile, apiKey: String?): StoredProfileV1 =
                StoredProfileV1(
                    schemaVersion = SCHEMA_VERSION,
                    id = profile.id,
                    name = profile.name,
                    providerId = profile.providerId,
                    baseUrl = profile.baseUrl,
                    model = profile.model,
                    apiKey = apiKey,
                )
        }
    }

    companion object {

        const val PREFERENCES_NAME = "ai_provider_vault"

        internal const val KEY_PROFILE_INDEX = "profile_index_v1"
        internal const val KEY_DEFAULT_PROFILE = "default_profile_v1"
        internal const val ENTRY_PREFIX = "profile_"

        private const val SCHEMA_VERSION = 1
        private const val MAX_API_KEY_LENGTH = 16_384

        private val vaultLock = Any()
    }
}

open class AiProviderVaultException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private class StoredProfileLoadException(
    val issueCode: AiProviderProfileIssueCode,
    message: String,
    cause: Throwable? = null,
) : AiProviderVaultException(message, cause)

internal interface VaultPreferences {

    fun getString(key: String): String?

    fun edit(): Editor

    interface Editor {

        fun putString(key: String, value: String): Editor

        fun remove(key: String): Editor

        fun commit(): Boolean
    }
}

private class SharedPreferencesVaultPreferences(
    private val preferences: SharedPreferences,
) : VaultPreferences {

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun edit(): VaultPreferences.Editor =
        SharedPreferencesVaultEditor(preferences.edit())

    private class SharedPreferencesVaultEditor(
        private val editor: SharedPreferences.Editor,
    ) : VaultPreferences.Editor {

        override fun putString(key: String, value: String): VaultPreferences.Editor {
            editor.putString(key, value)
            return this
        }

        override fun remove(key: String): VaultPreferences.Editor {
            editor.remove(key)
            return this
        }

        override fun commit(): Boolean = editor.commit()
    }
}
