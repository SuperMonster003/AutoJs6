package org.autojs.autojs.core.accessibility

import android.accessibilityservice.AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Screenshots through the accessibility service (API 30+), the one place that turns the
 * framework's hardware buffer into a software bitmap and that retries the rate-limit error
 * (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`: the system allows one screenshot every few
 * hundred milliseconds). `automator.captureScreen()` consumes it; the Python host keeps its own
 * synchronous variant until roadmap P5.4 decides.
 *
 * zh-CN: 经无障碍服务截图 (API 30+), 把框架的硬件缓冲转成软件位图并重试限频错误
 * (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`: 系统每几百毫秒只允许一次截图) 的唯一位置.
 * `automator.captureScreen()` 消费它; Python Host 在路线图 P5.4 决定前保留自己的同步版本.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
@RequiresApi(Build.VERSION_CODES.R)
class A11yScreenshotter(private val service: AccessibilityService) {

    sealed interface Result {

        /** A software ARGB_8888 copy owned by the receiver. zh-CN: 接收方拥有的软件 ARGB_8888 副本. */
        class Success(val bitmap: Bitmap) : Result

        /** A framework error code, or [ERROR_INTERNAL] when the buffer could not be converted. zh-CN: 框架错误码, 或缓冲无法转换时的 [ERROR_INTERNAL]. */
        class Failure(val errorCode: Int) : Result

    }

    /**
     * One attempt on [displayId]; [callback] runs on [executor] with a software copy (mutable when
     * [mutable]) of the screenshot, the hardware buffer being closed either way.
     * zh-CN: 在 [displayId] 上尝试一次; [callback] 在 [executor] 上收到截图的软件副本 ([mutable] 时可变), 硬件缓冲无论如何都会关闭.
     */
    fun take(
        displayId: Int = Display.DEFAULT_DISPLAY,
        mutable: Boolean = true,
        executor: Executor = service.mainExecutor,
        callback: (Result) -> Unit,
    ) {
        val frameworkCallback = object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                callback(convert(screenshot, mutable))
            }

            override fun onFailure(errorCode: Int) {
                callback(Result.Failure(errorCode))
            }
        }
        try {
            service.takeScreenshot(displayId, executor, frameworkCallback)
        } catch (e: Throwable) {
            Log.w(TAG, "takeScreenshot failed", e)
            callback(Result.Failure(ERROR_INTERNAL))
        }
    }

    /**
     * [take] repeated every [retryDelayMillis] (on the main looper) while the framework reports
     * [ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT], at most [maxAttempts] times; any other outcome
     * is delivered as is.
     * zh-CN: 框架报告 [ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT] 时每隔 [retryDelayMillis] (主 looper 上) 重复 [take],
     * 至多 [maxAttempts] 次; 其它结果原样投递.
     */
    fun takeWithRetry(
        displayId: Int = Display.DEFAULT_DISPLAY,
        mutable: Boolean = true,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
        callback: (Result) -> Unit,
    ) {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1 ($maxAttempts)" }
        val handler = Handler(Looper.getMainLooper())
        fun attempt(remaining: Int) {
            take(displayId, mutable) { result ->
                if (result is Result.Failure && result.errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && remaining > 1) {
                    handler.postDelayed({ attempt(remaining - 1) }, retryDelayMillis)
                } else {
                    callback(result)
                }
            }
        }
        attempt(maxAttempts)
    }

    // @Hint by SuperMonster003 on Jun 9, 2023 (moved here on Sep 5, 2026).
    //  ! Copied to a software bitmap to avoid the exception below.
    //  # java.lang.IllegalStateException: unable to getPixel(), pixel access is not supported on Config#HARDWARE bitmaps.
    //  ! Reference: https://stackoverflow.com/questions/60462841/.
    //  ! zh-CN: 复制为软件位图以避免以下异常.
    //  # java.lang.IllegalStateException: 无法调用 getPixel(), Config#HARDWARE 位图不支持像素访问.
    private fun convert(screenshot: ScreenshotResult, mutable: Boolean): Result {
        val buffer = screenshot.hardwareBuffer
        return try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                ?: return Result.Failure(ERROR_INTERNAL)
            try {
                hardwareBitmap.copy(Bitmap.Config.ARGB_8888, mutable)?.let(Result::Success) ?: Result.Failure(ERROR_INTERNAL)
            } finally {
                hardwareBitmap.recycle()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Unable to convert the screenshot", e)
            Result.Failure(ERROR_INTERNAL)
        } finally {
            // The framework hands the buffer over to the callback, which must close it.
            // zh-CN: 框架把缓冲交给回调, 由回调负责关闭.
            runCatching { buffer.close() }
        }
    }

    companion object {

        private const val TAG = "A11yScreenshotter"

        /** Not a framework code: the screenshot could not be converted or requested. zh-CN: 非框架错误码: 截图无法转换或无法发起. */
        const val ERROR_INTERNAL = -1

        const val DEFAULT_RETRY_DELAY_MILLIS = 50L

        /** 60 x 50 ms = 3 s of rate-limit retries before giving up. zh-CN: 60 x 50 ms = 放弃前对限频错误最多重试 3 s. */
        const val DEFAULT_MAX_ATTEMPTS = 60

    }

}
