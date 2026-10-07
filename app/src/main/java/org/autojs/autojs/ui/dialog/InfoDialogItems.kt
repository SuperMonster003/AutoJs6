package org.autojs.autojs.ui.dialog

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.InfoDialogItemBinding
import kotlin.math.roundToInt

/**
 * Ordered information rows, including optional separators. All mutations belong on the UI thread.
 *
 * Indices include hidden entries and separators, so async visibility changes do not move insertion anchors.
 * Use a stable [Item.id] or retain an [Entry] to insert relative to an existing entry:
 * ```
 * val size = items.addItem(R.string.apk_info_file_size, "12 KB", id = "fileSize")
 * items.addItem("Source", "Local", index = 0)
 * items.addItemAfter(size, "SHA-256", digest)
 * items.addItemBefore(items["fileSize"], "Format", "APK")
 * ```
 */
@MainThread
class InfoDialogItems(context: Context) : LinearLayout(context) {

    private val entries = mutableListOf<Entry>()
    private var dialog: MaterialDialog? = null
    private val spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 4F, resources.displayMetrics).roundToInt()

    val size: Int get() = entries.size

    init {
        orientation = VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.ref_md_listitem_height)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.ref_md_listitem_vertical_margin)
        setPaddingRelative(
            resources.getDimensionPixelSize(R.dimen.ref_md_listitem_margin_left), verticalPadding,
            resources.getDimensionPixelSize(R.dimen.ref_md_dialog_frame_margin), verticalPadding,
        )
    }

    operator fun get(id: String): Item = entries.filterIsInstance<Item>().firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown information item: $id")

    fun entry(id: String): Entry = entries.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown information entry: $id")

    fun entryAt(index: Int): Entry = entries[index]

    fun indexOf(entry: Entry): Int = entries.indexOf(entry).also {
        require(it >= 0) { "The entry does not belong to this dialog" }
    }

    /** A missing initial value displays the loading placeholder. Use [Item.setValue] to report a missing result. */
    @JvmOverloads
    fun addItem(key: CharSequence, value: String? = null, index: Int = size, id: String? = null): Item {
        require(index in 0..size) { "Invalid information item index: $index" }
        require(id == null || entries.none { it.id == id }) { "Duplicate information entry id: $id" }
        val binding = InfoDialogItemBinding.inflate(LayoutInflater.from(context), this, false)
        return Item(id, binding, key, value).also { insert(it, index) }
    }

    @JvmOverloads
    fun addItem(@StringRes keyRes: Int, value: String? = null, index: Int = size, id: String? = null): Item =
        addItem(context.getString(keyRes), value, index, id)

    @JvmOverloads
    fun addItemBefore(anchor: Entry, key: CharSequence, value: String? = null, id: String? = null): Item =
        addItem(key, value, indexOf(anchor), id)

    @JvmOverloads
    fun addItemAfter(anchor: Entry, key: CharSequence, value: String? = null, id: String? = null): Item =
        addItem(key, value, indexOf(anchor) + 1, id)

    @JvmOverloads
    fun addSeparator(index: Int = size, id: String? = null): Entry {
        require(index in 0..size) { "Invalid information entry index: $index" }
        require(id == null || entries.none { it.id == id }) { "Duplicate information entry id: $id" }
        val view = View(context).apply {
            setBackgroundColor(context.getColor(R.color.day_night_alpha_20))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, resources.displayMetrics.density.roundToInt().coerceAtLeast(1))
        }
        return Entry(id, view).also { insert(it, index) }
    }

    /** A caller-built view, such as a row of options, spaced like the other entries. */
    @JvmOverloads
    fun addCustom(view: View, index: Int = size, id: String? = null): Entry {
        require(index in 0..size) { "Invalid information entry index: $index" }
        require(id == null || entries.none { it.id == id }) { "Duplicate information entry id: $id" }
        if (view.layoutParams !is LayoutParams) {
            view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        return Entry(id, view).also { insert(it, index) }
    }

    fun remove(entry: Entry) {
        entries.removeAt(indexOf(entry))
        removeView(entry.view)
    }

    internal fun bindTo(dialog: MaterialDialog) {
        check(this.dialog == null) { "Information items already belong to a dialog" }
        this.dialog = dialog
        entries.filterIsInstance<Item>().forEach { it.bindCopyAction() }
    }

    private fun insert(entry: Entry, index: Int) {
        entries.add(index, entry)
        addView(entry.view, index)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visibleItems = entries.filterIsInstance<Item>().filter { it.isVisible }
        val naturalKeyWidth = visibleItems.maxOfOrNull {
            it.labelView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            it.labelView.measuredWidth
        } ?: 0
        val availableWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        // Reserve room for values even with long translated keys or a large font scale.
        val keyWidth = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            naturalKeyWidth
        } else {
            naturalKeyWidth.coerceAtMost((availableWidth * 0.45F).roundToInt())
        }
        var hasVisibleEntry = false
        entries.forEach { entry ->
            (entry.view.layoutParams as LayoutParams).topMargin = if (hasVisibleEntry && entry.isVisible) spacing else 0
            if (entry.isVisible) hasVisibleEntry = true
            if (entry is Item) {
                entry.labelView.layoutParams.width = keyWidth
                // The natural-width probe above changes the label's measured size. Remeasure its row
                // even when the row's outer size is unchanged, so cached child positions are refreshed.
                entry.view.forceLayout()
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    open class Entry internal constructor(val id: String?, val view: View) {
        var isVisible: Boolean
            get() = view.isVisible
            set(value) {
                view.isVisible = value
            }
    }

    inner class Item internal constructor(
        id: String?,
        binding: InfoDialogItemBinding,
        key: CharSequence,
        value: String?,
    ) : Entry(id, binding.root) {

        val labelView: TextView = binding.infoItemLabel
        val valueView: TextView = binding.infoItemValue
        private var copyValue: String? = value

        var key: CharSequence
            get() = labelView.text
            set(value) {
                labelView.text = value
                this@InfoDialogItems.requestLayout()
            }

        val value: String? get() = copyValue

        var maxLines: Int
            get() = valueView.maxLines
            set(value) {
                require(value > 0) { "maxLines must be positive" }
                valueView.maxLines = value
            }

        init {
            this.key = key
            if (value != null) setValue(value)
        }

        /** The suffix is displayed but excluded from the copied value. Null or blank values display "Unknown". */
        @JvmOverloads
        fun setValue(value: String?, suffix: String? = null) {
            copyValue = value?.takeIf { it.isNotBlank() }
            valueView.text = copyValue?.let { it + suffix.orEmpty() } ?: context.getString(R.string.text_unknown)
            bindCopyAction()
        }

        fun setValueIfAbsent(value: Pair<String?, String?>) = setValueIfAbsent(value.first, value.second)

        @JvmOverloads
        fun setValueIfAbsent(value: String?, suffix: String? = null) {
            if (copyValue.isNullOrBlank() || copyValue == context.getString(R.string.text_unknown) ||
                copyValue == context.getString(R.string.ellipsis_six)
            ) {
                setValue(value, suffix)
            }
        }

        fun setValueIfAbsent(scope: CoroutineScope, value: () -> String?) {
            scope.launch(Dispatchers.IO) {
                val result = value()
                withContext(Dispatchers.Main) { setValueIfAbsent(result) }
            }
        }

        internal fun bindCopyAction() {
            valueView.setOnClickListener(null)
            dialog?.makeTextCopyable(valueView, copyValue)
        }
    }
}
