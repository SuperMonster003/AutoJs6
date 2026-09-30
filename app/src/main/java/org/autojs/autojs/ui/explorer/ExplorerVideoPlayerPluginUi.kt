package org.autojs.autojs.ui.explorer

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ExplorerVideoPlayerAvailability {
    MISSING,
    DISABLED,
    UNAUTHORIZED,
    UNAVAILABLE,
}

internal object ExplorerVideoPlayerAvailabilityPolicy {

    fun resolve(
        installed: Boolean,
        enabled: Boolean,
        authorized: Boolean,
    ): ExplorerVideoPlayerAvailability = when {
        !installed -> ExplorerVideoPlayerAvailability.MISSING
        !enabled -> ExplorerVideoPlayerAvailability.DISABLED
        !authorized -> ExplorerVideoPlayerAvailability.UNAUTHORIZED
        else -> ExplorerVideoPlayerAvailability.UNAVAILABLE
    }
}

/** Foreground guidance and explicit system fallback for Explorer's official video action. */
internal object ExplorerVideoPlayerPluginUi {

    private val dialogShowing = AtomicBoolean(false)

    fun isOfficialAction(action: ResolvedExplorerAction): Boolean =
        action.packageName == OFFICIAL_PACKAGE && action.descriptor.id == ACTION_ID

    fun showUnavailableDialog(context: Context, item: ExplorerItem) {
        if (!dialogShowing.compareAndSet(false, true)) return
        val applicationInfo = findApplicationInfo(context)
        val displayName = applicationInfo
            ?.runCatching { loadLabel(context.packageManager).toString() }
            ?.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.text_video_player)
        val availability = ExplorerVideoPlayerAvailabilityPolicy.resolve(
            installed = applicationInfo != null,
            enabled = applicationInfo?.enabled == true &&
                PluginEnableStore.isEnabled(context.applicationContext, OFFICIAL_PACKAGE, true),
            authorized = applicationInfo != null &&
                PluginTrustManager.isAuthorized(context.applicationContext, OFFICIAL_PACKAGE),
        )
        val content = when (availability) {
            ExplorerVideoPlayerAvailability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                displayName,
            )
            ExplorerVideoPlayerAvailability.DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                displayName,
            )
            ExplorerVideoPlayerAvailability.UNAUTHORIZED -> context.getString(
                R.string.error_plugin_is_not_authorized_in_plugin_center,
                displayName,
            )
            ExplorerVideoPlayerAvailability.UNAVAILABLE -> context.getString(
                R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch,
            )
        }
        try {
            MaterialDialog.Builder(context)
                .title(R.string.text_video_player)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.text_open_by_other_apps)
                .neutralColorRes(R.color.dialog_button_hint)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onNeutral { _, _ -> openWithOtherApps(context, item) }
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = OFFICIAL_PACKAGE,
                    )
                }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    private fun openWithOtherApps(context: Context, item: ExplorerItem): Boolean {
        val uri = IntentUtils.getUriOfFile(context, item.path, AppFileProvider.AUTHORITY)
        val target = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, Mime.VIDEO_WILDCARD)
            clipData = ClipData.newRawUri(item.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(target, context.getString(R.string.text_open_by_other_apps))
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(EXTERNAL_VIEWER_COMPONENT))
            .startSafely(context, true) {
                ToastExceptionHolder(
                    context,
                    R.string.error_no_applications_available_for_playing_this_file,
                ).show()
            }
    }

    private fun findApplicationInfo(context: Context): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                OFFICIAL_PACKAGE,
                PackageManager.ApplicationInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(
                OFFICIAL_PACKAGE,
                PackageManager.MATCH_DISABLED_COMPONENTS,
            )
        }
    }.getOrNull()

    const val OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.amber.player"
    private const val ACTION_ID = "play-video"
    private val EXTERNAL_VIEWER_COMPONENT = ComponentName(
        OFFICIAL_PACKAGE,
        "$OFFICIAL_PACKAGE.ExternalViewerActivity",
    )
}
