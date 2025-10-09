enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "logic"

include(":convention")
include(":ksp-version-codes-processor")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version System.getProperty("org.gradle.toolchains.foojay-resolver-convention")
    }
}

plugins {
    // @Hint by SuperMonster003 on Oct 6, 2025.
    //  ! Enable JDK auto-resolution/download capability for build modules.
    //  ! zh-CN: 让构建模块具备 JDK 自动解析/下载能力.
    id("org.gradle.toolchains.foojay-resolver-convention")
}
