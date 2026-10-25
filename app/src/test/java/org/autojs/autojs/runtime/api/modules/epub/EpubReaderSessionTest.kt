package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.core.plugin.epub.EpubError
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.autojs.core.plugin.epub.EpubReaderEvent
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptBridges
import org.autojs.autojs.runtime.api.epub.EpubScriptArguments
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.util.RhinoUtils
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Wrapper
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `EpubReaderSession` emitter and its runner (plugin roadmap P6.2) on the pure-JVM dispatcher
 * seam: event order and payload shapes, the state properties, the single terminal `close`, the
 * port calls behind the session methods, overflow and the silent exit.
 */
class EpubReaderSessionTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

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

    /** [queued] keeps the dispatched actions until [drain], like a busy script thread. */
    private class Harness(queued: Boolean = false, maxQueuedActions: Int = 1_000) {
        val released = AtomicInteger()
        val terminal = AtomicInteger()
        val scheduled = ArrayDeque<() -> Unit>()
        val dispatcher = ScriptAsyncDispatcher(
            valueMapper = { it },
            scheduler = if (queued) { action -> scheduled.addLast(action) } else { action -> action() },
            releaseWait = { released.incrementAndGet() },
            maxQueuedActions = maxQueuedActions,
        )
        val controller = EpubReaderSession.Controller(dispatcher, ScriptBridges(), "/books/a.epub") { terminal.incrementAndGet() }
        val finished = ArrayList<EpubReaderSessionRunner>()
        val runner = EpubReaderSessionRunner(controller, onFinished = { finished += it })
        val session: EpubReaderSession get() = controller.session
        val events = ArrayList<Pair<String, List<Any?>>>()

        fun drain() {
            while (scheduled.isNotEmpty()) scheduled.removeFirst()()
        }
    }

    private class FakePort : EpubReaderSession.Port {
        val calls = ArrayList<String>()
        override fun goTo(target: EpubScriptArguments.GoTo) {
            calls += "goTo(${target.locatorJson}, ${target.href}, ${target.progression})"
        }

        override fun navigate(direction: Int) {
            calls += "navigate($direction)"
        }

        override fun setPreferences(preferencesJson: String) {
            calls += "setPreferences($preferencesJson)"
        }

        override fun bookmarks(): List<Any?> {
            calls += "bookmarks()"
            return listOf(mapOf("locator" to mapOf("href" to "OEBPS/c1.xhtml")))
        }

        override fun close(finish: Boolean) {
            calls += "close($finish)"
        }
    }

    private fun Harness.record(vararg names: String) {
        names.forEach { name -> session.on(name, listener { args -> events += name to args.toList() }) }
    }

    private fun listener(action: (Array<out Any?>) -> Unit): Callable =
        RhinoUtils.newBaseFunction(scope, "listener", { args -> action(args) }, RhinoUtils.NOT_CONSTRUCTABLE)

    private fun event(type: String, reason: String? = null, change: String? = null, locatorJson: String? = null, progression: Double? = null, title: String? = null, href: String? = null, positions: Int? = null, bookmarksJson: String? = null, errorCode: String? = null, errorMessage: String? = null, annotationsJson: String? = null) =
        EpubReaderEvent(type, reason, change, locatorJson, progression, title, href, true, positions, bookmarksJson, errorCode, errorMessage, annotationsJson)

    /** Listener arguments arrive through the bridges as Rhino wrappers; the tests look at the Java values. */
    private fun unwrap(value: Any?): Any? = (value as? Wrapper)?.unwrap() ?: value

    private fun payload(index: Int, events: List<Pair<String, List<Any?>>>): Map<*, *> = unwrap(events[index].second[0]) as Map<*, *>

    @Test
    fun eventsArriveInOrderWithTheAppendixPayloadsAndCloseIsTerminal() {
        val h = Harness()
        h.record(EpubReaderSession.EVENT_OPEN, EpubReaderSession.EVENT_PROGRESS, EpubReaderSession.EVENT_BOOKMARK, EpubReaderSession.EVENT_HIGHLIGHT, EpubReaderSession.EVENT_ERROR, EpubReaderSession.EVENT_CLOSE)
        assertEquals("pending", h.session.state)
        assertFalse(h.session.isOpen)
        assertFalse(h.session.isClosed)
        assertNull(h.session.locator)
        assertEquals(EpubReaderSession.UNKNOWN_PROGRESS, h.session.progress, 0.0)
        assertEquals(0, h.session.positions)
        assertEquals("/books/a.epub", h.session.path)

        h.runner.onEvent(event(EpubContract.EVENT_OPEN, locatorJson = """{"href":"OEBPS/c2.xhtml","type":"application/xhtml+xml","locations":{"progression":0.0,"totalProgression":0.4}}""", title = "The Book", href = "OEBPS/c2.xhtml", positions = 12))
        assertEquals("open", h.session.state)
        assertTrue(h.session.isOpen)
        assertEquals("The Book", h.session.title)
        assertEquals("OEBPS/c2.xhtml", h.session.href)
        assertEquals(12, h.session.positions)
        assertEquals(0.4, h.session.progress, 0.0)
        assertEquals("OEBPS/c2.xhtml", (h.session.locator as Map<*, *>)["href"])
        val open = payload(0, h.events)
        assertEquals(listOf("locator", "title", "href", "positions"), open.keys.toList())
        assertEquals("The Book", open["title"])
        assertEquals(12, open["positions"])

        h.runner.onEvent(event(EpubContract.EVENT_PROGRESS, locatorJson = """{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.1}}""", progression = 0.1, title = "The Harbour", href = "OEBPS/c1.xhtml"))
        val progress = payload(1, h.events)
        assertEquals(listOf("locator", "totalProgression", "chapterTitle", "href"), progress.keys.toList())
        assertEquals(0.1, progress["totalProgression"])
        assertEquals("The Harbour", progress["chapterTitle"])
        assertEquals(0.1, h.session.progress, 0.0)
        assertEquals("The Harbour", h.session.chapterTitle)
        assertEquals("OEBPS/c1.xhtml", h.session.href)
        assertEquals("The Book", h.session.title)

        h.runner.onEvent(event(EpubContract.EVENT_BOOKMARK, change = EpubContract.CHANGE_ADDED, bookmarksJson = """[{"locator":{"href":"OEBPS/c1.xhtml"},"createdAt":1789920631874,"title":"The Harbour","text":"First words"}]"""))
        val bookmark = payload(2, h.events)
        assertEquals(listOf("action", "locator", "createdAt", "title", "text"), bookmark.keys.toList())
        assertEquals("added", bookmark["action"])
        assertEquals("OEBPS/c1.xhtml", (bookmark["locator"] as Map<*, *>)["href"])
        assertEquals(1789920631874.0, bookmark["createdAt"])
        assertEquals("First words", bookmark["text"])

        // Contract version 2: a highlight of the book changed in the reader.
        h.runner.onEvent(
            event(
                EpubContract.EVENT_HIGHLIGHT,
                change = EpubContract.CHANGE_UPDATED,
                annotationsJson = """[{"id":7,"style":"underline","color":"#64B5F6","note":"look again","quote":"the old lantern","title":"The Harbour","locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.25},"text":{"highlight":"the old lantern"}},"createdAt":1789920631874,"updatedAt":1789920700000}]""",
            ),
        )
        val highlight = payload(3, h.events)
        assertEquals(listOf("action", "id", "style", "color", "note", "quote", "title", "locator", "createdAt", "updatedAt"), highlight.keys.toList())
        assertEquals("updated", highlight["action"])
        assertEquals(7, highlight["id"]) // integral ids stay script integers (EpubScriptValues.number)
        assertEquals("underline", highlight["style"])
        assertEquals("#64B5F6", highlight["color"])
        assertEquals("look again", highlight["note"])
        assertEquals("the old lantern", highlight["quote"])
        assertEquals("The Harbour", highlight["title"])
        assertEquals("OEBPS/c1.xhtml", (highlight["locator"] as Map<*, *>)["href"])
        assertEquals(1789920700000.0, highlight["updatedAt"])
        // A bare highlight (no note) keeps the key with a null value, like the bookmark payload does.
        h.runner.onEvent(event(EpubContract.EVENT_HIGHLIGHT, change = EpubContract.CHANGE_REMOVED, annotationsJson = """[{"id":7,"style":"highlight","color":"#FFEB3B","locator":{"href":"OEBPS/c1.xhtml"},"createdAt":1,"updatedAt":2}]"""))
        val removed = payload(4, h.events)
        assertEquals("removed", removed["action"])
        assertTrue(removed.containsKey("note"))
        assertNull(removed["note"])
        assertNull(removed["quote"])

        h.runner.onEvent(event(EpubContract.EVENT_ERROR, errorCode = EpubErrorCodes.UNSUPPORTED_PREFERENCE, errorMessage = "unsupported preferences: bogus"))
        val error = unwrap(h.events[5].second[0]) as EpubError
        assertEquals(EpubErrorCodes.UNSUPPORTED_PREFERENCE, error.code)
        assertEquals("unsupported preferences: bogus", error.message)

        assertEquals(0, h.terminal.get())
        h.runner.onEvent(event(EpubContract.EVENT_CLOSE, reason = EpubContract.REASON_USER))
        assertEquals(listOf("open", "progress", "bookmark", "highlight", "highlight", "error", "close"), h.events.map { it.first })
        assertEquals(mapOf("reason" to "user"), payload(6, h.events))
        assertEquals("closed", h.session.state)
        assertTrue(h.session.isClosed)
        assertFalse(h.session.isOpen)
        assertEquals("user", h.session.reason)
        assertEquals(1, h.terminal.get())
        assertTrue(h.released.get() >= 1)
        assertTrue(h.runner.isFinished)
        assertEquals(listOf(h.runner), h.finished)
        assertEquals(0.1, h.session.progress, 0.0)

        h.runner.onEvent(event(EpubContract.EVENT_PROGRESS, href = "OEBPS/c2.xhtml", progression = 0.9))
        h.runner.onEvent(event(EpubContract.EVENT_HIGHLIGHT, change = EpubContract.CHANGE_ADDED, annotationsJson = "[{\"id\":8}]"))
        h.runner.onEvent(event(EpubContract.EVENT_CLOSE, reason = EpubContract.REASON_HOST))
        assertEquals(7, h.events.size)
        assertEquals(1, h.terminal.get())
        assertTrue(h.session.toString().contains("state: closed") && h.session.toString().contains("reason: user"))
    }

    @Test
    fun sessionMethodsGoThroughTheBoundPort() {
        val h = Harness()
        val port = FakePort()
        assertTrue(h.controller.bindPort(port))
        h.runner.onEvent(event(EpubContract.EVENT_OPEN, href = "OEBPS/c1.xhtml", positions = 3))

        assertSame(h.session, h.session.goTo("OEBPS/c2.xhtml#s1"))
        assertSame(h.session, h.session.goTo(0.25))
        h.session.goTo(evaluate("({ href: 'OEBPS/c2.xhtml', locations: { progression: 0.5 } })"))
        h.session.next()
        h.session.prev()
        h.session.nextChapter()
        h.session.prevChapter()
        h.session.setPreferences(evaluate("({ fontSize: 1.2, theme: 'dark' })"))
        val bookmarks = h.session.bookmarks() as List<*>
        assertEquals(1, bookmarks.size)
        h.session.close(evaluate("({ finish: true })"))
        h.session.close()
        assertEquals(
            listOf(
                "goTo(null, OEBPS/c2.xhtml#s1, null)",
                "goTo(null, null, 0.25)",
                """goTo({"href":"OEBPS/c2.xhtml","locations":{"progression":0.5}}, null, null)""",
                "navigate(1)",
                "navigate(2)",
                "navigate(3)",
                "navigate(4)",
                """setPreferences({"fontSize":1.2,"theme":"dark"})""",
                "bookmarks()",
                "close(true)",
                "close(false)",
            ),
            port.calls,
        )

        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.goTo(1.5) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.goTo(null) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.goTo(evaluate("({ title: 'no href' })")) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.setPreferences("dark") })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.close(evaluate("({ finish: 'yes' })")) })
        assertEquals(EpubErrorCodes.INVALID_ARGUMENT, codeOf { h.session.close(evaluate("({ nope: true })")) })
        assertEquals(11, port.calls.size)

        h.controller.close(EpubContract.REASON_HOST)
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { h.session.next() })
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { h.session.bookmarks() })
        assertSame("close after close is a no-op", h.session, h.session.close())
        assertEquals(11, port.calls.size)
    }

    @Test
    fun portsBoundAfterCloseAreRefusedAndUnboundSessionsAnswerSessionClosed() {
        val h = Harness()
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { h.session.goTo("OEBPS/c1.xhtml") })
        h.controller.close(EpubContract.REASON_TIMEOUT)
        assertFalse(h.controller.bindPort(FakePort()))
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { h.session.next() })
        assertEquals("timeout", h.session.reason)
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun overflowReplacesTheQueueWithErrorAndCloseOverflow() {
        val h = Harness(queued = true, maxQueuedActions = 2)
        h.record(EpubReaderSession.EVENT_OPEN, EpubReaderSession.EVENT_PROGRESS, EpubReaderSession.EVENT_ERROR, EpubReaderSession.EVENT_CLOSE)
        h.runner.onEvent(event(EpubContract.EVENT_OPEN, href = "OEBPS/c1.xhtml", positions = 3))
        h.runner.onEvent(event(EpubContract.EVENT_PROGRESS, href = "OEBPS/c1.xhtml", progression = 0.2))
        h.runner.onEvent(event(EpubContract.EVENT_PROGRESS, href = "OEBPS/c1.xhtml", progression = 0.3))
        assertTrue(h.runner.isFinished)
        assertTrue(h.session.isClosed)
        h.drain()
        assertEquals(listOf("error", "close"), h.events.map { it.first })
        assertEquals(EpubErrorCodes.LIMIT_EXCEEDED, (unwrap(h.events[0].second[0]) as EpubError).code)
        assertEquals(mapOf("reason" to "overflow"), payload(1, h.events))
        assertEquals("overflow", h.session.reason)
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun cancelSilentlyEmitsNothingAndMarksTheSessionClosed() {
        val h = Harness()
        h.record(EpubReaderSession.EVENT_OPEN, EpubReaderSession.EVENT_CLOSE)
        h.runner.onEvent(event(EpubContract.EVENT_OPEN, href = "OEBPS/c1.xhtml", positions = 3))
        assertTrue(h.session.isOpen)
        h.runner.cancelSilently()
        assertEquals(listOf("open"), h.events.map { it.first })
        assertTrue(h.session.isClosed)
        assertFalse(h.session.isOpen)
        assertEquals(EpubReaderSession.REASON_EXIT, h.session.reason)
        assertEquals(1, h.terminal.get())
        assertTrue(h.released.get() >= 1)
        h.runner.onEvent(event(EpubContract.EVENT_CLOSE, reason = EpubContract.REASON_USER))
        assertEquals(1, h.events.size)
        assertEquals(EpubErrorCodes.SESSION_CLOSED, codeOf { h.session.prev() })
        h.runner.cancelSilently()
        assertEquals(1, h.terminal.get())
    }

    @Test
    fun totalProgressionIsReadFromTheLocatorWhenTheEventCarriesNone() {
        assertEquals(0.75, EpubReaderSession.totalProgressionOf(mapOf("locations" to mapOf("totalProgression" to 0.75))))
        assertNull(EpubReaderSession.totalProgressionOf(mapOf("locations" to mapOf("progression" to 0.75))))
        assertNull(EpubReaderSession.totalProgressionOf(null))
        assertNull(EpubReaderSession.totalProgressionOf("text"))
    }

    private fun evaluate(source: String): Scriptable = context.evaluateString(scope, source, "test.js", 1, null) as Scriptable

    private inline fun codeOf(block: () -> Any?): String {
        try {
            block()
        } catch (e: EpubPluginException) {
            return e.code
        }
        fail("expected an EpubPluginException")
        throw AssertionError()
    }
}
