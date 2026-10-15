package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.mcp.McpHostCapabilityBroker
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Path scope of model-facing plugin sessions (MCP roadmap P11 D26): working directory, read-only shared storage behind `files.shared_read`. */
class NodeBridgePluginSessionPathScopeInstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var work: File
    private lateinit var outside: File
    private var sharedFixture: File? = null
    private val sharedRoot: File @Suppress("DEPRECATION") get() = Environment.getExternalStorageDirectory()
    private val storageAccess get() = AllFilesAccessPermission(context).has()
    private val readToken = listOf("image", NodeBridgePermissionManifest.FILES_SHARED_READ)
    private val filesToken = listOf("files", NodeBridgePermissionManifest.FILES_SHARED_READ)

    @Before fun createFixtures() {
        work = File(context.cacheDir, "node-bridge-scope-work").apply { deleteRecursively(); check(mkdirs()) }
        outside = File(context.cacheDir, "node-bridge-scope-outside").apply { deleteRecursively(); check(mkdirs()) }
        writePng(File(work, "inside.png"))
        writePng(File(outside, "outside.png"))
        if (storageAccess) {
            val download = File(sharedRoot, "Download").apply { mkdirs() }
            sharedFixture = File(download, "autojs6-bridge-scope-${System.nanoTime()}.png").also { writePng(it) }
        }
    }

    @After fun removeFixtures() {
        work.deleteRecursively()
        outside.deleteRecursively()
        sharedFixture?.delete()
    }

    @Test
    fun mcpSessionsReadTheWorkingDirectoryButNotArbitraryAbsolutePaths() {
        val provider = mcpProvider()
        try {
            val inside = dispatch(provider, "image", "readImage", JSONArray().put("inside.png").toString(), listOf("image"))
            assertTrue(inside.toString(), inside.ok)
            recycle(provider, JSONObject(inside.resultJson.orEmpty()))

            val insideAbsolute = dispatch(provider, "image", "readImage", JSONArray().put(File(work, "inside.png").absolutePath).toString(), listOf("image"))
            assertTrue(insideAbsolute.toString(), insideAbsolute.ok)
            recycle(provider, JSONObject(insideAbsolute.resultJson.orEmpty()))

            val foreign = dispatch(provider, "image", "readImage", JSONArray().put(File(outside, "outside.png").absolutePath).toString(), readToken)
            assertDenied(foreign, "outside the working directory")

            val mediainfo = dispatch(provider, "mediainfo", "read", JSONArray().put(File(outside, "outside.png").absolutePath).toString(), listOf("media", "media.metadata"))
            assertDenied(mediainfo, "mediainfo.read")

            val handle = JSONObject(dispatch(provider, "image", "readImage", JSONArray().put("inside.png").toString(), listOf("image")).resultJson.orEmpty())
            val save = dispatch(provider, "image", "saveImage", JSONArray().put(handle).put(File(outside, "copy.png").absolutePath).toString(), listOf("image"))
            assertDenied(save, "only write inside the working directory")
            assertFalse(File(outside, "copy.png").exists())
            recycle(provider, handle)
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    @Test
    fun sharedStorageIsReadOnlyBehindTheToken() {
        val provider = mcpProvider()
        try {
            val photo = sharedFixture?.absolutePath ?: File(sharedRoot, "Download/missing.png").absolutePath
            val noToken = dispatch(provider, "image", "readImage", JSONArray().put(photo).toString(), listOf("image"))
            assertDenied(noToken, "files.shared_read")

            val hidden = dispatch(provider, "image", "readImage", JSONArray().put(File(sharedRoot, "Android/data/x.png").absolutePath).toString(), readToken)
            assertDenied(hidden, "Android/data")

            val withToken = dispatch(provider, "image", "readImage", JSONArray().put(photo).toString(), readToken)
            if (!storageAccess) {
                assertDenied(withToken, "all-files access")
                return
            }
            assertTrue(withToken.toString(), withToken.ok)
            val handle = JSONObject(withToken.resultJson.orEmpty())
            assertEquals(1.0, handle.getDouble("scale"), 1e-9)
            recycle(provider, handle)

            val list = dispatch(provider, "files", "list", JSONArray().put(File(sharedRoot, "Download").absolutePath).toString(), filesToken)
            assertTrue(list.toString(), list.ok)
            val listing = JSONObject(list.resultJson.orEmpty())
            assertEquals(sharedRoot.canonicalFile.absolutePath, listing.getString("root"))
            assertEquals("Download", listing.getString("path"))
            val names = (0 until listing.getJSONArray("entries").length()).map { listing.getJSONArray("entries").getJSONObject(it).getString("name") }
            assertTrue(names.toString(), sharedFixture!!.name in names)

            val rootListing = JSONObject(dispatch(provider, "files", "list", JSONArray().put(File(sharedRoot, "Android").absolutePath).toString(), filesToken).resultJson.orEmpty())
            val androidChildren = (0 until rootListing.getJSONArray("entries").length()).map { rootListing.getJSONArray("entries").getJSONObject(it).getString("name") }
            assertFalse(androidChildren.toString(), "data" in androidChildren)
            assertFalse(androidChildren.toString(), "obb" in androidChildren)

            val stat = dispatch(provider, "files", "stat", JSONArray().put(photo).toString(), filesToken)
            assertTrue(stat.toString(), stat.ok)
            assertTrue(JSONObject(stat.resultJson.orEmpty()).getBoolean("exists"))

            val listNoToken = dispatch(provider, "files", "list", JSONArray().put(File(sharedRoot, "Download").absolutePath).toString(), listOf("files"))
            assertDenied(listNoToken, "files.shared_read")

            val read = dispatch(provider, "files", "read", JSONArray().put(photo).toString(), filesToken)
            assertFalse("files.read must stay inside the working directory: $read", read.ok)
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    @Test
    fun scriptSessionsKeepResolvingAbsolutePaths() {
        val info = NodeBridgeEngineInfo.fromContext(context, id = "scope-script", sourceName = "scope.cjs", cwd = work.absolutePath)
        val provider = AndroidNodeBridgeCapabilityProvider(context, info)
        try {
            val foreign = dispatch(provider, "image", "readImage", JSONArray().put(File(outside, "outside.png").absolutePath).toString(), listOf("image"))
            assertTrue(foreign.toString(), foreign.ok)
            recycle(provider, JSONObject(foreign.resultJson.orEmpty()))
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    private fun mcpProvider(): AndroidNodeBridgeCapabilityProvider {
        val info = NodeBridgeEngineInfo.fromContext(context, id = "scope-mcp", sourceName = "scope.cjs", cwd = work.absolutePath)
            .copy(backend = "plugin", engineName = McpHostCapabilityBroker.ENGINE_NAME)
        return AndroidNodeBridgeCapabilityProvider(context, info)
    }

    private fun assertDenied(response: NodeBridgeResponse, fragment: String) {
        assertFalse(response.toString(), response.ok)
        assertEquals(response.toString(), NodeBridgeErrorCategory.PERMISSION_DENIED, response.error?.category)
        assertTrue(response.toString(), response.error?.message.orEmpty().contains(fragment))
    }

    private fun recycle(provider: NodeBridgeCapabilityProvider, handle: JSONObject) {
        assertTrue(dispatch(provider, "image", "recycle", JSONArray().put(handle).toString(), listOf("image")).ok)
    }

    private fun writePng(file: File) {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            file.outputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        timeoutMs: Long = 10_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method", latch.await(timeoutMs + 2_000L, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing response for $module.$method" }
    }
}
