package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Dec 27, 2025.
 */
class StringMatchFilter : RegexFilter {

    override val actionName = "match"

    constructor(s: String, keyGetter: KeyGetter) : super(s, keyGetter)
    constructor(regex: NativeRegExp, keyGetter: KeyGetter) : super(regex, keyGetter)

    override fun filter(node: UiObject) = keyGetter.getKey(node)?.contains(compiledRegex) == true

}
