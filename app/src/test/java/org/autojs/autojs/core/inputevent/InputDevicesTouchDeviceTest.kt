package org.autojs.autojs.core.inputevent

import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.engine.RootAutomatorEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Touch screen node lookup of docs/shizuku-oom/ROADMAP.md D17 / P3.6: /proc/bus/input/devices first, `getevent -pl` when
 * the former is denied, and only a resolved node is cached.
 *
 * zh-CN: ROADMAP D17 / P3.6 的触摸屏节点解析: 先读 /proc/bus/input/devices, 被拒时改用 `getevent -pl`, 只缓存解析成功的节点.
 */
class InputDevicesTouchDeviceTest : DomainKeysTestBase() {

    private val commands = mutableListOf<String>()

    @After
    fun resetTouchDevice() {
        RootAutomatorEngine.setTouchDevice(-1)
    }

    private fun exec(proc: String?, getevent: String?): (String) -> String? = { cmd ->
        commands += cmd
        when {
            cmd.contains("/proc/bus/input/devices") -> proc
            cmd.contains("getevent -pl") -> getevent
            else -> throw AssertionError("unexpected command: $cmd")
        }
    }

    private fun resolveAndCache(exec: (String) -> String?) = RootAutomatorEngine.getCachedOrResolvedTouchDevice {
        InputDevices.resolveTouchDeviceId(TOUCH_SCREEN, exec)
    }

    @Test
    fun procSucceeds() {
        assertEquals(1, resolveAndCache(exec(PROC_INPUT_DEVICES, GETEVENT_DEVICES)))
        assertEquals(1, commands.size)
        assertEquals(1, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get())
    }

    @Test
    fun procDeniedAndGeteventSucceeds() {
        assertEquals(1, resolveAndCache(exec(null, GETEVENT_DEVICES)))
        assertEquals(2, commands.size)
        assertEquals(1, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get())

        // Cached: no command runs again.
        // zh-CN: 已缓存: 不再运行命令.
        assertEquals(1, resolveAndCache(exec(null, null)))
        assertEquals(2, commands.size)
    }

    @Test
    fun bothFailAndNothingIsCached() {
        assertEquals(-1, resolveAndCache(exec(null, null)))
        assertEquals(-1, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get())

        // Not cached: the next lookup runs the commands again and can succeed.
        // zh-CN: 未缓存: 下次解析重新运行命令, 可以成功.
        assertEquals(1, resolveAndCache(exec(null, GETEVENT_DEVICES)))
        assertEquals(4, commands.size)
        assertEquals(1, MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.get())
    }

    @Test
    fun unknownDeviceNameFallsThrough() {
        assertNull(InputDevices.parseProcInputDevices(PROC_INPUT_DEVICES, "unknown"))
        assertNull(InputDevices.parseGeteventDevices(GETEVENT_DEVICES, "unknown"))
        assertEquals(1, InputDevices.resolveTouchDeviceId(TOUCH_SCREEN, exec(PROC_INPUT_DEVICES.replace(TOUCH_SCREEN, "other"), GETEVENT_DEVICES)))
        assertEquals(0, InputDevices.parseGeteventDevices(GETEVENT_DEVICES, "Power Button"))
    }

    private companion object {

        const val TOUCH_SCREEN = "qwerty2"

        // As root on an android-28 x86_64 emulator (the shell user is denied).
        // zh-CN: android-28 x86_64 模拟器上以 root 读取 (shell 用户被拒).
        val PROC_INPUT_DEVICES = """
            I: Bus=0019 Vendor=0000 Product=0001 Version=0000
            N: Name="Power Button"
            P: Phys=LNXPWRBN/button/input0
            S: Sysfs=/devices/LNXSYSTM:00/LNXPWRBN:00/input/input0
            U: Uniq=
            H: Handlers=kbd event0
            B: PROP=0
            B: EV=3
            B: KEY=10000000000000 0

            I: Bus=0019 Vendor=0000 Product=0000 Version=0000
            N: Name="qwerty2"
            P: Phys=
            S: Sysfs=/devices/platform/GFSH0002:00/input/input1
            U: Uniq=
            H: Handlers=kbd event1
            B: PROP=0
            B: EV=2b
        """.trimIndent()

        // `getevent -pl` as the shell user on the same emulator (excerpt).
        // zh-CN: 同一模拟器上 shell 用户运行 `getevent -pl` 的输出 (节选).
        val GETEVENT_DEVICES = """
            add device 1: /dev/input/event0
              name:     "Power Button"
              events:
                KEY (0001): KEY_POWER
              input props:
                <none>
            add device 2: /dev/input/event1
              name:     "qwerty2"
              events:
                KEY (0001): KEY_HOME              KEY_END               KEY_VOLUMEDOWN        KEY_VOLUMEUP
                ABS (0003): ABS_X                 : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_X     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
                            ABS_MT_POSITION_Y     : value 0, min 0, max 32767, fuzz 0, flat 0, resolution 0
              input props:
                <none>
        """.trimIndent()
    }
}
