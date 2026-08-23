package org.autojs.autojs.core.plugin.python

import android.accessibilityservice.AccessibilityService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PythonHostScreenshotRetryPolicyTest {
    @Test
    fun retriesOnlyTheFrameworkIntervalThrottleWithinItsAttemptAndDeadlineBounds() {
        val enoughTime = TimeUnit.MILLISECONDS.toNanos(
            PythonHostScreenshotRetryPolicy.RETRY_DELAY_MILLIS + 1,
        )
        val intervalTooShort = AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT

        assertTrue(PythonHostScreenshotRetryPolicy.canRetry(intervalTooShort, 1, enoughTime))
        assertTrue(PythonHostScreenshotRetryPolicy.canRetry(intervalTooShort, 2, enoughTime))
        assertFalse(PythonHostScreenshotRetryPolicy.canRetry(intervalTooShort, 3, enoughTime))
        assertFalse(PythonHostScreenshotRetryPolicy.canRetry(intervalTooShort, 0, enoughTime))
        assertFalse(PythonHostScreenshotRetryPolicy.canRetry(1, 1, enoughTime))
        assertFalse(
            PythonHostScreenshotRetryPolicy.canRetry(
                intervalTooShort,
                1,
                TimeUnit.MILLISECONDS.toNanos(
                    PythonHostScreenshotRetryPolicy.RETRY_DELAY_MILLIS,
                ),
            ),
        )
    }
}
