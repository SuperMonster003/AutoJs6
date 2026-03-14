package org.autojs.autojs.project

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.autojs.autojs.annotation.SerializedNameCompatible.With
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R
import java.util.LinkedHashMap

/**
 * Created by Stardust on Jan 25, 2018.
 * Created by SuperMonster003 on Jan 15, 2025.
 * Modified by SuperMonster003 as of Mar 8, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
class LaunchConfig : FuzzyDeserializer.OriginalJsonKeyAware {

    @Transient
    private val mContext = GlobalAppContext.get()
    @Transient
    private val mOriginalJsonKeys = LinkedHashMap<String, String>()

    @SerializedName("logsVisible")
    @field:SerializedNameCompatible(
        With(value = "showLogs"),
        With(value = "hideLogs", target = ["AutoJs4", "AutoX"], isReversed = true),
        With(value = "displayLogs"),
    )
    var isLogsVisible = true

    @SerializedName("splashVisible")
    @field:SerializedNameCompatible(
        With(value = "showSplash"),
        With(value = "hideSplash", isReversed = true),
        With(value = "displaySplash", target = ["AutoX"]),
    )
    var isSplashVisible = true

    @SerializedName("launcherVisible")
    @field:SerializedNameCompatible(
        With(value = "showLauncher"),
        With(value = "hideLauncher", target = ["AutoX"], isReversed = true),
        With(value = "displayLauncher"),
    )
    var isLauncherVisible = true

    @SerializedName("runOnBoot")
    @field:SerializedNameCompatible(
        With(value = "bootRun"),
        With(value = "autoRunOnBoot"),
        With(value = "bootAutoStart"),
        With(value = "autoStartOnBoot"),
    )
    var isRunOnBoot = false

    @SerializedName("slug")
    @field:SerializedNameCompatible(
        With(value = "slugText"),
        With(value = "splashText", target = ["AutoX"]),
    )
    var slug = mContext.getString(R.string.text_powered_by_autojs)

    // @SerializedName("permissions")
    // @field:SerializedNameCompatible(
    //     With(value = "permission"),
    //     With(value = "permissionList"),
    // )
    // var permissions = emptyList<String>()

    override fun recordOriginalJsonKey(canonicalKey: String, originalKey: String) {
        mOriginalJsonKeys[canonicalKey] = originalKey
    }

    fun applyOriginalJsonKeys(json: JsonObject, detectConflicts: Boolean) {
        mOriginalJsonKeys.forEach { (canonicalKey, originalKey) ->
            if (canonicalKey == originalKey) {
                return@forEach
            }
            if (!json.has(canonicalKey)) {
                return@forEach
            }
            if (json.has(originalKey)) {
                if (detectConflicts) {
                    throw IllegalStateException("Conflicting keys when serializing launchConfig: \"$canonicalKey\" and \"$originalKey\"")
                }
                json.remove(canonicalKey)
                return@forEach
            }
            val value = json.remove(canonicalKey)
            json.add(originalKey, value)
        }
    }

}
