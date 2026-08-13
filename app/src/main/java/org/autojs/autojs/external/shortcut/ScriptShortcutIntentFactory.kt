package org.autojs.autojs.external.shortcut

import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.script.ScriptLaunchSourceFactory

/** Thin Android adapter for the versioned, Android-free shortcut launch descriptor policy. */
object ScriptShortcutIntentFactory {

    data class DirectPathLaunch(
        val path: String,
        val launch: ScriptLaunchSourceFactory.Launch,
        val config: ExecutionConfig,
    )

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
     * Accepts only the historical direct-path shortcut shape. In particular, the exported
     * shortcut activity must not become a generic URI, JSON, inline-script, or pre-execute
     * launch endpoint.
     */
    @JvmStatic
    fun prepareDirectPathLaunch(context: Context, intent: Intent): DirectPathLaunch? {
        val path = try {
            ScriptShortcutDirectPathPolicy.requireDirectPath(
                hasData = intent.data != null,
                hasJsonExtra = intent.hasExtra(ScriptIntents.EXTRA_KEY_JSON),
                hasPreExecuteExtra = intent.hasExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT),
                path = intent.getStringExtra(ScriptIntents.EXTRA_KEY_PATH),
            )
        } catch (failure: ScriptShortcutDirectPathException) {
            throw failure
        } catch (failure: RuntimeException) {
            throw ScriptShortcutDirectPathException(
                ScriptShortcutDirectPathError.MALFORMED_PAYLOAD,
                failure,
            )
        }
        val prepared = ScriptIntents.prepareLaunch(context, intent) ?: return null
        return DirectPathLaunch(
            path = path,
            launch = prepared.launch,
            config = prepared.config,
        )
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
