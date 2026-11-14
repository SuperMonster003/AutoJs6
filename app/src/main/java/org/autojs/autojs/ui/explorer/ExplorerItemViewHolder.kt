package org.autojs.autojs.ui.explorer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionExecution
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.accessibility.LayoutSnapshotFiles
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedExplorerItem
import org.autojs.autojs.model.explorer.WorkspaceFileProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.script.AutoJs4ScriptSource
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.theme.ThemeColorManagerCompat
import org.autojs.autojs.ui.autojs4.AutoJs4RuntimePluginUi
import org.autojs.autojs.ui.floating.layoutinspector.LayoutSnapshotViewer
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager
import org.autojs.autojs.ui.main.scripts.ImageInfoDialogManager
import org.autojs.autojs.ui.main.scripts.MediaInfoDialogManager
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.FirstCharView
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerFileBinding
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding
import java.io.File

private const val LOG_TAG = "ExplorerItemView"
private const val PLUGIN_ACTION_MENU_ORDER_START = 1_000

private data class ExplorerItemMainContent(
    val icon: FileUtils.TYPE.Icon,
    val executable: Boolean,
    val name: CharSequence,
    val date: CharSequence,
    val size: CharSequence,
)

internal interface ExplorerItemViewHost {
    val context: Context
    val explorerView: ExplorerView
    val actionIconTint: ColorStateList
    val isInSelectionMode: Boolean
    val pluginActionProvider: ExplorerPluginActionProvider?
    val pluginActionSurface: ExplorerSurface

    fun selectItem(item: ExplorerItem)
    fun enterSelectionMode(item: ExplorerItem): Boolean
    fun blockListScrollUntilTouchRelease()
    fun toggleItemSelection(item: ExplorerItem)
    fun isItemSelected(item: ExplorerItem): Boolean
    fun selectPluginAction(item: ExplorerItem, actions: List<ResolvedExplorerAction>): ResolvedExplorerAction?
    fun openArchiveEntry(item: ArchiveExplorerFileItem, action: ExplorerPrimaryAction?)
    fun notifyItemOperated(item: ExplorerItem)
    fun launchOfficialMediaOrShowGuidance(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        preResolvedAction: ResolvedExplorerAction? = null,
    ): Boolean
    fun launchDocumentPreviewerOrShowGuidance(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        preResolvedAction: ResolvedExplorerAction? = null,
    ): Boolean
    fun resolvePluginAction(entryPoint: ExplorerActionEntryPoint, item: ExplorerItem): ResolvedExplorerAction?
    fun launchPluginAction(
        entryPoint: ExplorerActionEntryPoint,
        pluginAction: ResolvedExplorerAction,
        item: ExplorerItem,
    ): Boolean
    fun dispatchHostItemClick(itemView: View, item: ExplorerItem)
    fun hideHostDialog()
    fun showHostDialog()
    fun onBuiltInMenuItem(item: MenuItem): Boolean
    fun dispatchPluginAction(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit>
}

internal class ExplorerItemViewHolder(
    itemView: View,
    private val host: ExplorerItemViewHost,
) : BindableViewHolder<Any>(itemView) {

    private val mFirstChar: FirstCharView

    private val mName: TextView
    private val mFileDate: TextView
    private val mFileSize: TextView

    private val mActionIconContainer: View
    private val mPrimaryViewer: ImageView
    private val mInstall: View
    private val mRun: ImageView
    private val mEdit: View
    private val mInfo: View

    private val mItem: View
    private val mOptions: View

    private lateinit var mExplorerItem: ExplorerItem
    private var mBindGeneration = 0
    private var mActionBindGeneration = 0
    private var mPrimaryPluginAction: ResolvedExplorerAction? = null

    init {
        val explorerFileBinding = ExplorerFileBinding.bind(itemView)
        val firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView)

        mFirstChar = firstCharIconBinding.firstChar

        mName = explorerFileBinding.name
        mFileDate = explorerFileBinding.scriptFileDate
        mFileSize = explorerFileBinding.scriptFileSize

        mActionIconContainer = explorerFileBinding.actionIconContainer

        mItem = explorerFileBinding.item
        mPrimaryViewer = explorerFileBinding.primaryViewer.apply {
            setOnClickListener { withItemSelected { openPrimaryViewer() } }
        }
        mRun = explorerFileBinding.run.apply { setOnClickListener { withItemSelected { run() } } }
        mEdit = explorerFileBinding.edit.apply { setOnClickListener { withItemSelected { edit() } } }
        mInfo = explorerFileBinding.info.apply { setOnClickListener { withItemSelected { showInfo() } } }
        mInstall = explorerFileBinding.install.apply { setOnClickListener { withItemSelected { install() } } }

        mOptions = explorerFileBinding.more.apply { setOnClickListener { withItemSelected { showOptionsMenu() } } }

        listOf(mPrimaryViewer, mRun, mEdit, mInfo, mInstall, mOptions).forEach { view ->
            ImageViewCompat.setImageTintList(view, host.actionIconTint)
        }

        mItem.setOnClickListener { withItemSelected { onItemClick() } }
        mItem.setOnLongClickListener {
            if (withItemSelected { host.enterSelectionMode(mExplorerItem) }) {
                host.blockListScrollUntilTouchRelease()
            }
            true
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun bind(item: Any, position: Int) {
        if (item !is ExplorerItem) return
        mExplorerItem = item
        mPrimaryPluginAction = null
        val bindGeneration = ++mBindGeneration

        bindMainContent(item, bindGeneration)
        bindSelectionState(item)
        bindActions(item, bindGeneration, clearExisting = true)
    }

    private fun bindMainContent(item: ExplorerItem, bindGeneration: Int) {
        Observable.fromCallable {
            ExplorerItemMainContent(
                icon = ExplorerViewHelper.getIcon(item),
                executable = item.isExecutable,
                name = ExplorerViewHelper.getDisplayName(host.context, item),
                date = PFile.getFullDateString(item.lastModified()),
                size = PFiles.formatSizeWithUnit(item.size),
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { content ->
                if (bindGeneration != mBindGeneration) return@subscribe
                mName.text = content.name
                mFileDate.text = content.date
                mFileSize.text = content.size
                mFirstChar.setIcon(content.icon)
                if (content.executable) {
                    val themeColorForContrast = ColorUtils.adjustColorForContrast(
                        host.context.getColor(R.color.item_background_dark),
                        ThemeColorManagerCompat.getColorPrimary(),
                        1.15,
                    )
                    mFirstChar.setIconTextColorByThemeColorLuminance()
                    mFirstChar.setStrokeColor(themeColorForContrast)
                    mFirstChar.setFillColor(themeColorForContrast)
                } else {
                    mFirstChar.setIconTextColorDayNight()
                    mFirstChar.setStrokeColorDayNight()
                    mFirstChar.setFillTransparent()
                }
                mFirstChar.visibility = VISIBLE
            }
    }

    fun refreshActions() {
        if (!::mExplorerItem.isInitialized) return
        bindActions(mExplorerItem, mBindGeneration, clearExisting = false)
    }

    private fun bindActions(
        item: ExplorerItem,
        bindGeneration: Int,
        clearExisting: Boolean,
    ) {
        val actionBindGeneration = ++mActionBindGeneration
        if (clearExisting) {
            setVisibilityIf(mActionIconContainer, false)
        }

        Observable.fromCallable {
            val pluginPrimaryAction = if (item is ArchiveExplorerItem || item is PrivilegedExplorerItem) {
                null
            } else {
                runCatching {
                    host.pluginActionProvider?.actionsFor(
                        host.pluginActionSurface,
                        ExplorerActionEntryPoint.PRIMARY_BUTTON,
                        item,
                    )?.let { actions -> host.selectPluginAction(item, actions) }
                }.onFailure { error ->
                    Log.w(LOG_TAG, "Failed to resolve primary Explorer plugin action", error)
                }.getOrNull()
            }
            ExplorerItemActionPolicy.resolve(item, pluginPrimaryAction)
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { state ->
                if (
                    bindGeneration != mBindGeneration ||
                    actionBindGeneration != mActionBindGeneration
                ) {
                    return@subscribe
                }

                mPrimaryPluginAction = state.pluginPrimaryAction
                val hasPrimaryAction = state.primaryAction != null || state.pluginPrimaryAction != null
                val hasActionIcons = hasPrimaryAction ||
                    state.editVisible ||
                    state.runVisible ||
                    state.installVisible ||
                    state.infoVisible
                if (host.isInSelectionMode) {
                    setVisibilityIf(mActionIconContainer, if (hasActionIcons) INVISIBLE else GONE)
                    setVisibilityIf(mOptions, INVISIBLE)
                    return@subscribe
                }
                if (hasActionIcons) {
                    state.primaryAction?.let(::bindPrimaryViewer)
                        ?: state.pluginPrimaryAction?.let(::bindPrimaryPluginAction)
                    state.pluginPrimaryAction?.let { action ->
                        mPrimaryViewer.contentDescription = action.menuTitle
                    }
                    setVisibilityIf(mPrimaryViewer, hasPrimaryAction)
                    setVisibilityIf(mEdit, state.editVisible)
                    bindRunAction(state.runAsMedia)
                    setVisibilityIf(mRun, state.runVisible)
                    setVisibilityIf(mInstall, state.installVisible)
                    setVisibilityIf(mInfo, state.infoVisible)
                    setVisibilityIf(mActionIconContainer, true)
                } else {
                    setVisibilityIf(mActionIconContainer, false)
                }
            }
    }

    private fun setVisibilityWith(view: View, callable: () -> Boolean) {
        Observable.fromCallable(callable)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isVisible ->
                if (view.isVisible != isVisible) {
                    view.isVisible = isVisible
                }
            }
    }

    private fun setVisibilityIf(view: View, visibility: Int) {
        if (view.visibility != visibility) {
            view.visibility = visibility
        }
    }

    private fun setVisibilityIf(view: View, visible: Boolean) {
        setVisibilityIf(view, if (visible) VISIBLE else GONE)
    }

    override fun onViewRecycled() {
        super.onViewRecycled()
        mBindGeneration++
        mActionBindGeneration++
        mName.text = null
        mFileDate.text = null
        mFileSize.text = null
        mItem.setBackgroundColor(host.context.getColor(R.color.item_background_dark))
        setVisibilityIf(mFirstChar, INVISIBLE)
        setVisibilityIf(mActionIconContainer, false)
        setVisibilityIf(mOptions, true)
    }

    private fun onItemClick() {
        if (host.isInSelectionMode) {
            host.toggleItemSelection(mExplorerItem)
            return
        }
        if (mExplorerItem is PrivilegedExplorerItem) {
            host.explorerView.openPrivilegedFile(mExplorerItem)
            return
        }
        // A saved layout opens in the layout inspector rather than in the text editor.
        // zh-CN: 已保存的布局在布局分析中打开, 而非文本编辑器.
        if (mExplorerItem !is ArchiveExplorerItem && LayoutSnapshotFiles.isLayoutFile(mExplorerItem.name)) {
            LayoutSnapshotViewer.open(host.context, mExplorerItem.path)
            host.notifyItemOperated(mExplorerItem)
            return
        }
        if (mExplorerItem is ArchiveExplorerFileItem) {
            host.openArchiveEntry(
                mExplorerItem as ArchiveExplorerFileItem,
                ExplorerPrimaryActionResolver.resolve(mExplorerItem.type),
            )
            host.notifyItemOperated(mExplorerItem)
            return
        }
        if (
            host.launchOfficialMediaOrShowGuidance(
                ExplorerActionEntryPoint.DEFAULT_CLICK,
                mExplorerItem,
            )
        ) {
            host.notifyItemOperated(mExplorerItem)
            return
        }
        if (
            host.launchDocumentPreviewerOrShowGuidance(
                ExplorerActionEntryPoint.DEFAULT_CLICK,
                mExplorerItem,
            )
        ) {
            host.notifyItemOperated(mExplorerItem)
            return
        }
        val pluginAction = host.resolvePluginAction(
            ExplorerActionEntryPoint.DEFAULT_CLICK,
            mExplorerItem,
        )
        if (pluginAction == null) {
            dispatchHostItemClick()
            return
        }
        if (ExplorerActionExecution.withFallback(
                attempt = {
                    host.launchPluginAction(
                        ExplorerActionEntryPoint.DEFAULT_CLICK,
                        pluginAction,
                        mExplorerItem,
                    )
                },
                fallback = ::dispatchHostItemClick,
            )
        ) {
            host.notifyItemOperated(mExplorerItem)
        }
    }

    private fun dispatchHostItemClick() {
        host.dispatchHostItemClick(itemView, mExplorerItem)
    }

    private fun bindSelectionState(item: ExplorerItem) {
        val selected = host.isItemSelected(item)
        mItem.isSelected = selected
        mItem.setBackgroundColor(host.context.getColor(if (selected) R.color.window_background_selected else R.color.item_background_dark))
        setVisibilityIf(
            mOptions,
            when {
                item is ArchiveExplorerItem -> GONE
                host.isInSelectionMode -> INVISIBLE
                else -> VISIBLE
            },
        )
        if (host.isInSelectionMode) {
            if (mActionIconContainer.isVisible) {
                setVisibilityIf(mActionIconContainer, INVISIBLE)
            }
        }
    }

    private fun bindPrimaryViewer(action: ExplorerPrimaryAction) {
        val (iconRes, contentDescriptionRes) = when (action) {
            ExplorerPrimaryAction.DOCUMENT_PREVIEWER -> {
                R.drawable.ic_previewer_web_page to R.string.text_preview
            }
            ExplorerPrimaryAction.IMAGE_VIEW -> {
                R.drawable.ic_eye to R.string.text_view
            }
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL -> {
                R.drawable.ic_eye to R.string.text_view
            }
            ExplorerPrimaryAction.MEDIA_PLAY -> {
                R.drawable.ic_play_media to R.string.text_play
            }
        }
        mPrimaryViewer.setImageResource(iconRes)
        mPrimaryViewer.contentDescription = host.context.getString(contentDescriptionRes)
    }

    private fun bindPrimaryPluginAction(action: ResolvedExplorerAction) {
        mPrimaryViewer.setImageResource(
            if (action.descriptor.presentation == org.autojs.plugin.explorer.api.ExplorerActionValues.PRESENTATION_HOST_EXPLORER) {
                R.drawable.ic_eye
            } else {
                R.drawable.ic_explorer_plugin_action
            },
        )
        mPrimaryViewer.contentDescription = action.menuTitle
    }

    private fun bindRunAction(asMedia: Boolean) {
        mRun.setImageResource(
            if (asMedia) R.drawable.ic_play_media else R.drawable.ic_run_explorer_btn,
        )
        mRun.contentDescription = host.context.getString(
            if (asMedia) R.string.text_play else R.string.text_run,
        )
    }

    private fun openPrimaryViewer() {
        val action = ExplorerPrimaryActionResolver.resolve(mExplorerItem.type)
        val pluginAction = mPrimaryPluginAction
        if (action == null && pluginAction == null) return
        host.hideHostDialog()
        if (mExplorerItem is ArchiveExplorerFileItem) {
            host.openArchiveEntry(mExplorerItem as ArchiveExplorerFileItem, action)
            host.notifyItemOperated(mExplorerItem)
            return
        }
        if (
            host.launchOfficialMediaOrShowGuidance(
                entryPoint = ExplorerActionEntryPoint.PRIMARY_BUTTON,
                item = mExplorerItem,
                preResolvedAction = pluginAction,
            )
        ) {
            host.notifyItemOperated(mExplorerItem)
            return
        }
        if (
            host.launchDocumentPreviewerOrShowGuidance(
                entryPoint = ExplorerActionEntryPoint.PRIMARY_BUTTON,
                item = mExplorerItem,
                preResolvedAction = pluginAction,
            )
        ) {
            host.notifyItemOperated(mExplorerItem)
            return
        }
        val fallback = { openHostPrimaryViewer(action) }
        if (pluginAction != null) {
            ExplorerActionExecution.withFallback(
                attempt = {
                    host.launchPluginAction(
                        ExplorerActionEntryPoint.PRIMARY_BUTTON,
                        pluginAction,
                        mExplorerItem,
                    )
                },
                fallback = fallback,
            )
        } else {
            fallback()
        }
        host.notifyItemOperated(mExplorerItem)
    }

    private fun openHostPrimaryViewer(action: ExplorerPrimaryAction?) {
        when (action) {
            ExplorerPrimaryAction.DOCUMENT_PREVIEWER -> {
                host.launchDocumentPreviewerOrShowGuidance(
                    ExplorerActionEntryPoint.PRIMARY_BUTTON,
                    mExplorerItem,
                )
            }
            ExplorerPrimaryAction.IMAGE_VIEW,
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
            null,
            -> {
                mExplorerItem.view(host.context)
            }
            ExplorerPrimaryAction.MEDIA_PLAY -> {
                mExplorerItem.play(host.context)
            }
        }
    }

    private fun run() {
        host.hideHostDialog()
        when {
            mExplorerItem.isExecutable -> {
                Scripts.runInteractive(host.context, ScriptFile(mExplorerItem.path))
                host.notifyItemOperated(mExplorerItem)
            }
            host.launchOfficialMediaOrShowGuidance(
                ExplorerActionEntryPoint.PRIMARY_BUTTON,
                mExplorerItem,
            ) -> {
                host.notifyItemOperated(mExplorerItem)
            }
            mExplorerItem.isMediaPlayable -> {
                mExplorerItem.play(host.explorerView)
                host.notifyItemOperated(mExplorerItem)
            }
        }
    }

    private fun edit() {
        host.hideHostDialog()

        when {
            mExplorerItem.isTextEditable -> {
                Scripts.edit(host.context, ScriptFile(mExplorerItem.path))
                host.notifyItemOperated(mExplorerItem)
            }
            mExplorerItem.isExternalEditable -> {
                mExplorerItem.edit(host.explorerView)
                host.notifyItemOperated(mExplorerItem)
            }
        }
    }

    private fun showInfo(
        item: ExplorerItem = mExplorerItem,
        primaryPluginAction: ResolvedExplorerAction? = mPrimaryPluginAction,
    ) {
        // Hide host overlay dialog so the info dialog can be shown on top.
        // zh-CN: 隐藏宿主 overlay 对话框, 让信息对话框显示在最上层.
        host.hideHostDialog()

        when {
            primaryPluginAction?.descriptor?.presentation ==
                org.autojs.plugin.explorer.api.ExplorerActionValues.PRESENTATION_HOST_EXPLORER -> {
                val action = host.pluginActionProvider?.actionsFor(
                    host.pluginActionSurface,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                    item,
                )?.firstOrNull { it.descriptor.id == "manage-archive" } ?: return
                host.launchPluginAction(ExplorerActionEntryPoint.OVERFLOW_MENU, action, item)
                host.notifyItemOperated(item)
            }
            item.type.isImageFile() -> {
                ImageInfoDialogManager.showImageInfoDialog(host.context, File(item.path)) {
                    host.showHostDialog()
                }
                host.notifyItemOperated(item)
            }
            item.isInstallable -> {
                val packageItem = item
                val archiveActions = host.pluginActionProvider?.actionsFor(
                    host.pluginActionSurface,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                    packageItem,
                ).orEmpty().filter {
                    it.descriptor.presentation == org.autojs.plugin.explorer.api.ExplorerActionValues.PRESENTATION_HOST_EXPLORER ||
                        it.descriptor.id == "extract-to"
                }.associate { action ->
                    action.menuTitle to {
                        host.launchPluginAction(ExplorerActionEntryPoint.OVERFLOW_MENU, action, packageItem)
                        Unit
                    }
                }
                ApkInfoDialogManager.showApkInfoDialogWithActions(host.context, packageItem.toScriptFile(), archiveActions) {
                    // Restore host dialog after the info dialog is dismissed.
                    // zh-CN: 信息对话框关闭后恢复宿主对话框.
                    host.showHostDialog()
                }
                host.notifyItemOperated(item)
            }
            item.isMediaMenu || item.isMediaPlayable -> {
                val mediaItem = item
                MediaInfoDialogManager.showMediaInfoDialog(
                    context = host.context,
                    explorerItem = mediaItem,
                    builderApplier = null,
                    onDismissListener = {
                        // Restore host dialog after the info dialog is dismissed.
                        // zh-CN: 信息对话框关闭后恢复宿主对话框.
                        host.showHostDialog()
                    },
                    onPlayRequested = {
                        host.launchOfficialMediaOrShowGuidance(
                            ExplorerActionEntryPoint.MEDIA_INFO_DIALOG,
                            mediaItem,
                        ) || (
                            host.resolvePluginAction(ExplorerActionEntryPoint.MEDIA_INFO_DIALOG, mediaItem)?.let { action ->
                                host.launchPluginAction(ExplorerActionEntryPoint.MEDIA_INFO_DIALOG, action, mediaItem)
                            } == true
                        )
                    },
                )
                host.notifyItemOperated(item)
            }
        }
    }

    private fun install() {
        host.hideHostDialog()
        when {
            mExplorerItem.isInstallable -> {
                mExplorerItem.install(host.explorerView)
                host.notifyItemOperated(mExplorerItem)
            }
            else -> {
                mExplorerItem.view(host.explorerView)
                host.notifyItemOperated(mExplorerItem)
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun showOptionsMenu() {
        if (mExplorerItem is PrivilegedExplorerItem) {
            host.explorerView.showPrivilegedFileMenu(mOptions, mExplorerItem)
            return
        }
        val actionItem = mExplorerItem
        val actionPrimaryPlugin = mPrimaryPluginAction
        val popupMenu = PopupMenu(host.context, mOptions)
        popupMenu.inflate(R.menu.menu_script_options)
        val menu = popupMenu.menu
        // Keep the host package information reachable when a plugin fills its row icon slot.
        if (!actionItem.isInstallable || mInfo.isVisible) {
            menu.removeItem(R.id.action_info)
        }
        if (!mExplorerItem.type.isImageFile()) {
            menu.removeItem(R.id.action_edit_by_other_apps)
        }
        if (!mExplorerItem.isExecutable) {
            menu.removeItem(R.id.action_create_shortcut)
            menu.removeItem(R.id.action_timed_task)
            menu.removeItem(R.id.action_run_on_virtual_screen)
            menu.removeItem(R.id.action_run_repeatedly)
            menu.removeItem(R.id.action_floating_run)
        }
        if (!mExplorerItem.canMove()) {
            menu.removeItem(R.id.action_move_to)
        }
        if (!mExplorerItem.canCopy()) {
            menu.removeItem(R.id.action_copy_to)
        }
        if (!mExplorerItem.canRename()) {
            menu.removeItem(R.id.action_rename)
        }
        if (!mExplorerItem.canDelete()) {
            menu.removeItem(R.id.action_delete)
        }
        if (!mExplorerItem.isTextEditable) {
            menu.removeItem(R.id.action_version_history)
        }
        if (!mExplorerItem.canBuildApk()) {
            menu.removeItem(R.id.action_build_apk)
        }
        if (!mExplorerItem.canSetAsWorkingDir()) {
            menu.removeItem(R.id.action_set_as_working_dir)
        }
        val samplePath = PFile(host.context.filesDir, WorkspaceFileProvider.SAMPLE_PATH).path
        if (!mExplorerItem.path.startsWith(samplePath)) {
            menu.removeItem(R.id.action_reset)
        }
        // "Run with AutoJs4" is offered only for the files AutoJs4 can execute (.js and .auto);
        // when the plugin is missing, the same slot shows the install guidance instead.
        if (!AutoJs4ScriptSource.isAutoJs4FileType(mExplorerItem.type)) {
            menu.removeItem(R.id.action_run_with_autojs4)
            menu.removeItem(R.id.action_compare_with_autojs4)
            menu.removeItem(R.id.action_install_autojs4_runtime)
        } else if (AutoJs4RuntimePluginUi.isInstalled(host.context)) {
            menu.removeItem(R.id.action_install_autojs4_runtime)
        } else {
            menu.removeItem(R.id.action_run_with_autojs4)
            menu.removeItem(R.id.action_compare_with_autojs4)
        }
        val pluginMenuItems = mutableMapOf<Int, org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction>()
        runCatching {
            host.pluginActionProvider?.actionsFor(
                host.pluginActionSurface,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                actionItem,
            ).orEmpty()
        }
            .getOrElse { error ->
                Log.w(LOG_TAG, "Failed to resolve Explorer plugin actions", error)
                emptyList()
            }
            .forEachIndexed { index, action ->
                val menuId = View.generateViewId()
                menu.add(
                    Menu.NONE,
                    menuId,
                    PLUGIN_ACTION_MENU_ORDER_START + index,
                    action.menuTitle,
                )
                pluginMenuItems[menuId] = action
            }
        popupMenu.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_info) {
                showInfo(actionItem, actionPrimaryPlugin)
                return@setOnMenuItemClickListener true
            }
            val pluginAction = pluginMenuItems[menuItem.itemId]
            if (pluginAction == null) {
                return@setOnMenuItemClickListener host.onBuiltInMenuItem(menuItem)
            }
            host.dispatchPluginAction(
                host.context,
                host.pluginActionSurface,
                ExplorerActionEntryPoint.OVERFLOW_MENU,
                listOf(actionItem),
                pluginAction,
            )
                .onSuccess { host.notifyItemOperated(mExplorerItem) }
                .onFailure { error ->
                    ViewUtils.showToast(host.context, error.message ?: error.toString(), true)
                }
            true
        }
        popupMenu.show()
    }

    private fun <R> withItemSelected(func: () -> R): R {
        host.selectItem(mExplorerItem)
        return func.invoke()
    }
}
