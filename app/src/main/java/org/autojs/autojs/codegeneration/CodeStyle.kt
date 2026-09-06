package org.autojs.autojs.codegeneration

/**
 * The style of the code the layout inspector generates: the classic `selector.findOne().click()`
 * or the Flow form of roadmap P3 (`waitThenClick(selector).sync()`).
 * zh-CN: 布局分析器生成代码的风格: 经典的 `selector.findOne().click()` 或路线图 P3 的 Flow 形式 (`waitThenClick(selector).sync()`).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
enum class CodeStyle {

    CLASSIC,
    FLOW,

}

/**
 * The Flow rendering: `waitThenClick(sel).sync()` / `waitThenLongClick(sel).sync()` for the two
 * click actions, `waitAsync(sel).action.sync()` for the others, `waitAsync(sel).sync()` without
 * an action. Only the "find one" and "wait for" search modes have a Flow form (a wait is what
 * both do); "until find all" and "exists" keep the classic code.
 * zh-CN: Flow 渲染: 两种点击动作为 `waitThenClick(sel).sync()` / `waitThenLongClick(sel).sync()`, 其它动作为
 * `waitAsync(sel).action.sync()`, 无动作时为 `waitAsync(sel).sync()`. 只有 "找到一个" 与 "等待出现" 两种搜索模式有 Flow 形式
 * (二者做的都是等待); "找到全部" 与 "判断存在" 保持经典代码.
 */
object FlowCodeStyle {

    /** The Flow form of [selector] with [action] (a `UiObject` call like `click()`, or empty), or `null` when [searchMode] has none. zh-CN: 带 [action] (`click()` 这样的 `UiObject` 调用, 或空) 的 [selector] 的 Flow 形式; [searchMode] 没有 Flow 形式时为 `null`. */
    fun render(selector: String, searchMode: Int, action: String): String? {
        if (searchMode != CodeGenerator.FIND_ONE && searchMode != CodeGenerator.WAIT_FOR) return null
        return when (action) {
            "" -> "waitAsync($selector).sync()"
            "click()" -> "waitThenClick($selector).sync()"
            "longClick()" -> "waitThenLongClick($selector).sync()"
            else -> "waitAsync($selector).$action.sync()"
        }
    }

}
