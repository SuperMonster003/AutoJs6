package org.autojs.autojs.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PluginAutoWake.bindServiceWaking] unbinds a binding the system refused or that threw, since `bindService` registers
 * the connection before the system answers (docs/memory-phase2/ROADMAP.md C43); PluginBindReleaseDeviceTest checks it
 * against the framework.
 */
class PluginAutoWakeUnbindTest {

    private var unbinds = 0

    private fun bindOnce(bind: () -> Boolean) = PluginAutoWake.unbindUnlessBound({ unbinds++ }, bind)

    @Test
    fun aBindingThatHoldsTheConnectionIsLeftToTheCaller() {
        assertTrue(bindOnce { true })
        assertEquals(0, unbinds)
    }

    @Test
    fun aRefusedBindingIsUnboundOnce() {
        assertFalse(bindOnce { false })
        assertEquals(1, unbinds)
    }

    @Test
    fun aBindingThatThrewIsUnboundAndTheErrorPropagates() {
        val denied = SecurityException("denied")
        assertSame(denied, assertThrows(SecurityException::class.java) { bindOnce { throw denied } })
        assertEquals(1, unbinds)
        // A wake retry interrupted after a refused attempt. zh-CN: 被拒之后的唤醒重试被中断.
        assertThrows(InterruptedException::class.java) { bindOnce { throw InterruptedException() } }
        assertEquals(2, unbinds)
    }

    @Test
    fun unbindingAConnectionThatWasNeverRegisteredIsIgnored() {
        val notRegistered: () -> Unit = {
            unbinds++
            throw IllegalArgumentException("Service not registered")
        }
        assertFalse(PluginAutoWake.unbindUnlessBound(notRegistered) { false })
        val denied = SecurityException("denied")
        assertSame(denied, assertThrows(SecurityException::class.java) {
            PluginAutoWake.unbindUnlessBound(notRegistered) { throw denied }
        })
        assertEquals(2, unbinds)
    }
}
