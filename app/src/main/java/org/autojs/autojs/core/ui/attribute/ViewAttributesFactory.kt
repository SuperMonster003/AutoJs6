package org.autojs.autojs.core.ui.attribute

import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.core.console.ConsoleView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.widget.*
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.core.ui.widget.JsConsoleView

/**
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
object ViewAttributesFactory {

    private val sViewAttributesCreators = HashMap<Class<out View>, ViewAttributesCreator>()

    init {
        // @Hint by SuperMonster003 on Jun 12, 2023.
        //  ! ASCII Tree for Views with attributes appended to.
        //  !
        //  ! android.view.View
        //  ! ├─ android.view.SurfaceView
        //  ! │  ├─ android.widget.VideoView
        //  ! │  │  ├─ JsVideoView
        //  ! ├─ android.view.TextureView
        //  ! │  ├─ JsCanvasView
        //  ! ├─ android.view.ViewGroup
        //  ! │  ├─ android.widget.AbsoluteLayout
        //  ! │  │  ├─ android.webkit.WebView
        //  ! │  │  │  ├─ JsWebView
        //  ! │  ├─ android.widget.AdapterView
        //  ! │  │  ├─ android.widget.AbsSpinner
        //  ! │  │  │  ├─ android.widget.Spinner
        //  ! │  │  │  │  ├─ androidx.appcompat.widget.AppCompatSpinner
        //  ! │  │  │  │  │  ├─ JsSpinner
        //  ! │  ├─ android.widget.FrameLayout
        //  ! │  │  ├─ android.widget.CalendarView
        //  ! │  │  │  ├─ JsCalendarView
        //  ! │  │  ├─ android.widget.DatePicker
        //  ! │  │  │  ├─ JsDatePicker
        //  ! │  │  ├─ android.widget.HorizontalScrollView
        //  ! │  │  │  ├─ com.google.android.material.tabs.TabLayout
        //  ! │  │  │  │  ├─ JsTabLayout
        //  ! │  │  ├─ android.widget.ScrollView
        //  ! │  │  │  ├─ JsScrollView
        //  ! │  │  ├─ android.widget.TimePicker
        //  ! │  │  │  ├─ JsTimePicker
        //  ! │  │  ├─ android.widget.ViewAnimator
        //  ! │  │  │  ├─ android.widget.ViewFlipper
        //  ! │  │  │  │  ├─ JsViewFlipper
        //  ! │  │  │  ├─ android.widget.ViewSwitcher
        //  ! │  │  │  │  ├─ android.widget.ImageSwitcher
        //  ! │  │  │  │  │  ├─ JsImageSwitcher
        //  ! │  │  │  │  ├─ android.widget.TextSwitcher
        //  ! │  │  │  │  │  ├─ JsTextSwitcher
        //  ! │  │  │  │  ├─ JsViewSwitcher
        //  ! │  │  ├─ androidx.cardview.widget.CardView
        //  ! │  │  │  ├─ JsCardView
        //  ! │  │  ├─ org.autojs.autojs.core.console.ConsoleView
        //  ! │  │  │  ├─ JsConsoleView
        //  ! │  │  ├─ JsFrameLayout
        //  ! │  ├─ android.widget.LinearLayout
        //  ! │  │  ├─ android.widget.NumberPicker
        //  ! │  │  │  ├─ JsNumberPicker
        //  ! │  │  ├─ android.widget.RadioGroup
        //  ! │  │  │  ├─ JsRadioGroup
        //  ! │  │  ├─ android.widget.SearchView
        //  ! │  │  │  ├─ JsSearchView
        //  ! │  │  ├─ com.google.android.material.appbar.AppBarLayout
        //  ! │  │  │  ├─ JsAppBarLayout
        //  ! │  │  ├─ JsLinearLayout
        //  ! │  ├─ android.widget.RelativeLayout
        //  ! │  │  ├─ JsRelativeLayout
        //  ! │  ├─ androidx.appcompat.widget.Toolbar
        //  ! │  │  ├─ JsToolbar
        //  ! │  ├─ androidx.drawerlayout.widget.DrawerLayout
        //  ! │  │  ├─ JsDrawerLayout
        //  ! │  ├─ androidx.recyclerview.widget.RecyclerView
        //  ! │  │  ├─ JsListView
        //  ! │  │  │  ├─ JsGridView
        //  ! │  ├─ androidx.viewpager.widget.ViewPager
        //  ! │  │  ├─ JsViewPager
        //  ! ├─ android.widget.ImageView
        //  ! │  ├─ android.widget.ImageButton
        //  ! │  │  ├─ com.google.android.material.internal.VisibilityAwareImageButton
        //  ! │  │  │  ├─ com.google.android.material.floatingactionbutton.FloatingActionButton
        //  ! │  │  │  │  ├─ JsFloatingActionButton
        //  ! │  │  ├─ JsImageButton
        //  ! │  ├─ android.widget.QuickContactBadge
        //  ! │  │  ├─ JsQuickContactBadge
        //  ! │  ├─ com.makeramen.roundedimageview.RoundedImageView
        //  ! │  │  ├─ JsImageView
        //  ! ├─ android.widget.ProgressBar
        //  ! │  ├─ android.widget.AbsSeekBar
        //  ! │  │  ├─ android.widget.RatingBar
        //  ! │  │  │  ├─ JsRatingBar
        //  ! │  │  ├─ android.widget.SeekBar
        //  ! │  │  │  ├─ JsSeekBar
        //  ! │  ├─ JsProgressBar
        //  ! ├─ android.widget.Space
        //  ! ├─ android.widget.TextView
        //  ! │  ├─ android.widget.Button
        //  ! │  │  ├─ android.widget.CompoundButton
        //  ! │  │  │  ├─ android.widget.RadioButton
        //  ! │  │  │  │  ├─ JsRadioButton
        //  ! │  │  │  ├─ android.widget.CheckBox
        //  ! │  │  │  │  ├─ androidx.appcompat.widget.AppCompatCheckBox
        //  ! │  │  │  │  │  ├─ JsCheckBox
        //  ! │  │  │  ├─ android.widget.ToggleButton
        //  ! │  │  │  │  │  ├─ JsToggleButton
        //  ! │  │  │  ├─ androidx.appcompat.widget.SwitchCompat
        //  ! │  │  │  │  ├─ JsSwitch
        //  ! │  │  ├─ JsButton
        //  ! │  ├─ android.widget.CheckedTextView
        //  ! │  │  ├─ JsCheckedTextView
        //  ! │  ├─ android.widget.Chronometer
        //  ! │  │  ├─ JsChronometer
        //  ! │  ├─ android.widget.EditText
        //  ! │  │  ├─ JsEditText
        //  ! │  ├─ android.widget.TextClock
        //  ! │  │  ├─ JsTextClock
        //  ! │  ├─ androidx.appcompat.widget.AppCompatTextView
        //  ! │  │  ├─ JsTextView

        /* Level 0. */

        put(View::class.java, ::ViewAttributes)

        /* Level 1. */

        put(ImageView::class.java, ::ImageViewAttributes)
        put(ProgressBar::class.java, ::ProgressBarAttributes)
        put(Space::class.java, ::SpaceAttributes)
        put(SurfaceView::class.java, ::SurfaceViewAttributes)
        put(TextView::class.java, ::TextViewAttributes)
        put(TextureView::class.java, ::TextureViewAttributes)
        put(ViewGroup::class.java, ::ViewGroupAttributes)

        /* Level 2. */

        put(AbsSeekBar::class.java, ::AbsSeekBarAttributes)
        put(AdapterView::class.java, ::AdapterViewAttributes)
        put(AppCompatTextView::class.java, ::AppCompatTextViewAttributes)
        put(Button::class.java, ::ButtonAttributes)
        put(CheckedTextView::class.java, ::CheckedTextViewAttributes)
        put(Chronometer::class.java, ::ChronometerAttributes)
        put(DrawerLayout::class.java, ::DrawerLayoutAttributes)
        put(EditText::class.java, ::EditTextAttributes)
        put(FrameLayout::class.java, ::FrameLayoutAttributes)
        put(ImageButton::class.java, ::ImageButtonAttributes)
        put(JsCanvasView::class.java, ::JsCanvasViewAttributes)
        put(JsProgressBar::class.java, ::JsProgressBarAttributes)
        put(LinearLayout::class.java, ::LinearLayoutAttributes)
        put(QuickContactBadge::class.java, ::QuickContactBadgeAttributes)
        put(RecyclerView::class.java, ::RecyclerViewAttributes)
        put(RelativeLayout::class.java, ::RelativeLayoutAttributes)
        put(RoundedImageView::class.java, ::RoundedImageViewAttributes)
        put(TextClock::class.java, ::TextClockAttributes)
        put(Toolbar::class.java, ::ToolbarAttributes)
        put(VideoView::class.java, ::VideoViewAttributes)
        put(ViewPager::class.java, ::ViewPagerAttributes)

        /* Level 3. */

        put(AbsSpinner::class.java, ::AbsSpinnerAttributes)
        put(AppBarLayout::class.java, ::AppBarLayoutAttributes)
        put(CalendarView::class.java, ::CalendarViewAttributes)
        put(CardView::class.java, ::CardViewAttributes)
        put(CompoundButton::class.java, ::CompoundButtonAttributes)
        put(ConsoleView::class.java, ::ConsoleViewAttributes)
        put(DatePicker::class.java, ::DatePickerAttributes)
        put(HorizontalScrollView::class.java, ::HorizontalScrollViewAttributes)
        put(JsButton::class.java, ::JsButtonAttributes)
        put(JsCheckedTextView::class.java, ::JsCheckedTextViewAttributes)
        put(JsChronometer::class.java, ::JsChronometerAttributes)
        put(JsDrawerLayout::class.java, ::JsDrawerLayoutAttributes)
        put(JsEditText::class.java, ::JsEditTextAttributes)
        put(JsFrameLayout::class.java, ::JsFrameLayoutAttributes)
        put(JsImageButton::class.java, ::JsImageButtonAttributes)
        put(JsImageView::class.java, ::JsImageViewAttributes)
        put(JsLinearLayout::class.java, ::JsLinearLayoutAttributes)
        put(JsListView::class.java, ::JsListViewAttributes)
        put(JsQuickContactBadge::class.java, ::JsQuickContactBadgeAttributes)
        put(JsRelativeLayout::class.java, ::JsRelativeLayoutAttributes)
        put(JsTextClock::class.java, ::JsTextClockAttributes)
        put(JsToolbar::class.java, ::JsToolbarAttributes)
        put(JsVideoView::class.java, ::JsVideoViewAttributes)
        put(JsViewPager::class.java, ::JsViewPagerAttributes)
        put(NumberPicker::class.java, ::NumberPickerAttributes)
        put(RadioGroup::class.java, ::RadioGroupAttributes)
        put(RatingBar::class.java, ::RatingBarAttributes)
        put(ScrollView::class.java, ::ScrollViewAttributes)
        put(SearchView::class.java, ::SearchViewAttributes)
        put(SeekBar::class.java, ::SeekBarAttributes)
        put(TimePicker::class.java, ::TimePickerAttributes)
        put(ViewAnimator::class.java, ::ViewAnimatorAttributes)
        put(WebView::class.java, ::WebViewAttributes)

        when (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            true -> put(JsTextViewLegacy::class.java, ::JsTextViewLegacyAttributes)
            else -> put(JsTextView::class.java, ::JsTextViewAttributes)
        }

        /* Level 4. */

        put(CheckBox::class.java, ::CheckBoxAttributes)
        put(FloatingActionButton::class.java, ::FloatingActionButtonAttributes)
        put(JsAppBarLayout::class.java, ::JsAppBarLayoutAttributes)
        put(JsCalendarView::class.java, ::JsCalendarViewAttributes)
        put(JsCardView::class.java, ::JsCardViewAttributes)
        put(JsConsoleView::class.java, ::JsConsoleViewAttributes)
        put(JsDatePicker::class.java, ::JsDatePickerAttributes)
        put(JsGridView::class.java, ::JsGridViewAttributes)
        put(JsNumberPicker::class.java, ::JsNumberPickerAttributes)
        put(JsRadioGroup::class.java, ::JsRadioGroupAttributes)
        put(JsRatingBar::class.java, ::JsRatingBarAttributes)
        put(JsScrollView::class.java, ::JsScrollViewAttributes)
        put(JsSearchView::class.java, ::JsSearchViewAttributes)
        put(JsSeekBar::class.java, ::JsSeekBarAttributes)
        put(JsTimePicker::class.java, ::JsTimePickerAttributes)
        put(JsWebView::class.java, ::JsWebViewAttributes)
        put(RadioButton::class.java, ::RadioButtonAttributes)
        put(Spinner::class.java, ::SpinnerAttributes)
        put(SwitchCompat::class.java, ::SwitchCompatAttributes)
        put(TabLayout::class.java, ::TabLayoutAttributes)
        put(ToggleButton::class.java, ::ToggleButtonAttributes)
        put(ViewFlipper::class.java, ::ViewFlipperAttributes)
        put(ViewSwitcher::class.java, ::ViewSwitcherAttributes)

        /* Level 5. */

        put(AppCompatCheckBox::class.java, ::AppCompatCheckBoxAttributes)
        put(AppCompatSpinner::class.java, ::AppCompatSpinnerAttributes)
        put(ImageSwitcher::class.java, ::ImageSwitcherAttributes)
        put(JsFloatingActionButton::class.java, ::JsFloatingActionButtonAttributes)
        put(JsRadioButton::class.java, ::JsRadioButtonAttributes)
        put(JsSwitch::class.java, ::JsSwitchAttributes)
        put(JsTabLayout::class.java, ::JsTabLayoutAttributes)
        put(JsViewFlipper::class.java, ::JsViewFlipperAttributes)
        put(JsViewSwitcher::class.java, ::JsViewSwitcherAttributes)
        put(TextSwitcher::class.java, ::TextSwitcherAttributes)

        /* Level 6. */

        put(JsCheckBox::class.java, ::JsCheckBoxAttributes)
        put(JsImageSwitcher::class.java, ::JsImageSwitcherAttributes)
        put(JsSpinner::class.java, ::JsSpinnerAttributes)
        put(JsTextSwitcher::class.java, ::JsTextSwitcherAttributes)
        put(JsToggleButton::class.java, ::JsToggleButtonAttributes)
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