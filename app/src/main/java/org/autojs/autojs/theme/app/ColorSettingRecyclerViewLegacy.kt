package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.customColorPosition
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

class ColorSettingRecyclerViewLegacy : ThemeColorRecyclerView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private var mOnItemClickListener: ColorSelectBaseActivity.OnItemClickListener? = null

    private var mSelectedPosition = ColorSelectBaseActivity.SELECT_NONE

    val selectedThemeColor: ThemeColor?
        get() = when {
            mSelectedPosition < 0 -> null
            mSelectedPosition == customColorPosition -> customColor
            else -> context.getColor(ColorSelectBaseActivity.colorItems[mSelectedPosition].first)
        }?.let { ThemeColor(it) }

    private val customColor: Int
        get() = Pref.getInt(ColorSelectBaseActivity.KEY_CUSTOM_COLOR, context.getColor(R.color.md_blue_grey_800))

    private val mActualOnItemClickListener = OnClickListener { v ->
        getChildViewHolder(v)?.let { holder ->
            when (val pos = holder.bindingAdapterPosition) {
                customColorPosition -> showColorPicker(v)
                else -> {
                    setSelectedPosition(pos)
                    mOnItemClickListener?.onItemClick(v, pos)
                }
            }
        }
    }

    init {
        adapter = Adapter()
        layoutManager = LinearLayoutManager(context)
        addItemDecoration(DividerItemDecoration(context, VERTICAL))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedPosition(currentPosition: Int) {
        if (mSelectedPosition != ColorSelectBaseActivity.SELECT_NONE) {
            adapter!!.notifyItemChanged(mSelectedPosition)
            mSelectedPosition = currentPosition
            adapter!!.notifyItemChanged(currentPosition)
        } else {
            mSelectedPosition = currentPosition
            adapter!!.notifyDataSetChanged()
        }
        Pref.putInt(ColorSelectBaseActivity.KEY_SELECTED_COLOR_INDEX, mSelectedPosition)
    }

    fun setSelectedColor(colorPrimary: Int) {
        for ((i, colorItem) in ColorSelectBaseActivity.colorItems.withIndex()) {
            val color = when (i) {
                customColorPosition -> customColor
                else -> context.getColor(colorItem.first)
            }
            if (color == colorPrimary) {
                setSelectedPosition(i)
                return
            }
        }
        mSelectedPosition = ColorSelectBaseActivity.SELECT_NONE
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
                        Pref.putInt(ColorSelectBaseActivity.KEY_CUSTOM_COLOR, c)
                        setSelectedPosition(customColorPosition)
                        mOnItemClickListener?.onItemClick(v, customColorPosition)
                    }

                    override fun onDialogDismissed(dialogId: Int) {}
                })
            }
            .show((context as FragmentActivity).supportFragmentManager, "Tag")
    }

    fun setOnItemClickListener(onItemClickListener: ColorSelectBaseActivity.OnItemClickListener?) {
        mOnItemClickListener = onItemClickListener
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(context).inflate(R.layout.mt_color_setting_recycler_view_item, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val colorItem = ColorSelectBaseActivity.colorItems[position]
            val (colorRes, nameRes) = colorItem
            val color = when (position) {
                customColorPosition -> customColor
                else -> context.getColor(colorRes)
            }
            holder.apply {
                setColor(color)
                nameView.text = context.getString(nameRes)
                nameView.setTextColor(context.getColor(R.color.color_selector_item_text))
                setChecked(mSelectedPosition == position)
            }
        }

        override fun getItemCount() = ColorSelectBaseActivity.colorItems.size

    }

    private inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var colorView: ImageView
        var nameView: TextView

        init {
            itemView.setOnClickListener(mActualOnItemClickListener)
            colorView = itemView.findViewById(R.id.color)
            nameView = itemView.findViewById(R.id.name)
        }

        fun setChecked(checked: Boolean) {
            if (checked) {
                colorView.setImageResource(R.drawable.mt_ic_check_white_36dp)
                val selectedThemeColorRes = selectedThemeColor?.colorPrimary?.let {
                    if (ColorUtils.isLuminanceLight(it)) R.color.day else R.color.night
                } ?: R.color.night_day
                colorView.imageTintList = ColorStateList.valueOf(context.getColor(selectedThemeColorRes))
            } else {
                colorView.setImageDrawable(null)
            }
        }

        fun setColor(c: Int) = ThemeColorHelper.setBackgroundColor(colorView, c)

    }

}