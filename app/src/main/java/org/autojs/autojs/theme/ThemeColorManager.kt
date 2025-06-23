package org.autojs.autojs.theme

import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.view.View
import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ViewUtils
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by Stardust on May 10, 2016.
 */
object ThemeColorManager {

    @JvmField
    var defaultThemeColor = when {
        // R.color.md_blue_gray_900
        isInrt -> "#263238"
        // R.color.theme_color_default
        else -> "#00695C"
    }.let { ThemeColor(ColorUtils.parseColor(it)) }

    @JvmStatic
    lateinit var currentThemeColor: ThemeColor

    @JvmStatic
    val colorPrimary: Int
        get() = currentThemeColor.colorPrimary

    fun init() {
        ThemeColor.fromPreferences()?.let { currentThemeColor = it } ?: setThemeColor(defaultThemeColor)
    }

    @JvmStatic
    fun add(colorMutable: ThemeColorMutable) = ThemeColorMutableManager.add(colorMutable)

    @JvmStatic
    fun addViewBackground(titleBarView: View) = BackgroundColorManager.add(titleBarView)

    fun addPaint(paint: Paint) = PaintManager.add(paint)

    fun addActivityStatusBar(activity: Activity) = StatusBarManager.add(activity)

    fun addActivityNavigationBar(activity: Activity) {
        ThemeColorWidgetReferenceManager.add(object : ThemeColorMutableReference {
            val weakReference = WeakReference(activity)
            override fun setThemeColor(color: ThemeColor) {
                ViewUtils.setNavigationBarBackgroundColor(activity, color.colorPrimary)
            }

            override fun isNull() = weakReference.get() == null
        })
    }

    fun setThemeColor(themeColor: ThemeColor) {
        currentThemeColor = themeColor
        saveThemeColorIfNeeded()
        val color = themeColor.colorPrimary
        BackgroundColorManager.setColor(color)
        StatusBarManager.setColor(color)
        PaintManager.setColor(color)
        ThemeColorMutableManager.setColor(themeColor)
        ThemeColorWidgetReferenceManager.setColor(themeColor)
    }

    fun setThemeColor(color: Int) = setThemeColor(ThemeColor(color))

    private fun saveThemeColorIfNeeded() {
        currentThemeColor.saveIn()
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceLight(backgroundColorMatters: Boolean = true): Boolean {
        return ViewUtils.isLuminanceLight(colorPrimary, backgroundColorMatters)
    }

    @JvmStatic
    @JvmOverloads
    fun isLuminanceDark(backgroundColorMatters: Boolean = true): Boolean {
        return ViewUtils.isLuminanceDark(colorPrimary, backgroundColorMatters)
    }

    @JvmStatic
    fun getDayOrNightColorByLuminance(context: Context): Int {
        return ViewUtils.getDayOrNightColorByLuminance(context, colorPrimary)
    }

    @JvmStatic
    fun getDayOrNightColorResByLuminance(): Int {
        return ViewUtils.getDayOrNightColorResByLuminance(colorPrimary)
    }

    @JvmStatic
    fun setStatusBarBackgroundColor(activity: Activity) {
        ViewUtils.setStatusBarBackgroundColor(activity, colorPrimary)
    }

    @JvmStatic
    fun setStatusBarIconLight(activity: Activity) {
        ViewUtils.setStatusBarIconLight(activity, isLuminanceDark())
    }

    private object BackgroundColorManager {

        private val views: MutableList<WeakReference<View>> = LinkedList()

        fun add(view: View) {
            views.add(WeakReference(view))
            view.setBackgroundColor(currentThemeColor.colorPrimary)
        }

        fun setColor(color: Int) = views.iterator().let {
            while (it.hasNext()) {
                it.next().get()?.apply { setBackgroundColor(color) } ?: it.remove()
            }
        }
    }

    private object StatusBarManager {

        private val activityRefs = Vector<WeakReference<Activity>>()

        fun add(activity: Activity) {
            activityRefs.add(WeakReference(activity))
            setStatusBarBackgroundColor(activity)
        }

        fun setColor(color: Int) {
            val iterator = activityRefs.iterator()
            while (iterator.hasNext()) {
                iterator.next().get()?.also { activity ->
                    ViewUtils.setStatusBarBackgroundColor(activity, color)
                } ?: iterator.remove()
            }
        }
    }

    private object PaintManager {

        private val paints: MutableList<WeakReference<Paint>> = LinkedList()

        fun add(paint: Paint) {
            paint.color = currentThemeColor.colorPrimary
            paints.add(WeakReference(paint))
        }

        fun setColor(color: Int) {
            paints.iterator().let {
                while (it.hasNext()) {
                    it.next().get()?.apply { this.color = color } ?: it.remove()
                }
            }
        }
    }

    private object ThemeColorWidgetReferenceManager {

        private val widgets: MutableList<ThemeColorMutableReference> = LinkedList()

        fun add(widget: ThemeColorMutableReference) {
            widget.setThemeColor(currentThemeColor)
            widgets.add(widget)
        }

        fun setColor(color: ThemeColor?) {
            widgets.iterator().let { i ->
                while (i.hasNext()) {
                    i.next().takeUnless { it.isNull }?.apply { setThemeColor(color) } ?: i.remove()
                }
            }
        }
    }

    private object ThemeColorMutableManager {

        private val widgets: MutableList<WeakReference<ThemeColorMutable>> = LinkedList()

        fun add(widget: ThemeColorMutable) {
            widget.setThemeColor(currentThemeColor)
            widgets.add(WeakReference(widget))
        }

        fun setColor(color: ThemeColor?) {
            widgets.iterator().let {
                while (it.hasNext()) {
                    it.next().get()?.apply { setThemeColor(color) } ?: it.remove()
                }
            }
        }
    }

}