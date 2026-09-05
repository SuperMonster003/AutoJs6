package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector.Companion.ID_IDENTIFIER

/**
 * The argument rule of `id(s)` and `idStartsWith(prefix)`: an argument without `:id/` is compared
 * with the entry name of the resource id (`login` of `com.example:id/login`), one with it against
 * the full id. The other id builders are plain [StringPropertyFilter]s over [StringProperty.ID].
 * zh-CN: `id(s)` 与 `idStartsWith(prefix)` 的参数规则: 不含 `:id/` 的参数与资源 id 的条目名 (`com.example:id/login` 中的
 * `login`) 比较, 含它的与完整 id 比较. 其余 id builder 是 [StringProperty.ID] 上的普通 [StringPropertyFilter].
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
object IdFilter {

    @Suppress("CovariantEquals")
    fun equals(s: String) = StringPropertyFilter(propertyFor(s), StringMatcher.equals(s))

    fun startsWith(prefix: String) = StringPropertyFilter(propertyFor(prefix), StringMatcher.startsWith(prefix))

    private fun propertyFor(argument: String) = if (argument.contains(ID_IDENTIFIER)) StringProperty.ID else StringProperty.ID_ENTRY

}
