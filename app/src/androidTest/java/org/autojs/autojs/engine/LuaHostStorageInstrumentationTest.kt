package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LuaHostStorageInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val firstPrincipal = LuaStoragePrincipal.fromStableIdentity("instrumentation-first-${System.nanoTime()}")
    private val secondPrincipal = LuaStoragePrincipal.fromStableIdentity("instrumentation-second-${System.nanoTime()}")
    private lateinit var storage: SharedPreferencesLuaHostStorage

    @Before
    fun setUp() {
        storage = SharedPreferencesLuaHostStorage(context)
        storage.clear(firstPrincipal)
        storage.clear(secondPrincipal)
    }

    @After
    fun tearDown() {
        storage.clear(firstPrincipal)
        storage.clear(secondPrincipal)
    }

    @Test
    fun valuesPersistAcrossBackendInstancesAndRemainPrincipalIsolated() {
        val encoded = LuaStorageContract.encodeStoredValue(
            LuaValue.MapValue(
                linkedMapOf(
                    "enabled" to LuaValue.BooleanValue(true),
                    "count" to LuaValue.Int64Value(7),
                    "labels" to LuaValue.ArrayValue(listOf(LuaValue.StringValue("保存"))),
                ),
            ),
        )

        storage.put(firstPrincipal, "settings.v1", encoded)

        val reopened = SharedPreferencesLuaHostStorage(context)
        assertArrayEquals(encoded, reopened.get(firstPrincipal, "settings.v1"))
        assertNull(reopened.get(secondPrincipal, "settings.v1"))
        assertTrue(reopened.remove(firstPrincipal, "settings.v1"))
        assertFalse(reopened.remove(firstPrincipal, "settings.v1"))
        assertNull(reopened.get(firstPrincipal, "settings.v1"))
    }

    @Test
    fun projectedPrincipalQuotaRejectsWithoutReplacingCommittedValues() {
        val large = LuaStorageContract.encodeStoredValue(nestedIntegerArrays(730))
        assertTrue(large.size <= LuaStorageContract.MAX_ENCODED_VALUE_BYTES)
        repeat(8) { index -> storage.put(firstPrincipal, "large_$index", large) }
        val original = LuaStorageContract.encodeStoredValue(LuaValue.StringValue("original"))
        storage.put(firstPrincipal, "replace", original)

        try {
            storage.put(firstPrincipal, "overflow", large)
            fail("Expected the projected principal quota to reject the write")
        } catch (_: LuaStorageQuotaExceededException) {
            // Expected: the rejected mutation must leave both old and new keys unchanged.
        }
        assertArrayEquals(original, storage.get(firstPrincipal, "replace"))
        assertNull(storage.get(firstPrincipal, "overflow"))
        assertEquals(9, storage.clear(firstPrincipal))
        assertEquals(0, storage.clear(firstPrincipal))
    }

    @Test
    fun concurrentWritersAreLinearizedWithoutLostEntries() {
        val executor = Executors.newFixedThreadPool(8)
        try {
            repeat(32) { index ->
                executor.submit {
                    storage.put(
                        firstPrincipal,
                        "key_$index",
                        LuaStorageContract.encodeStoredValue(LuaValue.Int64Value(index.toLong())),
                    )
                }
            }
        } finally {
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS))
        }

        repeat(32) { index ->
            val decoded = LuaStorageContract.decodeStoredValue(
                checkNotNull(storage.get(firstPrincipal, "key_$index")),
            )
            assertEquals(LuaValue.Int64Value(index.toLong()), decoded)
        }
        assertEquals(32, storage.clear(firstPrincipal))
    }

    private fun nestedIntegerArrays(entriesPerChild: Int) = LuaValue.ArrayValue(
        List(4) {
            LuaValue.ArrayValue(List(entriesPerChild) { index -> LuaValue.Int64Value(index.toLong()) })
        },
    )
}
