package org.autojs.autojs.event

import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.RecordKeys
import org.autojs.autojs.core.pref.keys.RunningKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When [GlobalKeyObserver.initIfNeeded] creates the singleton (docs/shizuku-oom/ROADMAP.md P3.1): at once when a volume
 * control setting is on, otherwise once one is turned on while the app runs.
 *
 * zh-CN: [GlobalKeyObserver.initIfNeeded] 何时创建单例 (docs/shizuku-oom/ROADMAP.md P3.1): 音量键控制设置开启时立即创建,
 * 否则在应用运行中打开某项设置时创建.
 */
class VolumeControlSettingsTest : DomainKeysTestBase() {

    private fun turnBothOff() {
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(false)
        RecordKeys.USE_VOLUME_CONTROL_RECORD.set(false)
        assertFalse(VolumeControlSettings.isEnabled())
    }

    @Test
    fun bothSettingsAreOnByDefault() {
        assertTrue(VolumeControlSettings.isEnabled())
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(false)
        assertTrue(VolumeControlSettings.isEnabled())
    }

    @Test
    fun anEnabledSettingFiresAtOnce() {
        RecordKeys.USE_VOLUME_CONTROL_RECORD.set(false)
        var calls = 0
        VolumeControlSettings.whenEnabled { calls++ }
        assertEquals(1, calls)
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(false)
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(true)
        assertEquals(1, calls)
    }

    @Test
    fun nothingFiresWhileBothSettingsStayOff() {
        turnBothOff()
        var calls = 0
        VolumeControlSettings.whenEnabled { calls++ }
        RecordKeys.USE_VOLUME_CONTROL_RECORD.set(false)
        store.put("key_unrelated_to_volume_control", true)
        assertEquals(0, calls)
    }

    @Test
    fun turningEitherSettingOnFiresOnce() {
        for (key in listOf(RunningKeys.USE_VOLUME_CONTROL_RUNNING, RecordKeys.USE_VOLUME_CONTROL_RECORD)) {
            turnBothOff()
            var calls = 0
            VolumeControlSettings.whenEnabled { calls++ }
            key.set(true)
            assertEquals(key.name, 1, calls)
            key.set(false)
            key.set(true)
            assertEquals(key.name, 1, calls)
        }
    }

    @Test
    fun clearingTheStoreRestoresTheDefaultsAndFires() {
        turnBothOff()
        var calls = 0
        VolumeControlSettings.whenEnabled { calls++ }
        store.clear()
        assertEquals(1, calls)
    }

    @Test
    fun closingCancelsThePendingCall() {
        turnBothOff()
        var calls = 0
        VolumeControlSettings.whenEnabled { calls++ }.close()
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(true)
        assertEquals(0, calls)
    }

    @Test
    fun listenFollowsBothSettingsUntilClosed() {
        var calls = 0
        val subscription = VolumeControlSettings.listen { calls++ }
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(false)
        RecordKeys.USE_VOLUME_CONTROL_RECORD.set(false)
        store.put("key_unrelated_to_volume_control", true)
        assertEquals(2, calls)
        subscription.close()
        RunningKeys.USE_VOLUME_CONTROL_RUNNING.set(true)
        assertEquals(2, calls)
    }
}
