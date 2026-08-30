package org.autojs.plugin.imagequant.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/** Stable discovery and native ABI contract for the Image Quantization runtime plugin. */
object ImageQuantPluginContract {

    const val VERSION = 1
    const val MIN_SUPPORTED_VERSION = VERSION
    const val MAX_SUPPORTED_VERSION = VERSION

    const val ACTION_RUNTIME = "org.autojs.plugin.IMAGE_QUANT"
    const val CATEGORY_RUNTIME = "imagequant-runtime"

    const val PLUGIN_ID = "imagequant"
    const val ENGINE = "imagequant"
    const val VARIANT_2_17_0 = "2.17.0"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.imagequant"
    const val REQUIRED_HOST_VERSION_CODE = 5239L

    const val META_DATA_CONTRACT_VERSION = "org.autojs.plugin.imagequant.CONTRACT_VERSION"
    const val META_DATA_LIBIMAGEQUANT_VERSION = "org.autojs.plugin.imagequant.LIBIMAGEQUANT_VERSION"
    const val META_DATA_LIBPNG_VERSION = "org.autojs.plugin.imagequant.LIBPNG_VERSION"
    const val META_DATA_NATIVE_LIBRARY = "org.autojs.plugin.imagequant.NATIVE_LIBRARY"
    const val META_DATA_NATIVE_NDK_VERSION = "org.autojs.plugin.imagequant.NATIVE_NDK_VERSION"
    const val META_DATA_NATIVE_STL = "org.autojs.plugin.imagequant.NATIVE_STL"
    const val META_DATA_NATIVE_ABI_VERSION = "org.autojs.plugin.imagequant.NATIVE_ABI_VERSION"
    const val META_DATA_NATIVE_ABIS = "org.autojs.plugin.imagequant.NATIVE_ABIS"
    const val META_DATA_OPTIONS_API_VERSION = "org.autojs.plugin.imagequant.OPTIONS_API_VERSION"
    const val META_DATA_REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    const val META_DATA_MAX_HOST_VERSION = "org.autojs.plugin.imagequant.MAX_HOST_VERSION"

    const val EXPECTED_LIBIMAGEQUANT_VERSION = VARIANT_2_17_0
    const val EXPECTED_LIBPNG_VERSION = "1.6.49"
    const val EXPECTED_NATIVE_NDK_VERSION = "26.1.10909125"
    const val EXPECTED_NATIVE_STL = "c++_static"
    const val EXPECTED_NATIVE_ABI_VERSION = 1
    const val MIN_OPTIONS_API_VERSION = 1
    const val QUANTIZATION_RESULT_API_VERSION = 2
    const val COLOR_IO_API_VERSION = 3
    const val RESOURCE_CONTROL_API_VERSION = 4
    const val OPTIONS_API_VERSION = RESOURCE_CONTROL_API_VERSION

    const val NATIVE_LIBRARY_NAME = "pngquant_bridge"
    const val NATIVE_LIBRARY_FILE_NAME = "libpngquant_bridge.so"

    const val RUNTIME_PROBE_VALUE =
        "libimagequant=2.17.0;libpng=1.6.49;nativeAbi=1;stl=c++_static"

    @JvmField
    val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    @JvmField
    val EXPECTED_NATIVE_ABIS = SUPPORTED_ABIS.joinToString(",")
}
