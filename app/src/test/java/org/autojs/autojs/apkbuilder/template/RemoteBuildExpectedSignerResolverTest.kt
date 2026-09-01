package org.autojs.autojs.apkbuilder.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class RemoteBuildExpectedSignerResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun resolveCustom_matchesPublishedDefaultCertificateDigest() {
        val expectedDigest = sourceAsset(DEFAULT_DIGEST_ASSET).readText().trim()

        val actualDigest = RemoteBuildExpectedSignerResolver.resolveCustom(
            keyStoreFile = sourceAsset(DEFAULT_KEY_STORE_ASSET),
            keyStorePassword = DEFAULT_CREDENTIAL,
            keyAlias = DEFAULT_CREDENTIAL,
        )

        assertEquals(expectedDigest, actualDigest)
    }

    @Test
    fun resolveCustom_detectsBksContentWhenTransportNameHasNoExtension() {
        val extensionless = temporaryFolder.newFile("keystore.bin")
        sourceAsset(DEFAULT_KEY_STORE_ASSET).copyTo(extensionless, overwrite = true)

        val digest = RemoteBuildExpectedSignerResolver.resolveCustom(
            keyStoreFile = extensionless,
            keyStorePassword = DEFAULT_CREDENTIAL,
            keyAlias = DEFAULT_CREDENTIAL,
        )

        assertEquals(sourceAsset(DEFAULT_DIGEST_ASSET).readText().trim(), digest)
    }

    @Test
    fun requireExactSignerDigestMatch_acceptsOnlyOneExactSigner() {
        val digest = "a".repeat(64)

        RemoteBuildExpectedSignerResolver.requireExactSignerDigestMatch(
            actualCertificateSha256 = listOf(digest),
            expectedCertificateSha256 = digest,
        )
    }

    @Test
    fun requireExactSignerDigestMatch_rejectsMissingWrongMultipleAndMalformedSigners() {
        val expected = "a".repeat(64)
        val rejected = listOf(
            emptyList(),
            listOf("b".repeat(64)),
            listOf(expected, expected),
            listOf("not-a-certificate-digest"),
        )

        rejected.forEach { actual ->
            assertThrows(IOException::class.java) {
                RemoteBuildExpectedSignerResolver.requireExactSignerDigestMatch(actual, expected)
            }
        }
        assertThrows(IOException::class.java) {
            RemoteBuildExpectedSignerResolver.requireExactSignerDigestMatch(
                actualCertificateSha256 = listOf(expected),
                expectedCertificateSha256 = "A".repeat(64),
            )
        }
    }

    @Test
    fun resolveCustom_usesFixedRedactedFailureForInvalidCredentialsOrAlias() {
        val error = assertThrows(IOException::class.java) {
            RemoteBuildExpectedSignerResolver.resolveCustom(
                keyStoreFile = sourceAsset(DEFAULT_KEY_STORE_ASSET),
                keyStorePassword = DEFAULT_CREDENTIAL,
                keyAlias = "missing-alias",
            )
        }

        assertEquals(
            "Remote build expected signing certificate could not be resolved.",
            error.message,
        )
    }

    private fun sourceAsset(name: String): File = sequenceOf(
        File("src/main/assets", name),
        File("app/src/main/assets", name),
    ).firstOrNull(File::isFile)
        ?: error("Missing test source asset: $name")

    companion object {
        private const val DEFAULT_KEY_STORE_ASSET = "default_key_store.bks"
        private const val DEFAULT_DIGEST_ASSET =
            "apk_builder_default_signer_certificate_sha256.txt"
        private const val DEFAULT_CREDENTIAL = "AutoJs6"
    }
}
