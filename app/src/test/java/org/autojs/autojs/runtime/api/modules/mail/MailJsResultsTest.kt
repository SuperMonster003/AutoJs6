package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.mail.MailScriptValues
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/** Bare Rhino: how the plugin's message documents become script objects (mail roadmap P3.2, appendix A.4). */
class MailJsResultsTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private val binding = RecordingBinding()

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun messageGetsDatesAddressesAndBoundMethods() {
        val message = decorate(MESSAGE)
        ScriptableObject.putProperty(scope, "m", message)
        val summary = evaluate(
            """
            JSON.stringify({
                isDate: m.date instanceof Date,
                iso: m.date.toISOString(),
                received: m.receivedDate.toISOString(),
                from: String(m.from),
                fromName: m.from.name,
                to: m.to.map(String),
                cc: m.cc.map(String),
                replyTo: m.replyTo.length,
                keys: Object.keys(m).indexOf('load') < 0 && Object.keys(m).indexOf('loadAsync') < 0,
                loadType: typeof m.load,
                loadAsyncType: typeof m.loadAsync,
                attachmentUid: m.attachments[0].uid,
                attachmentFolder: m.attachments[0].folder,
                attachmentKeys: Object.keys(m.attachments[0]).sort().join(','),
                downloadType: typeof m.attachments[0].download,
                bodyPartUid: m.bodyParts[0].uid,
                flags: m.flags.join(','),
                uid: m.uid,
            })
            """,
        )
        assertEquals(
            """{"isDate":true,"iso":"2026-09-18T01:02:03.000Z","received":"2026-09-18T01:02:04.000Z","from":"Alice <alice@example.com>","fromName":"Alice",""" +
                """"to":["bob@example.com","Carol <carol@example.com>"],"cc":[],"replyTo":1,"keys":true,"loadType":"function","loadAsyncType":"function",""" +
                """"attachmentUid":42,"attachmentFolder":"INBOX","attachmentKeys":"contentId,fileName,folder,inline,mimeType,partId,size,uid","downloadType":"function",""" +
                """"bodyPartUid":42,"flags":"seen","uid":42}""",
            summary,
        )
        // JSON.stringify keeps the plain document: the bound methods are not enumerable and Dates serialize as ISO strings.
        val roundTrip = evaluate("JSON.parse(JSON.stringify(m))") as NativeObject
        assertEquals("2026-09-18T01:02:03.000Z", ScriptableObject.getProperty(roundTrip, "date"))
        assertEquals(Scriptable.NOT_FOUND, ScriptableObject.getProperty(roundTrip, "load"))
    }

    @Test
    fun boundMethodsForwardTheirReceiverAndArguments() {
        val message = decorate(MESSAGE)
        ScriptableObject.putProperty(scope, "m", message)
        evaluate("m.load({peek: true}); m.loadAsync(); m.attachments[0].download('/tmp', {overwrite: true}); m.attachments[0].downloadAsync();")
        assertEquals(listOf("load", "loadAsync", "download", "downloadAsync"), binding.calls.map { it.first })
        assertSame(message, binding.calls[0].second)
        assertEquals(true, ScriptableObject.getProperty(binding.calls[0].third[0] as Scriptable, "peek"))
        assertSame(message, binding.calls[1].second)
        assertNull(binding.calls[1].third[0])
        val attachment = evaluate("m.attachments[0]")
        assertSame(attachment, binding.calls[2].second)
        assertEquals("/tmp", binding.calls[2].third[0])
        assertEquals(true, ScriptableObject.getProperty(binding.calls[2].third[1] as Scriptable, "overwrite"))
        assertNull(binding.calls[3].third[0])
        assertNull(binding.calls[3].third[1])
        assertEquals("the binding's result is what the script sees", "loaded", evaluate("m.load()"))
        assertEquals("INVALID_ARGUMENT:load expects at most 1 argument, got 2", evaluate("try { m.load(1, 2); 'no' } catch (e) { e.code + ':' + e.message }"))
    }

    @Test
    fun listsAndSearchResultsWrapEveryMessage() {
        val plain = MailScriptValues.toPlain("""[$MESSAGE, {"uid": 43, "folder": "INBOX", "subject": "second", "attachments": [], "to": []}]""")
        val array = MailJsResults.messages(context, scope, plain, binding)
        ScriptableObject.putProperty(scope, "list", array)
        assertEquals("2,function,function,42,43", evaluate("[list.length, typeof list[0].load, typeof list[1].loadAsync, list[0].uid, list[1].uid].join(',')"))

        val search = MailJsResults.search(context, scope, MailScriptValues.toPlain("""{"messages": [$MESSAGE], "fallback": "client"}"""), binding)
        ScriptableObject.putProperty(scope, "found", search)
        assertEquals("1,client,true,42", evaluate("[found.length, found.fallback, Object.keys(found).indexOf('fallback') < 0, found[0].uid].join(',')"))
        val server = MailJsResults.search(context, scope, MailScriptValues.toPlain("""{"messages": []}"""), binding)
        assertEquals("server", ScriptableObject.getProperty(server, MailJsResults.FALLBACK))
        assertEquals(0L, (MailJsResults.messages(context, scope, null, binding) as org.mozilla.javascript.NativeArray).length)
    }

    @Test
    fun aMissingMessageObjectIsAnInternalError() {
        try {
            MailJsResults.message(context, scope, listOf(1), binding)
            fail("expected INTERNAL")
        } catch (e: MailPluginException) {
            assertEquals(MailErrorCodes.INTERNAL, e.code)
        }
    }

    @Test
    fun addressesWithoutNamesPrintBare() {
        val bare = RhinoObject(mapOf("address" to "x@y.z"))
        assertEquals("x@y.z", MailJsResults.formatAddress(bare))
        assertEquals("N <x@y.z>", MailJsResults.formatAddress(RhinoObject(mapOf("name" to " N ", "address" to "x@y.z"))))
        assertEquals("", MailJsResults.formatAddress(RhinoObject(emptyMap())))
        assertNull(MailJsResults.date(context, scope, null))
        assertNull(MailJsResults.date(context, scope, "2026"))
    }

    private fun decorate(document: String): Scriptable = MailJsResults.message(context, scope, MailScriptValues.toPlain(document), binding)

    private fun evaluate(source: String): Any? = context.evaluateString(scope, source, "test.js", 1, null)

    @Suppress("TestFunctionName")
    private fun RhinoObject(values: Map<String, Any?>): Scriptable {
        val target = context.newObject(scope)
        values.forEach { (key, value) -> ScriptableObject.putProperty(target, key, value) }
        return target
    }

    private class RecordingBinding : MailJsResults.Binding {

        val calls = ArrayList<Triple<String, Scriptable, List<Any?>>>()

        override fun load(message: Scriptable, options: Any?): Any? {
            calls += Triple("load", message, listOf(options))
            return "loaded"
        }

        override fun loadAsync(message: Scriptable, options: Any?): Any? {
            calls += Triple("loadAsync", message, listOf(options))
            return "promise"
        }

        override fun download(attachment: Scriptable, target: Any?, options: Any?): Any? {
            calls += Triple("download", attachment, listOf(target, options))
            return "/tmp/a.pdf"
        }

        override fun downloadAsync(attachment: Scriptable, target: Any?, options: Any?): Any? {
            calls += Triple("downloadAsync", attachment, listOf(target, options))
            return "promise"
        }
    }

    private companion object {
        const val MESSAGE = """{
            "uid": 42, "folder": "INBOX", "messageId": "<m1@example.com>", "subject": "hello",
            "from": {"name": "Alice", "address": "alice@example.com"},
            "sender": {"address": "alice@example.com"},
            "replyTo": [{"name": "Alice", "address": "alice@example.com"}],
            "to": [{"address": "bob@example.com"}, {"name": "Carol", "address": "carol@example.com"}],
            "cc": [], "bcc": [],
            "date": 1789693323000, "receivedDate": 1789693324000, "size": 1234,
            "flags": ["seen"], "seen": true, "flagged": false, "hasAttachments": true, "bodyLoaded": false,
            "attachments": [{"partId": "2", "fileName": "a.pdf", "mimeType": "application/pdf", "size": 1000, "contentId": null, "inline": false}],
            "bodyParts": [{"partId": "1", "mimeType": "text/plain", "size": 12}]
        }"""
    }
}
