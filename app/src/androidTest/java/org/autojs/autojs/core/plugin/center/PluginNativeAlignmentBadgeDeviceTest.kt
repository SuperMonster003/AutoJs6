package org.autojs.autojs.core.plugin.center

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.nativealign.NativePageSize
import org.autojs.autojs6.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the actual recycled Plugin Center row, including the unknown metadata state. */
class PluginNativeAlignmentBadgeDeviceTest {
    @Test fun incompatibleBadgeFollowsDevicePageSizeAndClearsOnRebind() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val themed = ContextThemeWrapper(context, context.applicationInfo.theme)
            val adapter = PluginCenterItemAdapter(object : PluginCenterItemAdapter.Listener {
                override fun onInstall(item: PluginCenterItem) = Unit
                override fun onToggleEnable(item: PluginCenterItem, enabled: Boolean) = Unit
                override fun onUninstall(item: PluginCenterItem) = Unit
                override fun onDetails(item: PluginCenterItem) = Unit
                override fun onUpdate(item: PluginCenterItem) = Unit
                override fun onSettings(item: PluginCenterItem) = Unit
            })
            val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)
            val item = PluginCenterItem(title = "4 KB fixture", packageName = "org.autojs.fixture.legacy",
                versionName = "1.0", isInstalled = false, nativePageAlignment = 4096)
            val badge = holder.itemView.findViewById<View>(R.id.native_alignment_badge)
            holder.bind(item)
            assertEquals(if (NativePageSize.current() >= 16384) View.VISIBLE else View.GONE, badge.visibility)
            for (alignment in listOf(16384L, 0L, null)) {
                holder.bind(item.copy(nativePageAlignment = alignment))
                assertEquals(View.GONE, badge.visibility)
            }
            holder.recycle()
        }
    }
}
