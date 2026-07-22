package org.autojs.autojs.core.plugin.opencv

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

/** Cross-APK JNI smoke test. It is skipped when the OpenCV plugin is not installed. */
class OpenCvPluginHostDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedOpenCvPluginLoadsAndRunsThroughHostJavaAbi() {
        assumeTrue("OpenCV plugin is not installed", isPluginInstalled())

        val diagnostics = OpenCvPluginHost.discover(context).joinToString { discovered ->
            "${discovered.serviceInfo.packageName}/${discovered.serviceInfo.name}: ${discovered.error?.code ?: "eligible"}"
        }
        assertTrue(diagnostics, OpenCvPluginHost.isAvailable(context))

        val runtime = OpenCvPluginHost.ensureLoaded(context)
        assertEquals(OpenCvPluginHost.Origin.PLUGIN, runtime.origin)
        assertEquals(OpenCvPluginContract.DEFAULT_PACKAGE_NAME, runtime.packageName)
        assertEquals(OpenCvPluginContract.EXPECTED_OPENCV_VERSION, runtime.openCvVersion)
        assertTrue(runtime.nativeLibraryPath.orEmpty(), runtime.nativeLibraryPath?.isNotBlank() == true)

        val matrix = Mat(2, 3, CvType.CV_8UC1, Scalar(7.0))
        try {
            assertEquals(2, matrix.rows())
            assertEquals(3, matrix.cols())
            assertEquals(7.0, Core.mean(matrix).`val`[0], 0.0)
        } finally {
            matrix.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(OpenCvPluginContract.DEFAULT_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
