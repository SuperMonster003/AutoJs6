package org.autojs.autojs.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.runtime.api.modules.ScriptModuleDebugSettings
import org.autojs.autojs.theme.preference.MaterialPreference
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs6.R

/**
 * Developer options: the top-level script modules whose Java exceptions print a debug entry while the debug printing
 * switch is on (docs/augment/ROADMAP.md P1.3, appendix C Q4). The modules come from the host registry; the preference
 * stores the unchecked ones, so all modules print by default. The XML makes it depend on the master switch, which greys
 * it out while the switch is off.
 *
 * zh-CN: 开发者选项: 调试打印开关打开时, 哪些顶层脚本模块的 Java 异常会打印调试条目 (ROADMAP P1.3, 附录 C Q4). 模块取自宿主注册表; 偏好设置保存
 * 未勾选的模块, 因此默认全部打印. XML 中令其依赖总开关.
 */
class ScriptModuleDebugPrintModulesPreference : MaterialPreference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        summaryProvider = SummaryProvider<ScriptModuleDebugPrintModulesPreference> {
            ScriptModuleDebugSettings.printingModules(ScriptModuleDebugSettings.moduleNames, DeveloperKeys.scriptModuleDebugPrintDisabledModules)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: prefContext.getString(R.string.text_none)
        }
    }

    /**
     * The XML dependency on the master switch disables the item, but the custom layout keeps its text colors, so the
     * item also takes the unavailable look of [MaterialPreference] while the switch is off.
     */
    override fun onDependencyChanged(dependency: Preference, disableDependent: Boolean) {
        super.onDependencyChanged(dependency, disableDependent)
        setAvailableForCurrentContext(!disableDependent)
    }

    override fun onClick() {
        if (handleUnavailableClickIfNeeded()) {
            return
        }
        val modules = ScriptModuleDebugSettings.moduleNames
        val excluded = DeveloperKeys.scriptModuleDebugPrintDisabledModules
        val checked = modules.indices.filter { modules[it] !in excluded }.toTypedArray()
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(prefContext)
                .title(R.string.text_script_module_debug_print_modules)
                .content(R.string.summary_script_module_debug_print_modules)
                .options(
                    listOf(
                        MaterialDialog.OptionMenuItemSpec(prefContext.getString(R.string.dialog_button_use_default)) { dialog ->
                            dialog.setSelectedIndices(modules.indices.toList().toTypedArray())
                        },
                    ),
                )
                .items(modules)
                .itemsCallbackMultiChoice(checked) { _, which, _ ->
                    val selected = which.map { modules[it] }
                    DeveloperKeys.scriptModuleDebugPrintDisabledModules = ScriptModuleDebugSettings.excludedAfter(modules, selected, DeveloperKeys.scriptModuleDebugPrintDisabledModules)
                    notifyChanged()
                    true
                }
                .choiceWidgetThemeColor()
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

}
