package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class NodeImageOperationsInstrumentationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun transformsAndSearchesUseHostPixelsAndKeepInputHandlesAlive() = withFixture { directory ->
        val result = runScript(directory, """
            const image = require('images');
            const source = await image.readImage('fixture.png');
            const full = await image.clip(source, 0, 0, source.width, source.height);
            await image.recycle(full);
            const point = await image.findColor(source, '#14be55', {threshold: 0});
            if (!point || point.x !== 64 || point.y !== 48) throw new Error('Color coordinates: ' + JSON.stringify(point));
            const multi = await image.findMultiColors(source, '#14be55', [[4, 0, '#e21b40']], {threshold: 0, region: [50, 40, 60, 50]});
            if (!multi || multi.x !== 64 || multi.y !== 48) throw new Error('Multi-color coordinates: ' + JSON.stringify(multi));
            if (await image.findColor(source, '#00ffff', {threshold: 0}) !== null) throw new Error('Absent color matched');
            const clipped = await image.clip(source, 64, 48, 40, 32);
            await image.saveImage(clipped, 'template.png');
            const template = await image.readImage('template.png');
            const found = await image.findImage(source, template, {threshold: 0.99, region: [40, 32, 96, 80]});
            if (!found || Math.abs(found.x - 64) > 2 || Math.abs(found.y - 48) > 2) throw new Error('Template coordinates: ' + JSON.stringify(found));
            const matches = await image.matchTemplate(source, template, {threshold: 0.99, limit: 1});
            if (matches.length !== 1 || matches[0].score < 0.99 || Math.abs(matches[0].x - 64) > 2 || Math.abs(matches[0].y - 48) > 2) throw new Error('Template matches: ' + JSON.stringify(matches));
            const resized = await image.resize(clipped, 80, 64);
            if (resized.width !== 80 || resized.height !== 64) throw new Error('Resize dimensions');
            const gray = await image.grayscale(resized);
            const binary = await image.threshold(resized, {threshold: 127, type: 'binary'});
            await image.saveImage(gray, 'gray.png');
            await image.saveImage(binary, 'binary.png');
            for (const handle of [template, clipped, resized, gray, binary, source]) await image.recycle(handle);
            console.log('m14.image.analysis=PASS point=' + JSON.stringify(found));
        """.trimIndent())
        NodePluginTestHarness.assertSucceeded("image analysis", result)
        assertTrue(result.stdout, result.stdout.contains("m14.image.analysis=PASS"))
        val gray = BitmapFactory.decodeFile(File(directory, "gray.png").path)
        val binary = BitmapFactory.decodeFile(File(directory, "binary.png").path)
        assertEquals(80, gray.width)
        assertEquals(64, binary.height)
        val values = mutableSetOf<Int>()
        for (y in 0 until 64) for (x in 0 until 80) {
            val pixel = gray.getPixel(x, y)
            assertEquals(Color.red(pixel), Color.green(pixel))
            assertEquals(Color.red(pixel), Color.blue(pixel))
            val bit = binary.getPixel(x, y)
            assertTrue("Non-binary pixel: $bit", bit == Color.BLACK || bit == Color.WHITE)
            values += bit
        }
        assertEquals(setOf(Color.BLACK, Color.WHITE), values)
        gray.recycle()
        binary.recycle()
    }

    @Test fun invalidRegionsRecycledHandlesAndHandleLimitAreReported() = withFixture { directory ->
        val result = runScript(directory, """
            const images = require('images');
            const original = await images.readImage('fixture.png');
            const expectFailure = async (operation, text) => {
              try { await operation(); } catch (error) {
                if (!String(error.message).toLowerCase().includes(text)) throw error;
                return;
              }
              throw new Error('Expected failure: ' + text);
            };
            await expectFailure(() => images.clip(original, 250, 0, 10, 10), 'region');
            await expectFailure(() => images.findImage(original, original, {region: [0, 0, 20, 20]}), 'template');
            const copy = await images.clip(original, 0, 0, 4, 4);
            await images.recycle(copy);
            await expectFailure(() => images.grayscale(copy), 'active image handle');
            const copies = [];
            for (let i = 0; i < 31; ++i) copies.push(await images.clip(original, 0, 0, 4, 4));
            await expectFailure(() => images.clip(original, 0, 0, 4, 4), '32 active');
            for (const handle of copies) await images.recycle(handle);
            const replacement = await images.clip(original, 0, 0, 4, 4);
            await images.recycle(replacement);
            await images.recycle(original);
            console.log('m14.image.lifecycle=PASS');
        """.trimIndent())
        NodePluginTestHarness.assertSucceeded("image limits and lifetime", result)
        assertTrue(result.stdout, result.stdout.contains("m14.image.lifecycle=PASS"))
    }

    private fun runScript(directory: File, script: String) = NodePluginTestHarness.runPluginScript(
        "(async () => {\n$script\n})().catch(error => { console.error(error.stack); process.exitCode = 1; });",
        "image-analysis.cjs", workingDirectory = directory, timeoutMs = 60_000,
        runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to NodeBridgePermissionManifest
            .fromDeclaredCapabilities(listOf("image")).toJson()),
    )

    private fun withFixture(test: (File) -> Unit) {
        val directory = File(context.cacheDir, "node-image-${UUID.randomUUID()}").apply { mkdirs() }
        val bitmap = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(10, 10, 10)) }
        for (y in 0 until 32) for (x in 0 until 40) {
            bitmap.setPixel(64 + x, 48 + y, Color.rgb((x * 71 + y * 39) % 256, (x * 13 + y * 109) % 256, (x * 97 + y * 23) % 256))
        }
        bitmap.setPixel(64, 48, Color.rgb(20, 190, 85))
        bitmap.setPixel(68, 48, Color.rgb(226, 27, 64))
        File(directory, "fixture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try { test(directory) } finally {
            directory.listFiles()?.forEach { it.delete() }
            directory.delete()
        }
    }
}
