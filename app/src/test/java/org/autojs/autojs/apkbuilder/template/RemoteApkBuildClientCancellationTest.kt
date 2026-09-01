package org.autojs.autojs.apkbuilder.template

import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CancellationException

class RemoteApkBuildClientCancellationTest {

    @Test
    fun outcomeForCaughtFailure_returnsCancelledForCancellationException() {
        val outcome = RemoteApkBuildClient.outcomeForCaughtFailure(
            error = CancellationException("cancelled"),
            cancelRequested = false,
        )

        assertEquals(ApkBuildResult.STATUS_CANCELLED, outcome?.status)
    }

    @Test
    fun outcomeForCaughtFailure_returnsCancelledWhenSignalWasRaised() {
        val outcome = RemoteApkBuildClient.outcomeForCaughtFailure(
            error = IllegalStateException("failure after cancellation"),
            cancelRequested = true,
        )

        assertEquals(ApkBuildResult.STATUS_CANCELLED, outcome?.status)
    }

    @Test
    fun outcomeForCaughtFailure_doesNotConsumeUnrelatedFailure() {
        val outcome = RemoteApkBuildClient.outcomeForCaughtFailure(
            error = IllegalStateException("failure"),
            cancelRequested = false,
        )

        assertNull(outcome)
    }
}
