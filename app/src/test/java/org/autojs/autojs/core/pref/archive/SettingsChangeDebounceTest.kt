package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.CredentialKeys
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.EditorKeyboardKeys
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P9.3: the debounce of the change backup ([SettingsChangeDebounce], T20): five minutes without
 * a tracked change before one backup, the changes that count, and the work as WorkManager runs it, with a test clock and
 * a recording scheduler.
 *
 * zh-CN: ROADMAP P9.3: 变更后备份的防抖 ([SettingsChangeDebounce], T20): 计入的变化之后 5 分钟没有新变化才备份一次, 哪些变化计入, 以及按
 * WorkManager 的方式执行任务; 使用测试时钟与记录调用的调度器.
 */
class SettingsChangeDebounceTest : DomainKeysTestBase() {

    private var now = 0L

    /** Records the delays scheduled; [due] runs the work the way WorkManager does once its delay is over. */
    private inner class RecordingScheduler : SettingsChangeDebounce.Scheduler {

        val delays = ArrayList<Long>()

        var cancels = 0

        /** The time the pending work runs at, or null. */
        var dueAt: Long? = null

        var backups = 0

        lateinit var debounce: SettingsChangeDebounce

        override fun schedule(delayMillis: Long) {
            delays += delayMillis
            dueAt = now + delayMillis
        }

        override fun cancel() {
            cancels++
            dueAt = null
        }

        /** Moves the clock to [time], running the work whenever it is due on the way. */
        fun advanceTo(time: Long) {
            while (true) {
                val at = dueAt?.takeIf { it <= time } ?: break
                now = at
                dueAt = null
                if (debounce.onDue()) backups++
            }
            now = time
        }
    }

    private fun debounce(scheduler: RecordingScheduler, enabled: () -> Boolean = { true }) =
        SettingsChangeDebounce(scheduler, { now }, filter = SettingsChangeDebounce.Filter(ownKeys = listOf(OWN_KEY)), enabled = enabled)
            .also { scheduler.debounce = it }

    private fun change(debounce: SettingsChangeDebounce, at: Long, name: String? = AppearanceKeys.NIGHT_MODE_ENABLED.name, store: StoreId = StoreId.DEFAULT) {
        now = at
        debounce.onChanged(store, name)
    }

    @Test
    fun aBurstOfChangesYieldsOneBackupFiveMinutesAfterTheLast() {
        val scheduler = RecordingScheduler()
        val debounce = debounce(scheduler)
        assertEquals(5 * MINUTE, SettingsChangeDebounce.DELAY_MILLIS)
        change(debounce, 0)
        change(debounce, MINUTE)
        scheduler.advanceTo(2 * MINUTE)
        change(debounce, 2 * MINUTE + 30 * SECOND)
        // Scheduled once for the burst; at 5 minutes the work finds 2.5 quiet minutes and comes back for the rest.
        assertEquals(listOf(5 * MINUTE), scheduler.delays)
        scheduler.advanceTo(7 * MINUTE)
        assertEquals(0, scheduler.backups)
        assertEquals(listOf(5 * MINUTE, 2 * MINUTE + 30 * SECOND), scheduler.delays)
        scheduler.advanceTo(7 * MINUTE + 30 * SECOND)
        assertEquals(1, scheduler.backups)
        scheduler.advanceTo(60 * MINUTE)
        assertEquals(1, scheduler.backups)

        // The next change starts another burst, and another backup.
        change(debounce, 60 * MINUTE)
        scheduler.advanceTo(65 * MINUTE)
        assertEquals(2, scheduler.backups)
        assertEquals(listOf(5 * MINUTE, 2 * MINUTE + 30 * SECOND, 5 * MINUTE), scheduler.delays)

        // Changes every minute for an hour keep putting it off, with few works scheduled.
        (0..60).forEach {
            change(debounce, 100 * MINUTE + it * MINUTE)
            scheduler.advanceTo(now)
        }
        assertEquals(2, scheduler.backups)
        scheduler.advanceTo(200 * MINUTE)
        assertEquals(3, scheduler.backups)
        // One work for the burst, and one more each time it came due (minutes 105, 110, ..., 160).
        assertEquals(3 + 1 + 12, scheduler.delays.size)
    }

    @Test
    fun onlyChangesOfTheBackedUpCategoriesCount() {
        val filter = SettingsChangeDebounce.Filter(ownKeys = listOf(OWN_KEY))
        val keyboard = EditorKeyboardKeys.FONT_SELECTED_VARIANT
        val tracked = listOf(
            StoreId.DEFAULT to AppearanceKeys.NIGHT_MODE_ENABLED.name,
            StoreId.DEFAULT to ExplorerKeys.WORKING_DIRECTORY.name,
            keyboard.template.store to keyboard.nameOf("Iosevka"),
            StoreId.DEFAULT to null,
        )
        val ignored = listOf(
            // `other`: a state key, and a name nobody declares (the run-time fallback of P4.3).
            StoreId.DEFAULT to MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name,
            StoreId.DEFAULT to "some_undeclared_key",
            // Categories an automatic backup leaves out, so their changes would not change it.
            CredentialKeys.GITHUB_TOKEN.store to CredentialKeys.GITHUB_TOKEN.name,
            StoreId.DEFAULT to DeveloperKeys.LEAKCANARY_ENABLED.name,
            // The keys of the automatic backup itself.
            OWN_KEY.store to OWN_KEY.name,
            // A store nobody declares a key of, also when it is cleared.
            StoreId("not_a_settings_store") to "x",
            StoreId("not_a_settings_store") to null,
        )
        tracked.forEach { (store, name) -> assertTrue("$store:$name", filter.tracks(store, name)) }
        ignored.forEach { (store, name) -> assertFalse("$store:$name", filter.tracks(store, name)) }

        val scheduler = RecordingScheduler()
        val debounce = debounce(scheduler)
        ignored.forEach { (store, name) -> change(debounce, now + MINUTE, name, store) }
        scheduler.advanceTo(now + 60 * MINUTE)
        assertEquals(emptyList<Long>(), scheduler.delays)
        assertEquals(0, scheduler.backups)
    }

    @Test
    fun theStoresOfTheAppReportTheirChanges() {
        val scheduler = RecordingScheduler()
        val subscription = SettingsStores.addChangeListener(debounce(scheduler))
        try {
            store.edit { put(MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.name, true) }
            assertEquals(emptyList<Long>(), scheduler.delays)
            store.edit { put(ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE.name, false) }
            assertEquals(listOf(5 * MINUTE), scheduler.delays)
            scheduler.advanceTo(5 * MINUTE)
            assertEquals(1, scheduler.backups)
            store.clear()
            assertEquals(listOf(5 * MINUTE, 5 * MINUTE), scheduler.delays)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun aWorkDueInANewProcessBacksUpAndASwitchedOffOneDoesNot() {
        // The app was stopped after a change: the new process knows nothing of it, but the work is due, so back up.
        assertTrue(debounce(RecordingScheduler()).onDue())

        var on = true
        val scheduler = RecordingScheduler()
        val debounce = debounce(scheduler) { on }
        change(debounce, 0)
        on = false
        change(debounce, MINUTE)
        scheduler.advanceTo(10 * MINUTE)
        assertEquals(0, scheduler.backups)
        // Switched off, a change schedules nothing; cancelling forgets the changes and cancels the work.
        assertEquals(listOf(5 * MINUTE), scheduler.delays)
        on = true
        change(debounce, 20 * MINUTE)
        debounce.cancel()
        assertEquals(1, scheduler.cancels)
        scheduler.advanceTo(30 * MINUTE)
        assertEquals(0, scheduler.backups)
        assertThrows(IllegalArgumentException::class.java) { SettingsChangeDebounce(scheduler, delayMillis = 0) }
    }

    @Test
    fun aClockThatGoesBackPutsTheBackupOffByTheDelayAtMost() {
        val scheduler = RecordingScheduler()
        val debounce = debounce(scheduler)
        change(debounce, 100 * MINUTE)
        // The clock is set back by an hour before the work runs.
        now = 40 * MINUTE
        assertFalse(debounce.onDue())
        assertEquals(listOf(5 * MINUTE, 5 * MINUTE), scheduler.delays)
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND

        /** Stands in for a switch of the automatic backups, which the pages declare later. */
        val OWN_KEY = AppearanceKeys.THEME_COLOR_PRIMARY
    }
}
