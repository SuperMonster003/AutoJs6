package org.autojs.autojs.codegeneration

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.tool.Consumer

/**
 * Builds the shortest selector that singles [mTarget] out under [mRoot] (a captured layout, see
 * [ReadOnlyUiObject]): properties are added in order of preference (id, class name, text,
 * description, the boolean properties that are set, depth) until the search under the root
 * yields exactly the target.
 * zh-CN: 构建在 [mRoot] (捕获的布局, 见 [ReadOnlyUiObject]) 下唯一确定 [mTarget] 的最短选择器: 按优先级 (id, 类名, 文本,
 * 描述, 已置位的布尔属性, 深度) 逐个添加属性, 直到根节点下的搜索恰好得到目标.
 *
 * Created by Stardust on Dec 7, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
class UiSelectorGenerator(private val mRoot: ReadOnlyUiObject, private val mTarget: ReadOnlyUiObject) {

    private var mUsingId = true
    private var mUsingDesc = true
    private var mUsingText = true
    private var mSearchMode = CodeGenerator.FIND_ONE

    fun generateSelector(): UiSelector? {

        val selector = UiSelector()

        if (mUsingId && tryWithStringCondition(selector, mTarget.id(), selector::id)) {
            return selector
        }
        if (tryWithStringCondition(selector, mTarget.className(), selector::className)) {
            return selector
        }
        if (mUsingText && tryWithStringCondition(selector, mTarget.text(), selector::text)) {
            return selector
        }
        if (mUsingDesc && tryWithStringCondition(selector, mTarget.desc(), selector::desc)) {
            return selector
        }
        if (mTarget.scrollable() && tryWithBooleanCondition(selector, mTarget.scrollable(), selector::scrollable)) {
            return selector
        }
        if (mTarget.clickable() && tryWithBooleanCondition(selector, mTarget.clickable(), selector::clickable)) {
            return selector
        }
        if (mTarget.selected() && tryWithBooleanCondition(selector, mTarget.selected(), selector::selected)) {
            return selector
        }
        if (mTarget.checkable() && tryWithBooleanCondition(selector, mTarget.checkable(), selector::checkable)) {
            return selector
        }
        if (mTarget.checked() && tryWithBooleanCondition(selector, mTarget.checked(), selector::checked)) {
            return selector
        }
        if (mTarget.longClickable() && tryWithBooleanCondition(selector, mTarget.longClickable(), selector::longClickable)) {
            return selector
        }
        if (tryWithIntCondition(selector, mTarget.depth(), selector::depth)) {
            return selector
        }
        return null
    }

    fun generateSelectorCode(): String? {
        val selector = generateSelector() ?: return null
        return when (mSearchMode) {
            CodeGenerator.FIND_ONE -> "$selector.findOne()"
            CodeGenerator.UNTIL_FIND -> "$selector.untilFind()"
            else -> selector.toString()
        }
    }

    fun setUsingId(usingId: Boolean) {
        mUsingId = usingId
    }

    fun setUsingDesc(usingDesc: Boolean) {
        mUsingDesc = usingDesc
    }

    fun setUsingText(usingText: Boolean) {
        mUsingText = usingText
    }

    fun setSearchMode(searchMode: Int) {
        mSearchMode = searchMode
    }

    private fun tryWithBooleanCondition(selector: UiSelector, value: Boolean, condition: Consumer<Boolean>): Boolean {
        condition.accept(value)
        return shouldStopGeneration(selector)
    }

    private fun tryWithStringCondition(selector: UiSelector, value: String?, condition: Consumer<String>): Boolean {
        if (value == null || value.isEmpty()) {
            return false
        }
        condition.accept(value)
        return shouldStopGeneration(selector)
    }

    private fun shouldStopGeneration(selector: UiSelector) = when (mSearchMode) {
        CodeGenerator.UNTIL_FIND -> selector.searchIn(mRoot, 1).isNotEmpty()
        else -> selector.searchIn(mRoot, 2).size == 1
    }

    private fun tryWithIntCondition(selector: UiSelector, value: Int, condition: Consumer<Int>): Boolean {
        condition.accept(value)
        return shouldStopGeneration(selector)
    }
}