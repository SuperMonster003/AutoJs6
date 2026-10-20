package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.JsonFileStore
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [JsonFileStore] in a JVM temporary directory (docs/settings/ROADMAP.md P1.2, T3): typed round trips through the
 * file, SharedPreferences rules of [JsonFileStore.edit], listeners, atomic replacement and damaged files.
 *
 * zh-CN: 在 JVM 临时目录中测试 [JsonFileStore] (ROADMAP P1.2, T3): 经文件的类型化往返, [JsonFileStore.edit] 的
 * SharedPreferences 规则, 监听器, 原子替换与损坏的文件.
 */
class JsonFileStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String = "store.json") = File(temp.root, "sub/$name")

    @Test
    fun missingFileIsAnEmptyStore() {
        val store = JsonFileStore(file())
        assertEquals(emptyMap<String, Any>(), store.all())
        assertFalse(store.contains("a"))
        assertNull(store.loadError)
        assertFalse(file().exists())
        assertEquals(StoreId("store.json", StoreId.Backend.JSON_FILE), store.id)
    }

    @Test
    fun valuesKeepTheirTypesThroughTheFile() {
        val values = linkedMapOf<String, Any>("b" to true, "i" to 7, "l" to 3000000000L, "small" to 5L, "f" to 0.1f, "whole" to 2f, "s" to "x/\"y\"", "set" to linkedSetOf("b", "a"))
        JsonFileStore(file()).edit { values.forEach { (k, v) -> put(k, v) } }
        val reopened = JsonFileStore(file())
        assertEquals(values, reopened.all())
        values.forEach { (k, v) -> assertEquals(k, v.javaClass, reopened[k]!!.javaClass) }
        assertEquals(true, reopened.getBoolean("b", false))
        assertEquals(7, reopened.getInt("i", 0))
        assertEquals(5L, reopened.getLong("small", 0L))
        assertEquals(0.1f, reopened.getFloat("f", 0f))
        assertEquals("x/\"y\"", reopened.getString("s", null))
        assertEquals(setOf("a", "b"), reopened.getStringSet("set", null))
        assertEquals("""{"t":"long","v":5}""", Regex("\"small\":(\\{[^}]*\\})").find(file().readText())!!.groupValues[1])
    }

    @Test
    fun typedGettersThrowOnMismatchLikeSharedPreferences() {
        val store = JsonFileStore(file())
        store.put("i", 1)
        assertThrows(ClassCastException::class.java) { store.getLong("i", 0L) }
        assertThrows(ClassCastException::class.java) { store.getString("i", null) }
        assertEquals("d", store.getString("absent", "d"))
        assertThrows(IllegalArgumentException::class.java) { store.put("d", 1.0) }
        assertThrows(IllegalArgumentException::class.java) { store.put("ints", setOf(1)) }
    }

    @Test
    fun editFollowsSharedPreferencesRules() {
        val store = JsonFileStore(file())
        store.edit {
            putInt("a", 1)
            putInt("b", 2)
        }
        val changed = store.edit {
            putInt("c", 3)
            clear()
            putInt("a", 1)
        }
        // clear() runs before the puts of the same block, whatever the call order.
        assertEquals(setOf("a", "b", "c"), changed)
        assertEquals(mapOf("a" to 1, "c" to 3), JsonFileStore(file()).all())
        assertEquals(emptySet<String>(), store.put("a", 1))
        assertEquals(setOf("a"), store.put("a", null))
        assertEquals(emptySet<String>(), store.remove("a"))
        assertEquals(mapOf("c" to 3), JsonFileStore(file()).all())
    }

    @Test
    fun unchangedEditsDoNotWrite() {
        val store = JsonFileStore(file())
        store.put("a", 1)
        val modified = file().lastModified()
        file().setLastModified(modified - 10_000)
        assertEquals(emptySet<String>(), store.put("a", 1))
        assertEquals(modified - 10_000, file().lastModified())
    }

    @Test
    fun listenersGetChangedKeysAndNullAfterClear() {
        val store = JsonFileStore(file())
        val events = mutableListOf<String?>()
        val registration = store.listen { events.add(it) }
        store.edit {
            putInt("a", 1)
            putInt("b", 2)
        }
        store.put("a", 1)
        store.clear()
        registration.close()
        store.put("z", 1)
        assertEquals(listOf("a", "b", null), events)
    }

    @Test
    fun writesReplaceTheFileWithoutLeavingTheTemporaryFile() {
        val store = JsonFileStore(file())
        store.put("a", "1")
        store.put("a", "2")
        assertEquals(listOf("store.json"), file().parentFile.list()!!.toList())
        assertEquals("""{"a":{"t":"string","v":"2"}}""", file().readText())
    }

    @Test
    fun damagedFilesReadAsEmptyAndAreReplacedOnWrite() {
        listOf("{not json", "[1]", """{"a":{"t":"int","v":"x"}}""", """{"a":{"t":"color","v":1}}""", """{"a":1}""").forEachIndexed { i, text ->
            val f = file("damaged-$i.json")
            f.parentFile.mkdirs()
            f.writeText(text)
            val store = JsonFileStore(f)
            assertEquals(text, emptyMap<String, Any>(), store.all())
            assertNotNull(text, store.loadError)
            store.put("k", true)
            assertEquals(mapOf("k" to true), JsonFileStore(f).all())
            assertNull(JsonFileStore(f).also { it.all() }.loadError)
        }
    }

    @Test
    fun reloadReadsChangesMadeByAnotherInstance() {
        val a = JsonFileStore(file())
        val b = JsonFileStore(file())
        assertFalse(b.contains("k"))
        a.put("k", 1)
        assertNull(b["k"])
        b.reload()
        assertEquals(1, b["k"])
        assertTrue(b.contains("k"))
    }

    @Test
    fun returnedValuesAreDetachedCopies() {
        val store = JsonFileStore(file())
        val source = linkedSetOf("a")
        store.put("set", source)
        source.add("b")
        @Suppress("UNCHECKED_CAST")
        (store["set"] as MutableSet<String>).add("c")
        assertEquals(setOf("a"), store.getStringSet("set", null))
    }
}
