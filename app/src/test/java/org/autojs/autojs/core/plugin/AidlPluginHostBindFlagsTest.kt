package org.autojs.autojs.core.plugin

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `bindService` flags of plugin bindings (3-Macaw Speech roadmap D22, P1.2): `BIND_INCLUDE_CAPABILITIES` is opt-in
 * and only from API 29, `BIND_ALLOW_ACTIVITY_STARTS` only from API 34, `BIND_IMPORTANT` is independent of the API level,
 * and a host that opts into nothing keeps the historical `BIND_AUTO_CREATE` binding.
 * zh-CN: 插件绑定的 `bindService` flags (3-Macaw Speech 路线图 D22, P1.2): `BIND_INCLUDE_CAPABILITIES` 需显式开启且只在 API 29 起生效,
 * `BIND_ALLOW_ACTIVITY_STARTS` 只在 API 34 起生效, `BIND_IMPORTANT` 与 API 级别无关, 未开启任何选项的宿主保持原有的 `BIND_AUTO_CREATE` 绑定.
 */
class AidlPluginHostBindFlagsTest {

    @Test
    fun theFrameworkConstantsAreTheOnesTheHostRelies() {
        assertEquals(0x1, Context.BIND_AUTO_CREATE)
        assertEquals(0x40, Context.BIND_IMPORTANT)
        assertEquals(0x200, Context.BIND_ALLOW_ACTIVITY_STARTS)
        assertEquals(0x1000, Context.BIND_INCLUDE_CAPABILITIES)
    }

    @Test
    fun aHostWithoutOptionsBindsWithAutoCreateOnly() {
        listOf(24, 28, 29, 33, 34, 37).forEach { sdk ->
            assertEquals("API $sdk", Context.BIND_AUTO_CREATE, AidlPluginHost.bindFlags(sdk, allowActivityStarts = false, includeCapabilities = false, important = false))
        }
    }

    @Test
    fun includeCapabilitiesAppliesFromApi29() {
        assertEquals(Context.BIND_AUTO_CREATE, AidlPluginHost.bindFlags(28, allowActivityStarts = false, includeCapabilities = true, important = false))
        listOf(29, 31, 33, 35, 36, 37).forEach { sdk ->
            assertEquals(
                "API $sdk",
                Context.BIND_AUTO_CREATE or Context.BIND_INCLUDE_CAPABILITIES,
                AidlPluginHost.bindFlags(sdk, allowActivityStarts = false, includeCapabilities = true, important = false),
            )
        }
    }

    @Test
    fun theSpeechHostCombinationAddsImportantOnEveryLevel() {
        assertEquals(Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT, AidlPluginHost.bindFlags(24, allowActivityStarts = false, includeCapabilities = true, important = true))
        assertEquals(
            Context.BIND_AUTO_CREATE or Context.BIND_INCLUDE_CAPABILITIES or Context.BIND_IMPORTANT,
            AidlPluginHost.bindFlags(33, allowActivityStarts = false, includeCapabilities = true, important = true),
        )
    }

    @Test
    fun activityStartsStayGatedAtApi34AndCombineWithTheOthers() {
        assertEquals(Context.BIND_AUTO_CREATE, AidlPluginHost.bindFlags(33, allowActivityStarts = true, includeCapabilities = false, important = false))
        assertEquals(
            Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS,
            AidlPluginHost.bindFlags(34, allowActivityStarts = true, includeCapabilities = false, important = false),
        )
        assertEquals(
            Context.BIND_AUTO_CREATE or Context.BIND_ALLOW_ACTIVITY_STARTS or Context.BIND_INCLUDE_CAPABILITIES or Context.BIND_IMPORTANT or EXTERNAL,
            AidlPluginHost.bindFlags(36, allowActivityStarts = true, includeCapabilities = true, important = true, externalServiceFlag = EXTERNAL),
        )
    }

    @Test
    fun theExternalServiceFlagPassesThroughUnchanged() {
        assertEquals(Context.BIND_AUTO_CREATE or EXTERNAL, AidlPluginHost.bindFlags(26, allowActivityStarts = false, includeCapabilities = false, important = false, externalServiceFlag = EXTERNAL))
    }

    private companion object {
        /** `Context.BIND_EXTERNAL_SERVICE` (read reflectively by the host because it is a system API). */
        const val EXTERNAL = 0x80000000.toInt()
    }
}
