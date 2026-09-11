package org.autojs.plugin.opencv.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/** Stable discovery contract for the native OpenCV runtime plugin. */
object OpenCvPluginContract {

    const val VERSION = 2
    const val MIN_SUPPORTED_VERSION = VERSION
    const val MAX_SUPPORTED_VERSION = VERSION

    const val ACTION_RUNTIME = "org.autojs.plugin.OPENCV"
    const val CATEGORY_RUNTIME = "opencv-runtime"

    const val PLUGIN_ID = "opencv"
    const val ENGINE = "opencv"
    const val VARIANT_4_8_0 = "4.8.0"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.opencv"
    const val REQUIRED_HOST_VERSION_CODE = 5237L

    const val META_DATA_CONTRACT_VERSION = "org.autojs.plugin.opencv.CONTRACT_VERSION"
    const val META_DATA_OPENCV_VERSION = "org.autojs.plugin.opencv.VERSION"
    const val META_DATA_NATIVE_LIBRARY = "org.autojs.plugin.opencv.NATIVE_LIBRARY"
    const val META_DATA_NATIVE_NDK_VERSION = "org.autojs.plugin.opencv.NATIVE_NDK_VERSION"
    const val META_DATA_JAVA_API_SHA256 = "org.autojs.plugin.opencv.JAVA_API_SHA256"
    const val META_DATA_REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    const val META_DATA_MAX_HOST_VERSION = "org.autojs.plugin.opencv.MAX_HOST_VERSION"

    const val EXPECTED_OPENCV_VERSION = VARIANT_4_8_0
    const val EXPECTED_NATIVE_NDK_VERSION = "26.1.10909125"

    /** NDK releases whose `libopencv_java4.so` builds the host accepts: r26b (4 KB-era donor) and r28c (16 KB rebuild). */
    @JvmField
    val SUPPORTED_NATIVE_NDK_VERSIONS = setOf(EXPECTED_NATIVE_NDK_VERSION, "28.2.13676358")
    const val NATIVE_LIBRARY_NAME = "opencv_java4"
    const val NATIVE_LIBRARY_FILE_NAME = "libopencv_java4.so"
    const val JAVA_API_SHA256 = "340976552fda3cce525021f0b072427cabf0aa1c786fb80cfc4a3a8105d90b3f"

    @JvmField
    val SUPPORTED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
}
