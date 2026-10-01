package org.autojs.autojs.runtime.api.installer

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InstallerScriptOptionsTest {

    @Test
    fun omittedOptionsUseContractDefaults() {
        val value = InstallerScriptOptions.install(null)
        assertEquals("auto", value.interaction)
        with(value.install) {
            assertEquals("auto", authorizer)
            assertFalse(allowDowngrade)
            assertFalse(allowTestOnly)
            assertFalse(bypassLowTargetSdk)
            assertNull(installer)
            assertEquals("current", user)
            assertFalse(deleteSource)
            assertTrue(continueOnError)
            assertEquals(1_800_000L, timeoutMillis)
            assertNull(validate())
        }
        val uninstall = InstallerScriptOptions.uninstall(null)
        assertEquals("auto", uninstall.authorizer)
        assertEquals("auto", uninstall.interaction)
        assertEquals("current", uninstall.user)
        assertFalse(uninstall.keepData)
        assertEquals(1_800_000L, uninstall.timeoutMillis)
    }

    @Test
    fun fullOptionsSeparateInteractionAndWireTimeout() {
        val options = InstallerScriptOptions.install(json("""{
            "authorizer":"shizuku","interaction":"silent","allowDowngrade":true,"allowTestOnly":true,
            "bypassLowTargetSdk":true,"installer":"com.example.store","user":10,"deleteSource":true,
            "continueOnError":false,"timeout":1234
        }"""))
        assertEquals("silent", options.interaction)
        with(options.install) {
            assertEquals("shizuku", authorizer)
            assertTrue(allowDowngrade)
            assertTrue(allowTestOnly)
            assertTrue(bypassLowTargetSdk)
            assertEquals("com.example.store", installer)
            assertEquals("10", user)
            assertTrue(deleteSource)
            assertFalse(continueOnError)
            assertEquals(1234L, timeoutMillis)
            assertNull(validate())
        }
        val uninstall = InstallerScriptOptions.uninstall(json("""{"authorizer":"root","interaction":"dialog","keepData":true,"user":"all","timeout":1}"""))
        assertEquals("root", uninstall.authorizer)
        assertEquals("dialog", uninstall.interaction)
        assertTrue(uninstall.keepData)
        assertEquals("all", uninstall.user)
        assertEquals(1L, uninstall.timeoutMillis)
    }

    @Test
    fun explicitNullDoesNotSelectADefault() {
        invalid { InstallerScriptOptions.install(JsonNull.INSTANCE) }
        invalid { InstallerScriptOptions.uninstall(JsonNull.INSTANCE) }
        listOf("authorizer", "interaction", "allowDowngrade", "allowTestOnly", "bypassLowTargetSdk", "installer", "user", "deleteSource", "continueOnError", "timeout").forEach {
            invalid { InstallerScriptOptions.install(json("""{"$it":null}""")) }
        }
        listOf("authorizer", "interaction", "keepData", "user", "timeout").forEach {
            invalid { InstallerScriptOptions.uninstall(json("""{"$it":null}""")) }
        }
        invalid { InstallerScriptOptions.authorizer(JsonNull.INSTANCE) }
        invalid { InstallerScriptOptions.enabled(JsonNull.INSTANCE) }
        invalid { InstallerScriptOptions.setDefault(JsonNull.INSTANCE) }
    }

    @Test
    fun unknownOptionsAndCoercedTypesAreRejected() {
        listOf("splits", "timeoutMillis", "allowTestOny", "keepData").forEach {
            assertTrue(invalid { InstallerScriptOptions.install(json("""{"$it":true}""")) }.contains("options.$it"))
        }
        assertTrue(invalid { InstallerScriptOptions.uninstall(json("""{"deleteSource":true}""")) }.contains("options.deleteSource"))
        listOf("[]", "42", "true", "\"auto\"").forEach { invalid { InstallerScriptOptions.install(json(it)) } }
        listOf("\"true\"", "1", "{}", "[]").forEach {
            assertTrue(invalid { InstallerScriptOptions.install(json("""{"allowDowngrade":$it}""")) }.contains("boolean"))
        }
        listOf("Notification", "Silent", " silent ").forEach {
            invalid { InstallerScriptOptions.install(json("""{"interaction":"$it"}""")) }
        }
        listOf("DHIZUKU", "ROOT", "", " root ").forEach { invalid { InstallerScriptOptions.authorizer(JsonPrimitive(it)) } }
        invalid { InstallerScriptOptions.authorizer(JsonPrimitive(true)) }
    }

    @Test
    fun usersAreNumericIntegersOrNamedTargets() {
        listOf("current", "all").forEach {
            assertEquals(it, InstallerScriptOptions.install(json("""{"user":"$it"}""" )).install.user)
        }
        listOf("0" to "0", "10.0" to "10", "1e2" to "100", "2147483647" to "2147483647").forEach { (input, expected) ->
            assertEquals(expected, InstallerScriptOptions.install(json("""{"user":$input}""")).install.user)
        }
        listOf("-1", "1.5", "2147483648", "2147483647.000001", "true", "\"10\"", "\"primary\"", "[]").forEach {
            invalid { InstallerScriptOptions.install(json("""{"user":$it}""")) }
        }
    }

    @Test
    fun timeoutRejectsFractionsOverflowAndNonFiniteNumbers() {
        assertEquals(1L, InstallerScriptOptions.timeout(JsonPrimitive(1)))
        assertEquals(7_200_000L, InstallerScriptOptions.timeout(JsonPrimitive(7_200_000)))
        assertEquals(1000L, InstallerScriptOptions.timeout(json("1e3")))
        listOf("0", "-1", "0.1", "1.000000000000000001", "7200001", "9223372036854775808", "1e309", "\"1000\"", "true").forEach {
            invalid { InstallerScriptOptions.timeout(json(it)) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            invalid { InstallerScriptOptions.timeout(JsonPrimitive(it)) }
        }
    }

    @Test
    fun installerPackageAndJsonBudgetsAreCheckedInUtf8Bytes() {
        assertNull(InstallerScriptOptions.install(json("""{"installer":""}""")).install.installer)
        val maximum = "com." + "a".repeat(251)
        assertEquals(maximum, InstallerScriptOptions.install(JsonObject().apply { addProperty("installer", maximum) }).install.installer)
        assertTrue(invalid { InstallerScriptOptions.install(JsonObject().apply { addProperty("installer", maximum + "a") }) }.contains("255 UTF-8 bytes"))
        assertTrue(invalid { InstallerScriptOptions.install(JsonObject().apply { addProperty("installer", "包".repeat(86)) }) }.contains("255 UTF-8 bytes"))
        listOf(" ", "com.example store", ".example", "com.1example", "com.example\u0000").forEach {
            invalid { InstallerScriptOptions.install(JsonObject().apply { addProperty("installer", it) }) }
        }
        val oversized = JsonObject().apply { addProperty("unknown", "界".repeat(InstallerContract.MAX_JSON_BYTES / 3)) }
        assertTrue(oversized.toString().length < InstallerContract.MAX_JSON_BYTES)
        assertTrue(invalid { InstallerScriptOptions.install(oversized) }.contains("65536 UTF-8 bytes"))
    }

    @Test
    fun defaultInstallerAndAuthorizerHelpersKeepTheirOwnShapes() {
        assertEquals("auto", InstallerScriptOptions.setDefault(null).authorizer)
        assertEquals("preferred", InstallerScriptOptions.setDefault(null).mode)
        assertEquals("root", InstallerScriptOptions.setDefault(json("""{"authorizer":"root"}""")).authorizer)
        assertTrue(InstallerScriptOptions.enabled(null))
        assertFalse(InstallerScriptOptions.enabled(JsonPrimitive(false)))
        assertEquals("none", InstallerScriptOptions.authorizer(JsonPrimitive("none")))
        assertEquals(42L, InstallerScriptOptions.timeout(null, 42L))
        invalid { InstallerScriptOptions.setDefault(json("""{"interaction":"silent"}""")) }
        invalid { InstallerScriptOptions.enabled(JsonPrimitive(1)) }
    }

    @Test
    fun v2OptionsAreParsedWithoutPretendingThatTheSelectedPluginSupportsThem() {
        val install = InstallerScriptOptions.install(json("""{"authorizer":"dhizuku","interaction":"notification"}"""))
        assertEquals("dhizuku", install.install.authorizer)
        assertEquals("notification", install.interaction)
        val persistent = InstallerScriptOptions.setDefault(json("""{"authorizer":"dhizuku","mode":"persistent"}"""))
        assertEquals("dhizuku", persistent.authorizer)
        assertEquals("persistent", persistent.mode)
        assertEquals("preferred", InstallerScriptOptions.setDefault(json("""{"mode":"preferred"}""")).mode)
        for (value in listOf("null", "true", "1", "\"Persistent\"", "\"none\"")) {
            invalid { InstallerScriptOptions.setDefault(json("""{"mode":$value}""")) }
        }
        invalid { InstallerScriptOptions.uninstall(json("""{"interaction":"notification"}""")) }
    }

    private fun json(value: String): JsonElement = JsonParser.parseString(value)

    private fun invalid(block: () -> Any?): String {
        try {
            block()
        } catch (e: InstallerPluginException) {
            assertEquals(e.message, InstallerErrorCodes.INVALID_ARGUMENT, e.code)
            assertFalse(e.retryable)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
