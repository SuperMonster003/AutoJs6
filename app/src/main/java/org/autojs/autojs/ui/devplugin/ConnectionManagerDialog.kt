package org.autojs.autojs.ui.devplugin

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.disposables.CompositeDisposable
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceInfoFormat
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.ui.common.ManagerSettingsSection
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.A11yServiceManagerEntryItemBinding
import org.autojs.autojs6.databinding.A11yServiceManagerSwitchItemBinding
import org.autojs.autojs6.databinding.ConnectionManagerHeaderButtonBinding
import org.autojs.autojs6.databinding.ConnectionManagerClientItemBinding
import org.autojs.autojs6.databinding.DialogConnectionManagerBinding
import org.autojs.autojs6.databinding.OperationDialogItemBinding
import kotlin.math.roundToInt

/**
 * Shared shell of the VSCode connection managers (client mode / server mode), modelled on the
 * accessibility service manager: a dialog with a live status section (rows refreshed every second, each
 * copyable, the whole section copyable through "Copy information" in the title's "more" menu), control
 * actions and preference switches.
 *
 * The status and control sections have a persisted verbosity level stepped by the header buttons: at
 * [Level.NONE] the status title carries the connection state (e.g. `State [Connected]`) and the primary
 * actions move into the control header as icon buttons; [Level.BASIC] shows the essential rows; [Level.ALL]
 * shows everything.
 *
 * zh-CN: VSCode 连接管理器 (客户端模式 / 服务端模式) 的公共骨架, 仿照无障碍服务管理器: 对话框含实时状态区
 * (每秒刷新, 逐行可复制, 整个区块可通过标题栏 "更多" 菜单的 "复制信息" 复制), 控制动作与偏好开关.
 * 状态区与控制区各有一个由标题按钮逐级切换并持久化的详略等级: [Level.NONE] 时状态标题附带连接状态 (如 "状态 [已连接]"),
 * 主要动作以图标按钮形式移入控制区标题行; [Level.BASIC] 显示基本行; [Level.ALL] 显示全部.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
abstract class ConnectionManagerDialog protected constructor(
    protected val context: Context,
    @StringRes titleRes: Int,
    @StringRes private val statusLevelKeyRes: Int,
    @StringRes private val controlLevelKeyRes: Int,
    @StringRes settingsCollapsedKeyRes: Int,
    @StringRes private val clientsLevelKeyRes: Int,
) {

    /** Verbosity of a section, stored by ordinal. zh-CN: 区块的详略等级, 按序号存储. */
    protected enum class Level { NONE, BASIC, ALL }

    protected class Action(
        @StringRes val titleRes: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val summaryRes: Int? = null,
        /** Lowest control level at which the row is listed. zh-CN: 列出该行所需的最低控制等级. */
        val level: Level = Level.BASIC,
        /** Also offered as an icon button in the control header at [Level.NONE]. zh-CN: 控制等级为 [Level.NONE] 时亦作为标题行图标按钮提供. */
        val header: Boolean = false,
        val onClick: () -> Unit,
    )

    /** One status row; `basic` rows survive [Level.BASIC]. zh-CN: 一行状态; `basic` 行在 [Level.BASIC] 下保留. */
    protected class StatusRow(val name: String, val value: String, val basic: Boolean = true)

    /** One connected client in the clients section. zh-CN: 客户端区中的一个已连接客户端. */
    protected class ClientRow(
        @DrawableRes val iconRes: Int,
        val title: String,
        val subtitle: String,
        val onOpen: (() -> Unit)? = null,
        val onClose: (() -> Unit)? = null,
        val onClick: () -> Unit,
    )

    protected val inflater: LayoutInflater = LayoutInflater.from(context)
    protected val binding = DialogConnectionManagerBinding.inflate(inflater)
    protected val handler = Handler(Looper.getMainLooper())
    protected val disposables = CompositeDisposable()

    private val headerTint: ColorStateList
    private val statusRows = ArrayList<A11yServiceManagerEntryItemBinding>()
    private var statusEntries: List<StatusRow> = emptyList()
    private var statusSummary: String? = null
    private val actionRows = LinkedHashMap<Action, OperationDialogItemBinding>()
    private val headerButtons = LinkedHashMap<Action, ImageButton>()
    private val actionVisible = HashMap<Action, Boolean>()
    private val clientRows = ArrayList<ConnectionManagerClientItemBinding>()
    private var clientEntries: List<ClientRow> = emptyList()
    private var statusLevel = Level.entries[Pref.getInt(statusLevelKeyRes, Level.BASIC.ordinal).coerceIn(Level.entries.indices)]
    private var controlLevel = Level.entries[Pref.getInt(controlLevelKeyRes, Level.BASIC.ordinal).coerceIn(Level.entries.indices)]

    private var clientsLevel = Level.entries[Pref.getInt(clientsLevelKeyRes, Level.ALL.ordinal).coerceIn(Level.entries.indices)]
    private var clientsTitleRes = R.string.text_clients
    private var emptyClientsRes = R.string.text_no_clients_connected
    private var emptyClientsIconRes = R.drawable.ic_laptop_black_48dp
    private val settingsSection: ManagerSettingsSection

    val dialog: MaterialDialog

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!dialog.isShowing) return
            refresh()
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    init {
        val sectionTitleColor = ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), ThemeColorManager.colorPrimary, 3.2)
        headerTint = ColorStateList.valueOf(sectionTitleColor)
        listOf(binding.statusTitle, binding.clientsTitle, binding.controlTitle, binding.settingsTitle).forEach { it.setTextColor(sectionTitleColor) }
        listOf(binding.statusRefresh, binding.statusLess, binding.statusMore, binding.controlLess, binding.controlMore,
            binding.clientsLess, binding.clientsMore, binding.settingsOpen, binding.settingsLess, binding.settingsMore).forEach { it.imageTintList = headerTint }

        binding.statusRefresh.setOnClickListener { refresh() }
        binding.statusLess.setOnClickListener { stepStatusLevel(-1) }
        binding.statusMore.setOnClickListener { stepStatusLevel(+1) }
        binding.controlLess.setOnClickListener { stepControlLevel(-1) }
        binding.controlMore.setOnClickListener { stepControlLevel(+1) }
        binding.clientsLess.setOnClickListener { stepClientsLevel(-1) }
        binding.clientsMore.setOnClickListener { stepClientsLevel(+1) }
        settingsSection = ManagerSettingsSection(context, binding.settingsContainer, binding.settingsOpen,
            binding.settingsHeaderDivider, binding.settingsLess, binding.settingsMore, settingsCollapsedKeyRes)
        applyStatusLevel()
        applyControlLevel()
        applyClientsLevel()

        dialog = MaterialDialog.Builder(context)
            .title(titleRes)
            .options(listOf(MaterialDialog.OptionMenuItemSpec(getString(R.string.text_copy_info)) { copyStatus() }))
            // Not wrapInScrollView: the library pads the inner view horizontally, which would cut the row ripples short.
            // zh-CN: 不使用 wrapInScrollView: 库会给内部视图加水平内边距, 使整行 ripple 被截断.
            .customView(binding.root, false)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .showListener { onShown() }
            .dismissListener { onDismissed() }
            .build()
    }

    /** Recompute the status rows and the visibility of actions. Runs on the main thread. zh-CN: 重新计算状态行与动作可见性, 主线程执行. */
    protected abstract fun refresh()

    @CallSuper
    protected open fun onShown() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    @CallSuper
    protected open fun onDismissed() {
        handler.removeCallbacksAndMessages(null)
        disposables.clear()
        settingsSection.dismiss()
    }

    fun show(): MaterialDialog = dialog.showAdaptive()

    protected fun getString(@StringRes res: Int): String = context.getString(res)

    protected fun getString(@StringRes res: Int, vararg args: Any): String = context.getString(res, *args)

    /** Elapsed text like `2.5 s` or `3 min 12 s`. zh-CN: 时长文本, 如 `2.5 s` 或 `3 min 12 s`. */
    protected fun formatElapsed(millis: Long): String = A11yServiceInfoFormat.formatElapsed(millis)

    /* Status section. zh-CN: 状态区. */

    /**
     * Replace the status rows; existing row views are reused so the dialog does not flicker on refresh.
     * [summary] is the connection state shown in the title when all rows are hidden.
     * zh-CN: 替换状态行; 复用已有行视图, 刷新时不闪烁. [summary] 为所有行隐藏时标题附带的连接状态.
     */
    protected fun setStatus(entries: List<StatusRow>, summary: String) {
        statusEntries = entries
        statusSummary = summary
        while (statusRows.size < entries.size) statusRows += createStatusRow(statusRows.size)
        statusRows.forEachIndexed { index, row ->
            val entry = entries.getOrNull(index)
            if (entry == null) {
                row.root.visibility = View.GONE
            } else {
                if (row.name.text != entry.name) row.name.text = entry.name
                if (row.value.text != entry.value) row.value.text = entry.value
            }
        }
        applyStatusLevel()
    }

    private fun createStatusRow(index: Int): A11yServiceManagerEntryItemBinding {
        return A11yServiceManagerEntryItemBinding.inflate(inflater, binding.statusContainer, true).apply {
            root.setOnClickListener {
                val entry = statusEntries.getOrNull(index) ?: return@setOnClickListener
                copyText("${entry.name}: ${entry.value}")
            }
        }
    }

    private fun stepStatusLevel(delta: Int) {
        val next = Level.entries.getOrNull(statusLevel.ordinal + delta) ?: return
        statusLevel = next
        Pref.putInt(statusLevelKeyRes, next.ordinal)
        applyStatusLevel()
    }

    private fun applyStatusLevel() {
        statusRows.forEachIndexed { index, row ->
            val entry = statusEntries.getOrNull(index)
            val shown = entry != null && when (statusLevel) {
                Level.NONE -> false
                Level.BASIC -> entry.basic
                Level.ALL -> true
            }
            row.root.visibility = if (shown) View.VISIBLE else View.GONE
        }
        // At level NONE the title carries the connection state, e.g. "State [Connected]".
        // zh-CN: 等级为 NONE 时标题附带连接状态, 如 "状态 [已连接]".
        val title = getString(R.string.text_label_state)
        val summary = statusSummary
        binding.statusTitle.text = if (statusLevel == Level.NONE && summary != null) "$title [$summary]" else title
        setStepEnabled(binding.statusLess, statusLevel.ordinal > 0)
        setStepEnabled(binding.statusMore, statusLevel.ordinal < Level.entries.lastIndex)
    }

    /* Clients section. zh-CN: 客户端区. */

    /** Show or hide the clients section (server mode only). zh-CN: 显示或隐藏客户端区 (仅服务端模式). */
    protected fun setClientsSectionVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        binding.clientsHeader.visibility = visibility
        binding.clientsContainer.visibility = visibility
    }

    /**
     * Replace the client rows; views are reused like the status rows. An empty list shows a single
     * placeholder row.
     * zh-CN: 替换客户端行; 与状态行一样复用视图. 空列表显示一行占位说明.
     */
    protected fun setClients(entries: List<ClientRow>) {
        clientEntries = entries
        val rows = if (entries.isEmpty()) {
            listOf(ClientRow(emptyClientsIconRes, getString(emptyClientsRes), "") {})
        } else entries
        while (clientRows.size < rows.size) clientRows += createClientRow(clientRows.size)
        clientRows.forEachIndexed { index, row ->
            val entry = rows.getOrNull(index)
            if (entry == null) {
                row.root.visibility = View.GONE
            } else {
                row.root.visibility = View.VISIBLE
                row.root.isClickable = entries.isNotEmpty()
                row.root.alpha = if (entries.isEmpty()) 0.6f else 1f
                row.icon.setImageResource(entry.iconRes)
                if (row.title.text != entry.title) row.title.text = entry.title
                if (row.subtitle.text != entry.subtitle) row.subtitle.text = entry.subtitle
                row.subtitle.visibility = if (entry.subtitle.isEmpty()) View.GONE else View.VISIBLE
                row.open.visibility = if (entry.onOpen != null) View.VISIBLE else View.GONE
                row.close.visibility = if (entry.onClose != null) View.VISIBLE else View.GONE
                row.open.contentDescription = "${getString(R.string.text_open)}: ${entry.title}"
                row.close.contentDescription = "${getString(R.string.text_close_session)}: ${entry.title}"
            }
        }
        applyClientsLevel()
    }

    protected fun configureClientsSection(@StringRes titleRes: Int, @StringRes emptyRes: Int, @DrawableRes emptyIconRes: Int) {
        clientsTitleRes = titleRes
        emptyClientsRes = emptyRes
        emptyClientsIconRes = emptyIconRes
        setClientsSectionVisible(true)
        applyClientsLevel()
    }

    private fun stepClientsLevel(delta: Int) {
        clientsLevel = Level.entries.getOrNull(clientsLevel.ordinal + delta) ?: return
        Pref.putInt(clientsLevelKeyRes, clientsLevel.ordinal)
        applyClientsLevel()
    }

    private fun applyClientsLevel() {
        binding.clientsContainer.visibility = if (binding.clientsHeader.visibility == View.VISIBLE && clientsLevel != Level.NONE) View.VISIBLE else View.GONE
        binding.clientsTitle.text = if (clientsLevel == Level.NONE) "${getString(clientsTitleRes)} [${clientEntries.size}]" else getString(clientsTitleRes)
        clientRows.forEachIndexed { index, row ->
            row.subtitle.visibility = if (clientsLevel == Level.ALL && clientEntries.getOrNull(index)?.subtitle?.isNotEmpty() == true) View.VISIBLE else View.GONE
        }
        setStepEnabled(binding.clientsLess, clientsLevel != Level.NONE)
        setStepEnabled(binding.clientsMore, clientsLevel != Level.ALL)
    }

    private fun createClientRow(index: Int): ConnectionManagerClientItemBinding {
        return ConnectionManagerClientItemBinding.inflate(inflater, binding.clientsContainer, true).apply {
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            open.setOnClickListener { clientEntries.getOrNull(index)?.onOpen?.invoke() }
            close.setOnClickListener { clientEntries.getOrNull(index)?.onClose?.invoke() }
            root.setOnClickListener {
                val entry = clientEntries.getOrNull(index) ?: return@setOnClickListener
                runCatching { entry.onClick() }.onFailure { e ->
                    snack(e.message ?: getString(R.string.error_an_error_occurred))
                }
            }
        }
    }

    /* Control section. zh-CN: 控制区. */

    protected fun addAction(action: Action): Action {
        actionRows[action] = OperationDialogItemBinding.inflate(inflater, binding.controlContainer, true).apply {
            val verticalPadding = DisplayUtils.dpToPx(10f).roundToInt()
            root.setPadding(root.paddingLeft, verticalPadding, root.paddingRight, verticalPadding)
            icon.setImageResource(action.iconRes)
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            title.setText(action.titleRes)
            action.summaryRes?.let { summaryRes ->
                subtitle.setText(summaryRes)
                subtitle.visibility = View.VISIBLE
            }
            root.setOnClickListener { runAction(action) }
        }
        if (action.header) {
            headerButtons[action] = ConnectionManagerHeaderButtonBinding.inflate(inflater, binding.controlHeaderActions, true).root.apply {
                setImageResource(action.iconRes)
                contentDescription = getString(action.titleRes)
                imageTintList = headerTint
                setOnClickListener { runAction(action) }
            }
        }
        applyActionVisibility(action)
        return action
    }

    private fun runAction(action: Action) {
        runCatching { action.onClick() }.onFailure { e ->
            snack(e.message ?: getString(R.string.error_an_error_occurred))
        }
    }

    /** State-driven visibility; the control level decides whether the row or the header button shows it. zh-CN: 由状态决定的可见性; 控制等级决定以行还是标题按钮展示. */
    protected fun setActionVisible(action: Action, visible: Boolean) {
        actionVisible[action] = visible
        applyActionVisibility(action)
    }

    protected fun setActionEnabled(action: Action, enabled: Boolean) {
        listOfNotNull(actionRows[action]?.root, headerButtons[action]).forEach {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else STEP_LIMIT_ALPHA
        }
    }

    private fun applyActionVisibility(action: Action) {
        val visible = actionVisible[action] ?: true
        val rowShown = visible && when (controlLevel) {
            Level.NONE -> false
            Level.BASIC -> action.level == Level.BASIC
            Level.ALL -> true
        }
        actionRows[action]?.root?.visibility = if (rowShown) View.VISIBLE else View.GONE
        headerButtons[action]?.visibility = if (visible && controlLevel == Level.NONE) View.VISIBLE else View.GONE
        applyControlHeader()
    }

    private fun stepControlLevel(delta: Int) {
        val next = Level.entries.getOrNull(controlLevel.ordinal + delta) ?: return
        controlLevel = next
        Pref.putInt(controlLevelKeyRes, next.ordinal)
        applyControlLevel()
    }

    private fun applyControlLevel() {
        actionRows.keys.forEach { applyActionVisibility(it) }
        applyControlHeader()
        setStepEnabled(binding.controlLess, controlLevel.ordinal > 0)
        setStepEnabled(binding.controlMore, controlLevel.ordinal < Level.entries.lastIndex)
    }

    // The header actions (and their divider) only take space when at least one of them is shown.
    // zh-CN: 标题行动作 (及其分隔线) 仅在至少一个动作可见时占位.
    private fun applyControlHeader() {
        val anyShown = controlLevel == Level.NONE && headerButtons.values.any { it.visibility == View.VISIBLE }
        val visibility = if (anyShown) View.VISIBLE else View.GONE
        binding.controlHeaderActions.visibility = visibility
        binding.controlHeaderDivider.visibility = visibility
    }

    // A step button at its limit stays visible but fades, so the row keeps its shape.
    // zh-CN: 到达边界的等级按钮保持可见但变淡, 使标题行形状不变.
    private fun setStepEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else STEP_LIMIT_ALPHA
    }

    /* Settings section. zh-CN: 设置区. */

    protected fun addSwitch(
        @StringRes titleRes: Int,
        @StringRes summaryRes: Int,
        key: SettingKey.Bool,
        onChanged: ((Boolean) -> Unit)? = null,
    ) {
        A11yServiceManagerSwitchItemBinding.inflate(inflater, binding.settingsContainer, true).apply {
            title.setText(titleRes)
            subtitle.setText(summaryRes)
            // PrefSwitch persists through setChecked(), so a checked-change listener does not interfere.
            // zh-CN: PrefSwitch 通过 setChecked() 持久化, 因此选中变化监听器不会与之冲突.
            sw.setPrefKey(key.name, key.defaultOrNull() ?: false)
            onChanged?.let { listener -> sw.setOnCheckedChangeListener { _, isChecked -> listener(isChecked) } }
            root.setOnClickListener { sw.toggle() }
        }
    }

    protected fun addSetting(@StringRes titleRes: Int, @DrawableRes iconRes: Int, onClick: () -> Unit) {
        OperationDialogItemBinding.inflate(inflater, binding.settingsContainer, true).apply {
            title.setText(titleRes)
            icon.setImageResource(iconRes)
            icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            root.setOnClickListener { onClick() }
        }
    }

    protected fun snack(text: CharSequence?) = ViewUtils.showSnack(dialog.view, text)

    protected fun snack(@StringRes res: Int) = ViewUtils.showSnack(dialog.view, res)

    protected fun copyText(text: String?) {
        if (text.isNullOrEmpty()) return
        ClipboardUtils.setClip(context, text)
        snack(R.string.text_already_copied_to_clip)
    }

    // "Copy information" copies every status row, whatever the current level hides.
    // zh-CN: "复制信息" 复制全部状态行, 与当前等级隐藏了哪些无关.
    private fun copyStatus() {
        copyText(statusEntries.joinToString("\n") { "${it.name}: ${it.value}" })
    }

    companion object {

        private const val REFRESH_INTERVAL_MILLIS = 1_000L
        private const val STEP_LIMIT_ALPHA = 0.3f

    }

}
