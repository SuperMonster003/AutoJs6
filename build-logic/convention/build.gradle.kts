import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl` /* kotlin("jvm") */
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())

    implementation(libs.apache.commons.compress)
    implementation(libs.tukaani.xz)

    // @Hint by JetBrains AI Assistant (Claude Opus 5) on Aug 18, 2026.
    //  ! "compileOnly" is mandatory here: convention plugins only need the Android DSL types at
    //  ! compile time, while at runtime the AGP instance loaded by the consuming module must be
    //  ! reused. Bundling a second AGP copy would place the same classes in a different classloader
    //  ! and break the DSL configuration with a ClassCastException.
    //  ! zh-CN: 此处必须使用 "compileOnly": 约定插件仅在编译期需要 Android DSL 类型, 运行时则须复用
    //  ! 消费方模块所加载的那份 AGP 实例. 若打包第二份 AGP, 相同的类将处于不同的类加载器中,
    //  ! 导致配置 DSL 时抛出 ClassCastException.
    compileOnly("com.android.tools.build:gradle:${System.getProperty("gradle.agp.version")}")
}

gradlePlugin {
    plugins {
        register("utils") {
            id = "org.autojs.build.utils"
            implementationClass = "org.autojs.build.UtilsPlugin"
            displayName = "AutoJs6 Build Utils Plugin"
            description = "Provides utilities for downloading, extracting archives, and version helpers."
        }
        register("versions") {
            id = "org.autojs.build.versions"
            implementationClass = "org.autojs.build.VersionsPlugin"
            displayName = "AutoJs6 Versions Plugin"
            description = "Provides version helpers."
        }
        register("signs") {
            id = "org.autojs.build.signs"
            implementationClass = "org.autojs.build.SignsPlugin"
            displayName = "AutoJs6 Signs Plugin"
            description = "Provides signing helpers."
        }
        register("properties") {
            id = "org.autojs.build.properties"
            implementationClass = "org.autojs.build.PropertiesPlugin"
            displayName = "AutoJs6 Properties Plugin"
            description = "Provides properties helpers."
        }
        register("jvmConvention") {
            id = "org.autojs.build.jvm-convention"
            implementationClass = "org.autojs.build.JvmConventionPlugin"
            displayName = "AutoJs6 JVM Convention Plugin"
            description = "Configures Java/Kotlin targets for Android modules using central Versions."
        }
        register("nodeAndroidConfigConvention") {
            id = "org.autojs.build.node-android-config"
            implementationClass = "org.autojs.build.NodeAndroidConfigPlugin"
            displayName = "AutoJs6 Node Android Config Convention Plugin"
            description = "Configures host policy for the external Node.js runtime plugin."
        }
        register("localAarRegisterConvention") {
            id = "org.autojs.build.local-arr-register-convention"
            implementationClass = "org.autojs.build.LocalAarRegisterConventionPlugin"
            displayName = "AutoJs6 Local AAR Register Convention Plugin"
            description = "Provides local AAR register helpers."
        }
    }
}

System.getProperty("gradle.java.version.select").toInt().let { jdk ->
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(jdk))
        }
    }
    kotlin {
        jvmToolchain(jdk)
    }
}

System.getProperty("gradle.jvm.target.effective").let { jvm ->
    java {
        sourceCompatibility = JavaVersion.toVersion(jvm)
        targetCompatibility = JavaVersion.toVersion(jvm)
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(jvm))
        }
    }
}
