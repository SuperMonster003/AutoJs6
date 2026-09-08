package org.autojs.autojs.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NodeImageBytesInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun pngAnd1080pRgbaCrossBinderAsBuffers() = checkTransport("jni")
    @Test fun fileFallbackUsesTheSameBinaryAttachments() = checkTransport("file")

    private fun checkTransport(transport: String) {
        val directory = File(context.cacheDir, "node-pixels-${UUID.randomUUID()}").apply { mkdirs() }
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val scaled = Bitmap.createScaledBitmap(screenshot, 1080, 1920, false)
        // Device screenshots may be Display P3; draw into sRGB before setting exact fixture colors.
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== screenshot) scaled.recycle()
        screenshot.recycle()
        bitmap.setHasAlpha(true)
        bitmap.setPixel(0, 0, Color.rgb(20, 190, 85))
        bitmap.setPixel(1, 0, Color.argb(128, 16, 32, 48))
        File(directory, "screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        // The npm tree is maintained in the runtime repository's test APK, including its license.
        val assets = context.createPackageContext("io.github.supermonster003.autojs6.plugin.nodejs.test", 0).assets
        copyAssets(assets, "npm_ecosystem_corpus/node_modules/pngjs", File(directory, "node_modules/pngjs"))
        try {
            val result = NodePluginTestHarness.runPluginScript(
                """
                (async () => {
                  const image = require('image'), {PNG} = require('pngjs');
                  const source = await image.readImage('screen.png');
                  const times = [];
                  let first;
                  for (let i = 0; i < 8; ++i) {
                    const start = performance.now();
                    const rgba = await image.toBytes(source, {format: 'rgba'});
                    times.push(performance.now() - start);
                    if (!Buffer.isBuffer(rgba) || rgba.length !== 1080 * 1920 * 4) throw new Error('RGBA byte count');
                    if (rgba[0] !== 20 || rgba[1] !== 190 || rgba[2] !== 85 || rgba[3] !== 255) throw new Error('RGBA channel order: ' + [...rgba.subarray(0,8)]);
                    if (rgba[4] !== 16 || rgba[5] !== 32 || rgba[6] !== 48 || rgba[7] !== 128) throw new Error('RGBA alpha conversion: ' + [...rgba.subarray(4,8)]);
                    const view = new Uint8Array(rgba.buffer, rgba.byteOffset, rgba.byteLength);
                    view[0] = 200;
                    if (rgba[0] !== 200) throw new Error('Buffer must share its mapped ArrayBuffer');
                    first ||= rgba;
                  }
                  const encoded = await image.toBytes(source, 'png');
                  const decoded = PNG.sync.read(encoded);
                  if (decoded.width !== 1080 || decoded.height !== 1920 || decoded.data[0] !== 20) throw new Error('PNG decode or source bitmap mutated');
                  await image.recycle(source);
                  if (first[0] !== 200 || first.length !== 8294400) throw new Error('Buffer lifetime depends on image handle');
                  const median = [...times].sort((a,b) => a-b)[4];
                  console.log('m14.bytes.$transport=' + JSON.stringify({times, median, png: encoded.length, rgba: first.length}));
                  if (median >= 200) throw new Error('1080p RGBA median exceeded 200 ms');
                })().catch(error => { console.error(error.stack); process.exitCode = 1; });
                """.trimIndent(),
                "image-bytes-$transport.cjs", workingDirectory = directory, timeoutMs = 60_000,
                moduleSources = File(directory, "node_modules/pngjs").walkTopDown()
                    .filter { it.isFile && (it.extension == "js" || it.name == "package.json") && it.name != "browser.js" }
                    .associate { it.relativeTo(directory).invariantSeparatorsPath to it.readText() },
                runtimeModuleSources = mapOf(
                    NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("image")).toJson(),
                    "autojs6:bridge-live-config" to """{"transport":"$transport"}""",
                ),
            )
            NodePluginTestHarness.assertSucceeded("image bytes $transport", result)
            assertEquals(transport, result.nativeValues["embedded_script.bridge_live_transport"])
            println(result.stdout)
            assertTrue("temporary image payload left on disk", context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("node-image-bytes-") })
        } finally { directory.walkBottomUp().forEach { it.delete() } }
    }

    private fun copyAssets(assets: AssetManager, source: String, destination: File) {
        val children = assets.list(source).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(source).use { input -> destination.outputStream().use { input.copyTo(it) } }
        } else {
            destination.mkdirs()
            children.forEach { copyAssets(assets, "$source/$it", File(destination, it)) }
        }
    }
}
