package org.autojs.autojs.core.pref

import java.io.File

/**
 * JVM stand-in for the app resources that key declarations read (docs/settings/ROADMAP.md P2, R3): key names through
 * [SettingKeyNames.resolver] and computed defaults through [SettingResources.reader], both answered from the XML files
 * of `app/src/main/res/values` (the default configuration, `@string/...` style references followed) and the ids of the
 * generated `R` classes. [install] replaces both readers and returns a handle that restores them; the resource reads
 * are recorded in [reads] ("R.bool.pref_guard_mode" and so on), so tests can check that a computed default reads the
 * resource its source names.
 *
 * zh-CN: 键声明所读应用资源的 JVM 替身 (ROADMAP P2, R3): 键名经 [SettingKeyNames.resolver], 计算型默认值经
 * [SettingResources.reader], 二者都从 `app/src/main/res/values` 的 XML (默认配置, 跟随 `@string/...` 等引用) 与生成的 `R` 类
 * id 取值. [install] 替换两者并返回可恢复的句柄; 资源读取记录在 [reads] 中, 供测试核对计算型默认值读取的正是其来源所写的资源.
 */
object SettingsTestResources {

    val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "tools/settings/classification.json").isFile }
            ?: throw IllegalStateException("tools/settings/classification.json not found above ${File("").absolutePath}")
    }

    /** Raw values by type ("string", "bool", "integer") and resource name. */
    private val values: Map<String, Map<String, String>> by lazy {
        val result = mapOf("string" to LinkedHashMap<String, String>(), "bool" to LinkedHashMap(), "integer" to LinkedHashMap())
        val pattern = Regex("""<(string|bool|integer)\s+name="([^"]+)"[^>]*>([\s\S]*?)</\1>""")
        File(root, "app/src/main/res/values").listFiles { f -> f.name.endsWith(".xml") }!!.sorted().forEach { file ->
            pattern.findAll(file.readText()).forEach { m -> result.getValue(m.groupValues[1])[m.groupValues[2]] = m.groupValues[3].trim() }
        }
        result
    }

    /** Resource names by id, from the generated R classes of the app. */
    private val names: Map<String, Map<Int, String>> by lazy {
        listOf("string", "bool", "integer").associateWith { type ->
            Class.forName("org.autojs.autojs6.R\$$type").fields.associate { it.getInt(null) to it.name }
        }
    }

    /** Resource reads in order, as "R.<type>.<name>". */
    val reads = mutableListOf<String>()

    fun nameOf(type: String, id: Int): String = names.getValue(type)[id] ?: throw IllegalArgumentException("No R.$type resource with id $id")

    /** The id of `R.<type>.<name>`. */
    fun idOf(type: String, name: String): Int = Class.forName("org.autojs.autojs6.R\$$type").getField(name).getInt(null)

    /** The value of `R.<type>.<name>` with references followed. */
    fun value(type: String, name: String): String {
        var currentType = type
        var currentName = name
        repeat(8) {
            val raw = values.getValue(currentType)[currentName] ?: throw IllegalArgumentException("No $currentType resource \"$currentName\" in res/values")
            val ref = Regex("^@(string|bool|integer)/(\\w+)$").find(raw) ?: return unescape(raw)
            currentType = ref.groupValues[1]
            currentName = ref.groupValues[2]
        }
        throw IllegalStateException("Reference loop at $type/$name")
    }

    fun string(id: Int): String = read("string", id)

    fun bool(id: Int): Boolean = read("bool", id).toBooleanStrict()

    fun int(id: Int): Int = read("integer", id).toInt()

    private fun read(type: String, id: Int): String {
        val name = nameOf(type, id)
        synchronized(reads) { reads.add("R.$type.$name") }
        return value(type, name)
    }

    private fun unescape(text: String) = text.replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

    /**
     * Installs the readers and returns a handle that restores the previous ones.
     * zh-CN: 安装读取器并返回恢复原读取器的句柄.
     */
    fun install(): AutoCloseable {
        val previousResolver = SettingKeyNames.resolver
        val previousReader = SettingResources.reader
        SettingKeyNames.resolver = ::string
        SettingResources.reader = object : SettingResources.Reader {
            override fun bool(id: Int) = this@SettingsTestResources.bool(id)
            override fun int(id: Int) = this@SettingsTestResources.int(id)
            override fun string(id: Int) = this@SettingsTestResources.string(id)
        }
        return AutoCloseable {
            SettingKeyNames.resolver = previousResolver
            SettingResources.reader = previousReader
        }
    }
}
