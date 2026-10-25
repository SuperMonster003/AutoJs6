package org.autojs.autojs.core.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.search.FastPath

/**
 * The framework lookups of the fast path over [UiObject] trees (docs/selector/ROADMAP.md P2.3): one
 * `findAccessibilityNodeInfosByViewId` / `findAccessibilityNodeInfosByText` per root (an entry name is looked up in
 * the package of the root and in `android`), a candidate placed at its depth by walking its parents up to the root.
 * A lookup that throws counts as unavailable. Candidates are released through [NodeLifecycle.default] (API < 33).
 * zh-CN: 快路径在 [UiObject] 树上的框架查找 (P2.3): 每个根节点一次 `findAccessibilityNodeInfosByViewId` / `findAccessibilityNodeInfosByText`
 * (条目名在根节点所属包与 `android` 中查找), 候选沿父节点上溯到根节点以确定深度. 抛出异常的查找视为不可用. 候选经 [NodeLifecycle.default] 释放 (API < 33).
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
internal object UiObjectLookup : FastPath.Lookup<UiObject, AccessibilityNodeInfo> {

    /** Deeper than any real tree; a walk that goes further gives up. zh-CN: 比任何真实树都深; 超过此深度的上溯放弃. */
    private const val MAX_DEPTH = 512

    private const val FRAMEWORK_PACKAGE = "android"

    override fun candidates(root: UiObject, key: FastPath.Key): List<AccessibilityNodeInfo>? = try {
        val info = root.unwrap()
        when (key) {
            is FastPath.Key.ViewId -> info.findAccessibilityNodeInfosByViewId(key.name).orEmpty()
            is FastPath.Key.Text -> info.findAccessibilityNodeInfosByText(key.text).orEmpty()
            is FastPath.Key.IdEntry -> {
                val own = root.packageName()?.let { info.findAccessibilityNodeInfosByViewId("$it${UiSelector.ID_IDENTIFIER}${key.entry}").orEmpty() }.orEmpty()
                when (root.packageName()) {
                    FRAMEWORK_PACKAGE -> own
                    else -> own + info.findAccessibilityNodeInfosByViewId("$FRAMEWORK_PACKAGE${UiSelector.ID_IDENTIFIER}${key.entry}").orEmpty()
                }
            }
        }
    } catch (e: RuntimeException) {
        null
    }

    override fun attach(root: UiObject, candidate: AccessibilityNodeInfo): UiObject? {
        val rootInfo = root.unwrap()
        if (candidate == rootInfo) return UiObject.createDescendant(candidate, root, 0)
        var depth = 1
        var current: AccessibilityNodeInfo? = parentOf(candidate)
        while (current != null && depth <= MAX_DEPTH) {
            if (current == rootInfo) {
                NodeLifecycle.default.release(current)
                return UiObject.createDescendant(candidate, root, depth)
            }
            val next = parentOf(current)
            NodeLifecycle.default.release(current)
            current = next
            depth += 1
        }
        NodeLifecycle.default.release(current)
        return null
    }

    override fun release(candidate: AccessibilityNodeInfo) {
        NodeLifecycle.default.release(candidate)
    }

    override fun releaseNode(node: UiObject) {
        NodeLifecycle.default.release(node.unwrap())
    }

    // API 33+: the ancestors come with the first parent in one IPC, the walk then reads the client cache.
    // zh-CN: API 33+: 祖先节点随第一个父节点在一次 IPC 中取回, 之后的上溯读客户端缓存.
    private fun parentOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> node.getParent(AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS)
            else -> node.parent
        }
    } catch (e: RuntimeException) {
        null
    }

}
