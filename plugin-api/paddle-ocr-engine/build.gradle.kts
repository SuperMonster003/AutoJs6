plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.baidu.paddle.lite.ocr"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // Depend on AIDL api types (OcrOptions/OcrResult) to keep unified DTO in host/plugin.
    // zh-CN: 依赖 AIDL api 类型 (OcrOptions/OcrResult), 以保持宿主/插件 DTO 统一.
    implementation(projects.pluginApi.paddleOcrApi)

    // OpenCV is required at compile time because OCRPredictorNative references OpenCVLoader.
    // zh-CN: OCRPredictorNative 引用了 OpenCVLoader, 因此需要 OpenCV 编译期依赖.
    compileOnly(projects.libs.orgOpencv480)

    implementation(libs.core.ktx)
    implementation(libs.preference.ktx)
}
