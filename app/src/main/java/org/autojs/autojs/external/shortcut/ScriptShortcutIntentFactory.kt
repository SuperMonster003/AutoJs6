package org.autojs.autojs.external.shortcut

import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.external.ScriptIntents

/** Thin Android adapter for the versioned, Android-free shortcut launch descriptor policy. */
object ScriptShortcutIntentFactory {

    const val EXTRA_DESCRIPTOR_SCHEMA = "autojs6.shortcut.launch.schema"
    const val EXTRA_DESCRIPTOR_ENGINE = "autojs6.shortcut.launch.engine"
    const val EXTRA_DESCRIPTOR_SOURCE_KIND = "autojs6.shortcut.launch.sourceKind"

    private val descriptorKeys = arrayOf(
        EXTRA_DESCRIPTOR_SCHEMA,
        EXTRA_DESCRIPTOR_ENGINE,
        EXTRA_DESCRIPTOR_SOURCE_KIND,
    )

    /** Creates path-only legacy intents for other languages and V1 descriptors for Lua files. */
    @JvmStatic
    fun create(path: String): Intent {
        val intent = Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, path)
        val descriptor = ScriptShortcutLaunchDescriptor.forPath(path) ?: return intent
        return intent
            .putExtra(EXTRA_DESCRIPTOR_SCHEMA, descriptor.schema)
            .putExtra(EXTRA_DESCRIPTOR_ENGINE, descriptor.engine)
            .putExtra(EXTRA_DESCRIPTOR_SOURCE_KIND, descriptor.sourceKind)
    }

    /**
     * Metadata is only an assertion about the route selected from [path]. It never selects an
     * engine, provider, component, or capability. Completely absent metadata remains legacy.
     */
    @JvmStatic
    fun isCompatible(intent: Intent, path: String, actualEngine: String): Boolean {
        val descriptorPresent = try {
            descriptorKeys.any(intent::hasExtra)
        } catch (_: RuntimeException) {
            return false
        }
        val extras = try {
            intent.extras
        } catch (_: RuntimeException) {
            return false
        }
        return ScriptShortcutLaunchDescriptor.evaluate(
            descriptorPresent = descriptorPresent,
            schema = extras.safeGet(EXTRA_DESCRIPTOR_SCHEMA),
            engine = extras.safeGet(EXTRA_DESCRIPTOR_ENGINE),
            sourceKind = extras.safeGet(EXTRA_DESCRIPTOR_SOURCE_KIND),
            path = path,
            actualEngine = actualEngine,
        ) != ScriptShortcutLaunchDescriptor.Evaluation.REJECTED
    }

    @Suppress("DEPRECATION")
    private fun Bundle?.safeGet(key: String): Any? = try {
        this?.get(key)
    } catch (_: RuntimeException) {
        null
    }
}
