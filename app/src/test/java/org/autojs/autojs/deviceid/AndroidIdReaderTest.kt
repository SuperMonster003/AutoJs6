package org.autojs.autojs.deviceid

import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidIdReaderTest {

    private val unavailable: (String) -> AbstractShell.Result = { error("Permission unavailable") }
    private val unexpected: (String) -> AbstractShell.Result = { error("Must not request elevated access") }

    @Test
    fun ordinaryReadsPreserveTheAppValueWithoutElevatedAccess() {
        assertEquals("app-id", AndroidIdReader.read(false, 33, 10, { "app-id" }, unexpected, unexpected))
        assertNull(AndroidIdReader.read(false, 33, 0, { null }, unexpected, unexpected))
    }

    @Test
    fun preOreoIgnoresTheFlagWithoutCheckingPermissions() {
        for (sdk in listOf(24, 25)) {
            for (useSystemId in listOf(false, true)) {
                assertEquals("legacy-id", AndroidIdReader.read(useSystemId, sdk, 10, { "legacy-id" }, unexpected, unexpected))
            }
        }
    }

    @Test
    fun oreoAndLaterPreferShizukuAndExplicitlySelectTheAppsAndroidUser() {
        for (sdk in listOf(26, 33, 37)) {
            val value = AndroidIdReader.read(true, sdk, 10, { error("Must not read the app ID") }, { command ->
                assertEquals("settings --user 10 get secure android_id", command)
                AbstractShell.Result(0, "  605796BD003B5DB0\n", "")
            }, unexpected)
            assertEquals("605796bd003b5db0", value)
        }
    }

    @Test
    fun unavailableShizukuFallsBackToRoot() {
        val value = AndroidIdReader.read(true, 33, 0, { error("Must not read the app ID") }, unavailable) { command ->
            assertEquals("settings --user 0 get secure android_id", command)
            AbstractShell.Result(0, "123\n", "")
        }
        assertEquals("123", value)
    }

    @Test
    fun failedCommandCannotReturnAnIdEvenWhenStdoutLooksValid() {
        val value = AndroidIdReader.read(true, 33, 0, { error("Must not read the app ID") }, {
            AbstractShell.Result(1, "deadbeef", "Permission denied")
        }, { AbstractShell.Result(0, "1234", "") })
        assertEquals("1234", value)
    }

    @Test
    fun missingOrMalformedValuesFailInsteadOfReturningTheAppId() {
        for (output in listOf("", "\n", "null", "Permission denied", "12345678901234567", "123\n456")) {
            val failure = assertThrows(IllegalStateException::class.java) {
                AndroidIdReader.read(true, 33, 0, { error("Must not read the app ID") }, {
                    AbstractShell.Result(0, output, "")
                }, unavailable)
            }
            assertEquals(2, failure.suppressed.size)
            assertTrue(failure.suppressed[0].message!!.contains("invalid Android ID"))
        }
    }

    @Test
    fun deniedPermissionsReportBothFailures() {
        val failure = assertThrows(IllegalStateException::class.java) {
            AndroidIdReader.read(true, 33, 0, { error("Must not read the app ID") }, unavailable, unavailable)
        }
        assertTrue(failure.message!!.contains("authorized Shizuku or Root is required"))
        assertTrue(failure.suppressed[0].message!!.startsWith("Shizuku:"))
        assertTrue(failure.suppressed[1].message!!.startsWith("Root:"))
    }

    @Test
    fun scriptInterruptionDoesNotTryAnotherPrivilegeChannel() {
        assertThrows(ScriptInterruptedException::class.java) {
            AndroidIdReader.read(true, 33, 0, { error("Must not read the app ID") }, {
                throw InterruptedException("Script stopped")
            }, unexpected)
        }
    }
}
