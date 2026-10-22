package org.autojs.autojs.network.download

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * JVM tests of a cancelled download (docs/memory/ROADMAP.md Q54): cancelling takes the task out of the active tasks of
 * the process-level manager at once, so the observer of the caller (an Activity in the app) is no longer reachable from
 * it; cancelling an older task of the same url leaves the newer one.
 * zh-CN: 取消下载的 JVM 测试 (docs/memory/ROADMAP.md Q54): 取消后任务立即离开进程级管理器的活动任务表, 调用方 (应用中为 Activity)
 * 的观察者不再能从它到达; 取消同一 url 的旧任务时保留新任务.
 */
class DownloadManagerCancelTest {

    private lateinit var server: ServerSocket
    private val accepted = CopyOnWriteArrayList<Socket>()
    private lateinit var dir: File

    @Before
    fun setUp() {
        RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
        RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
        // The server accepts and never answers, so a download stays pending until it is cancelled.
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            runCatching { while (true) accepted += server.accept() }
        }
        dir = Files.createTempDirectory("download-cancel").toFile()
    }

    @After
    fun tearDown() {
        server.close()
        accepted.forEach { runCatching { it.close() } }
        dir.deleteRecursively()
        RxAndroidPlugins.reset()
    }

    @Test
    fun cancellingReleasesTheTaskAndTheCaller() {
        val manager = DownloadManager()
        val url = url("pending.bin")
        val caller = subscribeCaller(manager, url)

        awaitTrue { accepted.isNotEmpty() }
        assertTrue(manager.hasActiveTask(url))

        manager.cancelDownload(url)

        assertFalse(manager.hasActiveTask(url))
        awaitTrue { System.gc(); caller.get() == null }
        assertNull(caller.get())
    }

    @Test
    fun cancellingAnOlderTaskLeavesTheNewerOne() {
        val manager = DownloadManager()
        val url = url("twice.bin")
        subscribeCaller(manager, url)

        // The second download of the same url cancels the first one, as before Q54.
        subscribeCaller(manager, url)

        assertTrue(manager.hasActiveTask(url))
        manager.cancelDownload(url)
        assertFalse(manager.hasActiveTask(url))
    }

    private fun url(name: String) = "http://127.0.0.1:${server.localPort}/$name"

    private fun subscribeCaller(manager: DownloadManager, url: String): WeakReference<Any> {
        val caller = Any()
        manager.download(url, File(dir, "download.bin").path).subscribe({ caller.hashCode() }, { caller.hashCode() })
        return WeakReference(caller)
    }

    private fun awaitTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
    }

}
