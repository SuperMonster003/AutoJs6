plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

// Binder contract between the host and an installer plugin (3-Setup Installer, roadmap P1.2):
// AIDL surface, discovery literals, identity, capability keys, JSON vocabulary, ceilings and
// error codes. Protocol document: docs/dev/installer-plugin-protocol-v1.md.
// zh-CN: 宿主与安装器插件 (3-Setup Installer) 的 Binder 契约: AIDL, 发现常量, 身份, 能力键, JSON 词汇, 上限与错误码.
android {
    namespace = "org.autojs.plugin.installer.api"
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        minSdk = versions.sdkVersionMin
        // Exported Binder contracts remain consumable by plugins on the audited SDK 36 platform.
        aarMetadata { minCompileSdk = 36 }
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        targetSdk = versions.sdkVersionTarget
        abortOnError = false
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }
}

dependencies {
    api(project(":plugin-api:common-plugin-api"))
    testImplementation(libs.junit)
}
