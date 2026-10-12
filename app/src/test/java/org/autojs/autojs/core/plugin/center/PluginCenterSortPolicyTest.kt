package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginCenterSortPolicyTest {

    private val policy = PluginCenterSortPolicy()

    @Test
    fun defaultsGroupByStatusBeforeSortingEachGroupByNewestUpdate() {
        val items = listOf(
            item("uninstalledOld", installed = false, enabled = false, updated = 500),
            item("disabledOld", enabled = false, updated = 300),
            item("installedOld", updated = 100),
            item("uninstalledNew", installed = false, updated = 600),
            item("installedNew", updated = 200),
            item("disabledNew", enabled = false, updated = 400),
        )

        assertEquals(
            listOf("installedNew", "installedOld", "disabledNew", "disabledOld", "uninstalledNew", "uninstalledOld"),
            items.sortedWith(policy.comparator()).map { it.title },
        )
    }

    @Test
    fun reversingStatusKeepsTheSecondaryUpdateOrderDescending() {
        val items = listOf(
            item("installed", updated = 900),
            item("disabled", enabled = false, updated = 800),
            item("older", installed = false, updated = 100),
            item("newer", installed = false, updated = 200),
        )

        assertEquals(
            listOf("newer", "older", "disabled", "installed"),
            items.sortedWith(policy.comparator(primaryAscending = false)).map { it.title },
        )
    }

    @Test
    fun secondaryDirectionCanChangeWithoutReversingStatusGroups() {
        val items = listOf(
            item("disabled", enabled = false, updated = 50),
            item("newer", updated = 200),
            item("older", updated = 100),
        )

        assertEquals(
            listOf("older", "newer", "disabled"),
            items.sortedWith(policy.comparator(secondaryAscending = true)).map { it.title },
        )
    }

    @Test
    fun aCustomSecondaryFieldTakesPrecedenceOverTheFallbackUpdateTime() {
        val items = listOf(item("Alpha", updated = 200), item("Zulu", updated = 100))

        assertEquals(
            listOf("Zulu", "Alpha"),
            items.sortedWith(policy.comparator(secondary = Sort.TITLE_ASC, secondaryAscending = false)).map { it.title },
        )
    }

    @Test
    fun secondarySortingAlsoAppliesToNonGroupingPrimaryFields() {
        val items = listOf(
            item("oldLarge", updated = 100).copy(packageSize = 200),
            item("small", updated = 900).copy(packageSize = 100),
            item("newLarge", updated = 200).copy(packageSize = 200),
        )

        assertEquals(
            listOf("newLarge", "oldLarge", "small"),
            items.sortedWith(policy.comparator(primary = Sort.PACKAGE_SIZE_DESC)).map { it.title },
        )
    }

    @Test
    fun missingUpdateTimesUseReleaseDatesAndUnknownDatesSortLast() {
        val items = listOf(
            item("unknown", installed = false).copy(versionDate = "invalid"),
            item("old", installed = false).copy(versionDate = "2026-10-10"),
            item("new", installed = false).copy(versionDate = "2026/10/12"),
        )

        assertEquals(listOf("new", "old", "unknown"), items.sortedWith(policy.comparator()).map { it.title })
    }

    @Test
    fun identicalStatusAndUpdateTimeHaveADeterministicPackageNameTieBreaker() {
        val items = listOf(
            item("same", updated = 100).copy(packageName = "plugin.z"),
            item("same", updated = 100).copy(packageName = "plugin.a"),
        )

        assertEquals(
            listOf("plugin.a", "plugin.z"),
            items.sortedWith(policy.comparator()).map { it.packageName },
        )
    }

    @Test
    fun enablingOrDisablingAPluginChangesItsGroupOnTheNextSort() {
        val newer = item("newer", updated = 200)
        val older = item("older", updated = 100)
        val items = listOf(newer, older)
        val comparator = policy.comparator()

        newer.isEnabled = false
        assertEquals(listOf("older", "newer"), items.sortedWith(comparator).map { it.title })
        newer.isEnabled = true
        assertEquals(listOf("newer", "older"), items.sortedWith(comparator).map { it.title })
    }

    @Test
    fun existingPersistedSortOrdinalsStillIdentifyTheSameFields() {
        assertEquals(
            listOf(
                "TITLE_ASC", "LAST_UPDATE_DESC", "PACKAGE_SIZE_DESC", "MECHANISM", "VERSION_CODE_DESC",
                "FIRST_INSTALL_TIME_DESC", "PACKAGE_NAME_ASC", "AUTHOR_ASC", "SOURCE", "VERSION_RELEASE_TIME_DESC",
            ),
            Sort.entries.take(10).map { it.name },
        )
    }

    private fun item(
        name: String,
        installed: Boolean = true,
        enabled: Boolean = true,
        updated: Long? = null,
    ) = PluginCenterItem(
        title = name,
        packageName = "plugin.$name",
        versionName = "1.0.0",
        isInstalled = installed,
        isEnabled = enabled,
        lastUpdateTime = updated,
    )
}
