package org.autojs.autojs.ui.floating.layoutinspector

import android.content.Context
import android.view.KeyEvent
import android.view.View
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.ui.floating.LayoutFloatyWindow
import org.autojs.autojs.util.EventUtils.isKeyBackAndActionUp
import org.autojs.autojs6.R

/**
 * Created by Stardust on 2017/3/12.
 * Modified by SuperMonster003 as of Aug 31, 2022.
 */
open class LayoutBoundsFloatyWindow @JvmOverloads constructor(
    private val rootNode: NodeInfo?,
    private val context: Context,
    isServiceRelied: Boolean = false,
) : LayoutFloatyWindow(rootNode, context, isServiceRelied) {

    private lateinit var mLayoutBoundsView: LayoutBoundsView

    override fun onCreateView(floatyService: FloatyService): View {
        onCreate(floatyService, LinkedHashMap<Int, Runnable>().apply {
            put(R.string.text_show_widget_information, Runnable { showNodeInfo() })
            put(R.string.text_show_layout_hierarchy, Runnable { showLayoutHierarchy() })
            put(R.string.text_generate_code, Runnable { generateCode() })
            put(R.string.text_exit, Runnable { close() })
        })

        return object : LayoutBoundsView(context) {
            override fun dispatchKeyEvent(e: KeyEvent) = when {
                isKeyBackAndActionUp(e) -> true.also { close() }
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
                    val y = bounds.bottom - view.mStatusBarHeight
                    if (width <= 0) {
                        try {
                            menu.preMeasure()
                        } catch (e: Exception) {
                            // Ignored.
                        }
                    }
                    menu.showAsDropDownAtLocation(view, bounds.height(), x, y)
                }
                view.isFocusable = true
            }
            view.boundsPaint.strokeWidth = 2f
            view.setRootNode(rootNode)
            getLayoutSelectedNode()?.let { view.setSelectedNode(it) }
        }
    }

}