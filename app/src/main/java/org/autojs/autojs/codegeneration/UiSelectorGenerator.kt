package org.autojs.autojs.codegeneration

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.tool.Consumer

/**
 * Created by Stardust on 2017/12/7.
 * Modified by SuperMonster003 as of Sep 2, 2022.
 */
class UiSelectorGenerator(private val mRoot: UiObject, private val mTarget: UiObject) {

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
        CodeGenerator.UNTIL_FIND -> selector.findAndReturnList(mRoot, 1).isNotEmpty()
        else -> selector.findAndReturnList(mRoot, 2).size == 1
    }

    private fun tryWithIntCondition(selector: UiSelector, value: Int, condition: Consumer<Int>): Boolean {
        condition.accept(value)
        return shouldStopGeneration(selector)
    }
}