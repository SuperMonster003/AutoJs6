plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

// Shared Android package archive parser (roadmap of AutoJs6-Plugin-Three-Setup-Installer, D10 / D28):
// format detection, container metadata, `.apks` table-of-contents decoding and split selection,
// APK / AAB manifest display decoding, signature scheme detection and the inspection ceilings.
// The host app and the installer plugin consume the same code; the plugin stages the release AAR
// under `libs/` with a SHA-256 lock. No AIDL, no resources, no Context beyond PackageDeviceSpec.from.
// zh-CN: 宿主与 3-Setup Installer 插件共用的安装包解析器 (格式识别, 容器元数据, .apks TOC 解码与分包选择,
// APK / AAB 清单显示解码, 签名方案探测, 检查上限). 插件以哈希锁定的 release AAR 消费.
android {
    namespace = "org.autojs.plugin.packagearchive"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        // Consumable by plugins on the audited SDK 36 platform, like the exported Binder contracts.
        aarMetadata { minCompileSdk = 36 }
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
    // `manifest.json` (xapk) / `info.json` (apkm) container descriptors; consumers of the raw AAR
    // must provide Gson themselves (the host app and the installer plugin both depend on it).
    implementation(libs.gson)
    testImplementation(libs.junit)
}
