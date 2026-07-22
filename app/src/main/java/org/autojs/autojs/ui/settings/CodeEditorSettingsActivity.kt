package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R

class CodeEditorSettingsActivity : PreferencesActivity() {

    private var lastAcePluginState: AceEditorPluginStateMonitor.State? = null
    private val acePluginStateListener: (AceEditorPluginStateMonitor.Change) -> Unit = { change ->
        val wasAvailable = lastAcePluginState?.available == true || change.previous?.available == true
        lastAcePluginState = change.current
        if (wasAvailable && !change.current.available && !isFinishing && !isDestroyed) {
            AceEditorPluginUi.showUnavailableToastOnce(this, change)
            finish()
        }
    }

    override val titleRes: Int = R.string.text_code_editor_settings

    override fun createPreferencesFragment(): Fragment = CodeEditorSettingsFragment.newInstance()

    override fun onStart() {
        super.onStart()
        val current = AceEditorPluginStateMonitor.current(this)
        val previous = lastAcePluginState
        lastAcePluginState = current
        if (previous?.available == true && !current.available) {
            val change = AceEditorPluginStateMonitor.latestChange(this)
            AceEditorPluginUi.showUnavailableToastOnce(this, change)
            finish()
            return
        }
        AceEditorPluginStateMonitor.addListener(this, acePluginStateListener)
    }

    override fun onStop() {
        AceEditorPluginStateMonitor.removeListener(acePluginStateListener)
        super.onStop()
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun launch(context: Context = GlobalAppContext.get()) {
            Intent(context, CodeEditorSettingsActivity::class.java)
                .startSafely(context)
        }
    }
}
