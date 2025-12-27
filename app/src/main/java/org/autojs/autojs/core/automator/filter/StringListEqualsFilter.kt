package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by SuperMonster003 on Jun 10, 2022.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
class StringListEqualsFilter : RegexListFilter {

    constructor(s: String, keysGetter: KeysGetter) : super(s, keysGetter)
    constructor(regex: NativeRegExp, keysGetter: KeysGetter) : super(regex, keysGetter)

    override fun filter(node: UiObject) = when {
        isRegexSource -> keysGetter.getKeys(node).any {
            it?.matches(compiledRegex) == true
        }
        else -> keysGetter.getKeys(node).any {
            it?.contentEquals(input) == true
        }
    }
}
