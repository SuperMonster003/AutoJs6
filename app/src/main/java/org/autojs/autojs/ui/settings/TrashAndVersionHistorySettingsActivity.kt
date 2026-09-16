package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

/**
 * Settings sub-page for the retention policies of trash, version history and emergency drafts.
 * zh-CN: 回收站, 版本历史与紧急草稿保留策略的设置子页.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
class TrashAndVersionHistorySettingsActivity : PreferencesActivity() {

    override val titleRes: Int = R.string.text_trash_and_version_history

    override fun createPreferencesFragment(): Fragment = TrashAndVersionHistorySettingsFragment()

    companion object {

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context = GlobalAppContext.get()) {
            Intent(context, TrashAndVersionHistorySettingsActivity::class.java)
                .startSafely(context)
        }
    }
}
