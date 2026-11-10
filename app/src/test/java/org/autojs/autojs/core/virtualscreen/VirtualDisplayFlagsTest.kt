package org.autojs.autojs.core.virtualscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flag table ported from Mika's ShellService.displayFlags (its DisplayFlagsTest.kt) is pinned
 * here: a wrong bit either makes createVirtualDisplay fail with SecurityException or, worse,
 * silently steals the main screen's focus.
 *
 * zh-CN: 固定从 Mika ShellService.displayFlags 移植的标志位表. 位值错误要么让建屏抛 SecurityException,
 * 要么更糟 —— 悄悄抢走主屏焦点.
 */
class VirtualDisplayFlagsTest {

    @Test
    fun baseFlagsDoNotDependOnTrustBeforeAndroid13() {
        assertEquals(0x1C9, VirtualDisplayFlags.BASE)
        assertEquals(VirtualDisplayFlags.BASE, VirtualDisplayFlags.forSdk(32, true))
        assertEquals(VirtualDisplayFlags.BASE, VirtualDisplayFlags.forSdk(32, false))
        assertEquals(VirtualDisplayFlags.BASE, VirtualDisplayFlags.forSdk(24, true))
    }

    @Test
    fun trustedFlagsOnAndroid13CreateAnOwnDisplayGroup() {
        val flags = VirtualDisplayFlags.forSdk(33, true)
        assertEquals(0x3DC9, flags)
        assertTrue(flags and VirtualDisplayFlags.TRUSTED != 0)
        assertTrue(flags and VirtualDisplayFlags.OWN_DISPLAY_GROUP != 0)
        assertTrue(flags and VirtualDisplayFlags.ALWAYS_UNLOCKED != 0)
        assertTrue(flags and VirtualDisplayFlags.TOUCH_FEEDBACK_DISABLED != 0)
        assertFalse(flags and VirtualDisplayFlags.OWN_FOCUS != 0)
    }

    @Test
    fun untrustedFlagsOnAndroid13DropEveryTrustedOnlyBit() {
        val flags = VirtualDisplayFlags.forSdk(33, false)
        assertEquals(0x21C9, flags)
        listOf(
            VirtualDisplayFlags.TRUSTED,
            VirtualDisplayFlags.OWN_DISPLAY_GROUP,
            VirtualDisplayFlags.ALWAYS_UNLOCKED,
            VirtualDisplayFlags.OWN_FOCUS,
            VirtualDisplayFlags.STEAL_TOP_FOCUS_DISABLED,
        ).forEach { assertEquals("flag $it must be dropped", 0, flags and it) }
    }

    @Test
    fun android14AddsOwnFocusTogetherWithStealTopFocusDisabled() {
        val flags = VirtualDisplayFlags.forSdk(34, true)
        assertEquals(0x17DC9, flags)
        assertTrue(flags and VirtualDisplayFlags.OWN_FOCUS != 0)
        assertTrue(flags and VirtualDisplayFlags.STEAL_TOP_FOCUS_DISABLED != 0)
        assertEquals(flags, VirtualDisplayFlags.forSdk(35, true))
    }

    @Test
    fun untrustedFlagsOnAndroid14StayTheSameAsAndroid13() {
        val flags = VirtualDisplayFlags.forSdk(34, false)
        assertEquals(0x21C9, flags)
        assertEquals(VirtualDisplayFlags.forSdk(33, false), flags)
    }

    @Test
    fun describeReportsOnlySetBits() {
        val names = VirtualDisplayFlags.describe(VirtualDisplayFlags.forSdk(34, true))
        assertTrue(names.contains("public"))
        assertTrue(names.contains("trusted"))
        assertTrue(names.contains("ownFocus"))
        assertTrue(names.contains("stealTopFocusDisabled"))
        assertFalse(VirtualDisplayFlags.isTrusted(VirtualDisplayFlags.forSdk(34, false)))
    }

}
