package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.PluginCenterAttentionLedger.Companion.SOURCE_AIDL
import org.autojs.autojs.core.plugin.center.PluginCenterAttentionLedger.Companion.SOURCE_LEGACY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCenterAttentionTest {

    private val ledger = PluginCenterAttentionLedger(processToken = "process-1")

    @Test
    fun theFirstComparisonOfASourceOnlyRecordsWhatIsInstalled() {
        assertEquals(emptyList<String>(), ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "b" to 2L), now = 100))
        assertEquals(emptyList<String>(), ledger.reconcile(SOURCE_LEGACY, mapOf("sdk" to 3L), now = 100))
        assertTrue(ledger.pins().isEmpty())
    }

    @Test
    fun aPackageInstalledWhilePluginCenterWasClosedIsPinnedOnce() {
        ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L), now = 100)

        assertEquals(listOf("new"), ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "new" to 5L), now = 200))
        assertEquals(emptyList<String>(), ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "new" to 5L), now = 300))
        assertEquals(200L, ledger.pins().getValue("new").createdAt)
    }

    @Test
    fun anUpdateIsNotAFreshInstallationButAReinstallIs() {
        ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "b" to 2L), now = 100)
        // A package briefly missing during its replacement is not forgotten. zh-CN: 替换期间短暂缺失的包不会被遗忘.
        ledger.reconcile(SOURCE_AIDL, mapOf("b" to 2L), now = 150)

        assertEquals(listOf("b"), ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "b" to 9L), now = 200))
    }

    @Test
    fun aBroadcastInstallationIsNotPinnedAgainByTheNextComparison() {
        ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L), now = 100)
        ledger.recordFreshInstall("new", firstInstallTime = 5L, now = 200)

        assertEquals(emptyList<String>(), ledger.reconcile(SOURCE_AIDL, mapOf("a" to 1L, "new" to 5L), now = 300))
        assertEquals(setOf("new"), ledger.pins().keys)
    }

    @Test
    fun seenPinsAreConsumedWhenTheSessionEnds() {
        ledger.recordFreshInstall("seen", 1L, now = 100)
        ledger.recordFreshInstall("unseen", 2L, now = 200)

        assertTrue(ledger.markSeen(listOf("seen")))
        assertFalse(ledger.markSeen(listOf("seen")))
        assertTrue(ledger.endSession())

        assertEquals(setOf("unseen"), ledger.pins().keys)
    }

    @Test
    fun pinsSeenByAnEarlierProcessAreDroppedWhenLoaded() {
        ledger.recordFreshInstall("seen", 1L, now = 100)
        ledger.recordFreshInstall("unseen", 2L, now = 200)
        ledger.markSeen(listOf("seen"))

        val reloaded = PluginCenterAttentionLedger(
            processToken = "process-2",
            known = ledger.knownFirstInstallTimes(),
            initializedSources = ledger.initializedSources(),
            pins = ledger.pins().values,
        )

        assertEquals(setOf("unseen"), reloaded.pins().keys)
    }

    @Test
    fun theNewestPinIsListedFirstAndOtherRowsKeepTheirOrder() {
        val pins = mapOf(
            "older" to PluginCenterAttentionLedger.Pin("older", createdAt = 100),
            "newer" to PluginCenterAttentionLedger.Pin("newer", createdAt = 200),
        )
        val sorted = listOf(item("a"), item("older"), item("b"), item("newer"))

        val result = PluginCenterAttentionPolicy.apply(sorted, pins) { false }

        assertEquals(listOf("newer", "older", "a", "b"), result.map { it.packageName })
        assertEquals(listOf(PluginCenterAttention.NEW_INSTALL, PluginCenterAttention.NEW_INSTALL, null, null), result.map { it.attention })
    }

    @Test
    fun aRowTheUserOperatedOnIsLabeledWithoutMoving() {
        val pins = mapOf("installed" to PluginCenterAttentionLedger.Pin("installed", createdAt = 100))
        val sorted = listOf(item("a"), item("installed"))

        val result = PluginCenterAttentionPolicy.apply(sorted, pins) { it == "installed" }

        assertEquals(listOf("a", "installed"), result.map { it.packageName })
        assertEquals(PluginCenterAttention.NEW_INSTALL, result.last().attention)
    }

    @Test
    fun rowsOutsideTheFilterOrNotInstalledAreNotPinned() {
        val pins = mapOf(
            "filtered" to PluginCenterAttentionLedger.Pin("filtered", createdAt = 100),
            "catalog" to PluginCenterAttentionLedger.Pin("catalog", createdAt = 200),
        )
        val sorted = listOf(item("a"), item("catalog").copy(isInstalled = false))

        val result = PluginCenterAttentionPolicy.apply(sorted, pins) { false }

        assertEquals(listOf("a", "catalog"), result.map { it.packageName })
        assertNull(result.last().attention)
        assertEquals(emptyList<String>(), PluginCenterAttentionPolicy.presentPins(sorted, pins))
    }

    @Test
    fun aFailedAutomaticEnableIsLabeledUntilThePluginIsEnabled() {
        val pin = PluginCenterAttentionLedger.Pin("p", createdAt = 100, enableFailed = true)
        val disabled = item("p").copy(isEnabled = false, enabledState = PluginEnabledState.DISABLED)
        val bindFailed = item("p").copy(enabledState = PluginEnabledState.ERROR(PluginError(PluginErrorCode.BIND_FAILED)))
        val notAuthorized = item("p").copy(isEnabled = false, lastError = PluginError(PluginErrorCode.NOT_AUTHORIZED))

        assertEquals(PluginCenterAttention.ENABLE_FAILED, PluginCenterAttentionPolicy.attentionOf(disabled, pin))
        assertEquals(PluginCenterAttention.ENABLE_FAILED, PluginCenterAttentionPolicy.attentionOf(bindFailed, pin.copy(enableFailed = false)))
        assertEquals(PluginCenterAttention.ENABLE_FAILED, PluginCenterAttentionPolicy.attentionOf(notAuthorized, pin.copy(enableFailed = false)))
        assertEquals(PluginCenterAttention.NEW_INSTALL, PluginCenterAttentionPolicy.attentionOf(item("p"), pin))
        assertEquals(PluginCenterAttention.NEW_INSTALL, PluginCenterAttentionPolicy.attentionOf(disabled, pin.copy(enableFailed = false)))
    }

    @Test
    fun withoutPinsTheSortedListIsReturnedAsIs() {
        val sorted = listOf(item("a"), item("b"))
        assertSame(sorted, PluginCenterAttentionPolicy.apply(sorted, emptyMap()) { false })
    }

    @Test
    fun theLabelTakesPartInTheListDiff() {
        val plain = item("a")
        assertFalse(PluginCenterItemSemantics.areContentsTheSame(plain, plain.copy(attention = PluginCenterAttention.NEW_INSTALL)))
    }

    private fun item(name: String) = PluginCenterItem(
        title = name,
        packageName = name,
        versionName = "1.0",
        isInstalled = true,
        isEnabled = true,
    )
}
