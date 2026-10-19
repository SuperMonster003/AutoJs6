package org.autojs.autojs.runtime.api

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The top activity asked through the Shizuku server, which the floating button "More" menu uses before the user service.
 * zh-CN: 经 Shizuku 服务端查询的顶部 Activity, 浮动按钮 "更多" 菜单在用户服务之前使用它.
 */
@RunWith(AndroidJUnit4::class)
class WrappedShizukuTopActivityInstrumentationTest {

    // Needs no Shizuku, so that every Android version the host runs on can check its getTasks signature.
    // zh-CN: 无需 Shizuku, 以便宿主支持的每个 Android 版本都能检查其 getTasks 签名.
    @Test
    fun getTasksSignatureIsKnown() {
        assertTrue("Unknown getTasks signature on API ${Build.VERSION.SDK_INT}", WrappedShizuku.hasTopTaskQuery())
    }

    @Test
    fun topActivityMatchesTheUserService() {
        assumeTrue("Shizuku is not operational", WrappedShizuku.isOperational())
        val topActivity = WrappedShizuku.getTopActivityOrNull()
        assertNotNull(topActivity)
        val service = WrappedShizuku.getServiceOrNull()
        assumeTrue("The Shizuku user service did not connect", service != null)
        assertEquals(service!!.currentComponent(), topActivity!!.flattenToString())
    }
}
