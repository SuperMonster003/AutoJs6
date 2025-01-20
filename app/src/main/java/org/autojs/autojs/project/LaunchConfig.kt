package org.autojs.autojs.project

import com.google.gson.annotations.SerializedName
import org.autojs.autojs.annotation.SerializedNameCompatible
import org.autojs.autojs.annotation.SerializedNameCompatible.With
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs6.R

/**
 * Created by Stardust on Jan 25, 2018.
 * Modified by SuperMonster003 as of Jan 15, 2025.
 * Created by SuperMonster003 on Jan 15, 2025.
 */
class LaunchConfig {

    @Transient
    private val mContext = GlobalAppContext.get()

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

    @SerializedName("slug")
    @field:SerializedNameCompatible(
        With(value = "slugText"),
        With(value = "splashText", target = ["AutoX"]),
    )
    var slug = mContext.getString(R.string.text_powered_by_autojs)

    @SerializedName("permissions")
    @field:SerializedNameCompatible(
        With(value = "permission"),
        With(value = "permissionList"),
    )
    var permissions = emptyList<String>()

}
