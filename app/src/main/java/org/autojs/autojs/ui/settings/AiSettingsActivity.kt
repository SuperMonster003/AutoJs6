package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

class AiSettingsActivity : PreferencesActivity() {

    override val titleRes: Int = R.string.text_ai_settings

    override fun createPreferencesFragment(): Fragment = AiSettingsFragment()

    companion object {

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context = GlobalAppContext.get()) {
            Intent(context, AiSettingsActivity::class.java).startSafely(context)
        }
    }
}
