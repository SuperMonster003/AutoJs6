package com.mcal.apksigner.utils

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Security
import java.security.cert.Certificate

/**
 * Keystore loading as the host's remote-build signer resolution uses it. BKS goes through the provider
 * named "BC" only (the platform's own BouncyCastle on Android; here the desktop BouncyCastle registered
 * under the same name), JKS through the bundled pure-Java reader.
 * zh-CN: 按宿主远程构建签名解析的用法测试密钥库加载. BKS 只经名为 "BC" 的 provider (Android 上是平台自带的
 * BouncyCastle, 这里是以同名注册的桌面版 BouncyCastle), JKS 经内置的纯 Java 读取器.
 */
class KeyStoreHelperTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `loads a keytool JKS through the bundled reader`() {
        val keyStore = KeyStoreHelper.loadJks(resource("keystore/keytool-jdk8.jks"), PASSWORD)
        assertEquals("JKS", keyStore.type)
        assertEquals("AutoJs6JKS", keyStore.provider.name)
        assertSampleEntry(keyStore)
    }

    @Test
    fun `loads SpongyCastle BKS v1 and v2 through the BC provider`() {
        listOf("keystore/spongy158-bks-v1.bks", "keystore/spongy158-bks-v2.bks").forEach { name ->
            val keyStore = KeyStoreHelper.loadBks(resource(name), PASSWORD)
            assertEquals(name, "BKS", keyStore.type)
            assertEquals(name, "BC", keyStore.provider.name)
            assertSampleEntry(keyStore)
        }
    }

    @Test
    fun `loadKeyStore dispatches on the file extension`() {
        assertEquals("JKS", KeyStoreHelper.loadKeyStore(resource("keystore/keytool-jdk8.jks"), PASSWORD).type)
        assertEquals("BKS", KeyStoreHelper.loadKeyStore(resource("keystore/spongy158-bks-v2.bks"), PASSWORD).type)
        assertTrue(KeyStoreHelper.validateKeystorePassword(resource("keystore/spongy158-bks-v2.bks"), "AutoJs6"))
        assertTrue(!KeyStoreHelper.validateKeystorePassword(resource("keystore/spongy158-bks-v2.bks"), "wrong"))
    }

    @Test
    fun `wrong passwords and mismatched formats fail with the fixed messages`() {
        val jksError = assertThrows(RuntimeException::class.java) {
            KeyStoreHelper.loadJks(resource("keystore/keytool-jdk8.jks"), "wrong".toCharArray())
        }
        assertEquals("Failed to load JKS keystore.", jksError.message)
        val bksError = assertThrows(RuntimeException::class.java) {
            KeyStoreHelper.loadBks(resource("keystore/keytool-jdk8.jks"), PASSWORD)
        }
        assertEquals("Failed to load BKS keystore.", bksError.message)
    }

    @Test
    fun `BKS needs the BC provider and nothing else`() {
        val bc = Security.getProvider("BC")
        Security.removeProvider("BC")
        try {
            val error = assertThrows(RuntimeException::class.java) {
                KeyStoreHelper.loadBks(resource("keystore/spongy158-bks-v2.bks"), PASSWORD)
            }
            assertEquals("Failed to load BKS keystore.", error.message)
            assertTrue(error.cause is java.security.NoSuchProviderException)
        } finally {
            Security.addProvider(bc)
        }
    }

    @Test
    fun `creates and reloads JKS and BKS files`() {
        val source = KeyStoreHelper.loadBks(resource("keystore/spongy158-bks-v2.bks"), PASSWORD)
        val alias = source.aliases().nextElement()
        val key = source.getKey(alias, PASSWORD)
        val chain = source.getCertificateChain(alias)
        listOf("created.jks" to "JKS", "created.bks" to "BKS").forEach { (name, type) ->
            val file = File(temporaryFolder.root, name)
            val created = KeyStoreHelper.createKeyStore(file, PASSWORD)
            assertEquals(name, type, created.type)
            created.setKeyEntry("created", key, PASSWORD, chain)
            KeyStoreHelper.writeKeyStore(created, file, PASSWORD)
            val reloaded = KeyStoreHelper.loadKeyStore(file, PASSWORD)
            assertEquals(name, type, reloaded.type)
            assertEquals(name, sha256(chain[0]), sha256(reloaded.getCertificate("created")))
            assertNotNull(name, reloaded.getKey("created", PASSWORD))
        }
    }

    private fun assertSampleEntry(keyStore: KeyStore) {
        assertEquals(listOf("autojs6"), keyStore.aliases().toList())
        assertTrue(keyStore.isKeyEntry("autojs6"))
        assertEquals("RSA", keyStore.getKey("autojs6", PASSWORD).algorithm)
        assertEquals(SAMPLE_CERTIFICATE_SHA256, sha256(keyStore.getCertificate("autojs6")))
    }

    private fun sha256(certificate: Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun resource(name: String): File {
        val url = KeyStoreHelperTest::class.java.classLoader!!.getResource(name) ?: error("Missing test resource $name")
        return File(url.toURI())
    }

    companion object {
        private val PASSWORD = "AutoJs6".toCharArray()

        // The same key + certificate sits in every sample (see docs/dev/evidence/apk-size-p4-bks-20260918.md).
        // zh-CN: 每个样本都是同一对密钥与证书 (见 docs/dev/evidence/apk-size-p4-bks-20260918.md).
        private const val SAMPLE_CERTIFICATE_SHA256 = "366f848e93cbcf4ed4cfec91f6fecb399ebb6b0728aa5ce7a46b63ad35030613"

        @JvmStatic
        @BeforeClass
        fun registerBouncyCastleAsBc() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

}
