package org.autojs.autojs.model.autocomplete

/**
 * Created by Stardust on Sep 27, 2017.
 * Transformed by 抠脚本人 (https://github.com/little-alei) on Jul 11, 2023.
 */
class CodeCompletions(val from: Int, private val mCompletions: List<CodeCompletion>) {
    fun size(): Int {
        return mCompletions.size
    }

    fun getHint(position: Int): String {
        return mCompletions[position].hint
    }

    operator fun get(pos: Int): CodeCompletion {
        return mCompletions[pos]
    }

    fun getUrl(pos: Int): String? {
        return mCompletions[pos].url
    }

    companion object {
        @JvmStatic
        fun just(hints: List<String?>): CodeCompletions {
            val completions: MutableList<CodeCompletion> = ArrayList(hints.size)
            for (hint in hints) {
                completions.add(CodeCompletion(hint!!, null, 0))
            }
            return CodeCompletions(-1, completions)
        }
    }
}