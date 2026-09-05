package org.autojs.autojs.core.accessibility.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreStartStrategyTest {

    private val self = "org.autojs.autojs6/.Usher"
    private val other = "com.example.other/.Service"

    private class MemoryStore(initial: String?) : EnabledServicesStore {
        var value: String? = initial
        val writes = mutableListOf<String>()
        var failWrites = false

        override fun read(): String? = value

        override fun write(value: String) {
            if (failWrites) throw IllegalStateException("write refused")
            writes += value
            this.value = value
        }
    }

    private val logs = mutableListOf<String>()

    private fun strategy(store: MemoryStore, available: Boolean = true) =
        SettingsStoreStartStrategy("memory", self, store, available = { available }, log = { logs += it })

    @Test
    fun enableAppendsTheServiceWithASingleWrite() {
        val store = MemoryStore(other)
        assertTrue(strategy(store).enable())
        assertEquals(listOf("$other:$self"), store.writes)
    }

    @Test
    fun enableRewritesAListedServiceInTwoSteps() {
        // A listed but unbound service is only re-bound when the system sees it leave and re-enter the list.
        val store = MemoryStore("$self:$other")
        assertTrue(strategy(store).enable())
        assertEquals(listOf(other, "$other:$self"), store.writes)
    }

    @Test
    fun enableStartsFromAnEmptyOrUnsetList() {
        val store = MemoryStore(null)
        assertTrue(strategy(store).enable())
        assertEquals(listOf(self), store.writes)
    }

    @Test
    fun disableRemovesTheServiceEvenWhenItIsTheOnlyOne() {
        val store = MemoryStore(self)
        assertTrue(strategy(store).disable())
        assertEquals(listOf(""), store.writes)
        assertFalse(EnabledA11yServices.contains(store.value, self))
    }

    @Test
    fun resultsComeFromReadingBackNotFromTheWrites() {
        val store = MemoryStore(other)
        val readBackSaysNo = SettingsStoreStartStrategy("memory", self, store, available = { true }, readBack = { other })
        assertFalse(readBackSaysNo.enable())
        assertEquals("the write still happened", "$other:$self", store.value)

        val readBackSaysStillListed = SettingsStoreStartStrategy("memory", self, store, available = { true }, readBack = { self })
        assertFalse(readBackSaysStillListed.disable())
    }

    @Test
    fun failuresAreReportedAsFalseAndLogged() {
        val store = MemoryStore(other).apply { failWrites = true }
        val strategy = strategy(store)
        assertFalse(strategy.enable())
        assertFalse(strategy.disable())
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.startsWith("memory: unable to") })
    }

    @Test
    fun availabilityErrorsCountAsUnavailable() {
        val store = MemoryStore(other)
        val strategy = SettingsStoreStartStrategy("memory", self, store, available = { throw IllegalStateException("no shell") }, log = { logs += it })
        assertFalse(strategy.isAvailable())
        assertTrue(strategy(store, available = false).isAvailable().not())
        assertTrue(strategy(store).isAvailable())
    }

}
