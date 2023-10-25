@file:Suppress("SpellCheckingInspection")

import org.gradle.kotlin.dsl.support.uppercaseFirstChar


enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
    ":app",

    ":libs:android-job-simplified-1.4.3",
    ":libs:androidx.appcompat-1.0.2",
    ":libs:com.tencent.bugly.crashreport-4.0.4",
    ":libs:jackpal.androidterm-1.0.70",
    ":libs:jackpal.androidterm.emulatorview-1.0.42",
    ":libs:jackpal.androidterm.libtermexec-1.0",
    ":libs:org.opencv-4.8.0",
    ":libs:paddleocr",
    ":libs:dev.rikka.shizuku-shared-13.1.5",
    ":libs:dev.rikka.shizuku-aidl-13.1.5",
    ":libs:dev.rikka.shizuku-api-13.1.5",
    ":libs:dev.rikka.shizuku-provider-13.1.5",

    ":libs:android-spackle-9.0.0",
    ":libs:android-assertion-9.0.0",
    ":libs:android-plugin-client-sdk-for-locale-9.0.0",
)

pluginManagement {

    val platformIdentifierForAS = "AndroidStudio"
    val platformIdentifierForIdea = "IntelliJIdea"

    val unknownIdentifier = "unknown"
    val unknownIdentifier1Up = unknownIdentifier[0].uppercase() + unknownIdentifier.substring(1)
    val previewIdentifier = "preview"
    val previewIdentifier1Up = previewIdentifier[0].uppercase() + previewIdentifier.substring(1)
    val predictedIdentifier = "predicted"
    val predictedSuffix = " [$predictedIdentifier]"
    val fallbackIdentifier = "fallback"
    val fallbackSuffix = " [$fallbackIdentifier]"

    // @Hint by SuperMonster003 on May 3, 2023.
    //  ! To download archives of Android Studio,
    //  ! visit https://developer.android.com/studio/archive?hl=en
    //  !
    //  ! To check the releases for AGP (Android Gradle Plugin),
    //  ! visit https://developer.android.com/reference/tools/gradle-api
    //  !
    //  ! To check the compatibility and released date of kotlin plugins,
    //  ! visit https://plugins.jetbrains.com/plugin/6954-kotlin/versions/eap
    //  !
    //  ! To check the releases for KSP (Kotlin Symbol Processing) plugin,
    //  ! visit https://github.com/google/ksp/releases

    val gradlePluginVersionMap = mapOf(
        "as" to mapOf(
            "abbr" to mapOf(
                "Preview2023.2" to "I", /* Aug 25, 2023. */
                "2023.1" to "H", /* Oct 18, 2023. */
                "Preview2023.1" to "H", /* May 13, 2023. */
                "2022.3" to "G", /* Jul 3, 2023. */
                "Preview2022.3" to "G", /* May 3, 2023. */
                "2022.2" to "F", /* May 3, 2023. */
                "Preview2022.2" to "F", /* May 3, 2023. */
                "2022.1" to "E", /* May 3, 2023. */
            ),
            "android" to mapOf(
                "Preview2023.2" to "8.3.0-alpha10", /* Oct 24, 2023. */
                "2023.1" to "8.2.0-rc01", /* Oct 18, 2023. */
                "Preview2023.1" to "8.2.0-beta06", /* Oct 11, 2023. */
                "2022.3" to "8.1.2", /* Sep 29, 2023. */
                "Preview2022.3" to "8.1.0-beta05", /* Jun 14 2023. */
                "2022.2" to "8.0.2", /* May 26, 2023. */
                "Preview2022.2" to "8.0.0-beta05", /* Mar 25, 2023. */
                "2022.1" to "7.4.2", /* Mar 25, 2023. */
                fallbackIdentifier to "7.4.2", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "Preview2023.2" to "1.9.20-RC", /* Oct 12, 2023. */
                "2023.1" to "1.9.20-RC2", /* Oct 25, 2023. */
                "Preview2023.1" to "1.9.20-RC", /* Oct 12, 2023. */
                "2022.3" to "1.9.0-RC", /* Jul 3, 2023. */
                "Preview2022.3" to "1.8.0", /* May 13, 2023. */
                "2022.2" to "1.8.20-RC2", /* Mar 23, 2023. */
                "Preview2022.2" to "1.8.0", /* Mar 23, 2023. */
                "2022.1" to "1.8.0-RC2", /* Dec 20, 2022. */
                fallbackIdentifier to "1.8.0", /* Aug 17, 2023. */
            )
        ),
        "idea" to mapOf(
            "android" to mapOf(
                "2023.2" to "8.1.2", /* Oct 18, 2023. */
                "2023.1" to "7.4.2", /* May 26, 2023. */
                "2022.3" to "7.4.0-beta02", /* Mar 25, 2023. */
                fallbackIdentifier to "7.4.0", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "2023.2" to "1.9.20-RC", /* Oct 18, 2023. */
                "2023.1" to "1.8.21", /* Apr 25, 2023. */
                "2022.3" to "1.8.21", /* Apr 25, 2023. */
                fallbackIdentifier to "1.8.21", /* May 3, 2023. */
            ),
        ),
    )

    val kspPluginVersionMap = mapOf(
        // "1.9.20-RC2" to "1.0.13", /* Oct 25, 2023. */
        "1.9.20-RC" to "1.0.13", /* Oct 12, 2023. */
        "1.9.20-Beta2" to "1.0.13", /* Sep 29, 2023. */
        "1.9.20-Beta" to "1.0.13", /* Sep 12, 2023. */
        "1.9.10" to "1.0.13", /* Sep 11, 2023. */
        "1.9.0" to "1.0.13", /* Aug 16, 2023. */
        "1.9.0-RC" to "1.0.11", /* Aug 16, 2023. */
        "1.8.21" to "1.0.11", /* Aug 16, 2023. */
        "1.8.20-RC2" to "1.0.9", /* Aug 16, 2023. */
        "1.8.0" to "1.0.9", /* Aug 16, 2023. */
        "1.8.0-RC2" to "1.0.8", /* Aug 16, 2023. */
        fallbackIdentifier to "1.8.0-1.0.9", /* Aug 16, 2023. */
    )

    val platformNicknameForAS = mapOf(
        "Z" to "Zebra", /* Predicted on Oct 18, 2023. */
        "Y" to "Yeti", /* Predicted on Oct 18, 2023. */
        "X" to "Xiphias", /* Predicted on Oct 18, 2023. */
        "W" to "Walrus", /* Predicted on Oct 18, 2023. */
        "V" to "Vicuna", /* Predicted on Oct 18, 2023. */
        "U" to "Unicorn", /* Predicted on Oct 18, 2023. */
        "T" to "Tiger", /* Predicted on Oct 18, 2023. */
        "S" to "Snail", /* Predicted on Oct 18, 2023. */
        "R" to "Rhino", /* Predicted on Oct 18, 2023. */
        "Q" to "Quail", /* Predicted on Oct 18, 2023. */
        "P" to "Penguin", /* Predicted on Oct 18, 2023. */
        "O" to "Ostrich", /* Predicted on Oct 18, 2023. */
        "N" to "Newt", /* Predicted on Oct 18, 2023. */
        "M" to "Monkey", /* Predicted on Oct 18, 2023. */
        "L" to "Lion", /* Predicted on Oct 18, 2023. */
        "K" to "Koala", /* Predicted on Oct 18, 2023. */
        "J" to "Jaguar", /* Predicted on Oct 18, 2023. */
        "I" to "Iguana", /* Aug 25, 2023. */
        "H" to "Hedgehog", /* Apr 25, 2023. */
        "G" to "Giraffe", /* Jan 17, 2023. */
        "F" to "Flamingo", /* Sep 20, 2022. */
        "E" to "Electric Eel", /* May 11, 2022. */
        "D" to "Dolphin", /* Jan 31, 2022. */
        "C" to "Chipmunk", /* Oct 13, 2021. */
        "B" to "Bumblebee", /* May 18, 2021. */
        "A" to "Arctic Fox", /* Jan 26, 2021. */
    )

    /* Null safety. */
    val platform = System.getProperty("idea.paths.selector") ?: platformIdentifierForAS

    val isPlatformAS = platform.startsWith(platformIdentifierForAS)
    val isPlatformIdea = platform.startsWith(platformIdentifierForIdea)

    val platformType = when {
        isPlatformAS -> "as"
        isPlatformIdea -> "idea"
        else -> throw Exception("$unknownIdentifier1Up platform: $platform")
    }

    val platformVersion = when {
        isPlatformAS -> platform.substring(platformIdentifierForAS.length)
        isPlatformIdea -> platform.substring(platformIdentifierForIdea.length)
        else -> "$unknownIdentifier1Up platform version" /* Should never happen. */
    }

    val abbrMap = gradlePluginVersionMap[platformType]!!["abbr"] ?: mapOf()
    val androidMap = gradlePluginVersionMap[platformType]!!["android"]!!
    val kotlinMap = gradlePluginVersionMap[platformType]!!["kotlin"]!!

    var infoList = emptyArray<String>()

    gradle.extra.apply {
        set("platformVersion", platformVersion)
        set("platformType", platformType)
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
        }

        dependencies /* Gather platform information. */ {
            when {
                isPlatformAS -> {
                    var isPredicted = false
                    val platformNickAbbr = abbrMap[platformVersion] ?: abbrMap["$previewIdentifier1Up$platformVersion"]?.also {
                        isPredicted = true
                    }
                    val platformNick = platformNickAbbr?.let { abbr ->
                        platformNicknameForAS[abbr]?.let {
                            when (isPredicted) {
                                true -> " [ $it ($predictedIdentifier) ]"
                                else -> " $it"
                            }
                        }
                    } ?: ""

                    val isPreview = platformVersion.contains(previewIdentifier1Up, true)
                    val previewSuffix = if (isPreview) " ($previewIdentifier)" else ""
                    val niceVersion = if (isPreview) platformVersion.substring(previewIdentifier1Up.length) else platformVersion

                    "Android Studio$platformNick$previewSuffix | $niceVersion"
                }
                isPlatformIdea -> "IntelliJ IDEA $platformVersion"
                else -> unknownIdentifier1Up
            }.let { infoList += "Platform: $it" }
        }

        dependencies /* Android/Kotlin Gradle Plugin. */  {
            arrayOf(
                arrayOf(
                    /* classpath = */ "com.android.tools.build:gradle",
                    /* version = */ androidMap[platformVersion],
                    /* versionPredicted = */ androidMap["$previewIdentifier1Up$platformVersion"],
                    /* versionFallback = */ androidMap[fallbackIdentifier],
                ),
                arrayOf(
                    /* classpath = */ "org.jetbrains.kotlin:kotlin-gradle-plugin",
                    /* version = */ kotlinMap[platformVersion],
                    /* versionPredicted = */ kotlinMap["$previewIdentifier1Up$platformVersion"],
                    /* versionFallback = */ kotlinMap[fallbackIdentifier],
                ),
            ).forEach {
                val (classpath, version, versionPredicted, versionFallback) = it

                var suffix = ""
                val classpathNotation = "$classpath:" + (
                        version ?: versionPredicted?.also {
                            suffix += predictedSuffix
                        } ?: versionFallback?.also {
                            suffix += fallbackSuffix
                        } ?: throw Exception("$unknownIdentifier1Up version for classpath $classpath"))

                infoList += "Classpath: \"$classpathNotation\"$suffix"
                classpath(classpathNotation)
            }
        }

    }

    plugins {
        arrayOf(
            arrayOf(
                /* id = */ "com.google.devtools.ksp",
                /* kspVersionKey = */ kotlinMap[platformVersion],
                /* kspVersionPredictedKey = */ kotlinMap["$previewIdentifier1Up$platformVersion"],
                /* kspVersionFallbackKey = */ kotlinMap[fallbackIdentifier],
                /* isApply = */ false,
            ),
        ).forEach { data ->
            val id = data[0] as String
            val kspVersionKey = data[1] as String?
            val kspVersionPredictedKey = data[2] as String?
            val kspVersionFallbackKey = data[3] as String
            val isApply = data[4] as Boolean

            var suffix = ""

            val pluginVersion = kspPluginVersionMap[kspVersionKey]?.let {
                "$kspVersionKey-$it"
            } ?: kspPluginVersionMap[kspVersionPredictedKey]?.let {
                suffix += predictedSuffix
                "$kspVersionPredictedKey-$it"
            } ?: kspPluginVersionMap[kspVersionFallbackKey]?.let {
                suffix += fallbackSuffix
                "$kspVersionFallbackKey-$it"
            } ?: throw Exception("$unknownIdentifier1Up version for plugin $id")

            infoList += "Plugin: \"$id:$pluginVersion\"$suffix"
            id(id) version pluginVersion apply isApply
        }
    }

    extra /* Print information. */ {
        val title = "Version information for IDE platform and Gradle plugins"
        val maxLength = infoList.plus(title).maxOf { it.length }

        arrayOf(
            "=".repeat(maxLength),
            title,
            "-".repeat(maxLength),
            *infoList,
            "=".repeat(maxLength),
            "",
        ).forEach { println(it) }
    }

}