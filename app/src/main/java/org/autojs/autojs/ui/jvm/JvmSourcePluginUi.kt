package org.autojs.autojs.ui.jvm

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.jvm.AndroidJvmSourceTransport
import org.autojs.autojs.core.plugin.jvm.JvmSourceProviderLanguagePolicy
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.script.JvmSourceKind
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground-only guidance for the independently installed Java/Kotlin Runtime plugins. */
object JvmSourcePluginUi {

    internal enum class Availability {
        READY,
        MISSING,
        DISABLED,
    }

    private val dialogShowing = AtomicBoolean(false)

    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(context: Context): Boolean =
        ensureAvailableForInteractiveLaunch(context, JvmSourceKind.JAVA, null)

    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(
        context: Context,
        sourceKind: JvmSourceKind,
    ): Boolean = ensureAvailableForInteractiveLaunch(context, sourceKind, null)

    /**
     * Java-friendly overload for short-lived foreground activities which must remain alive until
     * the unavailable-provider dialog has closed.
     */
    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(
        context: Context,
        onUnavailableDialogDismissed: Runnable?,
    ): Boolean = ensureAvailableForInteractiveLaunch(
        context,
        JvmSourceKind.JAVA,
        onUnavailableDialogDismissed,
    )

    @JvmStatic
    fun ensureAvailableForInteractiveLaunch(
        context: Context,
        sourceKind: JvmSourceKind,
        onUnavailableDialogDismissed: Runnable?,
    ): Boolean {
        val profile = profile(sourceKind)
        val availability = inspect(context, JvmSourceR1Language.language(sourceKind))
        if (availability == Availability.READY) return true
        showUnavailableDialog(context, profile, availability, onUnavailableDialogDismissed)
        return false
    }

    internal fun decideAvailability(
        installedCandidateCount: Int,
        enabledCandidateCount: Int,
    ): Availability = when {
        installedCandidateCount <= 0 -> Availability.MISSING
        enabledCandidateCount <= 0 -> Availability.DISABLED
        else -> Availability.READY
    }

    private fun inspect(context: Context, language: JvmSourceLanguage): Availability {
        val candidates = runCatching {
            AndroidJvmSourceTransport(context).discoverExactAction(JvmSourceContract.SERVICE_ACTION)
        }.getOrDefault(emptyList()).filter { candidate ->
            JvmSourceProviderLanguagePolicy.matches(candidate, language)
        }
        return decideAvailability(
            installedCandidateCount = candidates.size,
            enabledCandidateCount = candidates.count { candidate ->
                candidate.applicationEnabled && candidate.serviceEnabled && candidate.pluginEnabled
            },
        )
    }

    private fun showUnavailableDialog(
        context: Context,
        profile: PluginProfile,
        availability: Availability,
        onDismissed: Runnable?,
    ) {
        if (!dialogShowing.compareAndSet(false, true)) {
            onDismissed?.run()
            return
        }
        val content = when (availability) {
            Availability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                profile.displayName,
            )
            Availability.DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                profile.displayName,
            )
            Availability.READY -> {
                dialogShowing.set(false)
                onDismissed?.run()
                return
            }
        }
        try {
            MaterialDialog.Builder(context)
                .title(R.string.text_plugin_center)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        initialQuery = profile.officialPackage,
                    )
                }
                .dismissListener {
                    dialogShowing.set(false)
                    onDismissed?.run()
                }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            onDismissed?.run()
            throw error
        }
    }

    private fun profile(sourceKind: JvmSourceKind): PluginProfile = when (sourceKind) {
        JvmSourceKind.JAVA -> PluginProfile("Java Runtime", JAVA_OFFICIAL_PACKAGE)
        JvmSourceKind.KOTLIN -> PluginProfile("Kotlin Runtime", KOTLIN_OFFICIAL_PACKAGE)
    }

    private data class PluginProfile(
        val displayName: String,
        val officialPackage: String,
    )

    private object JvmSourceR1Language {
        fun language(sourceKind: JvmSourceKind): JvmSourceLanguage = when (sourceKind) {
            JvmSourceKind.JAVA -> JvmSourceLanguage.JAVA
            JvmSourceKind.KOTLIN -> JvmSourceLanguage.KOTLIN
        }
    }

    const val JAVA_OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.java.runtime"
    const val KOTLIN_OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.kotlin.runtime"
    const val OFFICIAL_PACKAGE = JAVA_OFFICIAL_PACKAGE
}
