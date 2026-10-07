package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackage
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.ui.edit.editor.ace.font.EcdsaP256CatalogSignatureVerifier
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogSignatureException
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.util.Base64

/** Real OpenSSL/Python outputs, independent of the Kotlin-generated repository fixtures. */
class ModelCatalogInteropTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun pythonSignedCatalogVerifiesAndMatchesTheActualPackagedModel() {
        val (catalogBytes, signatureBytes) = material()
        val publicBytes = resource("catalog/codegen_model_catalog_public_key_v1.der")
        assertEquals(PUBLIC_SHA, sha256(publicBytes))
        assertEquals(CATALOG_SHA, sha256(catalogBytes))
        verifier().verify(catalogBytes, signatureBytes)
        val catalog = ModelCatalogParser(Int.MAX_VALUE, expectedRepository = REPOSITORY).parse(catalogBytes)
        val packagedBytes = resource("models/tiny-test-v1.ajcgm")
        val parsed = ModelPackageReader().read(packagedBytes.inputStream())
        assertTrue("Shared tiny package must load in the real host reader: $parsed", parsed is ModelPackage)
        val manifest = (parsed as ModelPackage).manifest
        val model = requireNotNull(catalog.find(manifest.modelId))
        val version = model.versions.single()
        assertEquals(manifest.versionCode, version.versionCode)
        assertEquals(manifest.featureNamesHash, version.featureNamesHash)
        assertEquals(manifest.dataset.datasetId, version.datasetId)
        assertEquals(manifest.metrics, version.metrics)
        assertEquals(manifest.license, version.license)
        assertEquals(packagedBytes.size.toLong(), version.artifact.size)
        assertEquals(sha256(packagedBytes), version.artifact.sha256)
        assertTrue(catalog.canDownload(manifest.modelId, manifest.versionCode))
    }

    @Test
    fun exactBytesAndModelSpecificPinAreRequired() {
        val (catalog, signature) = material()
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier().verify(catalog + '\n'.code.toByte(), signature)
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier("p256-wrong-key-id").verify(catalog, signature)
        }
        assertThrows(ModelCatalogParseException::class.java) {
            ModelCatalogParser(Int.MAX_VALUE).parse(catalog)
        }
    }

    @Test
    fun pythonBootstrapIsDisplayOnlyUntilTheRemoteSignatureIsAccepted() {
        val (catalogBytes, signatureBytes) = material()
        var calls = 0
        val repository = ModelCatalogRepository(
            temporary.root,
            hostVersionCode = Int.MAX_VALUE,
            signatureVerifier = verifier(),
            bootstrapEnvelopeBytes = resource("catalog/codegen_model_catalog_v1.json"),
            expectedRepository = REPOSITORY,
            fetcher = FontCatalogFetcher { url, _ ->
                calls++
                if (url.endsWith(".sig.json")) signatureBytes else catalogBytes
            },
        )
        val bootstrap = requireNotNull(repository.loadBestAvailable())
        assertEquals(0, calls)
        assertFalse(bootstrap.remoteArtifactsAllowed)
        assertFalse(bootstrap.canDownload("autojs6.codegen.tiny-test", 1))
        assertTrue(repository.refresh().canDownload("autojs6.codegen.tiny-test", 1))
        assertEquals(2, calls)
        assertTrue(requireNotNull(repository.loadCached()).remoteArtifactsAllowed)
    }

    private fun material(): Pair<ByteArray, ByteArray> {
        val envelope = JsonParser.parseString(resource("catalog/codegen_model_catalog_v1.json").toString(Charsets.UTF_8)).asJsonObject
        assertEquals(1, envelope["format"].asInt)
        return Base64.getDecoder().decode(envelope["catalogBase64"].asString) to
            envelope["signature"].toString().toByteArray(Charsets.UTF_8)
    }

    private fun verifier(keyId: String = KEY_ID) = EcdsaP256CatalogSignatureVerifier(
        EcdsaP256CatalogSignatureVerifier.publicKeyFromDer(resource("catalog/codegen_model_catalog_public_key_v1.der")),
        expectedKeyId = keyId,
        expectedCatalogFileName = "catalog-v1.json",
    )

    private fun resource(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/codegen/$name")) {
        "Missing codegen test resource $name"
    }.use { it.readBytes() }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val REPOSITORY = "example/AutoJs6-Codegen-Test"
        private const val KEY_ID = "p256-0971601a168da7ee"
        private const val PUBLIC_SHA = "0971601a168da7ee9db0f4e23499454a69b9925e39ec1ce8fa353ab8ab839fa7"
        private const val CATALOG_SHA = "07af51c5ed5f512dcb902066aec53e2a0495e58c8df60f8643f9011ca0f89bb5"
    }
}
