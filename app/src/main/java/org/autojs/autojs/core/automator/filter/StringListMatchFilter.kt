package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Oct 17, 2022.
 */
class StringListMatchFilter internal constructor(private val mRegex: String, private val mKeysGetter: KeysGetter) : Filter {

    // @Hint by SuperMonster003 on Oct 17, 2022.
    //  ! Similar to JavaScript String.prototype.match
    //  ! Returns the result of matching a string against a regular expression.
    //  ! https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/match
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/contains-match-in.html
    //  ! https://stackoverflow.com/questions/21883629/difference-in-results-between-java-matches-vs-javascript-match
    override fun filter(node: UiObject) = mKeysGetter.getKeys(node).any {
        it ?: return@any false
        val prefix = "/"
        val suffix = "/i"
        if (mRegex.startsWith(prefix) && mRegex.endsWith(suffix)) {
            mRegex
                .slice(prefix.length until mRegex.length - suffix.length)
                .toRegex(RegexOption.IGNORE_CASE)
                .containsMatchIn(it)
        } else {
            mRegex.toRegex().containsMatchIn(it)
        }
    }

    override fun toString() = "${mKeysGetter}Match(\"$mRegex\")"

}
