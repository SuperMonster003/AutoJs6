package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import java.util.Locale

/** Which of a list of directory qualifiers a precedence step keeps. */
private typealias ResourceCriterion = (List<ResourceQualifiers>, ResourceConfiguration) -> List<Boolean>

/**
 * The configuration project resources are chosen for (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A7,
 * decision D8): what a qualified resource directory (`values-night`, `layout-land`, `drawable-xxhdpi`) is matched
 * against. A script run takes it from the inflater's context (the script's activity); a preview session from its own
 * context with the session's size ([org.autojs.autojs.core.ui.UiContentHost.resourceConfiguration]).
 *
 * zh-CN: 选取工程资源所依据的配置 (宿主路线图 A7, 决策 D8), 即限定符目录匹配的对象. 脚本运行取布局器上下文 (脚本的 Activity), 预览会话取自身上下文与会话尺寸.
 */
data class ResourceConfiguration(
    val locales: List<Locale> = listOf(Locale.US),
    val rtl: Boolean = false,
    val smallestWidthDp: Int = 0,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
    val landscape: Boolean = false,
    val night: Boolean = false,
    val densityDpi: Int = ResourceQualifiers.DENSITY_DEFAULT,
    val sdkInt: Int = 0,
) {

    companion object {

        /** No locale beyond `en-US`, portrait, not night, no size and no API level: only unqualified directories match. */
        @JvmField
        val DEFAULT = ResourceConfiguration()

        @JvmStatic
        fun of(context: Context): ResourceConfiguration {
            val configuration = context.resources.configuration
            return ResourceConfiguration(
                locales = (0 until configuration.locales.size()).map { configuration.locales[it] }.ifEmpty { listOf(Locale.getDefault()) },
                rtl = configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                smallestWidthDp = configuration.smallestScreenWidthDp,
                widthDp = configuration.screenWidthDp,
                heightDp = configuration.screenHeightDp,
                landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                night = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
                densityDpi = configuration.densityDpi,
                sdkInt = Build.VERSION.SDK_INT,
            )
        }
    }
}

/**
 * The qualifiers of a resource directory name and Android's rules for them (host roadmap A7): a directory whose
 * qualifier contradicts the configuration is out (a locale or night mode it does not have, a width it does not reach, a
 * newer API level); among the rest each qualifier in Android's precedence order (locale, layout direction, smallest
 * width, available width, available height, orientation, night mode, density, version) keeps only the directories that
 * name it best, until one is left. Density never rules a directory out: the closest one wins, scaling down preferred,
 * and `anydpi` beats any. Supported: language and region (`zh`, `zh-rCN`, `b+zh+Hans+CN`), `ldrtl` / `ldltr`,
 * `sw<N>dp`, `w<N>dp`, `h<N>dp`, `port` / `land`, `night` / `notnight`, densities (`ldpi` ... `xxxhdpi`, `tvdpi`,
 * `nodpi`, `anydpi`, `<N>dpi`) and `v<N>`; a directory with any other qualifier is ignored.
 *
 * zh-CN: 资源目录名的限定符与 Android 的选取规则 (宿主路线图 A7): 先排除与配置冲突的目录, 再按 Android 的优先级逐项只保留匹配最好的目录, 直到剩下一个.
 * 密度从不排除目录: 取最接近的, 优先缩小, `anydpi` 最优. 含其它限定符的目录被忽略.
 */
internal data class ResourceQualifiers(
    val language: String? = null,
    val region: String? = null,
    val script: String? = null,
    val rtl: Boolean? = null,
    val smallestWidthDp: Int? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val landscape: Boolean? = null,
    val night: Boolean? = null,
    val density: Int? = null,
    val sdk: Int? = null,
) {

    fun contradicts(configuration: ResourceConfiguration): Boolean = when {
        language != null && localeRank(configuration) == null -> true
        rtl != null && rtl != configuration.rtl -> true
        smallestWidthDp != null && smallestWidthDp > configuration.smallestWidthDp -> true
        widthDp != null && widthDp > configuration.widthDp -> true
        heightDp != null && heightDp > configuration.heightDp -> true
        landscape != null && landscape != configuration.landscape -> true
        night != null && night != configuration.night -> true
        sdk != null && sdk > configuration.sdkInt -> true
        else -> false
    }

    /** Lower is better: the first configuration locale this one matches, a region match before a language-only one. */
    fun localeRank(configuration: ResourceConfiguration): Int? {
        val language = language ?: return null
        configuration.locales.forEachIndexed { index, locale ->
            if (!locale.language.equals(language, ignoreCase = true)) return@forEachIndexed
            if (region != null && !locale.country.equals(region, ignoreCase = true)) return@forEachIndexed
            if (script != null && !script.equals(scriptOf(locale), ignoreCase = true)) return@forEachIndexed
            return index * 2 + if (region != null) 0 else 1
        }
        return null
    }

    companion object {

        const val DENSITY_DEFAULT = 160
        const val DENSITY_ANY = 0xfffe
        const val DENSITY_NONE = 0xffff

        private val DENSITIES = mapOf(
            "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240, "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
            "nodpi" to DENSITY_NONE, "anydpi" to DENSITY_ANY,
        )
        private val LANGUAGE = Regex("[a-z]{2,3}")
        private val REGION = Regex("r([A-Z]{2}|[0-9]{3})")
        private val SMALLEST_WIDTH = Regex("sw(\\d+)dp")
        private val WIDTH = Regex("w(\\d+)dp")
        private val HEIGHT = Regex("h(\\d+)dp")
        private val DENSITY = Regex("(\\d+)dpi")
        private val VERSION = Regex("v(\\d+)")

        /**
         * The qualifiers of the directory [name] for resources of [type] (`values`, `values-zh-rCN-night`), or null
         * when the name is of another type or has a qualifier this repository does not support.
         */
        fun parse(name: String, type: String): ResourceQualifiers? {
            if (name == type) return ResourceQualifiers()
            if (!name.startsWith("$type-")) return null
            var result = ResourceQualifiers()
            for (token in name.substring(type.length + 1).split('-')) {
                result = with(result) {
                    when {
                        token.startsWith("b+") -> {
                            if (language != null) return null
                            val parts = token.substring(2).split('+')
                            if (!LANGUAGE.matches(parts[0])) return null
                            var next = copy(language = parts[0])
                            for (part in parts.drop(1)) {
                                next = when {
                                    part.length == 4 && part.all(Char::isLetter) && next.script == null && next.region == null -> next.copy(script = part)
                                    (part.length == 2 && part.all(Char::isLetter) || part.length == 3 && part.all(Char::isDigit)) && next.region == null -> next.copy(region = part.uppercase(Locale.ROOT))
                                    else -> return null
                                }
                            }
                            next
                        }
                        LANGUAGE.matches(token) && language == null && token !in DENSITIES -> copy(language = token)
                        REGION.matches(token) && language != null && region == null -> copy(region = token.substring(1))
                        token == "ldrtl" || token == "ldltr" -> if (rtl == null) copy(rtl = token == "ldrtl") else return null
                        SMALLEST_WIDTH.matches(token) -> if (smallestWidthDp == null) copy(smallestWidthDp = token.drop(2).dropLast(2).toInt()) else return null
                        WIDTH.matches(token) -> if (widthDp == null) copy(widthDp = token.drop(1).dropLast(2).toInt()) else return null
                        HEIGHT.matches(token) -> if (heightDp == null) copy(heightDp = token.drop(1).dropLast(2).toInt()) else return null
                        token == "port" || token == "land" -> if (landscape == null) copy(landscape = token == "land") else return null
                        token == "night" || token == "notnight" -> if (night == null) copy(night = token == "night") else return null
                        token in DENSITIES -> if (density == null) copy(density = DENSITIES.getValue(token)) else return null
                        DENSITY.matches(token) -> if (density == null) copy(density = token.dropLast(3).toInt()) else return null
                        VERSION.matches(token) -> if (sdk == null) copy(sdk = token.drop(1).toInt()) else return null
                        else -> return null
                    }
                }
            }
            return result
        }

        /** The value of the directory Android would choose for [configuration] among [candidates], or null when all contradict it. */
        fun <T> best(candidates: List<Pair<ResourceQualifiers, T>>, configuration: ResourceConfiguration): T? {
            var remaining = candidates.filterNot { it.first.contradicts(configuration) }
            for (criterion in CRITERIA) {
                if (remaining.size <= 1) break
                val keep = criterion(remaining.map { it.first }, configuration)
                remaining = remaining.filterIndexed { index, _ -> keep[index] }
            }
            return remaining.firstOrNull()?.second
        }

        /** Keeps the candidates that name a qualifier with the best (lowest) score; all of them when none names it. */
        private fun byScore(score: (ResourceQualifiers, ResourceConfiguration) -> Int?): ResourceCriterion = { qualifiers, configuration ->
            val scores = qualifiers.map { score(it, configuration) }
            val best = scores.filterNotNull().minOrNull()
            scores.map { best == null || it == best }
        }

        /** Android's precedence order of the supported qualifiers. */
        private val CRITERIA: List<ResourceCriterion> = listOf(
            byScore { q, c -> q.localeRank(c) },
            byScore { q, _ -> q.rtl?.let { 0 } },
            byScore { q, _ -> q.smallestWidthDp?.let { -it } },
            byScore { q, _ -> q.widthDp?.let { -it } },
            byScore { q, _ -> q.heightDp?.let { -it } },
            byScore { q, _ -> q.landscape?.let { 0 } },
            byScore { q, _ -> q.night?.let { 0 } },
            ::byDensity,
            byScore { q, _ -> q.sdk?.let { -it } },
        )

        private fun byDensity(qualifiers: List<ResourceQualifiers>, configuration: ResourceConfiguration): List<Boolean> {
            if (qualifiers.all { it.density == null }) return qualifiers.map { true }
            val densities = qualifiers.map { it.density ?: DENSITY_DEFAULT }
            var best = densities.first()
            for (density in densities.drop(1)) if (betterDensity(density, best, configuration.densityDpi)) best = density
            return densities.map { it == best }
        }

        /** Android's `ResTable_config::isBetterThan` for densities: the closest, scaling down preferred; `anydpi` first. */
        internal fun betterDensity(a: Int, b: Int, requested: Int): Boolean {
            if (a == b) return false
            if (a == DENSITY_ANY) return true
            if (b == DENSITY_ANY) return false
            var high = a
            var low = b
            var aIsHigher = true
            if (low > high) {
                high = b
                low = a
                aIsHigher = false
            }
            if (requested >= high) return aIsHigher
            if (low >= requested) return !aIsHigher
            return if ((2 * low - requested).toLong() * high > requested.toLong() * requested) !aIsHigher else aIsHigher
        }

        /** The script of [locale], or the likely one for Chinese (Hans for CN / SG, Hant for TW / HK / MO). */
        private fun scriptOf(locale: Locale): String = locale.script.ifEmpty {
            if (locale.language != "zh") "" else when (locale.country) {
                "TW", "HK", "MO" -> "Hant"
                else -> "Hans"
            }
        }
    }
}
