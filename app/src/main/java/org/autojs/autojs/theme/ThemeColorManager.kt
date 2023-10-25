package org.autojs.autojs.theme

import android.app.Activity
import android.graphics.Paint
import android.view.View
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import java.lang.ref.WeakReference
import java.util.LinkedList
import java.util.Vector

/**
 * Created by Stardust on 2016/5/10.
 */
object ThemeColorManager {

    @JvmField
    var defaultThemeColor = ThemeColor(ColorUtils.fromResources(R.color.theme_color_default))

    @JvmStatic
    lateinit var currentThemeColor: ThemeColor

    @JvmStatic
    val colorPrimary: Int
        get() = currentThemeColor.colorPrimary

    init {
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
                activity.window.navigationBarColor = color.colorPrimary
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

    /**
     * 设置主题色并为记录的状态栏和标题栏改变颜色
     * @param color 主题色RGB值
     */
    fun setThemeColor(color: Int) = setThemeColor(ThemeColor(color))

    private fun saveThemeColorIfNeeded() {
        currentThemeColor.saveIn()
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

        private val activities = Vector<WeakReference<Activity>>()

        fun add(activity: Activity) {
            activities.add(WeakReference(activity))
            activity.window.statusBarColor = currentThemeColor.colorPrimary
        }

        fun setColor(color: Int) {
            activities.iterator().let {
                while (it.hasNext()) {
                    it.next().get()?.apply { window.statusBarColor = color } ?: it.remove()
                }
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