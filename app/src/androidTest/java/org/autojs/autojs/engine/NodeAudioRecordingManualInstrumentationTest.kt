package org.autojs.autojs.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.log.LogActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Opt-in physical microphone check. The user handles Android's permission dialog. */
class NodeAudioRecordingManualInstrumentationTest {
    @Test fun recordsThreeSecondsWithDisclosureAndParsesTheFileWithMediaInfo() {
        assumeTrue("Run only with audioRecording=manual when the user is ready for microphone consent and a 3-second recording.",
            InstrumentationRegistry.getArguments().getString("audioRecording") == "manual")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val directory = File(context.cacheDir, "node-audio-manual-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val output = File(directory, "recorded.m4a")
        val executor = Executors.newSingleThreadExecutor()
        val executionId = UUID.randomUUID().toString()
        val ready = CountDownLatch(1)
        try {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                instrumentation.runOnMainSync { ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), 1514) }
                val deadline = SystemClock.elapsedRealtime() + 120_000
                while (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            }
            assertEquals("Microphone permission was not granted", PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
            val future = executor.submit(Callable {
                NodeJsRuntimePluginBridge.runScript(context, NodePluginScriptRequest(
                    source = """
                      (async () => {
                        const assert = require('node:assert/strict'), recorder = require('recorder'), mediainfo = require('mediainfo');
                        try {
                          const recording = await recorder.start({path:'recorded.m4a',maxDurationMs:3000});
                          assert.equal(recording.recording, true);
                          assert.equal((await recorder.getStatus()).recording, true);
                          console.log('m15.audio.ready');
                          await new Promise(resolve => setTimeout(resolve, 3600));
                          const state = await recorder.getStatus();
                          assert.equal(state.recording, false);
                          const result = await recorder.stop();
                          assert.equal(result.reason, 'max-duration');
                          assert(result.byteCount > 0 && result.durationMs >= 2500 && result.durationMs < 5000);
                          const info = await mediainfo.read('recorded.m4a', {includeInform:false});
                          assert(info.sizeBytes > 0);
                          assert(Array.isArray(info.sections.audio) && info.sections.audio.length > 0);
                          assert(JSON.stringify(info.sections.audio).includes('AAC'));
                          console.log('m15.audio.manual=PASS bytes=' + result.byteCount + ' durationMs=' + result.durationMs);
                        } finally { await recorder.stop(); }
                      })().catch(error => { console.error(error.stack); process.exitCode=1; });
                    """.trimIndent(), sourceName = "record-audio.cjs", workingDirectory = directory.absolutePath,
                    timeoutMs = 25_000, executionId = executionId,
                    runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
                        .fromDeclaredCapabilities(listOf("media", "media.recording", "media.metadata")).toJson()),
                ), outputListener = { _, text -> if (text.contains("m15.audio.ready")) ready.countDown() })
            })
            assertTrue("Recording did not start", ready.await(10, TimeUnit.SECONDS))
            val notifications = context.getSystemService(NotificationManager::class.java).activeNotifications
            assertTrue("Microphone foreground disclosure is missing", notifications.any {
                it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 && it.notification.channelId == "node_audio_recording"
            })
            val result = future.get(20, TimeUnit.SECONDS)
            assertTrue(result.errorMessage + "\n" + result.stderr.take(3000), result.succeeded)
            assertTrue(output.length() > 0)
            instrumentation.waitForIdleSync()
            assertTrue("Recording disclosure remained after stop", context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                it.notification.channelId == "node_audio_recording"
            })
            println(result.stdout)
        } finally {
            NodeJsRuntimePluginBridge.cancelScript(context, executionId)
            executor.shutdownNow()
            instrumentation.runOnMainSync { activity.finish() }
            output.delete()
        }
    }
}
