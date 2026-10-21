package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * docs/settings/ROADMAP.md P6.5 (D23 / T21): the device key, the envelope of a sensitive category file, and the writer
 * and reader around it.
 * zh-CN: ROADMAP P6.5 (D23 / T21): 设备密钥, 敏感分类文件的信封, 以及使用它们的写入方与读取方.
 */
class SettingsSealedFileTest {

    private val key = SettingsDeviceKey.fromAndroidId("0123456789abcdef")

    private val otherKey = SettingsDeviceKey.fromAndroidId("fedcba9876543210")

    private val text = SettingsSnapshotCodec.categoryText(
        SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "ghp_secret")))),
    )

    @Test
    fun hkdfMatchesRfc5869() {
        val ikm = ByteArray(22) { 0x0b }
        val case1 = SettingsDeviceKey.hkdf(ikm, hex("000102030405060708090a0b0c"), hex("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), case1)
        val case3 = SettingsDeviceKey.hkdf(ikm, ByteArray(0), ByteArray(0), 42)
        assertArrayEquals(hex("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"), case3)
    }

    @Test
    fun theDeviceKeyIsFixedPerAndroidIdAndNamedByItsFingerprint() {
        assertEquals(key.fingerprint, SettingsDeviceKey.fromAndroidId(" 0123456789ABCDEF ").fingerprint)
        assertNotEquals(key.fingerprint, otherKey.fingerprint)
        assertTrue(Regex("[0-9a-f]{16}").matches(key.fingerprint))
        assertEquals("SettingsDeviceKey(${key.fingerprint})", key.toString())
        assertThrows(IllegalArgumentException::class.java) { SettingsDeviceKey.fromAndroidId(" ") }
        assertThrows(IllegalArgumentException::class.java) { SettingsDeviceKey.ofBytes(ByteArray(16)) }
    }

    @Test
    fun anEnvelopeOpensWithItsKeyOnly() {
        val sealed = SettingsSealedFile.seal("credentials", text, key)
        assertFalse(sealed.contains("ghp_secret") || sealed.contains("github_auth") || sealed.contains("token"))
        val json = SettingsJson.parse(sealed)
        assertTrue(SettingsSealedFile.isSealed(json))
        assertFalse(SettingsSealedFile.isSealed(SettingsJson.parse(text)))
        assertEquals(listOf("id", "sealed"), (json as Map<*, *>).keys.toList())
        assertEquals(listOf("version", "cipher", "nonce", "data", "slots"), (json["sealed"] as Map<*, *>).keys.toList())
        assertEquals(text, SettingsSealedFile.open("credentials", json, key))
        // Every seal draws a new content key and nonces; each opens to the same text.
        val again = SettingsSealedFile.seal("credentials", text, key)
        assertNotEquals(sealed, again)
        assertEquals(text, SettingsSealedFile.open("credentials", SettingsJson.parse(again), key))
        assertTrue(SettingsSealedFile.canOpen(json, key))
        assertFalse(SettingsSealedFile.canOpen(json, otherKey))
        assertFalse(SettingsSealedFile.canOpen(json, null))
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", json, otherKey) })
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", json, null) })
    }

    @Test
    fun anAlteredEnvelopeIsInvalid() {
        val json = SettingsJson.parse(SettingsSealedFile.seal("credentials", text, key)) as Map<*, *>
        val sealed = json["sealed"] as Map<*, *>
        val slot = (sealed["slots"] as List<*>).single() as Map<*, *>

        fun changed(sealedChanges: Map<String, Any?> = emptyMap(), slotChanges: Map<String, Any?> = emptyMap(), id: Any? = "credentials"): Any {
            val envelope = LinkedHashMap<Any?, Any?>(sealed)
            envelope["slots"] = listOf(LinkedHashMap<Any?, Any?>(slot).apply { putAll(slotChanges) })
            envelope.putAll(sealedChanges)
            return linkedMapOf("id" to id, "sealed" to envelope)
        }

        val data = sealed["data"] as String
        val flipped = (if (data[0] == 'A') "B" else "A") + data.substring(1)
        listOf(
            changed(mapOf("data" to flipped)),
            changed(mapOf("nonce" to slot["nonce"])),
            changed(slotChanges = mapOf("key" to sealed["nonce"])),
            changed(mapOf("version" to 2)),
            changed(mapOf("cipher" to "AES-128-CBC")),
            changed(mapOf("data" to "not base64!")),
            changed(mapOf("slots" to "none")),
            changed(id = "other"),
            "not an envelope",
        ).forEach { assertEquals(it.toString(), Reason.INVALID_FILE, reasonOf { SettingsSealedFile.open("credentials", it, key) }) }
        // The category id is bound to the ciphertext: an envelope moved to another category does not open.
        assertEquals(Reason.INVALID_FILE, reasonOf { SettingsSealedFile.open("other", changed(id = "other"), key) })
        // Slots of kinds this version does not know are skipped.
        val withPassword = changed(mapOf("slots" to listOf(mapOf("kind" to "password", "kdf" to "PBKDF2-HMAC-SHA256"), slot)))
        assertEquals(text, SettingsSealedFile.open("credentials", withPassword, key))
    }

    @Test
    fun theWriterSealsSensitiveCategoriesAndTheReaderOpensThem() {
        val snapshot = SettingsSnapshot(
            listOf(
                SettingsSnapshot.Category("appearance", linkedMapOf("default" to linkedMapOf("a" to TypedValue("int", 1)))),
                SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "ghp_secret")))),
            ),
        )
        val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")
        val device = SettingsArchiveManifest.Device("OPD2413", 35)
        // No plain form any more: without the key nothing is written.
        val refused = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) { SettingsArchiveWriter.write(refused, snapshot, "t", host, device) }
        assertEquals(0, refused.size())
        val bytes = ByteArrayOutputStream().also { SettingsArchiveWriter.write(it, snapshot, "t", host, device, key = key) }.toByteArray()

        val reader = SettingsArchiveReader({ ByteArrayInputStream(bytes) }, key = key)
        assertEquals(listOf(false, true), reader.manifest().categories.map { it.sensitive })
        assertEquals(listOf(1, 1), reader.manifest().categories.map { it.entries })
        assertEquals(snapshot.categories, snapshot.categories.map { reader.category(it.id).category })
        assertFalse(reader.isLocked("credentials"))

        listOf(SettingsArchiveReader({ ByteArrayInputStream(bytes) }), SettingsArchiveReader({ ByteArrayInputStream(bytes) }, key = otherKey)).forEach {
            assertTrue(it.isLocked("credentials"))
            assertFalse(it.isLocked("appearance"))
            assertEquals(snapshot.categories.first(), it.category("appearance").category)
            assertEquals(Reason.LOCKED, reasonOf { it.category("credentials") })
            assertEquals(Reason.LOCKED, reasonOf { it.categories(listOf("appearance", "credentials")) })
        }
        assertEquals(Reason.MISSING_FILE, reasonOf { reader.isLocked("other") })
    }

    private fun reasonOf(block: () -> Unit): Reason = assertThrows(SettingsArchiveException::class.java) { block() }.reason

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
