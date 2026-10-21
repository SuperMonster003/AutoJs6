package org.autojs.autojs.engine

import android.view.accessibility.AccessibilityWindowInfo
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.activity.ActivityWindowIdentity

/** A newly bound service need not replay the Activity's earlier window event. */
internal object RuntimeFlowForeground {
    fun read(): ActivityWindowIdentity {
        val autoJs = AutoJs.instance
        val recorded = autoJs.infoProvider.accessibilityIdentity
        val provider = WindowRootProvider.default
        val root = provider.currentRoot(A11yWindowRootPolicy(filter = {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused
        })) ?: provider.activeRoot(A11yWindowRootPolicy())
        val windowPackage = try { root?.packageName?.toString() } finally { NodeLifecycle.default.release(root) }
        val activity = autoJs.appUtils.currentActivity?.takeIf { !it.isFinishing && it.hasWindowFocus() }
        val owned = activity?.componentName?.let { ActivityWindowIdentity(it.packageName, it.className) }
        return recorded.foreground(windowPackage, owned)
    }
}
