package org.autojs.build

class Formatted(
    private val title: CharSequence,
    private val contents: Collection<CharSequence> = emptyList(),
    private val subtitle: CharSequence? = null
) {
    private val formattedOutput: List<CharSequence> = run {
        val maxLength = (listOfNotNull(title, subtitle) + contents).maxOf { it.length }
        buildList {
            add("=".repeat(maxLength))
            add(title)
            subtitle?.let { add(it) }
            if (contents.isNotEmpty()) add("-".repeat(maxLength))
            addAll(contents)
            add("=".repeat(maxLength))
            add("")
        }
    }

    @JvmOverloads
    fun print(contentsMatters: Boolean = false) {
        formattedOutput.forEach {
            if (!contentsMatters || contents.isNotEmpty()) {
                println(it)
            }
        }
    }

    fun throwException() {
        throw Exception(formattedOutput.joinToString("\n"))
    }
}
