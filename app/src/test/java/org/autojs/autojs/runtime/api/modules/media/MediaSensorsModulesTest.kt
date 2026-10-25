package org.autojs.autojs.runtime.api.modules.media

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.differential.ObsoleteModuleHost
import org.autojs.autojs.runtime.api.augment.differential.SurfaceDiff
import org.autojs.autojs.runtime.api.augment.media.Media
import org.autojs.autojs.runtime.api.augment.sensors.Sensors
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport
import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.autojs.autojs.runtime.api.Media as ApiMedia
import org.autojs.autojs.runtime.api.Sensors as ApiSensors

/**
 * Roadmap P3.5: `media` and `sensors` on the new script module layer, objects without own members whose prototypes are
 * the runtime's Java objects. The Java objects need Android to be created, so the JVM uses instances allocated without
 * their constructors (only their classes matter for the surface): the surface against the current
 * `augment/media/Media` / `augment/sensors/Sensors` and the obsolete `__media__.js` (whose object inherited from the same
 * Java object), and that installing builds neither module. Calls run on the device.
 *
 * zh-CN: P3.5: 新层的 media 与 sensors, 没有自有成员, 原型为运行时的 Java 对象. 这两个 Java 对象需要 Android 才能创建, 因此 JVM 上使用不经构造函数分配的实例
 * (表面只取决于类): 表面对照现有 augment/media/Media / augment/sensors/Sensors 与旧 __media__.js (其对象同样继承该 Java 对象), 以及安装时不构建模块. 调用在真机
 * 运行.
 */
class MediaSensorsModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        prepare(rhino)
        rhino.mountNewModules("media", "sensors")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun prepare(side: DifferentialRhino) {
        HostModuleTestSupport.setField(side.ktRuntime, "media", DifferentialRhino.allocateWithoutConstructor(ApiMedia::class.java))
        HostModuleTestSupport.setField(side.ktRuntime, "sensors", DifferentialRhino.allocateWithoutConstructor(ApiSensors::class.java))
    }

    private fun mountCurrent(side: DifferentialRhino) {
        val global = side.ktScope.globalThis as ScriptableObject
        Media.augmentWithRuntime(global, side.ktRuntime, side.ktRuntime.media)
        Sensors.augmentWithRuntime(global, side.ktRuntime, side.ktRuntime.sensors)
    }

    @Test
    fun installingBuildsNothing() {
        listOf("media", "\$media", "sensors", "\$sensors").forEach { assertTrue(it, LazyDataSlot.isPending(rhino.ktScope.globalThis, it)) }
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        HostModuleTestSupport.assertSameSurface(rhino, "media", ::prepare, ::mountCurrent)
        HostModuleTestSupport.assertSameSurface(rhino, "sensors", ::prepare, ::mountCurrent)
        assertEquals("[]", rhino.evalKt("JSON.stringify(Object.getOwnPropertyNames(media).concat(Object.getOwnPropertyNames(sensors)))"))
    }

    @Test
    fun obsoleteMembersExistOnTheNewModule() {
        val jsMedia = ScriptModuleExpectations.obsoleteSurface("surface: media") {
            val host = ObsoleteModuleHost(rhino.context, rhino.jsScope)
            host.setRuntimeProperty("media", rhino.ktRuntime.media)
            SurfaceDiff.surface(host.load("media"), rhino.jsScope.globalThis)
        }
        val report = SurfaceDiff.compare(jsMedia, SurfaceDiff.surface(rhino.evalKt("media"), rhino.ktScope.globalThis))
        assertTrue(SurfaceDiff.describe(report, "js", "kt"), report.onlyInFirst.isEmpty() && report.typeMismatches.isEmpty())
    }

    /**
     * Calls reach the same Java objects on both layers (P3.6): member types, the public field, and the errors of Rhino's
     * Java method resolution (no matching overload) or of the host give the same results as on the current modules.
     */
    @Test
    fun callsGiveTheSameResultsAsTheCurrentModules() {
        val compared = HostModuleTestSupport.assertSameResults(rhino, CALLS, emptyMap(), ::prepare, ::mountCurrent)
        assertEquals(CALLS.size, compared)
    }

    @Test
    fun deviceOnlyCases() {
        DEVICE.add(DEVICE_ONLY)
    }

    companion object {

        private val DEVICE = DeviceCases("MediaSensorsModulesTest", "new script module layer (P3.5)")

        private val CALLS = listOf(
            "[typeof media.playMusic, typeof media.scanFile, typeof media.isMusicPlaying, typeof media.stopMusic]",
            "[typeof sensors.register, typeof sensors.unregister, typeof sensors.on, typeof sensors.delay]",
            "sensors.ignoresUnsupportedSensor",
            "media.playMusic()",
            "media.scanFile()",
            "media.musicSeekTo('x')",
            "sensors.register()",
            "sensors.unregister()",
        )

        private val DEVICE_ONLY = listOf(
            "[typeof media, typeof \$media, media === \$media, Object.keys(media).length]" to "[\"object\",\"object\",true,0]",
            "String(Object.getPrototypeOf(media).getClass().getName())" to "\"org.autojs.autojs.runtime.api.Media\"",
            "[typeof media.playMusic, typeof media.scanFile, typeof media.isMusicPlaying]" to "[\"function\",\"function\",\"function\"]",
            "media.isMusicPlaying()" to "false",
            "media.scanFile(files.cwd())" to "undefined",
            "[typeof sensors, typeof \$sensors, sensors === \$sensors, Object.keys(sensors).length]" to "[\"object\",\"object\",true,0]",
            "String(Object.getPrototypeOf(sensors).getClass().getName())" to "\"org.autojs.autojs.runtime.api.Sensors\"",
            "[typeof sensors.register, typeof sensors.unregister, typeof sensors.on, typeof sensors.delay]" to "[\"function\",\"function\",\"function\",\"object\"]",
            "(function () { var e = sensors.register('accelerometer'); var r = e !== null; if (e) sensors.unregister(e); return r; })()" to "true",
            "sensors.register('no-such-sensor')" to "null",
        )

    }

}
