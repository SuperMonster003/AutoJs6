package org.autojs.autojs.ui.edit.editor.engine

import android.content.Context
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set

object EditorEnginePreferences {

    const val KEY_EDITOR_ENGINE = "key_\$_editor_engine"
    const val KEY_EDITOR_ENGINE_SELECTION_MODE = "key_\$_editor_engine_selection_mode"
    private const val KEY_SEEN_EDITOR_SWITCH_UNDO_NOTICE = "key_\$_seen_editor_switch_undo_notice"
    private const val KEY_SEEN_ACE_FALLBACK_NOTICE = "key_\$_seen_ace_fallback_notice"
    private const val KEY_SEEN_ACE_LARGE_FILE_NOTICE = "key_\$_seen_ace_large_file_notice"

    private val initializationLock = Any()

    @Volatile
    private var initialized = false

    private val pluginStateListener: (AceEditorPluginStateMonitor.Change) -> Unit = { change ->
        reconcile(GlobalAppContext.get(), change.current.available)
    }

    data class UserSelectionResult(
        val effectiveType: EditorEngineType,
        val needsAcePlugin: Boolean,
    )

    @JvmStatic
    fun initialize(context: Context) {
        if (!initialized) {
            synchronized(initializationLock) {
                if (!initialized) {
                    val appContext = context.applicationContext
                    val state = AceEditorPluginStateMonitor.initialize(appContext)
                    AceEditorPluginStateMonitor.addListener(appContext, pluginStateListener)
                    initialized = true
                    reconcile(appContext, state.available)
                    return
                }
            }
        }
        reconcile(context.applicationContext, AceEditorPluginStateMonitor.current(context).available)
    }

    @JvmStatic
    fun get(): EditorEngineType {
        initialize(GlobalAppContext.get())
        return readEffectiveType()
    }

    @JvmStatic
    fun selectByUser(context: Context, type: EditorEngineType): UserSelectionResult {
        initialize(context)
        val available = AceEditorPluginStateMonitor.current(context).available
        val decision = EditorEngineSelectionPolicy.selectByUser(
            currentMode = readMode(),
            selectedType = type,
            aceAvailable = available,
        )
        persist(decision.mode, decision.effectiveType)
        return UserSelectionResult(decision.effectiveType, decision.needsAcePlugin)
    }

    /** Direct intent override retained for instrumentation and compatibility callers. */
    @JvmStatic
    fun set(type: EditorEngineType) {
        val context = GlobalAppContext.get()
        initialize(context)
        val mode = when (type) {
            EditorEngineType.ACE -> EditorEngineSelectionPolicy.Mode.AUTO_ACE
            EditorEngineType.NATIVE -> EditorEngineSelectionPolicy.Mode.NATIVE_EXPLICIT
        }
        persist(
            mode,
            EditorEngineSelectionPolicy.effectiveType(
                mode,
                AceEditorPluginStateMonitor.current(context).available,
            ),
        )
    }

    fun reconcile(context: Context = GlobalAppContext.get()): EditorEngineType {
        initialize(context)
        return readEffectiveType()
    }

    fun consumeEditorSwitchUndoNotice(): Boolean {
        return consumeOnce(KEY_SEEN_EDITOR_SWITCH_UNDO_NOTICE)
    }

    fun consumeAceFallbackNotice(): Boolean {
        return consumeOnce(KEY_SEEN_ACE_FALLBACK_NOTICE)
    }

    fun consumeAceLargeFileNotice(): Boolean {
        return consumeOnce(KEY_SEEN_ACE_LARGE_FILE_NOTICE)
    }

    private fun reconcile(@Suppress("UNUSED_PARAMETER") context: Context, aceAvailable: Boolean): EditorEngineType {
        val mode = readMode()
        val decision = EditorEngineSelectionPolicy.reconcile(mode, aceAvailable)
        persist(decision.mode, decision.effectiveType)
        return decision.effectiveType
    }

    private fun readEffectiveType(): EditorEngineType = EditorEngineType.fromPreferenceValue(
        EditorKeys.EDITOR_ENGINE.storedKeyOrDefault(),
    )

    private fun readMode(): EditorEngineSelectionPolicy.Mode {
        val stored = EditorEngineSelectionPolicy.Mode.fromPreferenceValue(
            EditorKeys.EDITOR_ENGINE_SELECTION_MODE.storedOrNull(),
        )
        if (stored != null) return stored
        val rawEngineValue = EditorKeys.EDITOR_ENGINE.storedKey()
        return EditorEngineSelectionPolicy.migrateMode(rawEngineValue)
    }

    private fun persist(
        mode: EditorEngineSelectionPolicy.Mode,
        effectiveType: EditorEngineType,
    ) {
        val modeChanged = EditorKeys.EDITOR_ENGINE_SELECTION_MODE.storedOrNull() != mode.preferenceValue
        val engineChanged = EditorKeys.EDITOR_ENGINE.storedKey() != effectiveType.preferenceValue
        if (!modeChanged && !engineChanged) return
        // One write for both keys, as before.
        // zh-CN: 两个键一次写入, 与原先相同.
        SettingsStores.default.edit {
            this[EditorKeys.EDITOR_ENGINE_SELECTION_MODE] = mode.preferenceValue
            this[EditorKeys.EDITOR_ENGINE] = effectiveType.preferenceValue
        }
    }

    private fun consumeOnce(key: String): Boolean {
        if (Pref.getBoolean(key, false)) {
            return false
        }
        Pref.putBoolean(key, true)
        return true
    }
}
