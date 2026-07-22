package org.autojs.autojs.core.plugin.opencv

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.plugin.opencv.api.OpenCvPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenCvPluginPolicyTest {

    @Test
    fun `disabled candidates are rejected before all other checks`() {
        assertEquals(
            OpenCvPluginLoadError.PLUGIN_DISABLED,
            rejection(
                enabled = false,
                authorizedState = PluginAuthorizedState.REQUIRED,
                contractVersion = OpenCvPluginContract.VERSION + 1,
            ),
        )
    }

    @Test
    fun `only official trusted or same-signer candidates may load native code in process`() {
        listOf(PluginAuthorizedState.OFFICIAL, PluginAuthorizedState.TRUSTED).forEach { state ->
            assertNull(state.name, rejection(authorizedState = state))
        }
        listOf(PluginAuthorizedState.USER_GRANTED, PluginAuthorizedState.REQUIRED).forEach { state ->
            assertEquals(
                state.name,
                OpenCvPluginLoadError.IN_PROCESS_TRUST_REQUIRED,
                rejection(authorizedState = state),
            )
            assertNull(
                state.name,
                rejection(authorizedState = state, sameSignerAsHost = true),
            )
        }
    }

    @Test
    fun `contract version must be within the supported range`() {
        assertNull(rejection(contractVersion = OpenCvPluginContract.MIN_SUPPORTED_VERSION))
        assertNull(rejection(contractVersion = OpenCvPluginContract.MAX_SUPPORTED_VERSION))
        assertEquals(
            OpenCvPluginLoadError.CONTRACT_VERSION_UNSUPPORTED,
            rejection(contractVersion = OpenCvPluginContract.MIN_SUPPORTED_VERSION - 1),
        )
        assertEquals(
            OpenCvPluginLoadError.CONTRACT_VERSION_UNSUPPORTED,
            rejection(contractVersion = OpenCvPluginContract.MAX_SUPPORTED_VERSION + 1),
        )
    }

    @Test
    fun `OpenCV runtime version must exactly match the host Java ABI`() {
        assertNull(rejection(openCvVersion = OpenCvPluginContract.EXPECTED_OPENCV_VERSION))
        assertEquals(
            OpenCvPluginLoadError.OPENCV_VERSION_UNSUPPORTED,
            rejection(openCvVersion = "4.9.0"),
        )
        assertEquals(
            OpenCvPluginLoadError.OPENCV_VERSION_UNSUPPORTED,
            rejection(openCvVersion = ""),
        )
    }

    @Test
    fun `native library name must exactly match the versioned contract`() {
        assertNull(rejection(nativeLibraryName = OpenCvPluginContract.NATIVE_LIBRARY_NAME))
        listOf(
            OpenCvPluginContract.NATIVE_LIBRARY_FILE_NAME,
            "opencv_java3",
            "libopencv_java4",
            "",
        ).forEach { name ->
            assertEquals(
                name,
                OpenCvPluginLoadError.NATIVE_LIBRARY_UNSUPPORTED,
                rejection(nativeLibraryName = name),
            )
        }
    }

    @Test
    fun `Java API fingerprint must exactly match the host classes`() {
        assertNull(rejection(javaApiSha256 = OpenCvPluginContract.JAVA_API_SHA256))
        assertEquals(
            OpenCvPluginLoadError.JAVA_API_MISMATCH,
            rejection(javaApiSha256 = "0".repeat(64)),
        )
    }

    @Test
    fun `minimum and maximum host versions include their boundary`() {
        assertEquals(
            OpenCvPluginLoadError.HOST_VERSION_TOO_LOW,
            rejection(hostVersionCode = 99L, requiresHostVersion = 100L),
        )
        assertNull(rejection(hostVersionCode = 100L, requiresHostVersion = 100L))
        assertNull(rejection(hostVersionCode = 100L, maxHostVersion = 100L))
        assertEquals(
            OpenCvPluginLoadError.HOST_VERSION_TOO_HIGH,
            rejection(hostVersionCode = 101L, maxHostVersion = 100L),
        )
        assertNull(
            rejection(
                hostVersionCode = 100L,
                requiresHostVersion = 99L,
                maxHostVersion = 101L,
            ),
        )
    }

    @Test
    fun `native ABI selection follows current process bitness`() {
        val supported32BitAbis = listOf("armeabi-v7a", "x86")
        val supported64BitAbis = listOf("arm64-v8a", "x86_64")

        assertEquals(
            supported32BitAbis,
            OpenCvProcessAbiPolicy.select(false, supported32BitAbis, supported64BitAbis),
        )
        assertEquals(
            supported64BitAbis,
            OpenCvProcessAbiPolicy.select(true, supported32BitAbis, supported64BitAbis),
        )
    }

    private fun rejection(
        enabled: Boolean = true,
        authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        contractVersion: Int = OpenCvPluginContract.VERSION,
        openCvVersion: String = OpenCvPluginContract.EXPECTED_OPENCV_VERSION,
        nativeLibraryName: String = OpenCvPluginContract.NATIVE_LIBRARY_NAME,
        javaApiSha256: String = OpenCvPluginContract.JAVA_API_SHA256,
        hostVersionCode: Long = 100L,
        requiresHostVersion: Long? = null,
        maxHostVersion: Long? = null,
    ): OpenCvPluginLoadError? = OpenCvPluginPolicy.rejectionCode(
        enabled = enabled,
        authorizedState = authorizedState,
        sameSignerAsHost = sameSignerAsHost,
        contractVersion = contractVersion,
        openCvVersion = openCvVersion,
        nativeLibraryName = nativeLibraryName,
        javaApiSha256 = javaApiSha256,
        hostVersionCode = hostVersionCode,
        requiresHostVersion = requiresHostVersion,
        maxHostVersion = maxHostVersion,
    )
}
