package org.autojs.autojs.runtime.api.tts

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech

/** Package discovery does not bind or initialize an engine that may be broken or unavailable. */
internal object TtsEngineDirectory {
    data class Entry(val name: String, val label: String, val system: Boolean, val priority: Int)

    fun list(context: Context, requested: String?): List<TtsEngine> {
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val matches = manager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_DEFAULT_ONLY)
        val entries = matches.mapNotNull { match ->
            val info = match.serviceInfo ?: return@mapNotNull null
            if (!info.enabled || !info.applicationInfo.enabled) return@mapNotNull null
            Entry(info.packageName, match.loadLabel(manager)?.toString().orEmpty().ifBlank { info.packageName },
                info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0, match.priority)
        }
        return select(entries, Settings.Secure.getString(context.contentResolver, "tts_default_synth"), requested)
    }

    /** Android ranks system engines before third-party engines, then sorts by service priority. */
    fun select(entries: List<Entry>, configuredDefault: String?, requested: String?): List<TtsEngine> {
        val sorted = entries.sortedWith(compareByDescending<Entry> { it.system }.thenByDescending { it.priority }).distinctBy { it.name }
        val default = configuredDefault?.takeIf { value -> sorted.any { it.name == value } }
            ?: sorted.firstOrNull { it.system }?.name
        return sorted.map { TtsEngine(it.name, it.label, it.name == default, it.name == requested) }
    }
}
