package org.autojs.autojs.runtime.api.modules.toast

import org.autojs.autojs.runtime.api.modules.HostModuleTestSupport.DeviceCases
import org.junit.Test

/**
 * Floating windows used from a `threads.start` thread (fixed in 6.6.2, "floaty.window / floaty.rawWindow cannot run on a
 * child thread"; P3.6 found no case for it): the worker creates a `floaty.window` and a `floaty.rawWindow`, moves them,
 * changes a view through `ui.run`, closes them, and closes two more windows with `floaty.closeAll()`. Both states create
 * the windows with the current `augment/floaty/Floaty` (the new layer checks the arguments first), so the device cases
 * (`FloatyWorkerThreadTest.json`, run with the switch on and off) are expected to agree; they need the permission to
 * display over other apps, which the maintainer grants on the phone, and give "no permission" without it.
 *
 * zh-CN: 在 threads.start 线程中使用悬浮窗 (6.6.2 修复 "floaty.window/floaty.rawWindow 无法在子线程执行"; P3.6 发现没有对应用例): 工作线程创建
 * floaty.window 与 floaty.rawWindow, 移动位置, 经 ui.run 修改控件, 关闭窗口, 再以 floaty.closeAll() 关闭另外两个窗口. 两态都由现有 augment/floaty/Floaty
 * 创建窗口 (新层先检查参数), 因此设备用例 (FloatyWorkerThreadTest.json, 开关开 / 关各运行一次) 应当一致; 需要悬浮窗权限 (由维护者在手机上授予),
 * 没有权限时得到 "no permission".
 */
class FloatyWorkerThreadTest {

    @Test
    fun deviceCases() {
        DEVICE.add(DEVICE_CASES)
    }

    companion object {

        private val DEVICE = DeviceCases("FloatyWorkerThreadTest", "floaty windows on a threads.start thread (6.6.2; augment-misc)")

        private const val CONCURRENT = "java.util.concurrent"

        private const val LAYOUT = "'<frame><text id=\"label\" text=\"a\"/></frame>'"

        /** Runs [body] (which pushes to `steps`) on a `threads.start` thread and returns the steps joined by "|", or what went wrong. */
        private fun onWorker(body: String) =
            "(function () { if (!floaty.hasPermission()) return 'no permission'; " +
                "var latch = new $CONCURRENT.CountDownLatch(1), out = 'timeout'; " +
                "threads.start(function () { try { var steps = [String(java.lang.Thread.currentThread().getId() == android.os.Looper.getMainLooper().getThread().getId())]; " +
                "$body out = steps.join('|'); } catch (e) { out = 'failed ' + e; } latch.countDown(); }); " +
                "latch.await(10, $CONCURRENT.TimeUnit.SECONDS); return out; })()"

        /** Changes the text of the view "label" of `w` on the UI thread and reads it back, then closes `w` and reads its x there. */
        private const val UPDATE_AND_CLOSE =
            "ui.run(function () { w.label.setText('b'); }); steps.push(ui.run(function () { return String(w.label.getText()); })); " +
                "w.close(); steps.push(ui.run(function () { return String(w.getX()); }));"

        private val DEVICE_CASES = listOf(
            onWorker(
                "var w = floaty.window($LAYOUT); steps.push(typeof w.close, ui.run(function () { return String(w.label.getText()); })); " +
                    "w.setPosition(40, 60); w.setAdjustEnabled(true); steps.push(ui.run(function () { return [w.getX(), w.getY(), w.isAdjustEnabled()].join(); })); " +
                    UPDATE_AND_CLOSE,
            ) to "\"false|function|a|40,60,true|b|-1\"",
            onWorker(
                "var w = floaty.rawWindow($LAYOUT); steps.push(typeof w.close, ui.run(function () { return String(w.label.getText()); })); " +
                    "w.setPosition(40, 60); w.setTouchable(false); steps.push(ui.run(function () { return [w.getX(), w.getY()].join(); })); " +
                    UPDATE_AND_CLOSE,
            ) to "\"false|function|a|40,60|b|-1\"",
            onWorker(
                "var a = floaty.window('<frame/>'), r = floaty.rawWindow('<frame/>'); floaty.closeAll(); " +
                    "steps.push(ui.run(function () { return [a.getX(), r.getX()].join(); }));",
            ) to "\"false|-1,-1\"",
        )

    }

}
