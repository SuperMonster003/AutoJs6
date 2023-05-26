@file:Suppress("UnnecessaryVariable")

// Top-level build file where you can add configuration options common to all sub-projects/modules.

extra.apply {
    set("configurationName", "default")
}

buildscript {

    /* --== variables ==-- */

    // @Hint by SuperMonster003 on May 3, 2023.
    //  ! To download archives of Android Studio,
    //  ! visit https://developer.android.com/studio/archive
    //  ! To check the compatibility and released date of kotlin plugins,
    //  ! visit https://plugins.jetbrains.com/plugin/6954-kotlin/versions/eap

    val gradlePluginVersionList = mapOf(
        "as" to mapOf(
            "abbr" to mapOf(
                "Preview2023.1" to "H", /* May 13, 2023. */
                "Preview2022.3" to "G", /* May 3, 2023. */
                "2022.2" to "F", /* May 3, 2023. */
                "Preview2022.2" to "F", /* May 3, 2023. */
                "2022.1" to "E", /* May 3, 2023. */
            ),
            "android" to mapOf(
                "Preview2023.1" to "8.2.0-alpha05", /* May 26, 2023. */
                "Preview2022.3" to "8.1.0-beta03", /* May 26 2023. */
                "2022.2" to "8.0.2", /* May 26, 2023. */
                "Preview2022.2" to "8.0.0-beta05", /* Mar 25, 2023. */
                "2022.1" to "7.4.2", /* Mar 25, 2023. */
                "fallback" to "7.4.2", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                "Preview2023.1" to "1.8.0", /* May 13, 2023. */
                "Preview2022.3" to "1.8.0", /* May 13, 2023. */
                "2022.2" to "1.8.20-RC2", /* Mar 23, 2023. */
                "Preview2022.2" to "1.8.0", /* Mar 23, 2023. */
                "2022.1" to "1.8.0-RC2", /* Dec 20, 2022. */
                "fallback" to "1.8.0-RC2", /* May 3, 2023. */
            )
        ),
        "idea" to mapOf(
            "android" to mapOf(
                "2023.1" to "7.4.2", /* May 26, 2023. */
                "2022.3" to "7.4.0-beta02", /* Mar 25, 2023. */
                "fallback" to "7.4.0", /* May 3, 2023. */
            ),
            "kotlin" to mapOf(
                // CAUTION by SuperMonster003 on May 26, 2023.
                //  ! Do not update to "1.9.0-Beta" for now.
                "2023.1" to "1.8.21", /* Apr 25, 2023. */
                "2022.3" to "1.8.21", /* Apr 25, 2023. */
                "fallback" to "1.8.21", /* May 3, 2023. */
            ),
        ),
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

    val platformNicknameForAS = mapOf(
        "A" to "Arctic Fox",
        "B" to "Bumblebee",
        "C" to "Chipmunk",
        "D" to "Dolphin",
        "E" to "Electric Eel",
        "F" to "Flamingo",
        "G" to "Giraffe",
        "H" to "Hedgehog",
    )

    fun printCurrentPlatformInfo() = when {
        isPlatformAS -> {
            val platformNickAbbr = gradlePluginVersionList["as"]!!["abbr"]!![platformVersion]
            val platformNick = platformNickAbbr?.let { abbr -> platformNicknameForAS[abbr]?.let { " $it" } } ?: ""

            val previewIdentifier = "Preview"
            val isPreview = platformVersion.contains(previewIdentifier, true)
            val previewSuffix = if (isPreview) " ($previewIdentifier)" else ""
            val niceVersion = if (isPreview) platformVersion.substring(previewIdentifier.length) else platformVersion

            "Android Studio$platformNick$previewSuffix | $niceVersion"
        }
        isPlatformIdea -> "IntelliJ IDEA $platformVersion"
        else -> "Unknown"
    }.let { println("Platform: $it") }

    /* --== repositories ==-- */

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

    /* --== dependencies ==-- */

    dependencies /* Print Information. */ {
        printCurrentPlatformInfo()
    }

    dependencies /* Android/Kotlin Gradle Plugin. */  {
        val android = gradlePluginVersionList[platformType]!!["android"]!!
        val kotlin = gradlePluginVersionList[platformType]!!["kotlin"]!!

        arrayOf(
            arrayOf("com.android.tools.build:gradle", android[platformVersion], android["fallback"]),
            arrayOf("org.jetbrains.kotlin:kotlin-gradle-plugin", kotlin[platformVersion], kotlin["fallback"]),
        ).forEach { data ->
            val classpathNotation = "${data[0]}:${data[1] ?: data[2]}"
            val suffix = data[1]?.let { "" } ?: " [fallback]"
            println("Classpath: \"$classpathNotation\"$suffix")
            classpath(classpathNotation)
        }
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