package org.autojs.autojs.core.pref

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySettingsStoreTest {

    @Test
    fun everySharedPreferencesTypeRoundTrips() {
        val store = InMemorySettingsStore()
        store.edit {
            putBoolean("b", true)
            putInt("i", 42)
            putLong("l", 1L shl 40)
            putFloat("f", 1.5f)
            putString("s", "text")
            putStringSet("set", setOf("x", "y"))
        }
        assertEquals(true, store.getBoolean("b", false))
        assertEquals(42, store.getInt("i", 0))
        assertEquals(1L shl 40, store.getLong("l", 0L))
        assertEquals(1.5f, store.getFloat("f", 0f))
        assertEquals("text", store.getString("s", null))
        assertEquals(setOf("x", "y"), store.getStringSet("set", null))
        assertEquals(listOf("b", "i", "l", "f", "s", "set"), store.all().keys.toList())
    }

    @Test
    fun absentKeysReturnTheDefault() {
        val store = InMemorySettingsStore()
        assertEquals(true, store.getBoolean("missing", true))
        assertEquals(7, store.getInt("missing", 7))
        assertNull(store.getString("missing", null))
        assertNull(store.getStringSet("missing", null))
        assertNull(store.get("missing"))
        assertFalse(store.contains("missing"))
    }

    @Test
    fun typedReadOfAnotherTypeThrowsLikeSharedPreferences() {
        val store = InMemorySettingsStore(initial = mapOf("n" to 1, "s" to "1"))
        assertThrows(ClassCastException::class.java) { store.getLong("n", 0L) }
        assertThrows(ClassCastException::class.java) { store.getBoolean("s", false) }
        assertThrows(ClassCastException::class.java) { store.getStringSet("s", null) }
        assertThrows(ClassCastException::class.java) { store.getString("n", null) }
    }

    @Test
    fun unsupportedValuesAreRejected() {
        val store = InMemorySettingsStore()
        assertThrows(IllegalArgumentException::class.java) { store.put("d", 1.0) }
        assertThrows(IllegalArgumentException::class.java) { store.put("list", listOf("a")) }
        assertThrows(IllegalArgumentException::class.java) { store.put("ints", setOf(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { InMemorySettingsStore(initial = mapOf("d" to 2.0)) }
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun nullValueRemovesTheKey() {
        val store = InMemorySettingsStore(initial = mapOf("s" to "v"))
        assertEquals(setOf("s"), store.put("s", null))
        assertFalse(store.contains("s"))
        assertEquals(emptySet<String>(), store.remove("s"))
    }

    @Test
    fun stringSetsAreCopiedInAndOut() {
        val source = linkedSetOf("a")
        val store = InMemorySettingsStore()
        store.put("set", source)
        source.add("b")
        assertEquals(setOf("a"), store.getStringSet("set", null))
        (store.getStringSet("set", null) as MutableSet<String>).add("c")
        (store.all()["set"] as MutableSet<*>).clear()
        assertEquals(setOf("a"), store.get("set"))
    }

    @Test
    fun clearRunsBeforeThePutsOfTheSameEdit() {
        val store = InMemorySettingsStore(initial = mapOf("a" to 1, "b" to 2))
        store.edit {
            putInt("c", 3)
            clear()
        }
        assertEquals(mapOf("c" to 3), store.all())
    }

    @Test
    fun listenersGetOnlyChangedKeysAndNullAfterClear() {
        val store = InMemorySettingsStore(initial = mapOf("a" to 1, "b" to "x"))
        val events = mutableListOf<String?>()
        val registration = store.listen { events.add(it) }

        store.edit {
            putInt("a", 1)
            putString("b", "y")
            remove("absent")
        }
        assertEquals(listOf<String?>("b"), events)

        events.clear()
        store.edit {
            clear()
            putInt("a", 5)
        }
        assertEquals(listOf(null, "a"), events)

        events.clear()
        registration.close()
        store.put("a", 6)
        assertTrue(events.isEmpty())
    }

    @Test
    fun editReturnsChangedKeysAndCountsEveryCommit() {
        val store = InMemorySettingsStore(initial = mapOf("keep" to true))
        assertEquals(0, store.commitCount)
        val changed = store.edit {
            putBoolean("keep", true)
            putInt("new", 1)
            remove("absent")
        }
        assertEquals(setOf("new"), changed)
        assertEquals(1, store.commitCount)
        assertEquals(emptySet<String>(), store.edit { })
        assertEquals(2, store.commitCount)
        assertEquals(setOf("keep", "new"), store.clear())
        assertEquals(3, store.commitCount)
    }

    @Test
    fun typeTagsFollowTheArchiveFormat() {
        assertEquals("bool", InMemorySettingsStore.typeTag(false))
        assertEquals("int", InMemorySettingsStore.typeTag(1))
        assertEquals("long", InMemorySettingsStore.typeTag(1L))
        assertEquals("float", InMemorySettingsStore.typeTag(1f))
        assertEquals("string", InMemorySettingsStore.typeTag(""))
        assertEquals("stringSet", InMemorySettingsStore.typeTag(emptySet<String>()))
        assertThrows(IllegalArgumentException::class.java) { InMemorySettingsStore.typeTag(1.0) }
    }

    @Test
    fun nameDefaultsToTheDefaultStore() {
        assertEquals("default", InMemorySettingsStore().name)
        assertEquals("editor_symbols_config", InMemorySettingsStore("editor_symbols_config").name)
    }
}
