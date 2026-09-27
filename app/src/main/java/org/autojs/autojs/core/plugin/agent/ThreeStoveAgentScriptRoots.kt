package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import java.io.StringReader

/** The authenticated plugin may propose roots, never grants or other host configuration. */
internal object ThreeStoveAgentScriptRoots {
    fun configuration(json: String): String {
        require(json.toByteArray(Charsets.UTF_8).size <= ThreeStoveAgentContract.MAX_CONTEXT_BYTES)
        val roots = linkedSetOf<String>()
        JsonReader(StringReader(json)).use { reader ->
            @Suppress("DEPRECATION")
            reader.isLenient = false
            reader.beginObject()
            var seen = false
            while (reader.hasNext()) {
                require(reader.nextName() == "scriptRoots" && !seen)
                seen = true
                reader.beginArray()
                while (reader.hasNext()) {
                    require(reader.peek() == JsonToken.STRING && roots.size < 32)
                    val raw = reader.nextString()
                    require(raw.startsWith('/') && raw.toByteArray(Charsets.UTF_8).size <= 1024)
                    require(raw.none { it < ' ' || it == '\\' } && raw.split('/').none { it == "." || it == ".." })
                    val path = raw.trimEnd('/')
                    require(path.isNotEmpty() && roots.add(path))
                }
                reader.endArray()
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        return JsonObject().apply { add("scriptRoots", JsonArray().apply { roots.sorted().forEach(::add) }) }.toString()
    }
}
