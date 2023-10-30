@file:Suppress("SpellCheckingInspection")

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

    val fallbackIdentifier = "fallback"

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
                "Preview2023.2" to "8.3.0-alpha11", /* Oct 27, 2023. */
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
                "Eap2023.3" to "8.1.2", /* Oct 25, 2023. */
                "2023.2" to "8.1.2", /* Oct 18, 2023. */
                "2023.1" to "7.4.2", /* May 26, 2023. */
                "2022.3" to "7.4.0-beta02", /* Mar 25, 2023. */
                fallbackIdentifier to "7.4.0", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "Eap2023.3" to "1.9.20-RC2", /* Oct 25, 2023. */
                "2023.2" to "1.9.20-RC2", /* Oct 18, 2023. */
                "2023.1" to "1.8.21", /* Apr 25, 2023. */
                "2022.3" to "1.8.21", /* Apr 25, 2023. */
                fallbackIdentifier to "1.8.21", /* May 3, 2023. */
            ),
        ),
        "unknown" to mapOf(
            "android" to mapOf(
                fallbackIdentifier to "8.1.2", /* Oct 30, 2023. */
            ),
            "kotlin" to mapOf(
                fallbackIdentifier to "1.8.21", /* May 3, 2023. */
            ),
        )
    )

    val kspPluginVersionMap = mapOf(
        "1.9.20-RC2" to "1.0.13", /* Oct 26, 2023. */
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

    val platformCodenameForAS = mapOf(
        "I" to "Iguana", /* Born on Aug 25, 2023. */
        "H" to "Hedgehog", /* Born on Apr 25, 2023. */
        "G" to "Giraffe", /* Born on Jan 17, 2023. */
        "F" to "Flamingo", /* Born on Sep 20, 2022. */
        "E" to "Electric Eel", /* Born on May 11, 2022. */
        "D" to "Dolphin", /* Born on Jan 31, 2022. */
        "C" to "Chipmunk", /* Born on Oct 13, 2021. */
        "B" to "Bumblebee", /* Born on May 18, 2021. */
        "A" to "Arctic Fox", /* Born on Jan 26, 2021. */

        /* Codenames below were predicted on Oct 18, 2023. */

        "J" to "Jaguar",
        "K" to "Koala",
        "L" to "Lion",
        "M" to "Monkey",
        "N" to "Newt",
        "O" to "Ostrich",
        "P" to "Penguin",
        "Q" to "Quail",
        "R" to "Rhino",
        "S" to "Snail",
        "T" to "Tiger",
        "U" to "Unicorn",
        "V" to "Vicuna",
        "W" to "Walrus",
        "X" to "Xiphias",
        "Y" to "Yeti",
        "Z" to "Zebra",
    )

    val platformIdentifierForAS = "AndroidStudio"
    val vendorNameForAS = "Google"
    val platformIdentifierForIdea = "IntelliJIdea"
    val vendorNameForIdea = "Jetbrains"

    fun uppercaseFirstChar(s: String) = s[0].uppercase() + s.substring(1)

    val unknownIdentifier = "unknown"
    val unknownIdentifier1Up = uppercaseFirstChar(unknownIdentifier)
    val predictedIdentifier = "predicted"
    val unfocusedIdentifier = "unfocused"
    val unfocusedSuffix = " [$unfocusedIdentifier]"
    val predictedSuffix = " [$predictedIdentifier]"
    val fallbackSuffix = " [$fallbackIdentifier]"

    /* Nullable. */
    val platform = System.getProperty("idea.paths.selector") ?: System.getProperty("idea.platform.prefix") ?: unknownIdentifier

    val isPlatformAS = platform?.startsWith(platformIdentifierForAS) == true
            || System.getProperty("idea.vendor.name").equals(vendorNameForAS, true)
    val isPlatformIdea = platform?.startsWith(platformIdentifierForIdea) == true
            || System.getProperty("idea.vendor.name").equals(vendorNameForIdea, true)

    val platformType = when {
        isPlatformAS -> "as"
        isPlatformIdea -> "idea"
        else -> unknownIdentifier
    }

    val previewIdentifier = when {
        isPlatformAS -> "preview"
        isPlatformIdea -> "eap"
        else -> ""
    }

    val previewIdentifier1Up = previewIdentifier ?: uppercaseFirstChar(previewIdentifier)

    val platformVersion = (System.getProperty("idea.version") ?: when {
        isPlatformAS -> platform?.substring(platformIdentifierForAS.length)
        isPlatformIdea -> platform?.substring(platformIdentifierForIdea.length)
        else -> unknownIdentifier
    } ?: throw Exception("$unknownIdentifier1Up platform version"))
        .let { rawVersion ->
            platform?.let { rawVersion }
                ?: when (rawVersion.contains(previewIdentifier, true)) {
                    true -> rawVersion
                    else -> "$previewIdentifier1Up$rawVersion"
                }
        }

    val platformVersionUnfocused = platformVersion.replace(Regex("(.+)(\\.\\d+)(\\.\\d+$)"), "$1$2")

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
                    val platformCodenameAbbr = abbrMap[platformVersion] ?: abbrMap["$previewIdentifier1Up$platformVersion"]?.also {
                        isPredicted = true
                    }
                    val platformCodeName = platformCodenameAbbr?.let { abbr ->
                        platformCodenameForAS[abbr]?.let {
                            when (isPredicted) {
                                true -> " [ $it ($predictedIdentifier) ]"
                                else -> " $it"
                            }
                        }
                    } ?: ""

                    val isPreview = platformVersion.contains(previewIdentifier1Up, true)
                    val previewSuffix = if (isPreview) " ($previewIdentifier)" else ""
                    val niceVersion = if (isPreview) platformVersion.substring(previewIdentifier1Up.length) else platformVersion

                    "Android Studio$platformCodeName$previewSuffix${if (niceVersion.isNotEmpty()) " | $niceVersion" else ""}"
                }
                isPlatformIdea -> {
                    val isEap = platformVersion.contains(previewIdentifier1Up, true)
                    val isPredicted = platform == null
                    val suffix = when (isEap) {
                        true -> when (isPredicted) {
                            true -> " [ ${previewIdentifier.uppercase()} ($predictedIdentifier) ]"
                            else -> " ${previewIdentifier.uppercase()}"
                        }
                        else -> when (isPredicted) {
                            true -> " ($predictedIdentifier)"
                            else -> ""
                        }
                    }
                    val niceVersion = if (isEap) platformVersion.substring(previewIdentifier1Up.length) else platformVersion

                    "IntelliJ IDEA$suffix${if (niceVersion.isNotEmpty()) " | $niceVersion" else ""}"
                }
                else -> unknownIdentifier1Up
            }.let { infoList += "Platform: $it" }
        }

        dependencies /* Android/Kotlin Gradle Plugin. */  {
            arrayOf(
                arrayOf("com.android.tools.build:gradle", androidMap),
                arrayOf("org.jetbrains.kotlin:kotlin-gradle-plugin", kotlinMap),
            ).forEach {
                val (classpath, map) = it

                map as? Map<*, *> ?: throw Exception("Invalid map for classpath")

                val version = map[platformVersion] as? String
                val versionUnfocused = map[platformVersionUnfocused] as? String
                val versionPredicted = (map["$previewIdentifier1Up$platformVersion"]
                    ?: map["$previewIdentifier1Up$platformVersionUnfocused"]) as? String
                val versionFallback = map[fallbackIdentifier] as? String

                var suffix = ""
                val classpathNotation = "$classpath:" + (
                        version ?: versionUnfocused?.also {
                            suffix += unfocusedSuffix
                        } ?: versionPredicted?.also {
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

        arrayOf("com.google.devtools.ksp", kotlinMap, false).let { data ->
            var (id, map, isApply) = data

            id as? String ?: throw Exception("Invalid id for plugin")
            map as? Map<*, *> ?: throw Exception("Invalid map for plugin")
            isApply = isApply as? Boolean ?: false

            val kspVersionKey = map[platformVersion] as String?
            val kspVersionUnfocusedKey = map[platformVersionUnfocused] as String?
            val kspVersionPredictedKey = (map["$previewIdentifier1Up$platformVersion"]
                ?: map["$previewIdentifier1Up$platformVersionUnfocused"]) as String?
            val kspVersionFallbackKey = map[fallbackIdentifier] as String

            var suffix = ""

            val pluginVersion = kspPluginVersionMap[kspVersionKey]?.let {
                "$kspVersionKey-$it"
            } ?: kspPluginVersionMap[kspVersionUnfocusedKey]?.let {
                suffix += unfocusedSuffix
                "$kspVersionUnfocusedKey-$it"
            } ?: kspPluginVersionMap[kspVersionPredictedKey]?.let {
                suffix += predictedSuffix
                "$kspVersionPredictedKey-$it"
            } ?: kspPluginVersionMap[kspVersionFallbackKey]?.let {
                suffix += fallbackSuffix
                "$kspVersionFallbackKey-$it"
            } ?: throw Exception("$unknownIdentifier1Up version for plugin $id")

            infoList += "Plugin: \"$id:$pluginVersion\"$suffix"
            id(id) version pluginVersion apply isApply as Boolean
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
