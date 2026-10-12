package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import java.text.SimpleDateFormat
import java.util.Locale

/** Sorts by the selected fields before using stable tie breakers. */
internal class PluginCenterSortPolicy {

    private val versionDateEpochCache = mutableMapOf<String, Long?>()

    fun comparator(
        primary: Sort = Sort.defaultPrimarySort(),
        primaryAscending: Boolean = primary.defaultAscending,
        secondary: Sort = primary.defaultSecondarySort(),
        secondaryAscending: Boolean = secondary.defaultAscending,
    ): Comparator<PluginCenterItem> {
        var comparator = comparatorForSortField(primary, primaryAscending)
        val usedSorts = linkedSetOf(primary)
        if (secondary != primary) {
            comparator = comparator.thenComparing(comparatorForSortField(secondary, secondaryAscending))
            usedSorts += secondary
        }
        SORT_FALLBACK_CHAIN.forEach { fallback ->
            if (usedSorts.add(fallback)) {
                comparator = comparator.thenComparing(comparatorForSortField(fallback, fallback.defaultAscending))
            }
        }
        return comparator
    }

    private fun comparatorForSortField(
        sort: Sort,
        ascending: Boolean,
    ): Comparator<PluginCenterItem> {
        val base: Comparator<PluginCenterItem> = when (sort) {
            Sort.STATUS -> compareBy {
                when {
                    !it.isInstalled -> 2
                    !it.isEnabled -> 1
                    else -> 0
                }
            }
            Sort.TITLE_ASC ->
                compareBy { normalizeText(it.title) }
            Sort.LAST_UPDATE_DESC ->
                compareBy { resolveLastUpdateSortValue(it) }
            Sort.PACKAGE_SIZE_DESC ->
                compareBy { resolvePackageSizeSortValue(it) }
            Sort.MECHANISM ->
                compareBy {
                    when (it.mechanism) {
                        PluginMechanism.AIDL -> 0
                        PluginMechanism.SDK -> 1
                    }
                }
            Sort.VERSION_CODE_DESC ->
                compareBy { resolveVersionSortValue(it) }
            Sort.VERSION_RELEASE_TIME_DESC ->
                compareBy { resolveVersionReleaseTimeSortValue(it) }
            Sort.FIRST_INSTALL_TIME_DESC ->
                compareBy { resolveFirstInstallSortValue(it) }
            Sort.PACKAGE_NAME_ASC ->
                compareBy { normalizeText(it.packageName) }
            Sort.AUTHOR_ASC ->
                compareBy { normalizeText(it.author) }
            Sort.SOURCE ->
                compareBy {
                    when (it.source) {
                        PluginIndexSource.OFFICIAL -> 0
                        PluginIndexSource.THIRD_PARTY -> 1
                    }
                }
        }
        return if (ascending) base else base.reversed()
    }

    private fun resolveVersionSortValue(item: PluginCenterItem): Long =
        item.versionCode?.takeIf { it > 0 } ?: Long.MIN_VALUE

    private fun resolveVersionReleaseTimeSortValue(item: PluginCenterItem): Long =
        parseVersionDateToEpochMillis(item.versionDate) ?: 0L

    private fun resolvePackageSizeSortValue(item: PluginCenterItem): Long = when {
        item.packageSize > 0 -> item.packageSize
        (item.installableApkSizeBytes ?: 0L) > 0L -> item.installableApkSizeBytes ?: 0L
        (item.updatableApkSizeBytes ?: 0L) > 0L -> item.updatableApkSizeBytes ?: 0L
        else -> 0L
    }

    private fun resolveFirstInstallSortValue(item: PluginCenterItem): Long =
        item.firstInstallTime?.takeIf { it > 0L }
            ?: item.lastInstallTime?.takeIf { it > 0L }
            ?: 0L

    private fun resolveLastUpdateSortValue(item: PluginCenterItem): Long =
        item.lastUpdateTime?.takeIf { it > 0L }
            ?: parseVersionDateToEpochMillis(item.versionDate)
            ?: item.versionCode?.takeIf { it > 0L }
            ?: 0L

    private fun normalizeText(value: String?): String =
        value?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
            ?: ""

    private fun parseVersionDateToEpochMillis(versionDate: String?): Long? {
        val normalized = versionDate?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (versionDateEpochCache.containsKey(normalized)) {
            return versionDateEpochCache[normalized]
        }

        val parsed = DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
            val locale = if (pattern.contains("MMM")) Locale.ENGLISH else Locale.US
            runCatching {
                SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(normalized)?.time
            }.getOrNull()
        }
        versionDateEpochCache[normalized] = parsed
        return parsed
    }

    companion object {
        private val DATE_PATTERNS = arrayOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy.MM.dd",
            "dd.MM.yyyy",
            "yyyyMMdd",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "MMM dd, yyyy",
            "dd MMM yyyy",
            "MMM dd yyyy",
        )
        private val SORT_FALLBACK_CHAIN = listOf(
            Sort.LAST_UPDATE_DESC,
            Sort.VERSION_RELEASE_TIME_DESC,
            Sort.VERSION_CODE_DESC,
            Sort.TITLE_ASC,
            Sort.PACKAGE_NAME_ASC,
        )
    }
}
