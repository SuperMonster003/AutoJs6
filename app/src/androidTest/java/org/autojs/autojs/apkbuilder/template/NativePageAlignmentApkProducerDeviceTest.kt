package org.autojs.autojs.apkbuilder.template

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.apkbuilder.ApkBuildLibrary
import org.autojs.autojs.core.plugin.nativealign.ElfLoadAlignment
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.project.ProjectConfig
import org.autojs.plugin.apkbuilder.template.ApkBuildResult
import org.autojs.plugin.apkbuilder.template.ApkBuilderTemplatePluginIds
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Produces a real signed user APK. tools/16kb/verify_user_apk.py installs and runs its script. */
class NativePageAlignmentApkProducerDeviceTest {
    @Test fun producesNativeRuntimeApk() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autojs.nativeAlignment.produceApk").toBoolean())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "native-page-alignment-project").apply { mkdirs() }
        val output = File(context.getExternalFilesDir(null), "native-page-alignment-smoke.apk")
        val rapidVersion = InstrumentationRegistry.getArguments().getString("autojs.nativeAlignment.rapidVersion", "3")
        require(rapidVersion in setOf("3", "6"))
        val paddlePackages = (3..6).map { "io.github.supermonster003.autojs6.plugin.paddleocr.v$it" }
        val rapidPackages = listOf(3, 6).map { "io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv$it" }
        val fixturePackages = paddlePackages + rapidPackages
        val previousEnabled = fixturePackages.associateWith { PluginEnableStore.isEnabled(context, it) }
        try {
            val bitmap = BitmapFactory.decodeResource(context.resources, com.baidu.paddle.lite.ocr.R.drawable.paddle_ocr_test)
            // Match the existing live OCR bridge fixture, including its 4x scale.
            val scaled = Bitmap.createScaledBitmap(bitmap, bitmap.width * 4, bitmap.height * 4, false)
            File(source, "ocr-test.png").outputStream().use { assertTrue(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            scaled.recycle()
            bitmap.recycle()
            // Paddle plugins currently ship ARM ABIs only; the x86_64 fixture exercises Rapid.
            val includePaddle = Build.SUPPORTED_ABIS.first() == "arm64-v8a"
            val includeOpenCv = InstrumentationRegistry.getArguments().getString("autojs.nativeAlignment.includeOpenCv", "true").toBoolean()
            File(source, "main.js").writeText(SCRIPT.replace("__WITH_PADDLE__", includePaddle.toString()))
            val config = ProjectConfig().setName("16 KB native runtime smoke")
                .setPackageName("org.autojs.autojs6.nativepagesmoke").setVersionName("1.0").setVersionCode(1)
                .setMainScriptFileName("main.js").setProjectType("rhino").setSourcePath(source.absolutePath)
                .setAbis(listOf(Build.SUPPORTED_ABIS.first()))
                .setLibs((listOf(ApkBuildLibrary.IMAGE_QUANT, ApkBuildLibrary.RAPID_OCR) +
                    (if (includeOpenCv) listOf(ApkBuildLibrary.OPENCV) else emptyList()) +
                    (if (includePaddle) listOf(ApkBuildLibrary.PADDLE_OCR) else emptyList())).map { it.label })
                .setPermissions(emptyList())
            File(source, "project.json").writeText(config.toJson(false))
            fixturePackages.forEach { name ->
                PluginEnableStore.setEnabled(context, name,
                    name == paddlePackages.first() || name == "io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv$rapidVersion")
            }
            val outcome = RemoteApkBuildClient.buildBlocking(context, config, output, cancelSignal = null,
                preferredPluginPackageName = ApkBuilderTemplatePluginIds.PACKAGE_NAME)
            assertEquals(outcome.errors.toString(), ApkBuildResult.STATUS_OK, outcome.status)
            assertTrue(outcome.errors.toString(), outcome.errors.isEmpty())
            ZipFile(output).use { zip ->
                val libraries = zip.entries().asSequence().filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.toList()
                // OCR engines link a private OpenCV, so libopencv_java4.so is expected only with the OpenCV plugin.
                val names = libraries.map { it.name.substringAfterLast('/') }.toSet()
                val expected = listOf("libjackpal-androidterm5.so", "libjackpal-termexec2.so", "libRapidOcr.so", "libonnxruntime.so") +
                    (if (includeOpenCv) listOf("libopencv_java4.so") else emptyList()) +
                    (if (includePaddle) listOf("libNative.so", "libpaddle_light_api_shared.so") else emptyList())
                assertTrue("Missing ${expected - names} in $names", names.containsAll(expected))
                for (entry in libraries) {
                    assertEquals(entry.name, ZipEntry.DEFLATED, entry.method)
                    val alignment = zip.getInputStream(entry).use { input ->
                        val prefix = ByteArray(minOf(entry.size, 65536L).toInt())
                        java.io.DataInputStream(input).readFully(prefix)
                        ElfLoadAlignment.parse(prefix).minLoadAlign
                    }
                    assertTrue("${entry.name}: $alignment", alignment >= 16384)
                }
            }
        } finally {
            previousEnabled.forEach { (name, enabled) -> PluginEnableStore.setEnabled(context, name, enabled) }
            source.deleteRecursively()
        }
    }

    private companion object {
        val SCRIPT = """
            "ui";
            ui.layout(<vertical padding="24"><text id="result" text="Native runtime smoke running" /></vertical>);
            threads.start(function () {
                var result = { pageSize: android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE) };
                try {
                    // Trigger JNI registration through the app loader used by the terminal itself.
                    java.lang.Class.forName("jackpal.androidterm.Exec", true, context.getClassLoader());
                    java.lang.Class.forName("jackpal.androidterm.TermExec", true, context.getClassLoader());
                    var pty = android.os.ParcelFileDescriptor.open(new java.io.File("/dev/ptmx"), android.os.ParcelFileDescriptor.MODE_READ_WRITE);
                    try {
                        // TermExec consumes its command list; Rhino NativeArray is not mutable as a Java List.
                        var argv = new java.util.ArrayList();
                        ["/system/bin/sh", "sh", "-c", "exit 37"].forEach(function (arg) { argv.add(arg); });
                        var command = new Packages.jackpal.androidterm.TermExec(argv);
                        result.terminalExit = Packages.jackpal.androidterm.TermExec.waitFor(command.start(pty));
                        if (result.terminalExit !== 37) throw new Error("Unexpected PTY exit");
                    } finally { pty.close(); }
                    var img = images.read("ocr-test.png");
                    try {
                        result.width = img.getWidth();
                        result.quantizedBytes = images.quantize(img).size;
                        android.util.Log.i("Native16k", "Image quantization passed; starting OCR");
                        if (__WITH_PADDLE__) result.paddle = ocr.recognizeText(img, { mode: "paddle" });
                        android.util.Log.i("Native16k", "Paddle result: " + JSON.stringify(result.paddle));
                        result.rapid = ocr.recognizeText(img, { mode: "rapid" });
                        android.util.Log.i("Native16k", "Rapid result: " + JSON.stringify(result.rapid));
                        if ((__WITH_PADDLE__ && !result.paddle.length) || !result.rapid.length || !result.quantizedBytes) throw new Error("Empty native output");
                    } finally { img.recycle(); }
                    result.ok = true;
                } catch (error) { result.ok = false; result.error = String(error) + "\n" + error.stack; }
                files.write(new java.io.File(context.getExternalFilesDir(null), "native-16kb-result.json").getAbsolutePath(), JSON.stringify(result));
                ui.run(function () { ui.result.setText(result.ok ? "16 KB native runtime PASS" : result.error); });
            });
        """.trimIndent()
    }
}
