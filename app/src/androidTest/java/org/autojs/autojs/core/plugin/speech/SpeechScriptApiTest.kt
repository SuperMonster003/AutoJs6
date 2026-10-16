package org.autojs.autojs.core.plugin.speech

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The `speech` script API end to end (3-Macaw Speech roadmap P1.4): scripts run through the production Rhino engine
 * and write what they saw to a marker file. With the fake speech plugin of `test-apps/speech-provider-conformance`
 * installed (`adb install -t`, another uid signed like the host) every call crosses a real Binder with the
 * `BIND_INCLUDE_CAPABILITIES` binding: models, file and PCM recognition, voice activity, synthesis to a staged file,
 * progress operations, external PCM sessions, wake rounds and the death of the plugin process. Without it the script
 * sees `PLUGIN_UNAVAILABLE`, or `PLUGIN_INCOMPATIBLE` when only the 3-Macaw Speech skeleton of roadmap P0 is installed
 * (it does not answer the contract yet). Each test skips itself when the device is in the other state.
 *
 * ```
 * adb shell am instrument -w -e class org.autojs.autojs.core.plugin.speech.SpeechScriptApiTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * zh-CN: `speech` 脚本 API 端到端测试 (3-Macaw Speech 路线图 P1.4): 脚本经正式 Rhino 引擎运行, 把所见写入标记文件. 安装了
 * `test-apps/speech-provider-conformance` 的假语音插件 (`adb install -t`, 与宿主同签名的另一 uid) 时, 每个调用都经带 `BIND_INCLUDE_CAPABILITIES`
 * 的真实 Binder: 模型, 文件与 PCM 识别, 语音活动, 合成到暂存文件, 进度操作, 外部 PCM 会话, 唤醒轮次以及插件进程死亡. 未安装时脚本应看到
 * `PLUGIN_UNAVAILABLE`; 只安装了路线图 P0 的 3-Macaw Speech 骨架 (尚未应答契约) 时为 `PLUGIN_INCOMPATIBLE`. 设备处于另一种状态时相应用例自行跳过.
 */
class SpeechScriptApiTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val root by lazy { File(context.cacheDir, "speech-script-${System.nanoTime()}").apply { assertTrue(mkdirs()) } }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun installed(packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun fakeInstalled(): Boolean = installed(FAKE_PACKAGE)

    /** A mono PCM16 WAV of [millis] milliseconds of silence at 16 kHz. */
    private fun wav(name: String, millis: Int) {
        val samples = 16 * millis
        val out = ByteArrayOutputStream()
        SpeechAudio.writeWavHeader(out, 16_000, samples * 2)
        out.write(ByteArray(samples * 2))
        File(root, name).writeBytes(out.toByteArray())
    }

    @Test
    fun scriptsSeeAMissingOrIncompatiblePlugin() {
        assumeFalse("The fake speech plugin is installed", fakeInstalled())
        wav("a.wav", 200)
        val skeleton = installed(SpeechPluginIds.OFFICIAL_PACKAGE_NAME)
        val result = runScript(
            """
            var result = { available: speech.isAvailable(), same: ${'$'}speech === speech };
            try { speech.recognize("a.wav"); } catch (e) { result.code = e.code; result.name = e.name; result.message = e.message; result.isSpeechError = e instanceof speech.SpeechError; }
            try { speech.listen(); } catch (e) { result.listenCode = e.code; }
            try { speech.models.list(); } catch (e) { result.modelsCode = e.code; }
            try { speech.listen({ source: "file" }); } catch (e) { result.optionsCode = e.code; }
            try { speech.wake([]); } catch (e) { result.wakeCode = e.code; }
            try { speech.synthesize(new Array(8002).join("a"), "o.wav"); } catch (e) { result.limitCode = e.code; }
            try { speech.speak("hi", { engine: "x" }); } catch (e) { result.speakCode = e.code; }
            speech.recognizeAsync("a.wav").catch(function (e) {
                result.asyncCode = e.code;
                files.write(MARKER, JSON.stringify(result));
            });
            """,
        )
        val expected = if (skeleton) SpeechErrorCodes.PLUGIN_INCOMPATIBLE else SpeechErrorCodes.PLUGIN_UNAVAILABLE
        assertFalse(result.getBoolean("available"))
        assertTrue(result.getBoolean("same"))
        assertEquals(expected, result.getString("code"))
        assertEquals("SpeechError", result.getString("name"))
        assertTrue(result.getBoolean("isSpeechError"))
        if (!skeleton) assertTrue(result.getString("message"), result.getString("message").contains("3-Macaw Speech"))
        assertEquals(expected, result.getString("listenCode"))
        assertEquals(expected, result.getString("modelsCode"))
        assertEquals(expected, result.getString("asyncCode"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("optionsCode"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("wakeCode"))
        assertEquals(SpeechErrorCodes.LIMIT_EXCEEDED, result.getString("limitCode"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("speakCode"))
    }

    @Test
    fun scriptsRoundTripModelsFilesAndSynthesis() {
        assumeTrue("Install test-apps/speech-provider-conformance (adb install -t) to run this test", fakeInstalled())
        wav("hello.wav", 1500)
        File(root, "notes.txt").writeText("not audio")
        val result = runScript(
            """
            var r = {};
            r.available = speech.isAvailable();
            var caps = speech.capabilities();
            r.caps = [caps.contractVersion, caps.features.join(","), caps.catalogVersion, caps.engineName, caps.packageName];
            r.models = speech.models.list().map(function (m) { return m.id; });
            r.asrModels = speech.models.list({ kind: "asr" }).map(function (m) { return m.id + ":" + m.isDefault + ":" + m.license.spdx; });
            r.defaultTts = speech.models.getDefault("tts").id;
            var progress = [];
            r.installed = speech.models.install("asr.fake.zh-en", { onProgress: function (d, t) { progress.push(d + "/" + t); } }).installed;
            r.progress = progress;
            try { speech.models.install("tts.fake.noncommercial"); } catch (e) { r.licenseCode = e.code; }
            r.licensed = speech.models.install("tts.fake.noncommercial", { acceptLicense: true }).id;
            try { speech.models.install("asr.unknown"); } catch (e) { r.unknownCode = e.code; }
            r.removed = speech.models.remove("asr.fake.zh-en");
            try { speech.models.remove("nope"); } catch (e) { r.removeCode = e.code; }
            r.setDefault = speech.models.setDefault("tts", "tts.fake.zh").isDefault;
            r.catalog = speech.models.refreshCatalog();
            var rec = speech.recognize("hello.wav", { language: "en", hotwords: ["AutoJs6"] });
            r.recognized = [rec.text, rec.durationMillis, rec.segments.length, rec.language, rec.warnings.length, rec.warnings[0].code];
            var pcm = speech.recognize(new Int16Array(8000), { sampleRate: 8000 });
            r.pcm = [pcm.durationMillis, /^fake:pcm-.*\.wav$/.test(pcm.segments[0].text)];
            try { speech.recognize("hello.wav", { model: "asr.missing" }); } catch (e) { r.missingCode = e.code; }
            try { speech.recognize("notes.txt"); } catch (e) { r.formatCode = e.code; }
            try { speech.recognize("missing.wav"); } catch (e) { r.ioCode = e.code; }
            try { speech.recognize({}); } catch (e) { r.sourceCode = e.code; }
            r.vad = speech.detectVoice("hello.wav").segments.map(function (s) { return s.startMillis + "-" + s.endMillis; });
            var syn = speech.synthesize("hello", "out/hello.wav", { speaker: 2, speed: 1.2 });
            r.synth = [files.getName(syn.path), syn.sampleRate, syn.durationMillis, syn.speakerId, syn.sizeBytes];
            r.listing = files.listDir("out");
            speech.recognizeAsync("hello.wav").then(function (a) {
                r.async = a.text;
                return speech.synthesizeAsync("again", "out/again.wav");
            }).then(function (s) {
                r.asyncSynth = files.getName(s.path);
                return speech.models.installAsync("kws.fake.zh-en", { onProgress: function (d, t) { r.asyncProgress = d + "/" + t; } });
            }).then(function (m) {
                r.asyncInstall = m.id;
            }).catch(function (e) {
                r.asyncError = e.code + " " + e.message;
            }).then(function () {
                files.write(MARKER, JSON.stringify(r));
            });
            """,
        )
        assertFalse(result.optString("asyncError"), result.has("asyncError"))
        assertTrue(result.getBoolean("available"))
        assertEquals("""[1,"asr,asrStreaming,vad,kws,tts,models",1,"fake","$FAKE_PACKAGE"]""", result.getJSONArray("caps").toString())
        assertEquals("""["asr.fake.zh-en","tts.fake.zh","kws.fake.zh-en","tts.fake.noncommercial"]""", result.getJSONArray("models").toString())
        assertEquals("""["asr.fake.zh-en:true:Apache-2.0"]""", result.getJSONArray("asrModels").toString())
        assertEquals("tts.fake.zh", result.getString("defaultTts"))
        assertTrue(result.getBoolean("installed"))
        assertEquals("""["25/100","50/100","75/100","100/100"]""", result.getJSONArray("progress").toString().replace("\\/", "/"))
        assertEquals(SpeechErrorCodes.MODEL_LICENSE_NOT_ACCEPTED, result.getString("licenseCode"))
        assertEquals("tts.fake.noncommercial", result.getString("licensed"))
        assertEquals(SpeechErrorCodes.MODEL_MISSING, result.getString("unknownCode"))
        assertTrue(result.getBoolean("removed"))
        assertEquals(SpeechErrorCodes.MODEL_MISSING, result.getString("removeCode"))
        assertTrue(result.getBoolean("setDefault"))
        assertEquals("""{"version":1,"updated":false}""", result.getJSONObject("catalog").toString())
        assertEquals("""["fake:hello.wav:1500",1500,1,"en",1,"INVALID_OPTIONS"]""", result.getJSONArray("recognized").toString())
        assertEquals("[1000,true]", result.getJSONArray("pcm").toString())
        assertEquals(SpeechErrorCodes.MODEL_MISSING, result.getString("missingCode"))
        assertEquals(SpeechErrorCodes.UNSUPPORTED_FORMAT, result.getString("formatCode"))
        assertEquals(SpeechErrorCodes.IO_FAILED, result.getString("ioCode"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("sourceCode"))
        assertEquals("""["0-1500"]""", result.getJSONArray("vad").toString())
        assertEquals("""["hello.wav",16000,500,"2",16044]""", result.getJSONArray("synth").toString())
        assertEquals("""["hello.wav"]""", result.getJSONArray("listing").toString())
        assertEquals("fake:hello.wav:1500", result.getString("async"))
        assertEquals("again.wav", result.getString("asyncSynth"))
        assertEquals("100/100", result.getString("asyncProgress").replace("\\/", "/"))
        assertEquals("kws.fake.zh-en", result.getString("asyncInstall"))
        assertFalse("no staging file is left behind", File(root, "out").list()!!.any { it.startsWith(".speech-") })
    }

    @Test
    fun scriptsFollowExternalSessionsAndWakeRounds() {
        assumeTrue("Install test-apps/speech-provider-conformance (adb install -t) to run this test", fakeInstalled())
        val result = runScript(
            """
            var r = { events: [], wake: [] };
            var s = speech.listen({ source: "external" });
            r.source = s.source;
            r.openState = s.state;
            s.on("ready", function (e) { r.events.push("ready:" + e.modelId); r.readyId = s.id; });
            s.on("partial", function (e) { r.events.push("partial:" + e.text); });
            s.on("final", function (e) { r.events.push("final:" + e.text); });
            s.on("endpoint", function () { r.events.push("endpoint"); });
            s.on("end", function (e) {
                r.events.push("end:" + e.reason);
                r.result = s.result();
                r.endState = s.state;
                r.active = s.isActive();
                startWake();
            });
            s.feed(new Int16Array(800));
            s.feed(new Uint8Array(400).buffer);
            try { s.feed(new Uint8Array(3)); } catch (e) { r.oddCode = e.code; }
            try { s.feed("text"); } catch (e) { r.typeCode = e.code; }
            s.stop();
            s.stop();
            function startWake() {
                var w = speech.wake(["hey macaw"], { thenListen: true });
                r.keywords = w.keywords;
                w.on("wake", function (e) {
                    r.wake.push("wake:" + e.keyword + ":" + e.listen.round);
                    e.listen.on("final", function (f) { r.wake.push("final:" + f.text); });
                    e.listen.on("end", function (f) {
                        r.wake.push("roundEnd:" + f.reason);
                        r.roundResult = e.listen.result();
                        setTimeout(function () { w.stop(); }, 100);
                    });
                });
                w.on("end", function (e) {
                    r.wake.push("end:" + e.reason);
                    r.wakeState = w.state;
                    files.write(MARKER, JSON.stringify(r));
                });
            }
            """,
        )
        assertEquals("external", result.getString("source"))
        assertEquals(
            """["ready:asr.fake.zh-en","partial:fed 1600","partial:fed 2000","final:fed 2000 bytes","endpoint","end:stopped"]""",
            result.getJSONArray("events").toString(),
        )
        // The state is live: pending or active right after listen(), whatever events are still queued.
        assertTrue(result.getString("openState") in setOf("pending", "active"))
        assertTrue(result.getString("readyId").startsWith("external-"))
        assertEquals("fed 2000 bytes", result.getString("result"))
        assertEquals("ended", result.getString("endState"))
        assertFalse(result.getBoolean("active"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("oddCode"))
        assertEquals(SpeechErrorCodes.INVALID_OPTIONS, result.getString("typeCode"))
        assertEquals("""["hey macaw"]""", result.getJSONArray("keywords").toString())
        assertEquals(
            """["wake:hey macaw:0","final:turn on the light","roundEnd:stopped","end:stopped"]""",
            result.getJSONArray("wake").toString(),
        )
        assertEquals("turn on the light", result.getString("roundResult"))
        assertEquals("ended", result.getString("wakeState"))
    }

    @Test
    fun theDeathOfThePluginProcessEndsTheSessionWithAnError() {
        assumeTrue("Install test-apps/speech-provider-conformance (adb install -t) to run this test", fakeInstalled())
        val result = runScript(
            """
            var r = { events: [] };
            var s = speech.listen({ source: "external", model: "crash.process" });
            s.on("error", function (e) { r.events.push("error:" + e.code); });
            s.on("end", function (e) {
                r.events.push("end:" + e.reason);
                try { s.feed(new Int16Array(2)); } catch (x) { r.afterCode = x.code; }
                files.write(MARKER, JSON.stringify(r));
            });
            """,
        )
        assertEquals("""["error:SESSION_CLOSED","end:error"]""", result.getJSONArray("events").toString())
        assertEquals(SpeechErrorCodes.SESSION_CLOSED, result.getString("afterCode"))
    }

    private fun runScript(body: String, timeoutSeconds: Long = 60): JSONObject {
        val marker = File(root, "marker.json")
        val source = "var MARKER = " + JSONObject.quote(marker.path) + ";\n" + body.trimIndent()
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("speech-script-api", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(timeoutSeconds, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText())
        } finally {
            execution.engine?.forceStop()
        }
    }

    private companion object {
        const val FAKE_PACKAGE = "org.autojs.plugin.speech.fake"
    }
}
