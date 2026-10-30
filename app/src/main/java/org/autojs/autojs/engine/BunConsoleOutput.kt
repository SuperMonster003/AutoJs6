package org.autojs.autojs.engine

import android.util.Log
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

/**
 * Rhino's console output for the Bun host client (api-coverage P4.3, D-10): the `println` route of console.control takes the
 * lines of one event loop turn, `[[level, text], ...]`, and prints them in order at Rhino's levels (log and print at debug,
 * assert at assert) on the execution's console. console.control sits outside the per-run call budget, as streaming output
 * does; this text has a budget of its own, the size of the run's stdout / stderr budget, after which [println] answers false
 * and the client writes to Bun's stdout instead (Q171 (a)).
 */
internal class BunConsoleOutput(private val print: (Int, String) -> Unit) {

    private val bytes = AtomicLong(0)

    /** Prints the entries of [args], or answers false when they exceed what is left of the budget (nothing is printed). */
    fun println(args: JSONArray): Boolean {
        val entries = args.optJSONArray(0)?.takeIf { args.length() == 1 }
            ?: throw IllegalArgumentException("console.println expects one list of [level, text] entries")
        val lines = List(entries.length()) { index ->
            val entry = entries.optJSONArray(index)?.takeIf { it.length() == 2 }
                ?: throw IllegalArgumentException("console.println entries are [level, text]")
            val level = LEVELS[entry.opt(0)] ?: throw IllegalArgumentException("Unknown console level: ${entry.opt(0)}")
            level to (entry.opt(1) as? String ?: throw IllegalArgumentException("console.println text must be a string"))
        }
        val size = lines.sumOf { it.second.toByteArray(Charsets.UTF_8).size.toLong() }
        while (true) {
            val used = bytes.get()
            if (size > BunRuntimeContract.MAX_OUTPUT_BYTES - used) return false
            if (bytes.compareAndSet(used, used + size)) break
        }
        lines.forEach { (level, text) -> print(level, text) }
        return true
    }

    companion object {

        const val METHOD = "println"

        val LEVELS = mapOf("verbose" to Log.VERBOSE, "log" to Log.DEBUG, "info" to Log.INFO, "warn" to Log.WARN, "error" to Log.ERROR, "assert" to Log.ASSERT)
    }

}
