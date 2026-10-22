package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import org.autojs.autojs.core.plugin.FakeRemote
import org.autojs.autojs.core.plugin.allocate
import org.autojs.autojs.core.plugin.openAfterTheCallerWasCancelled
import org.autojs.autojs.core.plugin.registeredLease
import org.autojs.plugin.yolo.api.IYoloSession
import org.autojs.plugin.yolo.api.YoloCapabilities
import org.autojs.plugin.yolo.api.YoloProviderInfo
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * A YOLO session that opens after its script stopped (docs/memory/ROADMAP.md appendix C Q59): `YoloPluginHost.openSession`
 * closes the late session, which closes it in the plugin (freeing the native engine) and releases the dedicated binding.
 * zh-CN: 脚本停止之后才打开的 YOLO 会话 (docs/memory/ROADMAP.md 附录 C Q59): `YoloPluginHost.openSession` 关闭迟到的会话, 插件中的会话随之关闭
 * (释放原生引擎), 专用绑定随之释放.
 */
class YoloLateOpeningTest {

    @Test
    fun aSessionOpenedAfterTheScriptStoppedIsClosedInThePluginAndReleasesTheBinding() {
        val remote = FakeRemote(descriptor = IYoloSession.DESCRIPTOR)
        val lease = registeredLease()
        var session: YoloPluginHost.Session? = null

        openAfterTheCallerWasCancelled(YoloPluginHost.Session::close) {
            YoloPluginHost.Session(
                YoloRemoteSession.create(
                    applicationContext = allocate(TestContext::class.java),
                    component = allocate(ComponentName::class.java),
                    providerUid = PROVIDER_UID,
                    providerInfo = allocate(YoloProviderInfo::class.java),
                    capabilities = allocate(YoloCapabilities::class.java),
                    remote = remote.remote(IYoloSession::class.java),
                    callback = callbackBridge(),
                    bindingLease = lease,
                    modelDescriptorsClosedAfterOpen = true,
                ),
            ).also { session = it }
        }

        assertTrue(session!!.isClosed)
        assertTrue("close" in remote.calls)
        assertTrue(lease.isClosed)
    }

    /** The callback Stub needs a working Binder constructor, so its state is filled in by hand. zh-CN: 回调 Stub 需要可用的 Binder 构造函数, 因此手动填入其状态. */
    private fun callbackBridge(): YoloCallbackBridge = allocate(YoloCallbackBridge::class.java).also { bridge ->
        for (name in listOf("active", "mostRecentTrace", "fatalHandler")) {
            YoloCallbackBridge::class.java.getDeclaredField(name).apply { isAccessible = true }.set(bridge, AtomicReference<Any?>())
        }
    }

    private class TestContext : ContextWrapper(null) {

        override fun getApplicationContext(): Context = this
    }

    private companion object {
        const val PROVIDER_UID = 10_123
    }
}
