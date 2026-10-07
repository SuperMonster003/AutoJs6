package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.ui.edit.editor.ace.font.EcdsaP256CatalogSignatureVerifier
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontCatalogFetcher
import java.io.File

/** Source and trust material are selected by the build, never by a saved user preference.
 * The production namespace cannot be paired with a different signing key or cache directory.
 * zh-CN: 来源与信任材料在构建时选择, 不读取用户偏好; 正式命名空间只能配正式公钥与缓存目录. */
class ModelCatalogConfiguration internal constructor(
    val expectedRepository: String,
    val expectedKeyId: String,
    val publicKeySha256: String,
    private val cacheDirectoryName: String,
) {
    val catalogUrl = "https://raw.githubusercontent.com/$expectedRepository/main/codegen-models/catalogs/catalog-v1.json"
    val signatureUrl = "https://raw.githubusercontent.com/$expectedRepository/main/codegen-models/catalogs/catalog-v1.sig.json"
    val isProduction: Boolean get() = expectedRepository == ModelCatalogParser.OFFICIAL_REPOSITORY

    init {
        require(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(expectedRepository)) { "Invalid catalog repository" }
        require(Regex("[A-Za-z0-9._-]{1,64}").matches(expectedKeyId)) { "Invalid catalog key id" }
        require(Regex("[0-9a-f]{64}").matches(publicKeySha256)) { "Invalid catalog public key pin" }
        if (expectedRepository.equals(ModelCatalogParser.OFFICIAL_REPOSITORY, ignoreCase = true)) {
            require(isProduction && expectedKeyId == PRODUCTION_KEY_ID && publicKeySha256 == PRODUCTION_PUBLIC_KEY_SHA256 &&
                cacheDirectoryName == PRODUCTION_CACHE_DIRECTORY) { "Production catalog trust cannot be overridden" }
        } else {
            require(Regex("catalog-debug-[a-z0-9][a-z0-9-]{0,63}").matches(cacheDirectoryName)) { "Debug catalogs require an isolated cache" }
        }
    }

    /** Release adapters call this before opening any resources or persistent state. */
    fun requireProduction(): ModelCatalogConfiguration = apply {
        require(isProduction) { "Release builds require the production model catalog" }
    }

    fun cacheDirectory(noBackupDirectory: File): File = File(File(noBackupDirectory, "codegen_models"), cacheDirectoryName)

    /** Test packages and their OFFICIAL registry entries must not survive a switch to production.
     * zh-CN: 测试包及登记表中的 OFFICIAL 来源记录不能在切回正式配置后被继承. */
    fun modelDirectory(noBackupDirectory: File): File = File(noBackupDirectory,
        if (isProduction) "codegen_models" else "codegen_models-debug-${debugProfile()}")

    fun selectionPreferenceKey(baseKey: String): String =
        if (isProduction) baseKey else "${baseKey}_debug_${debugProfile()}"

    private fun debugProfile(): String = cacheDirectoryName.removePrefix("catalog-debug-")

    /** Verifies the exact DER public key bytes before constructing the shared P-256 verifier. */
    fun signatureVerifier(publicKeyBytes: ByteArray): EcdsaP256CatalogSignatureVerifier = try {
        val der = publicKeyBytes.copyOf()
        require(ModelCatalogJson.digest(der) == publicKeySha256) { "Model catalog public key differs from its build pin" }
        EcdsaP256CatalogSignatureVerifier(
            publicKey = EcdsaP256CatalogSignatureVerifier.publicKeyFromDer(der),
            expectedKeyId = expectedKeyId,
            expectedCatalogFileName = "catalog-v1.json",
        )
    } catch (error: Exception) {
        throw ModelCatalogSignatureException("Unable to load the pinned model catalog public key", error)
    }

    /** Reads only signed local material; networking starts at the caller's explicit refresh. */
    fun createRepository(
        noBackupDirectory: File,
        hostVersionCode: Int,
        publicKeyBytes: ByteArray,
        bootstrapEnvelopeBytes: ByteArray,
        fetcher: FontCatalogFetcher = OkHttpFontCatalogFetcher(),
    ): ModelCatalogRepository = ModelCatalogRepository(
        directory = cacheDirectory(noBackupDirectory),
        catalogUrl = catalogUrl,
        signatureUrl = signatureUrl,
        hostVersionCode = hostVersionCode,
        signatureVerifier = signatureVerifier(publicKeyBytes),
        fetcher = fetcher,
        bootstrapEnvelopeBytes = bootstrapEnvelopeBytes,
        expectedRepository = expectedRepository,
    )

    companion object {
        const val PRODUCTION_KEY_ID = "p256-f1a540e052cf0865"
        const val PRODUCTION_PUBLIC_KEY_SHA256 = "f1a540e052cf086520619e429cd3e3240fa3f8a4b8fca46e3531358906a67de3"
        private const val PRODUCTION_CACHE_DIRECTORY = "catalog"

        val PRODUCTION = ModelCatalogConfiguration(
            expectedRepository = ModelCatalogParser.OFFICIAL_REPOSITORY,
            expectedKeyId = PRODUCTION_KEY_ID,
            publicKeySha256 = PRODUCTION_PUBLIC_KEY_SHA256,
            cacheDirectoryName = PRODUCTION_CACHE_DIRECTORY,
        )
    }
}
