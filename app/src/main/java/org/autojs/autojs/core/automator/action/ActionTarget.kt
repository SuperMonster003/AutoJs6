package org.autojs.autojs.core.automator.action

import android.graphics.Rect

/**
 * Created by Stardust on 2017/1/27.
 */
interface ActionTarget {

    fun createAction(action: Int, vararg params: Any): SimpleAction

    class TextActionTarget(private var mText: String, private var mIndex: Int) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithTextFilter(action, mText, mIndex)

    }

    class BoundsActionTarget(private var mBoundsInRect: Rect) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithBoundsFilter(action, mBoundsInRect)

    }

    class EditableActionTarget(private val mIndex: Int) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithEditableFilter(action, mIndex, params[0].toString())

    }

    class IdActionTarget(private val mId: String) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithIdFilter(action, mId)

    }

}
