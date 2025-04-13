package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ThemeColorRecyclerView
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_PALETTE
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_DEFAULT
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.COLOR_LIBRARY_ID_MATERIAL
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_LEGACY_SELECTED_COLOR_INDEX
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_SELECTED_COLOR_LIBRARY_ID
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.KEY_SELECTED_COLOR_ITEM_ID
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.SELECT_NONE
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.colorItemsLegacy
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.customColorPosition
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.defaultColorPosition
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.saveDatabaseForColorHistories
import org.autojs.autojs.theme.app.ColorSelectBaseActivity.Companion.saveDatabaseForPaletteHistories
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorSettingRecyclerViewItemBinding

class ColorSettingRecyclerView : ThemeColorRecyclerView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    private val mAdapter: Adapter
    private var mOnItemClickListener: ColorSelectBaseActivity.OnItemClickListener? = null
    private var mSelectedPosition = SELECT_NONE

    val selectedColor: Int?
        get() = when {
            mSelectedPosition < 0 -> null
            mSelectedPosition == customColorPosition -> customColor
            else -> context.getColor(colorItemsLegacy[mSelectedPosition].first)
        }

    private val customColor: Int
        get() = Pref.getInt(ColorSelectBaseActivity.KEY_CUSTOM_COLOR, context.getColor(R.color.custom_color_default))

    private val mActualOnItemClickListener = OnClickListener { v ->
        getChildViewHolder(v)?.let { holder ->
            when (val pos = holder.bindingAdapterPosition) {
                customColorPosition -> showColorPicker(v)
                defaultColorPosition -> {
                    setSelectedPosition(pos)
                    mOnItemClickListener?.onItemClick(v, pos)

                    presetColorLibraries.find { it.isDefault }!!.colors.find { colorItem ->
                        context.getColor(colorItem.colorRes) == context.getColor(R.color.theme_color_default)
                    }?.let {
                        saveDatabaseForColorHistories(
                            applicationContext = context.applicationContext,
                            libraryId = COLOR_LIBRARY_ID_DEFAULT,
                            itemId = it.itemId,
                        )
                    }
                }
                else -> {
                    setSelectedPosition(pos)
                    mOnItemClickListener?.onItemClick(v, pos)

                    selectedColor?.let { c ->
                        presetColorLibraries.find { it.isMaterial }!!.colors.find { colorItem ->
                            context.getColor(colorItem.colorRes) == c
                        }?.let {
                            saveDatabaseForColorHistories(
                                applicationContext = context.applicationContext,
                                libraryId = COLOR_LIBRARY_ID_MATERIAL,
                                itemId = it.itemId,
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        adapter = Adapter().also { mAdapter = it }
        layoutManager = LinearLayoutManager(context)
        addItemDecoration(DividerItemDecoration(context, VERTICAL))
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedPosition(currentPosition: Int) {
        if (mSelectedPosition == SELECT_NONE) {
            mSelectedPosition = currentPosition
            mAdapter.notifyDataSetChanged()
        } else {
            mAdapter.notifyItemChanged(mSelectedPosition)
            mSelectedPosition = currentPosition
            mAdapter.notifyItemChanged(currentPosition)
        }
        savePrefsForLegacy()
        savePrefsForLibraries()
        selectedColor?.let {
            ThemeColorManager.setThemeColor(it)
            ThemeChangeNotifier.notifyThemeChanged()
        }
    }

    private fun savePrefsForLegacy() {
        Pref.putInt(KEY_LEGACY_SELECTED_COLOR_INDEX, mSelectedPosition)
    }

    private fun savePrefsForLibraries() {
        when (mSelectedPosition) {
            customColorPosition -> {
                Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_ID_PALETTE)
                Pref.putInt(KEY_SELECTED_COLOR_ITEM_ID, 0)
            }
            defaultColorPosition -> {
                Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_ID_DEFAULT)
                presetColorLibraries.find { it.isDefault }!!.colors.find { colorItem ->
                    context.getColor(colorItem.colorRes) == context.getColor(R.color.theme_color_default)
                }?.let { Pref.putInt(KEY_SELECTED_COLOR_ITEM_ID, it.itemId) }
            }
            else -> selectedColor?.let { c ->
                presetColorLibraries.find { it.isMaterial }!!.colors.find { colorItem ->
                    context.getColor(colorItem.colorRes) == c
                }?.let {
                    Pref.putInt(KEY_SELECTED_COLOR_LIBRARY_ID, COLOR_LIBRARY_ID_MATERIAL)
                    Pref.putInt(KEY_SELECTED_COLOR_ITEM_ID, it.itemId)
                }
            }
        }
    }

    fun setUpSelectedPosition(currentColor: Int) {
        val selectedIndex = Pref.getInt(KEY_LEGACY_SELECTED_COLOR_INDEX, SELECT_NONE)
        colorItemsLegacy.getOrNull(selectedIndex)?.let {
            setSelectedPosition(selectedIndex)
        } ?: when (currentColor) {
            context.getColor(R.color.theme_color_default) -> {
                setSelectedPosition(defaultColorPosition)
            }
            else -> mSelectedPosition = SELECT_NONE
        }
    }

    private fun showColorPicker(v: View) {
        ColorPickerDialog.newBuilder()
            .setUseLegacyMode(true)
            .setAllowCustom(true)
            .setAllowPresets(false)
            .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
            .setShowAlphaSlider(false)
            .setDialogTitle(R.string.mt_color_picker_title)
            .setColor(customColor)
            .create()
            .setColorPickerDialogListener { dialogId: Int, color: Int ->
                val colorWithFullAlpha = color or Color.BLACK
                Pref.putInt(ColorSelectBaseActivity.KEY_CUSTOM_COLOR, colorWithFullAlpha)
                setSelectedPosition(customColorPosition)
                saveDatabaseForPaletteHistories(applicationContext = context.applicationContext, color = colorWithFullAlpha)
                mOnItemClickListener?.onItemClick(v, customColorPosition)
            }
            .show((context as FragmentActivity).supportFragmentManager, "ColorPickerTagForColorSelect")
    }

    fun setOnItemClickListener(onItemClickListener: ColorSelectBaseActivity.OnItemClickListener?) {
        mOnItemClickListener = onItemClickListener
    }

    private inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = MtColorSettingRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val colorItem = colorItemsLegacy[position]
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

        override fun getItemCount() = colorItemsLegacy.size

    }

    private inner class ViewHolder(itemViewBinding: MtColorSettingRecyclerViewItemBinding) : RecyclerView.ViewHolder(itemViewBinding.root) {

        val colorView: ImageView = itemViewBinding.color
        val nameView: TextView = itemViewBinding.name

        init {
            itemViewBinding.root.apply { setOnClickListener(mActualOnItemClickListener) }
        }

        fun setChecked(checked: Boolean) {
            if (checked) {
                colorView.setImageResource(R.drawable.mt_ic_check_white_36dp)
                val selectedThemeColorRes = selectedColor?.let {
                    if (ViewUtils.isLuminanceLight(it)) R.color.day else R.color.night
                } ?: R.color.night_day
                colorView.imageTintList = ColorStateList.valueOf(context.getColor(selectedThemeColorRes))
            } else {
                colorView.setImageDrawable(null)
            }
        }

        fun setColor(c: Int) = ThemeColorHelper.setBackgroundColor(colorView, c)

    }

}