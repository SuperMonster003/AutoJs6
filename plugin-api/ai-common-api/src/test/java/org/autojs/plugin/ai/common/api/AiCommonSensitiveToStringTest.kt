package org.autojs.plugin.ai.common.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCommonSensitiveToStringTest {

    @Test
    fun errorSummaryDoesNotExposeProviderText() {
        val rendered = AiError(
            code = AiErrorCode.PROVIDER_FAILED,
            message = "sensitive-provider-error-message",
            retryDisposition = AiRetryDisposition.EXPLICIT_NEW_REQUEST_ONLY,
            providerCode = "sensitive-provider-code",
        ).toString()

        assertFalse(rendered.contains("sensitive-provider-error-message"))
        assertFalse(rendered.contains("sensitive-provider-code"))
        assertTrue(rendered.contains("code=${AiErrorCode.PROVIDER_FAILED}"))
        assertTrue(rendered.contains("messagePresent=true"))
        assertTrue(rendered.contains("providerCodePresent=true"))
    }

    @Test
    fun payloadSummaryDoesNotExposeInlineContentOrDigest() {
        val secret = "sensitive-inline-payload".toByteArray()
        val digest = "0123456789abcdef".repeat(4)
        val rendered = AiPayloadReference(
            mimeType = "text/plain",
            declaredLengthBytes = secret.size.toLong(),
            sha256 = digest,
            inlineBytes = secret,
            charset = "utf-8",
        ).toString()

        assertFalse(rendered.contains("sensitive-inline-payload"))
        assertFalse(rendered.contains(digest))
        assertTrue(rendered.contains("declaredLengthBytes=${secret.size}"))
        assertTrue(rendered.contains("sha256Present=true"))
        assertTrue(rendered.contains("inline=true"))
    }
}
