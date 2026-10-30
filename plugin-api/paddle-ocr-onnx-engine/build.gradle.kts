plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    namespace = "org.autojs.plugin.paddle.ocr.onnx"

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

    // Java classes only: the app module excludes libonnxruntime*.so, packaged applications copy them
    // from the installed Paddle OCR plugin (same pinned version).
    // zh-CN: 只用 Java 类: app 模块排除 libonnxruntime*.so, 打包应用从已安装的 Paddle OCR 插件复制 (版本一致).
    implementation(libs.onnxruntime.android)

    // OpenCV Java API is supplied by the app module; natives come from the OpenCV plugin.
    // zh-CN: OpenCV Java API 由 app 模块提供; 原生库来自 OpenCV 插件.
    compileOnly(project(":libs:org-opencv-java-4_8_0"))

    implementation(libs.core.ktx)
}
