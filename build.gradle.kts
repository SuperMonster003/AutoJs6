// Top-level build file where you can add configuration options common to all sub-projects/modules.

extra {
    extra["configurationName"] = "default"
}

buildscript {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/jcenter")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven")
    }

    dependencies {
        @Suppress("AndroidGradlePluginVersion")
        classpath("com.android.tools.build:gradle:8.0.0-alpha09")

        // @Alter classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0-RC2")
        classpath(kotlin(module = "gradle-plugin", version = "1.8.0-RC2"))

        classpath("com.jakewharton:butterknife-gradle-plugin:10.2.3")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/jcenter")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://jitpack.io")
        maven("https://repo.huaweicloud.com/repository/maven")
    }
}

tasks {
    register<Delete>("clean").configure {
        delete(rootProject.buildDir)
    }
}