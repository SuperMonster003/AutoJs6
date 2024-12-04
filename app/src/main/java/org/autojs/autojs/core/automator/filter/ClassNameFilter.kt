package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Nov 19, 2022.
 */
object ClassNameFilter {

    private val CLASS_NAME_GETTER = object : KeyGetter {

        override fun getKey(nodeInfo: UiObject) = nodeInfo.className?.toString()

        override fun toString() = "className"

    }

    val extendedClassNameMap = mapOf(
        "CardView" to "androidx.cardview.widget.CardView",
        "ConstraintLayout" to "androidx.constraintlayout.widget.ConstraintLayout",
        "CoordinatorLayout" to "androidx.coordinatorlayout.widget.CoordinatorLayout",
        "NestedScrollView" to "androidx.core.widget.NestedScrollView",
        "DrawerLayout" to "androidx.drawerlayout.widget.DrawerLayout",
        "FragmentContainerView" to "androidx.fragment.app.FragmentContainerView",
        "RecyclerView" to "androidx.recyclerview.widget.RecyclerView",
        "SwipeRefreshLayout" to "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
        "ViewPager" to "androidx.viewpager.widget.ViewPager",
        "ViewPager2" to "androidx.viewpager2.widget.ViewPager2",
        "AppBarLayout" to "com.google.android.material.appbar.AppBarLayout",
        "MaterialToolbar" to "com.google.android.material.appbar.MaterialToolbar",
        "BottomNavigationView" to "com.google.android.material.bottomnavigation.BottomNavigationView",
        "BottomSheetDialog" to "com.google.android.material.bottomsheet.BottomSheetDialog",
        "MaterialButton" to "com.google.android.material.button.MaterialButton",
        "Chip" to "com.google.android.material.chip.Chip",
        "MaterialCalendarGridView" to "com.google.android.material.datepicker.MaterialCalendarGridView",
        "FloatingActionButton" to "com.google.android.material.floatingactionbutton.FloatingActionButton",
        "BaselineLayout" to "com.google.android.material.internal.BaselineLayout",
        "CheckableImageButton" to "com.google.android.material.internal.CheckableImageButton",
        "ClippableRoundedCornerLayout" to "com.google.android.material.internal.ClippableRoundedCornerLayout",
        "NavigationMenuItemView" to "com.google.android.material.internal.NavigationMenuItemView",
        "NavigationMenuView" to "com.google.android.material.internal.NavigationMenuView",
        "TouchObserverFrameLayout" to "com.google.android.material.internal.TouchObserverFrameLayout",
        "VisibilityAwareImageButton" to "com.google.android.material.internal.VisibilityAwareImageButton",
        "NavigationView" to "com.google.android.material.navigation.NavigationView",
        "Snackbar" to "com.google.android.material.snackbar.Snackbar",
        "TabLayout" to "com.google.android.material.tabs.TabLayout",
        "TextInputEditText" to "com.google.android.material.textfield.TextInputEditText",
        "TextInputLayout" to "com.google.android.material.textfield.TextInputLayout",
        "ChipTextInputComboView" to "com.google.android.material.timepicker.ChipTextInputComboView",
        "ClockFaceView" to "com.google.android.material.timepicker.ClockFaceView",
        "ClockHandView" to "com.google.android.material.timepicker.ClockHandView",
        "TimePickerView" to "com.google.android.material.timepicker.TimePickerView",
    )

    @Suppress("CovariantEquals")
    fun equals(text: String) = when (text.contains(".")) {
        true -> text
        else -> extendedClassNameMap[text] ?: "android.widget.$text"
    }.let { StringEqualsFilter(it, CLASS_NAME_GETTER) }

    fun contains(str: String) = StringContainsFilter(str, CLASS_NAME_GETTER)

    fun startsWith(prefix: String) = when (prefix.contains(".")) {
        true -> prefix
        else -> extendedClassNameMap[prefix] ?: "android.widget.$prefix"
    }.let { StringStartsWithFilter(it, CLASS_NAME_GETTER) }

    fun endsWith(suffix: String) = StringEndsWithFilter(suffix, CLASS_NAME_GETTER)

    fun matches(regex: String) = StringMatchesFilter(regex, CLASS_NAME_GETTER)

    fun match(regex: String) = StringMatchFilter(regex, CLASS_NAME_GETTER)

}
