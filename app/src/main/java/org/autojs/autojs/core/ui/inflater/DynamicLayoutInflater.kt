@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.InflateException
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
import androidx.appcompat.widget.Toolbar as AppCompatToolbar
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.ui.UiContentHost
import org.autojs.autojs.util.ContextUtils.findActivity
import org.autojs.autojs.core.automator.filter.ClassNameFilter
import org.autojs.autojs.core.ui.inflater.inflaters.BaseViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsActionMenuViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsAppBarLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCalendarViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCanvasViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCardViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCheckBoxInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsCheckedTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsChronometerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsConsoleViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsGlobalConsoleViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsDatePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsDrawerLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsEditTextInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsFloatingActionButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsFrameLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ComposeContainerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsGridViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsImageViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsLinearLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsListViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsNumberPickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsProgressBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsQuickContactBadgeInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRadioButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRadioGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRatingBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsRelativeLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsScrollViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSearchViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSeekBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSpinnerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsSwitchInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTabLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextClockInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsTimePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsToggleButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsToolbarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsVideoViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewFlipperInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewPagerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsViewSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.JsWebViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SpaceInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AbsSeekBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AbsSpinnerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ActionMenuViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AdapterViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AppBarLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AppCompatCheckBoxInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AppCompatSpinnerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AppCompatTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.AutoCompleteTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.CalendarViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.CardViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.CheckBoxInflater
import org.autojs.autojs.core.ui.inflater.inflaters.CheckedTextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ChronometerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.CompoundButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.DatePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.DrawerLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.EditTextInflater
import org.autojs.autojs.core.ui.inflater.inflaters.FloatingActionButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.FrameLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.HorizontalScrollViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ImageButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ImageSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ImageViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.LinearLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.NumberPickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ProgressBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.QuickContactBadgeInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RadioButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RadioGroupInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RatingBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RecyclerViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RelativeLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.RoundedImageViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ScrollViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SearchViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SeekBarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SpinnerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SurfaceViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.SwitchCompatInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TabLayoutInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TextClockInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TextSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TextViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TextureViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.TimePickerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ToggleButtonInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ToolbarInflater
import org.autojs.autojs.core.ui.inflater.inflaters.VideoViewInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewAnimatorInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewFlipperInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewPagerInflater
import org.autojs.autojs.core.ui.inflater.inflaters.ViewSwitcherInflater
import org.autojs.autojs.core.ui.inflater.inflaters.WebViewInflater
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.widget.JsActionMenuView
import org.autojs.autojs.core.ui.widget.JsAppBarLayout
import org.autojs.autojs.core.ui.widget.JsButton
import org.autojs.autojs.core.ui.widget.JsCalendarView
import org.autojs.autojs.core.ui.widget.JsCanvasView
import org.autojs.autojs.core.ui.widget.JsCardView
import org.autojs.autojs.core.ui.widget.JsCheckBox
import org.autojs.autojs.core.ui.widget.JsCheckedTextView
import org.autojs.autojs.core.ui.widget.JsChronometer
import org.autojs.autojs.core.ui.widget.JsConsoleView
import org.autojs.autojs.core.ui.widget.JsGlobalConsoleView
import org.autojs.autojs.core.ui.widget.JsDatePicker
import org.autojs.autojs.core.ui.widget.JsDrawerLayout
import org.autojs.autojs.core.ui.widget.JsEditText
import org.autojs.autojs.core.ui.widget.JsFloatingActionButton
import org.autojs.autojs.core.ui.widget.JsFrameLayout
import org.autojs.autojs.core.ui.widget.JsGridView
import org.autojs.autojs.core.ui.widget.JsImageButton
import org.autojs.autojs.core.ui.widget.JsImageSwitcher
import org.autojs.autojs.core.ui.widget.JsImageView
import org.autojs.autojs.core.ui.widget.JsLinearLayout
import org.autojs.autojs.core.ui.widget.JsListView
import org.autojs.autojs.core.ui.widget.JsNumberPicker
import org.autojs.autojs.core.ui.widget.JsProgressBar
import org.autojs.autojs.core.ui.widget.JsQuickContactBadge
import org.autojs.autojs.core.ui.widget.JsRadioButton
import org.autojs.autojs.core.ui.widget.JsRadioGroup
import org.autojs.autojs.core.ui.widget.JsRatingBar
import org.autojs.autojs.core.ui.widget.JsRelativeLayout
import org.autojs.autojs.core.ui.widget.JsScrollView
import org.autojs.autojs.core.ui.widget.JsSearchView
import org.autojs.autojs.core.ui.widget.JsSeekBar
import org.autojs.autojs.core.ui.widget.JsSpinner
import org.autojs.autojs.core.ui.widget.JsSwitch
import org.autojs.autojs.core.ui.widget.JsTabLayout
import org.autojs.autojs.core.ui.widget.JsTextClock
import org.autojs.autojs.core.ui.widget.JsTextSwitcher
import org.autojs.autojs.core.ui.widget.JsTextView
import org.autojs.autojs.core.ui.widget.JsTimePicker
import org.autojs.autojs.core.ui.widget.JsToggleButton
import org.autojs.autojs.core.ui.widget.JsToolbar
import org.autojs.autojs.core.ui.widget.JsVideoView
import org.autojs.autojs.core.ui.widget.JsViewFlipper
import org.autojs.autojs.core.ui.widget.JsViewPager
import org.autojs.autojs.core.ui.widget.JsViewSwitcher
import org.autojs.autojs.core.ui.widget.JsWebView
import org.autojs.autojs.core.ui.xml.SecureXml
import org.autojs.autojs.core.ui.xml.XmlConverter
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs6.R
import org.w3c.dom.Node
import java.io.File

open class DynamicLayoutInflater {

    private var mViewAttrSetters: MutableMap<String, ViewInflater<*>> = HashMap()
    private var mViewCreators: MutableMap<String, ViewCreator<*>> = HashMap()
    private var scriptRuntime: ScriptRuntime? = null

    internal var privateContext: Context? = null

    @get:ScriptInterface
    @set:ScriptInterface
    var layoutInflaterDelegate: LayoutInflaterDelegate = LayoutInflaterDelegate.NO_OP

    var context: Context
        get() = privateContext ?: GlobalAppContext.get()
        set(value) {
            privateContext = value
        }

    val resourceParser: ResourceParser
    var inflateFlags = 0

    /** Sets the attributes no handler takes through the view's public API (host roadmap A2). */
    private val attributeFallback by lazy { AndroidAttributeFallback(AndroidAttributeTable.get(context), resourceParser) }

    /** `class|attribute` keys already warned about in this runtime's console (host roadmap A4, decision D7). */
    private val warnedAttributes = HashSet<String>()

    constructor(resourceParser: ResourceParser, scriptRuntime: ScriptRuntime) {
        this.resourceParser = resourceParser
        this.scriptRuntime = scriptRuntime
        registerViewAttrSetters(scriptRuntime)
    }

    constructor(inflater: DynamicLayoutInflater) {
        resourceParser = inflater.resourceParser
        scriptRuntime = inflater.scriptRuntime
        context = inflater.context
        mViewAttrSetters = HashMap(inflater.mViewAttrSetters)
        mViewCreators = HashMap(inflater.mViewCreators)
    }

    protected fun registerViewAttrSetters(scriptRuntime: ScriptRuntime) {
        registerNativeViewAttrSetter(SurfaceView::class.java, SurfaceViewInflater<SurfaceView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(TextureView::class.java, TextureViewInflater<TextureView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(WebView::class.java, WebViewInflater<WebView>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(AdapterView::class.java, AdapterViewInflater<AdapterView<*>>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AbsSpinner::class.java, AbsSpinnerInflater<AbsSpinner>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(Spinner::class.java, SpinnerInflater<Spinner>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AppCompatSpinner::class.java, AppCompatSpinnerInflater<AppCompatSpinner>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(ActionMenuView::class.java, ActionMenuViewInflater<ActionMenuView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(FrameLayout::class.java, FrameLayoutInflater<FrameLayout>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(CalendarView::class.java, CalendarViewInflater<CalendarView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(DatePicker::class.java, DatePickerInflater<DatePicker>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(HorizontalScrollView::class.java, HorizontalScrollViewInflater<HorizontalScrollView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ScrollView::class.java, ScrollViewInflater<ScrollView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(TimePicker::class.java, TimePickerInflater<TimePicker>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ViewAnimator::class.java, ViewAnimatorInflater<ViewAnimator>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ViewFlipper::class.java, ViewFlipperInflater<ViewFlipper>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ViewSwitcher::class.java, ViewSwitcherInflater<ViewSwitcher>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ImageSwitcher::class.java, ImageSwitcherInflater<ImageSwitcher>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(TextSwitcher::class.java, TextSwitcherInflater<TextSwitcher>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(LinearLayout::class.java, LinearLayoutInflater<LinearLayout>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(NumberPicker::class.java, NumberPickerInflater<NumberPicker>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RadioGroup::class.java, RadioGroupInflater<RadioGroup>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(SearchView::class.java, SearchViewInflater<SearchView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RelativeLayout::class.java, RelativeLayoutInflater<RelativeLayout>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(ImageView::class.java, ImageViewInflater<ImageView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ImageButton::class.java, ImageButtonInflater<ImageButton>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(QuickContactBadge::class.java, QuickContactBadgeInflater<QuickContactBadge>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(ProgressBar::class.java, ProgressBarInflater<ProgressBar>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AbsSeekBar::class.java, AbsSeekBarInflater<AbsSeekBar>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RatingBar::class.java, RatingBarInflater<RatingBar>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(SeekBar::class.java, SeekBarInflater<SeekBar>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(TextView::class.java, TextViewInflater<TextView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AppCompatTextView::class.java, AppCompatTextViewInflater<AppCompatTextView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(Button::class.java, ButtonInflater<Button>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(CompoundButton::class.java, CompoundButtonInflater<CompoundButton>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(CheckBox::class.java, CheckBoxInflater<CheckBox>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AppCompatCheckBox::class.java, AppCompatCheckBoxInflater<AppCompatCheckBox>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RadioButton::class.java, RadioButtonInflater(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ToggleButton::class.java, ToggleButtonInflater<ToggleButton>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(CheckedTextView::class.java, CheckedTextViewInflater<CheckedTextView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(Chronometer::class.java, ChronometerInflater<Chronometer>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(EditText::class.java, EditTextInflater<EditText>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AutoCompleteTextView::class.java, AutoCompleteTextViewInflater<AutoCompleteTextView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(TextClock::class.java, TextClockInflater<TextClock>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(VideoView::class.java, VideoViewInflater<VideoView>(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(AppCompatToolbar::class.java, ToolbarInflater<AppCompatToolbar>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(CardView::class.java, CardViewInflater<CardView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(DrawerLayout::class.java, DrawerLayoutInflater<DrawerLayout>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RecyclerView::class.java, RecyclerViewInflater<RecyclerView>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(ViewPager::class.java, ViewPagerInflater<ViewPager>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(AppBarLayout::class.java, AppBarLayoutInflater<AppBarLayout>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(FloatingActionButton::class.java, FloatingActionButtonInflater<FloatingActionButton>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(TabLayout::class.java, TabLayoutInflater<TabLayout>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(SwitchCompat::class.java, SwitchCompatInflater<SwitchCompat>(scriptRuntime, resourceParser))
        registerNativeViewAttrSetter(RoundedImageView::class.java, RoundedImageViewInflater<RoundedImageView>(scriptRuntime, resourceParser))

        registerViewAttrSetter(JsActionMenuView::class.java, JsActionMenuViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsAppBarLayout::class.java, JsAppBarLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsButton::class.java, JsButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCanvasView::class.java, JsCanvasViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCardView::class.java, JsCardViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCalendarView::class.java, JsCalendarViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCheckBox::class.java, JsCheckBoxInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsCheckedTextView::class.java, JsCheckedTextViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsChronometer::class.java, JsChronometerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsConsoleView::class.java, JsConsoleViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsGlobalConsoleView::class.java, JsGlobalConsoleViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsDatePicker::class.java, JsDatePickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsDrawerLayout::class.java, JsDrawerLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsEditText::class.java, JsEditTextInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsFloatingActionButton::class.java, JsFloatingActionButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsFrameLayout::class.java, JsFrameLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(org.autojs.autojs.core.ui.widget.ComposeContainerView::class.java, ComposeContainerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsGridView::class.java, JsGridViewInflater<JsGridView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageButton::class.java, JsImageButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageView::class.java, JsImageViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsImageSwitcher::class.java, JsImageSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsLinearLayout::class.java, JsLinearLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsListView::class.java, JsListViewInflater<JsListView>(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsNumberPicker::class.java, JsNumberPickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsProgressBar::class.java, JsProgressBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsQuickContactBadge::class.java, JsQuickContactBadgeInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRadioButton::class.java, JsRadioButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRadioGroup::class.java, JsRadioGroupInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRatingBar::class.java, JsRatingBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsRelativeLayout::class.java, JsRelativeLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsScrollView::class.java, JsScrollViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSearchView::class.java, JsSearchViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSeekBar::class.java, JsSeekBarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSpinner::class.java, JsSpinnerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsSwitch::class.java, JsSwitchInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTabLayout::class.java, JsTabLayoutInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTextClock::class.java, JsTextClockInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTextSwitcher::class.java, JsTextSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsTimePicker::class.java, JsTimePickerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsToggleButton::class.java, JsToggleButtonInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsToolbar::class.java, JsToolbarInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsVideoView::class.java, JsVideoViewInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewFlipper::class.java, JsViewFlipperInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewPager::class.java, JsViewPagerInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsViewSwitcher::class.java, JsViewSwitcherInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(JsWebView::class.java, JsWebViewInflater(scriptRuntime, resourceParser))

        registerViewAttrSetter(JsTextView::class.java, JsTextViewInflater(scriptRuntime, resourceParser))

        registerNativeViewAttrSetter(Space::class.java, SpaceInflater(scriptRuntime, resourceParser))
        registerViewAttrSetter(ViewGroup::class.java, ViewGroupInflater<ViewGroup>(scriptRuntime, resourceParser))
        registerViewAttrSetter(View::class.java, BaseViewInflater<View>(scriptRuntime, resourceParser))
    }

    fun registerViewAttrSetter(clazz: Class<*>, inflater: ViewInflater<*>) {
        registerViewAttrSetter(clazz.name, inflater)
    }

    private fun registerNativeViewAttrSetter(clazz: Class<*>, inflater: ViewInflater<*>) {
        mViewAttrSetters[clazz.name] = inflater
    }

    fun registerViewAttrSetter(className: String, inflater: ViewInflater<*>) {
        mViewAttrSetters[className] = inflater
        inflater.getCreator()?.let { mViewCreators[className] = it }
    }

    @JvmOverloads
    fun inflate(xml: String, parent: ViewGroup? = null, attachToParent: Boolean = parent != null): View {
        configureAndroidResources()
        val context = newInflateContext()
        return inflate(context, xml, parent, attachToParent)
    }

    @JvmOverloads
    fun inflateFile(path: String, parent: ViewGroup? = null, attachToParent: Boolean = parent != null): View {
        val sourceFile = File(path).canonicalFile
        require(sourceFile.isFile) { "Android layout file does not exist: $sourceFile" }
        configureAndroidResources(sourceFile)
        return inflate(newInflateContext(), SecureXml.read(sourceFile), parent, attachToParent)
    }

    fun inflate(context: InflateContext, xml: String, parent: ViewGroup?, attachToParent: Boolean): View {
        layoutInflaterDelegate.beforeInflation(context, xml, parent)?.let { return it }
        val niceXml = convertXml(context, xml)
        return layoutInflaterDelegate.afterInflation(context, doInflation(context, niceXml, parent, attachToParent), niceXml, parent)
    }

    @JvmOverloads
    fun newInflateContext(parent: InflateContext? = null) = InflateContext().also { child ->
        parent?.get(ANDROID_LAYOUT_STACK)?.let { child.put(ANDROID_LAYOUT_STACK, it) }
        parent?.get(ANDROID_VIEW_DEPTH)?.let { child.put(ANDROID_VIEW_DEPTH, it) }
        parent?.get(ANDROID_BASE_CONTEXT)?.let { child.put(ANDROID_BASE_CONTEXT, it) }
    }

    protected fun doInflation(context: InflateContext, xml: String, parent: ViewGroup?, attachToParent: Boolean): View {
        return try {
            val document = resourceParser.androidResources.repository.parseDocument(xml)
            inflate(context, document.documentElement, parent, attachToParent)
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    protected fun convertXml(context: InflateContext?, xml: String?): String {
        // A layout the delegate keeps as it is (ui.useAndroidLayout, or namespaced attributes) is native Android XML (A4).
        layoutInflaterDelegate.beforeConvertXml(context, xml)?.let { kept ->
            context?.put(ANDROID_XML, true)
            return kept
        }
        return try {
            layoutInflaterDelegate.afterConvertXml(context, XmlConverter.convertToAndroidLayout(xml))
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    fun inflate(context: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
        val depth = (context.get(ANDROID_VIEW_DEPTH) as? Int) ?: 0
        check(depth < MAX_VIEW_DEPTH) {
            "Android layout View depth exceeds $MAX_VIEW_DEPTH at <${node.nodeName}>"
        }
        context.put(ANDROID_VIEW_DEPTH, depth + 1)
        return try {
            val view = doInflation(context, node, parent, attachToParent)
            if (node.nodeName != "merge") {
                (view as? ShouldCallOnFinishInflate)?.onFinishDynamicInflate()
            }
            view
        } finally {
            if (depth == 0) {
                context.remove(ANDROID_VIEW_DEPTH)
            } else {
                context.put(ANDROID_VIEW_DEPTH, depth)
            }
        }
    }

    protected fun doInflation(context: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
        if (node.nodeName == "merge") {
            require(parent != null && attachToParent) {
                "<merge> can be inflated only with a parent and attachToParent=true"
            }
            inflateChildren(context, node, parent)
            return parent
        }
        require(node.nodeName !in NON_VIEW_ROOT_ELEMENTS) {
            "Android layout element <${node.nodeName}> cannot be used as a root View"
        }
        var view = layoutInflaterDelegate.beforeInflateView(context, node, parent, attachToParent)
        if (view != null) {
            return view
        }
        val attrs = getAttributesMap(node, inflationBaseContext(context, parent))
        view = doCreateView(context, node, node.nodeName, parent, attrs)
        if (view is EmptyView) {
            return view
        }
        if (parent != null) {
            parent.addView(view) // have to add to parent to generate layout params
            if (!attachToParent) {
                parent.removeView(view)
            }
        }
        val inflater = applyAttributes(context, view, attrs, parent)
        if (node.hasChildNodes()) {
            if (view is ViewGroup) {
                inflateChildren(context, inflater, node, view)
                if (inflater is ViewGroupInflater<*>) {
                    @Suppress("UNCHECKED_CAST")
                    applyPendingAttributesOfChildren(context, inflater as ViewGroupInflater<ViewGroup>, view)
                }
            } else {
                inflateMetadataChildren(node, view)
            }
        }
        return layoutInflaterDelegate.afterInflateView(context, view, node, parent, attachToParent)
    }

    protected fun applyPendingAttributesOfChildren(context: InflateContext, inflater: ViewGroupInflater<ViewGroup>, view: ViewGroup?) {
        if (!layoutInflaterDelegate.beforeApplyPendingAttributesOfChildren(context, inflater, view)) {
            view?.let { inflater.applyPendingAttributesOfChildren(it) }
            layoutInflaterDelegate.afterApplyPendingAttributesOfChildren(context, inflater, view)
        }
    }

    fun applyAttributes(context: InflateContext, view: View, attrs: HashMap<String, String>, parent: ViewGroup?): ViewInflater<View> {
        val inflater = getViewInflater(view)
        if (!layoutInflaterDelegate.beforeApplyAttributes(context, view, inflater, attrs, parent)) {
            applyAttributes(context, view, inflater, attrs, parent)
            layoutInflaterDelegate.afterApplyAttributes(context, view, inflater, attrs, parent)
        }
        return inflater
    }

    fun getViewInflater(view: View): ViewInflater<View> {
        var setter = mViewAttrSetters[view.javaClass.name]
        var c: Class<*> = view.javaClass
        while (setter == null && c != View::class.java) {
            c = c.superclass as Class<*>
            setter = mViewAttrSetters[c.name]
        }
        @Suppress("UNCHECKED_CAST")
        return setter as ViewInflater<View>
    }

    protected fun inflateChildren(context: InflateContext, inflater: ViewInflater<View>, node: Node, parent: ViewGroup?) {
        if (layoutInflaterDelegate.beforeInflateChildren(context, inflater, node, parent)) {
            return
        }
        if (inflater.inflateChildren(this, node, parent)) {
            return
        }
        inflateChildren(context, node, parent)
        layoutInflaterDelegate.afterInflateChildren(context, inflater, node, parent)
    }

    fun inflateChildren(context: InflateContext, node: Node, parent: ViewGroup?) {
        val nodeList = node.childNodes
        for (i in 0 until nodeList.length) {
            val currentNode = nodeList.item(i)
            if (currentNode.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            when (currentNode.nodeName) {
                "include" -> inflateIncludedLayout(context, currentNode, parent)
                "merge" -> throw InflateException("<merge> is allowed only as a layout resource root")
                "requestFocus" -> parent?.requestFocus()
                "tag" -> applyTag(currentNode, parent)
                else -> inflate(context, currentNode, parent, true)
            }
        }
    }

    protected fun doCreateView(context: InflateContext, node: Node?, viewName: String, parent: ViewGroup?, attrs: HashMap<String, String>): View {
        val view = layoutInflaterDelegate.beforeCreateView(context, node, viewName, parent)
        return view ?: layoutInflaterDelegate.afterCreateView(
            context,
            createViewForName(viewName, attrs, parent, inflationBaseContext(context, parent)),
            node,
            viewName,
            parent,
        )
    }

    fun createViewForName(name: String, attrs: HashMap<String, String>, parent: ViewGroup?): View =
        createViewForName(name, attrs, parent, null)

    private fun createViewForName(
        name: String,
        attrs: HashMap<String, String>,
        parent: ViewGroup?,
        contextOverride: Context?,
    ): View {
        var niceName = if (name == "view") attrs["class"] ?: name else name
        val baseContext = applyTheme(
            contextOverride ?: parent?.context ?: context,
            attrs["android:theme"] ?: attrs["theme"],
        )
        val androidWidgetPrefixBlacklist = listOf(
            "menu", "item", "shape", "paths", "set", "selector", "merge", "view",
        )
        return try {
            if (!niceName.contains(".") && !androidWidgetPrefixBlacklist.contains(niceName)) {
                niceName = ClassNameFilter.extendedClassNameMap[niceName]
                    ?: ANDROID_VIEW_PREFIXES
                        .asSequence()
                        .map { "$it.$niceName" }
                        .firstOrNull { runCatching { Class.forName(it) }.isSuccess }
                    ?: "android.widget.$niceName"
            }
            val creator = mViewCreators[niceName]
            if (creator != null) {
                return creator.create(baseContext, attrs, parent)
            }
            val clazz: Class<*> = runCatching { Class.forName(niceName) }.getOrElse {
                throw WrappedRuntimeException(context.getString(R.string.error_unknown_element_name, name))
            }
            createReflectedView(clazz, attrs, baseContext)
        } catch (e: Exception) {
            throw InflateException(e)
        }
    }

    private fun createReflectedView(clazz: Class<*>, attrs: Map<String, String>, baseContext: Context): View {
        val styleRes = attrs["style"]?.let { Res.parseStyle(baseContext, it) } ?: 0
        val creationContext = styleRes
            .takeIf { it != 0 }
            ?.let { ContextThemeWrapper(baseContext, it) }
            ?: baseContext
        val spinnerMode = attrs["android:spinnerMode"]?.let(::parseSpinnerMode)
        if (clazz == Spinner::class.java) {
            if (styleRes != 0) {
                return Spinner(
                    baseContext,
                    null,
                    0,
                    styleRes,
                    spinnerMode ?: SPINNER_MODE_THEME,
                )
            }
            return spinnerMode?.let {
                Spinner(creationContext, null, android.R.attr.spinnerStyle, it)
            } ?: Spinner(creationContext)
        }
        if (clazz == AppCompatSpinner::class.java) {
            return spinnerMode?.let {
                AppCompatSpinner(creationContext, null, androidx.appcompat.R.attr.spinnerStyle, it)
            } ?: AppCompatSpinner(creationContext)
        }
        if (styleRes != 0 && !Spinner::class.java.isAssignableFrom(clazz)) {
            instantiateView(
                clazz,
                arrayOf(
                    Context::class.java,
                    AttributeSet::class.java,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                ),
                arrayOf(baseContext, null, 0, styleRes),
            )?.let { return it }
        }
        instantiateView(clazz, arrayOf(Context::class.java), arrayOf(creationContext))
            ?.let { return it }
        instantiateView(
            clazz,
            arrayOf(Context::class.java, AttributeSet::class.java),
            arrayOf(creationContext, null),
        )?.let { return it }
        throw NoSuchMethodException("${clazz.name} has no supported Android View constructor")
    }

    private fun parseSpinnerMode(mode: String) = when (mode) {
        "dialog" -> Spinner.MODE_DIALOG
        "dropdown" -> Spinner.MODE_DROPDOWN
        else -> throw InflateException("Unknown spinnerMode: $mode")
    }

    private fun applyTheme(baseContext: Context, theme: String?): Context {
        theme ?: return baseContext
        resourceParser.androidResources.repository.resolveStyleDetails(theme)?.let { projectTheme ->
            val compiledContext = projectTheme.compiledParent
                ?.let { applyCompiledTheme(baseContext, it) }
                ?: baseContext
            val attributes = linkedMapOf<String, String>()
            attributes.putAll(baseContext.findScriptThemeAttributes())
            attributes.putAll(projectTheme.attributes)
            return ScriptThemeContext(compiledContext, attributes)
        }
        return applyCompiledTheme(baseContext, theme)
    }

    private fun applyCompiledTheme(baseContext: Context, theme: String): Context {
        val themeRes = Res.parseStyle(baseContext, theme)
        return themeRes.takeIf { it != 0 }
            ?.let { ContextThemeWrapper(baseContext, it) }
            ?: baseContext
    }

    private fun instantiateView(clazz: Class<*>, parameterTypes: Array<Class<*>>, arguments: Array<Any?>): View? {
        val constructor = runCatching { clazz.getConstructor(*parameterTypes) }.getOrNull() ?: return null
        return constructor.newInstance(*arguments) as View
    }

    fun getAttributesMap(currentNode: Node): HashMap<String, String> =
        getAttributesMap(currentNode, context)

    private fun getAttributesMap(currentNode: Node, baseContext: Context): HashMap<String, String> {
        val attributes = getRawAttributesMap(currentNode)
        val theme = (
            attributes["android:theme"]
                ?: attributes["theme"]
                ?: attributes["style"]
                    ?.let(resourceParser.androidResources.repository::resolveStyleDetails)
                    ?.attributes
                    ?.let { it["android:theme"] ?: it["theme"] }
            )
            ?.let { resourceParser.androidResources.resolveScalar(baseContext, it) }
        val resolutionContext = applyTheme(baseContext, theme)
        return resourceParser.androidResources.resolveAttributes(resolutionContext, attributes)
    }

    private fun inflateIncludedLayout(context: InflateContext, includeNode: Node, parent: ViewGroup?) {
        requireNotNull(parent) { "<include> requires a parent ViewGroup" }
        val includeAttributes = getRawAttributesMap(includeNode)
        val layoutReference = includeAttributes["layout"]
            ?: includeAttributes["android:layout"]
            ?: throw InflateException("<include> requires a layout attribute")
        val baseContext = inflationBaseContext(context, parent)
        val theme = (includeAttributes["android:theme"] ?: includeAttributes["theme"])
            ?.let { resourceParser.androidResources.resolveScalar(baseContext, it) }
        val includeContext = applyTheme(baseContext, theme)
        val resolvedLayoutReference = resourceParser.androidResources.resolveScalar(
            includeContext,
            layoutReference,
        )
        val stack = getLayoutStack(context)
        check(stack.size < MAX_RESOURCE_DEPTH) {
            "Android layout include depth exceeds $MAX_RESOURCE_DEPTH: ${stack.joinToString(" -> ")}"
        }
        check(stack.add(resolvedLayoutReference)) {
            "Circular Android layout include: ${(stack.toList() + resolvedLayoutReference).joinToString(" -> ")}"
        }
        try {
            val xml = resourceParser.androidResources.readLayout(resolvedLayoutReference)
                ?: throw InflateException("Android layout resource not found: $resolvedLayoutReference")
            val root = resourceParser.androidResources.repository.parseDocument(xml).documentElement
                ?: throw InflateException("Android layout resource has no root: $resolvedLayoutReference")
            if (root.nodeName == "merge") {
                withInflationBaseContext(context, parent, includeContext) {
                    inflateChildren(context, root, parent)
                }
                return
            }
            applyIncludeOverrides(root, includeNode)
            inflate(context, root, parent, true)
        } finally {
            stack.remove(resolvedLayoutReference)
        }
    }

    private fun applyIncludeOverrides(targetRoot: Node, includeNode: Node) {
        val target = targetRoot.attributes ?: return
        val source = includeNode.attributes ?: return
        val includeAttributes = getRawAttributesMap(includeNode)
        val hasCompleteLayoutSize = includeAttributes.keys.any(::isLayoutWidthAttribute) &&
            includeAttributes.keys.any(::isLayoutHeightAttribute)
        for (index in 0 until source.length) {
            val attribute = source.item(index)
            val name = attribute.nodeName
            val isViewOverride = name == "id" ||
                name == "android:id" ||
                name == "visibility" ||
                name == "android:visibility" ||
                name == "theme" ||
                name == "android:theme"
            val isLayoutOverride = hasCompleteLayoutSize &&
                (name.startsWith("layout_") || name.startsWith("android:layout_"))
            if (!isViewOverride && !isLayoutOverride) {
                continue
            }
            val existing = target.getNamedItemNS(attribute.namespaceURI, attribute.localName)
                ?: target.getNamedItem(attribute.nodeName)
            if (existing != null) {
                existing.nodeValue = attribute.nodeValue
            } else {
                target.setNamedItem(targetRoot.ownerDocument.importNode(attribute, true))
            }
        }
    }

    private fun inflateMetadataChildren(node: Node, view: View) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            when (child.nodeName) {
                "requestFocus" -> view.requestFocus()
                "tag" -> applyTag(child, view)
                else -> throw InflateException("<${node.nodeName}> cannot contain <${child.nodeName}>")
            }
        }
    }

    private fun applyTag(node: Node, view: View?) {
        view ?: return
        val attributes = getRawAttributesMap(node)
        val value = attributes["android:value"] ?: attributes["value"] ?: return
        val key = attributes["android:id"] ?: attributes["id"]
        if (key == null) {
            view.tag = value
        } else {
            val id = org.autojs.autojs.core.ui.inflater.util.Ids.parse(key)
            require(id ushr 24 >= 2) {
                "<tag> requires an app-specific id instead of $key"
            }
            view.setTag(id, value)
        }
    }

    private fun isLayoutWidthAttribute(name: String) =
        name == "layout_width" || name == "android:layout_width"

    private fun isLayoutHeightAttribute(name: String) =
        name == "layout_height" || name == "android:layout_height"

    @Suppress("UNCHECKED_CAST")
    private fun getLayoutStack(context: InflateContext): LinkedHashSet<String> {
        return (context.get(ANDROID_LAYOUT_STACK) as? LinkedHashSet<String>)
            ?: LinkedHashSet<String>().also { context.put(ANDROID_LAYOUT_STACK, it) }
    }

    private fun getRawAttributesMap(currentNode: Node): HashMap<String, String> {
        val attributeMap = currentNode.attributes
        // Kept in document order (host roadmap D5): of two attributes that set the same thing, the later one wins.
        val attributes = LinkedHashMap<String, String>(attributeMap?.length ?: 0)
        if (attributeMap != null) {
            for (index in 0 until attributeMap.length) {
                val attribute = attributeMap.item(index)
                attributes[attribute.nodeName] = attribute.nodeValue
            }
        }
        return attributes
    }

    private fun inflationBaseContext(context: InflateContext, parent: ViewGroup?): Context {
        (context.get(ANDROID_BASE_CONTEXT) as? InflationBaseContext)
            ?.takeIf { it.parent === parent }
            ?.context
            ?.let { return it }
        val baseContext = this.context
        val activity = baseContext.findActivity()
        // DecorView can hold a DecorContext whose wrapper chain does not retain the Activity.
        // Keep the Activity for root inflation so children can launch file choosers and dialogs.
        // zh-CN: DecorView 的 DecorContext 包装链可能不包含 Activity. 根布局保留 Activity,
        // 以便子视图能启动文件选择器和对话框; 普通子布局仍继承父视图的主题.
        return if (activity != null && parent === activity.window.decorView) baseContext
        else parent?.context ?: baseContext
    }

    private inline fun withInflationBaseContext(
        context: InflateContext,
        parent: ViewGroup,
        baseContext: Context,
        action: () -> Unit,
    ) {
        val previous = context.get(ANDROID_BASE_CONTEXT)
        context.put(ANDROID_BASE_CONTEXT, InflationBaseContext(parent, baseContext))
        try {
            action()
        } finally {
            if (previous == null) {
                context.remove(ANDROID_BASE_CONTEXT)
            } else {
                context.put(ANDROID_BASE_CONTEXT, previous)
            }
        }
    }

    private data class InflationBaseContext(
        val parent: ViewGroup,
        val context: Context,
    )

    private fun configureAndroidResources(sourceFile: File? = null) {
        val workingDirectory = runCatching { scriptRuntime?.files?.cwd()?.let(::File) }.getOrNull()
        resourceParser.androidResources.configure(sourceFile, workingDirectory)
    }

    private fun applyAttributes(context: InflateContext, view: View, setter: ViewInflater<View>?, attrs: Map<String, String>, parent: ViewGroup?) {
        if (setter != null) {
            for ((key, value) in attrs) {
                val attr = key.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (attr.size) {
                    1 -> applyAttribute(context, setter, view, null, attr[0], value, parent)
                    2 -> applyAttribute(context, setter, view, attr[0], attr[1], value, parent)
                    else -> throw InflateException("Illegal attr name: $key")
                }
            }
            setter.applyPendingAttributes(view, parent)
        } else {
            Log.e(LOG_TAG, "cannot set attributes for view: " + view.javaClass)
        }
    }

    protected fun applyAttribute(context: InflateContext, inflater: ViewInflater<View>, view: View, ns: String?, attrName: String, value: String, parent: ViewGroup?) {
        if (layoutInflaterDelegate.beforeApplyAttribute(context, inflater, view, ns, attrName, value, parent)) {
            return
        }
        val isDynamic = isDynamicValue(value)
        if (isDynamic && isIgnoresDynamicFlags || !isDynamic && isJustDynamicFlags) {
            return
        }
        applyAttributeValue(context, view, inflater, ns, attrName, value, parent)
        layoutInflaterDelegate.afterApplyAttribute(context, inflater, view, ns, attrName, value, parent)
    }

    /**
     * Sets one attribute (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A2): the view's handlers first
     * ([ViewInflater.setAttr]); an attribute none of them takes, or one they declare unsupported, goes to the
     * metadata fallback ([AndroidAttributeFallback]), which uses the view's public API. What happens to an attribute
     * that stays unset (A4, decision D7): in native Android XML ([context] marked by [convertXml]) it is skipped, the
     * script's console warns once per class and attribute, and a preview host hears of it
     * ([UiContentHost.onAttributeIgnored]); in AutoJs6 XML an unknown attribute stays silently ignored and one the
     * handlers declare unsupported still fails the layout.
     * zh-CN: 设置一个属性: 先由视图的处理器处理; 没有处理器接受或处理器声明不支持的属性交给元数据兜底, 以视图的公开 API 设置. 仍未设置的属性:
     * 原生 Android XML 中跳过, 控制台按类与属性警告一次, 并告知预览承载; AutoJs6 XML 中未知属性照旧忽略, 声明不支持的属性照旧使布局失败.
     */
    fun applyAttributeValue(context: InflateContext, view: View, inflater: ViewInflater<View>, ns: String?, attrName: String, value: String, parent: ViewGroup?): AttributeOutcome {
        val outcome = try {
            if (inflater.setAttr(view, ns, attrName, value, parent)) return AttributeOutcome.APPLIED
            if (ns != null && ns != "android" && ns != "app") AttributeOutcome.IGNORED_NAMESPACE else attributeFallback.apply(view, ns, attrName, value, parent)
        } catch (e: UnsupportedAttributeException) {
            val fallback = attributeFallback.apply(view, ns, attrName, value, parent)
            if (fallback == AttributeOutcome.APPLIED) return fallback
            if (context.get(ANDROID_XML) != true) throw e
            AttributeOutcome.UNSUPPORTED
        }
        if (outcome == AttributeOutcome.UNSUPPORTED || outcome == AttributeOutcome.UNKNOWN) {
            if (context.get(ANDROID_XML) == true) reportIgnoredAttribute(view, if (ns != null) "$ns:$attrName" else attrName, outcome)
        }
        return outcome
    }

    private fun reportIgnoredAttribute(view: View, attrName: String, outcome: AttributeOutcome) {
        val runtime = scriptRuntime ?: return
        val message = this.context.getString(
            if (outcome == AttributeOutcome.UNSUPPORTED) R.string.ui_android_attribute_unsupported else R.string.ui_android_attribute_unknown,
            attrName,
            view.javaClass.simpleName,
        )
        if (warnedAttributes.add(view.javaClass.name + "|" + attrName)) {
            runCatching { runtime.console.warn(message) }
        }
        UiContentHost.attached(runtime)?.onAttributeIgnored(attrName, outcome, message)
    }

    private val isJustDynamicFlags: Boolean
        get() = inflateFlags == FLAG_JUST_DYNAMIC_ATTRS

    private val isIgnoresDynamicFlags: Boolean
        get() = inflateFlags == FLAG_IGNORES_DYNAMIC_ATTRS

    fun isDynamicValue(value: String): Boolean {
        val i = value.indexOf("{{")
        return i >= 0 && value.indexOf("}}", i + 1) >= 0
    }

    companion object {
        const val FLAG_DEFAULT = 0
        const val FLAG_IGNORES_DYNAMIC_ATTRS = 1
        const val FLAG_JUST_DYNAMIC_ATTRS = 2
        private const val LOG_TAG = "DynamicLayoutInflater"
        private const val ANDROID_LAYOUT_STACK = "android.layout.stack"
        /** Set on an inflate context whose layout is native Android XML (kept, not converted from AutoJs6 XML). */
        private const val ANDROID_XML = "android.xml"
        private const val ANDROID_VIEW_DEPTH = "android.view.depth"
        private const val ANDROID_BASE_CONTEXT = "android.base.context"
        private const val MAX_RESOURCE_DEPTH = 32
        private const val MAX_VIEW_DEPTH = 128
        private const val SPINNER_MODE_THEME = -1
        private val NON_VIEW_ROOT_ELEMENTS = setOf("include", "requestFocus", "tag")
        private val ANDROID_VIEW_PREFIXES = listOf("android.widget", "android.view", "android.webkit")
    }
}
