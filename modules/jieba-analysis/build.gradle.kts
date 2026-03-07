plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

android {
    group = "com.huaban"
    namespace = "com.huaban.jieba"
    version = "1.0.3-SNAPSHOT [Optimized for AutoJs6]"
    description = "结巴分词工具 (jieba for java)"

    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        named("main") {
            // 主 Java/Kotlin 目录
            java.directories.add("src/main/java")
            // 将资源文件夹指定为 assets 目录
            assets.directories.add("src/main/assets")
        }
    }

    lint {
        targetSdk = versions.sdkVersionTarget
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.commons.lang3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.espresso.core)
}

tasks.withType(JavaCompile::class.java).configureEach {
    options.encoding = "UTF-8"
}
