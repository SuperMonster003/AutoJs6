package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import org.autojs.autojs.core.pref.archive.SettingsSealedFile.Access
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * docs/settings/ROADMAP.md P7.7 (D23 / T21): the password slot of a sealed category file, beside the device slot, and
 * the reader that asks for the password on another device.
 * zh-CN: ROADMAP P7.7 (D23 / T21): 加密分类文件中与设备槽并存的口令槽, 以及在其他设备上询问口令的读取方.
 */
class SettingsPasswordSlotTest {

    private val key = SettingsDeviceKey.fromAndroidId("0123456789abcdef")

    private val otherKey = SettingsDeviceKey.fromAndroidId("fedcba9876543210")

    /** The fewest iterations a reader accepts, so that the tests stay fast. */
    private val password = SettingsPassword.of("correct horse", SettingsPassword.MIN_ITERATIONS)!!

    private val wrongPassword = SettingsPassword.of("correct hearse", SettingsPassword.MIN_ITERATIONS)!!

    private val text = SettingsSnapshotCodec.categoryText(
        SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "ghp_secret")))),
    )

    @Test
    fun pbkdf2MatchesRfc7914AndTheJdk() {
        val case1 = SettingsPassword.pbkdf2("passwd".toByteArray(), "salt".toByteArray(), 1, 64)
        assertArrayEquals(hex("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"), case1)
        val case2 = SettingsPassword.pbkdf2("Password".toByteArray(), "NaCl".toByteArray(), 80_000, 64)
        assertArrayEquals(hex("4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d"), case2)
        // The JDK's PBKDF2WithHmacSHA256 (UTF-8 of the characters) agrees on a password beyond ASCII and a key of one block.
        val chars = "口令 pässwörd"
        val salt = hex("000102030405060708090a0b0c0d0e0f")
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(chars.toCharArray(), salt, 1_000, 256)).encoded
        assertArrayEquals(jdk, SettingsPassword.pbkdf2(chars.toByteArray(Charsets.UTF_8), salt, 1_000, 32))
        assertThrows(IllegalArgumentException::class.java) { SettingsPassword.pbkdf2(ByteArray(0), salt, 1, 32) }
    }

    @Test
    fun anEmptyPasswordIsNoPasswordAndAPasswordNeverPrintsItself() {
        assertNull(SettingsPassword.of(""))
        assertEquals(SettingsPassword.ITERATIONS, SettingsPassword.of(" ")!!.iterations)
        assertEquals("SettingsPassword", password.toString())
        assertThrows(IllegalArgumentException::class.java) { SettingsPassword.of("x", SettingsPassword.MIN_ITERATIONS - 1) }
        assertThrows(IllegalArgumentException::class.java) { SettingsPassword.of("x", SettingsPassword.MAX_ITERATIONS + 1) }
        assertThrows(IllegalArgumentException::class.java) { SettingsSealedFile.seal("credentials", text, null, null) }
    }

    @Test
    fun bothSlotsOpenTheSameEnvelope() {
        val sealed = SettingsSealedFile.seal("credentials", text, key, password)
        assertFalse(sealed.contains("ghp_secret") || sealed.contains("correct horse"))
        val json = SettingsJson.parse(sealed)
        val slots = ((json as Map<*, *>)["sealed"] as Map<*, *>)["slots"] as List<*>
        assertEquals(listOf("device", "password"), slots.map { (it as Map<*, *>)["kind"] })
        assertEquals(listOf("kind", "kdf", "iterations", "salt", "nonce", "key"), (slots[1] as Map<*, *>).keys.toList())
        assertEquals(SettingsPassword.KDF, (slots[1] as Map<*, *>)["kdf"])
        assertEquals(SettingsPassword.MIN_ITERATIONS, SettingsJson.toInt((slots[1] as Map<*, *>)["iterations"]))

        // This device opens it with its key alone, another one with the password alone or with both.
        assertEquals(text, SettingsSealedFile.open("credentials", json, key))
        assertEquals(text, SettingsSealedFile.open("credentials", json, otherKey, password))
        assertEquals(text, SettingsSealedFile.open("credentials", json, null, password))
        assertEquals(text, SettingsSealedFile.open("credentials", json, key, wrongPassword))
        assertEquals(Access.DEVICE, SettingsSealedFile.access(json, key))
        assertEquals(Access.PASSWORD, SettingsSealedFile.access(json, otherKey))
        assertEquals(Access.PASSWORD, SettingsSealedFile.access(json, null))
        assertEquals(Access.PLAIN, SettingsSealedFile.access(SettingsJson.parse(text), otherKey))
        assertTrue(SettingsSealedFile.hasPasswordSlot(json))
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", json, otherKey) })
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", json, otherKey, wrongPassword) })

        // A password-only envelope, and the NFC form: a composed and a decomposed "é" are the same password.
        val composed = SettingsPassword.of("caf" + 0xE9.toChar(), SettingsPassword.MIN_ITERATIONS)
        val decomposed = SettingsPassword.of("cafe" + 0x301.toChar(), SettingsPassword.MIN_ITERATIONS)
        val passwordOnly = SettingsJson.parse(SettingsSealedFile.seal("credentials", text, null, composed))
        assertEquals(listOf("password"), ((passwordOnly as Map<*, *>)["sealed"] as Map<*, *>)["slots"].let { s -> (s as List<*>).map { (it as Map<*, *>)["kind"] } })
        assertEquals(text, SettingsSealedFile.open("credentials", passwordOnly, key, decomposed))
        assertEquals(Access.PASSWORD, SettingsSealedFile.access(passwordOnly, key))
    }

    @Test
    fun anAlteredPasswordSlotIsInvalidAndAnUnknownKdfIsSkipped() {
        val json = SettingsJson.parse(SettingsSealedFile.seal("credentials", text, key, password)) as Map<*, *>
        val sealed = json["sealed"] as Map<*, *>
        val (device, slot) = (sealed["slots"] as List<*>).map { it as Map<*, *> }

        fun withPasswordSlot(changes: Map<String, Any?>): Any {
            val envelope = LinkedHashMap<Any?, Any?>(sealed)
            envelope["slots"] = listOf(device, LinkedHashMap<Any?, Any?>(slot).apply { putAll(changes) })
            return linkedMapOf("id" to "credentials", "sealed" to envelope)
        }

        listOf(
            mapOf("iterations" to SettingsPassword.MAX_ITERATIONS + 1),
            mapOf("iterations" to 1),
            mapOf("iterations" to "many"),
            mapOf("salt" to "AAAA"),
            mapOf("salt" to "not base64!"),
            mapOf("nonce" to null),
        ).forEach { assertEquals(it.toString(), Reason.INVALID_FILE, reasonOf { SettingsSealedFile.open("credentials", withPasswordSlot(it), otherKey, password) }) }
        // A changed salt derives another key: the password no longer opens it, which reads as a wrong password.
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", withPasswordSlot(mapOf("salt" to "AAAAAAAAAAAAAAAAAAAAAA==")), otherKey, password) })
        // A password slot of a kdf this version does not know is skipped like an unknown slot kind.
        val argon = withPasswordSlot(mapOf("kdf" to "Argon2id"))
        assertEquals(Access.LOCKED, SettingsSealedFile.access(argon, otherKey))
        assertEquals(Reason.LOCKED, reasonOf { SettingsSealedFile.open("credentials", argon, otherKey, password) })
        assertEquals(text, SettingsSealedFile.open("credentials", argon, key))
    }

    @Test
    fun theReaderOfAnotherDeviceChecksThePassword() {
        val snapshot = SettingsSnapshot(
            listOf(
                SettingsSnapshot.Category("appearance", linkedMapOf("default" to linkedMapOf("a" to TypedValue("int", 1)))),
                SettingsSnapshot.Category("credentials", linkedMapOf("github_auth" to linkedMapOf("token" to TypedValue("string", "ghp_secret")))),
            ),
        )
        val host = SettingsArchiveManifest.Host("org.autojs.autojs6", 5781, "6.8.0")
        val device = SettingsArchiveManifest.Device("OPD2413", 35)
        val withPassword = archive(snapshot, host, device, password)
        val deviceOnly = archive(snapshot, host, device, null)

        // This device reads it without the password.
        val here = SettingsArchiveReader({ ByteArrayInputStream(withPassword) }, key = key)
        assertEquals(Access.DEVICE, here.access("credentials"))
        assertEquals(snapshot.categories, snapshot.categories.map { here.category(it.id).category })

        // Another device is asked for the password; a wrong one is told apart and leaves the category locked.
        val there = SettingsArchiveReader({ ByteArrayInputStream(withPassword) }, key = otherKey)
        assertEquals(Access.PASSWORD, there.access("credentials"))
        assertEquals(Access.PLAIN, there.access("appearance"))
        assertTrue(there.isLocked("credentials"))
        assertEquals(Reason.LOCKED, reasonOf { there.category("credentials") })
        assertFalse(there.checkPassword("credentials", wrongPassword))
        assertFalse(there.checkPassword("appearance", password))
        assertTrue(there.checkPassword("credentials", password))
        val unlocked = there.withPassword(password)
        assertFalse(unlocked.isLocked("credentials"))
        assertEquals(snapshot.categories, unlocked.categories(listOf("appearance", "credentials")).map { it.category })
        assertFalse(there.withPassword(wrongPassword).isLocked("credentials"))
        assertEquals(Reason.LOCKED, reasonOf { there.withPassword(wrongPassword).category("credentials") })

        // The old format (a device slot only) reads as before, and has no password to ask for.
        val old = SettingsArchiveReader({ ByteArrayInputStream(deviceOnly) }, key = otherKey, password = password)
        assertEquals(Access.LOCKED, old.access("credentials"))
        assertTrue(old.isLocked("credentials"))
        assertFalse(old.checkPassword("credentials", password))
        assertEquals(Reason.LOCKED, reasonOf { old.category("credentials") })
        assertEquals(snapshot.categories, snapshot.categories.map { SettingsArchiveReader({ ByteArrayInputStream(deviceOnly) }, key = key).category(it.id).category })
        assertEquals(Reason.MISSING_FILE, reasonOf { there.checkPassword("other", password) })
    }

    private fun archive(snapshot: SettingsSnapshot, host: SettingsArchiveManifest.Host, device: SettingsArchiveManifest.Device, password: SettingsPassword?) =
        ByteArrayOutputStream().also { SettingsArchiveWriter.write(it, snapshot, "t", host, device, key = key, password = password) }.toByteArray()

    private fun reasonOf(block: () -> Unit): Reason = assertThrows(SettingsArchiveException::class.java) { block() }.reason

    private fun hex(text: String) = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
