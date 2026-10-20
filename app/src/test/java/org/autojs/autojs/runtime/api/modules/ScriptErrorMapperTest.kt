package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.WrappedException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Roadmap P1.4 / D10 / T5: the mapping rules of [ScriptErrorMapper] one by one (what passes through unchanged, what
 * becomes a [ScriptCallException], how interruptions are recognized), the details of a [ScriptCallException], which
 * module the debug printing switches are asked about and the format of the console entry.
 */
class ScriptErrorMapperTest {

    private lateinit var previousPrinting: ScriptErrorMapper.DebugPrinting
    private val asked = mutableListOf<String>()
    private val entries = mutableListOf<String>()
    private val console = ScriptErrorMapper.DebugConsole { _, text -> entries += text.toString() }

    @Before
    fun setUp() {
        previousPrinting = ScriptErrorMapper.debugPrinting
        ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { module -> asked += module; false }
    }

    @After
    fun tearDown() {
        ScriptErrorMapper.debugPrinting = previousPrinting
    }

    @Test
    fun rhinoExceptionsAndControlFlowPassThrough() {
        val inner = ScriptCallException("inner.fn", IllegalStateException("x"))
        val passing = listOf(
            JavaScriptException("thrown", "test", 1),
            RhinoScriptRuntime.typeError("t"),
            RhinoScriptRuntime.rangeError("r"),
            EvaluatorException("e"),
            WrappedException(IOException("w")),
            // A module calling another module: the innermost member keeps the prefix.
            inner,
            object : ContinuationPending(null) {},
            ScriptInterruptedException(),
            StackOverflowError(),
            OutOfMemoryError(),
        )
        passing.forEach { e -> assertSame(e.javaClass.name, e, ScriptErrorMapper.map(e, "mod.fn", console = console)) }
        assertEquals("pass-through errors never ask the debug switches", emptyList<String>(), asked)
        assertEquals(emptyList<String>(), entries)
    }

    @Test
    fun interruptionsAnywhereInTheCauseChainBecomeScriptInterruptions() {
        val direct = ScriptErrorMapper.map(InterruptedException(), "mod.fn")
        assertTrue(direct is ScriptInterruptedException)
        val nested = RuntimeException(IllegalStateException(InterruptedException()))
        val mapped = ScriptErrorMapper.map(nested, "mod.fn")
        assertTrue(mapped is ScriptInterruptedException && mapped.cause === nested)
        val viaScriptInterruption = ScriptErrorMapper.map(IllegalStateException(ScriptInterruptedException()), "mod.fn")
        assertTrue(viaScriptInterruption is ScriptInterruptedException)
    }

    @Test
    fun otherThrowablesKeepTheirTypeInsideAScriptCallException() {
        val wrapped = listOf(
            IllegalArgumentException("bad"),
            IOException("io"),
            AssertionError("assert"),
            NotImplementedError(),
            ExceptionInInitializerError(IllegalStateException("static")),
            UnsupportedOperationException(),
        )
        wrapped.forEach { e ->
            val mapped = ScriptErrorMapper.map(e, "mod.fn")
            assertTrue(e.javaClass.name, mapped is ScriptCallException && mapped.wrappedException === e && mapped.method == "mod.fn")
        }
    }

    @Test
    fun detailsNameTheMemberAndTheException() {
        assertEquals("Failed to call method \"base64.decode\": [ java.io.IOException: io ]", ScriptCallException("base64.decode", IOException("io")).details())
        assertEquals("Failed to call method \"new probe\": [ java.lang.UnsupportedOperationException ]", ScriptCallException("new probe", UnsupportedOperationException()).details())
    }

    @Test
    fun debugSwitchesAreAskedAboutTheTopLevelModule() {
        ScriptErrorMapper.map(IllegalStateException(), "base64.encode")
        ScriptErrorMapper.map(IllegalStateException(), "util.inspect.format", "util")
        ScriptErrorMapper.map(IllegalStateException(), "probe")
        assertEquals(listOf("base64", "util", "probe"), asked)
    }

    @Test
    fun enabledModulesWriteOneConsoleEntryAndAFailingConsoleKeepsTheError() {
        ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { it == "probe" }
        val e = IllegalStateException("boom")
        val stderr = captureStderr {
            assertTrue(ScriptErrorMapper.map(e, "probe.fail", console = console) is ScriptCallException)
            assertTrue(ScriptErrorMapper.map(e, "other.fail", console = console) is ScriptCallException)
        }
        assertEquals(1, entries.size)
        assertTrue(entries.single(), entries.single().startsWith("[modules] probe.fail: java.lang.IllegalStateException: boom"))
        // Without android.util.Log (plain JVM) the full stack trace goes to System.err instead of logcat.
        assertTrue(stderr, stderr.contains("java.lang.IllegalStateException: boom"))

        val failing = ScriptErrorMapper.DebugConsole { _, _ -> throw IllegalStateException("console") }
        captureStderr {
            val mapped = ScriptErrorMapper.map(e, "probe.fail", console = failing)
            assertTrue(mapped is ScriptCallException && mapped.wrappedException === e)
        }
    }

    @Test
    fun debugEntryKeepsTheFramesOfTheModulesPackage() {
        val e = IllegalStateException("boom").apply {
            stackTrace = arrayOf(
                StackTraceElement("org.autojs.autojs.runtime.api.modules.base64.Base64Module", "decode", "Base64Module.kt", 40),
                StackTraceElement("android.util.Base64", "decode", "Base64.java", 118),
                StackTraceElement("org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding", "f1", "Base64ModuleBinding.kt", 72),
                StackTraceElement("org.mozilla.javascript.Interpreter", "interpretLoop", "Interpreter.java", 1),
            )
        }
        assertEquals(
            listOf(
                "[modules] base64.decode: java.lang.IllegalStateException: boom",
                "    at org.autojs.autojs.runtime.api.modules.base64.Base64Module.decode(Base64Module.kt:40)",
                "    at org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding.f1(Base64ModuleBinding.kt:72)",
                "    ... 2 more",
            ),
            ScriptErrorMapper.debugEntry("base64.decode", e).lines(),
        )
        val bare = UnsupportedOperationException().apply { stackTrace = emptyArray() }
        assertEquals("[modules] m.f: java.lang.UnsupportedOperationException", ScriptErrorMapper.debugEntry("m.f", bare))
    }

    private fun captureStderr(block: () -> Unit): String {
        val previous = System.err
        val out = ByteArrayOutputStream()
        System.setErr(PrintStream(out, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setErr(previous)
        }
        return out.toString("UTF-8")
    }

}
