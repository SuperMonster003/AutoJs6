package org.autojs.autojs.core.plugin.installer

import com.google.gson.JsonParser
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerJsonTest {

    @Test fun `source profile requests are sparse while legacy JSON keeps its exact defaults`() {
        fun encoded(value: InstallerJson.InstallOptions, profiles: Boolean) = JsonParser.parseString(
            InstallerJson.encodeInstallRequest("profiles", sources(1), "dialog", value, applySourceProfiles = profiles)).asJsonObject
        val defaults = InstallerJson.InstallOptions(providedOptions = emptySet())
        val sparse = encoded(defaults, true)
        assertTrue(sparse["applySourceProfiles"].asBoolean)
        assertEquals("dialog", sparse["interaction"].asString)
        assertEquals(setOf("continueOnError", "timeoutMillis"), sparse.getAsJsonObject("options").keySet())
        val legacy = encoded(defaults, false)
        assertFalse(legacy.has("applySourceProfiles"))
        assertEquals("auto", legacy.getAsJsonObject("options")["authorizer"].asString)
        assertFalse(legacy.getAsJsonObject("options")["deleteSource"].asBoolean)
        val values = InstallerJson.InstallOptions(authorizer = "auto", user = "current", deleteSource = false,
            providedOptions = setOf("authorizer", "user", "deleteSource", "installer", "installReason", "packageSource"))
        val options = encoded(values, true).getAsJsonObject("options")
        assertEquals("auto", options["authorizer"].asString); assertEquals("current", options["user"].asString)
        assertFalse(options["deleteSource"].asBoolean)
        for (field in listOf("installer", "installReason", "packageSource")) assertTrue(options[field].isJsonNull)
        for (field in listOf("installer", "installReason", "packageSource")) assertFalse(encoded(values, false).getAsJsonObject("options").has(field))
        assertThrows(IllegalArgumentException::class.java) { encoded(InstallerJson.InstallOptions(), true) }
        assertThrows(IllegalArgumentException::class.java) { encoded(InstallerJson.InstallOptions(providedOptions = setOf("notAnOption")), false) }
    }

    @Test fun `plugin JSON cannot manufacture the local source profile negotiation fact`() {
        val value = InstallerJson.decodeCompletion("""{"ok":true,"sourceProfilesEnabled":true,"sourceDeleteRequested":true}""")
        assertFalse(value.sourceProfilesEnabled)
        assertEquals(true, value.single.sourceDeleteRequested)
        for (invalid in listOf("null", "\"true\"", "1", "{}")) {
            assertNull(InstallerJson.decodeCompletion("""{"ok":true,"sourceDeleteRequested":$invalid}""").single.sourceDeleteRequested)
        }
    }

    @Test fun `advanced option omission is distinct from explicitly requested false and none`() {
        fun options(value: InstallerJson.InstallOptions) = JsonParser.parseString(
            InstallerJson.encodeInstallRequest("v3", sources(1), "auto", value)).asJsonObject.getAsJsonObject("options")
        val keys = setOf("grantAllRequestedPermissions", "requestUpdateOwnership", "dexopt", "installReason", "packageSource")
        assertTrue(options(InstallerJson.InstallOptions()).keySet().intersect(keys).isEmpty())
        val explicit = options(InstallerJson.InstallOptions(grantAllRequestedPermissions = false, requestUpdateOwnership = false,
            dexopt = "none", installReason = "unknown", packageSource = "unspecified"))
        assertTrue(explicit.keySet().containsAll(keys))
        assertFalse(explicit["grantAllRequestedPermissions"].asBoolean)
        assertFalse(explicit["requestUpdateOwnership"].asBoolean)
        assertEquals("none", explicit["dexopt"].asString)
        for (value in listOf(InstallerJson.InstallOptions(dexopt = "invalid"),
            InstallerJson.InstallOptions(installReason = "1"), InstallerJson.InstallOptions(packageSource = "local"))) {
            assertThrows(IllegalArgumentException::class.java) { options(value) }
        }
    }

    private fun sources(items: Int, splitsPerItem: Int = 1): List<InstallerJson.SourceEntry> =
        (0 until items).flatMap { item -> (0 until splitsPerItem).map { split -> InstallerJson.SourceEntry(item, "item$item-split$split.apk", 1000L + split) } }

    @Test
    fun `install request carries sources interaction and every option`() {
        val json = InstallerJson.encodeInstallRequest(
            id = "host-1",
            sources = listOf(InstallerJson.SourceEntry(0, "app.apk", 12345L), InstallerJson.SourceEntry(1, "base.apk", 1L), InstallerJson.SourceEntry(1, "split_config.arm64_v8a.apk", 2L)),
            interaction = InstallerContract.INTERACTION_SILENT,
            options = InstallerJson.InstallOptions(authorizer = InstallerContract.AUTHORIZER_SHIZUKU, allowDowngrade = true, installer = "com.android.vending", user = "10", deleteSource = true),
        )
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("host-1", root["id"].asString)
        assertEquals(InstallerContract.SOURCE_HOST, root[InstallerContract.FIELD_SOURCE_ORIGIN].asString)
        assertTrue(root[InstallerContract.FIELD_BATCH].asBoolean)
        assertEquals("silent", root["interaction"].asString)
        val sources = root.getAsJsonArray("sources")
        assertEquals(3, sources.size())
        assertEquals(1, sources[2].asJsonObject["item"].asInt)
        assertEquals("split_config.arm64_v8a.apk", sources[2].asJsonObject["displayName"].asString)
        val options = root.getAsJsonObject("options")
        assertEquals("shizuku", options["authorizer"].asString)
        assertTrue(options["allowDowngrade"].asBoolean)
        assertFalse(options["allowTestOnly"].asBoolean)
        assertEquals("com.android.vending", options["installer"].asString)
        assertEquals("10", options["user"].asString)
        assertTrue(options["deleteSource"].asBoolean)
        assertTrue(options["continueOnError"].asBoolean)
        assertEquals(InstallerContract.DEFAULT_SESSION_TIMEOUT_MILLIS, options["timeoutMillis"].asLong)
    }

    @Test
    fun `script origin is explicit and local plugin origins cannot cross the host API`() {
        val json = InstallerJson.encodeInstallRequest("script", sources(1), "silent", InstallerJson.InstallOptions(),
            origin = InstallerContract.SOURCE_SCRIPT)
        assertEquals("script", JsonParser.parseString(json).asJsonObject[InstallerContract.FIELD_SOURCE_ORIGIN].asString)
        for (origin in listOf("home", "external", "unknown")) {
            assertThrows(IllegalArgumentException::class.java) {
                InstallerJson.encodeInstallRequest("invalid", sources(1), "auto", InstallerJson.InstallOptions(), origin = origin)
            }
        }
    }

    @Test
    fun `explicit batch preserves a one item array while legacy callers derive shape`() {
        fun encoded(sources: List<InstallerJson.SourceEntry>, batch: Boolean? = null) = JsonParser.parseString(
            if (batch == null) InstallerJson.encodeInstallRequest("shape", sources, "auto", InstallerJson.InstallOptions())
            else InstallerJson.encodeInstallRequest("shape", sources, "auto", InstallerJson.InstallOptions(), isBatch = batch),
        ).asJsonObject
        assertTrue(encoded(sources(1), true)[InstallerContract.FIELD_BATCH].asBoolean)
        assertFalse(encoded(sources(1))[InstallerContract.FIELD_BATCH].asBoolean)
        assertFalse(encoded(sources(1, 2))[InstallerContract.FIELD_BATCH].asBoolean)
        assertTrue(encoded(sources(2))[InstallerContract.FIELD_BATCH].asBoolean)
        assertThrows(IllegalArgumentException::class.java) { encoded(sources(2), false) }
    }

    @Test
    fun `one item batch error remains a completion result`() {
        val completed = InstallerJson.decodeCompletion("""{"results":[{"ok":false,"error":{"code":"INVALID_PACKAGE","message":"bad archive"}}]}""")
        assertEquals(1, completed.results.size)
        assertFalse(completed.allOk)
        assertEquals(InstallerErrorCodes.INVALID_PACKAGE, completed.single.error?.code)
    }

    @Test
    fun `install request enforces the batch and split ceilings and the closed value sets`() {
        InstallerJson.encodeInstallRequest("ok", sources(InstallerContract.MAX_BATCH_SOURCES), InstallerContract.INTERACTION_AUTO, InstallerJson.InstallOptions())
        assertThrows(IllegalArgumentException::class.java) {
            InstallerJson.encodeInstallRequest("too-many", sources(InstallerContract.MAX_BATCH_SOURCES + 1), InstallerContract.INTERACTION_AUTO, InstallerJson.InstallOptions())
        }
        InstallerJson.encodeInstallRequest("ok", sources(1, InstallerContract.MAX_SPLITS_PER_PACKAGE), InstallerContract.INTERACTION_AUTO, InstallerJson.InstallOptions())
        assertThrows(IllegalArgumentException::class.java) {
            InstallerJson.encodeInstallRequest("too-many-splits", sources(1, InstallerContract.MAX_SPLITS_PER_PACKAGE + 1), InstallerContract.INTERACTION_AUTO, InstallerJson.InstallOptions())
        }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("empty", emptyList(), InstallerContract.INTERACTION_AUTO, InstallerJson.InstallOptions()) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("bad-interaction", sources(1), "notifications", InstallerJson.InstallOptions()) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("bad-authorizer", sources(1), "auto", InstallerJson.InstallOptions(authorizer = "dhizuku-unknown")) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("bad-user", sources(1), "auto", InstallerJson.InstallOptions(user = "owner")) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("bad-timeout", sources(1), "auto", InstallerJson.InstallOptions(timeoutMillis = 0L)) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeInstallRequest("blank-installer", sources(1), "auto", InstallerJson.InstallOptions(installer = " ")) }
        assertEquals("unknown user: -1", InstallerJson.InstallOptions(user = "-1").validate())
        assertNull(InstallerJson.InstallOptions(user = InstallerContract.USER_ALL).validate())
    }

    @Test
    fun `display names are truncated to the ceiling and the request stays below the JSON ceiling`() {
        val longName = "a".repeat(InstallerContract.MAX_DISPLAY_NAME_LENGTH + 50) + ".apk"
        val json = InstallerJson.encodeInstallRequest("long", listOf(InstallerJson.SourceEntry(0, longName, 1L)), "auto", InstallerJson.InstallOptions())
        val stored = JsonParser.parseString(json).asJsonObject.getAsJsonArray("sources")[0].asJsonObject["displayName"].asString
        assertEquals(InstallerContract.MAX_DISPLAY_NAME_LENGTH, stored.length)
        assertTrue(json.length <= InstallerContract.MAX_JSON_BYTES)
    }

    @Test
    fun `completion decodes a single result and a batch with a failed item`() {
        val single = InstallerJson.decodeCompletion("""{"ok":true,"packageName":"com.example","versionName":"1.2","versionCode":12,"previousVersionCode":10,"authorizer":"root","interaction":"silent","durationMillis":321,"sourceDeleted":false,"notes":["n1"]}""")
        assertTrue(single.allOk)
        val result = single.single
        assertEquals("com.example", result.packageName)
        assertEquals(12L, result.versionCode)
        assertEquals(10L, result.previousVersionCode)
        assertEquals("root", result.authorizer)
        assertEquals(listOf("n1"), result.notes)
        assertNull(result.error)

        val batch = InstallerJson.decodeCompletion("""{"results":[{"ok":true,"packageName":"a"},{"ok":false,"packageName":"b","error":{"code":"INSTALL_FAILED","message":"boom","status":4,"systemMessage":"INSTALL_FAILED_VERSION_DOWNGRADE"}}]}""")
        assertFalse(batch.allOk)
        assertEquals(2, batch.results.size)
        val failed = batch.results[1]
        assertFalse(failed.ok)
        assertEquals(InstallerErrorCodes.INSTALL_FAILED, failed.error?.code)
        assertEquals(4, failed.error?.status)
        assertEquals("INSTALL_FAILED_VERSION_DOWNGRADE", failed.error?.systemMessage)
        assertEquals("b", failed.error?.packageName)

        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodeCompletion("""{"results":[]}""") }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodeCompletion("not json") }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodeCompletion(null) }
    }

    @Test
    fun `errors normalize unknown codes and fall back to the default retry hint`() {
        val known = InstallerJson.decodeError("""{"code":"AUTHORIZER_UNAVAILABLE","message":"Shizuku is not running"}""")
        assertEquals(InstallerErrorCodes.AUTHORIZER_UNAVAILABLE, known.code)
        assertTrue(known.retryable)
        val unknown = InstallerJson.decodeError("""{"code":"WHATEVER","message":"x","retryable":true}""")
        assertEquals(InstallerErrorCodes.INTERNAL, unknown.code)
        assertTrue(unknown.message.startsWith("unknown error code WHATEVER"))
        assertTrue(unknown.retryable)
        val unreadable = InstallerJson.decodeError("[1,2]")
        assertEquals(InstallerErrorCodes.INTERNAL, unreadable.code)
        assertFalse(unreadable.retryable)
    }

    @Test
    fun `status detail and user documents decode with their contract fields`() {
        val status = InstallerJson.decodeSessionStatus("""{"state":"writing","index":1,"progress":0.5}""")
        assertEquals(InstallerContract.STAGE_WRITING, status.state)
        assertEquals(1, status.index)
        assertEquals(0.5f, status.progress!!, 0.0001f)
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodeSessionStatus("""{"state":"verifying"}""") }

        val stage = InstallerJson.decodeStageDetail("""{"index":2,"packageName":"c"}""")
        assertEquals(2, stage.index)
        assertEquals("c", stage.packageName)
        assertNull(InstallerJson.decodeStageDetail(null).index)
        val progress = InstallerJson.decodeProgressDetail("""{"index":0,"bytesWritten":10,"totalBytes":100}""")
        assertEquals(10L, progress.bytesWritten)
        assertEquals(100L, progress.totalBytes)

        val authorizer = InstallerJson.decodeAuthorizerState("""{"available":true,"running":false,"granted":false,"reason":"stopped"}""")
        assertTrue(authorizer.available)
        assertFalse(authorizer.running)
        assertEquals("stopped", authorizer.reason)
        assertTrue(InstallerJson.decodeGranted("""{"granted":true}"""))

        val state = InstallerJson.decodeDefaultInstallerState("""{"component":"a/b","isSelf":false,"method":"preferred","requiresClear":true}""")
        assertEquals("a/b", state.component)
        assertTrue(state.requiresClear)
        assertEquals("preferred", state.method)

        val users = InstallerJson.decodeUsers("""{"users":[{"id":0,"name":"Owner","primary":true,"running":true},{"id":10,"name":"Work","primary":false,"running":false}]}""")
        assertEquals(listOf(0, 10), users.map { it.id })
        assertTrue(users[0].primary)
        assertFalse(users[1].running)
        assertTrue(InstallerJson.decodeUsers("{}").isEmpty())
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodeUsers("""{"users":[{"name":"x"}]}""") }

        val uninstall = InstallerJson.decodeUninstallResult("""{"ok":false,"packageName":"d","authorizer":"none","error":{"code":"USER_CANCELLED","message":"cancelled"}}""")
        assertFalse(uninstall.ok)
        assertEquals(InstallerErrorCodes.USER_CANCELLED, uninstall.error?.code)
    }

    @Test
    fun `package archive info requires a known format and is returned normalized`() {
        val json = InstallerJson.decodePackageArchiveInfo("""{"format":"xapk", "installable": true, "packageName": "e"}""")
        assertEquals("""{"format":"xapk","installable":true,"packageName":"e"}""", json)
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodePackageArchiveInfo("""{"format":"ipa"}""") }
    }

    @Test
    fun `uninstall and authorizer requests validate their options`() {
        val json = InstallerJson.encodeUninstallRequest("com.example", InstallerJson.UninstallOptions(keepData = true, user = "all", interaction = "silent"))
        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("com.example", root["packageName"].asString)
        assertTrue(root["keepData"].asBoolean)
        assertEquals("all", root["user"].asString)
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeUninstallRequest(" ", InstallerJson.UninstallOptions()) }
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeUninstallRequest("x", InstallerJson.UninstallOptions(interaction = "notification")) }
        assertEquals("""{"authorizer":"root"}""", InstallerJson.encodeAuthorizerRequest("root"))
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeAuthorizerRequest("sudo") }
    }

    @Test
    fun `documents beyond the JSON ceiling are rejected`() {
        val huge = "{\"format\":\"apk\",\"label\":\"" + "x".repeat(InstallerContract.MAX_JSON_BYTES) + "\"}"
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.decodePackageArchiveInfo(huge) }
    }

    @Test
    fun `preferred default requests retain the legacy shape and persistent carries only its mode`() {
        assertEquals("""{"authorizer":"root"}""", InstallerJson.encodeDefaultInstallerRequest("root", "preferred"))
        assertEquals("""{"authorizer":"dhizuku","mode":"persistent"}""", InstallerJson.encodeDefaultInstallerRequest("dhizuku", "persistent"))
        assertThrows(IllegalArgumentException::class.java) { InstallerJson.encodeDefaultInstallerRequest("root", "all") }
    }

    @Test
    fun `persistent success requires the requested scope while legacy booleans remain unchanged`() {
        fun state(self: Boolean, method: String) = InstallerJson.decodeDefaultInstallerState("""{"isSelf":$self,"method":"$method"}""")
        assertTrue(state(true, "preferred").satisfies(true, "preferred"))
        assertTrue(state(false, "none").satisfies(false, "preferred"))
        assertFalse(state(true, "persistent").satisfies(false, "preferred"))
        assertFalse(state(true, "preferred").satisfies(true, "persistent"))
        assertTrue(state(true, "persistent").satisfies(true, "persistent"))
        assertTrue(state(true, "preferred").satisfies(false, "persistent"))
        assertFalse(state(true, "persistent").satisfies(false, "persistent"))
        assertThrows(IllegalStateException::class.java) { state(true, "unknown").satisfies(false, "persistent") }
        // V1 decoding itself was deliberately permissive; later state strings remain parseable.
        assertEquals("unknown", state(true, "unknown").method)
    }

    @Test
    fun `a persistent receipt is optional metadata and never substitutes for an observed method`() {
        val legacy = InstallerJson.decodeDefaultInstallerState("""{"isSelf":true,"method":"preferred"}""")
        assertFalse(legacy.persistentConfigured)
        val receipt = InstallerJson.decodeDefaultInstallerState("""{"isSelf":true,"method":"preferred","persistentConfigured":true}""")
        assertTrue(receipt.persistentConfigured)
        assertFalse(receipt.satisfies(true, "persistent"))
        assertTrue(receipt.satisfies(true, "preferred"))
    }
}
