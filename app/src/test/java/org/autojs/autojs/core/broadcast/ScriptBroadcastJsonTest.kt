package org.autojs.autojs.core.broadcast

import org.autojs.autojs.engine.NodeAutomationObservers
import org.autojs.autojs.engine.NodeEventObservers
import org.autojs.autojs.runtime.ScriptBridges
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

/** events.broadcast between Rhino scripts and the JS runtimes keeps objects and arrays in both directions (api-coverage Q218 (a)). */
class ScriptBroadcastJsonTest {

    private lateinit var context: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    private fun js(source: String): Any? = context.evaluateString(scope, source, "broadcast", 1, null)

    private fun received(vararg args: Any?): org.json.JSONArray {
        val session = NodeEventObservers.Session("s", "scriptBroadcast", null)
        NodeAutomationObservers.attachScriptBroadcasts(session)
        try {
            Broadcast.send("event", arrayOf(*args))
            return session.drain().getJSONObject(0).getJSONArray("args")
        } finally {
            session.close("done")
        }
    }

    @Test
    fun rhinoObjectsAndArraysReachTheRuntimesAsJson() {
        val args = received(js("({action: 'go', nested: {list: [1, 'two', null, {deep: true}]}})"), js("[1, [2], {a: 3}]"))
        val action = args.getJSONObject(0)
        assertEquals("go", action.getString("action"))
        assertEquals(1, action.getJSONObject("nested").getJSONArray("list").getInt(0))
        assertTrue(action.getJSONObject("nested").getJSONArray("list").getJSONObject(3).getBoolean("deep"))
        assertEquals(3, args.getJSONArray(1).getJSONObject(2).getInt("a"))
    }

    @Test
    fun primitivesStayAsTheyWere() {
        val args = received("text", 1.5, 7, true, null, js("'a' + 'b'"))
        assertEquals("text", args.getString(0))
        assertEquals(1.5, args.getDouble(1), 0.0)
        assertEquals(7, args.getInt(2))
        assertTrue(args.getBoolean(3))
        assertTrue(args.isNull(4))
        assertEquals("ab", args.getString(5))
    }

    @Test
    fun valuesWithoutJsonFollowJsonStringify() {
        val args = received(js("({f: function() {}, u: undefined, kept: 1, list: [function() {}, undefined, NaN], date: new Date(0)})"))
        val value = args.getJSONObject(0)
        assertEquals(setOf("kept", "list", "date"), value.keys().asSequence().toSet())
        for (index in 0..2) assertTrue(value.getJSONArray("list").isNull(index))
        assertEquals("1970-01-01T00:00:00.000Z", value.getString("date"))
    }

    @Test
    fun argumentsWithoutJsonAreNullAndTheSenderKeepsGoing() {
        val cycle = js("var o = {}; o.self = o; o")
        val args = received(js("(function() {})"), Undefined.instance, Double.NaN, Double.POSITIVE_INFINITY, cycle, js("({toJSON: function() { throw new Error('no'); }})"), "last")
        for (index in 0..5) assertTrue("argument $index", args.isNull(index))
        assertEquals("last", args.getString(6))
    }

    @Test
    fun runtimeValuesStayJsonBetweenRuntimes() {
        val args = JSONObject("""{"args": [{"a": [1, {"b": 2}]}, [3], "s", 4, null]}""").getJSONArray("args")
        val received = received(*Array(args.length()) { NodeAutomationObservers.javaValue(args.opt(it)) })
        assertEquals(2, received.getJSONObject(0).getJSONArray("a").getJSONObject(1).getInt("b"))
        assertEquals(3, received.getJSONArray(1).getInt(0))
        assertEquals("s", received.getString(2))
        assertEquals(4, received.getInt(3))
        assertTrue(received.isNull(4))
    }

    @Test
    fun rhinoListenersReceiveNativeObjectsOfTheirOwn() {
        val emitter = BroadcastEmitter(ScriptBridges(), null)
        try {
            emitter.on("event", js("var seen = []; (function(value, list, text, nothing) { seen.push([value, list, text, nothing]); })") as Callable)
            val sent = JSONObject("""{"action": "go", "1": "numeric", "list": [1, {"b": 2}]}""")
            Broadcast.send("event", arrayOf(NodeAutomationObservers.javaValue(sent), NodeAutomationObservers.javaValue(sent.getJSONArray("list")), "text", null))
            Broadcast.send("event", arrayOf(NodeAutomationObservers.javaValue(sent)))
        } finally {
            emitter.unregister()
        }
        assertEquals("go", js("seen[0][0].action"))
        assertEquals("numeric", js("seen[0][0][1]"))
        assertEquals(2.0, Context.toNumber(js("seen[0][0].list[1].b")), 0.0)
        assertEquals(true, js("Array.isArray(seen[0][1]) && seen[0][1].length === 2"))
        assertEquals(true, js("Object.prototype.toString.call(seen[0][0]) === '[object Object]'"))
        assertEquals("text", js("seen[0][2]"))
        assertEquals(true, js("seen[0][3] === null || seen[0][3] === undefined"))
        assertEquals(true, js("seen[0][0] !== seen[1][0]"))
    }

    @Test
    fun anEmitterWithoutListenersParsesNothing() {
        val emitter = BroadcastEmitter(ScriptBridges(), null)
        try {
            assertEquals(false, emitter.onBroadcast("event", BroadcastEmitter.Json("{not json")))
        } finally {
            emitter.unregister()
        }
    }

    @Test
    fun rhinoObjectsStayLiveBetweenRhinoScripts() {
        val emitter = BroadcastEmitter(ScriptBridges(), null)
        try {
            emitter.on("event", js("var live = null; (function(value) { live = value; })") as Callable)
            Broadcast.send("event", arrayOf(js("var sent = {action: 'go'}; sent")))
        } finally {
            emitter.unregister()
        }
        assertEquals(true, js("live === sent"))
    }
}
