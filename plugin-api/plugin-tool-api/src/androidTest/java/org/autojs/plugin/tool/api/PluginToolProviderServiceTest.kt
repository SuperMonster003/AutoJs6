package org.autojs.plugin.tool.api

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.plugin.common.api.PluginInfo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PluginToolProviderService (MCP roadmap D59) answers the contract on behalf of its subclass: version checks, unknown tools,
 * inputs, file and directory outputs, cancel, error mapping and limits. The provider runs in the test process, so the
 * Binder calls are direct; the host side of the same contract is covered by the host bridge tests.
 */
@RunWith(AndroidJUnit4::class)
class PluginToolProviderServiceTest {

    private val service = TestProvider()
    private val provider = IPluginToolProvider.Stub.asInterface(service.onBind(null))

    @After
    fun tearDown() {
        service.onDestroy()
    }

    @Test
    fun infoCarriesTheContractVersion() {
        val info = provider.info
        assertEquals("Base class test", info.name)
        assertEquals(PluginToolContract.CONTRACT_VERSION, info.capabilities!!.getInt(PluginToolCapabilityKeys.CONTRACT_VERSION))
        assertEquals("kept", info.capabilities!!.getString("own"))
    }

    @Test
    fun catalogIsAnsweredOrRefused() {
        val answer = provider.getCatalog(catalogRequest(PluginToolContract.CONTRACT_VERSION))
        assertEquals("basetest", JSONObject(answer.getString(PluginToolCatalogKeys.CATALOG_JSON)!!).getString(PluginToolCatalogJson.SHORT_NAME))
        assertEquals(
            PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION,
            provider.getCatalog(catalogRequest(PluginToolContract.MAX_CONTRACT_VERSION + 1)).getString(PluginToolCatalogKeys.ERROR_CODE),
        )
        service.catalogError = PluginToolException(PluginToolErrorCodes.NOT_READY, "the model is missing")
        val refused = provider.getCatalog(catalogRequest(PluginToolContract.CONTRACT_VERSION))
        assertNull(refused.getString(PluginToolCatalogKeys.CATALOG_JSON))
        assertEquals(PluginToolErrorCodes.NOT_READY, refused.getString(PluginToolCatalogKeys.ERROR_CODE))
        assertEquals("the model is missing", refused.getString(PluginToolCatalogKeys.ERROR_MESSAGE))
    }

    @Test
    fun refusedCallsCloseTheirInputs() {
        val input = descriptorOf("abc")
        val answer = provider.invoke(invokeRequest("missing"), arrayOf(input), Recorder())
        assertFalse(answer.getBoolean(PluginToolAcceptKeys.ACCEPTED))
        assertEquals(PluginToolErrorCodes.UNKNOWN_TOOL, answer.getString(PluginToolAcceptKeys.ERROR_CODE))
        assertClosed(input)

        val old = invokeRequest("echo").apply { putInt(PluginToolInvokeKeys.CONTRACT_VERSION, PluginToolContract.MAX_CONTRACT_VERSION + 1) }
        assertEquals(PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, provider.invoke(old, emptyArray(), Recorder()).getString(PluginToolAcceptKeys.ERROR_CODE))
    }

    @Test
    fun echoReportsJsonAndText() {
        val recorder = call("echo", JSONObject().put("text", "hello"))
        val result = recorder.result()
        assertEquals("hello", JSONObject(result.getString(PluginToolResultKeys.RESULT_JSON)!!).getString("text"))
        assertEquals("hello", result.getString(PluginToolResultKeys.TEXT))
        assertEquals("[]", result.getString(PluginToolResultKeys.OUTPUTS_JSON))
        assertEquals(1, recorder.reports)
    }

    @Test
    fun inputsAreHandedOutByNameAndClosed() {
        val input = descriptorOf("0123456789")
        val inputs = JSONArray().put(JSONObject().put("name", "file").put("source", PluginToolValues.INPUT_SOURCE_FILE).put("displayName", "a.txt").put("byteCount", 10))
        val result = call("inspect", JSONObject(), inputs = arrayOf(input), inputsJson = inputs).result()
        val json = JSONObject(result.getString(PluginToolResultKeys.RESULT_JSON)!!)
        assertEquals(10, json.getInt("bytes"))
        assertEquals("a.txt", json.getString("displayName"))
        assertClosed(input)
    }

    @Test
    fun fileOutputsReachOnlyCallersThatWantThem() {
        val wanted = call("emit", JSONObject().put("text", "xyz"), outputs = listOf("data"))
        val outputs = JSONArray(wanted.result().getString(PluginToolResultKeys.OUTPUTS_JSON)!!)
        assertEquals(1, outputs.length())
        assertEquals("data", outputs.getJSONObject(0).getString(PluginToolOutputJson.NAME))
        assertEquals("xyz", wanted.outputTexts.single())

        val unwanted = call("emit", JSONObject().put("text", "xyz"))
        assertEquals("[]", unwanted.result().getString(PluginToolResultKeys.OUTPUTS_JSON))
        assertTrue(unwanted.outputTexts.isEmpty())
    }

    @Test
    fun directoryOutputsWriteAndSkipExistingFiles() {
        val tree = FakeTree(existing = setOf("b.txt"))
        val trees = Bundle().apply { putBinder("tree", tree) }
        val result = call("split", JSONObject().put("files", JSONArray(listOf("a.txt", "b.txt", "sub/c.txt"))), outputs = listOf("tree"), trees = trees).result()
        val json = JSONObject(result.getString(PluginToolResultKeys.RESULT_JSON)!!)
        assertEquals(2, json.getInt("written"))
        assertEquals("b.txt", json.getJSONArray("existing").getString(0))
        assertEquals(setOf("a.txt", "sub/c.txt"), tree.files.keys)
        assertEquals("content of sub/c.txt", tree.files["sub/c.txt"]!!.readText())
        assertEquals(listOf("sub"), tree.directories)
    }

    @Test
    fun cancelInterruptsTheCall() {
        val recorder = Recorder()
        val request = invokeRequest("slow")
        assertTrue(provider.invoke(request, emptyArray(), recorder).getBoolean(PluginToolAcceptKeys.ACCEPTED))
        assertTrue(service.started.await(5, TimeUnit.SECONDS))
        provider.cancel(Bundle().apply {
            putString(PluginToolCancelKeys.INVOCATION_ID, request.getString(PluginToolInvokeKeys.INVOCATION_ID))
            putString(PluginToolCancelKeys.REASON, PluginToolValues.CANCEL_REASON_TIMEOUT)
        })
        val error = recorder.error()
        assertEquals(PluginToolErrorCodes.CANCELLED, error.getString(PluginToolErrorKeys.CODE))
        assertTrue(error.getString(PluginToolErrorKeys.MESSAGE)!!.contains(PluginToolValues.CANCEL_REASON_TIMEOUT))
        assertTrue(service.listenerRan)
    }

    @Test
    fun runningInvocationIdsAreNotReused() {
        val request = invokeRequest("slow")
        assertTrue(provider.invoke(request, emptyArray(), Recorder()).getBoolean(PluginToolAcceptKeys.ACCEPTED))
        val again = provider.invoke(request, emptyArray(), Recorder())
        assertFalse(again.getBoolean(PluginToolAcceptKeys.ACCEPTED))
        assertEquals(PluginToolErrorCodes.FAILED, again.getString(PluginToolAcceptKeys.ERROR_CODE))
    }

    @Test
    fun failuresAreMappedToErrorCodes() {
        assertEquals(PluginToolErrorCodes.NOT_READY, call("fail", JSONObject().put("kind", "plugin")).error().getString(PluginToolErrorKeys.CODE))
        assertEquals(PluginToolErrorCodes.INVALID_ARGUMENTS, call("fail", JSONObject().put("kind", "argument")).error().getString(PluginToolErrorKeys.CODE))
        val other = call("fail", JSONObject().put("kind", "io")).error()
        assertEquals(PluginToolErrorCodes.FAILED, other.getString(PluginToolErrorKeys.CODE))
        assertEquals("IOException: disk gone", other.getString(PluginToolErrorKeys.MESSAGE))
        assertEquals(PluginToolErrorCodes.LIMIT_EXCEEDED, call("huge", JSONObject()).error().getString(PluginToolErrorKeys.CODE))
    }

    @Test
    fun javaSubclassesWork() {
        val java = JavaEchoProviderService()
        try {
            val provider = IPluginToolProvider.Stub.asInterface(java.onBind(null))
            assertEquals(PluginToolContract.CONTRACT_VERSION, provider.info.capabilities!!.getInt(PluginToolCapabilityKeys.CONTRACT_VERSION))
            val recorder = Recorder()
            assertTrue(provider.invoke(invokeRequest("echo", JSONObject().put("text", "java")), emptyArray(), recorder).getBoolean(PluginToolAcceptKeys.ACCEPTED))
            assertEquals("java", JSONObject(recorder.result().getString(PluginToolResultKeys.RESULT_JSON)!!).getString("text"))
        } finally {
            java.onDestroy()
        }
    }

    private fun call(
        tool: String,
        arguments: JSONObject,
        inputs: Array<ParcelFileDescriptor> = emptyArray(),
        inputsJson: JSONArray = JSONArray(),
        outputs: List<String> = emptyList(),
        trees: Bundle? = null,
    ): Recorder {
        val request = invokeRequest(tool, arguments).apply {
            putString(PluginToolInvokeKeys.INPUTS_JSON, inputsJson.toString())
            putString(PluginToolInvokeKeys.OUTPUTS_JSON, JSONArray(outputs).toString())
            trees?.let { putBundle(PluginToolInvokeKeys.OUTPUT_TREES, it) }
        }
        val recorder = Recorder()
        val answer = provider.invoke(request, inputs, recorder)
        assertTrue(answer.getString(PluginToolAcceptKeys.ERROR_MESSAGE), answer.getBoolean(PluginToolAcceptKeys.ACCEPTED))
        return recorder
    }

    private fun invokeRequest(tool: String, arguments: JSONObject = JSONObject()) = Bundle().apply {
        putInt(PluginToolInvokeKeys.CONTRACT_VERSION, PluginToolContract.CONTRACT_VERSION)
        putString(PluginToolInvokeKeys.INVOCATION_ID, UUID.randomUUID().toString())
        putString(PluginToolInvokeKeys.TOOL, tool)
        putString(PluginToolInvokeKeys.ARGUMENTS_JSON, arguments.toString())
        putString(PluginToolInvokeKeys.INPUTS_JSON, "[]")
        putString(PluginToolInvokeKeys.OUTPUTS_JSON, "[]")
        putLong(PluginToolInvokeKeys.TIMEOUT_MS, 10_000L)
        putString(PluginToolInvokeKeys.CONSUMER, PluginToolValues.CONSUMER_SCRIPT)
    }

    private fun catalogRequest(version: Int) = Bundle().apply { putInt(PluginToolCatalogRequestKeys.CONTRACT_VERSION, version) }

    private fun descriptorOf(text: String): ParcelFileDescriptor {
        val file = File.createTempFile("input-", ".txt").apply { writeText(text) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also { file.delete() }
    }

    private fun assertClosed(descriptor: ParcelFileDescriptor) {
        val closed = runCatching { descriptor.fd }.exceptionOrNull()
        assertNotNull("the descriptor is still open", closed)
    }

    /** Records the one report of a call; output descriptors are read at once, as the host does. */
    private class Recorder : IPluginToolCallback.Stub() {
        private val done = CountDownLatch(1)

        @Volatile
        var reports = 0

        private var result: Bundle? = null
        private var error: Bundle? = null
        val outputTexts = ArrayList<String>()

        override fun onProgress(update: Bundle?) = Unit

        override fun onResult(result: Bundle?, outputs: Array<out ParcelFileDescriptor>?) {
            // Direct Binder calls hand over the provider's own descriptors, so read them without closing.
            outputs?.forEach { outputTexts += ParcelFileDescriptor.AutoCloseInputStream(it.dup()).use { stream -> stream.readBytes().decodeToString() } }
            this.result = result
            reports++
            done.countDown()
        }

        override fun onError(error: Bundle?) {
            this.error = error
            reports++
            done.countDown()
        }

        fun result(): Bundle {
            assertTrue("no report", done.await(10, TimeUnit.SECONDS))
            assertNull("unexpected error ${error?.getString(PluginToolErrorKeys.CODE)}: ${error?.getString(PluginToolErrorKeys.MESSAGE)}", error)
            return result!!
        }

        fun error(): Bundle {
            assertTrue("no report", done.await(10, TimeUnit.SECONDS))
            assertNull("unexpected result", result)
            return error!!
        }
    }

    /** Stands in for the host's staging tree: answers EXISTS for [existing], keeps every file it opened. */
    private class FakeTree(private val existing: Set<String>) : IPluginToolOutputTree.Stub() {
        private val root = File.createTempFile("tree-", "").also { it.delete(); it.mkdirs() }
        val files = LinkedHashMap<String, File>()
        val directories = ArrayList<String>()

        override fun createDirectory(relativePath: String): Bundle {
            directories += relativePath
            File(root, relativePath).mkdirs()
            return Bundle().apply { putBoolean(PluginToolOutputTreeKeys.OK, true) }
        }

        override fun openFile(relativePath: String, expectedBytes: Long, answer: Bundle): ParcelFileDescriptor? {
            if (relativePath in existing) {
                answer.putBoolean(PluginToolOutputTreeKeys.OK, false)
                answer.putString(PluginToolOutputTreeKeys.ERROR_CODE, PluginToolOutputTreeErrors.EXISTS)
                return null
            }
            val file = File(root, relativePath).apply { parentFile!!.mkdirs() }
            files[relativePath] = file
            answer.putBoolean(PluginToolOutputTreeKeys.OK, true)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE)
        }
    }

    private class TestProvider : PluginToolProviderService() {

        @Volatile
        var catalogError: PluginToolException? = null

        val started = CountDownLatch(1)

        @Volatile
        var listenerRan = false

        override fun createPluginInfo() = PluginInfo().apply {
            name = "Base class test"
            author = "SuperMonster003"
            versionName = "1.0.0"
            versionCode = 1L
            capabilities = Bundle().apply { putString("own", "kept") }
        }

        override fun createCatalogJson(): String {
            catalogError?.let { throw it }
            val tools = JSONArray()
            for (name in listOf("echo", "inspect", "emit", "split", "slow", "fail", "huge")) {
                tools.put(JSONObject().put("name", name).put("description", "Test tool $name.").put("risk", PluginToolValues.RISK_READONLY)
                    .put("inputSchema", JSONObject().put("type", "object").put("additionalProperties", false)))
            }
            return JSONObject().put("shortName", "basetest").put("tools", tools).toString()
        }

        override fun onCall(call: PluginToolCall): PluginToolResult = when (call.tool) {
            "echo" -> call.arguments.getString("text").let { PluginToolResult(JSONObject().put("text", it)).withText(it) }
            "inspect" -> call.requireInput("file").let { input ->
                PluginToolResult(JSONObject().put("bytes", input.readBytes(1024).size).put("displayName", input.displayName))
            }
            "emit" -> PluginToolResult(JSONObject().put("ok", true)).withOutput("data", "text/plain", call.arguments.getString("text").toByteArray())
            "split" -> {
                val tree = call.directoryOutput("tree")!!
                tree.createDirectory("sub")
                val files = call.arguments.getJSONArray("files")
                var written = 0
                for (index in 0 until files.length()) {
                    val path = files.getString(index)
                    if (tree.writeFile(path, "content of $path".toByteArray())) written++
                }
                PluginToolResult(JSONObject().put("written", written).put("existing", JSONArray(tree.existing)))
            }
            "slow" -> {
                call.onCancel { listenerRan = true }
                started.countDown()
                Thread.sleep(30_000L)
                PluginToolResult("{}")
            }
            "fail" -> when (call.arguments.getString("kind")) {
                "plugin" -> throw PluginToolException(PluginToolErrorCodes.NOT_READY, "not yet")
                "argument" -> throw IllegalArgumentException("bad size")
                else -> throw IOException("disk gone")
            }
            "huge" -> PluginToolResult(JSONObject().put("text", "x".repeat(PluginToolLimits.MAX_RESULT_BYTES)))
            else -> throw IllegalStateException("unreachable")
        }
    }
}
