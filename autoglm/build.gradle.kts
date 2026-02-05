plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kevinluo.autoglm"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        // library 不需要 applicationId / versionCode / versionName
        // version 由最终宿主 app 决定（Phase B-2 会在 :app 输出 APK）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            // library 不建议再注入 app_name（会在 Phase B-2 与宿主资源合并时冲突）
            // resValue("string", "app_name", "AutoGLM Dev")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        // library 也可以生成 BuildConfig（如果你的代码引用了 BuildConfig 字段）
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Kotlin Coroutines for async operations
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)

    // Security for encrypted preferences
    implementation(libs.androidx.security.crypto)

    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)

    // Fragment
    implementation(libs.androidx.fragment)

    // SwipeRefreshLayout
    implementation(libs.androidx.swiperefreshlayout)

    // OkHttp for API communication
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)

    // Retrofit for API communication
    implementation(libs.retrofit)

    // Kotlin Serialization for JSON parsing
    implementation(libs.kotlinx.serialization.json)

    // Sherpa-ONNX for offline speech recognition
    implementation("com.github.k2-fsa:sherpa-onnx:1.12.20")

    // Apache Commons Compress for tar.bz2 extraction
    implementation("org.apache.commons:commons-compress:1.28.0")

    // Testing（library 仍然可保留）
    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}