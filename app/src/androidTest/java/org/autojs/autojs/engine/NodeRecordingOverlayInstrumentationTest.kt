package org.autojs.autojs.engine

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeRecordingOverlayInstrumentationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun permissions(vararg names: String) = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to
        NodeBridgePermissionManifest.fromDeclaredCapabilities(names.toList()).toJson())

    @Test fun recorderReportsItsProviderAndRequiresMicrophonePermission() {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val result = NodePluginTestHarness.runPluginScript("""
          (async () => {
            const assert = require('node:assert/strict'), recorder = require('recorder');
            const status = await recorder.getStatus();
            assert.equal(status.providerAvailable, true);
            assert.equal(status.recording, false);
            assert.equal(status.starting, false);
            assert.equal(status.microphonePermissionGranted, $granted);
            assert.equal(status.foregroundDisclosureRequired, true);
            assert.equal(status.active, null);
            assert.equal(await recorder.stop(), null);
            if (!$granted) await assert.rejects(recorder.start({path:'must-not-record.m4a'}), error => error.category === 'permission-denied');
            console.log('m15.recorder.permission=PASS granted=' + $granted);
          })().catch(error => { console.error(error.stack); process.exitCode=1; });
        """.trimIndent(), "recorder-permission.cjs", runtimeModuleSources = permissions("media", "media.recording"))
        assertTrue(result.errorMessage + "\n" + result.stderr.take(2500), result.succeeded)
        assertTrue(result.stdout, result.stdout.contains("m15.recorder.permission=PASS"))
        println(result.stdout)
    }

    @Test fun overlayUpdatesRealWindowsPushesEventsAndCleansUp() {
        val granted = Settings.canDrawOverlays(context)
        val inspect = Build.VERSION.SDK_INT >= 29
        val executor = Executors.newSingleThreadExecutor()
        val executionId = UUID.randomUUID().toString()
        var inspected = false
        try {
            val future = executor.submit(Callable {
                NodePluginTestHarness.runPluginScript("""
                  (async () => {
                    const assert = require('node:assert/strict'), overlay = require('ui.overlay');
                    const descriptor = {content:{type:'Column',id:'m15-overlay-root',children:[
                      {type:'Text',id:'m15-overlay-label',text:'before'},
                      {type:'Button',id:'m15-overlay-action',text:'m15-overlay-action'}]},
                      window:{width:180,height:120,x:16,y:32},disclosure:{title:'Roadmap test',text:'Visible test overlay'}};
                    if (!$granted) {
                      await assert.rejects(overlay.show(descriptor), error => error.category === 'permission-denied');
                      console.log('m15.overlay.permission=PASS'); return;
                    }
                    let handle;
                    try {
                      handle = await overlay.show(descriptor);
                      const clicked = $inspect ? new Promise(resolve => handle.once('click', resolve)) : null;
                      const moved = $inspect ? new Promise(resolve => handle.once('move', resolve)) : null;
                      const updateEvent = new Promise(resolve => handle.once('update', resolve));
                      const state = await handle.update({id:'m15-overlay-label',text:'m15-updated',textSize:21,
                        textColor:'#123456',backgroundColor:'#abcdef',padding:4,width:180,height:44,visible:true,enabled:true,
                        window:{width:220,height:160,x:32,y:64,alpha:0.7,focusable:true,touchable:true,draggable:true}});
                      assert.equal(state.window.draggable, true);
                      assert.equal(state.window.focusable, true);
                      assert.equal(state.window.touchable, true);
                      assert(Math.abs(state.window.alpha - 0.7) < 0.00001);
                      assert.equal((await updateEvent).targetId, 'm15-overlay-label');
                      if ($inspect) {
                        assert.equal((await clicked).targetId, 'm15-overlay-action');
                        const movement = await moved;
                        assert.equal(movement.state.x, state.bounds.x + 50);
                        const after = await handle.update({id:'m15-overlay-label',enabled:false,visible:false});
                        assert.equal(after.bounds.x, movement.state.x);
                      }
                      const replaced = await handle.update({content:{type:'Text',id:'m15-overlay-replaced',text:'Replacement'},
                        window:{width:'wrap_content',height:'wrap_content',alpha:1,focusable:false,touchable:false,draggable:false}});
                      assert.equal(replaced.bounds.width, -2);
                      assert.equal(replaced.bounds.height, -2);
                      assert.equal(replaced.window.touchable, false);
                      assert.equal(replaced.window.focusable, false);
                      assert.equal(replaced.window.draggable, false);
                      await assert.rejects(handle.update({window:{visible:false}}), error => error.category === 'invalid-request');
                      assert.equal(await handle.close(), true);
                      assert.equal(handle.closed, true);
                      assert.equal(await handle.close(), false);
                    } finally { if (handle && !handle.closed) await handle.close(); }
                    await overlay.show({content:{type:'Text',id:'m15-overlay-cleanup',text:'Cleanup'},disclosure:descriptor.disclosure});
                    console.log('m15.overlay=PASS inspected=' + $inspect);
                  })().catch(error => { console.error(error.stack); process.exitCode=1; });
                """.trimIndent(), "overlay-updates.cjs", timeoutMs = 30_000, executionId = executionId,
                    runtimeModuleSources = permissions("ui.overlay", "ui.overlay.permission"))
            })
            val deadline = SystemClock.elapsedRealtime() + 25_000
            while (!future.isDone && SystemClock.elapsedRealtime() < deadline) {
                if (inspect && granted && !inspected) instrumentation.runOnMainSync {
                    val root = roots().firstOrNull { it.findViewWithTag<View>("m15-overlay-root") != null }
                    val label = root?.findViewWithTag<TextView>("m15-overlay-label")
                    if (root != null && label != null && label.text.toString() == "m15-updated") {
                        assertEquals(Color.parseColor("#123456"), label.currentTextColor)
                        assertEquals(21f, label.textSize / context.resources.displayMetrics.scaledDensity, 0.01f)
                        assertEquals((4 * context.resources.displayMetrics.density).toInt(), label.paddingLeft)
                        assertEquals(0.7f, root.alpha, 0.00001f)
                        val layout = root.layoutParams as WindowManager.LayoutParams
                        assertEquals(0, layout.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                        root.findViewWithTag<Button>("m15-overlay-action").performClick()
                        val now = SystemClock.uptimeMillis()
                        for ((action, x, y) in listOf(Triple(MotionEvent.ACTION_DOWN,10f,10f),
                            Triple(MotionEvent.ACTION_MOVE,60f,45f),Triple(MotionEvent.ACTION_UP,60f,45f))) {
                            val event = MotionEvent.obtain(now, now+20, action,x,y,0)
                            try { label.dispatchTouchEvent(event) } finally { event.recycle() }
                        }
                        inspected = true
                    }
                }
                SystemClock.sleep(40)
            }
            val result = future.get(7, TimeUnit.SECONDS)
            assertTrue(result.errorMessage + "\n" + result.stderr.take(3000), result.succeeded)
            assertTrue(result.stdout, result.stdout.contains("=PASS"))
            if (granted && inspect) assertTrue("The real overlay was not inspected", inspected)
            instrumentation.waitForIdleSync()
            if (inspect) instrumentation.runOnMainSync {
                assertTrue("Overlay window leaked after script exit", roots().none { root ->
                    listOf("m15-overlay-root","m15-overlay-replaced","m15-overlay-cleanup").any { root.findViewWithTag<View>(it) != null }
                })
            }
            println(result.stdout)
        } finally {
            NodeJsRuntimePluginBridge.cancelScript(context, executionId)
            executor.shutdownNow()
        }
    }

    private fun roots(): List<View> = if (Build.VERSION.SDK_INT >= 29) android.view.inspector.WindowInspector.getGlobalWindowViews() else emptyList()
}
