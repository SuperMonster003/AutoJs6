package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject

internal enum class NodeFuzzerBudget(val id: String) {
    QUICK("quick"),
    NORMAL("normal"),
    EXTENDED("extended");

    fun caseCount(quick: Int, normal: Int, extended: Int): Int {
        return when (this) {
            QUICK -> quick
            NORMAL -> normal
            EXTENDED -> extended
        }
    }

    fun timeoutMs(quick: Long, normal: Long, extended: Long): Long {
        return when (this) {
            QUICK -> quick
            NORMAL -> normal
            EXTENDED -> extended
        }
    }

    fun <T> selectCases(cases: List<T>, quick: Int, normal: Int = cases.size, extended: Int = cases.size): List<T> {
        return cases.take(caseCount(quick, normal, extended).coerceAtMost(cases.size))
    }

    companion object {
        fun current(): NodeFuzzerBudget {
            val raw = InstrumentationRegistry.getArguments().getString(ARG_BUDGET).orEmpty()
                .ifBlank { "normal" }
                .trim()
                .lowercase()
            return entries.firstOrNull { it.id == raw }
                ?: error("Unsupported $ARG_BUDGET='$raw'; use quick, normal, or extended.")
        }

        const val ARG_BUDGET = "autojs.nodejs.fuzzerBudget"
    }
}

internal object NodeFuzzerSupport {
    const val SEED_PREFIX = "autojs6.fuzzer.seed="
    const val ARG_SEED = "autojs.nodejs.fuzzerSeed"

    fun baseSeed(fuzzer: String, defaultSeed: Long): Long {
        val args = InstrumentationRegistry.getArguments()
        return args.getString("autojs.nodejs.$fuzzer.seed")
            ?.trim()
            ?.toLongOrNull()
            ?: args.getString(ARG_SEED)
                ?.trim()
                ?.toLongOrNull()
            ?: defaultSeed
    }

    fun reportFailure(
        fuzzer: String,
        budget: NodeFuzzerBudget,
        baseSeed: Long,
        caseCount: Int,
        replayClass: String,
        replayMethod: String? = null,
        failure: JSONObject? = null,
        throwable: Throwable? = null,
    ) {
        val json = JSONObject()
            .put("schema", "autojs6-node-fuzzer-seed-v1")
            .put("fuzzer", fuzzer)
            .put("budget", budget.id)
            .put("baseSeed", baseSeed)
            .put("caseCount", caseCount)
            .put("replayClass", replayClass)
            .put("replayMethod", replayMethod ?: JSONObject.NULL)
            .put("replayArgs", JSONObject()
                .put("class", replayMethod?.let { "$replayClass#$it" } ?: replayClass)
                .put(NodeFuzzerBudget.ARG_BUDGET, budget.id)
                .put(ARG_SEED, baseSeed.toString()))
        failure?.let { json.put("failure", it) }
        throwable?.let {
            json.put(
                "throwable",
                JSONObject()
                    .put("type", it.javaClass.name)
                    .put("message", it.message.orEmpty().take(512)),
            )
        }
        println(SEED_PREFIX + json)
    }
}
