package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.KeyEvent
import android.view.View
import org.autojs.autojs.core.accessibility.Capture
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.util.EventUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 12, 2017.
 * Modified by SuperMonster003 as of Aug 31, 2022.
 */
open class LayoutBoundsFloatyWindow @JvmOverloads constructor(
    private val capture: Capture,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(capture, context, isServiceRelied) {

    private lateinit var mLayoutBoundsView: LayoutBoundsView

    override val popMenuActions = linkedMapOf(
        R.string.text_show_widget_information to ::showNodeInfo,
        R.string.text_show_layout_hierarchy to ::showLayoutHierarchy,
        R.string.text_generate_code to ::generateCode,
        R.string.text_switch_window to ::switchWindow,
        R.string.text_exit to ::close,
    )

    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService)

        return object : LayoutBoundsView(context) {
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                EventUtils.isKeyBackAndActionUp(e) -> true.also { close() }
                EventUtils.isKeyVolumeDownAndActionDown(e) -> true.also { close() }
                else -> super.dispatchKeyEvent(e)
            }
        }.also { mLayoutBoundsView = it }
    }

    override fun onViewCreated(v: View) {
        mLayoutBoundsView.let { view ->
            view.setOnNodeInfoSelectListener { info: NodeInfo ->
                view.isFocusable = false
                setLayoutSelectedNode(info)
                getBubblePopMenu().let { menu ->
                    val width = menu.contentView.measuredWidth
                    val bounds = info.boundsInScreen
                    val x = bounds.centerX() - width / 2
                    val y = bounds.bottom - view.statusBarHeight
                    if (width <= 0) {
                        try {
                            menu.preMeasure()
                        } catch (e: Exception) {
                            /* Ignored. */
                        }
                    }
                    menu.showAsDropDownAtLocation(view, bounds.height(), x, y)
                }
                view.isFocusable = true
            }
            view.boundsPaint.strokeWidth = 2f
            view.setRootNode(capture.root)
            getLayoutSelectedNode()?.let { view.setSelectedNode(it) }
        }
    }

}