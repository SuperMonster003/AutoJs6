package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils.appendSystemUiVisibility
import org.autojs.autojs6.R
import kotlin.math.hypot

/**
 * Created by Stardust on 2017/3/5.
 * Modified by SuperMonster003 as of Sep 22, 2022.
 * Transformed by SuperMonster003 on Sep 22, 2022.
 */
class ColorSelectActivity : BaseActivity() {

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

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent()
        setUpUI()
        appendSystemUiVisibility(this, View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun handleIntent() {
        mTitle = intent.getStringExtra("title") ?: getString(R.string.mt_color_picker_title)
    }

    private fun setUpUI() {
        setContentView(R.layout.mt_activity_color_select)
        mCurrentColor = ThemeColorManager.colorPrimary
        mAppBarLayout = findViewById<AppBarLayout?>(R.id.appBar).apply { setBackgroundColor(mCurrentColor) }
        setUpToolbar()
        setUpColorSettingRecyclerView()
    }

    private fun setUpToolbar() {
        findViewById<Toolbar>(R.id.toolbar).also { toolbar ->
            toolbar.title = mTitle
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener { finish() }
        }
    }

    private fun setUpColorSettingRecyclerView() {
        mColorSettingRecyclerView = findViewById<ColorSettingRecyclerView?>(R.id.color_setting_recycler_view)
            .apply {
                setCustomColor()
                setSelectedColor(mCurrentColor)
                setOnItemClickListener(mOnItemClickListener)
            }
    }

    override fun finish() {
        mColorSettingRecyclerView.selectedThemeColor?.let {
            ThemeColorManager.setThemeColor(it.colorPrimary)
        }
        super.finish()
        onFinish()
    }

    private fun setColorWithAnimation(view: View, colorTo: Int) {
        findViewById<View>(R.id.appBarContainer).setBackgroundColor(mCurrentColor)
        view.setBackgroundColor(colorTo)
        ViewAnimationUtils.createCircularReveal(
            /* view = */ view,
            /* centerX = */ view.left,
            /* centerY = */ view.bottom,
            /* startRadius = */ 0f,
            /* endRadius = */ hypot(view.width.toDouble(), view.height.toDouble()).toFloat()
        ).apply {
            duration = 500
            start()
        }
        mCurrentColor = colorTo
    }

    private class ColorItem(var name: String, var themeColor: ThemeColor) {
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

        var onFinish = {}

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

        private fun getColorItems(context: Context): List<ColorItem> = ArrayList<Pair</* nameRes */ Int, /* colorInt */ Int>>()
            .apply {
                add(customColorPosition, Pair(Pref.getInt(KEY_CUSTOM_COLOR, context.getColor(R.color.md_blue_grey_800)), R.string.mt_custom))
                add(defaultColorPosition, Pair(context.getColor(R.color.theme_color_default), R.string.theme_color_default))
                addAll(ColorUtils.MATERIAL_COLOR_ITEMS.map { pair -> Pair(context.getColor(pair.first), pair.second) })
            }
            .map { (colorInt, nameRes) -> ColorItem(context.getString(nameRes), colorInt) }

        class ColorSettingRecyclerView : ThemeColorRecyclerView {

            private var mOnItemClickListener: OnItemClickListener? = null

            private var mSelectedPosition = SELECT_NONE

            val selectedThemeColor: ThemeColor?
                get() = getColorItems(context)[mSelectedPosition].themeColor.takeUnless { mSelectedPosition < 0 }

            private val customColor: Int
                get() = Pref.getInt(KEY_CUSTOM_COLOR, context.getColor(R.color.md_blue_grey_800))

            private val mActualOnItemClickListener = OnClickListener { v ->
                getChildViewHolder(v)?.let { holder ->
                    val pos = holder.bindingAdapterPosition
                    if (pos == customColorPosition) {
                        showColorPicker(v)
                    } else {
                        setSelectedPosition(pos)
                        mOnItemClickListener?.onItemClick(v, pos)
                    }
                }
            }

            constructor(context: Context) : super(context)

            constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

            constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

            init {
                adapter = Adapter()
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(DividerItemDecoration(context, VERTICAL))
            }

            @SuppressLint("NotifyDataSetChanged")
            fun setSelectedPosition(currentPosition: Int) {
                if (mSelectedPosition != SELECT_NONE) {
                    adapter!!.notifyItemChanged(mSelectedPosition)
                    mSelectedPosition = currentPosition
                    adapter!!.notifyItemChanged(currentPosition)
                } else {
                    mSelectedPosition = currentPosition
                    adapter!!.notifyDataSetChanged()
                }
                Pref.putInt(KEY_SELECTED_COLOR_INDEX, mSelectedPosition)
            }

            fun setSelectedColor(colorPrimary: Int) {
                for ((i, colorItem) in getColorItems(context).withIndex()) {
                    if (colorItem.themeColor.colorPrimary == colorPrimary) {
                        return Unit.also { setSelectedPosition(i) }
                    }
                }
                mSelectedPosition = SELECT_NONE
            }

            private fun setCustomColor(color: Int) {
                getColorItems(context)[customColorPosition].themeColor.colorPrimary = color
            }

            fun setCustomColor() {
                setCustomColor(customColor)
            }

            private fun showColorPicker(v: View) {
                ColorPickerDialog.newBuilder()
                    .setAllowCustom(true)
                    .setAllowPresets(false)
                    .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                    .setShowAlphaSlider(false)
                    .setDialogTitle(R.string.mt_color_picker_title)
                    .setColor(customColor)
                    .create()
                    .apply {
                        setColorPickerDialogListener(object : ColorPickerDialogListener {
                            override fun onColorSelected(dialogId: Int, @ColorInt color: Int) {
                                val c = color or -0x1000000
                                Pref.putInt(KEY_CUSTOM_COLOR, c)
                                setCustomColor(c)
                                setSelectedPosition(customColorPosition)
                                mOnItemClickListener?.onItemClick(v, customColorPosition)
                            }

                            override fun onDialogDismissed(dialogId: Int) {}
                        })
                    }
                    .show((context as FragmentActivity).supportFragmentManager, "Tag")
            }

            fun setOnItemClickListener(onItemClickListener: OnItemClickListener?) {
                mOnItemClickListener = onItemClickListener
            }

            private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                    return ViewHolder(LayoutInflater.from(context).inflate(R.layout.mt_color_setting_recycler_view_item, parent, false))
                }

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    val item = getColorItems(context)[position]
                    holder.apply {
                        setColor(item.themeColor.colorPrimary)
                        name.text = item.name
                        name.setTextColor(context.getColor(R.color.color_selector_item_text))
                        setChecked(mSelectedPosition == position)
                    }
                }

                override fun getItemCount() = getColorItems(context).size

            }

            private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

                var color: ImageView
                var name: TextView

                init {
                    itemView.setOnClickListener(mActualOnItemClickListener)
                    color = itemView.findViewById(R.id.color)
                    name = itemView.findViewById(R.id.name)
                }

                fun setChecked(checked: Boolean) {
                    if (checked) {
                        color.setImageResource(R.drawable.mt_ic_check_white_36dp)
                        color.imageTintList = ColorStateList.valueOf(context.getColor(R.color.night_day))
                    } else {
                        color.setImageDrawable(null)
                    }
                }

                fun setColor(c: Int) = ThemeColorHelper.setBackgroundColor(color, c)

            }

        }

    }

}