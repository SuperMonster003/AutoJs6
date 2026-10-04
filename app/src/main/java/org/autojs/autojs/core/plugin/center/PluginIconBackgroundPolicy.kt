package org.autojs.autojs.core.plugin.center

/** Presentation belongs to the circular container, never to the transparent PNG. */
object PluginIconBackgroundPolicy {
    private val color = Regex("#[0-9a-fA-F]{6}")

    fun normalized(value: String?): String? = value?.trim()?.lowercase()?.takeIf {
        it == "transparent" || color.matches(it)
    }

    fun resolve(packageName: String, night: Boolean, dayColor: String?, nightColor: String?, installed: Int? = null): Int {
        val three = packageName.startsWith("io.github.supermonster003.autojs6.plugin.three.") ||
            packageName in setOf("io.github.supermonster003.autojs6.plugin.threestoneai", "io.github.supermonster003.autojs6.plugin.audioplayer")
        val value = if (three) {
            if (night) "#212121" else "#fafafa"
        } else {
            normalized(if (night) nightColor else dayColor) ?: normalized(dayColor)
        }
        return when (value) {
            null -> installed ?: 0
            "transparent" -> 0
            else -> (0xff000000L or value.drop(1).toLong(16)).toInt()
        }
    }
}
