@file:Suppress("SpellCheckingInspection")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "AutoJs6"

includeBuild("build-logic")

private val modules = listOf(
    "jieba-analysis",
    "apk-signer",
    "apk-parser",
    "color-picker",
    "material-dialogs",
    "material-date-time-picker",
    "expandable-layout",
    "expandable-recyclerview",
    "recyclerview-flexibledivider",
)

private val libs = listOf(
    "android-job-simplified-1_4_3",
    "androidx-appcompat-1_0_2",
    "apk-parser-1_0_2",
    "org-opencv-4_8_0",
    "rapidocr",
    "imagequant",

    "jackpal-androidterm-1_0_70",
    "jackpal-androidterm-emulatorview-1_0_42",
    "jackpal-androidterm-libtermexec-1_0",

    "android-spackle-9_0_0",
    "android-assertion-9_0_0",
    "android-plugin-client-sdk-for-locale-9_0_0",

    "markwon-core-4_6_2",
    "markwon-syntax-highlight-4_6_2",

    "root-shell-1_6",
)

private val pluginApi = listOf(
    "paddle-ocr-api",
    "paddle-ocr-engine",
)

include(
    ":app",
    *modules.map { ":modules:$it" }.toTypedArray(),
    *libs.map { ":libs:$it" }.toTypedArray(),
    *pluginApi.map { ":plugin-api:$it" }.toTypedArray(),
)

modules.forEach {
    project(":modules:$it").projectDir = File("modules", it)
}

pluginApi.forEach {
    project(":plugin-api:$it").projectDir = File("plugin-api", it)
}

pluginManagement {

    @Suppress("UNCHECKED_CAST")
    fun rootDirProps(name: String) = java.util.Properties().apply {
        rootDir.resolve("$name.properties").inputStream().use { load(it) }
    } as Map<String, String>

    fun rootDirList(name: String) = rootDir.resolve("$name.list").inputStream().use {
        it.bufferedReader().useLines { lines ->
            lines.filter { line ->
                line.isNotBlank() && !line.startsWith("#")
            }.toList()
        }
    }

    fun gradleDataProps(name: String) = rootDirProps("gradle/data/$name")
    fun gradleDataList(name: String) = rootDirList("gradle/data/$name")

    val versionProps by lazy { rootDirProps("version") }

    // @Hint by SuperMonster003 on Oct 2, 2025.
    //  ! Change the overridden value in version.properties to specify a version (instead of auto-selection), e.g. 23 or "8.9.3".
    //  ! zh-CN: 如需指定版本 (而非自动选择版本), 可修改 version.properties 相应的 overridden 值, 如 23 或 "8.9.3".
    //  !
    //  ! Gradle JVM [ e.g. 23 ]
    val overriddenJavaVersion: Int? = versionProps["OVERRIDDEN_JAVA_VERSION"]?.toIntOrNull()
    //  ! classpath("com.android.tools.build:gradle") [ e.g. "8.9.3", "8.12.0" ]
    val overriddenAgpVersion: String? = versionProps["OVERRIDDEN_ANDROID_GRADLE_PLUGIN_VERSION"]?.takeUnless { it.isBlank() || it == "NONE" }
    //  ! classpath("org.jetbrains.kotlin:kotlin-gradle-plugin") [ e.g. "1.9.25", "2.0.21" ]
    val overriddenKotlinVersion: String? = versionProps["OVERRIDDEN_KOTLIN_GRADLE_PLUGIN_VERSION"]?.takeUnless { it.isBlank() || it == "NONE" }
    //  ! plugin("com.google.devtools.ksp") [ e.g. "1.9.25-1.0.20", "2.0.21-1.0.28" ]
    val overriddenKspVersion: String? = versionProps["OVERRIDDEN_KSP_GRADLE_PLUGIN_VERSION"]?.takeUnless { it.isBlank() || it == "NONE" }

    val agpReleases by lazy { gradleDataList("agp-releases") }

    val gradleKotlinCompatProps by lazy { gradleDataProps("gradle-kotlin-compat") }
    val javaGradleCompatProps by lazy { gradleDataProps("java-gradle-compat") }
    val agpGradleCompatProps by lazy { gradleDataProps("agp-gradle-compat") }
    val kspReleaseProps by lazy { gradleDataProps("ksp-releases") }

    val androidStudioAgpCompatProps by lazy { gradleDataProps("android-studio-agp-compat") }
    val androidStudioBuildVersionProps by lazy { gradleDataProps("android-studio-build-version") }
    val androidStudioCodenameVersionProps by lazy { gradleDataProps("android-studio-codename-version") }
    val androidStudioCodenameProps by lazy { gradleDataProps("android-studio-codename") }

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
                    && key.startsWith("gradle.").not()
                    && key !in unconcernedKeys
                    && key.split(Regex("\\W")).any { it in concernedKeyWords }
                    && key.split(Regex("\\W")).none { it in unconcernedKeyWords }
        }.map { (key, value) -> "[ $key: $value ]" }
    }

    data class Classpath(val id: String, val version: String)

    data class Plugin(val id: String, val version: String, val isApply: Boolean = false, val share: Boolean = false)

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

        private val suffixPriorityMap = mapOf(
            "canary" to 1, "nightly" to 1, "snapshot" to 1, "dev" to 1,
            "pre-alpha" to 2, "prealpha" to 2, "preview" to 2, "eap" to 2, "milestone" to 2,
            "alpha" to 3,
            "beta" to 4,
            "rc" to 5,
            "" to 10, "stable" to 10, "ga" to 10, "final" to 10, "release" to 10, "lts" to 10,
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

        fun compareVersionStringsDesc(v1: String, v2: String): Int = compareVersionStrings(v2, v1)

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

            val p1 = suffixPriorityMap[name1] ?: Int.MAX_VALUE
            val p2 = suffixPriorityMap[name2] ?: Int.MAX_VALUE

            val byPriority = p1.compareTo(p2)
            if (byPriority != 0) return byPriority
            return num1.compareTo(num2)
        }

        fun toVersionParts(version: String): Pair<List<Int>, Pair<String, Int>> {
            // e.g. "1.2.3-rc1" / "1.2.3 RC 1" / "1.2.3-Alpha" / "1.2.3.m2" / "1.2.3_preview-2".
            val split = version.split(Regex("[\\s+\\-]"), limit = 2)
            val numberStr = split[0]
            val numberParts = numberStr.split('.').map {
                when {
                    it.matches(Regex("[xyz*?]", RegexOption.IGNORE_CASE)) -> 0
                    else -> it.toIntOrNull()
                } ?: throw IllegalArgumentException("Invalid version part: '$it' in version: '$version'")
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

            androidStudioBuildVersionProps[build]?.let { return it }

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

        @Suppress("unused")
        val platforms = object {

            val androidStudio = object : Platform(
                name = "AndroidStudio", vendor = "Google",
                agpVersionMap = androidStudioAgpCompatProps,
            ) {
                override val weight = Int.MAX_VALUE
                override val gradleSettingsName = "Gradle JDK"
                override val fullName by lazy {
                    val suffix = androidStudioCodenameVersionProps.let { prop ->
                        val letters = prop[version]
                            ?: prop[version.split(".").take(3).joinToString(".")]
                            ?: prop[version.split(".").take(2).joinToString(".")]
                            ?: return@let null
                        letters
                            .split("|")
                            .joinToString(" / ", prefix = " ") { key ->
                                androidStudioCodenameProps[key.trim()] ?: key
                            }
                    } ?: ""
                    return@lazy "Android Studio$suffix"
                }
                override val minSupportedVersion = versionProps["MIN_SUPPORTED_ANDROID_STUDIO_IDE_VERSION"] as String
            }

            val intelliJIdea = object : Platform(
                name = "IntelliJIdea", vendor = "Jetbrains",
                // @Reference AGP Upgrade Assistant integrated within JetBrains IntelliJ IDEA.
                // @Updated by SuperMonster003 on Mar 2, 2026. (Manual)
                agpVersionMap = mapOf(
                    "2026.1" to "8.13.2",
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
                override val minSupportedVersion = versionProps["MIN_SUPPORTED_INTELLIJ_IDEA_IDE_VERSION"] as String
            }

            val temurin = object : Platform(
                name = "Temurin", vendor = "temurin",
                /* More common as "Eclipse Adoptium". */
                // @Updated by SuperMonster003 on Mar 15, 2026. (Manual)
                agpVersionMap = mapOf(
                    "21.0.10+7" to "8.9.3", /* Mar 15, 2026. */
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
            Plugin(id = "com.google.devtools.ksp", version = overriddenKspVersion ?: "auto:ksp", share = true),
            Plugin(id = "org.gradle.toolchains.foojay-resolver-convention", version = "toml:foojay-resolver-convention", share = true),
        )

        abstract inner class Platform(
            var name: String,
            val vendor: String,
            val agpVersionMap: Map<String, String> = emptyMap(),
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
                val javaVersionMinSupported = versionProps["JAVA_VERSION_MIN_SUPPORTED"].let { it as String }.toInt()

                if (JavaVersion.current().majorVersion.toInt() < javaVersionMinSupported) {
                    Formatted(
                        "Current Gradle JDK version ${JavaVersion.current()} does not meet " +
                                "the minimum requirement which $javaVersionMinSupported is needed",
                        mutableListOf<String>().apply {
                            gradleSettingsName?.let { add("Settings path: File | Settings | Build, Execution, Deployment | Build Tools | Gradle") }
                            add("Change \"${gradleSettingsName ?: "Gradle JDK"}\" to $javaVersionMinSupported at the least")
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
        val upgraded = "upgraded"
        val downgraded = "downgraded"
        val nearestLowerMatched = "nearest-lower-matched"
        val fallback = "fallback"

        val specified = "specified"
        val auto = "auto"
        val toml = "toml"

        val upgradedSuffix = " [$upgraded]"
        val downgradedSuffix = " [$downgraded]"
        val nearestLowerMatchedSuffix = " [$nearestLowerMatched]"
        val fallbackSuffix = " [$fallback]"
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

            val agp = object : Version(platform.agpVersionMap, platform.version) {

                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val currentGradleVersion = gradle.gradleVersion.toGradleVersion()
                    val entries = agpGradleCompatProps.entries.sortedByDescending { it.value.toGradleVersion() }
                    var maxSupportedAgpVersionPrefix: String? = null
                    for ((agp, minRequiredGradle) in entries) {
                        maxSupportedAgpVersionPrefix = agp
                        if (currentGradleVersion >= minRequiredGradle.toGradleVersion()) {
                            break
                        }
                    }
                    val maxSupportedAgpVersion = maxSupportedAgpVersionPrefix?.let { getAgpReleasedVersion(it) }
                    maxSupportedAgpVersion ?: return bestMatchingValue
                    bestMatchingValue ?: return maxSupportedAgpVersion.also {
                        bestMatchingOperationHintSuffix += identifier.autoSpecifiedSuffix
                    }
                    if (!bestMatchingValue.contains("\\d+\\.\\d+\\.\\d+".toRegex())) {
                        bestMatchingOperationHintSuffix += identifier.autoSpecifiedSuffix
                        return getAgpReleasedVersion(bestMatchingValue) ?: "$bestMatchingValue.0"
                    }
                    if (utils.compareVersionStrings(bestMatchingValue, maxSupportedAgpVersion) > 0) {
                        bestMatchingOperationHintSuffix += identifier.downgradedSuffix
                        return maxSupportedAgpVersion
                    }
                    return bestMatchingValue
                }

                private fun getAgpReleasedVersion(referenceAgpVersion: String): String? {
                    val sortedAgpReleases = agpReleases.sortByVersionName(isDescend = true)
                    return sortedAgpReleases.find { it.startsWith(referenceAgpVersion) && !it.contains("-") }
                }

            }

            val kotlin = object : Version(
                gradleKotlinCompatProps.filter { (gradleMin, _) ->
                    gradle.gradleVersion.toGradleVersion() >= gradleMin.toGradleVersion()
                }.toSortedMap(utils::compareVersionStringsDesc),
                platform.version,
            ) {
                override fun refinedBestMatchingValue(bestMatchingValue: String?): String? {
                    val kotlinMin = map.maxByOrNull { it.key.toGradleVersion() }?.value
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

                val kotlinVersion = run {
                    val kotlinClasspath = config.libs.filterIsInstance<Classpath>().find {
                        it.id.contains("kotlin-gradle-plugin", true)
                    } ?: throw Exception("Failed to find classpath for Kotlin Gradle Plugin")

                    if (!kotlinClasspath.version.startsWith("${identifier.auto}:")) {
                        if (kotlinClasspath.version in versionMap) {
                            return@run kotlinClasspath.version
                        }
                        throw Exception("Failed to find Kotlin Gradle Plugin version for \"${kotlinClasspath.version}\"")
                    }
                    versions.kotlin.bestMatchingValue?.let {
                        suffix += identifier.autoSpecifiedSuffix
                        return@run it
                    }
                    versionMap.keys.minWithOrNull(utils::compareVersionStrings)?.let {
                        suffix += identifier.fallbackSuffix
                        return@run it
                    } ?: throw Exception("Failed to determine Kotlin Gradle Plugin version for \"${kotlinClasspath.version}\"")
                }

                val kspVersion = run {
                    val ver = Version(versionMap, kotlinVersion)
                    val key = ver.bestMatchingKey ?: return@run null
                    val value = ver.bestMatchingValue ?: return@run null
                    suffix += ver.bestMatchingOperationHintSuffix
                    when {
                        key.contains(Regex("[xyz*?]", RegexOption.IGNORE_CASE)) -> value
                        else -> "$key-$value"
                    }
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
                    } ?: ver.map.values.minWithOrNull(utils::compareVersionStrings)?.also {
                        suffix += identifier.fallbackSuffix
                    } ?: throw Exception("Failed to determine version for classpath \"${lib.id}\"")
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
                System.setProperty("gradle.java.version.coerced.by.gradle", "${getMaxSupportedJavaVersion(version)}")
                System.setProperty("gradle.java.version.overridden.by.user", "$overriddenJavaVersion")
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
                    val result = automator.invoke(kspReleaseProps)
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
            if (lib.share) {
                System.setProperty(lib.id, version)
            }
            console.versionInfo += "Plugin: \"${lib.id}:$version\"${if (config.isHideConsoleInfoHintSuffix) "" else suffix}"
            mapOf("id" to lib.id, "version" to version, "isApply" to lib.isApply)
        }

        private open inner class Version(val map: Map<String, String>, platformVersion: String) {

            val bestMatchingKey: String? = findBestMatchingMapKey(platformVersion)

            var bestMatchingOperationHintSuffix: String = when (bestMatchingKey != null && bestMatchingKey != platformVersion) {
                true -> identifier.nearestLowerMatchedSuffix
                else -> ""
            }

            val bestMatchingValue: String? = refinedBestMatchingValue(bestMatchingKey?.let { map[it] })

            open fun refinedBestMatchingValue(bestMatchingValue: String?) = bestMatchingValue

            fun findBestMatchingMapKey(platformVersion: String): String? {
                val (platformVersionNumbers, platformVersionSuffix) = utils.toVersionParts(platformVersion)
                val sortedVersions = map.keys.sortedWith(utils::compareVersionStrings).reversed()
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
            val sortedJavaGradleCompatibility = javaGradleCompatProps.entries.map {
                it.key.toInt() to it.value
            }.sortedBy { it.first }

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
