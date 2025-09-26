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
    "imagequant",

    "jackpal.androidterm-1.0.70",
    "jackpal.androidterm.emulatorview-1.0.42",
    "jackpal.androidterm.libtermexec-1.0",

    "android-spackle-9.0.0",
    "android-assertion-9.0.0",
    "android-plugin-client-sdk-for-locale-9.0.0",

    "markwon-core-4.6.2",
    "markwon-syntax-highlight-4.6.2",

    "root-shell-1.6",
    "expandable-layout-1.6.0",
    "recyclerview-flexibledivider-1.4.0"
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

    val versionProperties = java.util.Properties().apply {
        rootDir.resolve("version.properties").inputStream().use { load(it) }
    }

    // @AnchorBegin GRADLE_KOTLIN_COMPATIBILITY_LIST
    // @Script /.utils/scrape-and-inject-gradle-kotlin-compatibility-list.mjs
    // @Signature Pair<Gradle Version, Max Supported Kotlin Version>
    // @Reference https://docs.gradle.org/current/userguide/compatibility.html#kotlin
    // @Updated by SuperMonster003 on Sep 18, 2025.
    val gradleKotlinCompatibility = listOf(
        "9.0.0" to "2.2.0",
        "8.12" to "2.0.21",
        "8.11" to "2.0.20",
        "8.10" to "1.9.24",
        "8.9" to "1.9.23",
        "8.7" to "1.9.22",
        "8.5" to "1.9.20",
        "8.4" to "1.9.10",
        "8.3" to "1.9.0",
        "8.2" to "1.8.20",
    )
    // @AnchorEnd GRADLE_KOTLIN_COMPATIBILITY_LIST

    // @AnchorBegin JAVA_GRADLE_COMPATIBILITY_LIST
    // @Script /.utils/scrape-java-gradle-compatibility-map.mjs
    // @Signature Pair<Supported Java Version, Min Required Gradle Version>
    // @Reference https://docs.gradle.org/current/userguide/compatibility.html#java_runtime
    // @Updated by SuperMonster003 on Sep 18, 2025.
    val javaGradleCompatibility = listOf(
        26 to "N/A",
        25 to "9.1",
        24 to "8.14",
        23 to "8.10",
        22 to "8.8",
        21 to "8.5",
        20 to "8.3",
        19 to "7.6",
        18 to "7.5",
        17 to "7.3",
    )
    // @AnchorEnd JAVA_GRADLE_COMPATIBILITY_LIST

    // @AnchorBegin AGP_GRADLE_COMPATIBILITY_LIST
    // @Script /.utils/scrape-and-inject-agp-gradle-compatibility-list.mjs
    // @Signature Pair<Android Gradle Plugin Version, Min Required Gradle Version>
    // @Reference https://developer.android.com/build/releases/gradle-plugin#updating-gradle
    // @Updated by SuperMonster003 on Sep 3, 2025.
    val agpGradleCompatibility = listOf(
        "8.13" to "8.13",
        "8.12" to "8.13",
        "8.11" to "8.13",
        "8.10" to "8.11.1",
        "8.9" to "8.11.1",
        "8.8" to "8.10.2",
        "8.7" to "8.9",
        "8.6" to "8.7",
        "8.5" to "8.7",
        "8.4" to "8.6",
        "8.3" to "8.4",
        "8.2" to "8.2",
    )
    // @AnchorEnd AGP_GRADLE_COMPATIBILITY_LIST

    // @AnchorBegin ANDROID_GRADLE_PLUGIN_RELEASES_LIST
    // @Script /.utils/scrape-and-inject-agp-releases.mjs
    // @Reference https://developer.android.com/reference/tools/gradle-api
    // @Updated by SuperMonster003 on Sep 26, 2025.
    val agpReleases = listOf(
        "9.0.0-alpha07",
        "8.13.0",
        "8.12.3",
        "8.11.2",
        "8.10.1",
        "8.9.3",
        "8.8.2",
        "8.7.3",
        "8.6.1",
        "8.5.2",
        "8.4.2",
        "8.3.2",
        "8.2.2",
    )
    // @AnchorEnd ANDROID_GRADLE_PLUGIN_RELEASES_LIST

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
    }

    var isConcernedPropertiesAlreadyPrinted = false

    val concernedProperties: List<String> by lazy {
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
        System.getProperties().apply {
            putAll(gradle.startParameter.projectProperties)
        }.filterKeys { key ->
            return@filterKeys key is String
                    && key !in unconcernedKeys
                    && key.split(Regex("\\W")).any { it in concernedKeyWords }
                    && key.split(Regex("\\W")).none { it in unconcernedKeyWords }
        }.map { (key, value) -> "[ $key: $value ]" }
    }

    data class Classpath(val id: String, val version: String)

    data class Plugin(val id: String, val version: String, val isApply: Boolean = false)

    class Formatted(title: String, private val contents: Collection<String> = emptyList(), subtitle: String? = null, footers: Collection<String> = emptyList()) {
        private val formattedOutput = run {
            val elements = mutableListOf<String>()
            subtitle?.let { elements.add(it) }
            elements.addAll(contents)
            val maxLength = elements.plus(title).plus(footers).maxOf { it.length }

            listOfNotNull(
                "=".repeat(maxLength),
                title,
                subtitle,
                "-".repeat(maxLength).takeUnless { contents.isEmpty() },
                *contents.toTypedArray(),
                "-".repeat(maxLength).takeUnless { footers.isEmpty() },
                *footers.toTypedArray(),
                "=".repeat(maxLength),
                "",
            )
        }

        fun print(contentsMatters: Boolean = false) = formattedOutput.forEach { if (!contentsMatters || contents.isNotEmpty()) println(it) }

        fun throwException(): Unit = throw Exception(formattedOutput.joinToString("\n"))
    }

    val utils = object {

        private val SUFFIX_PRIORITY: Map<String, Int> = mapOf(
            "canary" to 1, "nightly" to 1, "snapshot" to 1, "dev" to 1,
            "pre-alpha" to 2, "prealpha" to 2, "preview" to 2, "eap" to 2, "milestone" to 2,
            "alpha" to 3,
            "beta" to 4,
            "rc" to 5,
            "" to 10, "stable" to 10, "ga" to 10, "final" to 10, "release" to 10, "lts" to 10
        )

        private fun normalizeSuffixName(raw: String?): String {
            val n = (raw ?: "").trim().lowercase()
            return when (n) {
                "a" -> "alpha"
                "b" -> "beta"
                "cr" -> "rc"
                "m" -> "milestone"
                "pre" -> "preview"
                else -> n
            }
        }

        fun compareVersionStrings(v1: String, v2: String): Int {
            val (ver1Numbers, ver1Suffix) = toVersionParts(v1)
            val (ver2Numbers, ver2Suffix) = toVersionParts(v2)
            return compareVersionParts(ver1Numbers, ver2Numbers)
                .takeIf { it != 0 }
                ?: compareVersionSuffix(ver1Suffix, ver2Suffix)
        }

        fun compareVersionParts(parts1: List<Int>, parts2: List<Int>): Int {
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val part1 = parts1.getOrElse(i) { 0 }
                val part2 = parts2.getOrElse(i) { 0 }
                if (part1 != part2) return part1.compareTo(part2)
            }
            return 0
        }

        fun compareVersionSuffix(suffix1: Pair<String, Int>, suffix2: Pair<String, Int>): Int {
            val (name1Raw, num1) = suffix1
            val (name2Raw, num2) = suffix2
            val name1 = normalizeSuffixName(name1Raw)
            val name2 = normalizeSuffixName(name2Raw)

            val p1 = SUFFIX_PRIORITY[name1] ?: Int.MAX_VALUE
            val p2 = SUFFIX_PRIORITY[name2] ?: Int.MAX_VALUE

            val byPriority = p1.compareTo(p2)
            if (byPriority != 0) return byPriority
            return num1.compareTo(num2)
        }

        fun toVersionParts(version: String): Pair<List<Int>, Pair<String, Int>> {
            // e.g. "1.2.3-rc1" / "1.2.3 RC 1" / "1.2.3-Alpha" / "1.2.3.m2" / "1.2.3_preview-2".
            val split = version.split(Regex("[\\s+\\-]"), limit = 2)
            val numberStr = split[0]
            val numberParts = numberStr.split('.').map {
                it.toIntOrNull() ?: throw IllegalArgumentException("Invalid version part: '$it' in version: '$version'")
            }

            val suffixStr = split.getOrNull(1)?.trim().orEmpty()
            if (suffixStr.isEmpty()) return numberParts to ("" to 0)

            val regex = Regex("([A-Za-z]+)[\\s._-]*(\\d*)|([A-Za-z]*)[\\s._-]*(\\d+)", RegexOption.IGNORE_CASE)
            val m = regex.matchEntire(suffixStr) ?: return numberParts to ("" to 0)

            val rawName = (m.groups[1]?.value ?: m.groups[3]?.value).orEmpty()
            val rawNum = (m.groups[2]?.value ?: m.groups[4]?.value).orEmpty()

            val normName = normalizeSuffixName(rawName)
            val suffixNum = rawNum.toIntOrNull() ?: if (normName.isNotEmpty()) 1 else 0

            return numberParts to (normName to suffixNum)
        }

        fun parseAndroidStudioBuildToVersion(): String? {
            // e.g. "251.26094.121.2513.13991806".
            val build = providers.gradleProperty("android.studio.version")
                .orElse(providers.systemProperty("android.studio.version"))
                .orNull ?: return null

            val parts = build.split('.')
            val baseStr = parts.getOrNull(0) ?: return null
            val base = baseStr.toIntOrNull() ?: return null

            val year = 2000 + base / 10
            val minor = base % 10

            // Look for strings starting with base and having longer length in the remaining fragments.
            // e.g. "2513" means patch version is "3".
            // zh-CN:
            // 在其余片段里找以 base 开头且长度更长的字段.
            // 如 "2513" 意味着补丁版本为 "3".
            val patch = parts.drop(1).firstNotNullOfOrNull { seg ->
                if (seg.startsWith(baseStr) && seg.length > baseStr.length) {
                    seg.substring(baseStr.length).toIntOrNull()
                } else null
            }

            return if (patch != null && patch > 0) "$year.$minor.$patch" else "$year.$minor"
        }
    }

    val config = object {

        /* Hide concerned properties by `System.getProperties()` and `gradle.startParameter.projectProperties`. */
        val isHideConcernedProperties = false

        /* Hide hint suffix like `[auto-specified]`, `[nearest-lower-matched]`, etc. in console. */
        val isHideConsoleInfoHintSuffix = false

        val isCleanupPaddleOcr = false
        val isCleanupRapidOcr = false

        val fallbackAgpVersion = "8.2.2"
        val fallbackKotlinVersion = "1.8.10"

        @Suppress("unused")
        val platforms = object {

            val androidStudio = object : Platform(
                name = "AndroidStudio", vendor = "Google",
                // @AnchorBegin ANDROID_STUDIO_AGP_VERSION_MAP
                // @Script /.utils/scrape-android-studio-agp_version_maps.mjs
                // @Reference https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility
                // @Updated by SuperMonster003 on Sep 5, 2025.
                agpVersionMap = mapOf(
                    "2025.1.3" to "8.13",
                    "2025.1.2" to "8.12",
                    "2025.1.1" to "8.11",
                    "2024.3.2" to "8.10",
                    "2024.3.1" to "8.9",
                    "2024.2.2" to "8.8",
                    "2024.2.1" to "8.7",
                    "2024.1.2" to "8.6",
                    "2024.1.1" to "8.5",
                    "2023.3.1" to "8.4",
                ),
                // @AnchorEnd ANDROID_STUDIO_AGP_VERSION_MAP

                // @AnchorBegin ANDROID_STUDIO_CODENAME_VERSION_MAP
                // @Script /.utils/scrape-and-inject-android-studio-codename_maps.mjs
                // @Reference https://developer.android.com/studio/archive?hl=en
                // @Updated by SuperMonster003 on Sep 23, 2025.
                codenameVersionMap = mapOf(
                    "2025.2" to "O",
                    "2025.1" to "N",
                    "2024.3" to "M",
                    "2024.2" to "L",
                    "2024.1.3" to "L",
                    "2024.1.2" to "K",
                    "2024.1.1" to "K",
                    "2023.3.2" to "J|K",
                    "2023.3.1" to "J",
                    "2023.2" to "I",
                    "2023.1" to "H",
                    "2022.3" to "G",
                    "2022.2" to "F",
                    "2022.1" to "E",
                    "2021.3" to "D",
                    "2021.2" to "C",
                    "2021.1" to "B",
                    "2020.3" to "A",
                ),
                // @AnchorEnd ANDROID_STUDIO_CODENAME_VERSION_MAP

                // @AnchorBegin ANDROID_STUDIO_CODENAME_MAP
                // @Script /.utils/scrape-and-inject-android-studio-codename_maps.mjs
                // @Reference https://developer.android.com/studio/archive?hl=en
                // @Updated by SuperMonster003 on Sep 23, 2025.
                codenameMap = mapOf(
                    "O" to "Otter", /* Born on Sep 22, 2025. */
                    "N" to "Narwhal", /* Born on Mar 19, 2025. */
                    "M" to "Meerkat", /* Born on Nov 12, 2024. */
                    "L" to "Ladybug", /* Born on Jul 15, 2024. */
                    "K" to "Koala", /* Born on Mar 22, 2024. */
                    "J" to "Jellyfish", /* Born on Dec 28, 2023. */
                    "I" to "Iguana", /* Born on Aug 25, 2023. */
                    "H" to "Hedgehog", /* Born on Apr 25, 2023. */
                    "G" to "Giraffe", /* Born on Jan 17, 2023. */
                    "F" to "Flamingo", /* Born on Sep 20, 2022. */
                    "E" to "Electric Eel", /* Born on May 11, 2022. */
                    "D" to "Dolphin", /* Born on Jan 31, 2022. */
                    "C" to "Chipmunk", /* Born on Oct 13, 2021. */
                    "B" to "Bumblebee", /* Born on May 18, 2021. */
                    "A" to "Arctic Fox", /* Born on Jan 26, 2021. */
                ),
                // @AnchorEnd ANDROID_STUDIO_CODENAME_MAP
            ) {
                override val weight = Int.MAX_VALUE
                override val gradleSettingsName = "Gradle JDK"
                override val fullName by lazy {
                    val suffix = codenameVersionMap?.let { map ->
                        val letters = map[version]
                            ?: map[version.split(".").take(2).joinToString(".")]
                            ?: return@let null
                        letters
                            .split("|")
                            .joinToString(" / ", prefix = " ") { key ->
                                codenameMap?.get(key.trim()) ?: key
                            }
                    } ?: ""
                    return@lazy "Android Studio$suffix"
                }
                override val minSupportedVersion = versionProperties["MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION"] as String
            }

            val intelliJIdea = object : Platform(
                name = "IntelliJIdea", vendor = "Jetbrains",
                // @Reference AGP Upgrade Assistant integrated within JetBrains IntelliJ IDEA.
                // @Updated by SuperMonster003 on Aug 20, 2025. (Manual)
                agpVersionMap = mapOf(
                    "2025.2.2" to "8.12.0",
                    "2025.2.1" to "8.11.1",
                    "2025.1" to "8.10.1",
                    "2024.3" to "8.7.3",
                    "2024.2" to "8.5.2",
                    "2024.1" to "8.2.2",
                    "2023.3" to "8.2.2", /* Settings: Enable sync with future AGP version. */
                ),
            ) {
                override val weight = 10
                override val gradleSettingsName = "Gradle JVM"
                override val fullName = "IntelliJ IDEA"
                override val minSupportedVersion = versionProperties["MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION"] as String
            }

            val temurin = object : Platform(
                name = "Temurin", vendor = "temurin",
                /* More common as "Eclipse Adoptium". */
                // @Updated by SuperMonster003 on Apr 16, 2025. (Manual)
                agpVersionMap = mapOf(
                    "21.0.6+7" to "8.7.3", /* Apr 16, 2025. */
                    "20.0.2+9" to "8.2.2", /* Dec 2, 2024. */
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
                    concernedProperties,
                    "However, here are some props may be useful for determining platform info",
                ).print().also { isConcernedPropertiesAlreadyPrinted = true }
            }

            fun determine(): Platform {
                val candidates = this::class.java.declaredFields.mapNotNull { field ->
                    field.get(this)?.let { tmpPlatform ->
                        tmpPlatform::class.java.methods.find { method ->
                            method.name == Platform::matchEnvironment.name && method.invoke(tmpPlatform) == true
                        }?.let { tmpPlatform as? Platform }
                    }
                }

                fun parseVersion(platform: Platform) = systemProperties.version ?: when {
                    platform != unknown && systemProperties.platform != null -> {
                        systemProperties.platform.substring(platform.name.length)
                            .replace(Regex("^\\W*"), "")
                            .replace(Regex("^Preview", RegexOption.IGNORE_CASE), "")
                    }
                    else -> consts.DEFAULT_VERSION
                }

                return when {
                    candidates.isEmpty() -> when (val osName = System.getProperty("os.name")) {
                        is String -> unknown.also { it.name = osName }
                        else -> unknown.also { it.declare() }
                    }
                    candidates.size > 1 -> candidates.maxBy { it.weight }
                    else -> candidates.first()
                }.also {
                    when (it) {
                        androidStudio -> {
                            it.version = utils.parseAndroidStudioBuildToVersion() ?: parseVersion(it)
                        }
                        else -> {
                            it.version = parseVersion(it)
                        }
                    }
                }
            }

        }

        val libs = listOf(
            Classpath(id = "com.android.tools.build:gradle", version = overriddenAgpVersion ?: "auto:agp"),
            Classpath(id = "org.jetbrains.kotlin:kotlin-gradle-plugin", version = overriddenKotlinVersion ?: "auto:kotlin"),
            Classpath(id = "org.apache.commons:commons-compress", version = "toml:commons-compress"),
            Classpath(id = "org.tukaani:xz", version = "toml:xz"),
            Plugin(id = "com.google.devtools.ksp", version = overriddenKspVersion ?: "auto:ksp"),
            Plugin(id = "org.gradle.toolchains.foojay-resolver-convention", version = "toml:foojay-resolver-convention"),
        )

        // @AnchorBegin KSP_VERSION_MAP
        // @Script /.utils/scrape-and-inject-ksp-releases.mjs
        // @Reference https://github.com/google/ksp/releases
        // @Updated by SuperMonster003 on Sep 12, 2025.
        val kspVersionMap = mapOf(
            "2.2.20" to "2.0.3", /* Sep 12, 2025. */
            "2.2.20-RC2" to "2.0.2", /* Sep 4, 2025. */
            "2.2.20-RC" to "2.0.2", /* Aug 20, 2025. */
            "2.2.20-Beta2" to "2.0.2", /* Aug 1, 2025. */
            "2.2.20-Beta1" to "2.0.2", /* Jul 11, 2025. */
            "2.2.10" to "2.0.2", /* Aug 15, 2025. */
            "2.2.10-RC2" to "2.0.2", /* Aug 7, 2025. */
            "2.2.10-RC" to "2.0.2", /* Jul 25, 2025. */
            "2.2.0" to "2.0.2", /* Jun 25, 2025. */
            "2.2.0-RC3" to "2.0.2", /* Jun 17, 2025. */
            "2.2.0-RC2" to "2.0.2", /* Jun 11, 2025. */
            "2.2.0-RC" to "2.0.1", /* May 17, 2025. */
            "2.2.0-Beta2" to "2.0.1", /* May 2, 2025. */
            "2.2.0-Beta1" to "2.0.0", /* Apr 17, 2025. */
            "2.1.21" to "2.0.2", /* Jun 10, 2025. */
            "2.1.21-RC2" to "2.0.1", /* May 2, 2025. */
            "2.1.21-RC" to "2.0.0", /* Apr 18, 2025. */
            "2.1.20" to "2.0.1", /* May 1, 2025. */
            "2.1.20-RC3" to "1.0.31", /* Mar 15, 2025. */
            "2.1.20-RC2" to "1.0.31", /* Mar 7, 2025. */
            "2.1.20-RC" to "1.0.31", /* Feb 28, 2025. */
            "2.1.20-Beta2" to "1.0.30", /* Feb 15, 2025. */
            "2.1.20-Beta1" to "1.0.29", /* Dec 21, 2024. */
            "2.1.10" to "1.0.31", /* Feb 28, 2025. */
            "2.1.10-RC2" to "1.0.29", /* Jan 22, 2025. */
            "2.1.10-RC" to "1.0.29", /* Jan 10, 2025. */
            "2.1.0" to "1.0.29", /* Nov 28, 2024. */
            "2.1.0-RC2" to "1.0.28", /* Nov 20, 2024. */
            "2.1.0-RC" to "1.0.27", /* Nov 8, 2024. */
            "2.1.0-Beta2" to "1.0.26", /* Oct 26, 2024. */
            "2.1.0-Beta1" to "1.0.25", /* Sep 19, 2024. */
            "2.0.21" to "1.0.28", /* Nov 16, 2024. */
            "2.0.21-RC" to "1.0.25", /* Oct 2, 2024. */
            "2.0.20" to "1.0.25", /* Sep 6, 2024. */
            "2.0.20-RC2" to "1.0.24", /* Aug 14, 2024. */
            "2.0.20-RC" to "1.0.24", /* Jul 31, 2024. */
            "2.0.20-Beta2" to "1.0.23", /* Jul 12, 2024. */
            "2.0.20-Beta1" to "1.0.22", /* Jun 21, 2024. */
            "2.0.10" to "1.0.24", /* Aug 7, 2024. */
            "2.0.10-RC2" to "1.0.24", /* Jul 30, 2024. */
            "2.0.10-RC" to "1.0.23", /* Jul 12, 2024. */
            "2.0.0" to "1.0.24", /* Jul 30, 2024. */
            "2.0.0-RC3" to "1.0.20", /* May 14, 2024. */
            "2.0.0-RC2" to "1.0.20", /* Apr 30, 2024. */
            "2.0.0-RC1" to "1.0.20", /* Apr 11, 2024. */
            "2.0.0-Beta5" to "1.0.20", /* Apr 5, 2024. */
            "2.0.0-Beta4" to "1.0.19", /* Mar 8, 2024. */
            "2.0.0-Beta3" to "1.0.17", /* Jan 20, 2024. */
            "2.0.0-Beta2" to "1.0.16", /* Dec 20, 2023. */
            "2.0.0-Beta1" to "1.0.15", /* Nov 28, 2023. */
            "1.9.25" to "1.0.20", /* Jul 24, 2024. */
            "1.9.24" to "1.0.20", /* May 8, 2024. */
            "1.9.23" to "1.0.20", /* Apr 5, 2024. */
            "1.9.22" to "1.0.17", /* Jan 19, 2024. */
            "1.9.21" to "1.0.16", /* Dec 14, 2023. */
            "1.9.20" to "1.0.14", /* Nov 3, 2023. */
            "1.9.20-RC2" to "1.0.13", /* Oct 26, 2023. */
            "1.9.20-RC" to "1.0.13", /* Oct 12, 2023. */
            "1.9.20-Beta2" to "1.0.13", /* Sep 22, 2023. */
            "1.9.20-Beta" to "1.0.13", /* Sep 12, 2023. */
            "1.9.10" to "1.0.13", /* Aug 24, 2023. */
            "1.9.0" to "1.0.13", /* Aug 3, 2023. */
            "1.9.0-RC" to "1.0.11", /* Jun 22, 2023. */
            "1.9.0-Beta" to "1.0.11", /* May 24, 2023. */
            "1.8.22" to "1.0.11", /* Jun 9, 2023. */
            "1.8.21" to "1.0.11", /* Apr 27, 2023. */
            "1.8.20" to "1.0.11", /* Apr 18, 2023. */
        )
        // @AnchorEnd KSP_VERSION_MAP

        abstract inner class Platform(
            var name: String,
            val vendor: String,
            val agpVersionMap: Map<String, String> = emptyMap(),
            val codenameVersionMap: Map<String, String>? = null,
            val codenameMap: Map<String, String>? = null,
        ) {

            open val gradleSettingsName: String? = null
            open val weight: Int = -Int.MAX_VALUE
            open var version: String = consts.DEFAULT_VERSION
            open val minSupportedVersion: String = consts.DEFAULT_VERSION

            @Suppress("unused")
            open val shouldPrintProgress: Boolean = true

            open val fullName
                get() = uppercaseFirstChar(name)

            open fun matchEnvironment() = systemProperties.platform?.startsWith(name) == true
                    || systemProperties.vendorName?.contains(vendor, true) == true

            fun ensureMinimalGradleJdkVersion() {
                val minVer = versionProperties["JAVA_VERSION_MIN_SUPPORTED"].let { it as String }.toInt()

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

            fun ensureMinimalIdeVersion() {
                if (minSupportedVersion == consts.DEFAULT_VERSION) return
                if (utils.compareVersionStrings(version, minSupportedVersion) >= 0) return
                throw Exception("Current IDE (${this.fullName}) version $version does not meet the minimum requirement which $minSupportedVersion is needed")
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
        val toml = "toml"

        val fallbackSuffix = " [$fallback]"
        val upgradedSuffix = " [$upgraded]"
        val downgradedSuffix = " [$downgraded]"
        val nearestLowerMatchedSuffix = " [$nearestLowerMatched]"
        val autoSpecifiedSuffix = " [$auto-$specified]"
        val userSpecifiedSuffix = " [user-$specified]"
        val tomlSpecifiedSuffix = " [toml-$specified]"

    }

    val platform = config.platforms.determine()

    val console = object {

        var versionInfo = mutableListOf<String>()

        fun printConcernedPropertiesIfNeeded() {
            if (!config.isHideConcernedProperties && !isConcernedPropertiesAlreadyPrinted) {
                Formatted("Information for concerned properties", concernedProperties).print(true)
            }
        }

        fun printVersionsOfIdeAndGradlePlugins() {
            val footers = listOf("Gradle version: ${gradle.gradleVersion}")
            Formatted("Version information for IDE platform and Gradle plugins", versionInfo, footers = footers).print()
        }

    }

    platform.ensureMinimalIdeVersion()
    platform.ensureMinimalGradleJdkVersion()
    platform.prependConsoleInformation(console.versionInfo)

    val notations = object {

        private val versions = object {

            val agp = object : Version(platform.agpVersionMap, platform.version, config.fallbackAgpVersion) {

                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val currentGradleVersion = gradle.gradleVersion.toGradleVersion()
                    val sorted = agpGradleCompatibility.sortedByDescending { it.second.toGradleVersion() }
                    var maxSupportedAgpVersionPrefix: String? = null
                    for (pair in sorted) {
                        maxSupportedAgpVersionPrefix = pair.first
                        if (currentGradleVersion >= pair.second.toGradleVersion()) {
                            break
                        }
                    }
                    val maxSupporedAgpVersion = maxSupportedAgpVersionPrefix?.let { getAgpReleasedVersion(it) }
                    maxSupporedAgpVersion ?: return bestMatchingValue
                    bestMatchingValue ?: return maxSupporedAgpVersion.also {
                        bestMatchingOperationHintSuffix += identifier.autoSpecifiedSuffix
                    }
                    if (!bestMatchingValue.contains("\\d+\\.\\d+\\.\\d+".toRegex())) {
                        bestMatchingOperationHintSuffix += identifier.autoSpecifiedSuffix
                        return getAgpReleasedVersion(bestMatchingValue) ?: "$bestMatchingValue.0"
                    }
                    if (utils.compareVersionStrings(bestMatchingValue, maxSupporedAgpVersion) > 0) {
                        bestMatchingOperationHintSuffix += identifier.downgradedSuffix
                        return maxSupporedAgpVersion
                    }
                    return bestMatchingValue
                }

                private fun getAgpReleasedVersion(referenceAgpVersion: String): String? {
                    val sortedAgpReleases = agpReleases.sortByVersionName(isDescend = true)
                    return sortedAgpReleases.find { it.startsWith(referenceAgpVersion) && !it.contains("-") }
                }

            }

            val kotlin = object : Version(emptyMap(), platform.version, config.fallbackKotlinVersion) {

                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val currentGradleVersion = gradle.gradleVersion.toGradleVersion()
                    val kotlinMin = gradleKotlinCompatibility
                        .filter { (gradleMin, _) -> currentGradleVersion >= gradleMin.toGradleVersion() }
                        .maxByOrNull { it.first.toGradleVersion() }
                        ?.second
                    return when {
                        kotlinMin == null -> bestMatchingValue
                        bestMatchingValue == null -> {
                            bestMatchingOperationHintSuffix += identifier.autoSpecifiedSuffix
                            kotlinMin
                        }
                        bestMatchingValue.toGradleVersion() < kotlinMin.toGradleVersion() -> {
                            bestMatchingOperationHintSuffix += identifier.upgradedSuffix
                            kotlinMin
                        }
                        bestMatchingValue.toGradleVersion() > kotlinMin.toGradleVersion() -> {
                            bestMatchingOperationHintSuffix += identifier.downgradedSuffix
                            kotlinMin
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
                    suffix += ver.bestMatchingOperationHintSuffix
                    "$key-$value"
                }

                mapOf("version" to kspVersion, "suffix" to suffix)
            },
        )

        private val toml: Map<String, String> = run {
            val lines = file("gradle/libs.versions.toml").also {
                require(it.isFile) { "File $it doesn't exist" }
            }.readLines()
            val versions = mutableMapOf<String, String>()
            var inVersions = false
            val keyValuePattern = Regex("""^\s*([A-Za-z0-9._-]+)\s*=\s*"(.*?)"\s*(#.*)?$""")
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                if (line.startsWith("[")) {
                    inVersions = line == "[versions]"
                    continue
                }
                if (!inVersions) continue
                keyValuePattern.find(line)?.let { m ->
                    val (_, key, value) = m.groupValues
                    versions[key] = value
                }
            }
            return@run versions
        }

        val classpath = config.libs.filterIsInstance<Classpath>().map { lib ->
            var suffix = ""
            val version: String = when {
                lib.version.startsWith("${identifier.auto}:") -> {
                    val mapType = lib.version.removePrefix("${identifier.auto}:")
                    val ver = when (mapType) {
                        "agp" -> versions.agp
                        "kotlin" -> versions.kotlin
                        else -> throw Exception("Unknown version ${lib.version} for classpath ${lib.id}")
                    }
                    ver.bestMatchingValue?.also {
                        suffix += ver.bestMatchingOperationHintSuffix
                    } ?: ver.fallbackVersion?.also {
                        suffix += identifier.fallbackSuffix
                    } ?: consts.DEFAULT_VERSION
                }
                lib.version.startsWith("${identifier.toml}:") -> {
                    toml.getValue(lib.version.removePrefix("${identifier.toml}:")).also {
                        suffix += identifier.tomlSpecifiedSuffix
                    }
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
                console.versionInfo += "Classpath: \"$notation\"${if (config.isHideConsoleInfoHintSuffix) "" else suffix}"
            }
        }

        val plugins = config.libs.filterIsInstance<Plugin>().map { lib ->
            var suffix = ""
            val version: String = when {
                lib.version.startsWith("${identifier.auto}:") -> {
                    val automator = pluginVersionAutomatorMap.getValue(lib.version.removePrefix("${identifier.auto}:"))
                    val result = automator.invoke(config.kspVersionMap)
                    result["suffix"]?.let { s -> suffix += s }
                    result["version"] ?: throw Exception("Unknown version for plugin ${lib.id}")
                }
                lib.version.startsWith("${identifier.toml}:") -> {
                    toml.getValue(lib.version.removePrefix("${identifier.toml}:")).also {
                        suffix += identifier.tomlSpecifiedSuffix
                    }
                }
                else -> {
                    suffix += identifier.userSpecifiedSuffix
                    lib.version
                }
            }
            console.versionInfo += "Plugin: \"${lib.id}:$version\"${if (config.isHideConsoleInfoHintSuffix) "" else suffix}"
            mapOf("id" to lib.id, "version" to version, "isApply" to lib.isApply)
        }

        private open inner class Version(val map: Map<String, String>, platformVersion: String, val fallbackVersion: String? = null) {

            val bestMatchingKey: String? = findBestMatchingMapKey(platformVersion)

            var bestMatchingOperationHintSuffix: String = when (bestMatchingKey != null && bestMatchingKey != platformVersion) {
                true -> identifier.nearestLowerMatchedSuffix
                else -> ""
            }

            val bestMatchingValue: String? = refinedBestMatchingValue(bestMatchingKey?.let { map[it] })

            open fun refinedBestMatchingValue(bestMatchingValue: String?) = bestMatchingValue

            fun findBestMatchingMapKey(platformVersion: String): String? {
                val (platformVersionNumbers, platformVersionSuffix) = utils.toVersionParts(platformVersion)
                val sortedVersions = map.keys.filter { it != identifier.fallback }.sortedWith(utils::compareVersionStrings).reversed()
                for (version in sortedVersions) {
                    val (versionNumbers, versionSuffix) = utils.toVersionParts(version)
                    val versionComparisonScore = utils.compareVersionParts(versionNumbers, platformVersionNumbers)
                    if (versionComparisonScore < 0) {
                        return version
                    }
                    if (versionComparisonScore == 0 && utils.compareVersionSuffix(versionSuffix, platformVersionSuffix) <= 0) {
                        return version
                    }
                }
                return null
            }

            fun List<String>.sortByVersionName(isDescend: Boolean = false): List<String> {
                return sortedWith { v1, v2 -> utils.compareVersionStrings(v1, v2) * if (isDescend) -1 else 1 }
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

    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    buildscript {
        repositories {
            mavenCentral()
            google()
        }
        dependencies {
            notations.classpath.forEach { classpath(it) }
        }
    }

    plugins {
        notations.plugins.forEach {
            id(it["id"] as String) version it["version"] as String apply it["isApply"] as Boolean
        }
    }

    gradle.taskGraph.whenReady {
        if (allTasks.none { it.name == "clean" }) {
            console.printConcernedPropertiesIfNeeded()
            console.printVersionsOfIdeAndGradlePlugins()
        }
    }

    gradle.extra.apply {
        set("platform", platform)
        set("isCleanupPaddleOcr", config.isCleanupPaddleOcr)
        set("isCleanupRapidOcr", config.isCleanupRapidOcr)
        set("isHideConsoleInfoHintSuffix", config.isHideConsoleInfoHintSuffix)
    }

}

plugins {
    // @Hint by SuperMonster003 on Sep 14, 2025.
    //  ! Enable JDK auto-resolution/download capability for build modules.
    //  ! zh-CN: 让构建模块具备 JDK 自动解析/下载能力.
    id("org.gradle.toolchains.foojay-resolver-convention")
}
