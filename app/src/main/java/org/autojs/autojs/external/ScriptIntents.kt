package org.autojs.autojs.external

import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.model.script.PathChecker
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.json.JSONException
import org.json.JSONObject

/**
 * Created by Stardust on Apr 1, 2017.
 * Modified by SuperMonster003 as of Mar 25, 2023.
 * Transformed by SuperMonster003 on Mar 25, 2023.
 */
object ScriptIntents {

    const val EXTRA_KEY_PATH = "path"
    const val EXTRA_KEY_PRE_EXECUTE_SCRIPT = "script"
    const val EXTRA_KEY_LOOP_TIMES = "loop"
    const val EXTRA_KEY_LOOP_INTERVAL = "interval"
    const val EXTRA_KEY_DELAY = "delay"
    const val EXTRA_KEY_JSON = "json"

    data class PreparedLaunch(
        val launch: ScriptLaunchSourceFactory.Launch,
        val config: ExecutionConfig,
    )

    @JvmStatic
    fun isTaskerJsonObjectValid(json: JSONObject) = json.has(EXTRA_KEY_PATH) || json.has(EXTRA_KEY_PRE_EXECUTE_SCRIPT)

    @JvmStatic
    fun handleIntent(context: Context?, intent: Intent) {
        prepareLaunch(context, intent)?.let {
            Scripts.executeLaunch(context ?: GlobalAppContext.get(), it.launch, it.config)
        }
    }

    @JvmStatic
    @VisibleForTesting
    @JvmOverloads
    fun prepareLaunch(context: Context?, intent: Intent, argumentIntent: Intent = intent): PreparedLaunch? {
        var path = getPath(intent)
        var script = intent.getStringExtra(EXTRA_KEY_PRE_EXECUTE_SCRIPT)

        if (intent.hasExtra(EXTRA_KEY_JSON)) {
            try {
                JSONObject(intent.getStringExtra(EXTRA_KEY_JSON)!!).let {
                    path = it.optIntentString(EXTRA_KEY_PATH) ?: path
                    script = it.optIntentString(EXTRA_KEY_PRE_EXECUTE_SCRIPT) ?: script
                }
            } catch (e: JSONException) {
                /* Ignored. */
            }
        }
        val launch = when {
            path == null && script != null -> ScriptLaunchSourceFactory.createScriptLaunch(script, WorkingDirectoryUtils.path)
            path != null && PathChecker(context).checkAndToastError(path) -> {
                ScriptLaunchSourceFactory.createPathLaunch(path, preExecuteScript = script)
            }
            else -> null
        }
        return launch?.let {
            PreparedLaunch(
                launch = it,
                config = it.createIntentExecutionConfig(argumentIntent),
            )
        }
    }

    private fun getPath(intent: Intent) = intent.data?.path ?: intent.getStringExtra(EXTRA_KEY_PATH)

    private fun ScriptLaunchSourceFactory.Launch.createIntentExecutionConfig(intent: Intent): ExecutionConfig {
        return createExecutionConfig(ExecutionConfig()).apply {
            delay = intent.getLongExtra(EXTRA_KEY_DELAY, 0)
            loopTimes = intent.getIntExtra(EXTRA_KEY_LOOP_TIMES, 1)
            interval = intent.getLongExtra(EXTRA_KEY_LOOP_INTERVAL, 0)
            setArgument("intent", intent)
        }
    }

    private fun JSONObject.optIntentString(key: String): String? {
        if (!has(key) || isNull(key)) {
            return null
        }
        val value = opt(key)
        require(value is String) {
            "Script intent JSON field must be a string: $key"
        }
        return value
    }

}
