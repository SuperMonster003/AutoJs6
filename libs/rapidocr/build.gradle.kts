/**
 * Rapid OCR (https://github.com/RapidAI/RapidOCR) build script (Kotlin DSL).
 *
 * Created by SuperMonster003 on Sep 19, 2024.
 * Transformed by SuperMonster003 on Sep 30, 2025.
 */

plugins {
    id("org.autojs.build.utils")
    id("org.autojs.build.properties")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
    id("kotlin-parcelize")
}

ext {
    set("projectName", "Rapid OCR")
}

val versionMap = mapOf(
    "OFFICIAL_NAME" to "1.3.0", /* From original build.gradle file. */
    "MIN_SDK" to props["MIN_SDK"].toInt(),
    "COMPILE_SDK" to props["COMPILE_SDK"].toInt(),
    "TARGET_SDK" to props["TARGET_SDK"].toInt(),
    "NDK" to props["RAPID_OCR/NDK"],
    "CMAKE" to props["RAPID_OCR/CMAKE"],
    "OPENCV_MOBILE" to props["RAPID_OCR/OPENCV_MOBILE"],
    "OPENCV_MOBILE_LABEL" to props["RAPID_OCR/OPENCV_MOBILE_LABEL"],
    "ONNX" to props["RAPID_OCR/ONNX"],
    "ONNX_RUNTIME" to props["RAPID_OCR/ONNX_RUNTIME"],
)

val nameMap = mapOf(
    "PROJECT" to extensions.extraProperties["projectName"] as String,
    "OPENCV_MOBILE" to "OpenCV Mobile",
    "OPENCV_MOBILE_LABEL" to "OpenCV Mobile Label",
    "ONNX" to "Onnx",
    "ONNX_RUNTIME" to "Onnx Runtime",
    "NDK" to "NDK",
    "CMAKE" to "Cmake",
)

val libsToDeploy = listOf(
    utils.newLibDeployer(
        project,
        nameMap["OPENCV_MOBILE"] as String,
        "https://github.com/nihui/opencv-mobile/releases/download/v${versionMap["OPENCV_MOBILE_LABEL"]}/opencv-mobile-${versionMap["OPENCV_MOBILE"]}-android.zip",
    ).apply {
        setSourceDir("/opencv-mobile-${versionMap["OPENCV_MOBILE"]}-android/sdk/native/")
        setDestDir("/src/sdk/native/")
    },
    utils.newLibDeployer(
        project,
        nameMap["ONNX"] as String,
        "https://github.com/RapidAI/RapidOcrOnnx/releases/download/${versionMap["ONNX"]}/Project_RapidOcrOnnx-${versionMap["ONNX"]}.7z",
    ).apply {
        setSourceDir("/Project_RapidOcrOnnx-${versionMap["ONNX"]}/models/")
        setDestDir("/src/main/assets/models/")
    },
    utils.newLibDeployer(
        project,
        nameMap["ONNX_RUNTIME"] as String,
        "https://github.com/RapidAI/OnnxruntimeBuilder/releases/download/${versionMap["ONNX_RUNTIME"]}/onnxruntime-${versionMap["ONNX_RUNTIME"]}-android-shared.7z",
    ).apply {
        setSourceDir("/onnxruntime-shared/")
        setDestDir("/src/main/onnxruntime-shared/")
    },
)

utils.configureLibraryLifecycleHooks(
    project,
    nameMap["PROJECT"] as String,
    listOf("OPENCV_MOBILE", "OPENCV_MOBILE_LABEL", "ONNX", "ONNX_RUNTIME", "NDK", "CMAKE")
        .map { "${nameMap[it] ?: it}: ${versionMap[it]}" },
    libsToDeploy,
    "isCleanupRapidOcr",
    listOf(".cxx"),
)

android {

    namespace = "com.benjaminwan.ocrlibrary"
    version = versionMap["OFFICIAL_NAME"] as String

    ndkVersion = versionMap["NDK"] as String
    compileSdk = versionMap["COMPILE_SDK"] as Int

    defaultConfig {
        minSdk = versionMap["MIN_SDK"] as Int

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
            }
        }
    }

    lint {
        targetSdk = versionMap["TARGET_SDK"] as Int
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = versionMap["CMAKE"] as String
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("include" to listOf("*.jar"), "dir" to "libs")))

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso.core)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
}
