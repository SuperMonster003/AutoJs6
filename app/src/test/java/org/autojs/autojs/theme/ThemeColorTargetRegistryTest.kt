package org.autojs.autojs.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ThemeColorTargetRegistryTest {

    @Test fun registrationDuringNotificationDoesNotInvalidateTheCurrentTraversal() {
        val registry = ThemeColorTargetRegistry<String> { true }
        registry.add("parent")
        registry.add("tail")
        val first = mutableListOf<String>()
        registry.forEach {
            first += it
            if (it == "parent") registry.add("child")
        }
        assertEquals(listOf("parent", "tail"), first)
        val second = mutableListOf<String>()
        registry.forEach { second += it }
        assertEquals(listOf("parent", "tail", "child"), second)
    }

    @Test fun nestedNotificationsAndDeadReferenceCleanupKeepRegistrations() {
        data class Target(val name: String, var alive: Boolean = true)
        val registry = ThemeColorTargetRegistry<Target> { it.alive }
        val dead = Target("dead", false)
        val parent = Target("parent")
        val tail = Target("tail")
        listOf(dead, parent, tail).forEach(registry::add)
        val outer = mutableListOf<String>()
        val inner = mutableListOf<String>()
        registry.forEach {
            outer += it.name
            if (it === parent) {
                registry.add(Target("child"))
                registry.forEach { nested -> inner += nested.name }
            }
        }
        assertEquals(listOf("parent", "tail"), outer)
        assertEquals(listOf("parent", "tail", "child"), inner)
        dead.alive = true
        val after = mutableListOf<String>()
        registry.forEach { after += it.name }
        assertEquals("Dead references must be removed, not just skipped", inner, after)
    }

    @Test(timeout = 5000) fun callbacksDoNotHoldTheRegistrationLock() {
        val registry = ThemeColorTargetRegistry<Int> { true }
        registry.add(1)
        val executor = Executors.newSingleThreadExecutor()
        val registered = CountDownLatch(1)
        try {
            registry.forEach {
                executor.submit { registry.add(2); registered.countDown() }
                assertTrue("Registration from another thread must finish during a callback", registered.await(2, TimeUnit.SECONDS))
            }
            val values = mutableListOf<Int>()
            registry.forEach { values += it }
            assertEquals(listOf(1, 2), values)
        } finally {
            executor.shutdownNow()
        }
    }
}
