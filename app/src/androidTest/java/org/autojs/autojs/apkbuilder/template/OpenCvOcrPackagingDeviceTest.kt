package org.autojs.autojs.apkbuilder.template

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Produces a packaged APK for the OpenCV / OCR runtime decoupling roadmap
 * (docs/dev/opencv-ocr-runtime-decoupling-roadmap.md, P0.2 / P0.3 / P4.2).
 *
 * The test only records the build outcome, so expected packaging failures stay observable.
 * tools/opencv-ocr/verify_packaged_ocr.py inspects, installs and runs the produced APK.
 *
 * Arguments (all prefixed with `autojs.ocrPackaging.`):
 * - `produceApk`: must be `true`, otherwise the test is skipped.
 * - `name`: output base name.
 * - `libs`: comma-separated library labels, for example `OpenCV,Paddle OCR,Rapid OCR`.
 * - `enable`: comma-separated OCR plugin packages enabled during the build. Every other installed
 *   Paddle OCR / Rapid OCR package is disabled for the build and restored afterwards.
 * - `abi`: packaged ABI, defaults to the primary device ABI.
 */
class OpenCvOcrPackagingDeviceTest {

    @Test
    fun producePackagedApk() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("$PREFIX.produceApk").toBoolean())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = arguments.getString("$PREFIX.name", "ocr-packaging").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val libs = arguments.getString("$PREFIX.libs", "").split(',').map(String::trim).filter(String::isNotEmpty)
        val enabled = arguments.getString("$PREFIX.enable", "").split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        val abi = arguments.getString("$PREFIX.abi", Build.SUPPORTED_ABIS.first())

        val outputDir = File(context.getExternalFilesDir(null), "ocr-packaging").apply { mkdirs() }
        val output = File(outputDir, "$name.apk").apply { delete() }
        val report = File(outputDir, "$name.json").apply { delete() }
        val source = File(context.cacheDir, "ocr-packaging-project").apply {
            deleteRecursively()
            mkdirs()
        }

        @Suppress("DEPRECATION")
        val ocrPackages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { packageName -> OCR_PACKAGE_PREFIXES.any(packageName::startsWith) }
        val previousEnabled = ocrPackages.associateWith { PluginEnableStore.isEnabled(context, it) }
        val result = JSONObject()
            .put("name", name)
            .put("abi", abi)
            .put("libs", JSONArray(libs))
            .put("enabledPackages", JSONArray(enabled.sorted()))
            .put("disabledPackages", JSONArray((ocrPackages - enabled).sorted()))
        try {
            val bitmap = BitmapFactory.decodeResource(context.resources, com.baidu.paddle.lite.ocr.R.drawable.paddle_ocr_test)
            // Match the native page-size fixture, including its 4x scale.
            val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width * 4, bitmap.height * 4, false)
            File(source, "ocr-test.png").outputStream().use { check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            scaled.recycle()
            bitmap.recycle()
            File(source, "main.js").writeText(SCRIPT)
            val config = ProjectConfig().setName("OpenCV OCR packaging smoke")
                .setPackageName(PACKAGED_APP_PACKAGE_NAME).setVersionName("1.0").setVersionCode(1)
                .setMainScriptFileName("main.js").setProjectType("rhino").setSourcePath(source.absolutePath)
                .setAbis(listOf(abi))
                .setLibs(libs)
                .setPermissions(emptyList())
            File(source, "project.json").writeText(config.toJson(false))
            ocrPackages.forEach { packageName ->
                PluginEnableStore.setEnabled(context, packageName, packageName in enabled)
            }
            val outcome = RemoteApkBuildClient.buildBlocking(
                context, config, output, cancelSignal = null,
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME,
            )
            result.put("status", outcome.status)
                .put("success", outcome.isSuccess && output.isFile)
                .put("errors", JSONArray(outcome.errors))
                .put("warnings", JSONArray(outcome.warnings))
                .put("apkPath", output.absolutePath.takeIf { output.isFile })
        } catch (error: Throwable) {
            result.put("success", false).put("exception", error.stackTraceToString())
        } finally {
            previousEnabled.forEach { (packageName, wasEnabled) -> PluginEnableStore.setEnabled(context, packageName, wasEnabled) }
            source.deleteRecursively()
            report.writeText(result.toString(2))
        }
    }

    private companion object {

        const val PREFIX = "autojs.ocrPackaging"
        const val PACKAGED_APP_PACKAGE_NAME = "org.autojs.autojs6.ocrpackagingsmoke"

        val OCR_PACKAGE_PREFIXES = listOf(
            "io.github.supermonster003.autojs6.plugin.paddleocr.",
            "io.github.supermonster003.autojs6.plugin.rapidocr.",
        )

        /** Records each step independently so that one missing engine does not hide the others. */
        val SCRIPT = """
            "ui";
            ui.layout(<vertical padding="24"><text id="result" text="OpenCV OCR packaging smoke running" /></vertical>);
            threads.start(function () {
                var started = Date.now();
                var result = {
                    pageSize: android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE),
                    abi: String(android.os.Build.SUPPORTED_ABIS[0]),
                    is64Bit: android.os.Process.is64Bit(),
                    steps: {},
                };
                function describe(error) {
                    var text = String(error);
                    if (error && error.javaException) text += "\n" + String(error.javaException);
                    return text;
                }
                function step(name, fn) {
                    var begin = Date.now();
                    try {
                        result.steps[name] = { ok: true, value: fn(), millis: Date.now() - begin };
                    } catch (error) {
                        result.steps[name] = { ok: false, error: describe(error), millis: Date.now() - begin };
                    }
                    android.util.Log.i("OcrPackaging", name + ": " + JSON.stringify(result.steps[name]));
                }
                function texts(list) {
                    var out = [];
                    for (var i = 0; i < list.length; i++) out.push(String(list[i]));
                    return out;
                }
                function findTemplate(img) {
                    var template = images.clip(img, 32, 32, 96, 64);
                    try {
                        var point = images.findImage(img, template, { threshold: 0.9 });
                        if (!point) throw new Error("findImage returned null");
                        var matches = images.matchTemplate(img, template, { threshold: 0.9, max: 3 });
                        return { x: point.x, y: point.y, matches: matches.matches.length };
                    } finally {
                        template.recycle();
                    }
                }
                function mappedLibraries() {
                    var lines = java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/self/maps"));
                    var seen = {};
                    var out = [];
                    for (var i = 0; i < lines.size(); i++) {
                        var line = String(lines.get(i));
                        var match = /(\S*\/(lib[^\/\s]+\.so|[^\/\s]+\.apk))(!\/lib\/[^\s]+\.so)?\s*$/.exec(line);
                        if (!match) continue;
                        var path = match[1] + (match[3] || "");
                        if (!/opencv|RapidOcr|libNative|paddle|onnxruntime|c\+\+_shared|tbb|omp/.test(path)) continue;
                        if (!seen[path]) { seen[path] = true; out.push(path); }
                    }
                    return out;
                }
                function taskCount() {
                    return new java.io.File("/proc/self/task").list().length;
                }
                var img = images.read("ocr-test.png");
                try {
                    result.width = img.getWidth();
                    result.height = img.getHeight();
                    step("tasksBefore", taskCount);
                    step("paddle", function () { return texts(ocr.recognizeText(img, { mode: "paddle" })); });
                    step("rapid", function () { return texts(ocr.recognizeText(img, { mode: "rapid" })); });
                    step("auto", function () { return texts(ocr.recognizeText(img)); });
                    step("opencv", function () { return findTemplate(img); });
                    // Rhino cannot catch UnsatisfiedLinkError, so query the version only after OpenCV loaded.
                    if (result.steps.opencv.ok) {
                        step("opencvVersion", function () { return String(org.opencv.core.Core.getVersionString()); });
                    }
                    // Alternating and concurrent rounds only use the engines that worked on their own.
                    var available = {
                        image: result.steps.opencv.ok,
                        rapid: result.steps.rapid.ok,
                        paddle: result.steps.paddle.ok,
                    };
                    step("alternating", function () {
                        var rounds = [];
                        for (var i = 0; i < 3; i++) {
                            var round = {};
                            if (available.image) round.image = findTemplate(img);
                            if (available.rapid) round.rapid = texts(ocr.recognizeText(img, { mode: "rapid" }));
                            if (available.paddle) round.paddle = texts(ocr.recognizeText(img, { mode: "paddle" }));
                            rounds.push(round);
                        }
                        return rounds;
                    });
                    step("concurrent", function () {
                        var errors = java.util.Collections.synchronizedList(new java.util.ArrayList());
                        var counts = { image: 0, rapid: 0, paddle: 0 };
                        function loop(kind, fn) {
                            return threads.start(function () {
                                for (var i = 0; i < 5; i++) {
                                    try { fn(); counts[kind]++; } catch (error) { errors.add(kind + ": " + describe(error)); }
                                }
                            });
                        }
                        var workers = [];
                        if (available.image) workers.push(loop("image", function () { findTemplate(img); }));
                        if (available.rapid) workers.push(loop("rapid", function () { ocr.recognizeText(img, { mode: "rapid" }); }));
                        if (available.paddle) workers.push(loop("paddle", function () { ocr.recognizeText(img, { mode: "paddle" }); }));
                        workers.forEach(function (worker) { worker.join(300000); });
                        var list = [];
                        for (var i = 0; i < errors.size(); i++) list.push(String(errors.get(i)));
                        return { counts: counts, errors: list };
                    });
                    step("tasksAfter", taskCount);
                    step("maps", mappedLibraries);
                } finally {
                    img.recycle();
                }
                result.totalMillis = Date.now() - started;
                result.done = true;
                var output = new java.io.File(context.getExternalFilesDir(null), "ocr-packaging-result.json");
                files.write(output.getAbsolutePath(), JSON.stringify(result));
                ui.run(function () { ui.result.setText("OpenCV OCR packaging smoke finished"); });
            });
        """.trimIndent()
    }
}
