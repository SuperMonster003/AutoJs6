package org.autojs.autojs.ui.explorer

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerPage
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerProvider
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveSessionConnection
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerChangeEvent
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerProjectToolbar.OnOperateListener
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs.util.WorkingDirectoryUtils.path
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerViewBinding
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.util.Objects
import java.util.Stack
import java.util.concurrent.Callable

private data class ArchiveNavigationState(
    val provider: ArchiveExplorerProvider,
    val previousExplorer: Explorer,
    val previousRootPath: String?,
    val previousPageState: ExplorerView.ExplorerPageState,
    val previousHistory: List<ExplorerView.ExplorerPageState>,
    val sourceParentSegments: List<ExplorerPathSegment>,
)

/**
 * Created by Stardust on Aug 21, 2017.
 * Transformed by SuperMonster003 on Nov 23, 2024.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 */
@SuppressLint("CheckResult", "NonConstantResourceId", "NotifyDataSetChanged")
open class ExplorerView : ThemeColorSwipeRefreshLayout,
    SwipeRefreshLayout.OnRefreshListener,
    PopupMenu.OnMenuItemClickListener {

    private var binding: ExplorerViewBinding? = null

    @JvmField
    internal var isProjectRecognitionEnabled = true

    @JvmField
    var onItemClickListener: OnItemClickListener? = null

    @JvmField
    var selectedItem: ExplorerItem? = null

    @JvmField
    var currentPageState: ExplorerPageState = ExplorerPageState()

    @JvmField
    var explorerItemManager: ExplorerItemManager = ExplorerItemManager(context)

    @JvmField
    var isDirSortMenuShowing = false

    val currentPage: ExplorerPage
        get() = currentPageState.page!!

    val currentDirectory: ScriptFile
        get() = currentPage.toScriptFile()

    private val isInSelectionMode: Boolean
        get() = mSelection.isActive

    var explorerItemListView: RecyclerView? = null
        private set

    private lateinit var mProjectToolbar: ExplorerProjectToolbar

    private var mExplorer: Explorer? = null
    private var mExplorerRootPath: String? = null

    private val mExplorerAdapter = ExplorerAdapter(
        itemManager = { explorerItemManager },
        pageState = { currentPageState },
        viewHolderFactory = { inflater, parent, viewType ->
            onCreateViewHolder(inflater, parent, viewType)
        },
    )
    private var mFilter: ((ExplorerItem) -> Boolean)? = null
    private val mSelection = ExplorerSelectionController(::isItemSelectable)
    private lateinit var mSelectionToolbar: ExplorerSelectionToolbarController

    private var mOnItemOperateListener: OnItemOperateListener? = null
    private var mOnSelectionToolbarStateChangeListener: OnSelectionToolbarStateChangeListener? = null
    private val mPluginActions by lazy(LazyThreadSafetyMode.NONE) {
        ExplorerPluginActionController { provider, surface, entryPoint, item, action ->
            mArchiveCoordinator.openArchive(provider, surface, entryPoint, item, action)
        }
    }
    private val mBuiltInActions by lazy(LazyThreadSafetyMode.NONE) {
        ExplorerBuiltInActionHandler(object : ExplorerBuiltInActionHost {
            override val context: Context
                get() = this@ExplorerView.context
            override val explorerView: ExplorerView
                get() = this@ExplorerView
            override val currentPage: ExplorerPage
                get() = this@ExplorerView.currentPage
            override val selectedItem: ExplorerItem?
                get() = this@ExplorerView.selectedItem

            override fun showError(error: Throwable, titleRes: Int) {
                showErrorDialog(error, titleRes)
            }

            override fun notifyItemOperated() {
                this@ExplorerView.notifyItemOperated()
            }

            override fun hideHostDialog() {
                mRequestHostDialogHide?.run()
            }

            override fun resetSucceeded() {
                this@ExplorerView.resetSucceeded()
            }

            override fun resetFailed() {
                this@ExplorerView.resetFailed()
            }
        })
    }
    private var mArchiveNavigationState: ArchiveNavigationState? = null
    private val mArchiveCoordinator by lazy(LazyThreadSafetyMode.NONE) {
        ExplorerArchiveCoordinator(
            object : ExplorerArchiveCoordinatorHost {
                override val archiveContext: Context
                    get() = context

                override val archiveLifecycleOwner
                    get() = findViewTreeLifecycleOwner()

                override fun setArchiveBusy(busy: Boolean) {
                    isRefreshing = busy
                }

                override fun postArchiveUi(action: () -> Unit) {
                    post { action() }
                }

                override fun onArchiveOpened(connection: ExplorerArchiveSessionConnection) {
                    enterArchiveNavigation(connection)
                }

                override fun isArchiveProviderActive(provider: ArchiveExplorerProvider): Boolean {
                    return mArchiveNavigationState?.provider === provider
                }

                override fun cancelExplorerRefresh() {
                    mRefreshDisposable?.dispose()
                    mRefreshDisposable = null
                }

                override fun applyArchiveReindex(result: ExplorerArchiveReindexResult) {
                    applyArchiveReindexResult(result)
                }

                override fun refreshArchivePage() {
                    refreshCurrentPage()
                }

                override fun openMaterializedArchiveEntry(
                    file: File,
                    displayName: String,
                    action: ExplorerPrimaryAction?,
                ) {
                    openMaterializedArchiveEntryResult(file, displayName, action)
                }

                override fun onArchiveWorkflowStateChanged() {
                    updateArchiveWorkflowState()
                }
            },
        )
    }
    private val mExplorerItemViewHost = object : ExplorerItemViewHost {
        override val context: Context
            get() = this@ExplorerView.context

        override val explorerView: ExplorerView
            get() = this@ExplorerView

        override val actionIconTint: ColorStateList
            get() = mActionIconTint

        override val isInSelectionMode: Boolean
            get() = this@ExplorerView.isInSelectionMode

        override val pluginActionProvider: ExplorerPluginActionProvider?
            get() = mPluginActions.provider

        override val pluginActionSurface: ExplorerSurface
            get() = mPluginActions.surface

        override fun selectItem(item: ExplorerItem) {
            selectedItem = item
        }

        override fun enterSelectionMode(item: ExplorerItem): Boolean {
            return this@ExplorerView.enterSelectionMode(item)
        }

        override fun blockListScrollUntilTouchRelease() {
            this@ExplorerView.blockListScrollUntilTouchRelease()
        }

        override fun toggleItemSelection(item: ExplorerItem) {
            this@ExplorerView.toggleItemSelection(item)
        }

        override fun isItemSelected(item: ExplorerItem): Boolean {
            return this@ExplorerView.isItemSelected(item)
        }

        override fun selectPluginAction(
            item: ExplorerItem,
            actions: List<ResolvedExplorerAction>,
        ): ResolvedExplorerAction? = this@ExplorerView.selectPluginAction(item, actions)

        override fun openArchiveEntry(item: ArchiveExplorerFileItem, action: ExplorerPrimaryAction?) {
            this@ExplorerView.openArchiveEntry(item, action)
        }

        override fun notifyItemOperated(item: ExplorerItem) {
            selectedItem = item
            this@ExplorerView.notifyItemOperated()
        }

        override fun launchOfficialMediaOrShowGuidance(
            entryPoint: ExplorerActionEntryPoint,
            item: ExplorerItem,
            preResolvedAction: ResolvedExplorerAction?,
        ): Boolean = this@ExplorerView.launchOfficialMediaOrShowGuidance(
            entryPoint,
            item,
            preResolvedAction,
        )

        override fun launchDocumentPreviewerOrShowGuidance(
            entryPoint: ExplorerActionEntryPoint,
            item: ExplorerItem,
            preResolvedAction: ResolvedExplorerAction?,
        ): Boolean = this@ExplorerView.launchDocumentPreviewerOrShowGuidance(
            entryPoint,
            item,
            preResolvedAction,
        )

        override fun resolvePluginAction(
            entryPoint: ExplorerActionEntryPoint,
            item: ExplorerItem,
        ): ResolvedExplorerAction? = this@ExplorerView.resolvePluginAction(entryPoint, item)

        override fun launchPluginAction(
            entryPoint: ExplorerActionEntryPoint,
            pluginAction: ResolvedExplorerAction,
            item: ExplorerItem,
        ): Boolean = this@ExplorerView.launchPluginAction(entryPoint, pluginAction, item)

        override fun dispatchHostItemClick(itemView: View, item: ExplorerItem) {
            selectedItem = item
            onItemClickListener?.let { listener ->
                listener.onItemClick(itemView, item)
                this@ExplorerView.notifyItemOperated()
            }
        }

        override fun hideHostDialog() {
            mRequestHostDialogHide?.run()
        }

        override fun showHostDialog() {
            mRequestHostDialogShow?.run()
        }

        override fun onBuiltInMenuItem(item: MenuItem): Boolean {
            return this@ExplorerView.onMenuItemClick(item)
        }

        override fun dispatchPluginAction(
            context: Context,
            surface: ExplorerSurface,
            entryPoint: ExplorerActionEntryPoint,
            items: List<ExplorerItem>,
            action: ResolvedExplorerAction,
        ): Result<Unit> = this@ExplorerView.dispatchPluginAction(
            context,
            surface,
            entryPoint,
            items,
            action,
        )
    }
    private val mExplorerPageViewHost = object : ExplorerPageViewHost {
        override val context: Context
            get() = this@ExplorerView.context

        override val explorerView: ExplorerView
            get() = this@ExplorerView

        override val actionIconTint: ColorStateList
            get() = mActionIconTint

        override val isInSelectionMode: Boolean
            get() = this@ExplorerView.isInSelectionMode

        override val isProjectRecognitionEnabled: Boolean
            get() = this@ExplorerView.isProjectRecognitionEnabled

        override val pluginActionProvider: ExplorerPluginActionProvider?
            get() = mPluginActions.provider

        override val pluginActionSurface: ExplorerSurface
            get() = mPluginActions.surface

        override val currentPage: ExplorerPage
            get() = this@ExplorerView.currentPage

        override fun selectItem(item: ExplorerItem) {
            selectedItem = item
        }

        override fun enterSelectionMode(item: ExplorerItem): Boolean {
            return this@ExplorerView.enterSelectionMode(item)
        }

        override fun blockListScrollUntilTouchRelease() {
            this@ExplorerView.blockListScrollUntilTouchRelease()
        }

        override fun isItemSelected(item: ExplorerItem): Boolean {
            return this@ExplorerView.isItemSelected(item)
        }

        override fun toggleItemSelection(item: ExplorerItem) {
            this@ExplorerView.toggleItemSelection(item)
        }

        override fun enterDirectChildPage(page: ExplorerPage) {
            this@ExplorerView.enterDirectChildPage(page)
        }

        override fun dispatchPluginAction(
            context: Context,
            surface: ExplorerSurface,
            entryPoint: ExplorerActionEntryPoint,
            items: List<ExplorerItem>,
            action: ResolvedExplorerAction,
        ): Result<Unit> = this@ExplorerView.dispatchPluginAction(
            context,
            surface,
            entryPoint,
            items,
            action,
        )

        override fun notifyItemOperated(item: ExplorerItem) {
            selectedItem = item
            this@ExplorerView.notifyItemOperated()
        }

        override fun hideHostDialog() {
            mRequestHostDialogHide?.run()
        }

        override fun showErrorDialog(error: Throwable, titleRes: Int) {
            this@ExplorerView.showErrorDialog(error, titleRes)
        }

        override fun resetSucceeded() {
            this@ExplorerView.resetSucceeded()
        }

        override fun resetFailed() {
            this@ExplorerView.resetFailed()
        }

        override fun onBuiltInMenuItem(item: MenuItem): Boolean {
            return this@ExplorerView.onMenuItemClick(item)
        }
    }
    private val mExplorerCategoryViewHost = object : ExplorerCategoryViewHost {
        override val context: Context
            get() = this@ExplorerView.context

        override val chevronIconTint: ColorStateList
            get() = mChevronIconTint

        override val itemManager: ExplorerItemManager
            get() = explorerItemManager

        override val pageState: ExplorerPageState
            get() = currentPageState

        override var isDirSortMenuShowing: Boolean
            get() = this@ExplorerView.isDirSortMenuShowing
            set(value) {
                this@ExplorerView.isDirSortMenuShowing = value
            }

        override fun sort(sortType: Int, isDir: Boolean, isSortedAscending: Boolean) {
            this@ExplorerView.sort(sortType, isDir, isSortedAscending)
        }

        override fun canGoUp(): Boolean = this@ExplorerView.canGoUp()

        override fun goUp() {
            this@ExplorerView.goUp()
        }

        override fun notifyDataSetChanged() {
            this@ExplorerView.notifyDataSetChanged()
        }
    }
    private var mOnArchiveModeStateChangeListener: ((Boolean) -> Unit)? = null

    private val mPageStateHistory = Stack<ExplorerPageState>()
    private var mDirectorySpanSize = 2

    // Keep only one refresh chain running to avoid concurrent updates.
    // zh-CN: 只允许一个刷新链路运行, 避免并发更新导致竞态问题.
    private var mRefreshDisposable: Disposable? = null

    // True after a page navigation or state restore; the next refresh then repopulates fully
    // and anchors the recorded scroll position. In-place refreshes keep the live position.
    // zh-CN: 页面导航或状态恢复后为 true, 下一次刷新全量填充并锚定记录的滚动位置;
    // 原地刷新则保持当前滚动位置不变.
    private var mApplyPageStateScrollOnRefresh = false

    // Metadata fingerprints (path -> lastModified/size mix) captured by the latest refresh,
    // used to diff the next refresh so unchanged rows are not rebound.
    // zh-CN: 最近一次刷新采集的元数据指纹 (path -> lastModified/size 混合值),
    // 用于与下一次刷新做差量比较, 未变化的行不再重新绑定.
    private var mRefreshMetaByPath: Map<String, Long> = emptyMap()

    // Unify action icon tint to match day/night theme, especially for overlay dialogs.
    // zh-CN: 统一操作图标着色以匹配日/夜主题, 尤其用于 overlay 对话框场景.
    private val mActionIconTint by lazy {
        ColorStateList.valueOf(context.getColor(R.color.explorer_file_operation_button))
    }

    private val mChevronIconTint by lazy {
        ColorStateList.valueOf(context.getColor(R.color.explorer_category_operation_button))
    }

    // Request host dialog to hide/show without losing state.
    // zh-CN: 请求宿主对话框隐藏/显示且不丢失状态.
    private var mRequestHostDialogHide: Runnable? = null
    private var mRequestHostDialogShow: Runnable? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    // Set hide callback from Java callers (e.g. CircularMenu).
    // zh-CN: 提供给 Java 调用方 (例如 CircularMenu) 设置 hide 回调.
    fun setRequestHostDialogHide(runnable: Runnable?) {
        mRequestHostDialogHide = runnable
    }

    // Set show callback from Java callers (e.g. CircularMenu).
    // zh-CN: 提供给 Java 调用方 (例如 CircularMenu) 设置 show 回调.
    fun setRequestHostDialogShow(runnable: Runnable?) {
        mRequestHostDialogShow = runnable
    }

    private fun init(context: Context) {
        ExplorerViewBinding.inflate(LayoutInflater.from(context), this, true).also { binding ->
            this.binding = binding
            binding.explorerItemList.also {
                this.explorerItemListView = it
                it.adapter = mExplorerAdapter
                it.setHasFixedSize(true)
                it.layoutManager = GridLayoutManager(this.context, 2).also { manager ->
                    manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int) = when {
                            position > mExplorerAdapter.positionOfDirectoryCategory &&
                                position < positionOfCategoryFile() -> {
                                mDirectorySpanSize // For directories
                            }
                            else -> 2 // For files and category
                        }
                    }
                }
            }
            binding.projectToolbar.also {
                mProjectToolbar = it
            }
            binding.explorerPathBar.setOnParentPathClickListener(::navigateToPath)
            binding.archiveFilenameEncoding.setOnClickListener {
                showArchiveFilenameEncodingDialog()
            }
            binding.archiveCreateDirectory.setOnClickListener {
                showArchiveCreateDirectoryDialog()
            }
            binding.archiveAddEntries.setOnClickListener {
                showArchiveAddEntriesDialog()
            }
            binding.archiveExtractCurrent.setOnClickListener { extractCurrentArchiveFolder() }
            mSelectionToolbar = ExplorerSelectionToolbarController(
                context = context,
                binding = binding,
                recyclerView = binding.explorerItemList,
                isSelectionActive = { isInSelectionMode },
                setRefreshEnabled = { enabled -> isEnabled = enabled },
                onClose = { clearSelection() },
                onShownChanged = { shown ->
                    mOnSelectionToolbarStateChangeListener?.onSelectionToolbarStateChanged(shown)
                },
            )
        }
        restoreSortConfig()
        setOnRefreshListener(this)
    }

    fun setOnSelectionToolbarStateChangeListener(listener: OnSelectionToolbarStateChangeListener?) {
        mOnSelectionToolbarStateChangeListener = listener
    }

    fun excludeListPaddingFromBottomNavigationBar(
        extraPaddingBottomDp: Float = 0F,
        clipToPadding: Boolean = false,
    ) {
        mSelectionToolbar.configureListBottomPadding(extraPaddingBottomDp, clipToPadding)
    }

    private fun blockListScrollUntilTouchRelease() {
        mSelectionToolbar.blockListScroll()
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        super.setThemeColor(themeColor)
        if (::mSelectionToolbar.isInitialized) mSelectionToolbar.applyTheme(themeColor)
    }
    @Subscribe
    fun onExplorerChange(event: ExplorerChangeEvent) {
        Log.d(LOG_TAG, "on explorer change: $event")
        if ((event.action == ExplorerChangeEvent.ALL)) {
            refreshCurrentPage()
            return
        }
        val currentDirPath = currentPage.path
        val changedDirPath = event.page.path
        val item = event.item
        val changedItemPath = item?.path
        if (currentDirPath == changedItemPath || (currentDirPath == changedDirPath && event.action == ExplorerChangeEvent.CHILDREN_CHANGE)) {
            refreshCurrentPage()
            return
        }
        if (currentDirPath == changedDirPath) {
            val i: Int
            when (event.action) {
                ExplorerChangeEvent.CHANGE -> {
                    i = explorerItemManager.update(item, event.newItem)
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemChanged(item, i)
                    }
                }
                ExplorerChangeEvent.CREATE -> {
                    explorerItemManager.insertAtFront(event.newItem)
                    mExplorerAdapter.notifyItemInserted(event.newItem, 0)
                    mExplorerAdapter.notifyItemChanged(mExplorerAdapter.positionOfDirectoryCategory)
                    mExplorerAdapter.notifyItemChanged(positionOfCategoryFile())
                }
                ExplorerChangeEvent.REMOVE -> {
                    i = explorerItemManager.remove(item)
                    if (item != null && mSelection.remove(item.path)) {
                        updateSelectionToolbar()
                    }
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i)
                        mExplorerAdapter.notifyItemChanged(mExplorerAdapter.positionOfDirectoryCategory)
                        mExplorerAdapter.notifyItemChanged(positionOfCategoryFile())
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mExplorer?.registerChangeListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mArchiveCoordinator.release()
        closeArchiveNavigation(restorePrevious = false)
        mExplorer?.unregisterChangeListener(this)
        mPageStateHistory.clear()
        if (::mSelectionToolbar.isInitialized) mSelectionToolbar.detach()
        binding = null

        // Cancel pending refresh to avoid late UI updates and concurrent iterations.
        // zh-CN: 取消未完成的刷新, 避免延迟 UI 更新以及并发遍历导致的问题.
        mRefreshDisposable?.dispose()
        mRefreshDisposable = null
    }

    override fun onRefresh() {
        currentPageState.scrollY = 0
        mExplorer?.notifyChildrenChanged(currentPage)
        if (mArchiveNavigationState == null) mProjectToolbar.refresh()
        updateProjectToolbarVisibility()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val archiveItem = selectedItem as? ArchiveExplorerItem
        if (archiveItem != null) {
            return when (item.itemId) {
                R.id.action_rename -> {
                    showArchiveRenameDialog(archiveItem)
                    true
                }
                R.id.action_delete -> {
                    confirmArchiveDeletion(listOf(archiveItem))
                    true
                }
                else -> false
            }
        }
        return mBuiltInActions.handle(item)
    }
    private fun showErrorDialog(t: Throwable, titleRes: Int) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(titleRes)
                .content(t.message ?: context.getString(R.string.error_unknown_exception))
                .apply {
                    t.message?.let { tMessage ->
                        neutralText(R.string.dialog_button_copy)
                        neutralColorRes(R.color.dialog_button_hint)
                        onNeutral { d, _ ->
                            ClipboardUtils.setClip(context, tMessage)
                            showSnack(d.view, R.string.text_already_copied_to_clip, false)
                        }
                    }
                }
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .onPositive { d, _ -> d.dismiss() }
                .cancelable(false)
                .autoDismiss(false)
                .build()
        }
    }

    protected open fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): BindableViewHolder<Any> {
        return when (viewType) {
            VIEW_TYPE_ITEM -> ExplorerItemViewHolder(
                inflater.inflate(R.layout.explorer_file, parent, false),
                mExplorerItemViewHost,
            )
            VIEW_TYPE_PAGE -> ExplorerPageViewHolder(
                inflater.inflate(R.layout.explorer_directory, parent, false),
                mExplorerPageViewHost,
            )
            VIEW_TYPE_CATEGORY -> ExplorerCategoryViewHolder(
                inflater.inflate(R.layout.explorer_category, parent, false),
                mExplorerCategoryViewHost,
            )
            else -> ExplorerItemViewHolder(
                inflater.inflate(R.layout.explorer_file, parent, false),
                mExplorerItemViewHost,
            )
        }
    }

    private fun resetSucceeded() {
        showSnack(this, R.string.text_reset_succeed)
    }

    private fun resetFailed() {
        showSnack(this, R.string.text_reset_to_initial_content_only_for_assets, true)
    }

    fun notifyItemOperated() {
        selectedItem?.let { mOnItemOperateListener?.onItemOperated(it) }
    }

    fun notifyDataSetChanged() {
        mExplorerAdapter.notifyDataSetChanged()
    }

    /** Refreshes only attached action buttons, avoiding a full row teardown and asynchronous repaint. */
    fun notifyPluginActionsChanged() {
        val recyclerView = explorerItemListView ?: return
        repeat(recyclerView.childCount) { index ->
            val child = recyclerView.getChildAt(index)
            (recyclerView.getChildViewHolder(child) as? ExplorerItemViewHolder)?.refreshActions()
        }
    }

    private fun enterSelectionMode(item: ExplorerItem): Boolean {
        if (!mSelection.enter(item)) return false
        updateSelectionToolbar()
        notifyDataSetChanged()
        return true
    }

    private fun toggleItemSelection(item: ExplorerItem) {
        if (!mSelection.toggle(item)) return
        updateSelectionToolbar()
        notifyDataSetChanged()
    }

    private fun clearSelection(notify: Boolean = true) {
        if (!mSelection.clear()) return
        updateSelectionToolbar()
        if (notify) {
            notifyDataSetChanged()
        }
    }

    private fun isItemSelectable(item: ExplorerItem) = when (item) {
        is ArchiveExplorerItem -> {
            val provider = mArchiveNavigationState?.provider
            provider != null &&
                (
                    provider.canExtractEntries && item.canExtractEntry ||
                        provider.canDeleteEntries && item.canDeleteEntry
                    )
        }
        else -> item.canCopy() || item.canMove() || item.canDelete()
    }

    private fun isItemSelected(item: ExplorerItem) = mSelection.contains(item)

    private fun selectedScriptFiles() = mSelection.items.map { it.toScriptFile() }

    private fun updateSelectionToolbar() {
        val selectedItems = mSelection.items
        val archiveSelection = selectedItems.isNotEmpty() &&
            selectedItems.all { it is ArchiveExplorerItem }
        val archiveExtraction = archiveSelection &&
            selectedItems.all { (it as ArchiveExplorerItem).canExtractEntry } &&
            mArchiveNavigationState?.provider?.canExtractEntries == true
        val archiveDeletion = archiveSelection &&
            selectedItems.all { (it as ArchiveExplorerItem).canDeleteEntry } &&
            mArchiveNavigationState?.provider?.canDeleteEntries == true
        // The plugin button runs the highest-priority action and "More" offers the others, so a second
        // plugin for the same files (an image converter next to an image viewer) stays reachable.
        // zh-CN: 插件按钮执行优先级最高的动作, "更多" 列出其余动作, 同类文件的第二个插件 (如图片查看器旁的图片转换) 也能用到.
        val pluginActions = if (selectedItems.isEmpty() || archiveSelection) {
            emptyList()
        } else {
            runCatching {
                mPluginActions.provider?.actionsFor(
                    mPluginActions.surface,
                    ExplorerActionEntryPoint.SELECTION_TOOLBAR,
                    selectedItems,
                ).orEmpty()
            }.onFailure { error ->
                Log.w(LOG_TAG, "Failed to resolve Explorer selection plugin action", error)
            }.getOrDefault(emptyList())
        }
        val deleteEnabled = if (archiveSelection) {
            archiveDeletion && !mArchiveCoordinator.isMutating
        } else {
            selectedItems.all { it.canDelete() }
        }
        val actions = buildList {
            if (archiveExtraction) {
                add(
                    ExplorerSelectionAction(
                        key = SELECTION_ACTION_EXTRACT,
                        label = context.getString(R.string.text_extract),
                        iconRes = R.drawable.ic_unarchive_black_48dp,
                        level = ExplorerSelectionActionLevel.PRIMARY,
                        enabled = !mArchiveCoordinator.isExtracting && !mArchiveCoordinator.isMutating,
                        onLongClick = { extractSelectedArchiveEntries(); true },
                        onClick = ::extractSelectedArchiveEntries,
                    ),
                )
            }
            if (!archiveSelection) {
                add(
                    ExplorerSelectionAction(
                        key = SELECTION_ACTION_COPY,
                        label = context.getString(R.string.text_copy_to),
                        iconRes = R.drawable.ic_content_copy_black_24dp,
                        iconPaddingDp = 1F,
                        level = ExplorerSelectionActionLevel.PRIMARY,
                        enabled = selectedItems.all { it.canCopy() },
                        onClick = ::copySelectedItems,
                    ),
                )
                add(
                    ExplorerSelectionAction(
                        key = SELECTION_ACTION_MOVE,
                        label = context.getString(R.string.text_move_to),
                        iconRes = R.drawable.ic_move_to,
                        iconPaddingDp = 1.5F,
                        level = ExplorerSelectionActionLevel.PRIMARY,
                        enabled = selectedItems.all { it.canMove() },
                        onClick = ::moveSelectedItems,
                    ),
                )
            }
            pluginActions.forEachIndexed { index, action ->
                add(
                    ExplorerSelectionAction(
                        key = "$SELECTION_ACTION_PLUGIN_PREFIX${action.packageName}/${action.descriptor.id}",
                        label = if (index == 0) action.actionLabel else action.menuTitle,
                        iconRes = R.drawable.ic_archive_black_48dp,
                        iconPaddingDp = 1F,
                        level = if (index == 0) {
                            ExplorerSelectionActionLevel.PRIMARY
                        } else {
                            ExplorerSelectionActionLevel.SECONDARY
                        },
                        group = ExplorerSelectionActionGroup.PLUGIN,
                        onClick = { launchPluginActionOnSelection(action) },
                    ),
                )
            }
            if (!archiveSelection || deleteEnabled) {
                add(
                    ExplorerSelectionAction(
                        key = SELECTION_ACTION_DELETE,
                        label = context.getString(R.string.text_delete),
                        iconRes = R.drawable.ic_delete_black_24dp,
                        level = ExplorerSelectionActionLevel.PRIMARY,
                        enabled = deleteEnabled,
                        onClick = ::deleteSelectedItems,
                    ),
                )
            }
            // Archive entries only have synthetic paths, which are of no use outside the archive view.
            // zh-CN: 压缩包条目只有合成路径, 离开压缩包视图后没有意义.
            if (!archiveSelection) {
                add(
                    ExplorerSelectionAction(
                        key = SELECTION_ACTION_COPY_PATH,
                        label = context.getString(R.string.text_copy_path),
                        iconRes = null,
                        level = ExplorerSelectionActionLevel.SECONDARY,
                        onClick = ::copySelectedPaths,
                    ),
                )
            }
        }
        if (::mSelectionToolbar.isInitialized) {
            mSelectionToolbar.render(ExplorerSelectionToolbarState(selectedItems.size, actions))
        }
        updateArchiveExtractionAction()
        updateProjectToolbarVisibility()
    }

    private fun copySelectedItems() {
        val selectedFiles = selectedScriptFiles()
        if (selectedFiles.isEmpty()) return
        ScriptOperations(context, this@ExplorerView, currentPage)
            .copy(selectedFiles)
            .subscribe({ clearSelection() }, { showErrorDialog(it, R.string.error_failed_to_copy) })
    }

    private fun moveSelectedItems() {
        val selectedFiles = selectedScriptFiles()
        if (selectedFiles.isEmpty()) return
        ScriptOperations(context, this@ExplorerView, currentPage)
            .move(selectedFiles)
            .subscribe({ clearSelection() }, { showErrorDialog(it, R.string.error_failed_to_move) })
    }

    private fun copySelectedPaths() {
        val selectedItems = mSelection.items
        if (selectedItems.isEmpty()) return
        ExplorerViewHelper.copyPaths(context, selectedItems)
        clearSelection()
    }

    private fun deleteSelectedItems() {
        val archiveItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem }
        if (archiveItems.size == mSelection.size && archiveItems.isNotEmpty()) {
            confirmArchiveDeletion(archiveItems)
            return
        }
        val selectedFiles = selectedScriptFiles()
        if (selectedFiles.isEmpty()) return
        ScriptOperations(context, this@ExplorerView, currentPage)
            .delete(selectedFiles)
        clearSelection()
    }

    fun setProjectToolbarRunnableOnly(b: Boolean) {
        mProjectToolbar.setRunnableOnly(b)
    }

    fun enterDirectChildPage(childItemGroup: ExplorerPage) {
        clearSelection()
        saveCurrentScrollPosition()
        saveCurrentPageIntoHistory()
        setCurrentPageState(ExplorerPageState(childItemGroup))
        refreshCurrentPage()
    }

    fun setOnItemClickListener(onItemClickListener: OnItemClickListener?) {
        this.onItemClickListener = onItemClickListener
    }

    internal fun setPluginActionProvider(
        surface: ExplorerSurface,
        provider: ExplorerPluginActionProvider?,
    ) {
        mPluginActions.setProvider(surface, provider)
        if (mSelection.isActive) {
            updateSelectionToolbar()
        }
    }

    private fun selectPluginAction(
        item: ExplorerItem,
        actions: List<ResolvedExplorerAction>,
    ): ResolvedExplorerAction? = mPluginActions.select(item, actions)

    private fun resolvePluginAction(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
    ): ResolvedExplorerAction? = mPluginActions.resolve(entryPoint, item)

    private fun launchOfficialMediaOrShowGuidance(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        preResolvedAction: ResolvedExplorerAction? = null,
    ): Boolean = mPluginActions.launchOfficialMediaOrShowGuidance(
        context,
        entryPoint,
        item,
        preResolvedAction,
    )

    private fun launchDocumentPreviewerOrShowGuidance(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        preResolvedAction: ResolvedExplorerAction? = null,
    ): Boolean = mPluginActions.launchDocumentPreviewerOrShowGuidance(
        context,
        entryPoint,
        item,
        preResolvedAction,
    )

    private fun launchPluginAction(
        entryPoint: ExplorerActionEntryPoint,
        pluginAction: ResolvedExplorerAction,
        item: ExplorerItem,
    ): Boolean = mPluginActions.launch(context, entryPoint, pluginAction, item)
    internal fun setOnArchiveModeStateChangeListener(listener: ((Boolean) -> Unit)?) {
        mOnArchiveModeStateChangeListener = listener
        listener?.invoke(mArchiveNavigationState != null)
    }

    internal fun releasePluginResources() {
        mArchiveCoordinator.release()
        closeArchiveNavigation(restorePrevious = false)
        mOnArchiveModeStateChangeListener = null
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage) {
        mArchiveCoordinator.release()
        closeArchiveNavigation(restorePrevious = false)
        if (mExplorer != null) {
            mExplorer!!.unregisterChangeListener(this)
        }
        mExplorer = explorer
        mExplorerRootPath = rootPage.path
        setRootPage(rootPage)
        mExplorer!!.registerChangeListener(this)
    }

    private fun extractSelectedArchiveEntries() {
        val selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem }
        if (selectedItems.size == mSelection.size && selectedItems.isNotEmpty()) {
            startArchiveExtraction(selectedItems)
        }
    }

    private fun launchPluginActionOnSelection(action: ResolvedExplorerAction) {
        val selectedItems = mSelection.items
        if (selectedItems.isEmpty()) return
        if (mPluginActions.provider == null) return
        dispatchPluginAction(
            context,
            mPluginActions.surface,
            ExplorerActionEntryPoint.SELECTION_TOOLBAR,
            selectedItems,
            action,
        )
            .onSuccess { clearSelection() }
            .onFailure { error ->
                Log.e(LOG_TAG, "Failed to launch Explorer selection plugin action", error)
                ViewUtils.showToast(context, error.message ?: error.toString(), true)
            }
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage, currentPage: ExplorerPage) {
        mArchiveCoordinator.release()
        closeArchiveNavigation(restorePrevious = false)
        mExplorer?.unregisterChangeListener(this)
        mExplorer = explorer
        mExplorerRootPath = rootPage.path
        setCurrentPageState(ExplorerPageState(rootPage))
        mExplorer?.registerChangeListener(this)
        enterChildPage(currentPage)
    }

    private fun dispatchPluginAction(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit> = mPluginActions.dispatch(context, surface, entryPoint, items, action)

    /**
     * Options of the "More" menu in the rename dialog of [item]: the plugin actions placed in that dialog, or the
     * Batch Rename guidance when no installed plugin offers one (Batch Rename plugin roadmap D10c / D29). Null when this
     * Explorer has no plugin entry points or the item lives in an archive.
     * zh-CN: [item] 的重命名对话框 "更多" 菜单选项: 放置在该对话框中的插件动作, 没有已安装插件提供时为 Batch Rename 引导
     * (Batch Rename 插件路线图 D10c / D29). 本文件管理器没有插件入口或该项位于压缩包内时返回 null.
     */
    internal fun renameDialogOptions(item: ExplorerItem?): List<MaterialDialog.OptionMenuItemSpec>? {
        if (item == null || item is ArchiveExplorerItem) return null
        val surface = mPluginActions.surface
        val actions = ExplorerRenameDialogMenu.resolveActions(mPluginActions.provider, surface, item)
        val entries = ExplorerRenameDialogMenu.entries(surface, actions)
        if (entries.isEmpty()) return null
        return ExplorerRenameDialogMenu.options(context, entries) { action ->
            dispatchPluginAction(context, surface, ExplorerActionEntryPoint.RENAME_DIALOG, listOf(item), action)
                .onFailure { error ->
                    Log.e(LOG_TAG, "Failed to launch Explorer rename dialog plugin action", error)
                    ViewUtils.showToast(context, error.message ?: error.toString(), true)
                }
        }
    }

    private fun applyArchiveReindexResult(result: ExplorerArchiveReindexResult) {
        val restoredPage = result.restoredPages.last()
        mPageStateHistory.clear()
        result.restoredPages.dropLast(1).forEach { ancestor ->
            mPageStateHistory.push(ExplorerPageState(ancestor))
        }
        mSelection.replace(result.restoredSelection)
        setCurrentPageState(
            ExplorerPageState(restoredPage).also { state ->
                if (result.exactPageRestored) {
                    state.scrollY = result.previousScrollY
                    state.dirsCollapsed = result.previousDirsCollapsed
                    state.filesCollapsed = result.previousFilesCollapsed
                }
            },
        )
        updateSelectionToolbar()
        refreshCurrentPage()
    }

    private fun openMaterializedArchiveEntryResult(
        file: File,
        displayName: String,
        action: ExplorerPrimaryAction?,
    ) {
        when (action) {
            null -> {
                val entry = ExplorerFileItem(file, null)
                if (entry.isExecutable) {
                    Scripts.runInteractive(context, ScriptFile(file.path))
                } else {
                    entry.view(context)
                }
            }
            ExplorerPrimaryAction.DOCUMENT_PREVIEWER -> {
                launchDocumentPreviewerOrShowGuidance(
                    ExplorerActionEntryPoint.PRIMARY_BUTTON,
                    ExplorerFileItem(file, null),
                )
            }
            ExplorerPrimaryAction.IMAGE_VIEW,
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
            -> ExplorerFileItem(file, null).view(context)
            ExplorerPrimaryAction.MEDIA_PLAY -> {
                val mediaItem = ExplorerFileItem(file, null)
                if (
                    !launchOfficialMediaOrShowGuidance(
                        ExplorerActionEntryPoint.PRIMARY_BUTTON,
                        mediaItem,
                    )
                ) {
                    mediaItem.play(context)
                }
            }
        }
    }

    private fun updateArchiveWorkflowState() {
        updateArchiveExtractionAction()
        if (mSelection.isActive) updateSelectionToolbar()
    }


    private fun enterArchiveNavigation(connection: ExplorerArchiveSessionConnection) {
        closeArchiveNavigation(restorePrevious = true)
        saveCurrentScrollPosition()
        val previousExplorer = requireNotNull(mExplorer) { "Explorer is unavailable" }
        val sourceParentPath = File(connection.sourcePath).parentFile?.path
            ?: ExplorerPathSegmentBuilder.filesystemRoot(connection.sourcePath)
        val sourceParentSegments = createPathSegments(sourceParentPath)
        val provider = ArchiveExplorerProvider(connection, context.cacheDir)
        val state = ArchiveNavigationState(
            provider = provider,
            previousExplorer = previousExplorer,
            previousRootPath = mExplorerRootPath,
            previousPageState = currentPageState,
            previousHistory = mPageStateHistory.toList(),
            sourceParentSegments = sourceParentSegments,
        )

        previousExplorer.unregisterChangeListener(this)
        mArchiveNavigationState = state
        mExplorer = Explorer(provider, 0)
        mExplorerRootPath = provider.rootPage.path
        mPageStateHistory.clear()
        mExplorer?.registerChangeListener(this)
        setRootPage(provider.rootPage)
        updateProjectToolbarVisibility()
        mOnArchiveModeStateChangeListener?.invoke(true)
    }

    private fun closeArchiveNavigation(restorePrevious: Boolean) {
        val state = mArchiveNavigationState ?: return
        mArchiveCoordinator.closeArchiveContent()
        isRefreshing = false
        mArchiveNavigationState = null
        updateArchiveExtractionAction()
        mExplorer?.unregisterChangeListener(this)
        state.provider.close()
        mOnArchiveModeStateChangeListener?.invoke(false)
        if (!restorePrevious) return

        mExplorer = state.previousExplorer
        mExplorerRootPath = state.previousRootPath
        mPageStateHistory.clear()
        mPageStateHistory.addAll(state.previousHistory)
        setCurrentPageState(state.previousPageState)
        mExplorer?.registerChangeListener(this)
        refreshCurrentPage()
        updateProjectToolbarVisibility()
    }

    private fun openArchiveEntry(
        item: ArchiveExplorerFileItem,
        action: ExplorerPrimaryAction?,
    ) {
        val provider = mArchiveNavigationState?.provider
        if (provider == null) {
            ViewUtils.showToast(context, R.string.text_unavailable)
            return
        }
        mArchiveCoordinator.openEntry(provider, item, action)
    }


    private fun showArchiveFilenameEncodingDialog() {
        val provider = mArchiveNavigationState?.provider ?: return
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        val selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem }
        mArchiveCoordinator.showFilenameEncodingDialog(
            provider = provider,
            page = page,
            selectedItems = selectedItems,
            previousScrollY = currentPageState.scrollY,
            previousDirsCollapsed = currentPageState.dirsCollapsed,
            previousFilesCollapsed = currentPageState.filesCollapsed,
        )
    }

    private fun extractCurrentArchiveFolder() {
        val page = currentPage as? ArchiveExplorerPage ?: return
        startArchiveExtraction(listOf(page))
    }

    private fun startArchiveExtraction(items: List<ArchiveExplorerItem>) {
        val provider = mArchiveNavigationState?.provider ?: return
        mArchiveCoordinator.startExtraction(provider, items) { clearSelection() }
    }

    private fun confirmArchiveDeletion(items: List<ArchiveExplorerItem>) {
        val provider = mArchiveNavigationState?.provider ?: return
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        mArchiveCoordinator.confirmDeletion(
            provider = provider,
            items = items,
            page = page,
            selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem },
            previousScrollY = currentPageState.scrollY,
            previousDirsCollapsed = currentPageState.dirsCollapsed,
            previousFilesCollapsed = currentPageState.filesCollapsed,
        )
    }

    private fun showArchiveRenameDialog(item: ArchiveExplorerItem) {
        val provider = mArchiveNavigationState?.provider ?: return
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        mArchiveCoordinator.showRenameDialog(
            provider = provider,
            item = item,
            page = page,
            selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem },
            previousScrollY = currentPageState.scrollY,
            previousDirsCollapsed = currentPageState.dirsCollapsed,
            previousFilesCollapsed = currentPageState.filesCollapsed,
        )
    }

    private fun showArchiveCreateDirectoryDialog() {
        val provider = mArchiveNavigationState?.provider ?: return
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        mArchiveCoordinator.showCreateDirectoryDialog(
            provider = provider,
            page = page,
            selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem },
            previousScrollY = currentPageState.scrollY,
            previousDirsCollapsed = currentPageState.dirsCollapsed,
            previousFilesCollapsed = currentPageState.filesCollapsed,
        )
    }

    private fun showArchiveAddEntriesDialog() {
        val provider = mArchiveNavigationState?.provider ?: return
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        mArchiveCoordinator.showAddEntriesDialog(
            provider = provider,
            page = page,
            selectedItems = mSelection.items.mapNotNull { it as? ArchiveExplorerItem },
            previousScrollY = currentPageState.scrollY,
            previousDirsCollapsed = currentPageState.dirsCollapsed,
            previousFilesCollapsed = currentPageState.filesCollapsed,
        )
    }


    private fun updateArchiveExtractionAction() {
        val page = currentPageState.page as? ArchiveExplorerPage
        val supported = page != null &&
            page.canExtractEntry &&
            mArchiveNavigationState?.provider?.canExtractEntries == true
        binding?.archiveExtractCurrent?.apply {
            isVisible = false
            isEnabled = supported &&
                !isInSelectionMode &&
                !mArchiveCoordinator.isExtracting &&
                !mArchiveCoordinator.isFilenameReindexing &&
                !mArchiveCoordinator.isMutating
            alpha = if (isEnabled) 1.0f else 0.38f
        }
        updateArchiveCreateDirectoryAction()
        updateArchiveAddEntriesAction()
        updateArchiveFilenameEncodingAction()
    }

    private fun updateArchiveCreateDirectoryAction() {
        val provider = mArchiveNavigationState?.provider
        val page = currentPageState.page as? ArchiveExplorerPage
        val supported = page != null &&
            page.canCreateChildren &&
            provider?.canCreateDirectory == true
        binding?.archiveCreateDirectory?.apply {
            isVisible = false
            isEnabled = supported &&
                !isInSelectionMode &&
                !mArchiveCoordinator.isExtracting &&
                !mArchiveCoordinator.isFilenameReindexing &&
                !mArchiveCoordinator.isMutating
            alpha = if (isEnabled) 1.0f else 0.38f
        }
    }

    private fun updateArchiveAddEntriesAction() {
        val provider = mArchiveNavigationState?.provider
        val page = currentPageState.page as? ArchiveExplorerPage
        val supported = page != null &&
            page.canAddChildren &&
            provider?.canAddEntries == true
        binding?.archiveAddEntries?.apply {
            isVisible = false
            isEnabled = supported &&
                !isInSelectionMode &&
                !mArchiveCoordinator.isExtracting &&
                !mArchiveCoordinator.isFilenameReindexing &&
                !mArchiveCoordinator.isMutating
            alpha = if (isEnabled) 1.0f else 0.38f
        }
    }

    private fun updateArchiveFilenameEncodingAction() {
        val provider = mArchiveNavigationState?.provider
        val supported = currentPageState.page is ArchiveExplorerPage &&
            provider?.filenameCharsetNames?.isNotEmpty() == true
        binding?.archiveFilenameEncoding?.apply {
            isVisible = false
            isEnabled = supported &&
                !isInSelectionMode &&
                !mArchiveCoordinator.isExtracting &&
                !mArchiveCoordinator.isFilenameReindexing &&
                !mArchiveCoordinator.isMutating
            alpha = if (isEnabled) 1.0f else 0.38f
        }
    }

    private fun updateProjectToolbarVisibility() {
        if (mArchiveNavigationState != null) {
            mProjectToolbar.visibility = GONE
        } else {
            mProjectToolbar.updateVisibility()
        }
    }

    fun setExplorer(rootPath: String?, currentPath: String) {
        setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(Objects.requireNonNullElseGet(rootPath, WorkingDirectoryUtils::path)), ExplorerDirPage.createRoot(currentPath))
    }

    private fun setRootPage(page: ExplorerPage) {
        clearSelection()
        setCurrentPageState(ExplorerPageState(page))
        refreshCurrentPage()
    }

    private fun setDefaultExplorer() {
        setExplorer(externalStoragePath, path)
    }

    private fun setCurrentPageState(pageState: ExplorerPageState) {
        currentPageState = pageState
        mApplyPageStateScrollOnRefresh = true
        pageState.page?.let(::updatePathBar)
        updateArchiveExtractionAction()
    }

    private fun updatePathBar(page: ExplorerPage) {
        binding?.explorerPathBar?.setSegments(createPathSegments(page))
    }

    private fun createPathSegments(page: ExplorerPage): List<ExplorerPathSegment> {
        val archiveState = mArchiveNavigationState
        if (archiveState != null && page is ArchiveExplorerPage) {
            return archiveState.sourceParentSegments + archiveState.provider.ancestors(page).map { archivePage ->
                val label = if (
                    archivePage.parent == null && archiveState.provider.shouldAnnotateFormat
                ) {
                    context.getString(
                        R.string.text_archive_format_path_segment,
                        archivePage.name,
                        archiveState.provider.formatDisplayName,
                    )
                } else {
                    archivePage.name
                }
                ExplorerPathSegment(label, archivePage.path)
            }
        }
        return createPathSegments(page.path)
    }

    private fun createPathSegments(currentPath: String): List<ExplorerPathSegment> {
        findSampleRootPath(currentPath)?.let { sampleRootPath ->
            val workspaceSegments = ExplorerPathSegmentBuilder.build(
                currentPath = path,
                rootPath = externalStoragePath,
                rootLabel = context.getString(R.string.term_internal_strorage),
            )
            val sampleSegments = ExplorerPathSegmentBuilder.build(
                currentPath = currentPath,
                rootPath = sampleRootPath,
                rootLabel = context.getString(R.string.text_sample),
            )
            return workspaceSegments + sampleSegments
        }

        if (ExplorerPathSegmentBuilder.isSameOrDescendant(currentPath, externalStoragePath)) {
            return ExplorerPathSegmentBuilder.build(
                currentPath = currentPath,
                rootPath = externalStoragePath,
                rootLabel = context.getString(R.string.term_internal_strorage),
            )
        }

        val configuredRoot = mExplorerRootPath
            ?.takeIf { ExplorerPathSegmentBuilder.isSameOrDescendant(currentPath, it) }
        val displayRoot = configuredRoot ?: ExplorerPathSegmentBuilder.filesystemRoot(currentPath)
        return ExplorerPathSegmentBuilder.build(
            currentPath = currentPath,
            rootPath = displayRoot,
            rootLabel = ExplorerPathSegmentBuilder.labelForPath(displayRoot),
        )
    }

    private fun findSampleRootPath(currentPath: String): String? {
        val workspaceProvider = Explorers.Providers.workspace()
        if (mExplorer?.provider !== workspaceProvider) return null

        var directory: File? = File(currentPath)
        while (directory != null && workspaceProvider.isInSampleDir(directory)) {
            if (workspaceProvider.isCurrentSampleDir(directory)) return directory.path
            directory = directory.parentFile
        }
        return null
    }

    private fun navigateToPath(targetPath: String) {
        val archiveState = mArchiveNavigationState
        if (archiveState != null) {
            val archivePage = archiveState.provider.pageForPath(targetPath)
            if (archivePage != null) {
                if (archivePage.path == currentPage.path) return
                clearSelection()
                saveCurrentScrollPosition()
                saveCurrentPageIntoHistory()
                setCurrentPageState(ExplorerPageState(archivePage))
                refreshCurrentPage()
                return
            }
            closeArchiveNavigation(restorePrevious = true)
            if (targetPath == currentPage.path) return
        }
        if (targetPath == currentPage.path) return
        clearSelection()
        saveCurrentScrollPosition()
        saveCurrentPageIntoHistory()
        setCurrentPageState(ExplorerPageState(ExplorerDirPage.createRoot(targetPath)))
        refreshCurrentPage()
    }

    private fun saveCurrentScrollPosition() {
        val layoutManager = explorerItemListView?.layoutManager as? LinearLayoutManager ?: return
        // @Overwrite by SuperMonster003 on Apr 3, 2023.
        //  ! Should be "first" instead of "last".
        //  ! zh-CN: 应该是 "first" 而非 "last".
        //  # mCurrentPageState.scrollY = layoutManager.findLastCompletelyVisibleItemPosition();
        currentPageState.scrollY = layoutManager.findFirstCompletelyVisibleItemPosition()
    }

    private fun enterChildPage(childPage: ExplorerPage) {
        clearSelection()
        setCurrentPageState(ExplorerPageState(childPage))
        refreshCurrentPage()
    }

    fun setOnItemOperateListener(onItemOperateListener: OnItemOperateListener?) {
        mOnItemOperateListener = onItemOperateListener
    }

    fun setOnProjectToolbarOperateListener(onOperateListener: OnOperateListener?) {
        mProjectToolbar.setOnOperateListener(onOperateListener)
    }

    fun setOnProjectToolbarClickListener(onClickListener: OnClickListener?) {
        mProjectToolbar.setOnClickListener(onClickListener)
    }

    fun canGoBack(): Boolean {
        return isInSelectionMode || !mPageStateHistory.empty() || mArchiveNavigationState != null
    }

    fun goBack() {
        if (isInSelectionMode) {
            clearSelection()
            return
        }
        if (mPageStateHistory.isNotEmpty()) {
            setCurrentPageState(mPageStateHistory.pop())
            refreshCurrentPage()
        } else {
            closeArchiveNavigation(restorePrevious = true)
        }
    }

    fun saveViewStates() {
        saveExplorerState()
        savePageState()
    }

    fun restoreViewStates() {
        restoreExplorerState()
        restorePageState()
    }

    private fun saveSortConfig() {
        explorerItemManager.saveSortConfig()
    }

    private fun restoreSortConfig() {
        explorerItemManager.restoreSortConfig()
    }

    private fun savePageState() {
        val layoutManager = explorerItemListView?.layoutManager as? LinearLayoutManager ?: return
        val currentScrollPosition = mArchiveNavigationState?.previousPageState?.scrollY
            ?: layoutManager.findFirstCompletelyVisibleItemPosition()
        ExplorerViewStateStore.savePageScroll(currentScrollPosition)
    }

    private fun restorePageState() {
        ExplorerViewStateStore.restorePageScroll()?.let { scrollPosition ->
            currentPageState.scrollY = scrollPosition
        }
    }

    private fun saveExplorerState() {
        val archiveState = mArchiveNavigationState
        val persistentPageState = archiveState?.previousPageState ?: currentPageState
        val persistentHistory = archiveState?.previousHistory ?: mPageStateHistory.toList()
        val currentPath = persistentPageState.page?.path ?: return
        val rootPath = archiveState?.previousRootPath
            ?: if (persistentHistory.isEmpty()) path else persistentHistory.first().page!!.path
        ExplorerViewStateStore.saveExplorer(
            currentPath,
            rootPath,
            persistentHistory.mapNotNull { it.toStoredState() },
        )
    }

    private fun restoreExplorerState() {
        val stored = ExplorerViewStateStore.restoreExplorer()
        if (stored.currentPath != null) {
            setExplorer(stored.rootPath, stored.currentPath)
        } else {
            setDefaultExplorer()
        }
        stored.history.mapTo(mPageStateHistory) { it.toPageState() }
    }
    fun canGoUp(): Boolean {
        val archivePage = currentPage as? ArchiveExplorerPage
        if (archivePage != null) return archivePage.parentId != null
        return !externalStoragePath.startsWith(currentPage.path)
    }

    fun goUp() {
        clearSelection()
        saveCurrentPageIntoHistory()
        val archiveState = mArchiveNavigationState
        val archivePage = currentPage as? ArchiveExplorerPage
        if (archiveState != null && archivePage != null) {
            val parent = archivePage.parentId?.let { parentId ->
                archiveState.provider.ancestors(archivePage)
                    .firstOrNull { it.entryId == parentId }
            } ?: return
            setCurrentPageState(ExplorerPageState(parent))
            refreshCurrentPage()
            return
        }
        val currentPagePath = currentPage.path
        val nextPagePath = if (Explorers.Providers.workspace().isCurrentSampleDir(currentDirectory)) {
            path
        } else {
            File(currentPagePath).parent
        }
        setCurrentPageState(ExplorerPageState(ExplorerDirPage.createRoot(nextPagePath)))
        refreshCurrentPage()
    }

    fun saveCurrentPageIntoHistory() {
        mPageStateHistory.push(currentPageState)
    }

    fun setDirectorySpanSize(directorySpanSize: Int) {
        mDirectorySpanSize = directorySpanSize
    }

    fun setFilter(filter: ((ExplorerItem) -> Boolean)?) {
        clearSelection()
        mFilter = filter
        reload()
    }

    fun reload() {
        clearSelection()
        refreshCurrentPage()
    }

    fun sort(sortType: Int, isDir: Boolean, isFileSortedAscending: Boolean) {
        isRefreshing = true

        val explorerItemListCallable = Callable {
            if (isDir) {
                explorerItemManager.sortDirs(sortType, isFileSortedAscending)
            } else {
                explorerItemManager.sortFiles(sortType, isFileSortedAscending)
            }
            explorerItemManager
        }
        Observable.fromCallable(explorerItemListCallable)
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                notifyDataSetChanged()
                isRefreshing = false
                saveSortConfig()
            }
    }

    private fun positionOfCategoryFile() = mExplorerAdapter.positionOfFileCategory

    private fun refreshCurrentPage() {
        val explorer = mExplorer ?: return
        isRefreshing = true

        // Cancel the previous refresh to prevent multiple concurrent iterators on mutable data.
        // zh-CN: 取消上一次刷新, 防止对可变数据产生多个并发迭代器.
        mRefreshDisposable?.dispose()

        mRefreshDisposable = explorer.fetchChildren(currentPage)
            .subscribeOn(Schedulers.io())
            .flatMapObservable { page: ExplorerPage? ->
                currentPageState.page = page

                // Snapshot the iterable to avoid ConcurrentModificationException during iteration.
                // zh-CN: 对可迭代对象做快照, 避免迭代期间被修改触发 ConcurrentModificationException.
                val snapshot: List<ExplorerItem> = page?.toList() ?: emptyList()

                Observable.fromIterable(snapshot)
            }
            .filter { f: ExplorerItem -> mFilter == null || mFilter!!.invoke(f) }
            .collectInto(explorerItemManager.cloneConfig(), ExplorerItemManager::add)
            .observeOn(Schedulers.computation())
            .doOnSuccess(ExplorerItemManager::sort)
            .map { newManager -> newManager to ExplorerRefreshDiffer.buildMetadata(newManager) }
            .observeOn(AndroidSchedulers.mainThread())
            .doFinally {
                // Always end the refreshing state even when errors happen.
                // zh-CN: 无论成功或失败都要结束刷新状态.
                isRefreshing = false
            }
            .subscribe({ (newManager, newMeta) ->
                updateProjectToolbarVisibility()
                val oldManager = explorerItemManager
                val oldMeta = mRefreshMetaByPath
                val applyPageStateScroll = mApplyPageStateScrollOnRefresh
                mApplyPageStateScrollOnRefresh = false
                explorerItemManager = newManager
                mRefreshMetaByPath = newMeta
                if (applyPageStateScroll) {
                    // Page navigation or state restore: repopulate fully and anchor the layout
                    // at the recorded position within the same pass (no post-layout jump).
                    // zh-CN: 页面导航或状态恢复: 全量填充并在同一次布局中锚定记录的滚动位置,
                    // 避免先按顶部布局再跳转造成的可见闪动.
                    notifyDataSetChanged()
                    scrollToPositionImmediately()
                } else {
                    notifyRefreshedInPlace(oldManager, oldMeta, newManager, newMeta)
                }
            }, { e ->
                Log.e(LOG_TAG, "refreshCurrentPage() failed", e)
                showSnack(this, R.string.error_refresh_failed, true)
            })
    }

    private fun notifyRefreshedInPlace(
        oldManager: ExplorerItemManager,
        oldMeta: Map<String, Long>,
        newManager: ExplorerItemManager,
        newMeta: Map<String, Long>,
    ) {
        val dispatched = ExplorerRefreshDiffer.dispatch(
            oldManager = oldManager,
            oldMetadata = oldMeta,
            newManager = newManager,
            newMetadata = newMeta,
            directoriesCollapsed = currentPageState.dirsCollapsed,
            filesCollapsed = currentPageState.filesCollapsed,
            adapter = mExplorerAdapter,
        )
        if (!dispatched) notifyDataSetChanged()
    }
    private fun scrollToPositionSmoothly() {
        val recyclerView = explorerItemListView ?: return
        val layoutManager = recyclerView.layoutManager
        val position = currentPageState.scrollY

        val smoothScroller: RecyclerView.SmoothScroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }.apply { targetPosition = position }

        when (layoutManager) {
            null -> recyclerView.smoothScrollToPosition(position)
            is GridLayoutManager -> layoutManager.startSmoothScroll(smoothScroller)
            is LinearLayoutManager -> layoutManager.startSmoothScroll(smoothScroller)
            else -> layoutManager.startSmoothScroll(smoothScroller)
        }
    }

    private fun scrollToPositionImmediately() {
        val recyclerView = explorerItemListView ?: return
        val layoutManager = recyclerView.layoutManager
        val position = currentPageState.scrollY

        when (layoutManager) {
            null -> recyclerView.scrollToPosition(position)
            is GridLayoutManager -> layoutManager.scrollToPositionWithOffset(position, 0)
            is LinearLayoutManager -> layoutManager.scrollToPositionWithOffset(position, 0)
            else -> layoutManager.scrollToPosition(position)
        }
    }


    class ExplorerPageState {

        var page: ExplorerPage? = null

        @JvmField
        var dirsCollapsed: Boolean = false

        @JvmField
        var filesCollapsed: Boolean = false

        var scrollY: Int = 0

        constructor()

        constructor(page: ExplorerPage) {
            this.page = page
        }

    }

    private fun ExplorerPageState.toStoredState(): ExplorerStoredPageState? {
        val pagePath = page?.path ?: return null
        return ExplorerStoredPageState(pagePath, scrollY, dirsCollapsed, filesCollapsed)
    }

    private fun ExplorerStoredPageState.toPageState(): ExplorerPageState {
        return ExplorerPageState(ExplorerDirPage.createRoot(path)).also { state ->
            state.scrollY = scrollPosition
            state.dirsCollapsed = directoriesCollapsed
            state.filesCollapsed = filesCollapsed
        }
    }

    interface OnItemClickListener {
        fun onItemClick(view: View?, item: ExplorerItem)
    }

    interface OnItemOperateListener {
        fun onItemOperated(item: ExplorerItem)
    }

    interface OnSelectionToolbarStateChangeListener {
        fun onSelectionToolbarStateChanged(shown: Boolean)
    }

    companion object {
        private const val LOG_TAG = "ExplorerView"
        private const val PLUGIN_ACTION_MENU_ORDER_START = 1_000

        private const val SELECTION_ACTION_COPY = "copy"
        private const val SELECTION_ACTION_MOVE = "move"
        private const val SELECTION_ACTION_DELETE = "delete"
        private const val SELECTION_ACTION_EXTRACT = "extract"
        private const val SELECTION_ACTION_COPY_PATH = "copy-path"
        private const val SELECTION_ACTION_PLUGIN_PREFIX = "plugin:"

        // Upper bound for diff-based refresh dispatching; larger snapshots fall back to
        // a full rebind to keep the main-thread diff cost negligible.
        // zh-CN: 差量刷新的行数上限, 超出时回退全量重绑定, 确保主线程差量计算开销可忽略.

        protected const val VIEW_TYPE_ITEM: Int = ExplorerRowViewType.ITEM
        protected const val VIEW_TYPE_PAGE: Int = ExplorerRowViewType.PAGE

        // @Hint by Stardust (https://github.com/hyb1996) on Aug 20, 2017.
        //  ! Category 是类别, 例如 "文件", "文件夹" 这样的文本.
        protected const val VIEW_TYPE_CATEGORY: Int = ExplorerRowViewType.CATEGORY

        fun clearViewStates() {
            ExplorerViewStateStore.clear()
        }

        fun getPrefKey(key: String): String = ExplorerViewStateStore.prefKey(key)

    }

    private fun ExplorerProjectToolbar.updateVisibility() {
        if (!isProjectRecognitionEnabled || !ProjectConfig.isProject(currentPage)) {
            clearProject()
            return
        }
        if (!trySetProject(currentPage.toScriptFile())) return
        visibility = if (isInSelectionMode) {
            INVISIBLE
        } else {
            VISIBLE
        }
    }

}
