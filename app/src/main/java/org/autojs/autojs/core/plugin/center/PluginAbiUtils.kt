package org.autojs.autojs.core.plugin.center

import android.os.Build
import org.autojs.autojs.runtime.api.augment.sysprops.SystemProperties
import org.autojs.autojs.util.AndroidUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 27, 2026.
 */
object PluginAbiUtils {

    const val ABI_UNIVERSAL = "universal"
    const val ABI_RISCV64 = "riscv64"

    private val ABI_ORDER = listOf(
        AndroidUtils.Abi.ARM64_V8A,
        AndroidUtils.Abi.ARMEABI_V7A,
        AndroidUtils.Abi.X86_64,
        AndroidUtils.Abi.X86,
        AndroidUtils.Abi.ARMEABI,
        AndroidUtils.Abi.MIPS64,
        AndroidUtils.Abi.MIPS,
        ABI_RISCV64,
        ABI_UNIVERSAL,
    )

    private val ABI_ORDER_INDEX = ABI_ORDER.withIndex().associate { it.value to it.index }

    private val ABI_TEXT_PATTERNS = listOf(
        AndroidUtils.Abi.ARM64_V8A to Regex("""(?i)(arm64[-_]?v8a|aarch64)"""),
        AndroidUtils.Abi.ARMEABI_V7A to Regex("""(?i)(armeabi[-_]?v7a|armv7a?)"""),
        AndroidUtils.Abi.X86_64 to Regex("""(?i)(x86[-_]?64|amd64)"""),
        AndroidUtils.Abi.X86 to Regex("""(?i)(?<![a-z0-9])x86(?![-_]?64)"""),
        AndroidUtils.Abi.ARMEABI to Regex("""(?i)(armeabi(?![-_]?v7a))"""),
        AndroidUtils.Abi.MIPS64 to Regex("""(?i)(mips64)"""),
        AndroidUtils.Abi.MIPS to Regex("""(?i)(?<![a-z0-9])mips(?!64)"""),
        ABI_RISCV64 to Regex("""(?i)(riscv[-_]?64)"""),
        ABI_UNIVERSAL to Regex("""(?i)(universal|noarch|all[-_]?abi(?:s)?|all[-_]?arch(?:es)?)"""),
    )

    fun normalizeAbi(input: String?): String? {
        val token = input
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace('_', '-')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when {
            token == AndroidUtils.Abi.ARM64_V8A ||
                    token == "arm64" ||
                    token.contains("arm64-v8a") ||
                    token == "aarch64" -> AndroidUtils.Abi.ARM64_V8A

            token == AndroidUtils.Abi.ARMEABI_V7A ||
                    token == "armv7" ||
                    token == "armv7a" ||
                    token.contains("armeabi-v7a") -> AndroidUtils.Abi.ARMEABI_V7A

            token == AndroidUtils.Abi.X86_64 ||
                    token == "x86-64" ||
                    token == "amd64" ||
                    token == "x64" ||
                    token.contains("x86-64") -> AndroidUtils.Abi.X86_64

            token == AndroidUtils.Abi.X86 ||
                    token == "i686" ||
                    token == "i386" -> AndroidUtils.Abi.X86

            token == AndroidUtils.Abi.ARMEABI -> AndroidUtils.Abi.ARMEABI
            token == AndroidUtils.Abi.MIPS64 -> AndroidUtils.Abi.MIPS64
            token == AndroidUtils.Abi.MIPS -> AndroidUtils.Abi.MIPS
            token == ABI_RISCV64 || token == "risc-v64" || token.contains("riscv64") -> ABI_RISCV64

            isUniversalToken(token) -> ABI_UNIVERSAL

            else -> null
        }
    }

    fun normalizeAbiList(values: Collection<String>?): List<String> {
        if (values.isNullOrEmpty()) return emptyList()
        val normalized = values.mapNotNull(::normalizeAbi).toSet()
        return sortAbis(normalized)
    }

    fun parseSupportedAbisFromText(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val found = linkedSetOf<String>()
        ABI_TEXT_PATTERNS.forEach { (abi, pattern) ->
            if (pattern.containsMatchIn(text)) {
                found += abi
            }
        }

        text.split(
            ',', ';', '|', '/', '\\', ' ', '\n', '\r', '\t', '[', ']', '{', '}', '(', ')',
        ).forEach { token ->
            normalizeAbi(token)?.let(found::add)
        }

        return sortAbis(found)
    }

    fun parseSupportedAbisFromAny(value: Any?): List<String> = when (value) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> {
            val collected = linkedSetOf<String>()
            for (i in 0 until value.length()) {
                val item = value.opt(i)
                collected += parseSupportedAbisFromAny(item)
            }
            sortAbis(collected)
        }
        is JSONObject -> {
            val collected = linkedSetOf<String>()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val child = value.opt(key)
                val boolState = parseBoolean(child)
                if (boolState != false) {
                    normalizeAbi(key)?.let(collected::add)
                }
                if (boolState == null) {
                    collected += parseSupportedAbisFromAny(child)
                }
            }
            sortAbis(collected)
        }
        is Collection<*> -> {
            val collected = linkedSetOf<String>()
            value.forEach { item ->
                collected += parseSupportedAbisFromAny(item)
            }
            sortAbis(collected)
        }
        is String -> parseSupportedAbisFromText(value)
        else -> parseSupportedAbisFromText(value.toString())
    }

    fun hasMismatchWithDevice(
        supportedAbis: Collection<String>?,
        deviceAbis: Collection<String> = AndroidUtils.deviceFilteredAbiList,
    ): Boolean {
        val pluginAbis = normalizeAbiList(supportedAbis)
        if (pluginAbis.isEmpty()) return false
        if (ABI_UNIVERSAL in pluginAbis) return false
        val localAbis = normalizeAbiList(deviceAbis)
        if (localAbis.isEmpty()) return false
        val primaryAbi = resolveDevicePrimaryAbi(localAbis)
        val effectiveDeviceAbis = filterDeviceAbisByPrimaryFamily(localAbis, primaryAbi)
        return pluginAbis.none { it in effectiveDeviceAbis }
    }

    fun formatAbiList(abis: Collection<String>): String = normalizeAbiList(abis).joinToString(", ")

    /** The index describes the full distribution; legacy INFO may describe only the installed APK. */
    fun resolveSupportedAbisForDisplay(
        declaredAbis: List<String>?,
        indexAbis: List<String>?,
        localAbis: List<String>?,
    ): List<String>? = declaredAbis ?: indexAbis ?: localAbis

    /** Match complete ABI tokens, so an x86 APK never selects an x86_64 asset by substring. */
    fun assetMatchesAbi(assetName: String, abi: String): Boolean =
        normalizeAbi(abi)?.let { it in parseSupportedAbisFromText(assetName) } == true

    fun formatAbiListOrFallback(abis: Collection<String>, fallback: String): String {
        val text = formatAbiList(abis)
        return text.ifBlank { fallback }
    }

    fun formatSupportedAbisForDisplay(
        supportedAbis: Collection<String>?,
        unknownText: String,
        allText: String,
    ): String {
        return when {
            supportedAbis == null -> unknownText
            supportedAbis.isEmpty() -> allText
            else -> formatAbiListOrFallback(supportedAbis, unknownText)
        }
    }

    fun resolveSupportedAbisFromValue(value: Any?): List<String>? {
        if (value == null || value == JSONObject.NULL) return null
        if (isExplicitEmptyListValue(value)) return emptyList()
        if (containsUniversalMarker(value)) return emptyList()
        val parsed = parseSupportedAbisFromAny(value)
            .filterNot { it == ABI_UNIVERSAL }
        return parsed.takeIf { it.isNotEmpty() }
    }

    /**
     * Parse all ABI directories from local APK native libs (e.g. `lib/<abi>/xxx.so` or `libs/<abi>/xxx.so`).
     *
     * Return contract:
     * - `null`: file is unreadable / invalid / analysis failed
     * - `emptyList()`: analyzed successfully but no native `.so` found
     * - non-empty: parsed ABI list in stable order
     */
    fun parseApkSupportedAbis(apkFile: File): List<String>? {
        if (!apkFile.exists() || !apkFile.isFile || !apkFile.canRead()) {
            return null
        }
        val knownAbiSet = ABI_ORDER.toSet() - ABI_UNIVERSAL
        return runCatching {
            val found = linkedSetOf<String>()
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val path = entry.name
                    if (!path.endsWith(".so", ignoreCase = true)) continue

                    val parts = path.split('/')
                    if (parts.size < 3) continue
                    val root = parts[0]
                    val abiPart = parts[1]
                    if (root != "lib" && root != "libs") continue

                    val normalized = normalizeAbi(abiPart)
                    if (normalized != null && normalized in knownAbiSet) {
                        found += normalized
                    }
                }
            }
            sortAbis(found)
        }.getOrNull()
    }

    /**
     * Parse ABI list from installed package APK paths (base + splits).
     *
     * If at least one APK can be analyzed, returns union of all parsed ABIs.
     * - returns `null` only when all APK paths are unanalyzable.
     */
    fun parseInstalledApkSupportedAbis(apkPaths: Collection<String>): List<String>? {
        if (apkPaths.isEmpty()) return null
        var analyzed = false
        val found = linkedSetOf<String>()
        apkPaths
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { path ->
                val parsed = parseApkSupportedAbis(File(path))
                if (parsed != null) {
                    analyzed = true
                    found += parsed
                }
            }
        if (!analyzed) return null
        return sortAbis(found)
    }

    private fun sortAbis(abis: Collection<String>): List<String> {
        if (abis.isEmpty()) return emptyList()
        return abis
            .distinct()
            .sortedWith(
                compareBy<String>(
                    { ABI_ORDER_INDEX[it] ?: Int.MAX_VALUE },
                    { it },
                ),
            )
    }

    private fun isUniversalToken(token: String): Boolean {
        val normalized = token.trim().lowercase(Locale.ROOT)
        return normalized == ABI_UNIVERSAL ||
                normalized == "all" ||
                normalized == "any" ||
                normalized == "noarch" ||
                normalized.contains("universal") ||
                normalized.contains("all-abi") ||
                normalized.contains("all-arch")
    }

    private fun resolveDevicePrimaryAbi(normalizedDeviceAbis: List<String>): String? {
        if (normalizedDeviceAbis.isEmpty()) return null

        // Prefer arm64 when both ARM and x86 families are reported (common on some emulator images),
        // so plugins containing arm64 should not be treated as mismatched.
        if (AndroidUtils.Abi.ARM64_V8A in normalizedDeviceAbis) {
            return AndroidUtils.Abi.ARM64_V8A
        }

        val propertyAbi = SystemProperties.get("ro.product.cpu.abi")
            ?.let(::normalizeAbi)
            ?.takeIf { it in normalizedDeviceAbis }
        if (propertyAbi != null) return propertyAbi

        val propertyAbiList = SystemProperties.get("ro.product.cpu.abilist")
            ?.split(',')
            .orEmpty()
            .asSequence()
            .mapNotNull(::normalizeAbi)
            .firstOrNull { it in normalizedDeviceAbis }
        if (propertyAbiList != null) return propertyAbiList

        val supportedAbiFirst = Build.SUPPORTED_ABIS
            .asSequence()
            .mapNotNull(::normalizeAbi)
            .firstOrNull { it in normalizedDeviceAbis }
        if (supportedAbiFirst != null) return supportedAbiFirst

        val appMainAbi = runCatching { AndroidUtils.appMainAbi }
            .getOrNull()
            ?.let(::normalizeAbi)
            ?.takeIf { it in normalizedDeviceAbis }
        if (appMainAbi != null) return appMainAbi

        val hasArm = normalizedDeviceAbis.any { abiFamily(it) == "arm" }
        val x86Candidate = normalizedDeviceAbis.firstOrNull { abiFamily(it) == "x86" }
        if (hasArm && x86Candidate != null) {
            return x86Candidate
        }

        return normalizedDeviceAbis.firstOrNull()
    }

    private fun filterDeviceAbisByPrimaryFamily(
        normalizedDeviceAbis: List<String>,
        primaryAbi: String?,
    ): Set<String> {
        if (normalizedDeviceAbis.isEmpty()) return emptySet()
        val family = abiFamily(primaryAbi) ?: return normalizedDeviceAbis.toSet()
        return normalizedDeviceAbis.filterTo(linkedSetOf()) { abi ->
            abiFamily(abi) == family
        }.ifEmpty { normalizedDeviceAbis.toSet() }
    }

    private fun abiFamily(abi: String?): String? = when (abi) {
        AndroidUtils.Abi.ARM64_V8A,
        AndroidUtils.Abi.ARMEABI_V7A,
        AndroidUtils.Abi.ARMEABI,
            -> "arm"

        AndroidUtils.Abi.X86_64,
        AndroidUtils.Abi.X86,
            -> "x86"

        AndroidUtils.Abi.MIPS64,
        AndroidUtils.Abi.MIPS,
            -> "mips"

        ABI_RISCV64 -> "riscv"
        else -> null
    }

    private fun isExplicitEmptyListValue(value: Any): Boolean = when (value) {
        is JSONArray -> value.length() == 0
        is Collection<*> -> value.isEmpty()
        else -> false
    }

    private fun containsUniversalMarker(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL -> false
        is String -> parseSupportedAbisFromText(value).contains(ABI_UNIVERSAL) ||
                value.split(
                    ',', ';', '|', '/', '\\', ' ', '\n', '\r', '\t', '[', ']', '{', '}', '(', ')',
                ).any { token -> isUniversalToken(token) }
        is JSONArray -> {
            for (i in 0 until value.length()) {
                if (containsUniversalMarker(value.opt(i))) {
                    return true
                }
            }
            false
        }
        is Collection<*> -> value.any(::containsUniversalMarker)
        is JSONObject -> {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val boolState = parseBoolean(value.opt(key))
                if (boolState != false && isUniversalToken(key)) {
                    return true
                }
                if (containsUniversalMarker(value.opt(key))) {
                    return true
                }
            }
            false
        }
        else -> false
    }

    private fun parseBoolean(value: Any?): Boolean? = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.trim().lowercase(Locale.ROOT)) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
        else -> null
    }
}
