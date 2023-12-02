package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
class StringMatchFilter internal constructor(private val mRegex: String, private val mKeyGetter: KeyGetter) : Filter {

    // @Hint by SuperMonster003 on Oct 17, 2022.
    //  ! Similar to JavaScript String.prototype.match
    //  ! Returns the result of matching a string against a regular expression.
    //  ! https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/match
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/contains-match-in.html
    //  ! https://stackoverflow.com/questions/21883629/difference-in-results-between-java-matches-vs-javascript-match
    override fun filter(node: UiObject) = mKeyGetter.getKey(node)?.contains(mRegex.toRegex()) ?: false

    override fun toString(): String {
        val regexStr = when (mRegex.isEmpty()) {
            true -> "(?:)"
            else -> mRegex.toRegex().toString().replace("/", "\\/")
        }
        return "${mKeyGetter}Match(/$regexStr/)"
    }

}
