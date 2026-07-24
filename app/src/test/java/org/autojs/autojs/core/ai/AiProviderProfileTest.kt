package org.autojs.autojs.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AiProviderProfileTest {

    @Test
    fun builtInProviderIdsAreStableAndComplete() {
        assertEquals(
            listOf(
                "openai",
                "anthropic",
                "gemini",
                "deepseek",
                "openrouter",
                "openai-compatible",
            ),
            AiProviderDefinition.entries.map { it.id },
        )
        AiProviderDefinition.entries.forEach { definition ->
            assertEquals(definition, AiProviderDefinition.requireById(definition.id.uppercase()))
        }
    }

    @Test
    fun profileNormalizationTrimsFieldsAndCanonicalizesUrl() {
        val id = UUID.randomUUID().toString()
        val profile = AiProviderProfile(
            id = "  ${id.uppercase()}  ",
            name = "  Personal OpenAI  ",
            provider = AiProviderDefinition.OPENAI,
            baseUrl = " https://API.OPENAI.COM:443/v1/ ",
            model = "  model-name  ",
        ).normalized()

        assertEquals(id, profile.id)
        assertEquals("Personal OpenAI", profile.name)
        assertEquals("https://api.openai.com/v1", profile.baseUrl)
        assertEquals("model-name", profile.model)
        assertEquals("openai", profile.providerId)
    }

    @Test
    fun urlValidationAllowsHttpsAndStrictLoopbackHttp() {
        val allowed = listOf(
            "https://example.com/v1",
            "http://localhost:11434/v1",
            "http://localhost.:11434/v1/",
            "http://127.0.0.1:8080",
            "http://127.255.255.255:8080/v1",
            "http://[::1]:8080/v1",
        )

        allowed.forEach { url ->
            assertTrue(AiProviderUrl.normalize(url).startsWith(url.substringBefore("://") + "://"))
        }
    }

    @Test
    fun urlValidationRejectsUnsafeOrAmbiguousUrls() {
        val rejected = listOf(
            "http://example.com/v1",
            "http://192.168.1.2/v1",
            "ftp://localhost/v1",
            "https://user@example.com/v1",
            "https://example.com/v1?api_key=secret",
            "https://example.com/v1#fragment",
            "https://example.com/v1?",
            "https://example.com/v1#",
            "not a url",
        )

        rejected.forEach { url ->
            org.junit.Assert.assertThrows(AiProviderValidationException::class.java) {
                AiProviderUrl.normalize(url)
            }
        }
    }

    @Test
    fun blankSecretInputMeansKeepAndReplacementToStringIsRedacted() {
        assertTrue(SecretUpdate.fromInput("  ") === SecretUpdate.Keep)
        val replacement = SecretUpdate.fromInput("super-secret-key")

        assertTrue(replacement is SecretUpdate.Replace)
        assertEquals("Replace([REDACTED])", replacement.toString())
        assertTrue("super-secret-key" !in replacement.toString())
    }

    @Test
    fun profileNormalizationRejectsControlCharacters() {
        val invalidProfiles = listOf(
            AiProviderProfile.create(
                name = "Profile",
                provider = AiProviderDefinition.OPENAI,
            ).copy(name = "Profile\nInjected"),
            AiProviderProfile.create(
                name = "Profile",
                provider = AiProviderDefinition.OPENAI,
            ).copy(model = "model\u007Fname"),
        )

        invalidProfiles.forEach { profile ->
            org.junit.Assert.assertThrows(AiProviderValidationException::class.java) {
                profile.normalized()
            }
        }
    }

    @Test
    fun validationErrorsExposeStableFieldAndCode() {
        val profileNameError = assertThrows(AiProviderValidationException::class.java) {
            AiProviderProfile.create(
                name = " ",
                provider = AiProviderDefinition.OPENAI,
            )
        }
        assertEquals(AiProviderValidationField.PROFILE_NAME, profileNameError.field)
        assertEquals(AiProviderValidationCode.REQUIRED, profileNameError.code)

        val baseUrlError = assertThrows(AiProviderValidationException::class.java) {
            AiProviderUrl.normalize("http://example.com/v1")
        }
        assertEquals(AiProviderValidationField.BASE_URL, baseUrlError.field)
        assertEquals(AiProviderValidationCode.INSECURE_TRANSPORT, baseUrlError.code)

        val providerError = assertThrows(AiProviderValidationException::class.java) {
            AiProviderDefinition.requireById("unknown")
        }
        assertEquals(AiProviderValidationField.PROVIDER, providerError.field)
        assertEquals(AiProviderValidationCode.UNSUPPORTED, providerError.code)
    }
}
