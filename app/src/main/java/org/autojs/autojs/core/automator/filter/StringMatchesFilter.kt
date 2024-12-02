package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 */
class StringMatchesFilter internal constructor(private val mRegex: String, private val mKeyGetter: KeyGetter) : Filter {

    // @Hint by SuperMonster003 on Oct 17, 2022.
    //  ! Kotlin method "CharSequence.matches(regex: Regex): Boolean"
    //  ! indicates whether the regular expression matches the ENTIRE input (not partial input).
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/matches.html
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/matches.html
    //  ! https://stackoverflow.com/questions/21883629/difference-in-results-between-java-matches-vs-javascript-match
    //  ! zh-CN:
    //  ! Kotlin 的方法 "CharSequence.matches(regex: Regex): Boolean"
    //  ! 表示正则表达式是否完全匹配输入 (注意是完全而非部分匹配).
    //  ! 参考链接见上.
    override fun filter(node: UiObject) = mKeyGetter.getKey(node)?.matches(mRegex.toRegex()) ?: false

    override fun toString(): String {
        val regexStr = when (mRegex.isEmpty()) {
            true -> "(?:)"
            else -> mRegex.toRegex().toString().replace("/", "\\/")
        }
        return "${mKeyGetter}Matches(/$regexStr/)"
    }

}
