package org.autojs.autojs.execution

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator

/** Host-owned context and bounded result slot for one registered-script invocation. */
class AgentScriptExecutionState(
    val runId: String,
    parametersJson: String,
    val presetName: String?,
) {
    val parametersJson: String
    private var resultJson: String? = null
    private var finished = false

    init {
        require(RUN_ID.matches(runId)) { "Invalid Agent run ID" }
        require(presetName == null || (presetName.toByteArray(Charsets.UTF_8).size <= 256 &&
            presetName.none(Character::isISOControl))) { "Invalid Agent preset name" }
        require(parametersJson.toByteArray(Charsets.UTF_8).size <= MAX_PARAMETERS_BYTES) { "Agent parameters exceed the byte limit" }
        requireJson(parametersJson)
        val parameters = JsonParser.parseString(parametersJson)
        require(parameters.isJsonObject) { "Agent parameters must be an object" }
        this.parametersJson = parameters.toString()
    }

    /** Last report wins until completion. Strings are immutable snapshots, never Rhino objects. */
    @Synchronized
    fun reportResult(json: String): Boolean {
        if (finished) return false
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_RESULT_BYTES) { "Agent result exceeds the byte limit" }
        requireJson(json)
        resultJson = json
        return true
    }

    @Synchronized fun getResultJson(): String? = resultJson

    /** Freezes both successful results and results interrupted by timeout/cancellation. */
    @Synchronized fun finish() { finished = true }

    fun contextJson(): String = JsonObject().apply {
        addProperty("runId", runId)
        add("parameters", JsonParser.parseString(parametersJson))
        addProperty("presetName", presetName)
    }.toString()

    override fun toString() = "AgentScriptExecutionState(parametersBytes=${parametersJson.toByteArray(Charsets.UTF_8).size})"

    companion object {
        const val MAX_RESULT_BYTES = ThreeStoveAgentContract.MAX_SCRIPT_RESULT_BYTES
        const val MAX_PARAMETERS_BYTES = ThreeStoveAgentContract.MAX_SCRIPT_ARGUMENTS_BYTES
        private val RUN_ID = Regex("^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$")

        /** Execution-layer hook also used by the public ai.agent.result/context helpers. */
        @JvmStatic
        fun report(engine: ScriptEngine<*>, json: String): Boolean = fromEngine(engine)?.reportResult(json) ?: false

        @JvmStatic
        fun contextJson(engine: ScriptEngine<*>): String? = fromEngine(engine)?.contextJson()

        @JvmStatic
        fun fromEngine(engine: ScriptEngine<*>): AgentScriptExecutionState? =
            (engine.getTag(ExecutionConfig.tag) as? ExecutionConfig)?.agentExecutionState

        private fun requireJson(json: String) {
            try { AiJsonSyntaxValidator.requireValid(json) } catch (_: Exception) {
                throw IllegalArgumentException("Agent value must be valid JSON")
            }
        }
    }
}
