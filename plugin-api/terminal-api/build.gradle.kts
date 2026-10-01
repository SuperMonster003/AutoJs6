plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.library")
}

// Binder contract between the host and a terminal plugin (3-Shell Terminal, plugin roadmap P1.1):
// AIDL surface, discovery and launch literals, identity, capability keys, JSON vocabulary, ceilings
// and error codes. Protocol document: docs/dev/terminal-plugin-protocol-v1.md (roadmap P1.4).
// zh-CN: 宿主与终端插件 (3-Shell Terminal) 的 Binder 契约: AIDL, 发现与启动常量, 身份, 能力键, JSON 词汇, 上限与错误码.
android {
    namespace = "org.autojs.plugin.terminal.api"
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
