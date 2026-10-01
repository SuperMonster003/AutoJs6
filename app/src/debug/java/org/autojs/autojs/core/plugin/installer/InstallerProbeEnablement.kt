package org.autojs.autojs.core.plugin.installer

import android.content.Context
import android.os.Process
import android.util.AtomicFile
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import java.io.File

/** Debug-only transaction for exactly one existing production plugin-enable preference. */
internal object InstallerProbeEnablement {
    private const val TARGET = "io.github.supermonster003.autojs6.plugin.three.setup.installer"
    private const val STORE = "plugin_center_enable_state"
    private const val KEY = "key_\$_enabled_plugin_\$_$TARGET"
    private const val JOURNAL = "enable-restore.json"

    @Synchronized fun prepare(context: Context, directory: File) {
        check(PluginTrustManager.isAuthorized(context, TARGET)) { "The probe cannot authorize an untrusted plugin" }
        check(listOf("", ".bak", ".new").none { File(directory, JOURNAL + it).exists() }) { "Restore an existing probe journal before retrying" }
        // A killed caller must not allow a new case to overwrite its original preference.
        requireNotNull(directory.parentFile).listFiles().orEmpty().filter { it.isDirectory && it != directory }.forEach { other ->
            val journal = File(other, JOURNAL)
            if (journal.exists() || File(journal.path + ".bak").exists()) {
                check(!read(journal).get("pending").asBoolean) { "An earlier probe still needs enablement restoration: ${other.name}" }
            }
        }
        val fingerprints = fingerprints(context)
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val hadKey = prefs.contains(KEY)
        val original = prefs.getBoolean(KEY, true)
        val journal = JsonObject().apply {
            addProperty("caseId", directory.name)
            addProperty("hostUid", Process.myUid())
            addProperty("targetPackage", TARGET)
            addProperty("originalPresent", hadKey)
            addProperty("originalEnabled", original)
            addProperty("pending", true)
            addProperty("phase", "preparing")
            add("fingerprints", JsonArray().apply { fingerprints.forEach(::add) })
        }
        write(File(directory, JOURNAL), journal)
        PluginEnableStore.setEnabled(context, TARGET, true)
        check(PluginEnableStore.isEnabled(context, TARGET)) { "The production enablement policy rejected the test plugin" }
        // setEnabled uses apply(). Commit only this same key to establish the durable test barrier.
        check(prefs.edit().putBoolean(KEY, true).commit()) { "Cannot durably prepare plugin enablement" }
        check(PluginEnableStore.isEnabled(context, TARGET) && prefs.getBoolean(KEY, false))
        journal.addProperty("preparedEnabled", true)
        journal.addProperty("phase", "prepared")
        write(File(directory, JOURNAL), journal)
    }

    @Synchronized fun restore(context: Context, directory: File) {
        val file = File(directory, JOURNAL)
        val journal = read(file)
        check(journal["caseId"].asString == directory.name && journal["hostUid"].asInt == Process.myUid() &&
            journal["targetPackage"].asString == TARGET)
        check(journal["fingerprints"].asJsonArray.map { it.asString } == fingerprints(context)) { "The plugin signing identity changed" }
        val originalPresent = journal["originalPresent"].asBoolean
        val originalEnabled = journal["originalEnabled"].asBoolean
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        fun restored() = prefs.contains(KEY) == originalPresent && prefs.getBoolean(KEY, true) == originalEnabled
        if (!journal["pending"].asBoolean) {
            check(journal["phase"].asString == "restored" && restored()) { "An already restored preference was subsequently changed" }
            return
        }
        // Allow an idempotent restore after a crash between preference commit and journal update.
        check(restored() || (prefs.contains(KEY) && prefs.getBoolean(KEY, false))) { "The temporary enable key was changed externally" }
        val editor = prefs.edit()
        if (originalPresent) editor.putBoolean(KEY, originalEnabled) else editor.remove(KEY)
        check(editor.commit() && restored()) { "Cannot restore the original plugin enable key" }
        PluginStateChangeRegistry.notifyChanged(TARGET)
        journal.addProperty("restoredPresent", prefs.contains(KEY))
        journal.addProperty("restoredEnabled", prefs.getBoolean(KEY, true))
        journal.addProperty("pending", false)
        journal.addProperty("phase", "restored")
        write(file, journal)
    }

    private fun fingerprints(context: Context): List<String> = PluginSignatureUtils.getSha256Fingerprints(context, TARGET).distinct().sorted().also {
        check(it.isNotEmpty() && it.all { fingerprint -> fingerprint.matches(Regex("[0-9a-f]{64}")) })
    }

    private fun read(file: File): JsonObject {
        val bytes = AtomicFile(file).openRead().use { input -> check(input.channel.size() in 1..16_384); input.readBytes() }
        return JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
    }

    private fun write(file: File, value: JsonObject) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(value.toString().toByteArray(Charsets.UTF_8)); output.fd.sync(); atomic.finishWrite(output) }
        catch (failure: Exception) { atomic.failWrite(output); throw failure }
    }
}
