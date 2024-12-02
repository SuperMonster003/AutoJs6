package org.autojs.autojs.core.automator.action

import android.graphics.Rect

/**
 * Created by Stardust on Jan 27, 2017.
 */
interface ActionTarget {

    fun createAction(action: Int, vararg params: Any): SimpleAction

    class TextActionTarget(private var text: String, private var index: Int) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithTextFilter(action, text, index)

    }

    class BoundsActionTarget(private var boundsInRect: Rect) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithBoundsFilter(action, boundsInRect)

    }

    class EditableActionTarget(private val index: Int) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithEditableFilter(action, index, params[0].toString())

    }

    class IdActionTarget(private val id: String) : ActionTarget {

        override fun createAction(action: Int, vararg params: Any) = ActionFactory.createActionWithIdFilter(action, id)

    }

}
