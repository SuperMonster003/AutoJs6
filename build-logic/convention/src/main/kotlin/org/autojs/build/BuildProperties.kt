package org.autojs.build

import org.gradle.api.Project
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class BuildProperties private constructor(
    private val file: File,
    private val props: Properties,
) {

    val path: String get() = file.absolutePath

    operator fun get(propertyName: String): String = when {
        propertyName.contains("/") -> {
            get(propertyName.split("/"))
        }
        else -> requireValue(propertyName)
    }

    operator fun get(propertyInfo: List<String>): String {
        val (lib, body) = propertyInfo
        return requireValue(lib, body)
    }

    private fun requireString(key: String, alternate: String): String {
        return props.getProperty(key)
            ?: props.getProperty(alternate)
            ?: throw IllegalStateException("Property '$key' is missing in '${file.absolutePath}'")
    }

    fun requireString(key: String): String {
        return props.getProperty(key)
            ?: throw IllegalStateException("Property '$key' is missing in '${file.absolutePath}'")
    }

    fun requireInt(key: String): Int = requireString(key).toInt()

    fun getIntOrNull(key: String): Int? = props.getProperty(key)?.toIntOrNull()

    private fun requireValue(name: String): String {
        return when (val value = requireString("${name}_VERSION", name)) {
            "PUBLIC" -> requireString("PUBLIC_${name}_VERSION", "PUBLIC_${name}")
            else -> value
        }
    }

    private fun requireValue(lib: String, body: String): String {
        return when (val value = requireString("${lib}_${body}_VERSION", "${lib}_${body}")) {
            "PUBLIC" -> requireString("PUBLIC_${body}_VERSION", "PUBLIC_${body}")
            else -> value
        }
    }

    companion object {

        @JvmStatic
        fun loadFrom(project: Project): BuildProperties {
            return loadFrom("${project.rootDir}/version.properties")
        }

        @JvmStatic
        fun loadFrom(filePath: String): BuildProperties {
            val f = File(filePath)
            if (!f.canRead()) {
                throw FileNotFoundException("Cannot read file '$filePath'")
            }
            val p = Properties()
            f.inputStream().use { p.load(it) }
            return BuildProperties(f, p)
        }

    }

}
