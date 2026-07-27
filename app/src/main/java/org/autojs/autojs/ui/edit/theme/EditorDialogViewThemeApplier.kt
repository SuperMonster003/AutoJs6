package org.autojs.autojs.ui.edit.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.MainThread
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.CompoundButtonCompat
import com.afollestad.materialdialogs.internal.MDButton
import com.google.android.material.textfield.TextInputLayout
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Applies an editor-owned palette to arbitrary dialog content.
 *
 * A weak global-layout watcher keeps asynchronously attached Fragment and adapter children in the
 * same palette. It is detached explicitly with the dialog and does not retain either its root view
 * or Activity.
 */
object EditorDialogViewThemeApplier {

    private data class Palette(
        @param:ColorInt val background: Int,
        @param:ColorInt val foreground: Int,
        @param:ColorInt val control: Int,
    )

    private val watchers = WeakHashMap<View, HierarchyWatcher>()
    private val appliedPalettes = WeakHashMap<View, Palette>()

    @JvmStatic
    @JvmOverloads
    @MainThread
    fun apply(
        root: View,
        @ColorInt background: Int,
        @ColorInt foreground: Int,
        @ColorInt control: Int = foreground,
    ) {
        val palette = Palette(background, foreground, control)
        if (root is ViewGroup) {
            val watcher = synchronized(watchers) {
                watchers[root] ?: HierarchyWatcher(root).also {
                    watchers[root] = it
                    it.start()
                }
            }
            watcher.palette = palette
        }
        applyTree(root, palette)
    }

    @JvmStatic
    @MainThread
    fun detach(root: View?) {
        root ?: return
        synchronized(watchers) {
            watchers.remove(root)
        }?.stop()
        clearAppliedPaletteTree(root)
    }

    private fun applyTree(view: View, palette: Palette) {
        val paletteChanged = synchronized(appliedPalettes) {
            appliedPalettes.put(view, palette) != palette
        }
        if (paletteChanged) {
            when (view) {
                is MDButton -> Unit
                is TextInputLayout -> applyTextInputLayout(view, palette)
                is SwitchCompat -> applySwitchCompat(view, palette)
                is Switch -> applySwitch(view, palette)
                is SeekBar -> applySeekBar(view, palette)
                is CompoundButton -> applyCompoundButton(view, palette)
                is EditText -> applyEditText(view, palette)
                is Button -> applyButton(view, palette)
                is TextView -> applyTextView(view, palette)
                is ProgressBar -> applyProgressBar(view, palette)
            }

            if (view is AbsListView || view is androidx.recyclerview.widget.RecyclerView) {
                view.setBackgroundColor(Color.TRANSPARENT)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyTree(view.getChildAt(index), palette)
            }
        }
    }

    private fun clearAppliedPaletteTree(view: View) {
        synchronized(appliedPalettes) {
            appliedPalettes.remove(view)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                clearAppliedPaletteTree(view.getChildAt(index))
            }
        }
    }

    private fun applyTextView(view: TextView, palette: Palette) {
        view.setTextColor(enabledStateList(palette.foreground))
        view.setHintTextColor(withAlpha(palette.foreground, SECONDARY_ALPHA))
        view.setLinkTextColor(palette.foreground)
    }

    private fun applyEditText(view: EditText, palette: Palette) {
        applyTextView(view, palette)
        ViewCompat.setBackgroundTintList(view, controlStateList(palette))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.textCursorDrawable = view.textCursorDrawable?.mutate()?.apply {
                setTint(palette.control)
            }
            view.textSelectHandle?.mutate()?.setTint(palette.control)
            view.textSelectHandleLeft?.mutate()?.setTint(palette.control)
            view.textSelectHandleRight?.mutate()?.setTint(palette.control)
        }
    }

    private fun applyButton(view: Button, palette: Palette) {
        applyTextView(view, palette)
        ViewCompat.setBackgroundTintList(view, buttonStateList(palette))
    }

    private fun applyTextInputLayout(view: TextInputLayout, palette: Palette) {
        view.defaultHintTextColor = ColorStateList.valueOf(
            withAlpha(palette.foreground, SECONDARY_ALPHA),
        )
        view.hintTextColor = ColorStateList.valueOf(palette.control)
        view.boxStrokeColor = palette.control
    }

    private fun applyCompoundButton(view: CompoundButton, palette: Palette) {
        applyTextView(view, palette)
        CompoundButtonCompat.setButtonTintList(view, choiceStateList(palette))
    }

    private fun applySwitchCompat(view: SwitchCompat, palette: Palette) {
        applyTextView(view, palette)
        view.thumbTintList = choiceStateList(palette)
        view.trackTintList = trackStateList(palette)
    }

    private fun applySwitch(view: Switch, palette: Palette) {
        applyTextView(view, palette)
        view.thumbTintList = choiceStateList(palette)
        view.trackTintList = trackStateList(palette)
    }

    private fun applySeekBar(view: SeekBar, palette: Palette) {
        view.thumbTintList = ColorStateList.valueOf(palette.control)
        view.progressTintList = ColorStateList.valueOf(palette.control)
        view.progressBackgroundTintList = ColorStateList.valueOf(
            withAlpha(palette.foreground, TRACK_ALPHA),
        )
    }

    private fun applyProgressBar(view: ProgressBar, palette: Palette) {
        view.progressTintList = ColorStateList.valueOf(palette.control)
        view.indeterminateTintList = ColorStateList.valueOf(palette.control)
        view.progressBackgroundTintList = ColorStateList.valueOf(
            withAlpha(palette.foreground, TRACK_ALPHA),
        )
    }

    private fun enabledStateList(@ColorInt foreground: Int) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(foreground, DISABLED_ALPHA),
            foreground,
        ),
    )

    private fun controlStateList(palette: Palette) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(palette.foreground, DISABLED_ALPHA),
            palette.control,
            withAlpha(palette.foreground, SECONDARY_ALPHA),
        ),
    )

    private fun choiceStateList(palette: Palette) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(palette.foreground, DISABLED_ALPHA),
            palette.control,
            withAlpha(palette.foreground, SECONDARY_ALPHA),
        ),
    )

    private fun trackStateList(palette: Palette) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(palette.foreground, TRACK_DISABLED_ALPHA),
            withAlpha(palette.control, TRACK_CHECKED_ALPHA),
            withAlpha(palette.foreground, TRACK_ALPHA),
        ),
    )

    private fun buttonStateList(palette: Palette) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_pressed),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(),
        ),
        intArrayOf(
            withAlpha(palette.foreground, BUTTON_DISABLED_ALPHA),
            withAlpha(palette.control, BUTTON_PRESSED_ALPHA),
            withAlpha(palette.control, BUTTON_FOCUSED_ALPHA),
            withAlpha(palette.foreground, BUTTON_ALPHA),
        ),
    )

    @ColorInt
    private fun withAlpha(@ColorInt color: Int, alpha: Float): Int {
        val sourceAlpha = Color.alpha(color)
        return color and 0x00FFFFFF or
            ((sourceAlpha * alpha).toInt().coerceIn(0, 0xFF) shl 24)
    }

    private class HierarchyWatcher(root: View) :
        ViewTreeObserver.OnGlobalLayoutListener,
        View.OnAttachStateChangeListener {

        private val rootReference = WeakReference(root)
        private var observer: ViewTreeObserver? = null
        lateinit var palette: Palette

        fun start() {
            rootReference.get()?.addOnAttachStateChangeListener(this)
            registerObserver()
        }

        fun stop() {
            rootReference.get()?.removeOnAttachStateChangeListener(this)
            unregisterObserver()
        }

        override fun onGlobalLayout() {
            val root = rootReference.get() ?: return
            if (::palette.isInitialized) {
                applyTree(root, palette)
            }
        }

        override fun onViewAttachedToWindow(view: View) {
            registerObserver()
            if (::palette.isInitialized) {
                applyTree(view, palette)
            }
        }

        override fun onViewDetachedFromWindow(view: View) {
            unregisterObserver()
        }

        private fun registerObserver() {
            val nextObserver = rootReference.get()?.viewTreeObserver ?: return
            if (observer === nextObserver && nextObserver.isAlive) {
                return
            }
            unregisterObserver()
            if (nextObserver.isAlive) {
                nextObserver.addOnGlobalLayoutListener(this)
                observer = nextObserver
            }
        }

        private fun unregisterObserver() {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            observer = null
        }
    }

    private const val SECONDARY_ALPHA = 0.68f
    private const val DISABLED_ALPHA = 0.38f
    private const val TRACK_ALPHA = 0.24f
    private const val TRACK_CHECKED_ALPHA = 0.48f
    private const val TRACK_DISABLED_ALPHA = 0.12f
    private const val BUTTON_ALPHA = 0.12f
    private const val BUTTON_FOCUSED_ALPHA = 0.18f
    private const val BUTTON_PRESSED_ALPHA = 0.24f
    private const val BUTTON_DISABLED_ALPHA = 0.06f
}
