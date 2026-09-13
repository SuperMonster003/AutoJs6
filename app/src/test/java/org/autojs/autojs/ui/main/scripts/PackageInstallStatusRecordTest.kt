package org.autojs.autojs.ui.main.scripts

import android.content.pm.PackageInstaller
import org.junit.Assert.*
import org.junit.Test

class PackageInstallStatusRecordTest {
    @Test fun delayedPendingCallbacksCannotReviveCompletedSessions() {
        for (terminal in listOf(PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_CONFLICT)) {
            val waiting = PackageInstallStatusRecord(7, "example.first", 1)
                .updated(PackageInstaller.STATUS_PENDING_USER_ACTION, "", "confirmation")!!
            val completed = waiting.updated(terminal, "result", null)!!
            assertTrue(completed.isTerminal)
            assertNull(completed.confirmationKey)
            assertNull(completed.updated(PackageInstaller.STATUS_PENDING_USER_ACTION, "late", "old-confirmation"))
            assertNull(completed.updated(PackageInstaller.STATUS_SUCCESS, "duplicate", null))
        }
    }
}
