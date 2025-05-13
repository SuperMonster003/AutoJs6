@file:Suppress("SpellCheckingInspection")

private val modules = listOf(
    "jieba-analysis",
    "apk-signer",
    "apk-parser",
    "color-picker",
    "material-dialogs",
    "material-date-time-picker",
)

private val libs = listOf(
    "android-job-simplified-1.4.3",
    "androidx.appcompat-1.0.2",
    "apk-parser-1.0.2",
    "com.tencent.bugly.crashreport-4.0.4",
    "org.opencv-4.8.0",
    "paddleocr",
    "rapidocr",

    "jackpal.androidterm-1.0.70",
    "jackpal.androidterm.emulatorview-1.0.42",
    "jackpal.androidterm.libtermexec-1.0",

    "android-spackle-9.0.0",
    "android-assertion-9.0.0",
    "android-plugin-client-sdk-for-locale-9.0.0",

    "markwon-core-4.6.2",
    "markwon-syntax-highlight-4.6.2",
)

include(
    ":app",
    *modules.map { ":modules:$it" }.toTypedArray(),
    *libs.map { ":libs:$it" }.toTypedArray(),
)

modules.forEach {
    project(":modules:$it").projectDir = File("modules", it)
}

pluginManagement {

    // @Hint by SuperMonster003 on May 13, 2025.
    //  ! Change the null value to specify a version (instead of auto-selection), e.g. 23 or "8.3.0-beta02".
    //  ! zh-CN: 如需指定版本 (而非自动选择版本), 可修改相应的 null 值, 如 23 或 "8.3.0-beta02".
    //  !
    //  ! Gradle JVM
    val overriddenJavaVersion: Int? = null
    //  ! classpath("com.android.tools.build:gradle")
    val overriddenAgpVersion: String? = null
    //  ! classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")
    val overriddenKotlinVersion: String? = null
    //  ! plugin("com.google.devtools.ksp")
    val overriddenKspVersion: String? = null

    // @Signature Pair<Gradle Version, Embedded Compatible Kotlin Version>
    // @Reference gradle-<version>-src.zip\gradle\dependency-management\kotlin-version.properties
    // @Reference https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    // @Updated by SuperMonster003 on May 12, 2025.
    val embeddedKotlin: List<Pair<String, String>> = listOf(

        /* Unofficial. */

        "9.0" to "2.1.20",
        "8.14" to "2.1.10",
        "8.13" to "2.1.10",

        /* Official. */

        "8.12" to "2.0.21",
        "8.11" to "2.0.20",
        "8.10" to "1.9.24",
        "8.9" to "1.9.23",
        "8.7" to "1.9.22",
        "8.5" to "1.9.20",
        "8.4" to "1.9.10",
        "8.3" to "1.9.0",
        "8.2" to "1.8.20",
        "8.0" to "1.8.10",
    )

    // @Signature Pair<Supported Java Version, Min Required Gradle Version>
    // @Reference https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
    // @Updated by SuperMonster003 on May 12, 2025.
    val javaGradleCompatibility = listOf(

        /* Unofficial. */

        25 to "9.0",

        /* Official. */

        24 to "8.14",
        23 to "8.10",
        22 to "8.8",
        21 to "8.5",
        20 to "8.3",
        19 to "7.6",
        18 to "7.5",
        17 to "7.3",
    )

    // @Signature Pair<Android Gradle Plugin Version, Min Required Gradle Version>
    // @Reference https://developer.android.com/build/releases/gradle-plugin#updating-gradle
    // @Updated by SuperMonster003 on May 13, 2025.
    val agpGradleCompatibility: List<Pair<String, String>> = listOf(
        "8.9" to "8.11.1",
        "8.8" to "8.10.2",
        "8.7" to "8.9",
        "8.6" to "8.7",
        "8.5" to "8.7",
        "8.4" to "8.6",
        "8.3" to "8.4",
        "8.2" to "8.2",
        "8.1" to "8.0",
        "8.0" to "8.0",
        "7.4" to "7.5",
        "7.3" to "7.4",
    )

    // @Reference https://developer.android.com/reference/tools/gradle-api
    // @Updated by SuperMonster003 on May 13, 2025.
    val agpReleases: List<String> = listOf(
        "8.11.0-alpha10",
        "8.10.0",
        "8.9.2",
        "8.8.2",
        "8.7.3",
        "8.6.1",
        "8.5.2",
        "8.4.2",
        "8.3.2",
        "8.2.2",
        "8.1.4",
        "8.0.2",
        "7.4.2",
    )

    val consts = object {
        val DEFAULT_VERSION = "0"
    }

    val systemProperties = object {
        val version: String? = System.getProperty("idea.version")
        val platform: String? = System.getProperty("idea.paths.selector")
            ?: System.getProperty("idea.platform.prefix")
            ?: System.getProperty("java.vendor.version")
        val vendorName: String? = System.getProperty("idea.vendor.name")
            ?: System.getProperty("java.vendor")
            ?: System.getProperty("java.vm.vendor")
        val concerns: List<String> by lazy {
            val concernedKeyWords = setOf("name", "vendor", "version", "platform", "paths", "os")
            val unconcernedKeyWords = setOf("url", "user", "runtime", "specification", "date")
            val unconcernedKeys = setOf(
                "java.class.version",
                "java.vm.name",
                "java.vm.version",
                "java.version",
                "platform.random.idempotence.check.rate",
                "sun.os.patch.level",
            )
            System.getProperties().filterKeys { key ->
                return@filterKeys key is String
                        && key !in unconcernedKeys
                        && key.split(Regex("\\W")).any { it in concernedKeyWords }
                        && key.split(Regex("\\W")).none { it in unconcernedKeyWords }
            }.map { (key, value) -> "[ $key: $value ]" }
        }
        var isConcernsAlreadyPrinted = false
    }

    data class Classpath(val id: String, val version: String)

    data class Plugin(val id: String, val version: String, val isApply: Boolean = false)

    class Formatted(title: String, private val contents: Collection<String> = emptyList(), subtitle: String? = null) {
        private val formattedOutput = run {
            val elements = mutableListOf<String>()
            subtitle?.let { elements.add(it) }
            elements.addAll(contents)
            val maxLength = elements.plus(title).maxOf { it.length }

            listOfNotNull(
                "=".repeat(maxLength),
                title,
                subtitle,
                "-".repeat(maxLength).takeUnless { contents.isEmpty() },
                *contents.toTypedArray(),
                "=".repeat(maxLength),
                "",
            )
        }

        fun print(contentsMatters: Boolean = false) = formattedOutput.forEach { if (!contentsMatters || contents.isNotEmpty()) println(it) }

        fun throwException(): Unit = throw Exception(formattedOutput.joinToString("\n"))
    }

    val config = object {

        /* Print concerned info by `System.getProperties()`. */
        val isShowConcernedSystemProperties = true

        val isCleanupPaddleOcr = false
        val isCleanupRapidOcr = false

        val fallbackAgpVersion = "7.4.2"
        val fallbackKotlinVersion = "1.7.10"

        @Suppress("unused")
        val platforms = object {

            val androidStudio = object : Platform(
                name = "AndroidStudio", vendor = "Google",
                // @Reference https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility
                // @Updated by SuperMonster003 on May 13, 2025.
                agpVersionMap = mapOf(
                    "2025.1" to "8.10.0", /* May 13, 2025. */
                    "2024.3" to "8.9.2", /* Apr 11, 2025. */
                    "2024.2" to "8.7.3", /* Jan 13, 2025. */
                    "2024.1" to "8.5.2", /* Aug 30, 2024. */
                    "2023.3" to "8.4.2", /* Mar 28, 2024. */
                    "2023.2" to "8.3.2", /* Feb 14, 2024. */
                    "2023.1" to "8.2.2", /* Feb 6, 2024. */
                    "2022.3" to "8.1.4", /* Mar 31, 2024. */
                    "2022.2" to "8.0.2", /* May 26, 2023. */
                    "2022.1" to "7.4.2", /* Mar 25, 2023. */
                ),
                // @Updated by SuperMonster003 on May 13, 2025.
                kotlinVersionMap = mapOf(
                    "2024.3" to "2.1.20", /* Apr 11, 2024. */
                    "2024.2" to "2.1.0", /* Nov 29, 2024. */
                    "2024.1" to "2.0.0", /* Aug 13, 2024. */
                ),
                codenameVersionMap = mapOf(
                    "2025.1" to "N", /* Apr 11, 2025. */
                    "2024.3" to "M", /* Nov 29, 2024. */
                    "2024.2" to "L", /* Aug 13, 2024. */
                    "2024.1" to "K|L", /* May 14, 2024. */
                    "2023.3" to "J|K", /* Jan 21, 2024. */
                    "2023.2" to "I", /* Aug 25, 2023. */
                    "2023.1" to "H", /* May 13, 2023. */
                    "2022.3" to "G", /* May 3, 2023. */
                    "2022.2" to "F", /* May 3, 2023. */
                    "2022.1" to "E", /* May 3, 2023. */
                ),
                codenameMap = mapOf(
                    "N" to "Narwhal", /* Born on Mar 19, 2025. */
                    "M" to "Meerkat", /* Born on Nov 12, 2024. */
                    "L" to "Ladybug", /* Born on Jul 15, 2024. */
                    "K" to "Koala", /* Born on Mar 19, 2024. */
                    "J" to "Jellyfish", /* Born on Dec 28, 2023. */
                    "I" to "Iguana", /* Born on Aug 25, 2023. */
                    "H" to "Hedgehog", /* Born on Apr 25, 2023. */
                    "G" to "Giraffe", /* Born on Jan 17, 2023. */
                    "F" to "Flamingo", /* Born on Sep 20, 2022. */
                    "E" to "Electric Eel", /* Born on May 11, 2022. */
                    "D" to "Dolphin", /* Born on Jan 31, 2022. */
                    "C" to "Chipmunk", /* Born on Oct 13, 2021. */
                    "B" to "Bumblebee", /* Born on May 18, 2021. */
                    "A" to "Arctic Fox",
                    /* Born on Jan 26, 2021. */

                    // @Hint by SuperMonster003 on Oct 18, 2023.
                    //  ! The codenames below were predicted but not official.

                    "O" to "Ostrich", "P" to "Penguin", "Q" to "Quail",
                    "R" to "Rhino", "S" to "Snail", "T" to "Tiger",
                    "U" to "Unicorn", "V" to "Vicuna", "W" to "Walrus",
                    "X" to "Xiphias", "Y" to "Yeti", "Z" to "Zebra",
                ),
            ) {
                override val weight = Int.MAX_VALUE
                override val gradleSettingsName = "Gradle JDK"
                override val fullName by lazy {
                    val suffix = codenameVersionMap?.get(version)
                        ?.split("|")
                        ?.joinToString(" / ", prefix = " ") { key ->
                            codenameMap?.get(key.trim()) ?: key
                        } ?: ""
                    return@lazy "Android Studio$suffix"
                }
            }

            val intelliJIdea = object : Platform(
                name = "IntelliJIdea", vendor = "Jetbrains",
                // @Updated by SuperMonster003 on Apr 23, 2025.
                agpVersionMap = mapOf(
                    "2025.1" to "8.9.2", /* Apr 23, 2025. */
                    "2024.3.1" to "8.7.3", /* Dec 10, 2024. */
                    "2024.3" to "8.7.0-rc01", /* Nov 15, 2024. */
                    "2024.2" to "8.5.2", /* Aug 13, 2024. */
                    "2024.1" to "8.2.2", /* Apr 6, 2024. */
                    "2023.3" to "8.2.2", /* Jan 19, 2024. */
                    "2023.1" to "7.4.2", /* May 26, 2023. */
                    "2022.3" to "7.4.0-beta02", /* Mar 25, 2023. */
                ),
                // @Updated by SuperMonster003 on Apr 23, 2025.
                kotlinVersionMap = mapOf(
                    "2025.1" to "2.1.20", /* Apr 12, 2025. */
                    "2024.3.4" to "2.1.10", /* Feb 28, 2025. */
                    "2024.2.3" to "2.0.21", /* Oct 17, 2024. */
                    "2024.2" to "2.0.21-RC", /* Sep 27, 2024. */
                    "2024.1" to "1.9.24", /* Dec 3, 2024. */
                    "2023.3" to "1.9.23", /* Mar 29, 2024. */
                ),
            ) {
                override val weight = 10
                override val gradleSettingsName = "Gradle JVM"
                override val fullName = "IntelliJ IDEA"
            }

            val temurin = object : Platform(
                name = "Temurin", vendor = "temurin",  /* More common as "Eclipse Adoptium". */
                agpVersionMap = mapOf(
                    "21.0.6+7" to "8.7.3", /* Apr 16, 2025. */
                    "20.0.2+9" to "8.2.2", /* Dec 2, 2024. */
                ),
                kotlinVersionMap = mapOf(
                    "21.0.6+7" to "2.1.10", /* Apr 16, 2025. */
                    "20.0.2+9" to "1.9.24", /* Dec 2, 2024. */
                ),
            ) {
                override val weight = 5
                override val shouldPrintProgress = false
            }

            val unknown = object : Platform(
                name = "Unknown", vendor = "unknown",
            ) {
                fun declare() = systemProperties.platform?.let {
                    println("Unexpected platform: $it")
                } ?: Formatted(
                    "Current platform is unknown",
                    systemProperties.concerns,
                    "However, here are some props may be useful for determining platform info",
                ).print().also { systemProperties.isConcernsAlreadyPrinted = true }
            }

            fun determine(): Platform {
                val candidates = this::class.java.declaredFields.mapNotNull { field ->
                    field.get(this)?.let { tmpPlatform ->
                        tmpPlatform::class.java.methods.find { method ->
                            method.name == Platform::matchEnvironment.name && method.invoke(tmpPlatform) == true
                        }?.let { tmpPlatform as? Platform }
                    }
                }
                return when {
                    candidates.isEmpty() -> when (val osName = System.getProperty("os.name")) {
                        is String -> unknown.also { it.name = osName }
                        else -> unknown.also { it.declare() }
                    }
                    candidates.size > 1 -> candidates.maxBy { it.weight }
                    else -> candidates.first()
                }.also {
                    it.version = systemProperties.version ?: when {
                        it != unknown && systemProperties.platform != null -> {
                            systemProperties.platform.substring(it.name.length)
                                .replace(Regex("^\\W*"), "")
                                .replace(Regex("^Preview", RegexOption.IGNORE_CASE), "")
                        }
                        else -> consts.DEFAULT_VERSION
                    }
                }
            }

        }

        val libs = listOf(
            Classpath(id = "com.android.tools.build:gradle", version = overriddenAgpVersion ?: "auto:agp"),
            Classpath(id = "org.jetbrains.kotlin:kotlin-gradle-plugin", version = overriddenKotlinVersion ?: "auto:kotlin"),
            Plugin(id = "com.google.devtools.ksp", version = overriddenKspVersion ?: "auto:ksp"),
        )

        // @Reference https://github.com/google/ksp/releases
        // @Updated by SuperMonster003 on May 13, 2025.
        val kspVersionMap = mapOf(
            "2.2.0-Beta2" to "2.0.1", /* May 2, 2025. */
            "2.2.0-Beta1" to "2.0.0", /* Apr 17, 2025. */
            "2.1.21-RC2" to "2.0.1", /* May 2, 2025. */
            "2.1.21-RC" to "2.0.0", /* Apr 18, 2025. */
            "2.1.20" to "2.0.1", /* May 1, 2025. */
            "2.1.20-RC3" to "1.0.31", /* Mar 15, 2025. */
            "2.1.20-RC2" to "1.0.31", /* Mar 7, 2025. */
            "2.1.20-RC" to "1.0.31", /* Feb 28, 2025. */
            "2.1.20-Beta2" to "1.0.30", /* Feb 15, 2025. */
            "2.1.20-Beta1" to "1.0.29", /* Dec 25, 2024. */
            "2.1.10" to "1.0.31", /* Feb 28, 2025. */
            "2.1.10-RC" to "1.0.29", /* Jan 10, 2025. */
            "2.1.0" to "1.0.29", /* Nov 28, 2024. */
            "2.1.0-RC2" to "1.0.29", /* Jan 22, 2025. */
            "2.1.0-RC" to "1.0.27", /* Nov 8, 2024. */
            "2.1.0-Beta2" to "1.0.26", /* Oct 26, 2024. */
            "2.1.0-Beta1" to "1.0.25", /* Sep 25, 2024. */
            "2.0.21" to "1.0.28", /* Nov 16, 2024. */
            "2.0.21-RC" to "1.0.25", /* Oct 4, 2024. */
            "2.0.20" to "1.0.25", /* Sep 7, 2024. */
            "2.0.20-RC2" to "1.0.24", /* Aug 24, 2024. */
            "2.0.20-RC" to "1.0.24", /* Aug 8, 2024. */
            "2.0.20-Beta2" to "1.0.23", /* Aug 8, 2024. */
            "2.0.20-Beta1" to "1.0.22", /* Aug 8, 2024. */
            "2.0.10" to "1.0.24", /* Aug 8, 2024. */
            "2.0.10-RC2" to "1.0.24", /* Aug 8, 2024. */
            "2.0.10-RC" to "1.0.23", /* Aug 8, 2024. */
            "2.0.0" to "1.0.24", /* Aug 8, 2024. */
            "2.0.0-RC3" to "1.0.20", /* May 24, 2024. */
            "2.0.0-RC2" to "1.0.20", /* May 24, 2024. */
            "2.0.0-RC1" to "1.0.20", /* May 24, 2024. */
            "2.0.0-Beta5" to "1.0.20", /* May 24, 2024. */
            "2.0.0-Beta4" to "1.0.19", /* May 24, 2024. */
            "2.0.0-Beta3" to "1.0.17", /* May 24, 2024. */
            "2.0.0-Beta2" to "1.0.16", /* May 24, 2024. */
            "2.0.0-Beta1" to "1.0.15", /* May 24, 2024. */
            "1.9.25" to "1.0.20", /* Aug 8, 2024. */
            "1.9.24" to "1.0.20", /* May 14, 2024. */
            "1.9.23" to "1.0.20", /* Apr 6, 2024. */
            "1.9.22" to "1.0.17", /* Jan 20, 2024. */
            "1.9.21" to "1.0.15", /* Dec 2, 2023. */
            "1.9.20" to "1.0.14", /* Nov 9, 2023. */
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
        )

        abstract inner class Platform(
            var name: String,
            val vendor: String,
            val agpVersionMap: Map<String, String> = emptyMap(),
            val kotlinVersionMap: Map<String, String> = emptyMap(),
            val codenameVersionMap: Map<String, String>? = null,
            val codenameMap: Map<String, String>? = null,
        ) {

            open val gradleSettingsName: String? = null
            open val weight: Int = -Int.MAX_VALUE
            open var version: String = consts.DEFAULT_VERSION

            @Suppress("unused")
            open val shouldPrintProgress: Boolean = true

            open val fullName
                get() = uppercaseFirstChar(name)

            open fun matchEnvironment() = systemProperties.platform?.startsWith(name) == true
                    || systemProperties.vendorName?.contains(vendor, true) == true

            fun ensureMinimalGradleJdkVersion() {
                val minVer = java.util.Properties().apply {
                    load(java.io.FileInputStream("$rootDir/version.properties"))
                }["JAVA_VERSION_MIN_SUPPORTED"].let { it as String }.toInt()

                if (JavaVersion.current().majorVersion.toInt() < minVer) {
                    Formatted(
                        "Current Gradle JDK version ${JavaVersion.current()} does not meet " +
                                "the minimum requirement which $minVer is needed",
                        mutableListOf<String>().apply {
                            gradleSettingsName?.let { add("Settings path: File | Settings | Build, Execution, Deployment | Build Tools | Gradle") }
                            add("Change \"${gradleSettingsName ?: "Gradle JDK"}\" to $minVer at the least")
                        }
                    ).throwException()
                }
            }

            fun prependConsoleInformation(consoleInfo: MutableList<String>) {
                val versionSuffix = when (this.version.isNotEmpty() && this.version != consts.DEFAULT_VERSION) {
                    true -> " | ${this.version}"
                    else -> ""
                }
                consoleInfo.add(0, "Platform: ${this.fullName}$versionSuffix")
            }

            private fun uppercaseFirstChar(s: String) = when (s.isEmpty()) {
                true -> ""
                else -> s[0].uppercase() + s.substring(1)
            }

        }

    }

    val identifier = object {

        // @Hint by SuperMonster003 on Oct 18, 2024.
        //  ! Fallback is a temporary or alternative solution
        //  ! used to handle some specific situations where functionality is unavailable.
        //  ! Compatibility is a broader design principle which ensures new versions
        //  ! remain compatible and continue to support the operations and data of older versions.
        //  ! zh-CN:
        //  ! Fallback (回退) 是一种临时或替代的解决方法, 用于应对功能不可用的特定情况.
        //  ! Compatibility (兼容) 是一种更广泛的设计原则, 确保新版本能兼容并继续支持旧版本的操作和数据.
        val fallback = "fallback"

        val upgraded = "upgraded"
        val downgraded = "downgraded"
        val nearestLowerMatched = "nearest-lower-matched"

        val specified = "specified"
        val auto = "auto"

        val fallbackSuffix = " [$fallback]"
        val upgradedSuffix = " [$upgraded]"
        val downgradedSuffix = " [$downgraded]"
        val nearestLowerMatchedSuffix = " [$nearestLowerMatched]"
        val autoSpecifiedSuffix = " [$auto-$specified]"
        val userSpecifiedSuffix = " [user-$specified]"

    }

    val platform = config.platforms.determine()

    val console = object {

        var versionInfo = mutableListOf<String>()

        fun printConcernedSystemPropertiesIfNeeded() {
            if (config.isShowConcernedSystemProperties && !systemProperties.isConcernsAlreadyPrinted) {
                Formatted("Information for concerned system properties", systemProperties.concerns).print(true)
            }
        }

        fun printVersionsOfIdeAndGradlePlugins() {
            Formatted("Version information for IDE platform and Gradle plugins", versionInfo).print()
        }

    }

    platform.ensureMinimalGradleJdkVersion()
    platform.prependConsoleInformation(console.versionInfo)

    val notations = object {

        private val versions = object {

            val agp = object : Version(platform.agpVersionMap, platform.version, config.fallbackAgpVersion) {

                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val currentGradleVersion = gradle.gradleVersion.toGradleVersion()
                    val sorted = agpGradleCompatibility.sortedByDescending { it.second.toGradleVersion() }
                    var maxSupporedAgpVersionPrefix: String? = null
                    for (pair in sorted) {
                        maxSupporedAgpVersionPrefix = pair.first
                        if (currentGradleVersion >= pair.second.toGradleVersion()) {
                            break
                        }
                    }
                    val maxSupporedAgpVersion = maxSupporedAgpVersionPrefix?.let { getAgpReleasedVersion(it) }
                    return when {
                        maxSupporedAgpVersion == null -> bestMatchingValue
                        bestMatchingValue == null -> {
                            bestMatchingOperationHintSuffix = identifier.autoSpecifiedSuffix
                            maxSupporedAgpVersion
                        }
                        compareVersionStrings(bestMatchingValue, maxSupporedAgpVersion) > 0 -> {
                            bestMatchingOperationHintSuffix = identifier.downgradedSuffix
                            maxSupporedAgpVersion
                        }
                        else -> bestMatchingValue
                    }
                }

                private fun getAgpReleasedVersion(referenceAgpVersion: String): String? {
                    return agpReleases.find { it.startsWith(referenceAgpVersion) && !it.contains("-") }
                }

            }

            val kotlin = object : Version(platform.kotlinVersionMap, platform.version, config.fallbackKotlinVersion) {

                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val currentGradleVersion = gradle.gradleVersion.toGradleVersion()
                    val embeddedMin = embeddedKotlin
                        .filter { (gradleMin, _) -> currentGradleVersion >= gradleMin.toGradleVersion() }
                        .maxByOrNull { it.first.toGradleVersion() }
                        ?.second
                    return when {
                        embeddedMin == null -> bestMatchingValue
                        bestMatchingValue == null -> {
                            bestMatchingOperationHintSuffix = identifier.autoSpecifiedSuffix
                            embeddedMin
                        }
                        bestMatchingValue.toGradleVersion() < embeddedMin.toGradleVersion() -> {
                            bestMatchingOperationHintSuffix = identifier.upgradedSuffix
                            embeddedMin
                        }
                        bestMatchingValue.toGradleVersion() > embeddedMin.toGradleVersion() -> {
                            bestMatchingOperationHintSuffix = identifier.downgradedSuffix
                            embeddedMin
                        }
                        else -> bestMatchingValue
                    }
                }
            }

        }

        private val pluginVersionAutomatorMap: Map<String, (Map<String, String>) -> Map<String, String?>> = mapOf(
            "ksp" to { versionMap: Map<String, String> ->
                var suffix = ""

                val kotlinVersion = config.libs.filterIsInstance<Classpath>()
                    .find { it.id.contains("kotlin-gradle-plugin", true) }
                    ?.takeIf { !it.version.startsWith("${identifier.auto}:") }
                    ?.version?.let { kt ->
                        versionMap[kt]?.let { kt }
                    }
                    ?: versions.kotlin.bestMatchingValue?.also {
                        suffix += identifier.autoSpecifiedSuffix
                    }
                    ?: versions.kotlin.fallbackVersion?.let { kt ->
                        suffix += identifier.fallbackSuffix
                        versionMap[kt]?.let { kt }
                    }

                val kspVersion = kotlinVersion?.let { kt ->
                    val ver = Version(config.kspVersionMap, kt)
                    val key = ver.bestMatchingKey ?: return@let null
                    val value = ver.bestMatchingValue ?: return@let null
                    ver.bestMatchingOperationHintSuffix?.let { suffix += it }
                    "$key-$value"
                }

                mapOf("version" to kspVersion, "suffix" to suffix)
            },
        )

        val classpath = config.libs.filterIsInstance<Classpath>().map { lib ->
            var suffix = ""
            val version: String = when {
                lib.version.startsWith("${identifier.auto}:") -> {
                    val mapType = lib.version.substring("${identifier.auto}:".length)
                    val ver = when (mapType) {
                        "agp" -> versions.agp
                        "kotlin" -> versions.kotlin
                        else -> throw Exception("Unknown version ${lib.version} for classpath ${lib.id}")
                    }
                    ver.bestMatchingValue?.also {
                        ver.bestMatchingOperationHintSuffix?.let { s -> suffix += s }
                    } ?: ver.fallbackVersion?.also {
                        suffix += identifier.fallbackSuffix
                    } ?: consts.DEFAULT_VERSION
                }
                else -> lib.version.also {
                    suffix += identifier.userSpecifiedSuffix
                }
            }
            if (lib.id == "com.android.tools.build:gradle") {
                gradle.extra.set("javaVersionCoercedByGradle", getMaxSupportedJavaVersion(version))
                gradle.extra.set("javaVersionOverriddenByUser", overriddenJavaVersion)
            }
            "${lib.id}:$version".also { notation ->
                console.versionInfo += "Classpath: \"$notation\"$suffix"
            }
        }

        val plugins = config.libs.filterIsInstance<Plugin>().map { lib ->
            var suffix = ""
            val version: String = when {
                lib.version.startsWith("${identifier.auto}:") -> {
                    val automator = pluginVersionAutomatorMap[lib.version.substring("${identifier.auto}:".length)]!!
                    val result = automator.invoke(config.kspVersionMap)
                    result["suffix"]?.let { s -> suffix += s }
                    result["version"] ?: throw Exception("Unknown version for plugin ${lib.id}")
                }
                else -> {
                    suffix += identifier.userSpecifiedSuffix
                    lib.version
                }
            }
            console.versionInfo += "Plugin: \"${lib.id}:$version\"$suffix"
            mapOf("id" to lib.id, "version" to version, "isApply" to lib.isApply)
        }

        private open inner class Version(val map: Map<String, String>, platformVersion: String, val fallbackVersion: String? = null) {

            val bestMatchingKey: String? = findBestMatchingMapKey(platformVersion)

            var bestMatchingOperationHintSuffix: String? = identifier.nearestLowerMatchedSuffix.takeIf {
                bestMatchingKey != null && bestMatchingKey != platformVersion
            }

            val bestMatchingValue: String? = refinedBestMatchingValue(bestMatchingKey?.let { map[it] })

            open fun refinedBestMatchingValue(bestMatchingValue: String?) = bestMatchingValue

            fun findBestMatchingMapKey(platformVersion: String): String? {
                val (platformVersionNumbers, platformVersionSuffix) = toVersionParts(platformVersion)
                val sortedVersions = map.keys.filter { it != identifier.fallback }.sortedWith(::compareVersionStrings).reversed()
                for (version in sortedVersions) {
                    val (versionNumbers, versionSuffix) = toVersionParts(version)
                    val versionComparisonScore = compareVersionParts(versionNumbers, platformVersionNumbers)
                    if (versionComparisonScore < 0) {
                        return version
                    }
                    if (versionComparisonScore == 0 && compareSuffix(versionSuffix, platformVersionSuffix) <= 0) {
                        return version
                    }
                }
                return null
            }

            fun compareVersionStrings(v1: String, v2: String): Int {
                val (ver1Numbers, ver1Suffix) = toVersionParts(v1)
                val (ver2Numbers, ver2Suffix) = toVersionParts(v2)
                return compareVersionParts(ver1Numbers, ver2Numbers)
                    .takeIf { it != 0 }
                    ?: compareSuffix(ver1Suffix, ver2Suffix)
            }

            private fun compareVersionParts(parts1: List<Int>, parts2: List<Int>): Int {
                for (i in 0 until maxOf(parts1.size, parts2.size)) {
                    val part1 = parts1.getOrElse(i) { 0 }
                    val part2 = parts2.getOrElse(i) { 0 }
                    if (part1 != part2) return part1.compareTo(part2)
                }
                return 0
            }

            private fun compareSuffix(suffix1: Pair<String, Int>, suffix2: Pair<String, Int>): Int {
                val suffixPriority = mapOf("" to 10, "Alpha" to 1, "Beta" to 2, "RC" to 5)
                val (suffixName1, suffixNumber1) = suffix1
                val (suffixName2, suffixNumber2) = suffix2
                val priority1 = suffixPriority[suffixName1] ?: Int.MAX_VALUE
                val priority2 = suffixPriority[suffixName2] ?: Int.MAX_VALUE
                return priority1.compareTo(priority2).takeIf { it != 0 } ?: suffixNumber1.compareTo(suffixNumber2)
            }

            private fun toVersionParts(version: String): Pair<List<Int>, Pair<String, Int>> {
                val parts = version.split(Regex("[+-]"))
                val numberParts = parts[0].split('.').map {
                    it.toIntOrNull() ?: throw IllegalArgumentException("Invalid version part: '$it' in version: '$version'")
                }

                val suffixPattern = Regex("([A-Za-z]+)(\\d*)|([A-Za-z]*)(\\d+)")
                val suffixMatch = suffixPattern.matchEntire(parts.getOrElse(1) { "" }) ?: return numberParts to ("" to 0)

                val suffixName = suffixMatch.groupValues[1] // "Alpha", "Beta", "RC" or empty string
                val suffixNumber = suffixMatch.groupValues[2].toIntOrNull() ?: 1 // Default to 1 for suffixes like "Alpha", "Beta", "RC"

                return numberParts to (suffixName to suffixNumber)
            }

        }

        private fun String.toGradleVersion() = GradleVersion.version(this)

        private fun getMaxSupportedJavaVersion(gradleVersion: String): Int {

            fun parseVersion(version: String) = version.split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }

            val inputGradleVersionInts = parseVersion(gradleVersion)
            val sortedJavaGradleCompatibility = javaGradleCompatibility.sortedBy { it.first }

            var maxJavaVersion: Int = sortedJavaGradleCompatibility.first().first

            for ((presetJavaVersion, presetGradleVersion) in sortedJavaGradleCompatibility) {
                val presetGradleVersionInts: List<Int> = parseVersion(presetGradleVersion)

                for (i in presetGradleVersionInts.indices) {
                    when {
                        i > inputGradleVersionInts.lastIndex -> {
                            break
                        }
                        inputGradleVersionInts[i] > presetGradleVersionInts[i] -> {
                            maxJavaVersion = presetJavaVersion
                            break
                        }
                        inputGradleVersionInts[i] < presetGradleVersionInts[i] -> {
                            break
                        }
                        i == presetGradleVersionInts.lastIndex -> {
                            maxJavaVersion = presetJavaVersion
                        }
                    }
                }
            }

            return maxJavaVersion
        }

    }

    buildscript {
        repositories {
            mavenCentral()
            google()
        }
        dependencies /* Android/Kotlin Gradle Plugin. */  {
            notations.classpath.forEach { classpath(it) }
        }
        dependencies /* Apache Compress for utils.build.gradle module. */ {
            classpath("org.apache.commons:commons-compress:1.27.1")
            classpath("org.tukaani:xz:1.9")
        }
    }

    plugins {
        notations.plugins.forEach {
            id(it["id"] as String) version it["version"] as String apply it["isApply"] as Boolean
        }
    }

    gradle.taskGraph.whenReady {
        if (allTasks.none { it.name == "clean" }) {
            console.printConcernedSystemPropertiesIfNeeded()
            console.printVersionsOfIdeAndGradlePlugins()
        }
    }

    gradle.extra.apply {
        set("isCleanupPaddleOcr", config.isCleanupPaddleOcr)
        set("isCleanupRapidOcr", config.isCleanupRapidOcr)
    }

    gradle.beforeProject {
        extensions.extraProperties["kotlinVersion"] = notations.classpath.find {
            it.contains("kotlin-gradle-plugin")
        }?.substringAfterLast(":") ?: config.fallbackKotlinVersion
        extensions.extraProperties["platform"] = platform
    }

}
