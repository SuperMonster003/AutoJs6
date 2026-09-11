package org.autojs.autojs.core.plugin.imagequant

import org.autojs.autojs.core.plugin.center.PluginAuthorizedState
import org.autojs.plugin.imagequant.api.ImageQuantPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQuantPluginPolicyTest {

    @Test
    fun `disabled candidates are rejected before all other checks`() {
        assertEquals(
            ImageQuantPluginLoadError.PLUGIN_DISABLED,
            rejection(
                enabled = false,
                authorizedState = PluginAuthorizedState.REQUIRED,
                contractVersion = ImageQuantPluginContract.VERSION + 1,
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
                ImageQuantPluginLoadError.IN_PROCESS_TRUST_REQUIRED,
                rejection(authorizedState = state),
            )
            assertNull(state.name, rejection(authorizedState = state, sameSignerAsHost = true))
        }
    }

    @Test
    fun `contract and dependency versions must exactly match`() {
        assertEquals(
            ImageQuantPluginLoadError.CONTRACT_VERSION_UNSUPPORTED,
            rejection(contractVersion = ImageQuantPluginContract.VERSION + 1),
        )
        assertEquals(
            ImageQuantPluginLoadError.LIBIMAGEQUANT_VERSION_UNSUPPORTED,
            rejection(libimagequantVersion = "2.18.0"),
        )
        assertEquals(
            ImageQuantPluginLoadError.LIBPNG_VERSION_UNSUPPORTED,
            rejection(libpngVersion = "1.6.50"),
        )
    }

    @Test
    fun `native library toolchain STL and ABI contract must exactly match`() {
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_LIBRARY_UNSUPPORTED,
            rejection(nativeLibraryName = ImageQuantPluginContract.NATIVE_LIBRARY_FILE_NAME),
        )
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_NDK_VERSION_MISMATCH,
            rejection(nativeNdkVersion = "27.0.12077973"),
        )
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_NDK_VERSION_MISMATCH,
            rejection(nativeNdkVersion = "28.0.13004108"),
        )
        ImageQuantPluginContract.SUPPORTED_NATIVE_NDK_VERSIONS.forEach { version ->
            assertNull(version, rejection(nativeNdkVersion = version))
        }
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_STL_MISMATCH,
            rejection(nativeStl = "c++_shared"),
        )
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_ABI_VERSION_MISMATCH,
            rejection(nativeAbiVersion = ImageQuantPluginContract.EXPECTED_NATIVE_ABI_VERSION + 1),
        )
    }

    @Test
    fun `declared CPU ABIs must be the complete supported set`() {
        assertNull(rejection(nativeAbis = "x86_64, x86, armeabi-v7a, arm64-v8a"))
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_ABIS_MISMATCH,
            rejection(nativeAbis = "arm64-v8a,armeabi-v7a,x86_64"),
        )
        assertEquals(
            ImageQuantPluginLoadError.NATIVE_ABIS_MISMATCH,
            rejection(nativeAbis = ImageQuantPluginContract.EXPECTED_NATIVE_ABIS + ",mips"),
        )
    }

    @Test
    fun `required host metadata is mandatory and cannot predate the contract`() {
        assertEquals(
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID,
            rejection(requiresHostVersion = null),
        )
        assertEquals(
            ImageQuantPluginLoadError.CONTRACT_METADATA_INVALID,
            rejection(requiresHostVersion = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE - 1),
        )
        assertNull(
            rejection(
                hostVersionCode = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
                requiresHostVersion = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
            ),
        )
        assertEquals(
            ImageQuantPluginLoadError.HOST_VERSION_TOO_LOW,
            rejection(
                hostVersionCode = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
                requiresHostVersion = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE + 1,
            ),
        )
    }

    @Test
    fun `maximum host version includes its boundary`() {
        assertNull(rejection(hostVersionCode = 5240L, maxHostVersion = 5240L))
        assertEquals(
            ImageQuantPluginLoadError.HOST_VERSION_TOO_HIGH,
            rejection(hostVersionCode = 5241L, maxHostVersion = 5240L),
        )
    }

    @Test
    fun `native ABI selection follows current process bitness`() {
        val supported32BitAbis = listOf("armeabi-v7a", "x86")
        val supported64BitAbis = listOf("arm64-v8a", "x86_64")
        assertEquals(
            supported32BitAbis,
            ImageQuantProcessAbiPolicy.select(false, supported32BitAbis, supported64BitAbis),
        )
        assertEquals(
            supported64BitAbis,
            ImageQuantProcessAbiPolicy.select(true, supported32BitAbis, supported64BitAbis),
        )
    }

    @Test
    fun `options API capability is additive and backward compatible`() {
        assertFalse(ImageQuantOptionsApiPolicy.supports(0))
        assertTrue(ImageQuantOptionsApiPolicy.supports(ImageQuantPluginContract.MIN_OPTIONS_API_VERSION))
        assertTrue(ImageQuantOptionsApiPolicy.supports(ImageQuantPluginContract.OPTIONS_API_VERSION + 1))
        assertFalse(
            ImageQuantOptionsApiPolicy.supportsQuantizationResult(
                ImageQuantPluginContract.QUANTIZATION_RESULT_API_VERSION - 1,
            ),
        )
        assertTrue(
            ImageQuantOptionsApiPolicy.supportsQuantizationResult(
                ImageQuantPluginContract.QUANTIZATION_RESULT_API_VERSION,
            ),
        )
        assertFalse(
            ImageQuantOptionsApiPolicy.supportsColorIo(
                ImageQuantPluginContract.COLOR_IO_API_VERSION - 1,
            ),
        )
        assertTrue(
            ImageQuantOptionsApiPolicy.supportsColorIo(
                ImageQuantPluginContract.COLOR_IO_API_VERSION,
            ),
        )
        assertFalse(
            ImageQuantOptionsApiPolicy.supportsResourceControl(
                ImageQuantPluginContract.RESOURCE_CONTROL_API_VERSION - 1,
            ),
        )
        assertTrue(
            ImageQuantOptionsApiPolicy.supportsResourceControl(
                ImageQuantPluginContract.RESOURCE_CONTROL_API_VERSION,
            ),
        )
    }

    @Test
    fun `loaded plugin lifecycle rejects removal disablement and replacement`() {
        assertNull(
            ImageQuantRuntimeLifecyclePolicy.rejectionCode(
                bundled = true,
                loadedPackageIdentity = null,
                currentPackageIdentity = null,
                currentError = ImageQuantPluginLoadError.PLUGIN_NOT_FOUND,
            ),
        )
        assertEquals(
            ImageQuantPluginLoadError.PLUGIN_NOT_FOUND,
            ImageQuantRuntimeLifecyclePolicy.rejectionCode(false, "pkg-1", null, null),
        )
        assertEquals(
            ImageQuantPluginLoadError.PLUGIN_DISABLED,
            ImageQuantRuntimeLifecyclePolicy.rejectionCode(
                false,
                "pkg-1",
                null,
                ImageQuantPluginLoadError.PLUGIN_DISABLED,
            ),
        )
        assertEquals(
            ImageQuantPluginLoadError.PLUGIN_CHANGED_RESTART_REQUIRED,
            ImageQuantRuntimeLifecyclePolicy.rejectionCode(false, "pkg-1", "pkg-2", null),
        )
        assertNull(ImageQuantRuntimeLifecyclePolicy.rejectionCode(false, "pkg-1", "pkg-1", null))
    }

    private fun rejection(
        enabled: Boolean = true,
        authorizedState: PluginAuthorizedState = PluginAuthorizedState.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        contractVersion: Int = ImageQuantPluginContract.VERSION,
        libimagequantVersion: String = ImageQuantPluginContract.EXPECTED_LIBIMAGEQUANT_VERSION,
        libpngVersion: String = ImageQuantPluginContract.EXPECTED_LIBPNG_VERSION,
        nativeLibraryName: String = ImageQuantPluginContract.NATIVE_LIBRARY_NAME,
        nativeNdkVersion: String = ImageQuantPluginContract.EXPECTED_NATIVE_NDK_VERSION,
        nativeStl: String = ImageQuantPluginContract.EXPECTED_NATIVE_STL,
        nativeAbiVersion: Int = ImageQuantPluginContract.EXPECTED_NATIVE_ABI_VERSION,
        nativeAbis: String = ImageQuantPluginContract.EXPECTED_NATIVE_ABIS,
        hostVersionCode: Long = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
        requiresHostVersion: Long? = ImageQuantPluginContract.REQUIRED_HOST_VERSION_CODE,
        maxHostVersion: Long? = null,
    ): ImageQuantPluginLoadError? = ImageQuantPluginPolicy.rejectionCode(
        enabled = enabled,
        authorizedState = authorizedState,
        sameSignerAsHost = sameSignerAsHost,
        contractVersion = contractVersion,
        libimagequantVersion = libimagequantVersion,
        libpngVersion = libpngVersion,
        nativeLibraryName = nativeLibraryName,
        nativeNdkVersion = nativeNdkVersion,
        nativeStl = nativeStl,
        nativeAbiVersion = nativeAbiVersion,
        nativeAbis = nativeAbis,
        hostVersionCode = hostVersionCode,
        requiresHostVersion = requiresHostVersion,
        maxHostVersion = maxHostVersion,
    )
}
