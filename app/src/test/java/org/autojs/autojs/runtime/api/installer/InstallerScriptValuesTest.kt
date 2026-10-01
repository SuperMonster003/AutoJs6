package org.autojs.autojs.runtime.api.installer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InstallerScriptValuesTest {

    @Test fun optionalAdvancedObservationsPassThroughWithoutChangingInstallationSuccess() {
        for (status in InstallerContract.DEXOPT_STATUSES) {
            val completion = InstallerJson.decodeCompletion("""{"ok":true,"packageName":"com.example.app","updateOwner":null,
                "dexopt":{"filter":"speed-profile","status":"$status","message":"platform observation"}}""")
            val result = InstallerScriptValues.installResult(completion.single)
            assertEquals(true, result["ok"])
            assertTrue(result.containsKey("updateOwner"))
            assertNull(result["updateOwner"])
            assertEquals(status, (result["dexopt"] as Map<*, *>)["status"])
        }
        val absent = InstallerScriptValues.installResult(InstallerJson.decodeCompletion("""{"ok":true}""").single)
        assertFalse(absent.containsKey("updateOwner"))
        assertFalse(absent.containsKey("dexopt"))
        assertEquals("optimizing", InstallerJson.decodeSessionStatus("""{"state":"optimizing"}""").state)
    }

    @Test
    fun integerFieldsKeepLongPrecisionAndNestedNullsKeepTheirKeys() {
        val value = InstallerScriptValues.toPlain("""{"versionCode":9223372036854775807,"old":-9223372036854775808,"list":[1,1e3,0.25,true,null,"x"],"optional":null}""") as Map<*, *>
        assertEquals(listOf("versionCode", "old", "list", "optional"), value.keys.toList())
        assertEquals(Long.MAX_VALUE, value["versionCode"])
        assertEquals(Long.MIN_VALUE, value["old"])
        assertEquals(listOf(1L, 1000L, 0.25, true, null, "x"), value["list"])
        assertTrue(value.containsKey("optional"))
        assertNull(value["optional"])
        assertNull(InstallerScriptValues.toPlain(null as JsonElement?))
        assertNull(InstallerScriptValues.toPlain("null"))
    }

    @Test
    fun originalInputShapeKeepsASingleItemBatchAnArray() {
        val completion = InstallerJson.decodeCompletion("""{"ok":true,"packageName":"com.example.app","versionCode":4294967297,"authorizer":"shizuku","interaction":"silent","durationMillis":25}""")
        val single = InstallerScriptValues.completion(completion, false)
        val batch = InstallerScriptValues.completion(completion, true)
        assertTrue(single is Map<*, *>)
        assertEquals(listOf(single), batch)
        assertEquals(4294967297L, (single as Map<*, *>)["versionCode"])
        val two = InstallerJson.decodeCompletion("""{"results":[{"ok":true},{"ok":false}]}""")
        assertEquals(2, (InstallerScriptValues.completion(two, true) as List<*>).size)
        invalid { InstallerScriptValues.completion(two, false) }
    }

    @Test
    fun failedBatchItemsKeepNormalizedErrorsAndSystemDetails() {
        val completion = InstallerJson.decodeCompletion("""{"results":[{"ok":true,"packageName":"com.example.good"},{"ok":false,"packageName":"com.example.bad","error":{"code":"INSTALL_FAILED","message":"failed","status":-1,"systemMessage":"INSTALL_FAILED_VERSION_DOWNGRADE","authorizer":"root","retryable":false}}]}""")
        val results = InstallerScriptValues.completion(completion, true) as List<*>
        val failure = results[1] as Map<*, *>
        val error = failure["error"] as Map<*, *>
        assertEquals(false, failure["ok"])
        assertEquals("InstallerError", error["name"])
        assertEquals("INSTALL_FAILED", error["code"])
        assertEquals(-1L, error["status"])
        assertEquals("INSTALL_FAILED_VERSION_DOWNGRADE", error["systemMessage"])
        assertEquals("com.example.bad", error["packageName"])
        assertEquals("root", error["authorizer"])
        assertEquals(false, error["retryable"])
    }

    @Test
    fun deletionUpdatesAndNotesDoNotDependOnStaleWireJson() {
        val original = InstallerJson.decodeCompletion("""{"ok":true,"sourceDeleted":false,"notes":["old"],"extension":{"flag":true}}""").single
        val value = InstallerScriptValues.installResult(original.copy(sourceDeleted = true, notes = listOf("host deleted source")))
        assertEquals(true, value["sourceDeleted"])
        assertEquals(listOf("host deleted source"), value["notes"])
        assertEquals(mapOf("flag" to true), value["extension"])
    }

    @Test
    fun inspectKeepsSplitInstalledAabAndIconDataAsPlainValues() {
        val archive = InstallerScriptValues.json("""{
            "format":"aab","installable":false,"packageName":"com.example.app","versionCode":4294967297,
            "size":2147483648,"modules":["base","feature"],"problems":["use bundletool"],"icon":"cG5n",
            "splits":[{"name":"base.apk","size":2000,"selected":true,"reason":"base"}],
            "installed":{"versionName":"1.0","versionCode":4294967296,"signerMatch":"match","installer":"com.example.store"},
            "signatureSchemes":["v2","v3.1"]
        }""") as Map<*, *>
        assertEquals("aab", archive["format"])
        assertEquals(false, archive["installable"])
        assertEquals(2147483648L, archive["size"])
        assertEquals(listOf("base", "feature"), archive["modules"])
        assertEquals("cG5n", archive["icon"])
        assertEquals(4294967296L, (archive["installed"] as Map<*, *>)["versionCode"])
        assertEquals(2000L, ((archive["splits"] as List<*>).single() as Map<*, *>)["size"])
        assertEquals(listOf("v2", "v3.1"), archive["signatureSchemes"])
    }

    @Test
    fun uninstallAuthorizerAndUsersExposePlainObjects() {
        val result = InstallerJson.decodeUninstallResult("""{"ok":false,"packageName":"com.example.app","authorizer":"none","error":{"code":"USER_CANCELLED","message":"cancelled","status":1}}""")
        val plain = InstallerScriptValues.uninstallResult(result)
        assertEquals("com.example.app", plain["packageName"])
        assertEquals("USER_CANCELLED", (plain["error"] as Map<*, *>)["code"])
        val authorizer = InstallerScriptValues.authorizer(InstallerJson.decodeAuthorizerState("""{"available":true,"running":true,"granted":false,"reason":"permission"}"""), "shizuku")
        assertEquals("shizuku", authorizer["name"])
        assertEquals(false, authorizer["granted"])
        assertEquals("permission", authorizer["reason"])
        assertEquals(listOf(mapOf("id" to 10L, "name" to "工作资料", "primary" to false, "running" to true)), InstallerScriptValues.users(listOf(InstallerJson.DeviceUser(10, "工作资料", false, true))))
        val error = InstallerScriptValues.error(InstallerError("CANCELLED", "stopped", false))
        assertFalse(error.containsKey("status"))
        assertFalse(error.containsKey("systemMessage"))
    }

    @Test
    fun malformedOversizedAndUnrepresentableResultsAreRejected() {
        listOf("", " ", "{bad", "[1,", "{\"a\":1} trailing", "9223372036854775808", "1e309").forEach {
            invalid { InstallerScriptValues.toPlain(it) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY).forEach { invalid { InstallerScriptValues.toPlain(JsonPrimitive(it)) } }
        val oversized = JsonObject().apply { addProperty("text", "界".repeat(InstallerContract.MAX_JSON_BYTES / 3)) }
        assertTrue(oversized.toString().length < InstallerContract.MAX_JSON_BYTES)
        invalid { InstallerScriptValues.toPlain(oversized.toString()) }
        var nested: JsonElement = JsonPrimitive(true)
        repeat(66) { nested = JsonArray().apply { add(nested) } }
        invalid { InstallerScriptValues.toPlain(nested) }
    }

    private fun invalid(block: () -> Any?): String {
        try {
            block()
        } catch (e: InstallerPluginException) {
            assertEquals(e.message, InstallerErrorCodes.INTERNAL, e.code)
            assertFalse(e.retryable)
            return e.error.message
        }
        fail("expected INTERNAL")
        throw AssertionError()
    }
}
