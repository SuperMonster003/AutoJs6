package org.autojs.autojs.core.plugin.terminal

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TerminalLegacyCleanupTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val legacyKeys = listOf(
        "key_\$_terminal_text_size",
        "key_\$_terminal_npm_registry",
        "key_\$_terminal_npm_registry_custom_url",
        "key_\$_terminal_npm_ignore_scripts",
        "key_\$_terminal_manager_sessions_level",
        "key_\$_dialog_terminal_tips",
    )

    private val unrelatedKeys = listOf(
        "key_\$_night_mode_enabled",
        "key_\$_enabled_plugin_\$_io.github.supermonster003.autojs6.plugin.three.shell.terminal",
        "key_\$_terminalx",
        "key_\$_dialog_terminal_tips_v2",
        "terminal",
    )

    @Test
    fun `an existing terminal tree is deleted together with its contents`() {
        val filesDir = temporaryFolder.newFolder("files")
        val root = File(filesDir, "terminal")
        File(root, "usr/lib/autojs6-node-cli/bin").mkdirs()
        File(root, "usr/lib/autojs6-node-cli/bin/node").writeText("#!/bin/sh")
        File(root, "home/.profile").apply { parentFile!!.mkdirs(); writeText("export A=1") }
        val sibling = File(filesDir, "scripts").apply { mkdirs() }

        val result = TerminalLegacyCleanup.run(filesDir, FakePreferences())

        assertTrue(result.directoryExisted)
        assertTrue(result.directoryDeleted)
        assertTrue(result.changedAnything)
        assertFalse(root.exists())
        assertTrue(sibling.exists())
    }

    @Test
    fun `a missing terminal tree is reported without side effects`() {
        val filesDir = temporaryFolder.newFolder("files")
        val before = filesDir.list()!!.toSet()

        val result = TerminalLegacyCleanup.run(filesDir, FakePreferences())

        assertFalse(result.directoryExisted)
        assertFalse(result.directoryDeleted)
        assertFalse(result.changedAnything)
        assertEquals(before, filesDir.list()!!.toSet())
        assertEquals(emptyList<String>(), result.removedKeys)
    }

    @Test
    fun `legacy keys are removed and every other key survives`() {
        val preferences = FakePreferences()
        legacyKeys.forEach { preferences.values[it] = "x" }
        unrelatedKeys.forEach { preferences.values[it] = "keep" }

        val result = TerminalLegacyCleanup.run(temporaryFolder.newFolder("files"), preferences)

        assertEquals(legacyKeys.sorted(), result.removedKeys)
        assertEquals(unrelatedKeys.toSet(), preferences.values.keys)
        assertTrue(result.changedAnything)
        // Second run: nothing left to do.
        val again = TerminalLegacyCleanup.run(temporaryFolder.newFolder("files2"), preferences)
        assertEquals(emptyList<String>(), again.removedKeys)
        assertFalse(again.changedAnything)
        assertEquals(unrelatedKeys.toSet(), preferences.values.keys)
    }

    @Test
    fun `key selection follows the legacy prefix and the tips flag exactly`() {
        legacyKeys.forEach { assertTrue(it, TerminalLegacyCleanup.isLegacyKey(it)) }
        unrelatedKeys.forEach { assertFalse(it, TerminalLegacyCleanup.isLegacyKey(it)) }
        assertEquals(legacyKeys.sorted(), TerminalLegacyCleanup.selectLegacyKeys(unrelatedKeys + legacyKeys))
        assertEquals("key_\$_terminal_", TerminalLegacyCleanup.LEGACY_KEY_PREFIX)
        assertEquals("key_\$_dialog_terminal_tips", TerminalLegacyCleanup.LEGACY_TIPS_KEY)
        assertEquals("terminal", TerminalLegacyCleanup.LEGACY_ROOT_DIR_NAME)
    }

    /** In-memory SharedPreferences; the editor applies removals synchronously. */
    private class FakePreferences : SharedPreferences {
        val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? MutableSet<String> ?: defValues
        }
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val removals = mutableListOf<String>()
            private val puts = linkedMapOf<String, Any?>()
            private var clear = false

            override fun putString(key: String?, value: String?) = apply { puts[key!!] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { puts[key!!] = values }
            override fun putInt(key: String?, value: Int) = apply { puts[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { puts[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { puts[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { puts[key!!] = value }
            override fun remove(key: String?) = apply { removals += key!! }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) values.clear()
                removals.forEach(values::remove)
                values.putAll(puts)
            }
        }
    }
}
