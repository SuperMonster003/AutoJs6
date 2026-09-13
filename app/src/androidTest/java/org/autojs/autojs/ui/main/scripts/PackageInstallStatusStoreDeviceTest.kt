package org.autojs.autojs.ui.main.scripts

import android.content.pm.PackageInstaller
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PackageInstallStatusStoreDeviceTest {
    @Test fun callbacksWithMismatchedSessionOrIntentIdentityAreIgnored() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = PackageInstallStatusCoordinator
        val used = coordinator.records(context).map { it.sessionId }.toSet() +
            context.packageManager.packageInstaller.mySessions.map { it.sessionId }
        val id = (1..1000).first { it !in used }
        coordinator.register(context, id, "example.identity.test")
        try {
            val before = coordinator.records(context)
            val valid = coordinator.callbackIntent(context, id)
                .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
                .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_SUCCESS)
            val wrongSession = android.content.Intent(valid).putExtra(PackageInstaller.EXTRA_SESSION_ID, id + 1)
            val wrongData = android.content.Intent(valid).setData(android.net.Uri.parse("autojs6://wrong/$id"))
            val wrongAction = android.content.Intent(valid).setAction("example.WRONG")
            for (callback in listOf(wrongSession, wrongData, wrongAction)) {
                coordinator.receive(context, callback)
                assertEquals(before, coordinator.records(context))
            }
        } finally {
            coordinator.acknowledge(context, id)
        }
    }

    @Test fun separateSessionsSurviveReconstructionAndAcknowledgement() {
        val file = File.createTempFile("install-status-test", ".json",
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        file.delete()
        try {
            val original = PackageInstallStatusStore(file)
            original.register(7, "example.first")
            original.register(8, "example.second")
            original.update(7, PackageInstaller.STATUS_PENDING_USER_ACTION, "", "first-token")
            original.update(8, PackageInstaller.STATUS_PENDING_USER_ACTION, "", "second-token")
            val rebuilt = PackageInstallStatusStore(file)
            assertEquals(original.records(), rebuilt.records())
            assertNull(rebuilt.update(9, PackageInstaller.STATUS_SUCCESS, "unknown session", null))
            rebuilt.update(7, PackageInstaller.STATUS_FAILURE_ABORTED, "cancelled", null)
            assertNull(rebuilt.update(7, PackageInstaller.STATUS_PENDING_USER_ACTION, "late", "first-token"))
            rebuilt.remove(7)
            val surviving = PackageInstallStatusStore(file).records().single()
            assertEquals(8, surviving.sessionId)
            assertEquals("second-token", surviving.confirmationKey)
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, surviving.status)
            assertEquals(1, surviving.generation)
        } finally {
            android.util.AtomicFile(file).delete()
        }
    }
}
