package org.autojs.autojs.engine.module

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.RequireBuilder
import java.io.FileNotFoundException
import java.io.Reader
import java.io.StringReader
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProcessWideAssetModuleScriptProviderTest {

    @Before
    fun clearCache() {
        ProcessWideRhinoAssetCache.clearForTests()
    }

    @After
    fun clearCacheAfterTest() {
        ProcessWideRhinoAssetCache.clearForTests()
    }

    @Test
    fun secondAssetRequestReusesCompilationWithoutReadingAgain() {
        val loader = CountingAssetLoader(
            "same-apk",
            mapOf("modules/example.js" to "module.exports = { value: 42 };")
        )
        val provider = ProcessWideAssetModuleScriptProvider(loader, "modules")

        val first = withRhinoContext { context ->
            provider.getModuleScript(context, "example", null, null, null)
        }
        val second = withRhinoContext { context ->
            provider.getModuleScript(context, "modules/./example.js", null, null, null)
        }

        assertSame(first, second)
        assertEquals(1, loader.readCount.get())
    }

    @Test
    fun cachedModuleScriptKeepsRequireExportsIsolatedBetweenEngines() {
        val loader = CountingAssetLoader(
            "exports-apk",
            mapOf("modules/state.js" to "module.exports = { marker: {}, value: 7 };")
        )
        val provider = ProcessWideAssetModuleScriptProvider(loader, "modules")

        val first = requireOnce(provider, "state")
        val second = requireOnce(provider, "state")

        assertNotSame(first, second)
        assertNotSame(
            ScriptableObject.getProperty(first, "marker"),
            ScriptableObject.getProperty(second, "marker"),
        )
        assertEquals(7, Context.toNumber(ScriptableObject.getProperty(first, "value")).toInt())
        assertEquals(7, Context.toNumber(ScriptableObject.getProperty(second, "value")).toInt())
        assertEquals(1, loader.readCount.get())
    }

    @Test
    fun relativeModulesInDifferentAssetDirectoriesDoNotShareCacheEntries() {
        val loader = CountingAssetLoader(
            "relative-apk",
            mapOf(
                "modules/alpha/main.js" to "module.exports = require('./value');",
                "modules/alpha/value.js" to "module.exports = { name: 'alpha' };",
                "modules/beta/main.js" to "module.exports = require('./value');",
                "modules/beta/value.js" to "module.exports = { name: 'beta' };",
            ),
        )
        val provider = ProcessWideAssetModuleScriptProvider(loader, "modules")

        val first = requireAll(provider, "alpha/main", "beta/main")
        val second = requireAll(provider, "alpha/main", "beta/main")

        assertEquals("alpha", ScriptableObject.getProperty(first[0], "name"))
        assertEquals("beta", ScriptableObject.getProperty(first[1], "name"))
        assertEquals("alpha", ScriptableObject.getProperty(second[0], "name"))
        assertEquals("beta", ScriptableObject.getProperty(second[1], "name"))
        assertNotSame(first[0], second[0])
        assertNotSame(first[1], second[1])
        assertEquals(4, loader.readCount.get())
    }

    @Test
    fun cachedBootstrapScriptExecutesConcurrentlyInIndependentContexts() {
        val loader = CountingAssetLoader(
            "concurrent-apk",
            mapOf(
                "init.js" to "this.counter = " +
                    "(typeof this.counter === 'undefined' ? 0 : this.counter) + 1;"
            )
        )
        val script = withRhinoContext { context ->
            ProcessWideRhinoAssetCache.getBootstrapScript(context, loader, "init.js", "<init>")
        }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val futures = List(2) {
                executor.submit<Int> {
                    start.await(5, TimeUnit.SECONDS)
                    withRhinoContext { context ->
                        val scope = context.initStandardObjects()
                        repeat(100) {
                            script.exec(context, scope, scope.globalThis)
                        }
                        Context.toNumber(ScriptableObject.getProperty(scope, "counter")).toInt()
                    }
                }
            }
            start.countDown()

            assertEquals(listOf(100, 100), futures.map { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, loader.readCount.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun fileHttpAndCwdModulesNeverEnterSharedAssetCache() {
        val loader = CountingAssetLoader("boundary-apk", emptyMap())
        val provider = ProcessWideAssetModuleScriptProvider(loader, "modules")

        withRhinoContext { context ->
            assertNull(provider.getModuleScript(context, "https://example.com/a.js", null, null, null))
            assertNull(provider.getModuleScript(context, "file:///sdcard/a.js", null, null, null))
            assertNull(provider.getModuleScript(context, "/sdcard/a.js", null, null, null))
            assertNull(
                provider.getModuleScript(
                    context,
                    "project-module",
                    URI("file:///sdcard/project/project-module.js"),
                    URI("file:///sdcard/project/"),
                    null,
                )
            )
        }

        assertEquals(0, loader.readCount.get())
    }

    @Test
    fun namespaceAndCompilationProfilePartitionTheCache() {
        val source = mapOf("modules/profile.js" to "module.exports = 1;")
        val firstLoader = CountingAssetLoader("apk-a", source)
        val secondLoader = CountingAssetLoader("apk-b", source)
        val firstProvider = ProcessWideAssetModuleScriptProvider(firstLoader, "modules")
        val secondProvider = ProcessWideAssetModuleScriptProvider(secondLoader, "modules")

        val apkA = withRhinoContext { context ->
            firstProvider.getModuleScript(context, "profile", null, null, null)
        }
        val apkB = withRhinoContext { context ->
            secondProvider.getModuleScript(context, "profile", null, null, null)
        }
        val differentProfile = withRhinoContext(Context.VERSION_1_8) { context ->
            firstProvider.getModuleScript(context, "profile", null, null, null)
        }

        assertNotSame(apkA, apkB)
        assertNotSame(apkA, differentProfile)
        assertEquals(2, firstLoader.readCount.get())
        assertEquals(1, secondLoader.readCount.get())
    }

    private fun requireOnce(
        provider: ProcessWideAssetModuleScriptProvider,
        moduleId: String,
    ): Scriptable = requireAll(provider, moduleId).single()

    private fun requireAll(
        provider: ProcessWideAssetModuleScriptProvider,
        vararg moduleIds: String,
    ): List<Scriptable> = withRhinoContext { context ->
        val scope = context.initStandardObjects()
        val require = RequireBuilder()
            .setModuleScriptProvider(provider)
            .setSandboxed(true)
            .createRequire(context, scope)
        moduleIds.map { moduleId ->
            require.call(context, scope, scope.globalThis, arrayOf(moduleId)) as Scriptable
        }
    }

    private fun <T> withRhinoContext(
        languageVersion: Int = Context.VERSION_ES6,
        block: (Context) -> T,
    ): T {
        val context = Context.enter().apply {
            isInterpretedMode = true
            this.languageVersion = languageVersion
            isGeneratingDebug = false
            isGeneratingSource = true
        }
        return try {
            block(context)
        } finally {
            Context.exit()
        }
    }

    private class CountingAssetLoader(
        override val namespace: String,
        private val sources: Map<String, String>,
    ) : RhinoAssetLoader {

        val readCount = AtomicInteger()

        override fun open(assetPath: String): Reader {
            readCount.incrementAndGet()
            val source = sources[assetPath] ?: throw FileNotFoundException(assetPath)
            return StringReader(source)
        }
    }
}
