package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.r8.AndroidR8CompilerTransport
import org.autojs.autojs.core.plugin.r8.R8CompilerComponent
import org.autojs.autojs.core.plugin.r8.R8CompilerOptInStore
import org.autojs.autojs.core.plugin.r8.R8CompilerSelectionDecision
import org.autojs.autojs.core.plugin.r8.R8CompilerSelectionOptions
import org.autojs.autojs.core.plugin.r8.R8CompilerSelectionPolicy
import org.autojs.autojs.core.plugin.r8.R8CompilerServiceCandidate
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import org.autojs.plugin.r8compiler.api.R8CompilerContract

/** User-visible, default-off exact-component selector for the R8-only script entry. */
class R8CompilerPreference : MaterialPreference {

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
        val options = R8CompilerOptInStore.read()
        val candidates = discovery.candidates
        val items = buildList {
            add(context.getString(R.string.text_r8_compiler_use_disabled))
            candidates.mapTo(this) { it.component.exactLabel() }
        }
        var selectedIndex = options.selectedComponent
            ?.takeIf { options.enabled }
            ?.let { selected -> candidates.indexOfFirst { it.component == selected } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        val content = when {
            discovery.failed -> R.string.text_r8_compiler_provider_discovery_failed
            candidates.isEmpty() -> R.string.text_r8_compiler_no_eligible_providers
            else -> R.string.description_r8_compiler_explicit_preference
        }

        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_r8_compiler_provider)
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
                    ?.let { R8CompilerOptInStore.select(it.component) }
                    ?: R8CompilerOptInStore.disable()
                refreshSummary()
                notifyChanged()
                currentDialog.dismiss()
            }
            .showAdaptive()
        super.onClick()
    }

    private fun discoverEligibleCandidates(): Discovery {
        val discovered = try {
            AndroidR8CompilerTransport(context.applicationContext)
                .discoverExactAction(R8CompilerContract.SERVICE_ACTION)
        } catch (_: Exception) {
            return Discovery(failed = true)
        }
        val candidates = discovered
            .groupBy(R8CompilerServiceCandidate::component)
            .values
            .mapNotNull { exactMatches ->
                val candidate = exactMatches.singleOrNull() ?: return@mapNotNull null
                candidate.takeIf {
                    R8CompilerSelectionPolicy.decide(
                        options = R8CompilerSelectionOptions(
                            enabled = true,
                            selectedComponent = candidate.component,
                        ),
                        discoverCandidates = { discovered },
                    ) is R8CompilerSelectionDecision.Selected
                }
            }
            .sortedWith(
                compareBy<R8CompilerServiceCandidate> { it.component.packageName }
                    .thenBy { it.component.className },
            )
        return Discovery(candidates = candidates)
    }

    private fun refreshSummary() {
        val options = R8CompilerOptInStore.read()
        val nextSummary = when {
            options.enabled && options.selectedComponent != null -> context.getString(
                R.string.summary_r8_compiler_explicit_selected,
                options.selectedComponent.exactLabel(),
            )
            options.selectedComponent != null -> context.getString(
                R.string.summary_r8_compiler_explicit_disabled_with_selection,
                options.selectedComponent.exactLabel(),
            )
            else -> context.getString(R.string.summary_r8_compiler_explicit_disabled)
        }
        if (summary?.toString() != nextSummary) summary = nextSummary
    }

    private fun R8CompilerComponent.exactLabel(): String = "$packageName/$className"

    private data class Discovery(
        val candidates: List<R8CompilerServiceCandidate> = emptyList(),
        val failed: Boolean = false,
    )
}
