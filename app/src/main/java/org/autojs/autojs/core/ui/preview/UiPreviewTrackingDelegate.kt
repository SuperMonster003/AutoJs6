package org.autojs.autojs.core.ui.preview

import android.view.View
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.InflateContext
import org.autojs.autojs.core.ui.inflater.LayoutInflaterDelegate
import org.autojs.autojs.core.ui.inflater.inflaters.ViewInflater
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * Wraps the ui module's own [LayoutInflaterDelegate] so a preview session learns, for every view, the
 * pre-order index of the element it came from (elements only, root = 0), which element and attribute the
 * inflater is in when an exception escapes, and gets a chance to replace views that need a window.
 *
 * The indices refer to the converted layout document the inflater parsed; the client serializes its own
 * element tree in the same order, so index n on both sides is the same element. List and grid item
 * templates are inflated later from nodes of the same document and get the template element's index.
 *
 * zh-CN: 包装 ui 模块自己的 [LayoutInflaterDelegate], 让预览会话得知每个 View 来自哪个元素 (先序号, 只数元素, 根为 0),
 * 异常逃逸时 inflater 位于哪个元素与属性, 并有机会替换需要窗口的控件. 先序号对应 inflater 解析的转换后文档;
 * 客户端以同样顺序序列化自己的元素树, 两侧的第 n 个即同一元素. 列表 / 网格的项目模板稍后从同一文档的节点 inflate, 得到模板元素的先序号.
 */
class UiPreviewTrackingDelegate(
    private val base: LayoutInflaterDelegate,
    private val tracker: Tracker,
) : LayoutInflaterDelegate by base {

    interface Tracker {
        fun onElementEnter(elementIndex: Int)
        fun onElementExit()
        fun onAttributeEnter(attrName: String)
        fun onAttributeExit()

        /** A replacement for [view] before attributes are applied, or null to keep it. */
        fun replaceCreatedView(view: View): View?

        fun onViewCreated(view: View, elementIndex: Int, id: String?)
    }

    private val indexCache = WeakHashMap<Document, IdentityHashMap<Node, Int>>()

    /** Number of elements in the document of the last element the inflater entered. */
    var lastDocumentElementCount = 0
        private set

    /** Number of elements of the document [node] belongs to; 0 without a document. */
    fun elementCount(node: Node?): Int = node?.ownerDocument?.let { indices(it).size } ?: 0

    fun elementIndex(node: Node?): Int {
        val document = node?.ownerDocument ?: return -1
        return indices(document)[node] ?: -1
    }

    override fun beforeInflateView(inflateContext: InflateContext, node: Node, parent: ViewGroup?, attachToParent: Boolean): View? {
        lastDocumentElementCount = elementCount(node)
        tracker.onElementEnter(elementIndex(node))
        return base.beforeInflateView(inflateContext, node, parent, attachToParent)
    }

    override fun afterInflateView(inflateContext: InflateContext, view: View, node: Node, parent: ViewGroup?, attachToParent: Boolean): View {
        val result = base.afterInflateView(inflateContext, view, node, parent, attachToParent)
        tracker.onElementExit()
        return result
    }

    override fun afterCreateView(inflateContext: InflateContext, view: View, node: Node?, viewName: String, parent: ViewGroup?): View {
        val effective = tracker.replaceCreatedView(view) ?: view
        val result = base.afterCreateView(inflateContext, effective, node, viewName, parent)
        tracker.onViewCreated(result, elementIndex(node), idOf(node))
        return result
    }

    override fun beforeApplyAttribute(inflateContext: InflateContext, inflater: ViewInflater<View>, view: View, ns: String?, attrName: String, value: String, parent: ViewGroup?): Boolean {
        tracker.onAttributeEnter(attrName)
        // Not in a finally block: an escaping exception leaves the attribute name in place for the failure report.
        // zh-CN: 不放在 finally 中: 异常逃逸时保留属性名, 供失败报告使用.
        val result = base.beforeApplyAttribute(inflateContext, inflater, view, ns, attrName, value, parent)
        tracker.onAttributeExit()
        return result
    }

    private fun indices(document: Document): IdentityHashMap<Node, Int> = indexCache.getOrPut(document) {
        val map = IdentityHashMap<Node, Int>()
        var next = 0
        fun walk(current: Node) {
            if (current.nodeType == Node.ELEMENT_NODE) map[current] = next++
            var child = current.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        document.documentElement?.let(::walk)
        map
    }

    private fun idOf(node: Node?): String? {
        val element = node as? Element ?: return null
        val raw = element.getAttribute("android:id").takeIf { it.isNotEmpty() } ?: element.getAttribute("id")
        return raw.removePrefix("@+id/").removePrefix("@id/").takeIf { it.isNotEmpty() }
    }
}
