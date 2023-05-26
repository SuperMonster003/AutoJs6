package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.console.JsConsoleView
import org.autojs.autojs.core.graphics.JsCanvasView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.*

/**
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
object ViewAttributesFactory {

    private val sViewAttributesCreators = HashMap<Class<out View>, ViewAttributesCreator>()

    init {
        put(JsAppBarLayout::class.java, ::JsAppBarLayoutAttributes)
        put(JsButton::class.java, ::JsButtonAttributes)
        put(JsCanvasView::class.java, ::JsCanvasViewAttributes)
        put(JsCardView::class.java, ::JsCardViewAttributes)
        put(JsCheckBox::class.java, ::JsCheckBoxAttributes)
        put(JsConsoleView::class.java, ::JsConsoleViewAttributes)
        put(JsDatePicker::class.java, ::JsDatePickerAttributes)
        put(JsDrawerLayout::class.java, ::JsDrawerLayoutAttributes)
        put(JsEditText::class.java, ::JsEditTextAttributes)
        put(JsFloatingActionButton::class.java, ::JsFloatingActionButtonAttributes)
        put(JsFrameLayout::class.java, ::JsFrameLayoutAttributes)
        put(JsGridView::class.java, ::JsGridViewAttributes)
        put(JsImageButton::class.java, ::JsImageButtonAttributes)
        put(JsImageView::class.java, ::JsImageViewAttributes)
        put(JsLinearLayout::class.java, ::JsLinearLayoutAttributes)
        put(JsListView::class.java, ::JsListViewAttributes)
        put(JsProgressBar::class.java, ::JsProgressBarAttributes)
        put(JsRadioButton::class.java, ::JsRadioButtonAttributes)
        put(JsRadioGroup::class.java, ::JsRadioGroupAttributes)
        put(JsRatingBar::class.java, ::JsRatingBarAttributes)
        put(JsRelativeLayout::class.java, ::JsRelativeLayoutAttributes)
        put(JsScrollView::class.java, ::JsScrollViewAttributes)
        put(JsSeekBar::class.java, ::JsSeekbarAttributes)
        put(JsSpinner::class.java, ::JsSpinnerAttributes)
        put(JsSwitch::class.java, ::JsSwitchAttributes)
        put(JsTabLayout::class.java, ::JsTabLayoutAttributes)
        put(JsTextClock::class.java, ::JsTextClockAttributes)
        put(JsTimePicker::class.java, ::JsTimePickerAttributes)
        put(JsToggleButton::class.java, ::JsToggleButtonAttributes)
        put(JsToolbar::class.java, ::JsToolbarAttributes)
        put(JsViewPager::class.java, ::JsViewPagerAttributes)
        put(JsWebView::class.java, ::JsWebViewAttributes)

        put(ViewGroup::class.java, ::ViewGroupAttributes)
        put(View::class.java, ::ViewAttributes)

        when (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            true -> put(JsTextViewLegacy::class.java, ::JsTextViewLegacyAttributes)
            else -> put(JsTextView::class.java, ::JsTextViewAttributes)
        }
    }

    fun put(clazz: Class<out View>, creator: (ResourceParser, View) -> ViewAttributes) {
        sViewAttributesCreators[clazz] = object : ViewAttributesCreator {
            override fun create(resourceParser: ResourceParser, view: View) = creator(resourceParser, view)
        }
    }

    @JvmStatic
    fun create(resourceParser: ResourceParser, view: View): ViewAttributes {
        var viewClass: Class<*>? = view.javaClass
        while (viewClass != null && viewClass != Any::class.java) {
            val creator = sViewAttributesCreators[viewClass]
            if (creator != null) {
                return creator.create(resourceParser, view)
            }
            viewClass = viewClass.superclass
        }
        return ViewAttributes(resourceParser, view)
    }

    interface ViewAttributesCreator {

        fun create(resourceParser: ResourceParser, view: View): ViewAttributes

    }

}