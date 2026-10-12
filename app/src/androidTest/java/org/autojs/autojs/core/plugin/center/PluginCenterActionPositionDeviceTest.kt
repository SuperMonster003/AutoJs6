package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.autojs.autojs.core.plugin.center.PluginCenterFragment.Sort
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Fragment collector, row action and adapter without changing installed plugins. */
@RunWith(AndroidJUnit4::class)
class PluginCenterActionPositionDeviceTest {

    @Test
    fun disablingKeepsTheRowWhileAutomaticSnapshotsUpdateAndSortOtherRows() = withCenter { fixture ->
        fixture.onMain { it.disableFirst() }
        fixture.awaitOrder("first", "second", "third")
        fixture.onMain {
            assertFalse(it.adapter.items().first().isEnabled)
            it.publish(it.current().map { row ->
                if (row.title == "third") row.copy(lastUpdateTime = 400, description = "automatic update") else row
            })
        }
        fixture.awaitOrder("first", "third", "second")
        fixture.onMain {
            assertEquals("automatic update", it.adapter.items()[1].description)
            it.fragment.setSort(Sort.STATUS)
        }
        fixture.awaitOrder("third", "second", "first")
    }

    @Test
    fun returningFromAnExternalActivityStillRefreshesWithoutLosingTheActionPosition() = withCenter { fixture ->
        fixture.onMain { it.disableFirst() }
        fixture.awaitOrder("first", "second", "third")
        var generation = 0L
        var snapshot = emptyList<PluginCenterItem>()
        fixture.onMain {
            generation = it.generation()
            snapshot = it.current()
        }
        fixture.scenario.moveToState(Lifecycle.State.CREATED)
        fixture.scenario.moveToState(Lifecycle.State.RESUMED)
        fixture.onMain {
            assertTrue("Returning must still trigger discovery", it.generation() > generation)
            it.stopDiscovery()
            it.publish(snapshot.map { row -> row.copy(description = "fresh after return") })
        }
        fixture.awaitOrder("first", "second", "third")
        fixture.awaitItems { rows -> rows.firstOrNull()?.description == "fresh after return" }
        fixture.onMain { assertEquals("fresh after return", it.adapter.items().first().description) }
    }

    @Test
    fun anOperatedRowSurvivesItsStateFilterUntilTheUserAppliesTheFilterAgain() = withCenter { fixture ->
        val enabledOnly = PluginFilterStore.FilterState(enabledState = PluginFilterStore.EnabledState.ENABLED)
        fixture.onMain {
            it.fragment.setFilterState(enabledOnly)
            it.disableFirst()
        }
        fixture.awaitOrder("first", "second", "third")
        fixture.onMain {
            it.publish(it.current().map { row -> if (row.title == "second") row.copy(isEnabled = false) else row })
        }
        fixture.awaitOrder("first", "third")
        fixture.onMain { it.fragment.setFilterState(enabledOnly) }
        fixture.awaitOrder("third")
    }

    private fun withCenter(block: (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sortPreferences = context.getSharedPreferences("plugin_center_sort_state", Context.MODE_PRIVATE)
        val keys = listOf(
            "key_\$_plugin_center_sort_state", "key_\$_plugin_center_sort_state_secondary_STATUS",
            "key_\$_plugin_center_sort_state_ascending_STATUS", "key_\$_plugin_center_sort_state_ascending_LAST_UPDATE_DESC",
        )
        val saved = keys.associateWith { sortPreferences.all[it] }
        val enablePreferences = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
        val enableKey = "key_\$_enabled_plugin_\$_${PACKAGE_PREFIX}first"
        val savedEnabled = enablePreferences.all[enableKey] as? Boolean
        try {
            PluginSortStore.setSort(context, Sort.STATUS)
            PluginSortStore.setSecondarySort(context, Sort.STATUS, Sort.LAST_UPDATE_DESC)
            PluginSortStore.setAscending(context, Sort.STATUS, true)
            PluginSortStore.setAscending(context, Sort.LAST_UPDATE_DESC, false)
            ActivityScenario.launch(PluginCenterActivity::class.java).use { scenario ->
                val fixture = Fixture(scenario)
                fixture.onMain {
                    it.stopDiscovery()
                    it.fragment.setSort(Sort.STATUS)
                    it.fragment.setFilterState(PluginFilterStore.defaultFilterState())
                    it.publish(listOf(item("first", 300), item("second", 200), item("third", 100)))
                }
                fixture.awaitOrder("first", "second", "third")
                block(fixture)
            }
        } finally {
            sortPreferences.edit().apply {
                saved.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        else -> remove(key)
                    }
                }
            }.commit()
            enablePreferences.edit().apply {
                if (savedEnabled == null) remove(enableKey) else putBoolean(enableKey, savedEnabled)
            }.commit()
        }
    }

    private class Fixture(val scenario: ActivityScenario<PluginCenterActivity>) {
        lateinit var fragment: PluginCenterFragment
        lateinit var model: PluginCenterViewModel
        val adapter: PluginCenterItemAdapter
            get() = fragment.requireView().findViewById<RecyclerView>(R.id.plugin_center_recycler_view).adapter as PluginCenterItemAdapter

        init {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_plugin_center) as PluginCenterFragment
                model = ViewModelProvider(fragment)[PluginCenterViewModel::class.java]
            }
        }

        fun onMain(block: (Fixture) -> Unit) = scenario.onActivity { block(this) }

        fun stopDiscovery() {
            (field(model, "loadJob") as? Job)?.cancel()
        }

        fun generation(): Long = field(model, "loadGeneration") as Long
        fun current(): List<PluginCenterItem> = model.items.value

        @Suppress("UNCHECKED_CAST")
        fun publish(items: List<PluginCenterItem>) {
            (field(model, "_items") as MutableStateFlow<List<PluginCenterItem>>).value = items
        }

        fun disableFirst() {
            val listener = field(adapter, "listener") as PluginCenterItemAdapter.Listener
            listener.onToggleEnable(adapter.items().first { it.title == "first" }, false)
        }

        fun awaitOrder(vararg expected: String) {
            awaitItems { rows -> rows.map { it.title } == expected.toList() }
        }

        fun awaitItems(matches: (List<PluginCenterItem>) -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 10_000L
            var actual = emptyList<PluginCenterItem>()
            do {
                onMain { actual = adapter.items() }
                if (matches(actual)) return
                SystemClock.sleep(50L)
            } while (SystemClock.uptimeMillis() < deadline)
            assertTrue("Unexpected rows: ${actual.map { it.title to it.description }}", matches(actual))
        }

        private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    }

    private fun item(name: String, updated: Long) = PluginCenterItem(
        title = name, packageName = "$PACKAGE_PREFIX$name", versionName = "1.0",
        isInstalled = true, isEnabled = true, lastUpdateTime = updated,
    )

    private companion object {
        const val PACKAGE_PREFIX = "org.autojs.autojs6.test.position."
    }
}
