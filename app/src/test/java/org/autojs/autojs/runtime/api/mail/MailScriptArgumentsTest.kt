package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The script-to-operation argument mapping of the `MailClient` methods (mail roadmap P3.2), without Rhino. */
class MailScriptArgumentsTest {

    @Test
    fun fetchAndSearchNormalizePagingAndRefuseUnknownKeys() {
        assertEquals("{}", MailScriptArguments.fetch(null).toString())
        val fetch = MailScriptArguments.fetch(json("""{"folder":"INBOX","limit":20,"before":{"uid":300,"folder":"x"},"after":"u-1","order":"asc","unseenOnly":true}"""))
        assertEquals("""{"folder":"INBOX","limit":20,"before":300,"after":"u-1","order":"asc","unseenOnly":true}""", fetch.toString())
        assertTrue(invalidMessage { MailScriptArguments.fetch(json("""{"limit":0}""")) }.contains("positive integer"))
        assertTrue(invalidMessage { MailScriptArguments.fetch(json("""{"order":"up"}""")) }.contains("options.order"))
        assertTrue(invalidMessage { MailScriptArguments.fetch(json("""{"unseenOnly":"yes"}""")) }.contains("boolean"))
        assertTrue(invalidMessage { MailScriptArguments.fetch(json("""{"folders":"INBOX"}""")) }.contains("unknown option 'options.folders'"))
        assertTrue(invalidMessage { MailScriptArguments.fetch(json("\"INBOX\"")) }.contains("must be an object"))

        val search = MailScriptArguments.search(json("""{"subject":"invoice","since":"2026-09-01"}"""), json("""{"folder":"INBOX","limit":5,"fallback":"always"}"""))
        assertEquals("""{"query":{"subject":"invoice","since":"2026-09-01"},"folder":"INBOX","limit":5,"fallback":"always"}""", search.toString())
        assertTrue(invalidMessage { MailScriptArguments.search(json("\"invoice\""), null) }.contains("'query' must be an object"))
        assertTrue(invalidMessage { MailScriptArguments.search(json("{}"), json("""{"fallback":"maybe"}""")) }.contains("options.fallback"))
        assertTrue(invalidMessage { MailScriptArguments.search(json("{}"), json("""{"after":1}""")) }.contains("unknown option 'options.after'"))
    }

    @Test
    fun getAcceptsUidsAndMessageObjectsAndKeepsTheirFolder() {
        assertEquals("""{"uid":42}""", MailScriptArguments.get(json("42"), null).toString())
        assertEquals("""{"uid":"UIDL-7"}""", MailScriptArguments.get(json("\" UIDL-7 \""), null).toString())
        val fromMessage = MailScriptArguments.get(json("""{"uid":9,"folder":"Archive","subject":"ignored","attachments":[]}"""), json("""{"peek":true}"""))
        assertEquals("""{"peek":true,"uid":9,"folder":"Archive"}""", fromMessage.toString())
        val explicit = MailScriptArguments.get(json("""{"uid":9,"folder":"Archive"}"""), json("""{"folder":"INBOX","includeRaw":false}"""))
        assertEquals("""{"folder":"INBOX","includeRaw":false,"uid":9}""", explicit.toString())
        assertTrue(invalidMessage { MailScriptArguments.get(null, null) }.contains("'message' is required"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("0"), null) }.contains("positive integer"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("1.5"), null) }.contains("positive integer"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("\" \""), null) }.contains("blank"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("""{"subject":"no uid"}"""), null) }.contains("without 'uid'"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("true"), null) }.contains("must be a number or a string"))
        assertTrue(invalidMessage { MailScriptArguments.get(json("1"), json("""{"peek":"yes"}""")) }.contains("boolean"))
    }

    @Test
    fun rawAndDownloadDescribeTheSinkBeforeTheTransfer() {
        val raw = MailScriptArguments.raw(json("""{"uid":12,"folder":"INBOX"}"""), null)
        assertEquals("""{"uid":12,"folder":"INBOX"}""", raw.args.toString())
        assertEquals("12.eml", raw.fileName)
        assertNull(raw.size)
        assertFalse(raw.overwrite)
        val rawOverwrite = MailScriptArguments.raw(json("\"u1\""), json("""{"overwrite":true,"folder":"Sent"}"""))
        assertEquals("""{"uid":"u1","folder":"Sent"}""", rawOverwrite.args.toString())
        assertTrue(rawOverwrite.overwrite)
        assertTrue(invalidMessage { MailScriptArguments.raw(json("1"), json("""{"peek":true}""")) }.contains("unknown option 'options.peek'"))

        val attachment = json("""{"partId":"2","fileName":"a.pdf","mimeType":"application/pdf","size":1234,"contentId":null,"inline":false,"uid":77,"folder":"INBOX"}""")
        val download = MailScriptArguments.download(attachment, null)
        assertEquals("""{"uid":77,"partId":"2","folder":"INBOX"}""", download.args.toString())
        assertEquals("a.pdf", download.fileName)
        assertEquals(1234L, download.size)
        assertFalse(download.overwrite)
        val minimal = MailScriptArguments.download(json("""{"uid":"p1","partId":"1.2"}"""), json("""{"folder":"Archive","overwrite":true}"""))
        assertEquals("""{"uid":"p1","partId":"1.2","folder":"Archive"}""", minimal.args.toString())
        assertNull(minimal.fileName)
        assertNull(minimal.size)
        assertTrue(minimal.overwrite)
        assertTrue(invalidMessage { MailScriptArguments.download(json("7"), null) }.contains("attachment object"))
        assertTrue(invalidMessage { MailScriptArguments.download(json("""{"uid":7}"""), null) }.contains("'attachment.partId' is required"))
        assertTrue(invalidMessage { MailScriptArguments.download(json("""{"partId":"2"}"""), null) }.contains("'attachment.uid' is required"))
        assertTrue(invalidMessage { MailScriptArguments.download(json("""{"uid":7,"partId":"2","download":1}"""), null) }.contains("unknown option 'attachment.download'"))
    }

    @Test
    fun flagsTransfersAndDeletionsShareTheUidListRules() {
        val flags = MailScriptArguments.flags(json("[1, {\"uid\": 2, \"folder\": \"Work\"}, \"u3\"]"), json("\"seen\""), null, null)
        assertEquals("""{"uids":[1,2,"u3"],"flags":["seen"],"folder":"Work"}""", flags.toString())
        val explicit = MailScriptArguments.flags(json("5"), json("""["flagged"," custom "]"""), json("\"set\""), json("""{"folder":"INBOX"}"""))
        assertEquals("""{"uids":[5],"flags":["flagged","custom"],"mode":"set","folder":"INBOX"}""", explicit.toString())
        assertTrue(invalidMessage { MailScriptArguments.flags(json("[]"), json("\"seen\""), null, null) }.contains("at least one UID"))
        assertTrue(invalidMessage { MailScriptArguments.flags(json("[1, 0]"), json("\"seen\""), null, null) }.contains("'uids[1]'"))
        assertTrue(invalidMessage { MailScriptArguments.flags(json("1"), json("[]"), null, null) }.contains("at least one flag"))
        assertTrue(invalidMessage { MailScriptArguments.flags(json("1"), json("\"seen\""), json("\"toggle\""), null) }.contains("'mode' must be one of"))
        assertTrue(invalidMessage { MailScriptArguments.flags(json("1"), json("\"seen\""), null, json("""{"expunge":true}""")) }.contains("unknown option 'options.expunge'"))

        val move = MailScriptArguments.transfer(json("""[{"uid":3,"folder":"INBOX"}]"""), json("\" Archive \""), null)
        assertEquals("""{"uids":[3],"target":"Archive","folder":"INBOX"}""", move.toString())
        val copy = MailScriptArguments.transfer(json("4"), json("\"Archive\""), json("""{"from":"Work"}"""))
        assertEquals("""{"uids":[4],"target":"Archive","folder":"Work"}""", copy.toString())
        assertTrue(invalidMessage { MailScriptArguments.transfer(json("4"), null, null) }.contains("'folder' is required"))
        assertTrue(invalidMessage { MailScriptArguments.transfer(json("4"), json("\" \""), null) }.contains("blank"))
        assertTrue(invalidMessage { MailScriptArguments.transfer(json("4"), json("7"), null) }.contains("folder path string"))

        val delete = MailScriptArguments.delete(json("[8, 9]"), json("""{"folder":"Trash","expunge":true}"""))
        assertEquals("""{"uids":[8,9],"folder":"Trash","expunge":true}""", delete.toString())
        assertEquals("""{"uids":[8]}""", MailScriptArguments.delete(json("8"), null).toString())
        assertEquals("{}", MailScriptArguments.expunge(null).toString())
        assertEquals("""{"folder":"Trash"}""", MailScriptArguments.expunge(json("\"Trash\"")).toString())
    }

    @Test
    fun folderCallsTakeNonBlankPaths() {
        assertEquals("{}", MailScriptArguments.folders(null).toString())
        assertEquals("""{"subscribedOnly":true,"status":true}""", MailScriptArguments.folders(json("""{"subscribedOnly":true,"status":true}""")).toString())
        assertTrue(invalidMessage { MailScriptArguments.folders(json("""{"status":1}""")) }.contains("boolean"))
        assertEquals("""{"folder":"INBOX"}""", MailScriptArguments.folderStatus(json("\"INBOX\"")).toString())
        assertEquals("""{"path":"Work/2026"}""", MailScriptArguments.createFolder(json("\"Work/2026\"")).toString())
        assertEquals("""{"path":"Work/2026"}""", MailScriptArguments.deleteFolder(json("\"Work/2026\"")).toString())
        assertEquals("""{"path":"Old","newPath":"New"}""", MailScriptArguments.renameFolder(json("\"Old\""), json("\"New\"")).toString())
        assertTrue(invalidMessage { MailScriptArguments.renameFolder(json("\"Old\""), null) }.contains("'newPath' is required"))
        assertTrue(invalidMessage { MailScriptArguments.createFolder(json("null")) }.contains("'path' is required"))
    }

    @Test
    fun sendResolvesAttachmentsAndNormalizesTheDate() {
        val message = json(
            """{"to":"bob@example.com","cc":[{"name":"Carol","address":"carol@example.com"}],"subject":"hi","text":"body",
               "date":"2026-09-18T01:02:03Z","headers":{"X-Test":"1"},"html":null,
               "attachments":["report.pdf", {"path":"/abs/logo.png","fileName":"Logo.png","mimeType":"image/png","contentId":"logo","inline":true}]}""",
        )
        val outgoing = MailScriptArguments.send(message, json("""{"saveToSent":false}"""), { path -> if (path.startsWith("/")) path else "/cwd/$path" }) { path -> if (path.endsWith(".pdf")) 2048L else null }
        assertEquals(listOf("/cwd/report.pdf", "/abs/logo.png"), outgoing.attachmentPaths)
        val args = outgoing.args
        assertFalse(args.get("saveToSent").asBoolean)
        val normalized = args.getAsJsonObject("message")
        assertEquals("bob@example.com", normalized.get("to").asString)
        assertEquals("Carol", normalized.getAsJsonArray("cc")[0].asJsonObject.get("name").asString)
        assertFalse("null values fall away", normalized.has("html"))
        assertEquals(1789693323000L, normalized.get("date").asLong)
        assertEquals("1", normalized.getAsJsonObject("headers").get("X-Test").asString)
        val attachments = normalized.getAsJsonArray("attachments")
        assertEquals("""{"descriptorIndex":0,"fileName":"report.pdf","size":2048}""", attachments[0].toString())
        assertEquals("""{"fileName":"Logo.png","mimeType":"image/png","contentId":"logo","inline":true,"descriptorIndex":1}""", attachments[1].toString())

        val numericDate = MailScriptArguments.send(json("""{"to":"a@b.c","subject":"s","date":1700000000000}"""), null, { it }, { null })
        assertEquals(1700000000000L, numericDate.args.getAsJsonObject("message").get("date").asLong)
        assertTrue(numericDate.attachmentPaths.isEmpty())
        assertFalse(numericDate.args.has("saveToSent"))
        assertFalse(numericDate.args.getAsJsonObject("message").has("attachments"))

        assertTrue(invalidMessage { MailScriptArguments.send(json("\"bob\""), null, { it }, { null }) }.contains("'message' must be an object"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c","subjekt":"typo"}"""), null, { it }, { null }) }.contains("unknown option 'message.subjekt'"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c","date":"yesterday"}"""), null, { it }, { null }) }.contains("'message.date'"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c","attachments":[{"fileName":"x"}]}"""), null, { it }, { null }) }.contains("'message.attachments[0].path' is required"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c","attachments":[" "]}"""), null, { it }, { null }) }.contains("must not be blank"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c","attachments":[7]}"""), null, { it }, { null }) }.contains("'message.attachments[0]' must be"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c"}"""), json("""{"saveToSent":"no"}"""), { it }, { null }) }.contains("boolean"))
        assertTrue(invalidMessage { MailScriptArguments.send(json("""{"to":"a@b.c"}"""), json("""{"folder":"Sent"}"""), { it }, { null }) }.contains("unknown option 'options.folder'"))
    }

    @Test
    fun appendCarriesFolderMessageAndFlags() {
        val outgoing = MailScriptArguments.append(json("\"Drafts\""), json("""{"to":"a@b.c","subject":"draft","attachments":"note.txt"}"""), json("""["draft","seen"]"""), { "/cwd/$it" }) { 10L }
        assertEquals("""{"folder":"Drafts","message":{"to":"a@b.c","subject":"draft","attachments":[{"descriptorIndex":0,"fileName":"note.txt","size":10}]},"flags":["draft","seen"]}""", outgoing.args.toString())
        assertEquals(listOf("/cwd/note.txt"), outgoing.attachmentPaths)
        val bare = MailScriptArguments.append(json("\"Drafts\""), json("""{"subject":"x"}"""), null, { it }) { null }
        assertEquals("""{"folder":"Drafts","message":{"subject":"x"}}""", bare.args.toString())
        assertTrue(invalidMessage { MailScriptArguments.append(null, json("{}"), null, { it }) { null } }.contains("'folder' is required"))
        assertTrue(invalidMessage { MailScriptArguments.append(json("\"Drafts\""), json("{}"), json("[\"\"]"), { it }) { null } }.contains("'flags[0]'"))
    }

    @Test
    fun uidHelpersExposeTheSharedRules() {
        assertEquals(JsonPrimitive(15L), MailScriptArguments.uid(json("15"), "uid"))
        assertEquals(JsonPrimitive("abc"), MailScriptArguments.uid(json("\"abc\""), "uid"))
        assertEquals(JsonPrimitive(3L) to "INBOX", MailScriptArguments.uidAndFolder(json("""{"uid":3,"folder":"INBOX"}"""), "message"))
        assertEquals(JsonPrimitive(3L) to null, MailScriptArguments.uidAndFolder(json("""{"uid":3,"folder":7}"""), "message"))
        val (uids, folder) = MailScriptArguments.uids(json("""[{"uid":1},{"uid":2,"folder":"Later"}]"""), "uids")
        assertEquals("[1,2]", uids.toString())
        assertEquals("the first folder wins", "Later", folder)
        assertTrue(invalidMessage { MailScriptArguments.uid(json("9007199254740992"), "uid") }.contains("positive integer"))
        assertTrue(invalidMessage { MailScriptArguments.uids(json("[[1]]"), "uids") }.contains("'uids[0]' must be a UID or a message object"))
    }

    @Test
    fun watchNormalizesFolderModeIntervalBodyAndReconnect() {
        val defaults = MailScriptArguments.watch(null, null)
        assertEquals("INBOX", defaults.options.folder)
        assertNull(defaults.options.mode)
        assertNull(defaults.options.pollIntervalMs)
        assertNull(defaults.options.fetchBody)
        assertTrue(defaults.reconnect)
        assertEquals("Archive", MailScriptArguments.watch(json("\" Archive \""), null).options.folder)
        val full = MailScriptArguments.watch(json("\"INBOX\""), json("""{"mode":"poll","pollIntervalMs":30000,"fetchBody":true,"reconnect":false}"""))
        assertEquals("poll", full.options.mode)
        assertEquals(30_000L, full.options.pollIntervalMs)
        assertEquals(true, full.options.fetchBody)
        assertFalse(full.reconnect)
        val single = MailScriptArguments.watch(json("""{"folder":"Sent","mode":"idle"}"""), null)
        assertEquals("Sent", single.options.folder)
        assertEquals("idle", single.options.mode)
        assertNull(MailScriptArguments.watch(json("\"INBOX\""), json("""{"mode":"auto"}""")).options.mode)
        assertEquals("INBOX", MailScriptArguments.watch(json("\"INBOX\""), json("""{"folder":"INBOX"}""")).options.folder)
        assertTrue(invalidMessage { MailScriptArguments.watch(json("\"INBOX\""), json("""{"folder":"Sent"}""")) }.contains("different folders"))
        assertTrue(invalidMessage { MailScriptArguments.watch(json("\"\""), null) }.contains("must not be blank"))
        assertTrue(invalidMessage { MailScriptArguments.watch(json("7"), null) }.contains("folder path string"))
        assertTrue(invalidMessage { MailScriptArguments.watch(null, json("""{"mode":"push"}""")) }.contains("options.mode"))
        assertTrue(invalidMessage { MailScriptArguments.watch(null, json("""{"pollIntervalMs":1000}""")) }.contains("at least 15000"))
        assertTrue(invalidMessage { MailScriptArguments.watch(null, json("""{"fetchBody":"yes"}""")) }.contains("boolean"))
        assertTrue(invalidMessage { MailScriptArguments.watch(null, json("""{"interval":5}""")) }.contains("unknown option 'options.interval'"))
        assertTrue(invalidMessage { MailScriptArguments.watch(null, json("[]")) }.contains("must be an object"))
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun invalidMessage(block: () -> Any?): String {
        try {
            block()
        } catch (e: MailPluginException) {
            assertEquals(e.message, MailErrorCodes.INVALID_ARGUMENT, e.code)
            assertFalse(e.retryable)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
