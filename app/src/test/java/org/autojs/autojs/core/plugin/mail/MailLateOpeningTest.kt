package org.autojs.autojs.core.plugin.mail

import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.plugin.FakeRemote
import org.autojs.autojs.core.plugin.openAfterTheCallerWasCancelled
import org.autojs.autojs.core.plugin.registeredLease
import org.autojs.plugin.mail.api.IMailSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A mail session that opens after its script stopped (docs/memory/ROADMAP.md appendix C Q59): `MailPluginHost.openSession`
 * closes the late port, which closes the plugin session and releases the dedicated binding.
 * zh-CN: 脚本停止之后才打开的邮件会话 (docs/memory/ROADMAP.md 附录 C Q59): `MailPluginHost.openSession` 关闭迟到的端口, 插件会话随之关闭,
 * 专用绑定随之释放.
 */
class MailLateOpeningTest {

    @Test
    fun aPortOpenedAfterTheScriptStoppedClosesThePluginSessionAndReleasesTheBinding() {
        val remote = FakeRemote()
        val lease = registeredLease()
        val events = object : MailSessionEvents {
            override fun onClosed(reason: MailError?) = Unit
        }

        openAfterTheCallerWasCancelled(MailSessionPort::close) {
            BinderMailSessionPort(AidlPluginHost.LeasedCall(remote.remote(IMailSession::class.java), lease), events)
        }

        assertEquals(listOf("binder.linkToDeath", "binder.unlinkToDeath", "close"), remote.calls)
        assertTrue(lease.isClosed)
    }
}
