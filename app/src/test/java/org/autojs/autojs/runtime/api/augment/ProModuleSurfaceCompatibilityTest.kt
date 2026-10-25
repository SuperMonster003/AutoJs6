package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.timers.Timers
import org.autojs.autojs.runtime.api.modules.floaty.Floaty
import org.autojs.autojs.runtime.api.modules.http.Http
import org.autojs.autojs.runtime.api.modules.images.Images
import org.autojs.autojs.runtime.api.modules.images.ObjectFrame
import org.autojs.autojs.runtime.api.modules.tasks.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point
import java.lang.reflect.Modifier

class ProModuleSurfaceCompatibilityTest {

    @Test
    fun proEntryPointsAreExposedAsJvmStaticMethods() {
        assertJvmStaticMethods(Floaty::class.java, "checkPermission")
        assertJvmStaticMethods(Http::class.java, "client")
        assertJvmStaticMethods(Auto::class.java, "getWindowRoot")
        assertJvmStaticMethods(Automator::class.java, "switchToInputMethod", "switchToInputMethodWithId")
        assertJvmStaticMethods(Images::class.java, "__buildRegion")
        assertJvmStaticMethods(
            WorkManager::class.java,
            "addDailyTask",
            "addWeeklyTask",
            "addDisposableTask",
            "addIntentTask",
            "addBroadcastIntentTask",
            "getTimedTask",
            "getIntentTask",
            "removeIntentTask",
            "removeTimedTask",
            "queryIntentTasks",
            "queryTimedTasks",
        )
        assertJvmStaticMethods(
            Timers::class.java,
            "addDailyTask",
            "addWeeklyTask",
            "addDisposableTask",
            "addIntentTask",
            "getTimedTask",
            "getIntentTask",
            "removeIntentTask",
            "removeTimedTask",
            "queryIntentTasks",
            "queryTimedTasks",
        )
    }

    @Test
    fun proBuildRegionDefaultsMissingSizeComponents() {
        val region = Images.buildRegionForDimensions(listOf(10, 20), 100, 80)

        assertEquals(10, region.x)
        assertEquals(20, region.y)
        assertEquals(90, region.width)
        assertEquals(60, region.height)
    }

    @Test
    fun objectFrameCalculatesItsCenterFromAllCorners() {
        val frame = ObjectFrame(
            topLeft = Point(2.0, 4.0),
            topRight = Point(10.0, 4.0),
            bottomLeft = Point(2.0, 12.0),
            bottomRight = Point(10.0, 12.0),
        )

        assertEquals(6.0, frame.centerX, 0.0)
        assertEquals(8.0, frame.centerY, 0.0)
        assertEquals(6.0, frame.center.x, 0.0)
        assertEquals(8.0, frame.center.y, 0.0)
    }

    private fun assertJvmStaticMethods(type: Class<*>, vararg methodNames: String) {
        val staticMethodNames = type.declaredMethods
            .filter { Modifier.isStatic(it.modifiers) }
            .mapTo(mutableSetOf()) { it.name }
        methodNames.forEach { methodName ->
            assertTrue("${type.simpleName}.$methodName must be exposed as a JVM static method", methodName in staticMethodNames)
        }
    }
}
