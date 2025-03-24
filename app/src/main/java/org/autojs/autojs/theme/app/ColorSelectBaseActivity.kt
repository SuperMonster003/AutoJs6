package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewAnimationUtils
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.AppBarLayout
import io.codetail.widget.RevealFrameLayout
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByColorLuminance
import org.autojs.autojs6.R
import kotlin.math.hypot
import kotlin.properties.Delegates

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Sep 22, 2022.
 * Transformed by SuperMonster003 on Sep 22, 2022.
 */
abstract class ColorSelectBaseActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private lateinit var mToolbar: Toolbar
    private lateinit var mAppBarLayout: AppBarLayout
    private lateinit var mAppBarContainer: RevealFrameLayout
    private lateinit var mColorSettingRecyclerView: ColorSettingRecyclerView

    private lateinit var mTitle: String

    private val mOnItemClickListener = object : OnItemClickListener {
        override fun onItemClick(v: View?, position: Int) {
            mColorSettingRecyclerView.selectedThemeColor?.let {
                setColorWithAnimation(mAppBarLayout, it.colorPrimary)
            }
        }
    }

    private var currentColor by Delegates.notNull<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTitle = this.intent.getStringExtra("title") ?: this.getString(R.string.mt_color_picker_title)
        currentColor = this.intent.getIntExtra("currentColor", ThemeColorManager.colorPrimary)
    }

    protected fun setUpToolbar(toolbar: Toolbar) {
        toolbar.apply {
            title = mTitle
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
            setTitleTextAppearance(this@ColorSelectBaseActivity, R.style.TextAppearanceMainTitle)
            mToolbar = this
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected fun setUpColorSettingRecyclerView(colorSettingRecyclerView: ColorSettingRecyclerView) {
        mColorSettingRecyclerView = colorSettingRecyclerView.apply {
            setCustomColor()
            setSelectedColor(currentColor)
            setOnItemClickListener(mOnItemClickListener)
            ViewUtils.excludePaddingClippableViewFromNavigationBar(this)
        }
    }

    override fun finish() {
        mColorSettingRecyclerView.selectedThemeColor?.let {
            ThemeColorManager.setThemeColor(it.colorPrimary)
            Pref.putString(key(R.string.key_theme_color), getColorString(this))
            ThemeChangeNotifier.notifyThemeChanged()
        }
        super.finish()
    }

    protected fun finishWithoutSaving() {
        super.finish()
    }

    override fun initThemeColors() {
        super.initThemeColors()
        mToolbar.setTitlesTextColorByColorLuminance(this, currentColor)
        mToolbar.setNavigationIconColorByColorLuminance(this, currentColor)
        ViewUtils.setStatusBarAppearanceLightByColorLuminance(this, currentColor)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateToolbarIconsColor()
        return super.onCreateOptionsMenu(menu)
    }

    private fun updateToolbarIconsColor() {
        mToolbar.setMenuIconsColorByColorLuminance(this, currentColor)
    }

    private fun setColorWithAnimation(view: View, colorTo: Int) {
        mAppBarContainer.setBackgroundColor(currentColor)
        view.setBackgroundColor(colorTo)
        currentColor = colorTo

        initThemeColors()
        updateToolbarIconsColor()

        ViewAnimationUtils.createCircularReveal(
            /* view = */ view,
            /* centerX = */ view.left,
            /* centerY = */ view.bottom,
            /* startRadius = */ 0f,
            /* endRadius = */ hypot(view.width.toDouble(), view.height.toDouble()).toFloat()
        ).apply {
            duration = 500L
            start()
        }
    }

    protected fun setUpAppBar(appBar: AppBarLayout, appBarContainer: RevealFrameLayout) {
        mAppBarLayout = appBar.apply { setBackgroundColor(currentColor) }
        mAppBarContainer = appBarContainer
    }

    class ColorItem(var name: String, var themeColor: ThemeColor) {
        constructor(name: String, color: Int) : this(name, ThemeColor(color))
    }

    interface OnItemClickListener {
        fun onItemClick(v: View?, position: Int)
    }

    companion object {

        const val SELECT_NONE = -1

        val KEY_CUSTOM_COLOR = "${ColorSettingRecyclerView::class.java.name}.COLOR_SETTING_CUSTOM_COLOR"
        val KEY_SELECTED_COLOR_INDEX = "${ColorSettingRecyclerView::class.java.name}.SELECTED_COLOR_INDEX"

        val customColorPosition: Int
            get() = /* return mColors.size() - 1; */ 0

        private val defaultColorPosition: Int
            get() = customColorPosition + 1

        var isLegacyLayout: Boolean
            get() = Pref.getBoolean(R.string.key_color_select_activity_legacy_layout, false)
            set(value) = Pref.putBoolean(R.string.key_color_select_activity_legacy_layout, value)

        @JvmStatic
        fun startActivity(context: Context) {
            val cls = when (isLegacyLayout) {
                true -> ColorSelectLegacyActivity::class.java
                else -> ColorSelectActivity::class.java
            }
            val intent = Intent(context, cls).apply {
                putExtra("title", context.getString(R.string.mt_color_picker_title))
            }
            if (context is ColorSelectBaseActivity) {
                intent.putExtra("currentColor", context.currentColor)
                context.finishWithoutSaving()
            }
            context.startActivity(intent)
        }

        fun getColorString(context: Context): String {
            val index = Pref.getInt(KEY_SELECTED_COLOR_INDEX, defaultColorPosition)
            val colorItem = getColorItems(context)[index]
            return "${colorItem.name} [${ColorUtils.toString(colorItem.themeColor.colorPrimary).uppercase()}]"
        }

        internal fun getColorItems(context: Context): List<ColorItem> = ArrayList<Pair</* nameRes */ Int, /* colorInt */ Int>>()
            .apply {
                add(customColorPosition, Pair(Pref.getInt(KEY_CUSTOM_COLOR, context.getColor(R.color.md_blue_grey_800)), R.string.mt_custom))
                add(defaultColorPosition, Pair(context.getColor(R.color.theme_color_default), R.string.theme_color_default))
                addAll(ColorUtils.MATERIAL_COLOR_ITEMS.map { pair -> Pair(context.getColor(pair.first), pair.second) })
            }
            .map { (colorInt, nameRes) -> ColorItem(context.getString(nameRes), colorInt) }

    }

}