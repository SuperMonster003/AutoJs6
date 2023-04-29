@file:Suppress("UnnecessaryVariable")

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

    dependencies /* Kotlin Gradle Plugin */  {
        // @Alter classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.20-RC2")
        classpath(kotlin(module = "gradle-plugin", version = "1.8.20-RC2"))

        // @Histories for latest three. */
        /* [ 1.8.0-RC2, 1.8.20-Beta, 1.8.20-RC ] */
    }

    dependencies /* Android Gradle Plugin */  {
        val platform = System.getProperty("idea.paths.selector")

        fun fallback(classpath: String) {
            println("Current platform info: $platform")
            println("Dependency to the script classpath may need be specified manually")
            println("The fallback classpath is \"$classpath\"")
            this.classpath(classpath)
        }

        fun setClasspath(classpath: String) {
            println("Current classpath: $classpath")
            this.classpath(classpath)
        }

        // @Updated by SuperMonster003 on Mar 25, 2023.
        val fallbackClasspathForAS = "com.android.tools.build:gradle:7.4.2"

        // @Updated by SuperMonster003 on Mar 30, 2023.
        val fallbackClasspathForIDEA = "com.android.tools.build:gradle:7.4.0"

        // @Updated by SuperMonster003 on Mar 25, 2023.
        val fallbackClasspathForUnknown = fallbackClasspathForAS

        when {
            platform.startsWith("AndroidStudio") || System.getProperty("idea.platform.prefix") == "AndroidStudio" -> {
                when (platform) {
                    // @Created by SuperMonster003 on Apr 16, 2023.
                    "AndroidStudio2022.2" -> {
                        println("Current platform: Android Studio Flamingo | 2022.2")
                        setClasspath("com.android.tools.build:gradle:8.0.0")
                    }
                    // @Created by SuperMonster003 on Mar 25, 2023.
                    "AndroidStudio2022.1" -> {
                        println("Current platform: Android Studio Electric Eel | 2022.1")
                        setClasspath("com.android.tools.build:gradle:7.4.2")
                    }
                    // @Created by SuperMonster003 on Mar 25, 2023.
                    "AndroidStudioPreview2022.2" -> {
                        println("Current platform: Android Studio Flamingo | 2022.2")
                        setClasspath("com.android.tools.build:gradle:8.0.0-beta05")
                    }
                    // @Created by SuperMonster003 on Mar 25, 2023.
                    "AndroidStudioPreview2022.3" -> {
                        println("Current platform: Android Studio Giraffe | 2022.3")
                        setClasspath("com.android.tools.build:gradle:8.1.0-alpha10")
                    }
                    else -> {
                        println("Current platform: $platform")
                        println("Fallback classpass for Android Studio will be used")
                        setClasspath(fallbackClasspathForAS)
                    }
                }
            }
            platform.startsWith("IntelliJIdea") -> {
                when (platform) {
                    // @Created by SuperMonster003 on Mar 30, 2023.
                    "IntelliJIdea2023.1" -> {
                        println("Current platform: IntelliJ IDEA 2022.3")
                        setClasspath("com.android.tools.build:gradle:7.4.0")
                    }
                    // @Created by SuperMonster003 on Mar 25, 2023.
                    "IntelliJIdea2022.3" -> {
                        println("Current platform: IntelliJ IDEA 2022.3")
                        setClasspath("com.android.tools.build:gradle:7.4.0-beta02")
                    }
                    else -> {
                        println("Current platform: $platform")
                        println("Fallback classpass for IntelliJ IDEA will be used")
                        fallback(fallbackClasspathForIDEA)
                    }
                }
            }
            else -> fallback(fallbackClasspathForUnknown)
        }
    }

    dependencies /* ButterKnife Gradle Plugin */  {
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