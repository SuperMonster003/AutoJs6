package org.autojs.autojs.core.pref.fleet

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Applies a fleet/headless configuration profile to AutoJs6's default SharedPreferences.
 *
 * Profiles are JSON objects mapping preference keys to typed values. Supported types:
 * - boolean  -> putBoolean
 * - string   -> putString
 * - int      -> putInt
 * - long     -> putLong
 * - float    -> putFloat
 * - string[] -> putStringSet
 *
 * Known preference keys are exported in R.string.key_* and resolve to keys like
 * "key_$_foreground_service". The profile can use either the raw key string or
 * a short alias from the key alias table (e.g. "foreground_service").
 *
 * For ListPreference-style keys, the value can be either the internal key string
 * (e.g. "key_$_timed_task_backend_alarm") or a human-readable alias (e.g. "alarm").
 *
 * Example profile:
 * {
 *   "foreground_service": true,
 *   "floating_menu_shown": false,
 *   "enable_a11y_service_with_secure_settings": true,
 *   "stable_mode": true,
 *   "guard_mode": true,
 *   "restart_strategy": "quick",
 *   "timed_task_backend": "alarm",
 *   "file_extensions": "show_all"
 * }
 */
object FleetProfileApplier {

    private const val KEY_PREFIX = "key_\$_"

    private val aliasToKey by lazy {
        mapOf(
            "foreground_service" to key(R.string.key_foreground_service),
            "floating_menu_shown" to key(R.string.key_floating_menu_shown),
            "a11y_service" to key(R.string.key_a11y_service),
            "enable_a11y_service_with_root_access" to key(R.string.key_enable_a11y_service_with_root_access),
            "enable_a11y_service_with_secure_settings" to key(R.string.key_enable_a11y_service_with_secure_settings),
            "stable_mode" to key(R.string.key_stable_mode),
            "guard_mode" to key(R.string.key_guard_mode),
            "use_volume_control_running" to key(R.string.key_use_volume_control_running),
            "use_volume_control_record" to key(R.string.key_use_volume_control_record),
            "record_toast" to key(R.string.key_record_toast),
            "extending_js_build_in_objects" to key(R.string.key_extending_js_build_in_objects),
            "rhino_java_primitive_wrap" to key(R.string.key_rhino_java_primitive_wrap),
            "auto_check_for_updates" to key(R.string.key_auto_check_for_updates),
            "post_notifications_permission" to key(R.string.key_post_notifications_permission),
            "display_over_other_apps" to key(R.string.key_display_over_other_apps),
            "all_files_access" to key(R.string.key_all_files_access),
            "root_mode" to key(R.string.key_root_mode),
            "restart_strategy" to key(R.string.key_restart_strategy),
            "timed_task_backend" to key(R.string.key_timed_task_backend),
            "scheduled_restart_backend" to key(R.string.key_scheduled_restart_backend),
            "night_mode" to key(R.string.key_night_mode),
            "app_language" to key(R.string.key_app_language),
            "theme_color" to key(R.string.key_theme_color),
            "editor_theme" to key(R.string.key_editor_theme),
            "editor_text_size" to key(R.string.key_editor_text_size),
            "screen_capture_request_delay" to key(R.string.key_screen_capture_request_delay),
            "file_extensions" to key(R.string.key_file_extensions),
            "hidden_files" to key(R.string.key_hidden_files),
            "working_directory" to key(R.string.key_working_directory),
            "documentation_source" to key(R.string.key_documentation_source),
            "server_address" to key(R.string.key_server_address),
            "client_socket_normally_closed" to key(R.string.key_client_socket_normally_closed),
            "server_socket_normally_closed" to key(R.string.key_server_socket_normally_closed),
            "gesture_observing" to key(R.string.key_gesture_observing),
            "launcher_icon" to key(R.string.key_launcher_icon),
            "keep_screen_on_when_in_foreground" to key(R.string.key_keep_screen_on_when_in_foreground),
        )
    }

    /**
     * For ListPreference-style keys, the stored value must be one of the internal
     * key strings (e.g. "key_$_timed_task_backend_alarm"). Fleet profiles are meant
     * to be human-readable, so we also accept short aliases like "alarm" and map
     * them to the real key strings.
     */
    private val valueAliasToKey by lazy {
        mapOf(
            key(R.string.key_timed_task_backend) to mapOf(
                "alarm" to key(R.string.key_timed_task_backend_alarm),
                "work" to key(R.string.key_timed_task_backend_work),
                "job" to key(R.string.key_timed_task_backend_job),
            ),
            key(R.string.key_scheduled_restart_backend) to mapOf(
                "alarm_manager" to key(R.string.key_scheduled_restart_backend_alarm_manager),
                "work_manager" to key(R.string.key_scheduled_restart_backend_work_manager),
            ),
            key(R.string.key_restart_strategy) to mapOf(
                "quick" to key(R.string.key_restart_strategy_quick),
                "scheduled" to key(R.string.key_restart_strategy_scheduled),
            ),
            key(R.string.key_night_mode) to mapOf(
                "follow_system" to key(R.string.key_night_mode_follow_system),
                "always_on" to key(R.string.key_night_mode_always_on),
                "always_off" to key(R.string.key_night_mode_always_off),
            ),
            key(R.string.key_keep_screen_on_when_in_foreground) to mapOf(
                "off" to key(R.string.key_keep_screen_on_when_in_foreground_disabled),
                "disabled" to key(R.string.key_keep_screen_on_when_in_foreground_disabled),
                "all_pages" to key(R.string.key_keep_screen_on_when_in_foreground_all_pages),
                "homepage_only" to key(R.string.key_keep_screen_on_when_in_foreground_homepage_only),
            ),
            key(R.string.key_root_mode) to mapOf(
                "auto_detect" to key(R.string.key_root_mode_auto_detect),
                "force_root" to key(R.string.key_root_mode_force_root),
                "force_non_root" to key(R.string.key_root_mode_force_non_root),
            ),
            key(R.string.key_hidden_files) to mapOf(
                "show" to key(R.string.key_hidden_files_show),
                "not_show" to key(R.string.key_hidden_files_not_show),
            ),
            key(R.string.key_file_extensions) to mapOf(
                "show_all" to key(R.string.key_file_extensions_show_all),
                "not_show" to key(R.string.key_file_extensions_not_show),
                "show_all_but_executable" to key(R.string.key_file_extensions_show_all_but_executable),
            ),
            key(R.string.key_documentation_source) to mapOf(
                "local" to key(R.string.key_documentation_source_local),
                "online" to key(R.string.key_documentation_source_online),
            ),
            key(R.string.key_app_language) to mapOf(
                "auto" to key(R.string.key_app_language_auto),
                "zh_hans" to key(R.string.key_app_language_zh_hans),
                "zh_hant_hk" to key(R.string.key_app_language_zh_hant_hk),
                "zh_hant_tw" to key(R.string.key_app_language_zh_hant_tw),
                "en" to key(R.string.key_app_language_en),
                "fr" to key(R.string.key_app_language_fr),
                "es" to key(R.string.key_app_language_es),
                "ja" to key(R.string.key_app_language_ja),
                "ko" to key(R.string.key_app_language_ko),
                "ru" to key(R.string.key_app_language_ru),
                "ar" to key(R.string.key_app_language_ar),
            ),
            key(R.string.key_launcher_icon) to mapOf(
                "adaptive" to key(R.string.key_launcher_icon_adaptive),
                "transparent_background" to key(R.string.key_launcher_icon_transparent_background),
            ),
            key(R.string.key_editor_pinch_to_zoom_strategy) to mapOf(
                "change_text_size" to key(R.string.key_editor_pinch_to_zoom_change_text_size),
                "scale_view" to key(R.string.key_editor_pinch_to_zoom_scale_view),
                "disable" to key(R.string.key_editor_pinch_to_zoom_disable),
            ),
            key(R.string.key_root_record_out_file_type) to mapOf(
                "binary" to key(R.string.key_root_record_out_file_type_binary),
                "js" to key(R.string.key_root_record_out_file_type_js),
            ),
        )
    }

    data class Result(
        val success: Boolean,
        val appliedCount: Int,
        val skippedCount: Int,
        val appliedKeys: List<String>,
        val failedKeys: List<String>,
        val errors: List<String>,
        val message: String,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("success", this@Result.success)
            put("applied_count", this@Result.appliedCount)
            put("skipped_count", this@Result.skippedCount)
            put("applied_keys", JSONArray(this@Result.appliedKeys))
            put("failed_keys", JSONArray(this@Result.failedKeys))
            put("errors", JSONArray(this@Result.errors))
            put("message", this@Result.message)
        }

        fun toLogLine(): String {
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
            val level = when {
                success -> "INFO "
                appliedCount > 0 -> "WARN "
                else -> "ERROR"
            }
            val sb = StringBuilder().apply {
                append(ts).append(' ').append(level).append(" fleet_profile: applied=").append(appliedCount)
                append(" skipped=").append(skippedCount)
                if (appliedKeys.isNotEmpty()) append(" keys=").append(appliedKeys.joinToString(","))
                if (failedKeys.isNotEmpty()) append(" failed=").append(failedKeys.joinToString(","))
                append(" errors=").append(errors.size)
                append(" message=").append(quote(message))
            }
            return sb.toString()
        }

        private fun quote(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    /**
     * Apply a fleet profile from a JSON string, using the default SharedPreferences.
     */
    @JvmStatic
    fun applyJson(context: Context, json: String): Result {
        return try {
            val profile = JSONObject(json)
            applyProfile(Pref.get(), profile)
        } catch (e: Exception) {
            Result(
                success = false, appliedCount = 0, skippedCount = 0,
                appliedKeys = emptyList(), failedKeys = emptyList(),
                errors = listOf(e.message ?: "Invalid JSON"),
                message = "Profile parse failed: ${e.message}"
            )
        }
    }

    /**
     * Apply a fleet profile from a JSON object to the given SharedPreferences.
     * Visible for testing, so callers can provide a dedicated SharedPreferences instance.
     */
    @VisibleForTesting
    @JvmStatic
    fun applyJson(context: Context, json: String, prefs: SharedPreferences): Result {
        return try {
            applyProfile(prefs, JSONObject(json))
        } catch (e: Exception) {
            Result(
                success = false, appliedCount = 0, skippedCount = 0,
                appliedKeys = emptyList(), failedKeys = emptyList(),
                errors = listOf(e.message ?: "Invalid JSON"),
                message = "Profile parse failed: ${e.message}"
            )
        }
    }

    /**
     * Apply a fleet profile read from a local file path.
     */
    @JvmStatic
    fun applyFromPath(context: Context, path: String): Result {
        return try {
            val json = File(path).readText(Charsets.UTF_8)
            applyJson(context, json)
        } catch (e: Exception) {
            Result(
                success = false, appliedCount = 0, skippedCount = 0,
                appliedKeys = emptyList(), failedKeys = emptyList(),
                errors = listOf(e.message ?: "Read error"),
                message = "Failed to read $path: ${e.message}"
            )
        }
    }

    /**
     * Apply a fleet profile read from a content URI.
     */
    @JvmStatic
    fun applyFromUri(context: Context, uri: Uri): Result {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result(
                    success = false, appliedCount = 0, skippedCount = 0,
                    appliedKeys = emptyList(), failedKeys = emptyList(),
                    errors = listOf("Cannot open URI"),
                    message = "Cannot open URI: $uri"
                )
            val json = stream.use { it.reader(Charsets.UTF_8).readText() }
            applyJson(context, json)
        } catch (e: Exception) {
            Result(
                success = false, appliedCount = 0, skippedCount = 0,
                appliedKeys = emptyList(), failedKeys = emptyList(),
                errors = listOf(e.message ?: "URI error"),
                message = "Failed to read URI $uri: ${e.message}"
            )
        }
    }

    private fun applyProfile(prefs: SharedPreferences, profile: JSONObject): Result {
        val errors = mutableListOf<String>()
        val appliedKeys = mutableListOf<String>()
        val failedKeys = mutableListOf<String>()
        val meta = profile.optJSONObject("_meta")
        // clear_existing wipes ALL SharedPreferences before applying. Use with extreme care.
        val clearExisting = meta?.optBoolean("clear_existing", false) ?: false
        if (clearExisting) {
            Log.w("FleetProfile", "clear_existing is true — wiping ALL preferences before applying")
        }

        prefs.edit(commit = true) {
            if (clearExisting) {
                clear()
            }

            val keys = profile.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                if (rawKey.startsWith("_")) {
                    continue
                }
                val prefKey = resolveKey(rawKey)
                if (prefKey == null) {
                    failedKeys.add(rawKey)
                    errors.add("Unknown key: $rawKey")
                    continue
                }

                val value = profile.get(rawKey)
                try {
                    val resolvedValue = resolveValue(prefKey, value)
                    putValue(this, prefKey, resolvedValue)
                    appliedKeys.add(rawKey)
                } catch (e: Exception) {
                    failedKeys.add(rawKey)
                    errors.add("$rawKey: ${e.message}")
                }
            }
        }

        val message = "Applied ${appliedKeys.size} preferences, skipped ${failedKeys.size}" +
                if (errors.isEmpty()) "" else " (${errors.size} errors)"

        return Result(
            success = errors.isEmpty(),
            appliedCount = appliedKeys.size,
            skippedCount = failedKeys.size,
            appliedKeys = appliedKeys,
            failedKeys = failedKeys,
            errors = errors,
            message = message,
        )
    }

    private fun resolveKey(rawKey: String): String? {
        if (rawKey.startsWith(KEY_PREFIX)) {
            return rawKey
        }
        return aliasToKey[rawKey]
    }

    private fun resolveValue(prefKey: String, value: Any?): Any? {
        if (value !is String) {
            return value
        }
        val valueAliases = valueAliasToKey[prefKey] ?: return value
        return valueAliases[value] ?: run {
            Log.w("FleetProfile", "Unrecognized value '$value' for key '$prefKey'; passing through as-is")
            value
        }
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is Float -> editor.putFloat(key, value)
            is JSONArray -> {
                val set = LinkedHashSet<String>()
                for (i in 0 until value.length()) {
                    set.add(value.getString(i))
                }
                editor.putStringSet(key, set)
            }
            else -> throw IllegalArgumentException("Unsupported type: ${value?.javaClass?.simpleName}")
        }
    }

}
