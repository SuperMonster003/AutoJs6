package org.autojs.autojs.ui.settings.codegen

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SharedPreferencesCurrentModelSelectionTest {
    @Test
    fun selectionIsReadBackByANewAdapterAfterSynchronousCommit() {
        val preferences = FakePreferences().apply { values["unrelated"] = "retained" }
        val selection = SharedPreferencesCurrentModelSelection(preferences, KEY)
        assertNull(selection.currentKey)
        selection.currentKey = "model/1-deadbeef"
        assertEquals(1, preferences.commits)
        assertEquals("model/1-deadbeef", SharedPreferencesCurrentModelSelection(preferences, KEY).currentKey)
        selection.currentKey = null
        assertEquals(2, preferences.commits)
        assertFalse(preferences.contains(KEY))
        assertNull(SharedPreferencesCurrentModelSelection(preferences, KEY).currentKey)
        assertEquals("retained", preferences.values["unrelated"])
    }

    @Test
    fun failedCommitsLeaveMemoryChangedAndCanBeRetriedDurably() {
        val preferences = FakePreferences()
        val selection = SharedPreferencesCurrentModelSelection(preferences, KEY)
        selection.currentKey = "model/1-deadbeef"
        preferences.commitSucceeds = false
        assertThrows(IOException::class.java) { selection.currentKey = "model/2-feedface" }
        assertEquals("model/2-feedface", selection.currentKey)
        assertEquals("model/1-deadbeef", preferences.persistedValues[KEY])
        preferences.commitSucceeds = true
        selection.currentKey = "model/2-feedface"
        assertEquals("model/2-feedface", preferences.persistedValues[KEY])
        preferences.commitSucceeds = false
        assertThrows(IOException::class.java) { selection.currentKey = null }
        assertNull(selection.currentKey)
        assertEquals("model/2-feedface", preferences.persistedValues[KEY])
        preferences.commitSucceeds = true
        selection.currentKey = null
        assertFalse(preferences.persistedValues.containsKey(KEY))
        assertEquals(5, preferences.commits)
    }

    /** Only synchronous writes are supported, so accidentally switching to apply fails the test. */
    private class FakePreferences : SharedPreferences {
        val values = linkedMapOf<String, String>()
        val persistedValues = linkedMapOf<String, String>()
        var commits = 0
        var commitSucceeds = true

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("Not used")
        override fun getInt(key: String?, defValue: Int): Int = error("Not used")
        override fun getLong(key: String?, defValue: Long): Long = error("Not used")
        override fun getFloat(key: String?, defValue: Float): Float = error("Not used")
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = error("Not used")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changed = linkedMapOf<String, String?>()

            override fun putString(key: String?, value: String?) = apply { changed[requireNotNull(key)] = value }
            override fun remove(key: String?) = apply { changed[requireNotNull(key)] = null }
            override fun commit(): Boolean {
                commits++
                // Android updates its in-memory map even if persisting the change fails.
                changed.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                if (commitSucceeds) {
                    persistedValues.clear()
                    persistedValues.putAll(values)
                }
                return commitSucceeds
            }
            override fun apply(): Unit = error("Current model selection must use synchronous commit")
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = error("Not used")
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = error("Not used")
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = error("Not used")
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = error("Not used")
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = error("Not used")
            override fun clear(): SharedPreferences.Editor = error("Not used")
        }
    }

    companion object {
        private const val KEY = "current-model"
    }
}
