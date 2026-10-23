package org.autojs.autojs.storage.database

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.autojs.autojs.storage.history.HistoryCleanupWorker
import org.autojs.autojs.theme.app.ColorLibrariesActivity
import org.autojs.autojs.theme.app.ColorSelectActivity
import org.autojs.autojs.ui.keystore.ManageKeyStoreActivity
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.storage.TrashActivity
import org.autojs.autojs.ui.storage.VersionHistoryActivity
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.util.concurrent.TimeUnit

/**
 * Page check of docs/db-downgrade/ROADMAP.md P2.3, driven by `tools/db-downgrade-room/room_pages.py`, which writes
 * "future" files of the three Room databases before the host starts. Opens the pages that read them (the drawer, the
 * version history page, the trash page, the color pages, the keystore page) and runs `HistoryCleanupWorker` once.
 * Off unless the instrumentation argument `autojs.dbDowngrade.pages` is `true`, because it works on the real databases.
 *
 * zh-CN: docs/db-downgrade/ROADMAP.md P2.3 的页面核对, 由 `tools/db-downgrade-room/room_pages.py` 驱动 (宿主启动前写好三个
 * Room 库的 "未来" 文件). 依次打开读取这些库的页面 (抽屉, 版本历史页, 回收站, 颜色页, 密钥库页) 并运行一次
 * `HistoryCleanupWorker`. 它操作真实的库, 因此只在 instrumentation 参数 `autojs.dbDowngrade.pages` 为 `true` 时运行.
 *
 * Created by Claude (Opus 5.5) on Oct 23, 2026.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RoomDowngradePagesDeviceTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @Before
    fun requireOptIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.dbDowngrade.pages").toBoolean())
    }

    @Test
    fun page1Drawer() = open(MainActivity::class.java, Intent(context, MainActivity::class.java)) { activity ->
        activity.findViewById<DrawerLayout>(R.id.drawer_layout).openDrawer(GravityCompat.START)
    }

    @Test
    fun page2VersionHistory() = open(VersionHistoryActivity::class.java, Intent(context, VersionHistoryActivity::class.java))

    @Test
    fun page3Trash() = open(TrashActivity::class.java, Intent(context, TrashActivity::class.java))

    @Test
    fun page4ColorLibraries() = open(ColorLibrariesActivity::class.java, colorIntent(ColorLibrariesActivity::class.java))

    @Test
    fun page5ColorSelect() = open(ColorSelectActivity::class.java, colorIntent(ColorSelectActivity::class.java))

    @Test
    fun page6KeyStore() = open(ManageKeyStoreActivity::class.java, Intent(context, ManageKeyStoreActivity::class.java))

    @Test
    fun worker1HistoryCleanup() {
        val workManager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<HistoryCleanupWorker>().build()
        workManager.enqueue(request).result.get(10, TimeUnit.SECONDS)
        val deadline = SystemClock.uptimeMillis() + WORKER_TIMEOUT_MILLIS
        var info = workManager.getWorkInfoById(request.id).get()
        // The worker answers retry when a pass throws, which leaves it enqueued with a run attempt counted.
        // zh-CN: 某一步抛异常时 worker 返回 retry, 状态回到 enqueued 且计入一次运行.
        while (info != null && !info.state.isFinished && info.runAttemptCount == 0 && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(200)
            info = workManager.getWorkInfoById(request.id).get()
        }
        Log.i(TAG, "HistoryCleanupWorker: ${info?.state}, attempts ${info?.runAttemptCount}")
        workManager.cancelWorkById(request.id)
        assertEquals(WorkInfo.State.SUCCEEDED, info?.state)
    }

    private fun colorIntent(klass: Class<out Activity>) = Intent(context, klass)
        .putExtra("title", context.getString(R.string.text_theme_color))
        .putExtra("isToggled", false)

    private fun <A : Activity> open(klass: Class<A>, intent: Intent, prepare: (A) -> Unit = {}) {
        val activity = klass.cast(instrumentation.startActivitySync(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))!!
        instrumentation.runOnMainSync { prepare(activity) }
        // The page opens its database and loads its rows on background threads.
        // zh-CN: 页面在后台线程打开库并加载数据.
        SystemClock.sleep(SETTLE_MILLIS)
        instrumentation.waitForIdleSync()
        var resumed = emptyList<Activity>()
        instrumentation.runOnMainSync { resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).toList() }
        Log.i(TAG, "${klass.simpleName}: resumed ${resumed.map { it.javaClass.simpleName }}")
        instrumentation.runOnMainSync { activity.finish() }
        instrumentation.waitForIdleSync()
        assertTrue("${klass.simpleName} is not resumed after ${SETTLE_MILLIS} ms: ${resumed.map { it.javaClass.simpleName }}", activity in resumed)
    }

    private companion object {
        const val TAG = "RoomDowngradePages"
        const val SETTLE_MILLIS = 4000L
        const val WORKER_TIMEOUT_MILLIS = 60_000L
    }
}
