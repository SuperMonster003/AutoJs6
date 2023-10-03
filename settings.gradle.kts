@file:Suppress("SpellCheckingInspection")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

include(
    ":app",

    ":libs:android-job-simplified-1.4.3",
    ":libs:androidx.appcompat-1.0.2",
    ":libs:com.tencent.bugly.crashreport-4.0.4",
    ":libs:com.zeugmasolutions.localehelper-1.5.1",
    ":libs:jackpal.androidterm-1.0.70",
    ":libs:jackpal.androidterm.emulatorview-1.0.42",
    ":libs:jackpal.androidterm.libtermexec-1.0",
    ":libs:org.opencv-4.5.5",
    ":libs:paddleocr",

    ":libs:android-spackle-9.0.0",
    ":libs:android-assertion-9.0.0",
    ":libs:android-plugin-client-sdk-for-locale-9.0.0",
)

pluginManagement {

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
                "Preview2023.1" to "H", /* May 13, 2023. */
                "2022.3" to "G", /* Jul 3, 2023. */
                "Preview2022.3" to "G", /* May 3, 2023. */
                "2022.2" to "F", /* May 3, 2023. */
                "Preview2022.2" to "F", /* May 3, 2023. */
                "2022.1" to "E", /* May 3, 2023. */
            ),
            "android" to mapOf(
                "Preview2023.2" to "8.3.0-alpha04", /* Sep 16, 2023. */
                "Preview2023.1" to "8.2.0-beta03", /* Sep 11, 2023. */
                "2022.3" to "8.1.2", /* Sep 29, 2023. */
                "Preview2022.3" to "8.1.0-beta05", /* Jun 14 2023. */
                "2022.2" to "8.0.2", /* May 26, 2023. */
                "Preview2022.2" to "8.0.0-beta05", /* Mar 25, 2023. */
                "2022.1" to "7.4.2", /* Mar 25, 2023. */
                "fallback" to "7.4.2", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "Preview2023.2" to "1.9.20-Beta", /* Sep 12, 2023. */
                "Preview2023.1" to "1.9.20-Beta", /* Sep 12, 2023. */
                "2022.3" to "1.9.0-RC", /* Jul 3, 2023. */
                "Preview2022.3" to "1.8.0", /* May 13, 2023. */
                "2022.2" to "1.8.20-RC2", /* Mar 23, 2023. */
                "Preview2022.2" to "1.8.0", /* Mar 23, 2023. */
                "2022.1" to "1.8.0-RC2", /* Dec 20, 2022. */
                "fallback" to "1.8.0", /* Aug 17, 2023. */
            )
        ),
        "idea" to mapOf(
            "android" to mapOf(
                "2023.2" to "8.1.0", /* Sep 15, 2023. */
                "2023.1" to "7.4.2", /* May 26, 2023. */
                "2022.3" to "7.4.0-beta02", /* Mar 25, 2023. */
                "fallback" to "7.4.0", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "2023.2" to "1.9.20-Beta", /* Sep 12, 2023. */
                "2023.1" to "1.8.21", /* Apr 25, 2023. */
                "2022.3" to "1.8.21", /* Apr 25, 2023. */
                "fallback" to "1.8.21", /* May 3, 2023. */
            ),
        ),
    )

    val kspPluginVersionMap = mapOf(
        "1.9.20-Beta2" to "1.0.13", /* Sep 29, 2023. */
        "1.9.20-Beta" to "1.0.13", /* Sep 12, 2023. */
        "1.9.10" to "1.0.13", /* Sep 11, 2023. */
        "1.9.0" to "1.0.13", /* Aug 16, 2023. */
        "1.9.0-RC" to "1.0.11", /* Aug 16, 2023. */
        "1.8.21" to "1.0.11", /* Aug 16, 2023. */
        "1.8.20-RC2" to "1.0.9", /* Aug 16, 2023. */
        "1.8.0" to "1.0.9", /* Aug 16, 2023. */
        "1.8.0-RC2" to "1.0.8", /* Aug 16, 2023. */
        "fallback" to "1.8.0-1.0.9", /* Aug 16, 2023. */
    )

    val platformNicknameForAS = mapOf(
        "I" to "Iguana",
        "H" to "Hedgehog",
        "G" to "Giraffe",
        "F" to "Flamingo",
        "E" to "Electric Eel",
        "D" to "Dolphin",
        "C" to "Chipmunk",
        "B" to "Bumblebee",
        "A" to "Arctic Fox",
    )

    val platform = System.getProperty("idea.paths.selector")
    val platformIdentifierForAS = "AndroidStudio"
    val platformIdentifierForIdea = "IntelliJIdea"

    val isPlatformAS = platform.startsWith(platformIdentifierForAS)
    val isPlatformIdea = platform.startsWith(platformIdentifierForIdea)

    val platformVersion = when {
        isPlatformAS -> platform.substring(platformIdentifierForAS.length)
        isPlatformIdea -> platform.substring(platformIdentifierForIdea.length)
        else -> "Unknown"
    }

    val platformType = when {
        isPlatformAS -> "as"
        isPlatformIdea -> "idea"
        else -> throw Exception("Unknown platform: $platform")
    }

    val kotlinVersion = gradlePluginVersionMap[platformType]!!["kotlin"]!!.let { kotlinMap ->
        kotlinMap[platformVersion] ?: kotlinMap["fallback"]!!
    }

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
                    val platformNickAbbr = gradlePluginVersionMap["as"]!!["abbr"]!![platformVersion]
                    val platformNick = platformNickAbbr?.let { abbr -> platformNicknameForAS[abbr]?.let { " $it" } } ?: ""

                    val previewIdentifier = "Preview"
                    val isPreview = platformVersion.contains(previewIdentifier, true)
                    val previewSuffix = if (isPreview) " ($previewIdentifier)" else ""
                    val niceVersion = if (isPreview) platformVersion.substring(previewIdentifier.length) else platformVersion

                    "Android Studio$platformNick$previewSuffix | $niceVersion"
                }
                isPlatformIdea -> "IntelliJ IDEA $platformVersion"
                else -> "Unknown"
            }.let { infoList += "Platform: $it" }
        }

        dependencies /* Android/Kotlin Gradle Plugin. */  {
            val androidMap = gradlePluginVersionMap[platformType]!!["android"]!!
            val kotlinMap = gradlePluginVersionMap[platformType]!!["kotlin"]!!

            arrayOf(
                arrayOf("com.android.tools.build:gradle", androidMap[platformVersion], androidMap["fallback"]),
                arrayOf("org.jetbrains.kotlin:kotlin-gradle-plugin", kotlinMap[platformVersion], kotlinMap["fallback"]),
            ).forEach { data ->
                val classpath = data[0]
                val version = data[1]
                val versionFallback = data[2]

                val classpathNotation = "$classpath:${version ?: versionFallback}"
                val suffix = version?.let { "" } ?: " [fallback]"

                infoList += "Classpath: \"$classpathNotation\"$suffix"
                classpath(classpathNotation)
            }
        }

    }

    plugins {
        arrayOf(
            arrayOf("com.google.devtools.ksp", kspPluginVersionMap[kotlinVersion], kspPluginVersionMap["fallback"], false),
        ).forEach { data ->
            val id = data[0] as String
            val kspVersion = data[1] as String?
            val kspVersionFallback = data[2] as String
            val isApply = data[3] as Boolean

            val pluginVersion = kspVersion?.let { "$kotlinVersion-$it" } ?: kspVersionFallback
            val suffix = kspVersion?.let { "" } ?: " [fallback]"

            infoList += "Plugin: \"$id:$pluginVersion\"$suffix"
            id(id) version pluginVersion apply isApply
        }
    }

    extra /* Print information. */ {
        val title = "Version information for IDE platform and Gradle plugins"
        val maxLength = infoList.maxOf { it.length }

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