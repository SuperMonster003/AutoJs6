plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

// APK signature verification and keystore reading shared by the host and by modules/apk-signer:
// the apksig library (com.android.apksig), the pure-Java JKS reader and the BKS loader that relies on
// the platform's own BouncyCastle provider. No third-party dependencies; the signing tool, certificate
// creation and SpongyCastle stay in modules/apk-signer (plugin side only).
// zh-CN: 宿主与 modules/apk-signer 共用的 APK 签名校验与密钥库读取: apksig 库 (com.android.apksig), 纯 Java 的 JKS
// 读取器, 以及依赖平台自带 BouncyCastle provider 的 BKS 加载器. 无第三方依赖; 签名工具, 证书创建与 SpongyCastle
// 留在 modules/apk-signer (仅插件侧).
android {
    namespace = "com.mcal.apkverifier"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
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
    testImplementation(libs.junit)
    // JVM tests need a "BC" provider for BKS; Android ships one, the desktop JDK does not.
    // zh-CN: JVM 测试需要名为 "BC" 的 provider 才能读 BKS; Android 自带, 桌面 JDK 没有.
    testImplementation(libs.bcprov.jdk15on)
}
