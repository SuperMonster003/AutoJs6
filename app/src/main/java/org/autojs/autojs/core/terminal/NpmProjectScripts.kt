package org.autojs.autojs.core.terminal

import com.google.gson.JsonParser
import java.io.File

/**
 * Reads the `scripts` table of a `package.json` so the terminal can offer `npm run <script>`.
 * zh-CN: 读取 `package.json` 的 `scripts` 表, 供终端提供 `npm run <script>` 菜单.
 */
object NpmProjectScripts {

    const val PACKAGE_JSON = "package.json"

    data class Script(val name: String, val command: String)

    @JvmStatic
    fun packageJsonOf(directory: File): File = File(directory, PACKAGE_JSON)

    /**
     * @return the declared scripts in file order, or null when the file is missing or unparsable
     */
    @JvmStatic
    fun read(directory: File): List<Script>? {
        val file = packageJsonOf(directory)
        if (!file.isFile) return null
        return runCatching { parse(file.readText()) }.getOrNull()
    }

    /**
     * @return the declared scripts in file order; empty when the object has no `scripts` table
     * @throws IllegalArgumentException when [json] is not a JSON object
     */
    @JvmStatic
    fun parse(json: String): List<Script> {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "package.json root is not an object" }
        val scripts = root.asJsonObject.get("scripts")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        return scripts.entrySet().mapNotNull { (name, value) ->
            val command = value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return@mapNotNull null
            Script(name, command)
        }
    }

}
