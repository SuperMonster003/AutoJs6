package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.dex.AndroidDexCompilerExperimentalTransport
import org.autojs.autojs.core.plugin.dex.DexCompilerComponent
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInDecision
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInOptions
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInPolicy
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInStore
import org.autojs.autojs.core.plugin.dex.DexCompilerServiceCandidate
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.dexcompiler.api.DexCompilerContract

class DexCompilerPreference : MaterialPreference {

    private var dialog: MaterialDialog? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        refreshSummary()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        refreshSummary()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        refreshSummary()
    }

    constructor(context: Context) : super(context) {
        refreshSummary()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        refreshSummary()
        super.onBindViewHolder(holder)
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded() || dialog?.isShowing == true) return

        val discovery = discoverEligibleCandidates()
        val options = DexCompilerOptInStore.read()
        val candidates = discovery.candidates
        val items = buildList {
            add(context.getString(R.string.text_dex_compiler_use_builtin))
            candidates.mapTo(this) { it.component.exactLabel() }
        }
        var selectedIndex = options.selectedComponent
            ?.takeIf { options.enabled }
            ?.let { selected -> candidates.indexOfFirst { it.component == selected } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        val content = when {
            discovery.failed -> R.string.text_dex_compiler_provider_discovery_failed
            candidates.isEmpty() -> R.string.text_dex_compiler_no_eligible_providers
            else -> R.string.description_dex_compiler_experimental_preference
        }

        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_dex_compiler_provider)
            .content(content)
            .items(items)
            .itemsCallbackSingleChoice(selectedIndex) { _, _, which, _ ->
                selectedIndex = which
                true
            }
            .alwaysCallSingleChoiceCallback()
            .choiceWidgetThemeColor()
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { currentDialog, _ -> currentDialog.dismiss() }
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { currentDialog, _ ->
                candidates.getOrNull(selectedIndex - 1)
                    ?.let { DexCompilerOptInStore.select(it.component) }
                    ?: DexCompilerOptInStore.disable()
                refreshSummary()
                notifyChanged()
                currentDialog.dismiss()
            }
            .showAdaptive()
        super.onClick()
    }

    private fun discoverEligibleCandidates(): Discovery {
        val discovered = try {
            AndroidDexCompilerExperimentalTransport(context.applicationContext)
                .discoverExactAction(DexCompilerContract.SERVICE_ACTION)
        } catch (_: Exception) {
            return Discovery(failed = true)
        }
        val candidates = discovered
            .groupBy(DexCompilerServiceCandidate::component)
            .values
            .mapNotNull { exactMatches ->
                val candidate = exactMatches.singleOrNull() ?: return@mapNotNull null
                candidate.takeIf {
                    candidate.sameSignerAsHost &&
                        DexCompilerOptInPolicy.decide(
                            options = DexCompilerOptInOptions(
                                enabled = true,
                                selectedComponent = candidate.component,
                            ),
                            releaseBuild = !BuildConfig.DEBUG,
                            discoverCandidates = { discovered },
                        ) is DexCompilerOptInDecision.Selected
                }
            }
            .sortedWith(
                compareBy<DexCompilerServiceCandidate> { it.component.packageName }
                    .thenBy { it.component.className },
            )
        return Discovery(candidates = candidates)
    }

    private fun refreshSummary() {
        val options = DexCompilerOptInStore.read()
        val nextSummary = when {
            options.enabled && options.selectedComponent != null -> context.getString(
                R.string.summary_dex_compiler_experimental_selected,
                options.selectedComponent.exactLabel(),
            )
            options.selectedComponent != null -> context.getString(
                R.string.summary_dex_compiler_experimental_disabled_with_selection,
                options.selectedComponent.exactLabel(),
            )
            else -> context.getString(R.string.summary_dex_compiler_experimental_disabled)
        }
        if (summary?.toString() != nextSummary) summary = nextSummary
    }

    private fun DexCompilerComponent.exactLabel(): String = "$packageName/$className"

    private data class Discovery(
        val candidates: List<DexCompilerServiceCandidate> = emptyList(),
        val failed: Boolean = false,
    )
}
