package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by SuperMonster003 on Oct 17, 2022.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
class StringListMatchFilter : RegexListFilter {

    override val actionName = "match"

    constructor(s: String, keysGetter: KeysGetter) : super(s, keysGetter)
    constructor(regex: NativeRegExp, keysGetter: KeysGetter) : super(regex, keysGetter)

    override fun filter(node: UiObject) = keysGetter.getKeys(node).any {
        it?.contains(compiledRegex) == true
    }

}
