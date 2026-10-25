package org.autojs.autojs.runtime.api.modules.notice

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.runtime.api.Notice as ApiNotice

/**
 * Roadmap P3.2 (group 1): `notice` and `notice.channel` on the new script module layer. Notifications and channels need
 * Android, so the JVM covers the surface against the current `augment/notice/Notice` + `Channel`, the argument errors,
 * which the new layer raises before it calls the current module (T4; the current module threw IllegalArgumentException,
 * WrappedIllegalArgumentException or sent the test notification), and `notice.config`, which writes the runtime's
 * configuration (all keys checked first). The notifications run on the device, silent and cancelled at once, on a
 * channel of their own that the last case removes.
 *
 * zh-CN: P3.2 (第 1 组): 新层的 notice 与 notice.channel. 通知与渠道需要 Android, 因此 JVM 覆盖表面 (对照现有 augment/notice/Notice + Channel), 参数错误
 * (新层在调用现有模块之前抛出; T4; 现有模块抛 IllegalArgumentException, WrappedIllegalArgumentException 或发送测试通知), 以及写入运行时配置的 notice.config
 * (先检查全部键). 通知在真机发送, 静音并随即消除, 使用专用渠道, 最后一个用例删除该渠道.
 */
class NoticeModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("notice")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) {
        HostModuleTestSupport.setField(side.ktRuntime, "notice", ApiNotice(side.ktRuntime))
    }

    private fun mountCurrent(side: DifferentialRhino) {
        val notice = Notice(side.ktRuntime).augment(side.ktScope.globalThis as ScriptableObject)
        Channel(side.ktRuntime).augment(notice)
    }

    @Test
    fun installingBuildsNothing() {
        listOf("notice", "\$notice").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        HostModuleTestSupport.assertSameSurface(rhino, "notice", ::prepare, ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "notice.channel", ::prepare, ::mountCurrent)
        HostModuleTestSupport.check(rhino, SURFACE)
        DEVICE.add(SURFACE)
    }

    @Test
    fun argumentErrors() {
        HostModuleTestSupport.check(rhino, ERRORS)
        DEVICE.add(ERRORS)
    }

    @Test
    fun configChecksEveryKeyBeforeItChangesAny() {
        val config = rhino.ktRuntime.notice.config
        HostModuleTestSupport.check(rhino, listOf("notice.config({ defaultTitle: 'T', nope: 1 })" to RANGE, "notice.config({ defaultTitle: 'T', defaultPriority: 'x' })" to RANGE))
        assertNull(config.defaultTitle)
        assertNull(config.defaultPriority)
        HostModuleTestSupport.check(
            rhino, listOf(
                "notice.config({ defaultTitle: 'T' + 1, defaultPriority: 'HIGH', defaultAppendScriptName: 'BIGCONTENT', useDynamicDefaultNotificationId: false })" to "undefined",
                "notice.config({ defaultVibrationPatternForChannel: [0, 100, 50.0], defaultLockscreenVisibilityForChannel: 'no_override', defaultImportanceForChannel: 2 })" to "undefined",
            )
        )
        assertEquals("T1", config.defaultTitle)
        assertEquals(1, config.defaultPriority)
        assertEquals("bigContent", config.defaultAppendScriptName)
        assertEquals(false, config.useDynamicDefaultNotificationId)
        assertArrayEquals(longArrayOf(0, 100, 50), config.defaultVibrationPatternForChannel)
        assertEquals(-1000, config.defaultLockscreenVisibilityForChannel)
        assertEquals(2, (config.defaultImportanceForChannel as Number).toInt())
        HostModuleTestSupport.check(rhino, listOf("notice.config({ defaultTitle: null, useDynamicDefaultNotificationId: undefined, defaultVibrationPatternForChannel: null })" to "undefined"))
        assertNull(config.defaultTitle)
        assertEquals(true, config.useDynamicDefaultNotificationId)
        assertNull(config.defaultVibrationPatternForChannel)
    }

    /**
     * Every member of the obsolete `__notice__.js` (P3.6) exists on the new layer with the same type, on `notice` and on
     * `notice.channel` ([SurfaceDiff]; members only the new layer has are additions). The obsolete module only asked the
     * application context for the NotificationManager when loaded, so a stand-in `context` answers null.
     */
    @Test
    fun obsoleteMembersExistOnTheNewModules() {
        val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
        rhino.evalJs("global.context = { getSystemService: function () { return null; } }")
        val obsolete = host.load("notice") as ScriptableObject
        listOf(
            "notice" to (obsolete to "notice"),
            "notice.channel" to (ScriptableObject.getProperty(obsolete, "channel") to "notice.channel"),
        ).forEach { (name, sides) ->
            val (js, kt) = sides
            val report = SurfaceDiff.compare(SurfaceDiff.surface(js, rhino.jsScope.globalThis), SurfaceDiff.surface(rhino.evalKt(kt), rhino.ktScope.globalThis))
            assertTrue("$name: " + SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
        }
        assertEquals("function", rhino.evalKt("typeof notice"))
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("NoticeModulesTest", "new script module layer (P3.2)")

        private const val TYPE = "throws TypeError"
        private const val RANGE = "throws RangeError"

        private val SURFACE = listOf(
            "[typeof notice, typeof \$notice, notice === \$notice, typeof notice.channel, typeof \$notice.channel, typeof notice.getBuilder]" to
                "[\"function\",\"function\",true,\"object\",\"object\",\"function\"]",
            "Object.keys(notice).join()" to "\"isEnabled,ensureEnabled,launchSettings,config,cancel,getBuilder,builder,channel\"",
            "Object.keys(notice.channel).join()" to "\"create,createIfNeeded,remove,contains,get,getAll\"",
        )

        private val ERRORS = listOf(
            "notice(1)" to TYPE,
            "notice(true)" to TYPE,
            "notice([])" to TYPE,
            "notice(function () {})" to TYPE,
            "notice('a', 1)" to TYPE,
            "notice('a', [])" to TYPE,
            "notice({}, 'x')" to TYPE,
            "notice('a', 'b', 1)" to TYPE,
            "notice('a', 1, {})" to TYPE,
            "notice('a', 'b', {}, 1)" to TYPE,
            "notice({ priority: 'x' })" to RANGE,
            "notice({ priority: {} })" to TYPE,
            "notice('c', { priority: 'nope' })" to RANGE,
            "notice('t', 'c', { priority: true })" to TYPE,
            "notice({ appendScriptName: 'x' })" to RANGE,
            "notice({ appendScriptName: 1 })" to TYPE,
            "notice({ isSilent: 'yes' })" to TYPE,
            "notice({ autoCancel: 1 })" to TYPE,
            "notice({ notificationId: 'x' })" to TYPE,
            "notice({ notificationId: Infinity })" to RANGE,
            "notice({ channelId: {} })" to TYPE,
            "notice({ intent: 1 })" to TYPE,
            "notice.isEnabled(1)" to TYPE,
            "notice.ensureEnabled(1)" to TYPE,
            "notice.launchSettings(1)" to TYPE,
            "notice.getBuilder(1)" to TYPE,
            "notice.config()" to TYPE,
            "notice.config(1)" to TYPE,
            "notice.config([])" to TYPE,
            "notice.config({}, 1)" to TYPE,
            "notice.config({ nope: 1 })" to RANGE,
            "notice.config({ defaultTitle: 1 })" to TYPE,
            "notice.config({ useDynamicDefaultNotificationId: 'x' })" to TYPE,
            "notice.config({ defaultIsSilent: 0 })" to TYPE,
            "notice.config({ defaultAppendScriptName: 'x' })" to RANGE,
            "notice.config({ defaultPriority: 'x' })" to RANGE,
            "notice.config({ defaultImportanceForChannel: 'x' })" to RANGE,
            "notice.config({ defaultLockscreenVisibilityForChannel: 'x' })" to RANGE,
            "notice.config({ defaultVibrationPatternForChannel: 'x' })" to TYPE,
            "notice.config({ defaultVibrationPatternForChannel: [1, 'a'] })" to TYPE,
            "notice.config({ defaultVibrationPatternForChannel: [1.5] })" to RANGE,
            "notice.cancel()" to TYPE,
            "notice.cancel(null)" to TYPE,
            "notice.cancel('x')" to TYPE,
            "notice.cancel(NaN)" to RANGE,
            "notice.cancel(Infinity)" to RANGE,
            "notice.cancel(1, 2)" to TYPE,
            "notice.channel.create(true)" to TYPE,
            "notice.channel.create({}, 'x')" to TYPE,
            "notice.channel.create('id', 1)" to TYPE,
            "notice.channel.create('id', [])" to TYPE,
            "notice.channel.create('a', {}, 'c')" to TYPE,
            "notice.channel.create({ id: {} })" to TYPE,
            "notice.channel.create({ importance: 'x' })" to RANGE,
            "notice.channel.create('id', { importance: {} })" to TYPE,
            "notice.channel.create({ lockscreenVisibility: 'x' })" to RANGE,
            "notice.channel.create({ vibrationPattern: 'x' })" to TYPE,
            "notice.channel.create({ enableLights: 'x' })" to TYPE,
            "notice.channel.create({ enableVibration: 1 })" to TYPE,
            "notice.channel.createIfNeeded({}, 1)" to TYPE,
            "notice.channel.createIfNeeded({ channelId: [] })" to TYPE,
            "notice.channel.createIfNeeded('id', { importance: 'x' })" to RANGE,
            "notice.channel.remove()" to TYPE,
            "notice.channel.remove(null)" to TYPE,
            "notice.channel.contains(undefined)" to TYPE,
            "notice.channel.get({})" to TYPE,
            "notice.channel.get('a', 'b')" to TYPE,
            "notice.channel.getAll(1)" to TYPE,
        )

        private const val CHANNEL = "aj6-p32"

        private val DEVICE_ONLY = listOf(
            "typeof notice.isEnabled()" to "\"boolean\"",
            "String(notice.getBuilder().getClass().getName())" to "\"androidx.core.app.NotificationCompat\$Builder\"",
            "String(notice.builder.getClass().getName())" to "\"androidx.core.app.NotificationCompat\$Builder\"",
            "notice.channel.create('$CHANNEL', { name: 'aj6 p32', importance: 'low' })" to "\"$CHANNEL\"",
            "notice.channel.contains('$CHANNEL')" to "true",
            "String(notice.channel.get('$CHANNEL').getId())" to "\"$CHANNEL\"",
            "notice.channel.getAll().some(function (c) { return String(c.getId()) === '$CHANNEL'; })" to "true",
            "notice.channel.createIfNeeded('$CHANNEL', { name: 'ignored' })" to "undefined",
            "notice.channel.createIfNeeded({ channelId: '$CHANNEL' })" to "undefined",
            notified("notice('aj6-p32 content', { notificationId: 3201, isSilent: true, channelId: '$CHANNEL' })") to "3201",
            notified("notice('aj6-p32 title', 'content', { notificationId: 3202, isSilent: true, channelId: '$CHANNEL' })") to "3202",
            notified("notice({ title: 'aj6-p32', notificationId: 3203, isSilent: true, channelId: '$CHANNEL', priority: 'LOW' })") to "3203",
            // The current module did not recognize a builder behind its Java wrapper and sent the test notification.
            notified("notice(notice.getBuilder().setContentTitle('aj6-p32 builder'), { notificationId: 3204, isSilent: true, channelId: '$CHANNEL' })") to "3204",
            // The current module threw for the documented target 'bigContent'.
            notified("notice({ title: 'aj6-p32', bigContent: 'long', appendScriptName: 'bigContent', notificationId: 3205, isSilent: true, channelId: '$CHANNEL' })") to "3205",
            "notice.cancel(3201.4)" to "undefined",
            "notice.channel.create('$CHANNEL-v', { lockscreenVisibility: 'no_override' })" to "\"$CHANNEL-v\"",
            "notice.channel.remove('$CHANNEL-v')" to "true",
            "notice.channel.remove('$CHANNEL')" to "true",
            // Not the same expression as the check after create: device cases are keyed by their expression.
            "!notice.channel.contains('$CHANNEL')" to "true",
        )

        /** Sends a notification with [expression] and cancels it at once; the value is the notification id. */
        private fun notified(expression: String) = "(function () { var id = $expression; notice.cancel(id); return id; })()"

    }

}
