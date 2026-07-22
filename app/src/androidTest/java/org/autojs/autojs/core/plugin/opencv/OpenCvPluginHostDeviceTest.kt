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
import org.opencv.core.CvException
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

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
        assertEquals(OpenCvPluginContract.EXPECTED_NATIVE_NDK_VERSION, runtime.nativeNdkVersion)
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

    @Test
    fun nativeOpenCvErrorsReachJavaWithoutKillingTheHostProcess() {
        assumeTrue("OpenCV plugin is not installed", isPluginInstalled())
        assumeTrue("An eligible OpenCV plugin is not installed", OpenCvPluginHost.isAvailable(context))
        OpenCvPluginHost.ensureLoaded(context)

        val source = Mat(2, 2, CvType.CV_8UC1, Scalar(7.0))
        val destination = Mat()
        try {
            val error = try {
                Imgproc.resize(source, destination, Size(3.0, 3.0), 0.0, 0.0, 999)
                throw AssertionError("OpenCV accepted an invalid interpolation value")
            } catch (error: CvException) {
                error
            }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("interpolation", ignoreCase = true))

            Imgproc.resize(source, destination, Size(3.0, 3.0), 0.0, 0.0, Imgproc.INTER_LINEAR)
            assertEquals(3, destination.rows())
            assertEquals(3, destination.cols())
            assertEquals(7.0, Core.mean(destination).`val`[0], 0.0)
        } finally {
            source.release()
            destination.release()
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
