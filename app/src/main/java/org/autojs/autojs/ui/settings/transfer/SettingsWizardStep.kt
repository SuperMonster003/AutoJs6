package org.autojs.autojs.ui.settings.transfer

import android.net.Uri
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.wizard.WizardUi.showAndAwait
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs6.R

/**
 * The page that runs a settings wizard (docs/settings/ROADMAP.md T10): it keeps [wizardState] with its instance state
 * and resumes the wizard from it when it is recreated.
 *
 * zh-CN: 运行设置向导的页面 (ROADMAP T10): 把 [wizardState] 保存在实例状态中, 重建后据此恢复向导.
 */
interface SettingsWizardHost {

    val hostActivity: FragmentActivity

    /** The state of the running wizard, null when none runs. zh-CN: 运行中向导的状态, 没有向导时为 null. */
    var wizardState: SettingsWizardState?

    /**
     * Opens the system page that creates a document (SAF, T11) named [fileName]; its result reaches [awaitDocument].
     * zh-CN: 打开系统的创建文档页面 (SAF, T11), 文件名为 [fileName]; 结果经 [awaitDocument] 取得.
     */
    fun requestDocument(fileName: String)

    /**
     * The document created after [requestDocument], also when the result reaches a recreated page; null when canceled.
     * zh-CN: [requestDocument] 之后创建的文档, 结果到达重建后的页面时同样可取得; 取消时为 null.
     */
    suspend fun awaitDocument(): Uri?
}

/**
 * A settings wizard (T10): [steps] shown one after another, each as a dialog awaited with `WizardUi.showAndAwait`. A
 * step returns the state to go on with, whose [SettingsWizardState.step] names the step shown next ([next] /
 * [previous] follow the order of [steps]), or null to end the wizard. Every state passes through
 * [SettingsWizardHost.wizardState] before its step is shown, so a wizard cancelled by the recreation of the page (the
 * coroutine and its dialog end with the view) continues from there; only a wizard that ends by itself clears it.
 *
 * zh-CN: 设置向导 (T10): 依次显示 [steps], 每一步是以 `WizardUi.showAndAwait` 等待的对话框. 步骤返回接下来使用的状态, 其
 * [SettingsWizardState.step] 指明下一个显示的步骤 ([next] / [previous] 按 [steps] 的顺序), 返回 null 则结束向导. 每个状态在显示
 * 对应步骤之前都先写入 [SettingsWizardHost.wizardState], 因此页面重建时被取消的向导 (协程与对话框随视图结束) 可以从该状态继续;
 * 只有向导自行结束时才清空它.
 */
abstract class SettingsWizard(val host: SettingsWizardHost) {

    @get:StringRes
    abstract val titleRes: Int

    abstract val steps: List<SettingsWizardStep>

    suspend fun run(initial: SettingsWizardState) {
        var state: SettingsWizardState? = initial
        while (state != null) {
            val current: SettingsWizardState = state
            host.wizardState = current
            val step = steps.firstOrNull { it.id == current.step } ?: break
            state = step.run(this, current)
        }
        host.wizardState = null
    }

    /** [state] at the step after its own, or null after the last one. zh-CN: 下一步骤的状态, 已是最后一步时为 null. */
    fun next(state: SettingsWizardState): SettingsWizardState? =
        steps.getOrNull(indexOf(state) + 1)?.let { state.copy(step = it.id) }

    /** [state] at the step before its own, or null at the first one. zh-CN: 上一步骤的状态, 已是第一步时为 null. */
    fun previous(state: SettingsWizardState): SettingsWizardState? =
        indexOf(state).takeIf { it > 0 }?.let { state.copy(step = steps[it - 1].id) }

    private fun indexOf(state: SettingsWizardState): Int = steps.indexOfFirst { it.id == state.step }
}

/**
 * One step of a settings wizard (T10). [dialog] gives the common frame: the title of the wizard, "Cancel", "Back" when
 * there is a previous step, and a positive button; [navigate] turns the pressed button into the state to go on with
 * ("Back" -> [SettingsWizard.previous], "Cancel" or the back key -> null). A step that offers "Skip" goes on with
 * [SettingsWizard.next] of the state without its own choices.
 *
 * zh-CN: 设置向导的一个步骤 (T10). [dialog] 提供公共框架: 向导标题, "取消", 有上一步时的 "上一步", 以及一个确认按钮; [navigate]
 * 把按下的按钮转为接下来使用的状态 ("上一步" -> [SettingsWizard.previous], "取消" 或返回键 -> null). 提供 "跳过" 的步骤以不含本步
 * 选择的状态调用 [SettingsWizard.next].
 */
abstract class SettingsWizardStep(val id: SettingsWizardState.Step) {

    /**
     * Shows the step for [state] and suspends until the user leaves it; returns the state to go on with, or null to end
     * the wizard. zh-CN: 为 [state] 显示本步骤并挂起至用户离开; 返回接下来使用的状态, 返回 null 则结束向导.
     */
    abstract suspend fun run(wizard: SettingsWizard, state: SettingsWizardState): SettingsWizardState?

    /** [back] / [cancel] false leave out "Back" / "Cancel" (pages after the work is done). */
    protected fun dialog(
        wizard: SettingsWizard,
        state: SettingsWizardState,
        @StringRes positiveRes: Int = R.string.dialog_button_next_step,
        back: Boolean = true,
        cancel: Boolean = true,
    ): MaterialDialog.Builder = MaterialDialog.Builder(wizard.host.hostActivity)
        .title(wizard.titleRes)
        .widgetThemeColor()
        .canceledOnTouchOutside(false)
        .positiveText(positiveRes)
        .positiveColorRes(R.color.dialog_button_attraction)
        .also { builder ->
            if (cancel) {
                builder.negativeText(R.string.dialog_button_cancel).negativeColorRes(R.color.dialog_button_default)
            }
            if (back && wizard.previous(state) != null) {
                builder.neutralText(R.string.dialog_button_back).neutralColorRes(R.color.dialog_button_default)
            }
        }

    /**
     * Shows [builder] and maps the pressed button; [onPositive] gives the state after a positive press. "Back" keeps
     * the choices the step has published to the host meanwhile.
     * zh-CN: 显示 [builder] 并映射按下的按钮; [onPositive] 给出确认后的状态. "上一步" 保留本步骤期间已写入宿主的选择.
     */
    protected suspend fun navigate(
        wizard: SettingsWizard,
        state: SettingsWizardState,
        builder: MaterialDialog.Builder,
        onPositive: () -> SettingsWizardState?,
    ): SettingsWizardState? = when (builder.showAndAwait()) {
        DialogAction.POSITIVE -> onPositive()
        DialogAction.NEUTRAL -> wizard.previous(wizard.host.wizardState ?: state)
        else -> null
    }
}
