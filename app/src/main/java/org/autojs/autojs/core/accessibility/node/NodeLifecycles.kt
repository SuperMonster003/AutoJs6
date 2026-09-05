package org.autojs.autojs.core.accessibility.node

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/*
 * Android side of NodeLifecycle: the process-wide instance for the running platform and the
 * release overloads for the framework node types.
 * zh-CN: NodeLifecycle 的 Android 侧: 针对当前平台的进程级实例, 以及框架节点类型的 release 重载.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */

private const val TAG = "NodeLifecycle"

private val defaultLifecycle: NodeLifecycle by lazy {
    NodeLifecycle(NodeRecyclePolicy.forSdk(Build.VERSION.SDK_INT)) { message, error -> Log.w(TAG, message, error) }
}

/** The lifecycle for the running Android version. zh-CN: 当前 Android 版本对应的生命周期对象. */
val NodeLifecycle.Companion.default: NodeLifecycle
    get() = defaultLifecycle

@Suppress("DEPRECATION")
fun NodeLifecycle.release(node: AccessibilityNodeInfo?): Boolean = node != null && release { node.recycle() }

@Suppress("DEPRECATION")
fun NodeLifecycle.release(node: AccessibilityNodeInfoCompat?): Boolean = node != null && release { node.recycle() }

/** [NodeLifecycle.releaseAll] for compat nodes (a `UiObject` is one). zh-CN: 面向 compat 节点 (`UiObject` 即是) 的 [NodeLifecycle.releaseAll]. */
@Suppress("DEPRECATION")
fun <N : AccessibilityNodeInfoCompat> NodeLifecycle.releaseAll(nodes: Iterable<N>, except: N? = null): Int =
    releaseAll(nodes, except) { it.recycle() }
