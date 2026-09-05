package org.autojs.autojs.core.automator.filter

/**
 * The argument rule of `className(name)` and `classNameStartsWith(prefix)`: a bare widget name is
 * expanded to its framework or androidx / material class. The other className builders are plain
 * [StringPropertyFilter]s over [StringProperty.CLASS_NAME].
 * zh-CN: `className(name)` 与 `classNameStartsWith(prefix)` 的参数规则: 不带包名的控件名扩展为框架或 androidx / material
 * 的类名. 其余 className builder 是 [StringProperty.CLASS_NAME] 上的普通 [StringPropertyFilter].
 *
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Sep 6, 2026.
 */
object ClassNameFilter {

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

    /** `Button` -> `android.widget.Button`, `RecyclerView` -> its androidx name; a dotted name is taken as is. zh-CN: `Button` -> `android.widget.Button`, `RecyclerView` -> 其 androidx 全名; 含点的名称原样使用. */
    fun expand(name: String): String = when (name.contains(".")) {
        true -> name
        else -> extendedClassNameMap[name] ?: "android.widget.$name"
    }

    @Suppress("CovariantEquals")
    fun equals(text: String) = StringPropertyFilter(StringProperty.CLASS_NAME, StringMatcher.equals(expand(text)))

    fun startsWith(prefix: String) = StringPropertyFilter(StringProperty.CLASS_NAME, StringMatcher.startsWith(expand(prefix)))

}
