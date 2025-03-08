package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.view.ViewAnimationUtils
import com.google.android.material.appbar.AppBarLayout
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorSelectBinding
import kotlin.math.hypot

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Sep 22, 2022.
 * Transformed by SuperMonster003 on Sep 22, 2022.
 */
class ColorSelectActivity : BaseActivity() {

    override val handleStatusBarThemeColorAutomatically = false

    private var binding: MtActivityColorSelectBinding? = null

    private lateinit var mAppBarLayout: AppBarLayout
    private lateinit var mTitle: String
    private lateinit var mColorSettingRecyclerView: ColorSettingRecyclerView

    private var mCurrentColor = 0

    private val mOnItemClickListener = object : OnItemClickListener {
        override fun onItemClick(v: View?, position: Int) {
            mColorSettingRecyclerView.selectedThemeColor?.let {
                setColorWithAnimation(mAppBarLayout, it.colorPrimary)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
        binding = MtActivityColorSelectBinding.inflate(layoutInflater).also {
            setUpUI(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    private fun handleIntent() {
        mTitle = intent.getStringExtra("title") ?: getString(R.string.mt_color_picker_title)
    }

    private fun setUpUI(binding: MtActivityColorSelectBinding) {
        setContentView(binding.root)
        mCurrentColor = ThemeColorManager.colorPrimary
        mAppBarLayout = binding.appBar.apply { setBackgroundColor(mCurrentColor) }
        setUpToolbar(binding)
        setUpColorSettingRecyclerView(binding)
    }

    private fun setUpToolbar(binding: MtActivityColorSelectBinding) {
        binding.toolbar.also { toolbar ->
            toolbar.title = mTitle
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { finish() }
        }
    }

    private fun setUpColorSettingRecyclerView(binding: MtActivityColorSelectBinding) {
        mColorSettingRecyclerView = binding.colorSettingRecyclerView.apply {
            setCustomColor()
            setSelectedColor(mCurrentColor)
            setOnItemClickListener(mOnItemClickListener)
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

    override fun initThemeColors() {
        super.initThemeColors()
        setUpToolbarColors()
        setUpStatusBarIconLight()
    }

    private fun setUpToolbarColors() {
        val toolbar = binding!!.toolbar
        val aimColor = when {
            ColorUtils.isLuminanceLight(mCurrentColor) -> getColor(R.color.day)
            else -> getColor(R.color.night)
        }
        toolbar.setTitleTextColor(aimColor)
        toolbar.setSubtitleTextColor(aimColor)
        toolbar.navigationIcon?.let { navigationIcon ->
            navigationIcon.colorFilter = PorterDuffColorFilter(aimColor, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun setUpStatusBarIconLight() {
        ViewUtils.setStatusBarIconLight(this, ColorUtils.isLuminanceDark(mCurrentColor))
    }

    private fun setColorWithAnimation(view: View, colorTo: Int) {
        binding!!.appBarContainer.setBackgroundColor(mCurrentColor)
        view.setBackgroundColor(colorTo)
        mCurrentColor = colorTo

        initThemeColors()

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

        @JvmStatic
        fun startActivity(context: Context) {
            Intent(context, ColorSelectActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("title", context.getString(R.string.mt_color_picker_title))
                .let { context.startActivity(it) }
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