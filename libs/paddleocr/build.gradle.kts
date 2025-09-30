/**
 * Paddle OCR (https://github.com/PaddlePaddle/PaddleOCR) build script (Kotlin DSL).
 *
 * Created by TonyJiangWJ (https://github.com/TonyJiangWJ) on Aug 7, 2023.
 * Modified by TonyJiangWJ (https://github.com/TonyJiangWJ) as of Aug 11, 2023.
 * Modified by SuperMonster003 as of Sep 4, 2023.
 * Transformed by SuperMonster003 on Sep 30, 2025.
 */

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.properties")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

ext {
    set("projectName", "Paddle OCR")
}

val versionMap = mapOf(
    "MIN_SDK" to props["MIN_SDK"].toInt(),
    "COMPILE_SDK" to props["COMPILE_SDK"].toInt(),
    "TARGET_SDK" to props["TARGET_SDK"].toInt(),
    "NDK" to props["PADDLE_OCR/NDK"],
    "CMAKE" to props["PADDLE_OCR/CMAKE"],
    "OPENCV" to props["PADDLE_OCR/OPENCV"],
)

val nameMap = mapOf(
    "PROJECT" to extensions.extraProperties.get("projectName") as String,
    "OPENCV" to "OpenCV",
    "NDK" to "NDK",
    "CMAKE" to "Cmake",
)

val libsToDeploy = listOf(
    // @Hint by TonyJiangWJ (https://github.com/TonyJiangWJ) on Aug 7, 2023.
    //  ! 下载 OpenCV 源码包 (默认为 4.2.0).
    //  ! 和 Auto.js 中的版本 (如 4.8.0) 不匹配会产生冲突,
    //  ! 可按需修改 version.properties 中对应内容.
    //  ! en-US (translated by SuperMonster003 on Oct 22, 2024):
    //  ! Download the archive for source code of OpenCV (defaults to 4.2.0).
    //  ! Not matching the version in Auto.js (e.g., 4.8.0) will cause conflicts.
    //  ! Adjust the corresponding content in version.properties as needed.
    utils.newLibDeployer(
        project,
        nameMap["OPENCV"] as String,
        "https://github.com/opencv/opencv/releases/download/${versionMap["OPENCV"]}/opencv-${versionMap["OPENCV"]}-android-sdk.zip",
    ).apply {
        setSourceDir("/OpenCV-android-sdk/sdk/native/")
        setDestDir("/src/sdk/native/")
    }
)

val argsMap = mapOf(
    // @Hint by LZX284 (https://github.com/LZX284) on Sep 30, 2023.
    //  ! "ANDROID_PLATFORM" 默认为 "android-23", 这里修改为与 AutoJs6 最低 SDK 版本一致的 `versionMap.MIN_SDK`.
    //  ! en-US (translated by SuperMonster003 on Oct 22, 2024):
    //  ! "ANDROID_PLATFORM" with default value "android-23" was changed to `versionMap.MIN_SDK`
    //  ! to align with the min SDK version of AutoJs6.
    "ANDROID_PLATFORM" to "android-${versionMap["MIN_SDK"]}",
    "ANDROID_STL" to "c++_shared",
    "ANDROID_ARM_NEON" to "TRUE",
)

// @Hint by SuperMonster003 on Nov 12, 2023.
//  ! Do not add a space (nbsp) after "-D".
//  ! Reference: https://stackoverflow.com/questions/14887438/spacing-in-d-option-in-cmake
//  ! zh-CN:
//  ! 在 "-D" 后不要添加空格 (不间断空格).
//  ! 参阅: https://stackoverflow.com/questions/14887438/spacing-in-d-option-in-cmake
val args = argsMap.entries.map { (k, v) -> "-D$k=$v" }

utils.configureLibraryLifecycleHooks(
    project,
    nameMap["PROJECT"] as String,
    listOf("OPENCV", "NDK", "CMAKE").map { "${nameMap[it]}: ${versionMap[it]}" },
    libsToDeploy,
    "isCleanupPaddleOcr",
    listOf(".cxx")
)

android {
    namespace = "com.baidu.paddle.lite.ocr"

    ndkVersion = versionMap["NDK"] as String
    compileSdk = versionMap["COMPILE_SDK"] as Int

    defaultConfig {
        minSdk = versionMap["MIN_SDK"] as Int

        externalNativeBuild {
            cmake {
                val cppFlagsMap = mapOf(
                    "-std" to "c++11"
                )
                val cppListFlags = mapOf(
                    "-f" to listOf("rtti", "exceptions"),
                    "-W" to "no-format"
                )

                // 组装等价于 Groovy 脚本中的拼接逻辑
                val cppFlagsJoined = buildString {
                    append(
                        cppFlagsMap.entries.joinToString(" ") { (k, v) -> "$k=$v" }
                    )
                    append(" ")
                    append(
                        cppListFlags.entries.joinToString(" ") { (k, v) ->
                            when (v) {
                                is List<*> -> v.joinToString(" ") { opt -> k + opt }
                                else -> "$k$v"
                            }
                        }
                    )
                }.trim()

                cppFlags += cppFlagsJoined
                arguments += args
            }
        }

        ndk {
            // @Hint by SuperMonster003 on Jan 2, 2024.
            //  ! Supported architectures: arm-v7, arm-v8.
            //  ! References:
            //  ! https://github.com/PaddlePaddle/Paddle-Lite/blob/develop/lite/tools/build_android.sh#L7
            //  ! https://github.com/PaddlePaddle/Paddle-Lite/issues/80
            //  ! zh-CN:
            //  ! 支持的架构: arm-v7, arm-v8.
            //  ! 参阅:
            //  ! https://github.com/PaddlePaddle/Paddle-Lite/blob/develop/lite/tools/build_android.sh#L7
            //  ! https://github.com/PaddlePaddle/Paddle-Lite/issues/80
            // noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            @Suppress("DEPRECATION")
            ldLibs?.add("jnigraphics")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = versionMap["CMAKE"] as String
        }
    }

    lint {
        targetSdk = versionMap["TARGET_SDK"] as Int
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))
    implementation(project(mapOf("path" to ":libs:org.opencv-${versionMap["OPENCV"]}")))
    implementation(libs.core.ktx)
    implementation(libs.preference.ktx)
}