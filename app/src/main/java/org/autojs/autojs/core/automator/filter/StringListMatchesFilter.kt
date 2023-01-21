package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 */
class StringListMatchesFilter internal constructor(private val mRegex: String, private val mKeysGetter: KeysGetter) : Filter {

    // @Hint by SuperMonster003 on Oct 17, 2022.
    //  ! Kotlin CharSequence.matches(regex: Regex): Boolean
    //  ! Indicates whether the regular expression matches the ENTIRE input.
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/matches.html
    //  ! https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/-regex/matches.html
    //  ! https://stackoverflow.com/questions/21883629/difference-in-results-between-java-matches-vs-javascript-match
    override fun filter(node: UiObject) = mKeysGetter.getKeys(node).any { it?.matches(mRegex.toRegex()) ?: false }

    override fun toString() = "${mKeysGetter}Matches(\"$mRegex\")"

}
