package org.autojs.autojs.core.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.security.SecretCipher
import org.autojs.autojs.core.security.SecretCipherException
import org.autojs.autojs.core.security.createOrReloadAfterConcurrentCreation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AiProviderVaultTest {

    @Test
    fun saveEncryptsWholeProfileAndCommitsEntryIndexAndDefaultAtomically() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val profile = profile(
            name = "Private account",
            provider = AiProviderDefinition.OPENAI,
        )
        val apiKey = "sk-private-1234567890"

        val summary = vault.saveProfile(profile, SecretUpdate.Replace.with(apiKey))

        assertTrue(summary.hasApiKey)
        assertTrue(summary.isDefault)
        assertTrue(apiKey !in summary.toString())
        assertTrue("7890" !in summary.toString())
        assertTrue("hasApiKey" !in summary.toString())
        assertTrue(profile.name !in preferences.values.values.joinToString())
        assertTrue(apiKey !in preferences.values.values.joinToString())

        val entryKey = AiProviderVault.ENTRY_PREFIX + profile.id
        assertTrue(preferences.values.getValue(entryKey).startsWith("v1:"))
        assertEquals(
            setOf(
                entryKey,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )
        assertEquals(summary, vault.listProfiles().single())
        assertEquals(profile, vault.getProfile(profile.id))
        assertTrue(apiKey !in vault.getProfile(profile.id).toString())
    }

    @Test
    fun blankReplacementKeepsExistingKeyAndClearIsExplicit() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )
        val original = profile(name = "Profile")
        vault.saveProfile(original, SecretUpdate.Replace.with("original-secret-1234"))

        val edited = original.copy(model = "new-model")
        val kept = vault.saveProfile(edited, SecretUpdate.Replace.with(" \t "))
        assertTrue(kept.hasApiKey)
        vault.withResolvedProfile { resolved ->
            assertEquals("original-secret-1234", resolved.apiKey?.reveal())
        }

        val cleared = vault.saveProfile(edited, SecretUpdate.Clear)
        assertFalse(cleared.hasApiKey)
        vault.withResolvedProfile { resolved ->
            assertNull(resolved.apiKey)
        }
    }

    @Test
    fun resolvedSecretIsRedactedAndClearedAfterUse() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )
        val profile = profile(name = "Profile")
        vault.saveProfile(profile, SecretUpdate.Replace.with("resolved-secret-1234"))
        var capturedSecret: AiProviderSecret? = null

        vault.withResolvedProfile(AiProviderSelector.Id(profile.id)) { resolved ->
            capturedSecret = resolved.apiKey
            assertEquals("[REDACTED]", resolved.apiKey.toString())
            assertTrue("resolved-secret-1234" !in resolved.toString())
            assertEquals("resolved-secret-1234", resolved.apiKey?.reveal())
        }

        assertEquals(
            "\u0000".repeat("resolved-secret-1234".length),
            capturedSecret?.reveal(),
        )
    }

    @Test
    fun resolvedProfileStringDoesNotRevealSecretPresenceOrSuffix() {
        val profile = profile(name = "Profile")
        val withSecret = AiResolvedProviderProfile(
            profile,
            AiProviderSecret("private-secret-9876"),
        )
        val withoutSecret = AiResolvedProviderProfile(profile, null)

        assertEquals(withoutSecret.toString(), withSecret.toString())
        assertTrue("private-secret-9876" !in withSecret.toString())
        assertTrue("9876" !in withSecret.toString())
        assertTrue("apiKey=null" !in withoutSecret.toString())
        withSecret.clearSecret()
    }

    @Test
    fun providerSelectorUsesMatchingDefaultAndRejectsAmbiguityWithoutOne() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )
        val first = profile(name = "First", provider = AiProviderDefinition.OPENAI)
        val second = profile(name = "Second", provider = AiProviderDefinition.OPENAI)
        vault.saveProfile(first)
        vault.saveProfile(second)
        vault.setDefaultProfile(second.id)

        vault.withResolvedProfile(AiProviderSelector.ProviderId("openai")) { resolved ->
            assertEquals(second.id, resolved.id)
        }
        vault.withResolvedProfile(AiProviderSelector.Name("first")) { resolved ->
            assertEquals(first.id, resolved.id)
        }

        vault.setDefaultProfile(null)
        assertThrows(AiProviderSelectionException::class.java) {
            vault.withResolvedProfile(AiProviderSelector.ProviderId("openai")) { Unit }
        }
        assertThrows(AiProviderSelectionException::class.java) {
            vault.withResolvedProfile { Unit }
        }
    }

    @Test
    fun profileNamesAreCaseInsensitiveUniqueButCanBeKeptWhileEditing() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )
        val first = profile(name = "Personal")
        vault.saveProfile(first)

        val duplicateError = assertThrows(AiProviderValidationException::class.java) {
            vault.saveProfile(profile(name = " personal "))
        }
        assertEquals(AiProviderValidationField.PROFILE_NAME, duplicateError.field)
        assertEquals(AiProviderValidationCode.DUPLICATE, duplicateError.code)

        val edited = vault.saveProfile(first.copy(model = "updated-model"))
        assertEquals("Personal", edited.name)
        assertEquals("updated-model", edited.model)
    }

    @Test
    fun explicitDefaultUpdateIsCommittedWithTheProfileAndIndex() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val first = profile(name = "First")
        val second = profile(name = "Second", provider = AiProviderDefinition.ANTHROPIC)
        vault.saveProfile(first)
        preferences.commits.clear()

        val nonDefault = vault.saveProfile(
            second,
            defaultUpdate = DefaultProfileUpdate.Unset,
        )
        assertFalse(nonDefault.isDefault)
        assertEquals(first.id, vault.defaultProfileId)
        assertEquals(
            setOf(
                AiProviderVault.ENTRY_PREFIX + second.id,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )

        preferences.commits.clear()
        val newDefault = vault.saveProfile(
            second.copy(model = "updated-model"),
            defaultUpdate = DefaultProfileUpdate.Set,
        )
        assertTrue(newDefault.isDefault)
        assertEquals(second.id, vault.defaultProfileId)
        assertEquals(
            setOf(
                AiProviderVault.ENTRY_PREFIX + second.id,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )

        preferences.commits.clear()
        val unset = vault.saveProfile(
            second.copy(model = "updated-again"),
            defaultUpdate = DefaultProfileUpdate.Unset,
        )
        assertFalse(unset.isDefault)
        assertNull(vault.defaultProfileId)
        assertEquals(
            setOf(
                AiProviderVault.ENTRY_PREFIX + second.id,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )
    }

    @Test
    fun explicitUnsetSuppressesFirstProfileAutoSelection() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val first = profile(name = "First")

        val summary = vault.saveProfile(
            first,
            defaultUpdate = DefaultProfileUpdate.Unset,
        )

        assertFalse(summary.isDefault)
        assertNull(vault.defaultProfileId)
        assertTrue(vault.listProfiles().none(AiProviderProfileSummary::isDefault))
        assertEquals(
            setOf(
                AiProviderVault.ENTRY_PREFIX + first.id,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )
    }

    @Test
    fun deleteUpdatesIndexAndDefaultInOneCommit() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val first = profile(name = "First")
        val second = profile(name = "Second", provider = AiProviderDefinition.ANTHROPIC)
        vault.saveProfile(first)
        vault.saveProfile(second)
        preferences.commits.clear()

        assertTrue(vault.deleteProfile(first.id))

        assertEquals(second.id, vault.defaultProfileId)
        assertEquals(second.id, vault.listProfiles().single().id)
        assertEquals(
            setOf(
                AiProviderVault.ENTRY_PREFIX + first.id,
                AiProviderVault.KEY_PROFILE_INDEX,
                AiProviderVault.KEY_DEFAULT_PROFILE,
            ),
            preferences.commits.single(),
        )
        assertFalse(vault.deleteProfile(first.id))
    }

    @Test
    fun authenticatedEntryKeyPreventsCiphertextSwapping() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val first = profile(name = "First")
        val second = profile(name = "Second", provider = AiProviderDefinition.GEMINI)
        vault.saveProfile(first, SecretUpdate.Replace.with("first-secret-1234"))
        vault.saveProfile(second, SecretUpdate.Replace.with("second-secret-5678"))
        val firstKey = AiProviderVault.ENTRY_PREFIX + first.id
        val secondKey = AiProviderVault.ENTRY_PREFIX + second.id
        val firstCiphertext = preferences.values.getValue(firstKey)
        preferences.values[firstKey] = preferences.values.getValue(secondKey)
        preferences.values[secondKey] = firstCiphertext

        assertThrows(AiProviderVaultException::class.java) {
            vault.getProfile(first.id)
        }
        assertThrows(AiProviderVaultException::class.java) {
            vault.getProfile(second.id)
        }
    }

    @Test
    fun savingSameProfileAgainUsesAFreshIv() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val profile = profile(name = "Profile")
        vault.saveProfile(profile, SecretUpdate.Replace.with("private-secret-1234"))
        val entryKey = AiProviderVault.ENTRY_PREFIX + profile.id
        val firstCiphertext = preferences.values.getValue(entryKey)

        vault.saveProfile(profile, SecretUpdate.Keep)

        assertNotEquals(firstCiphertext, preferences.values.getValue(entryKey))
    }

    @Test
    fun failedCommitDoesNotPartiallyPublishProfile() {
        val preferences = InMemoryVaultPreferences().apply {
            failNextCommit = true
        }
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val profile = profile(name = "Profile")

        assertThrows(AiProviderVaultException::class.java) {
            vault.saveProfile(profile, SecretUpdate.Replace.with("private-secret-1234"))
        }

        assertTrue(preferences.values.isEmpty())
        assertTrue(vault.listProfiles().isEmpty())
    }

    @Test
    fun failedAtomicDefaultUpdatePreservesPreviousProfileState() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val first = profile(name = "First")
        val second = profile(name = "Second", provider = AiProviderDefinition.ANTHROPIC)
        vault.saveProfile(first)
        val valuesBeforeFailure = preferences.values.toMap()
        preferences.failNextCommit = true

        assertThrows(AiProviderVaultException::class.java) {
            vault.saveProfile(
                second,
                defaultUpdate = DefaultProfileUpdate.Set,
            )
        }

        assertEquals(valuesBeforeFailure, preferences.values)
        assertEquals(first.id, vault.defaultProfileId)
        assertNull(vault.getProfile(second.id))
    }

    @Test
    fun replacementRejectsCharactersThatAreUnsafeInHeaders() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )

        listOf(
            "secret\r\nInjected: value",
            "secret with spaces",
            "secret-\u2603",
        ).forEach { invalidSecret ->
            val error = assertThrows(AiProviderValidationException::class.java) {
                vault.saveProfile(
                    profile(name = "Profile"),
                    SecretUpdate.Replace.with(invalidSecret),
                )
            }
            assertEquals(AiProviderValidationField.API_KEY, error.field)
            assertEquals(AiProviderValidationCode.INVALID_FORMAT, error.code)
        }
    }

    @Test
    fun keepingSecretRequiresReentryWhenProviderOrOriginChanges() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val original = profile(
            name = "Profile",
            provider = AiProviderDefinition.OPENAI,
        )
        vault.saveProfile(original, SecretUpdate.Replace.with("original-secret-1234"))

        val pathOnlyChange = original.copy(baseUrl = "https://api.openai.com/v2")
        vault.saveProfile(pathOnlyChange, SecretUpdate.Keep)
        vault.withResolvedProfile { resolved ->
            assertEquals("original-secret-1234", resolved.apiKey?.reveal())
        }

        val valuesBeforeRejectedUpdates = preferences.values.toMap()
        val providerChange = pathOnlyChange.copy(
            provider = AiProviderDefinition.OPENAI_COMPATIBLE,
        )
        val providerError = assertThrows(AiProviderValidationException::class.java) {
            vault.saveProfile(providerChange, SecretUpdate.Keep)
        }
        assertEquals(AiProviderValidationField.API_KEY, providerError.field)
        assertEquals(
            AiProviderValidationCode.SECRET_REENTRY_REQUIRED,
            providerError.code,
        )

        val originChange = pathOnlyChange.copy(baseUrl = "https://example.com/v1")
        val originError = assertThrows(AiProviderValidationException::class.java) {
            vault.saveProfile(originChange, SecretUpdate.Replace.with(" \t "))
        }
        assertEquals(AiProviderValidationField.API_KEY, originError.field)
        assertEquals(
            AiProviderValidationCode.SECRET_REENTRY_REQUIRED,
            originError.code,
        )
        assertEquals(valuesBeforeRejectedUpdates, preferences.values)

        val replaced = vault.saveProfile(
            providerChange,
            SecretUpdate.Replace.with("replacement-secret-5678"),
        )
        assertTrue(replaced.hasApiKey)
        vault.withResolvedProfile { resolved ->
            assertEquals("replacement-secret-5678", resolved.apiKey?.reveal())
        }

        val cleared = vault.saveProfile(
            providerChange.copy(baseUrl = "https://example.com/v1"),
            SecretUpdate.Clear,
        )
        assertFalse(cleared.hasApiKey)
    }

    @Test
    fun explicitMissingDefaultSurvivesSavingAndDeletingNonDefaultProfiles() {
        val vault = AiProviderVault(
            InMemoryVaultPreferences(),
            JvmAesGcmSecretCipher(),
        )
        val first = profile(name = "First")
        val second = profile(name = "Second", provider = AiProviderDefinition.ANTHROPIC)
        vault.saveProfile(first)
        vault.saveProfile(second)
        vault.setDefaultProfile(null)

        vault.saveProfile(first.copy(model = "updated-model"))
        assertNull(vault.defaultProfileId)
        assertTrue(vault.deleteProfile(second.id))
        assertNull(vault.defaultProfileId)
        assertTrue(vault.listProfiles().none(AiProviderProfileSummary::isDefault))
    }

    @Test
    fun storedProfileCarriesExplicitSchemaVersion() {
        val preferences = InMemoryVaultPreferences()
        val cipher = JvmAesGcmSecretCipher()
        val vault = AiProviderVault(preferences, cipher)
        val profile = profile(name = "Profile")
        vault.saveProfile(profile, SecretUpdate.Replace.with("private-secret-1234"))
        val entryKey = AiProviderVault.ENTRY_PREFIX + profile.id

        val plaintext = cipher.decrypt(
            preferences.values.getValue(entryKey),
            entryKey.toByteArray(),
        )
        try {
            val storedJson = JsonParser.parseString(plaintext.decodeToString()).asJsonObject
            assertEquals(1, storedJson.get("schema_version").asInt)
            storedJson.remove("schema_version")
            preferences.values[entryKey] = cipher.encrypt(
                storedJson.toString().toByteArray(),
                entryKey.toByteArray(),
            )
        } finally {
            plaintext.fill(0)
        }
        val unversionedCiphertext = preferences.values.getValue(entryKey)

        assertEquals(profile, vault.getProfile(profile.id))
        assertEquals(unversionedCiphertext, preferences.values.getValue(entryKey))
    }

    @Test
    fun inspectionIsolatesMissingUndecryptableAndInvalidEntriesWithoutMutatingThem() {
        val preferences = InMemoryVaultPreferences()
        val cipher = JvmAesGcmSecretCipher()
        val vault = AiProviderVault(preferences, cipher)
        val missing = profile(name = "Missing")
        val undecryptable = profile(
            name = "Undecryptable",
            provider = AiProviderDefinition.ANTHROPIC,
        )
        val invalid = profile(
            name = "Invalid",
            provider = AiProviderDefinition.GEMINI,
        )
        val healthy = profile(
            name = "Healthy",
            provider = AiProviderDefinition.OPENAI,
        )
        listOf(missing, undecryptable, invalid, healthy).forEach { profile ->
            vault.saveProfile(profile)
        }

        val missingEntryKey = AiProviderVault.ENTRY_PREFIX + missing.id
        val undecryptableEntryKey = AiProviderVault.ENTRY_PREFIX + undecryptable.id
        val invalidEntryKey = AiProviderVault.ENTRY_PREFIX + invalid.id
        preferences.values.remove(missingEntryKey)
        preferences.values[undecryptableEntryKey] = "not-an-encrypted-envelope"
        val invalidPlaintext = cipher.decrypt(
            preferences.values.getValue(invalidEntryKey),
            invalidEntryKey.toByteArray(),
        )
        try {
            val invalidJson = JsonParser.parseString(invalidPlaintext.decodeToString()).asJsonObject
            invalidJson.addProperty("schema_version", 2)
            preferences.values[invalidEntryKey] = cipher.encrypt(
                invalidJson.toString().toByteArray(),
                invalidEntryKey.toByteArray(),
            )
        } finally {
            invalidPlaintext.fill(0)
        }
        val valuesBeforeInspection = preferences.values.toMap()
        preferences.commits.clear()

        val inspection = vault.inspectProfiles()

        assertEquals(
            listOf(missing.id, undecryptable.id, invalid.id, healthy.id),
            inspection.entries.map(AiProviderProfileInspection::id),
        )
        assertEquals(listOf(healthy.id), inspection.profiles.map(AiProviderProfileSummary::id))
        assertEquals(
            listOf(
                AiProviderProfileIssueCode.MISSING_ENTRY,
                AiProviderProfileIssueCode.DECRYPTION_FAILED,
                AiProviderProfileIssueCode.INVALID_DATA,
            ),
            inspection.issues.map(AiProviderProfileInspection.Unavailable::errorCode),
        )
        assertEquals(
            listOf("unavailable", "unavailable", "unavailable"),
            inspection.issues.map { issue -> issue.status.id },
        )
        assertTrue(inspection.issues.first().isDefault)
        assertEquals(inspection.issues, vault.listProfileIssues())
        assertEquals(listOf(healthy.id), vault.listProfiles().map(AiProviderProfileSummary::id))
        vault.withResolvedProfile(AiProviderSelector.Name("Healthy")) { resolved ->
            assertEquals(healthy.id, resolved.id)
        }
        assertThrows(AiProviderVaultException::class.java) {
            vault.setDefaultProfile(undecryptable.id)
        }
        assertEquals(valuesBeforeInspection, preferences.values)
        assertTrue(preferences.commits.isEmpty())

        val added = profile(
            name = "Added after damage",
            provider = AiProviderDefinition.OPENROUTER,
        )
        assertEquals(added.id, vault.saveProfile(added).id)
    }

    @Test
    fun unavailableProfileDataIsRemovedOnlyAfterExplicitDelete() {
        val preferences = InMemoryVaultPreferences()
        val vault = AiProviderVault(preferences, JvmAesGcmSecretCipher())
        val damaged = profile(name = "Damaged")
        val healthy = profile(
            name = "Healthy",
            provider = AiProviderDefinition.OPENAI,
        )
        vault.saveProfile(damaged)
        vault.saveProfile(healthy)
        val damagedEntryKey = AiProviderVault.ENTRY_PREFIX + damaged.id
        preferences.values[damagedEntryKey] = "not-an-encrypted-envelope"
        val preservedCiphertext = preferences.values.getValue(damagedEntryKey)

        assertEquals(preservedCiphertext, preferences.values[damagedEntryKey])
        assertEquals(damaged.id, vault.inspectProfiles().issues.single().id)

        assertTrue(vault.deleteProfile(damaged.id))
        assertFalse(preferences.values.containsKey(damagedEntryKey))
        assertTrue(vault.inspectProfiles().issues.isEmpty())
        assertEquals(healthy.id, vault.defaultProfileId)
    }

    @Test
    fun concurrentKeyCreationFailureUsesFreshlyReloadedKey() {
        val expectedKey = Any()

        val actualKey = createOrReloadAfterConcurrentCreation(
            create = { throw IllegalStateException("Alias was created concurrently") },
            reload = { expectedKey },
        )

        assertSame(expectedKey, actualKey)
    }

    @Test
    fun permanentKeyCreationFailureRemainsPrimary() {
        val creationFailure = IllegalStateException("Permanent generation failure")
        val reloadFailure = IllegalArgumentException("Reload failure")

        val thrown = assertThrows(IllegalStateException::class.java) {
            createOrReloadAfterConcurrentCreation<Any>(
                create = { throw creationFailure },
                reload = { throw reloadFailure },
            )
        }

        assertSame(creationFailure, thrown)
        assertEquals(listOf(reloadFailure), thrown.suppressed.toList())
    }

    @Test
    fun absentReloadDoesNotHideKeyCreationFailure() {
        val creationFailure = IllegalStateException("Permanent generation failure")

        val thrown = assertThrows(IllegalStateException::class.java) {
            createOrReloadAfterConcurrentCreation<Any>(
                create = { throw creationFailure },
                reload = { null },
            )
        }

        assertSame(creationFailure, thrown)
        assertTrue(thrown.suppressed.isEmpty())
    }

    private fun profile(
        name: String,
        provider: AiProviderDefinition = AiProviderDefinition.OPENAI_COMPATIBLE,
    ): AiProviderProfile = AiProviderProfile(
        id = UUID.randomUUID().toString(),
        name = name,
        provider = provider,
        baseUrl = provider.defaultBaseUrl ?: "http://127.0.0.1:11434/v1",
        model = "test-model",
    )
}

private class InMemoryVaultPreferences : VaultPreferences {

    val values = linkedMapOf<String, String>()
    val commits = mutableListOf<Set<String>>()
    var failNextCommit = false

    override fun getString(key: String): String? = values[key]

    override fun edit(): VaultPreferences.Editor = Editor()

    private inner class Editor : VaultPreferences.Editor {

        private val pendingValues = linkedMapOf<String, String>()
        private val pendingRemovals = linkedSetOf<String>()

        override fun putString(key: String, value: String): VaultPreferences.Editor {
            pendingRemovals -= key
            pendingValues[key] = value
            return this
        }

        override fun remove(key: String): VaultPreferences.Editor {
            pendingValues -= key
            pendingRemovals += key
            return this
        }

        override fun commit(): Boolean {
            val touchedKeys = pendingValues.keys + pendingRemovals
            commits += touchedKeys.toSet()
            if (failNextCommit) {
                failNextCommit = false
                return false
            }
            pendingRemovals.forEach(values::remove)
            values.putAll(pendingValues)
            return true
        }
    }
}

private class JvmAesGcmSecretCipher : SecretCipher {

    private val key = SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
    private val random = SecureRandom()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): String {
        return try {
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            val ciphertext = cipher.doFinal(plaintext)
            listOf(
                "v1",
                Base64.getEncoder().withoutPadding().encodeToString(iv),
                Base64.getEncoder().withoutPadding().encodeToString(ciphertext),
            ).joinToString(":")
        } catch (e: Exception) {
            throw SecretCipherException("Encryption failed", e)
        }
    }

    override fun decrypt(payload: String, associatedData: ByteArray): ByteArray {
        return try {
            val parts = payload.split(":", limit = 3)
            require(parts.size == 3 && parts[0] == "v1")
            val iv = Base64.getDecoder().decode(parts[1])
            val ciphertext = Base64.getDecoder().decode(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SecretCipherException("Decryption failed", e)
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
