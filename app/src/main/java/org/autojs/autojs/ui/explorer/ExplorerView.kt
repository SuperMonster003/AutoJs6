package org.autojs.autojs.ui.explorer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerActionExecution
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerPage
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerProvider
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveEntryTooLargeException
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionFailure
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionListener
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionProgress
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveExtractionResult
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenInteractionException
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveSessionConnection
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.core.pref.Pref.getInt
import org.autojs.autojs.core.pref.Pref.getLinkedList
import org.autojs.autojs.core.pref.Pref.getStringOrNull
import org.autojs.autojs.core.pref.Pref.putInt
import org.autojs.autojs.core.pref.Pref.putLinkedList
import org.autojs.autojs.core.pref.Pref.putString
import org.autojs.autojs.core.pref.Pref.remove
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerChangeEvent
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerSamplePage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.WorkspaceFileProvider
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.project.ProjectConfig
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorManagerCompat
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout
import org.autojs.autojs.ui.common.ScriptLoopDialog
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerProjectToolbar.OnOperateListener
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager
import org.autojs.autojs.ui.main.scripts.ImageInfoDialogManager
import org.autojs.autojs.ui.main.scripts.MediaInfoDialogManager
import org.autojs.autojs.ui.preview.DocumentPreviewActivity
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_DATE
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_NAME
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_SIZE
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager.Companion.SORT_TYPE_TYPE
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.FirstCharView
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setForceShowIconCompat
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs.util.WorkingDirectoryUtils.path
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerCategoryBinding
import org.autojs.autojs6.databinding.ExplorerDirectoryBinding
import org.autojs.autojs6.databinding.ExplorerFileBinding
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding
import org.autojs.autojs6.databinding.ExplorerViewBinding
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues
import org.autojs.plugin.explorer.api.ExplorerArchiveOpenValues
import org.autojs.plugin.explorer.api.ExplorerArchiveSessionValues
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.util.LinkedList
import java.util.Objects
import java.util.Stack
import java.util.concurrent.Callable
import kotlin.math.roundToInt

private data class ExplorerItemActionState(
    val primaryAction: ExplorerPrimaryAction?,
    val pluginPrimaryAction: ResolvedExplorerAction?,
    val editVisible: Boolean,
    val runVisible: Boolean,
    val runAsMedia: Boolean,
    val installVisible: Boolean,
    val infoVisible: Boolean,
)

// One background-computed payload per row bind keeps the main thread down to a single
// callback per row instead of four, avoiding relayout bursts while scrolling.
// zh-CN: 每次行绑定只在后台计算一份数据载荷, 主线程回调从四次减少到一次, 避免滑动时的布局风暴.
private data class ExplorerItemMainContent(
    val icon: FileUtils.TYPE.Icon,
    val executable: Boolean,
    val name: CharSequence,
    val date: CharSequence,
    val size: CharSequence,
)

private data class ArchiveNavigationState(
    val provider: ArchiveExplorerProvider,
    val previousExplorer: Explorer,
    val previousRootPath: String?,
    val previousPageState: ExplorerView.ExplorerPageState,
    val previousHistory: List<ExplorerView.ExplorerPageState>,
    val sourceParentSegments: List<ExplorerPathSegment>,
)

private data class ArchiveFilenameReindexResult(
    val restoredPages: List<ArchiveExplorerPage>,
    val restoredSelection: List<ArchiveExplorerItem>,
)

private class ArchiveOpenUiState(
    val surface: ExplorerSurface,
    val entryPoint: ExplorerActionEntryPoint,
    val item: ExplorerItem,
    val action: ResolvedExplorerAction,
) : AutoCloseable {
    var interactionDialog: MaterialDialog? = null
    private var passwordValue: CharArray? = null

    @Synchronized
    fun replacePassword(password: CharArray?) {
        passwordValue?.fill('\u0000')
        passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()
    }

    @Synchronized
    fun requestOptions(): ExplorerArchiveOpenRequestOptions {
        val password = passwordValue?.clone()
        return try {
            ExplorerArchiveOpenRequestOptions(password)
        } finally {
            password?.fill('\u0000')
        }
    }

    @Synchronized
    override fun close() {
        passwordValue?.fill('\u0000')
        passwordValue = null
    }
}

private class ArchiveExtractionAttempt(
    val controller: DialogUtils.OperationController,
    val progressSession: DialogUtils.ProgressDialogSession,
) {
    @Volatile
    var operationId: String? = null
}

private class ArchiveExtractionUiState(
    val items: List<ArchiveExplorerItem>,
) : AutoCloseable {
    var attempt: ArchiveExtractionAttempt? = null
    var interactionDialog: MaterialDialog? = null
    var skipUnsafePaths: Boolean = false
    var allowResourceBudgetOverride: Boolean = false
    private var passwordValue: CharArray? = null

    @Synchronized
    fun replacePassword(password: CharArray?) {
        passwordValue?.fill('\u0000')
        passwordValue = password?.takeIf(CharArray::isNotEmpty)?.clone()
    }

    @Synchronized
    fun requestOptions(): ExplorerArchiveExtractionRequestOptions {
        val password = passwordValue?.clone()
        return try {
            ExplorerArchiveExtractionRequestOptions(
                password = password,
                skipUnsafePaths = skipUnsafePaths,
                allowResourceBudgetOverride = allowResourceBudgetOverride,
            )
        } finally {
            password?.fill('\u0000')
        }
    }

    @Synchronized
    override fun close() {
        passwordValue?.fill('\u0000')
        passwordValue = null
    }
}

/**
 * Created by Stardust on Aug 21, 2017.
 * Transformed by SuperMonster003 on Nov 23, 2024.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 3, 2026.
 * Modified by SuperMonster003 as of Feb 3, 2026.
 */
@SuppressLint("CheckResult", "NonConstantResourceId", "NotifyDataSetChanged")
open class ExplorerView : ThemeColorSwipeRefreshLayout, SwipeRefreshLayout.OnRefreshListener, PopupMenu.OnMenuItemClickListener {

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
        get() = mSelectedItems.isNotEmpty()

    var explorerItemListView: RecyclerView? = null
        private set

    private lateinit var mProjectToolbar: ExplorerProjectToolbar

    private var mExplorer: Explorer? = null
    private var mExplorerRootPath: String? = null

    private val mExplorerAdapter = ExplorerAdapter()
    private var mFilter: ((ExplorerItem) -> Boolean)? = null
    private val mSelectedItems = LinkedHashMap<String, ExplorerItem>()
    private var mSelectionToolbarShown = false
    private var mSelectionNavigationBarApplied = false
    private var mBlockListScrollUntilTouchRelease = false
    private var mNavigationBarInsetBottom = 0
    private var mExtraListPaddingBottom = 0
    private var mListClipToPadding = false

    private var mOnItemOperateListener: OnItemOperateListener? = null
    private var mOnSelectionToolbarStateChangeListener: OnSelectionToolbarStateChangeListener? = null
    private var mPluginActionProvider: ExplorerPluginActionProvider? = null
    private var mPluginActionSurface = ExplorerSurface.UNSPECIFIED
    private var mSelectionPluginAction: ResolvedExplorerAction? = null
    private var mSelectionArchiveExtraction = false
    private var mArchiveNavigationState: ArchiveNavigationState? = null
    private var mArchiveOpenJob: Job? = null
    private var mArchiveOpenUiState: ArchiveOpenUiState? = null
    private var mArchiveEntryOpenJob: Job? = null
    private var mArchiveFilenameReindexJob: Job? = null
    private var mArchiveExtractionStartJob: Job? = null
    private var mArchiveExtractionUiState: ArchiveExtractionUiState? = null
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
                setupSelectionTouchBlocker(it)
                it.layoutManager = GridLayoutManager(this.context, 2).also { manager ->
                    manager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int) = when {
                            position > positionOfCategoryDir && position < positionOfCategoryFile() -> {
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
            binding.archiveExtractCurrent.setOnClickListener { extractCurrentArchiveFolder() }
            setupSelectionToolbar(binding)
            applySelectionToolbarTheme(ThemeColorManager.currentThemeColor)
        }
        restoreSortConfig()
        setOnRefreshListener(this)
    }

    private fun setupSelectionToolbar(binding: ExplorerViewBinding) {
        listOf(
            binding.selectionClose,
            binding.selectionCopy,
            binding.selectionMove,
            binding.selectionPlugin,
            binding.selectionDelete,
        ).forEach(::applyButtonAccessibilityRole)
        ViewCompat.setAccessibilityHeading(binding.selectionCount, true)
        binding.selectionClose.setOnClickListener { clearSelection() }
        binding.selectionCopy.setOnClickListener { copySelectedItems() }
        binding.selectionMove.setOnClickListener { moveSelectedItems() }
        binding.selectionPlugin.setOnClickListener { launchSelectedPluginAction() }
        binding.selectionDelete.setOnClickListener { deleteSelectedItems() }
    }

    private fun applyButtonAccessibilityRole(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = Button::class.java.name
            }
        })
    }

    fun setOnSelectionToolbarStateChangeListener(listener: OnSelectionToolbarStateChangeListener?) {
        mOnSelectionToolbarStateChangeListener = listener
    }

    fun excludeListPaddingFromBottomNavigationBar(extraPaddingBottomDp: Float = 0F, clipToPadding: Boolean = false) {
        val recyclerView = explorerItemListView ?: return
        mExtraListPaddingBottom = DisplayUtils.dpToPx(extraPaddingBottomDp).roundToInt()
        mListClipToPadding = clipToPadding
        recyclerView.clipToPadding = clipToPadding
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { _, insets ->
            mNavigationBarInsetBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            recyclerView.clipToPadding = mListClipToPadding
            updateSelectionToolbarBottomInset()
            updateExplorerListBottomPadding()
            insets
        }
        ViewCompat.requestApplyInsets(recyclerView)
        recyclerView.post { ViewCompat.requestApplyInsets(recyclerView) }
    }

    private fun setupSelectionTouchBlocker(recyclerView: RecyclerView) {
        recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (!mBlockListScrollUntilTouchRelease) {
                    return false
                }
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    unblockListScrollAfterLongPress()
                }
                return true
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    unblockListScrollAfterLongPress()
                }
            }
        })
    }

    private fun blockListScrollUntilTouchRelease() {
        mBlockListScrollUntilTouchRelease = true
        isEnabled = false
        explorerItemListView?.let {
            it.stopScroll()
            it.parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private fun unblockListScrollAfterLongPress() {
        if (!mBlockListScrollUntilTouchRelease) {
            return
        }
        mBlockListScrollUntilTouchRelease = false
        isEnabled = true
        explorerItemListView?.parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun setThemeColor(themeColor: ThemeColor) {
        super.setThemeColor(themeColor)
        applySelectionToolbarTheme(themeColor)
        if (mSelectionToolbarShown) {
            applySelectionNavigationBarColor(themeColor)
        }
    }

    private fun applySelectionToolbarTheme(themeColor: ThemeColor = ThemeColorManager.currentThemeColor) {
        val backgroundColor = themeColor.colorPrimary
        val foregroundColor = ViewUtils.getDayOrNightColorByLuminance(context, backgroundColor)
        val foregroundTint = ColorStateList.valueOf(foregroundColor)
        binding?.let { binding ->
            binding.selectionToolbar.setBackgroundColor(backgroundColor)
            listOf(
                binding.selectionCount,
                binding.selectionCloseText,
                binding.selectionCopyText,
                binding.selectionMoveText,
                binding.selectionPluginText,
                binding.selectionDeleteText,
            ).forEach { it.setTextColor(foregroundColor) }
            listOf<ImageView>(
                binding.selectionCloseIcon,
                binding.selectionCopyIcon,
                binding.selectionMoveIcon,
                binding.selectionPluginIcon,
                binding.selectionDeleteIcon,
            ).forEach { ImageViewCompat.setImageTintList(it, foregroundTint) }
        }
    }

    private fun setSelectionToolbarShown(shown: Boolean) {
        val toolbar = binding?.selectionToolbar ?: return
        toolbar.animate().cancel()
        if (shown) {
            applySelectionToolbarTheme()
            applySelectionNavigationBarColor()
            if (mSelectionToolbarShown && toolbar.isVisible) {
                toolbar.translationY = 0F
                updateExplorerListBottomPadding()
                notifySelectionToolbarStateChanged(true)
                return
            }
            mSelectionToolbarShown = true
            toolbar.isVisible = true
            toolbar.translationY = selectionToolbarHeight().toFloat()
            notifySelectionToolbarStateChanged(true)
            toolbar.animate()
                .translationY(0F)
                .setDuration(SELECTION_TOOLBAR_SHOW_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { updateExplorerListBottomPadding() }
                .start()
            updateExplorerListBottomPadding()
            toolbar.post {
                updateExplorerListBottomPadding()
                notifySelectionToolbarStateChanged(true)
            }
            return
        }
        if (!mSelectionToolbarShown && !toolbar.isVisible) {
            restoreSelectionNavigationBarColor()
            updateExplorerListBottomPadding()
            return
        }
        mSelectionToolbarShown = false
        toolbar.animate()
            .translationY(selectionToolbarHeight().toFloat())
            .setDuration(SELECTION_TOOLBAR_HIDE_DURATION)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!isInSelectionMode) {
                    toolbar.isVisible = false
                    toolbar.translationY = 0F
                    restoreSelectionNavigationBarColor()
                    updateExplorerListBottomPadding()
                    notifySelectionToolbarStateChanged(false)
                }
            }
            .start()
    }

    private fun updateSelectionToolbarBottomInset() {
        binding?.selectionToolbar?.updatePadding(bottom = mNavigationBarInsetBottom)
        if (mSelectionToolbarShown) {
            notifySelectionToolbarStateChanged(true)
        }
    }

    private fun updateExplorerListBottomPadding() {
        val recyclerView = explorerItemListView ?: return
        val bottomPadding = when {
            isInSelectionMode || mSelectionToolbarShown -> selectionToolbarHeight()
            else -> mNavigationBarInsetBottom
        }
        recyclerView.updatePadding(bottom = mExtraListPaddingBottom + bottomPadding)
    }

    private fun selectionToolbarHeight(): Int {
        val toolbar = binding?.selectionToolbar
        val fallbackHeight = DisplayUtils.dpToPx(SELECTION_TOOLBAR_CONTENT_HEIGHT_DP).roundToInt() + mNavigationBarInsetBottom
        return toolbar?.height?.takeIf { it > 0 } ?: fallbackHeight
    }

    private fun notifySelectionToolbarStateChanged(shown: Boolean) {
        mOnSelectionToolbarStateChangeListener?.onSelectionToolbarStateChanged(shown)
    }

    private fun applySelectionNavigationBarColor(themeColor: ThemeColor = ThemeColorManager.currentThemeColor) {
        val activity = context.findActivity() ?: return
        ViewUtils.setNavigationBarBackgroundColor(activity, themeColor.colorPrimary)
        ViewUtils.setNavigationBarIconLightByColorLuminance(activity, themeColor.colorPrimary)
        mSelectionNavigationBarApplied = true
    }

    private fun restoreSelectionNavigationBarColor() {
        if (!mSelectionNavigationBarApplied) {
            return
        }
        context.findActivity()?.let { activity ->
            ViewUtils.setNavigationBarBackgroundColor(activity, Color.TRANSPARENT)
            ViewUtils.setNavigationBarIconLight(activity, ViewUtils.isNightModeEnabled)
        }
        mSelectionNavigationBarApplied = false
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
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
                    mExplorerAdapter.notifyItemChanged(positionOfCategoryDir)
                    mExplorerAdapter.notifyItemChanged(positionOfCategoryFile())
                }
                ExplorerChangeEvent.REMOVE -> {
                    i = explorerItemManager.remove(item)
                    if (item != null && mSelectedItems.remove(item.path) != null) {
                        updateSelectionToolbar()
                    }
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i)
                        mExplorerAdapter.notifyItemChanged(positionOfCategoryDir)
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
        cancelArchiveOpenUi()
        mArchiveEntryOpenJob?.cancel()
        mArchiveEntryOpenJob = null
        mArchiveFilenameReindexJob?.cancel()
        mArchiveFilenameReindexJob = null
        mArchiveExtractionStartJob?.cancel()
        mArchiveExtractionStartJob = null
        dismissArchiveExtractionUi()
        closeArchiveNavigation(restorePrevious = false)
        mExplorer?.unregisterChangeListener(this)
        mPageStateHistory.clear()
        unblockListScrollAfterLongPress()
        restoreSelectionNavigationBarColor()
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
        when (item.itemId) {
            R.id.action_move_to -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .move(selectedItem!!.toScriptFile())
                    .ignoreElements()
                    .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_move) })
            }
            R.id.action_copy_to -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .copy(selectedItem!!.toScriptFile())
                    .ignoreElements()
                    .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_copy) })
            }
            R.id.action_rename -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .rename(selectedItem as ExplorerFileItem?)
                    .ignoreElements()
                    .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_rename) })
            }
            R.id.action_delete -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .delete(selectedItem!!.toScriptFile())
            }
            R.id.action_version_history -> {
                val selected = selectedItem ?: return false

                // Show version history for this file and restore to disk when chosen.
                // zh-CN: 显示该文件的版本历史, 选择后写回磁盘恢复.
                VersionHistoryController(context).showForFilePath(selected.path, selected.path)

                notifyItemOperated()
                mRequestHostDialogHide?.run()
            }
            R.id.action_run_repeatedly -> {
                ScriptLoopDialog(context, selectedItem!!.toScriptFile())
                    .show()
                notifyItemOperated()
            }
            R.id.action_create_shortcut -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .createShortcut(selectedItem!!.toScriptFile())
                mRequestHostDialogHide?.run()
            }
            R.id.action_edit_by_other_apps -> {
                selectedItem?.edit(this@ExplorerView)
                notifyItemOperated()
                mRequestHostDialogHide?.run()
            }
            R.id.action_open_by_other_apps -> {
                Scripts.openByOtherApps(selectedItem!!.toScriptFile())
                notifyItemOperated()
            }
            R.id.action_send -> {
                Scripts.send(context, selectedItem!!.toScriptFile())
                notifyItemOperated()
                mRequestHostDialogHide?.run()
            }
            R.id.action_timed_task -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .timedTask(selectedItem!!.toScriptFile())
                notifyItemOperated()
                mRequestHostDialogHide?.run()
            }
            R.id.action_build_apk -> {
                BuildActivity.launch(context, selectedItem!!.path)
                notifyItemOperated()
                mRequestHostDialogHide?.run()
            }
            R.id.action_reset -> {
                val o = Explorers.Providers.workspace()
                    .resetSample(selectedItem!!.toScriptFile())
                if (o == null) {
                    resetFailed()
                } else {
                    o.observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ file: ScriptFile ->
                            if (file.exists()) {
                                resetSucceeded()
                            } else {
                                resetFailed()
                            }
                        }, Observers.toastMessage())
                }
            }
            else -> {
                return false
            }
        }
        return true
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
            VIEW_TYPE_ITEM -> ExplorerItemViewHolder(inflater.inflate(R.layout.explorer_file, parent, false))
            VIEW_TYPE_PAGE -> ExplorerPageViewHolder(inflater.inflate(R.layout.explorer_directory, parent, false))
            VIEW_TYPE_CATEGORY -> ExplorerCategoryViewHolder(inflater.inflate(R.layout.explorer_category, parent, false))
            else -> ExplorerItemViewHolder(inflater.inflate(R.layout.explorer_file, parent, false))
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
        if (!isItemSelectable(item)) return false
        mSelectedItems[item.path] = item
        updateSelectionToolbar()
        notifyDataSetChanged()
        return true
    }

    private fun toggleItemSelection(item: ExplorerItem) {
        if (!isItemSelectable(item)) return
        if (mSelectedItems.containsKey(item.path)) {
            mSelectedItems.remove(item.path)
        } else {
            mSelectedItems[item.path] = item
        }
        updateSelectionToolbar()
        notifyDataSetChanged()
    }

    private fun clearSelection(notify: Boolean = true) {
        if (mSelectedItems.isEmpty()) return
        mSelectedItems.clear()
        updateSelectionToolbar()
        if (notify) {
            notifyDataSetChanged()
        }
    }

    private fun isItemSelectable(item: ExplorerItem) = when (item) {
        is ArchiveExplorerItem -> mArchiveNavigationState?.provider?.canExtractEntries == true &&
            item.canExtractEntry
        else -> item.canCopy() || item.canMove() || item.canDelete()
    }

    private fun isItemSelected(item: ExplorerItem) = mSelectedItems.containsKey(item.path)

    private fun selectedScriptFiles() = mSelectedItems.values.map { it.toScriptFile() }

    private fun updateSelectionToolbar() {
        binding?.let { binding ->
            val selectedCount = mSelectedItems.size
            binding.selectionCount.text = context.getString(R.string.text_selected_items, selectedCount)

            val selectedItems = mSelectedItems.values
            val archiveSelection = selectedItems.isNotEmpty() &&
                selectedItems.all { it is ArchiveExplorerItem && it.canExtractEntry } &&
                mArchiveNavigationState?.provider?.canExtractEntries == true
            binding.selectionCopy.isVisible = !archiveSelection
            binding.selectionMove.isVisible = !archiveSelection
            binding.selectionDelete.isVisible = !archiveSelection
            updateSelectionActionState(binding.selectionCopy, !archiveSelection && selectedItems.all { it.canCopy() })
            updateSelectionActionState(binding.selectionMove, !archiveSelection && selectedItems.all { it.canMove() })
            updateSelectionActionState(binding.selectionDelete, !archiveSelection && selectedItems.all { it.canDelete() })
            val pluginAction = if (selectedItems.isEmpty() || archiveSelection) {
                null
            } else {
                runCatching {
                    mPluginActionProvider?.actionsFor(
                        mPluginActionSurface,
                        ExplorerActionEntryPoint.SELECTION_TOOLBAR,
                        selectedItems.toList(),
                    )?.firstOrNull()
                }.onFailure { error ->
                    Log.w(LOG_TAG, "Failed to resolve Explorer selection plugin action", error)
                }.getOrNull()
            }
            mSelectionPluginAction = pluginAction
            mSelectionArchiveExtraction = archiveSelection
            binding.selectionPlugin.isVisible = archiveSelection || pluginAction != null
            if (archiveSelection) {
                val label = context.getString(R.string.text_extract)
                binding.selectionPluginText.text = label
                binding.selectionPlugin.contentDescription = label
                binding.selectionPluginIcon.setImageResource(R.drawable.ic_unarchive_black_48dp)
                updateSelectionActionState(binding.selectionPlugin, mArchiveExtractionUiState == null)
            } else {
                binding.selectionPluginIcon.setImageResource(R.drawable.ic_archive_black_48dp)
                pluginAction?.let { action ->
                    binding.selectionPluginText.text = action.actionLabel
                    binding.selectionPlugin.contentDescription = action.actionLabel
                }
                updateSelectionActionState(binding.selectionPlugin, pluginAction != null)
            }
            setSelectionToolbarShown(selectedCount > 0)
        }
        updateArchiveExtractionAction()
        updateProjectToolbarVisibility()
    }

    private fun updateSelectionActionState(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.38f
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

    private fun deleteSelectedItems() {
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
        mPluginActionSurface = if (provider == null) ExplorerSurface.UNSPECIFIED else surface
        mPluginActionProvider = provider
        if (mSelectedItems.isNotEmpty()) {
            updateSelectionToolbar()
        }
    }

    internal fun setOnArchiveModeStateChangeListener(listener: ((Boolean) -> Unit)?) {
        mOnArchiveModeStateChangeListener = listener
        listener?.invoke(mArchiveNavigationState != null)
    }

    internal fun releasePluginResources() {
        cancelArchiveOpenUi()
        mArchiveEntryOpenJob?.cancel()
        mArchiveEntryOpenJob = null
        mArchiveFilenameReindexJob?.cancel()
        mArchiveFilenameReindexJob = null
        mArchiveExtractionStartJob?.cancel()
        mArchiveExtractionStartJob = null
        dismissArchiveExtractionUi()
        closeArchiveNavigation(restorePrevious = false)
        mOnArchiveModeStateChangeListener = null
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage) {
        cancelArchiveOpenUi()
        closeArchiveNavigation(restorePrevious = false)
        if (mExplorer != null) {
            mExplorer!!.unregisterChangeListener(this)
        }
        mExplorer = explorer
        mExplorerRootPath = rootPage.path
        setRootPage(rootPage)
        mExplorer!!.registerChangeListener(this)
    }

    private fun launchSelectedPluginAction() {
        if (mSelectionArchiveExtraction) {
            val selectedItems = mSelectedItems.values.mapNotNull { it as? ArchiveExplorerItem }
            if (selectedItems.size == mSelectedItems.size && selectedItems.isNotEmpty()) {
                clearSelection()
                startArchiveExtraction(selectedItems)
            }
            return
        }
        val action = mSelectionPluginAction ?: return
        val selectedItems = mSelectedItems.values.toList()
        if (selectedItems.isEmpty()) return
        if (mPluginActionProvider == null) return
        dispatchPluginAction(
            context,
            mPluginActionSurface,
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
        cancelArchiveOpenUi()
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
    ): Result<Unit> {
        val provider = mPluginActionProvider
            ?: return Result.failure(IllegalStateException("Explorer Action provider is unavailable"))
        if (action.descriptor.presentation != ExplorerActionValues.PRESENTATION_HOST_EXPLORER) {
            return provider.launch(context, surface, entryPoint, items, action)
        }
        if (items.size != 1) {
            return Result.failure(IllegalArgumentException("Host archive sessions require one file"))
        }
        if (findViewTreeLifecycleOwner() == null) {
            return Result.failure(IllegalStateException("Explorer lifecycle is unavailable"))
        }
        if (mArchiveOpenUiState != null || mArchiveOpenJob?.isActive == true) {
            return Result.success(Unit)
        }
        val uiState = ArchiveOpenUiState(
            surface = surface,
            entryPoint = entryPoint,
            item = items.single(),
            action = action,
        )
        mArchiveOpenUiState = uiState
        startArchiveOpenAttempt(uiState)
        return Result.success(Unit)
    }

    private fun startArchiveOpenAttempt(uiState: ArchiveOpenUiState) {
        if (mArchiveOpenUiState !== uiState || mArchiveOpenJob?.isActive == true) return
        val provider = mPluginActionProvider ?: run {
            finishArchiveOpenUi(uiState)
            return
        }
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: run {
            finishArchiveOpenUi(uiState)
            return
        }
        mArchiveOpenJob = lifecycleOwner.lifecycleScope.launch {
            val openJob = coroutineContext[Job]
            var connection: ExplorerArchiveSessionConnection? = null
            var options: ExplorerArchiveOpenRequestOptions? = null
            try {
                isRefreshing = true
                val requestOptions = uiState.requestOptions()
                options = requestOptions
                connection = provider.openArchive(
                    context = context,
                    surface = uiState.surface,
                    entryPoint = uiState.entryPoint,
                    item = uiState.item,
                    action = uiState.action,
                    options = requestOptions,
                )
                enterArchiveNavigation(connection)
                connection = null
                finishArchiveOpenUi(uiState)
            } catch (error: CancellationException) {
                finishArchiveOpenUi(uiState)
                throw error
            } catch (error: ExplorerArchiveOpenInteractionException) {
                showArchiveOpenPasswordDialog(
                    uiState = uiState,
                    wrongPassword = error.interactionKind ==
                        ExplorerArchiveOpenValues.ERROR_WRONG_PASSWORD,
                )
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Failed to open host archive session", error)
                finishArchiveOpenUi(uiState)
                ViewUtils.showToast(context, error.message ?: error.toString(), true)
            } finally {
                options?.close()
                connection?.close()
                isRefreshing = false
                if (mArchiveOpenJob === openJob) {
                    mArchiveOpenJob = null
                }
            }
        }
    }

    private fun showArchiveOpenPasswordDialog(
        uiState: ArchiveOpenUiState,
        wrongPassword: Boolean,
    ) {
        if (mArchiveOpenUiState !== uiState) return
        uiState.replacePassword(null)
        val dialog = MaterialDialog.Builder(context)
            .title(R.string.dialog_title_archive_password)
            .content(
                if (wrongPassword) {
                    R.string.error_archive_password_incorrect
                } else {
                    R.string.dialog_message_archive_open_password_required
                },
            )
            .inputType(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            .input(context.getString(R.string.hint_archive_password), "", false) { current, input ->
                val password = input?.let { value ->
                    CharArray(value.length) { index -> value[index] }
                }
                current.inputEditText?.text?.clear()
                if (
                    mArchiveOpenUiState === uiState &&
                    password != null &&
                    password.isNotEmpty()
                ) {
                    uiState.replacePassword(password)
                    uiState.interactionDialog = null
                    current.dismiss()
                    startArchiveOpenAttempt(uiState)
                }
                password?.fill('\u0000')
            }
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { current, _ ->
                current.inputEditText?.text?.clear()
                uiState.interactionDialog = null
                current.dismiss()
                finishArchiveOpenUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH),
        )
        uiState.interactionDialog = dialog
    }

    private fun finishArchiveOpenUi(uiState: ArchiveOpenUiState) {
        if (mArchiveOpenUiState !== uiState) return
        mArchiveOpenUiState = null
        uiState.interactionDialog?.inputEditText?.text?.clear()
        uiState.interactionDialog?.dismiss()
        uiState.interactionDialog = null
        uiState.close()
    }

    private fun dismissArchiveOpenUi() {
        val uiState = mArchiveOpenUiState ?: return
        finishArchiveOpenUi(uiState)
    }

    private fun cancelArchiveOpenUi() {
        mArchiveOpenJob?.cancel()
        mArchiveOpenJob = null
        dismissArchiveOpenUi()
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
        mArchiveEntryOpenJob?.cancel()
        mArchiveEntryOpenJob = null
        mArchiveFilenameReindexJob?.cancel()
        mArchiveFilenameReindexJob = null
        mArchiveExtractionStartJob?.cancel()
        mArchiveExtractionStartJob = null
        dismissArchiveExtractionUi()
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
        val state = mArchiveNavigationState
        if (state == null || action == null || !item.canOpenEntry) {
            ViewUtils.showToast(context, R.string.text_unavailable)
            return
        }
        val maxBytes = when (action) {
            ExplorerPrimaryAction.DOCUMENT_PREVIEW -> DocumentPreviewActivity.MAX_DOCUMENT_BYTES.toLong()
            ExplorerPrimaryAction.IMAGE_VIEW,
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
            -> MAX_ARCHIVE_IMAGE_PREVIEW_BYTES
            ExplorerPrimaryAction.MEDIA_PLAY -> MAX_ARCHIVE_MEDIA_PREVIEW_BYTES
        }
        if (item.size > maxBytes) {
            ViewUtils.showToast(
                context,
                context.getString(
                    R.string.error_document_preview_file_too_large,
                    PFiles.formatSizeWithUnit(maxBytes),
                ),
                true,
            )
            return
        }
        if (mArchiveEntryOpenJob?.isActive == true) return
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        mArchiveEntryOpenJob = lifecycleOwner.lifecycleScope.launch {
            val openingJob = coroutineContext[Job]
            try {
                isRefreshing = true
                val materialized = withContext(Dispatchers.IO) {
                    state.provider.materializeEntry(item, maxBytes)
                }
                if (mArchiveNavigationState !== state) return@launch
                when (action) {
                    ExplorerPrimaryAction.DOCUMENT_PREVIEW -> {
                        DocumentPreviewActivity.launch(context, materialized.path, item.name)
                    }
                    ExplorerPrimaryAction.IMAGE_VIEW,
                    ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
                    -> ExplorerFileItem(materialized, null).view(context)
                    ExplorerPrimaryAction.MEDIA_PLAY -> ExplorerFileItem(materialized, null).play(context)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (mArchiveNavigationState === state) {
                    Log.e(LOG_TAG, "Failed to open archive entry", error)
                    val message = if (error is ArchiveEntryTooLargeException) {
                        context.getString(
                            R.string.error_document_preview_file_too_large,
                            PFiles.formatSizeWithUnit(error.limitBytes),
                        )
                    } else {
                        context.getString(R.string.text_cannot_read_file)
                    }
                    ViewUtils.showToast(context, message, true)
                }
            } finally {
                if (mArchiveEntryOpenJob === openingJob) {
                    isRefreshing = false
                    mArchiveEntryOpenJob = null
                }
            }
        }
    }

    private fun showArchiveFilenameEncodingDialog() {
        val provider = mArchiveNavigationState?.provider ?: return
        val charsetNames = provider.filenameCharsetNames
        if (
            charsetNames.isEmpty() ||
            mArchiveFilenameReindexJob?.isActive == true ||
            mArchiveEntryOpenJob?.isActive == true ||
            mArchiveExtractionUiState != null
        ) {
            return
        }
        val defaultLabel = buildString {
            append(context.getString(R.string.dialog_button_use_default))
            if (provider.filenameCharsetOverride == null) {
                provider.filenameCharsetName?.let { detected ->
                    append(" (")
                    append(detected)
                    append(')')
                }
            }
        }
        val choices = listOf(defaultLabel) + charsetNames
        val selectedIndex = provider.filenameCharsetOverride
            ?.let { selected ->
                charsetNames.indexOfFirst { name -> name.equals(selected, ignoreCase = true) }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            }
            ?: 0
        MaterialDialog.Builder(context)
            .title(R.string.text_file_encoding)
            .items(choices)
            .itemsCallbackSingleChoice(selectedIndex) { _, _, _, _ -> true }
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .onPositive { dialog, _ ->
                val requested = dialog.selectedIndex
                    .takeIf { it > 0 }
                    ?.let { index -> charsetNames.getOrNull(index - 1) }
                if (requested != provider.filenameCharsetOverride) {
                    reindexArchiveFilenameCharset(requested)
                }
            }
            .show()
    }

    private fun reindexArchiveFilenameCharset(filenameCharsetName: String?) {
        val archiveState = mArchiveNavigationState ?: return
        val provider = archiveState.provider
        val page = currentPageState.page as? ArchiveExplorerPage ?: return
        if (mArchiveFilenameReindexJob?.isActive == true) return
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: return
        val pageLocator = provider.locatorFor(page)
        val selectionLocators = mSelectedItems.values.mapNotNull { item ->
            (item as? ArchiveExplorerItem)?.let(provider::locatorFor)
        }
        val previousScrollY = currentPageState.scrollY
        val previousDirsCollapsed = currentPageState.dirsCollapsed
        val previousFilesCollapsed = currentPageState.filesCollapsed
        mRefreshDisposable?.dispose()
        mRefreshDisposable = null
        mArchiveFilenameReindexJob = lifecycleOwner.lifecycleScope.launch {
            val reindexJob = coroutineContext[Job]
            var refreshStarted = false
            try {
                isRefreshing = true
                updateArchiveExtractionAction()
                val restored = withContext(Dispatchers.IO) {
                    provider.reindexFilenameCharset(filenameCharsetName)
                    val restoredPages = runCatching { provider.restorePage(pageLocator) }
                        .getOrElse { listOf(provider.rootPage) }
                    val restoredSelection = selectionLocators.mapNotNull { locator ->
                        runCatching { provider.restoreItem(locator) }.getOrNull()
                    }
                    ArchiveFilenameReindexResult(restoredPages, restoredSelection)
                }
                if (mArchiveNavigationState !== archiveState) return@launch

                val restoredPage = restored.restoredPages.last()
                val exactPageRestored = restoredPage.entryId == pageLocator.lastOrNull()
                mPageStateHistory.clear()
                restored.restoredPages.dropLast(1).forEach { ancestor ->
                    mPageStateHistory.push(ExplorerPageState(ancestor))
                }
                mSelectedItems.clear()
                restored.restoredSelection.forEach { item ->
                    mSelectedItems[item.path] = item
                }
                setCurrentPageState(
                    ExplorerPageState(restoredPage).also { state ->
                        if (exactPageRestored) {
                            state.scrollY = previousScrollY
                            state.dirsCollapsed = previousDirsCollapsed
                            state.filesCollapsed = previousFilesCollapsed
                        }
                    },
                )
                updateSelectionToolbar()
                refreshCurrentPage()
                refreshStarted = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (mArchiveNavigationState === archiveState) {
                    Log.e(LOG_TAG, "Failed to rebuild archive filename index", error)
                    ViewUtils.showToast(context, error.message ?: error.toString(), true)
                    refreshCurrentPage()
                    refreshStarted = true
                }
            } finally {
                if (mArchiveFilenameReindexJob === reindexJob) {
                    mArchiveFilenameReindexJob = null
                }
                if (!refreshStarted) isRefreshing = false
                updateArchiveExtractionAction()
            }
        }
    }

    private fun extractCurrentArchiveFolder() {
        val page = currentPage as? ArchiveExplorerPage ?: return
        startArchiveExtraction(listOf(page))
    }

    private fun startArchiveExtraction(items: List<ArchiveExplorerItem>) {
        if (mArchiveNavigationState == null) return
        if (mArchiveExtractionUiState != null || items.isEmpty()) return
        if (findViewTreeLifecycleOwner() == null) return
        val uiState = ArchiveExtractionUiState(items.toList())
        mArchiveExtractionUiState = uiState
        updateArchiveExtractionAction()
        if (mSelectedItems.isNotEmpty()) updateSelectionToolbar()
        startArchiveExtractionAttempt(uiState)
    }

    private fun startArchiveExtractionAttempt(uiState: ArchiveExtractionUiState) {
        if (mArchiveExtractionUiState !== uiState || uiState.attempt != null) return
        val archiveState = mArchiveNavigationState ?: run {
            finishArchiveExtractionUi(uiState)
            return
        }
        val lifecycleOwner = findViewTreeLifecycleOwner() ?: run {
            finishArchiveExtractionUi(uiState)
            return
        }
        val controller = DialogUtils.OperationController()
        val progressSession = DialogUtils.ProgressDialogSession(controller)
        val attempt = ArchiveExtractionAttempt(controller, progressSession)
        uiState.attempt = attempt

        progressSession.scheduleShow(
            MaterialDialog.Builder(context)
                .title(R.string.text_extracting_archive)
                .content(R.string.text_preparing_archive_extraction)
                .progress(false, DialogUtils.PROGRESS_MAX_LARGE, true)
                .negativeText(R.string.dialog_button_cancel)
                .onNegative { _, _ -> requestArchiveExtractionCancellation(uiState, attempt) }
                .cancelable(false)
                .autoDismiss(false),
        )

        val listener = object : ExplorerArchiveExtractionListener {
            override fun onProgress(progress: ExplorerArchiveExtractionProgress) {
                post { renderArchiveExtractionProgress(uiState, attempt, progress) }
            }

            override fun onCompleted(result: ExplorerArchiveExtractionResult) {
                post { completeArchiveExtraction(uiState, attempt, result) }
            }

            override fun onFailed(failure: ExplorerArchiveExtractionFailure) {
                post { failArchiveExtraction(uiState, attempt, failure) }
            }
        }
        mArchiveExtractionStartJob = lifecycleOwner.lifecycleScope.launch {
            val startJob = coroutineContext[Job]
            var options: ExplorerArchiveExtractionRequestOptions? = null
            try {
                val requestOptions = uiState.requestOptions()
                options = requestOptions
                val operationId = withContext(Dispatchers.IO) {
                    archiveState.provider.startExtraction(uiState.items, requestOptions, listener)
                }
                attempt.operationId = operationId
                if (controller.cancelled.get()) {
                    withContext(Dispatchers.IO) {
                        archiveState.provider.cancelExtraction(operationId)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(LOG_TAG, "Failed to start host archive extraction", error)
                failArchiveExtraction(
                    uiState,
                    attempt,
                    ExplorerArchiveExtractionFailure(
                        ExplorerArchiveSessionValues.EXTRACTION_ERROR_UNKNOWN,
                        error.message ?: error.toString(),
                    ),
                )
            } finally {
                options?.close()
                if (mArchiveExtractionStartJob === startJob) {
                    mArchiveExtractionStartJob = null
                }
            }
        }
    }

    private fun requestArchiveExtractionCancellation(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
    ) {
        if (mArchiveExtractionUiState !== uiState || uiState.attempt !== attempt) return
        attempt.controller.cancel()
        attempt.progressSession.setContentThrottled(
            mutableListOf(context.getString(R.string.text_cancelling_archive_extraction)),
        )
        val operationId = attempt.operationId ?: return
        val archiveState = mArchiveNavigationState ?: return
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            runCatching { archiveState.provider.cancelExtraction(operationId) }
                .onFailure { error -> Log.w(LOG_TAG, "Failed to cancel archive extraction", error) }
        }
    }

    private fun renderArchiveExtractionProgress(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        progress: ExplorerArchiveExtractionProgress,
    ) {
        if (mArchiveExtractionUiState !== uiState || uiState.attempt !== attempt) return
        val content = when (progress.phase) {
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_PREPARING ->
                mutableListOf(context.getString(R.string.text_preparing_archive_extraction))
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_EXTRACTING -> buildList {
                add(
                    context.getString(
                        R.string.text_archive_extraction_progress,
                        progress.completedEntries,
                        progress.totalEntries,
                    ),
                )
                progress.currentEntry?.let(::add)
            }.toMutableList()
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_COMMITTING ->
                mutableListOf(context.getString(R.string.text_committing_archive_extraction))
            ExplorerArchiveSessionValues.EXTRACTION_PHASE_CLEANING_UP ->
                mutableListOf(context.getString(R.string.text_cleaning_archive_extraction))
            else -> return
        }
        attempt.progressSession.setContentThrottled(content)
        if (progress.totalBytes > 0L) {
            attempt.progressSession.setProgressThrottled(progress.bytesWritten, progress.totalBytes)
        }
    }

    private fun completeArchiveExtraction(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        result: ExplorerArchiveExtractionResult,
    ) {
        if (mArchiveExtractionUiState !== uiState || uiState.attempt !== attempt) return
        finishArchiveExtractionUi(uiState)
        ViewUtils.showToast(
            context,
            context.getString(R.string.text_archive_extraction_completed, result.outputDisplayPath),
        )
    }

    private fun failArchiveExtraction(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
        failure: ExplorerArchiveExtractionFailure,
    ) {
        if (mArchiveExtractionUiState !== uiState || uiState.attempt !== attempt) return
        if (
            failure.code == ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED &&
            failure.interactionKind != ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_NONE
        ) {
            finishArchiveExtractionAttempt(uiState, attempt)
            showArchiveExtractionInteraction(uiState, failure.interactionKind)
            return
        }
        finishArchiveExtractionUi(uiState)
        val message = when (failure.code) {
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_CANCELLED ->
                context.getString(R.string.text_archive_extraction_cancelled)
            ExplorerArchiveSessionValues.EXTRACTION_ERROR_INTERACTION_REQUIRED ->
                context.getString(R.string.error_archive_extraction_requires_interaction)
            else -> context.getString(R.string.error_archive_extraction_failed, failure.message)
        }
        ViewUtils.showToast(context, message, failure.code != ExplorerArchiveSessionValues.EXTRACTION_ERROR_CANCELLED)
    }

    private fun showArchiveExtractionInteraction(
        uiState: ArchiveExtractionUiState,
        interactionKind: Int,
    ) {
        if (mArchiveExtractionUiState !== uiState) return
        when (interactionKind) {
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_PASSWORD_REQUIRED,
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_WRONG_PASSWORD,
            -> showArchivePasswordDialog(
                uiState,
                wrongPassword = interactionKind ==
                    ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_WRONG_PASSWORD,
            )
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_UNSAFE_PATHS ->
                showArchiveExtractionConfirmation(
                    uiState = uiState,
                    title = R.string.dialog_title_archive_unsafe_paths,
                    message = R.string.dialog_message_archive_skip_unsafe_paths,
                ) { uiState.skipUnsafePaths = true }
            ExplorerArchiveSessionValues.EXTRACTION_INTERACTION_RESOURCE_BUDGET ->
                showArchiveExtractionConfirmation(
                    uiState = uiState,
                    title = R.string.dialog_title_archive_resource_budget,
                    message = R.string.dialog_message_archive_resource_budget,
                ) { uiState.allowResourceBudgetOverride = true }
            else -> {
                finishArchiveExtractionUi(uiState)
                ViewUtils.showToast(
                    context,
                    R.string.error_archive_extraction_requires_interaction,
                    true,
                )
            }
        }
    }

    private fun showArchivePasswordDialog(
        uiState: ArchiveExtractionUiState,
        wrongPassword: Boolean,
    ) {
        uiState.replacePassword(null)
        val dialog = MaterialDialog.Builder(context)
            .title(R.string.dialog_title_archive_password)
            .content(
                if (wrongPassword) {
                    R.string.error_archive_password_incorrect
                } else {
                    R.string.dialog_message_archive_password_required
                },
            )
            .inputType(
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            )
            .input(context.getString(R.string.hint_archive_password), "", false) { current, input ->
                val password = input?.let { value ->
                    CharArray(value.length) { index -> value[index] }
                }
                current.inputEditText?.text?.clear()
                if (
                    mArchiveExtractionUiState === uiState &&
                    password != null &&
                    password.isNotEmpty()
                ) {
                    uiState.replacePassword(password)
                    uiState.interactionDialog = null
                    current.dismiss()
                    startArchiveExtractionAttempt(uiState)
                }
                password?.fill('\u0000')
            }
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onNegative { current, _ ->
                current.inputEditText?.text?.clear()
                uiState.interactionDialog = null
                current.dismiss()
                finishArchiveExtractionUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        dialog.inputEditText?.filters = arrayOf(
            InputFilter.LengthFilter(ExplorerActionProtocol.MAX_ARCHIVE_PASSWORD_LENGTH),
        )
        uiState.interactionDialog = dialog
    }

    private fun showArchiveExtractionConfirmation(
        uiState: ArchiveExtractionUiState,
        title: Int,
        message: Int,
        confirm: () -> Unit,
    ) {
        val dialog = MaterialDialog.Builder(context)
            .title(title)
            .content(message)
            .positiveText(R.string.dialog_button_continue)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { current, _ ->
                if (mArchiveExtractionUiState === uiState) {
                    confirm()
                    uiState.interactionDialog = null
                    current.dismiss()
                    startArchiveExtractionAttempt(uiState)
                }
            }
            .onNegative { current, _ ->
                uiState.interactionDialog = null
                current.dismiss()
                finishArchiveExtractionUi(uiState)
            }
            .cancelable(false)
            .autoDismiss(false)
            .show()
        uiState.interactionDialog = dialog
    }

    private fun finishArchiveExtractionAttempt(
        uiState: ArchiveExtractionUiState,
        attempt: ArchiveExtractionAttempt,
    ) {
        if (uiState.attempt !== attempt) return
        uiState.attempt = null
        attempt.progressSession.dismissImmediately()
    }

    private fun finishArchiveExtractionUi(uiState: ArchiveExtractionUiState) {
        if (mArchiveExtractionUiState !== uiState) return
        mArchiveExtractionUiState = null
        uiState.attempt?.let { attempt ->
            attempt.controller.cancel()
            attempt.progressSession.dismissSafely()
        }
        uiState.attempt = null
        uiState.interactionDialog?.inputEditText?.text?.clear()
        uiState.interactionDialog?.dismiss()
        uiState.interactionDialog = null
        uiState.close()
        updateArchiveExtractionAction()
        if (mSelectedItems.isNotEmpty()) updateSelectionToolbar()
    }

    private fun dismissArchiveExtractionUi() {
        val uiState = mArchiveExtractionUiState ?: return
        uiState.attempt?.controller?.cancel()
        finishArchiveExtractionUi(uiState)
    }

    private fun updateArchiveExtractionAction() {
        val page = currentPageState.page as? ArchiveExplorerPage
        val supported = page != null &&
            page.canExtractEntry &&
            mArchiveNavigationState?.provider?.canExtractEntries == true
        binding?.archiveExtractCurrent?.apply {
            isVisible = supported
            isEnabled = supported &&
                !isInSelectionMode &&
                mArchiveExtractionUiState == null &&
                mArchiveFilenameReindexJob?.isActive != true
            alpha = if (isEnabled) 1.0f else 0.38f
        }
        updateArchiveFilenameEncodingAction()
    }

    private fun updateArchiveFilenameEncodingAction() {
        val provider = mArchiveNavigationState?.provider
        val supported = currentPageState.page is ArchiveExplorerPage &&
            provider?.filenameCharsetNames?.isNotEmpty() == true
        binding?.archiveFilenameEncoding?.apply {
            isVisible = supported
            isEnabled = supported &&
                !isInSelectionMode &&
                mArchiveExtractionUiState == null &&
                mArchiveFilenameReindexJob?.isActive != true
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
        val layoutManager = explorerItemListView!!.layoutManager as LinearLayoutManager?
        if (layoutManager != null) {
            val currentScrollY = mArchiveNavigationState?.previousPageState?.scrollY
                ?: layoutManager.findFirstCompletelyVisibleItemPosition()
            putInt(getPrefKey("page_state"), currentScrollY)
        }
    }

    private fun restorePageState() {
        val scrollY = getInt(getPrefKey("page_state"), -1)
        if (scrollY > 0) {
            currentPageState.scrollY = scrollY
            // The asynchronous page refresh applies this once after restored contents are ready.
            // Applying it against the old/empty adapter first causes two visible layout passes.
            // zh-CN: 等目录内容恢复后只应用一次, 避免先对旧列表滚动再二次布局.
        }
    }

    // TODO by SuperMonster003 on Apr 1, 2023.
    //  ! Apparently, a more graceful way is needed.
    //  ! zh-CN: 显然, 需要一个更优雅的实现方式.
    private fun saveExplorerState() {
        val archiveState = mArchiveNavigationState
        val persistentPageState = archiveState?.previousPageState ?: currentPageState
        val persistentHistory = archiveState?.previousHistory ?: mPageStateHistory.toList()
        val currentPath = persistentPageState.page?.path ?: return
        putString(getPrefKey("explorer_current"), currentPath)

        val rootPath = archiveState?.previousRootPath
            ?: if (persistentHistory.isEmpty()) path else persistentHistory.first().page!!.path
        putString(getPrefKey("explorer_root"), rootPath)

        val historyItems = LinkedList<String>()
        for (explorerPageState in persistentHistory) {
            historyItems.add(
                (explorerPageState.page!!.path
                        + "," + explorerPageState.scrollY
                        + "," + explorerPageState.dirsCollapsed
                        + "," + explorerPageState.filesCollapsed)
            )
        }
        putLinkedList(getPrefKey("explorer_history"), historyItems)
    }

    // TODO by SuperMonster003 on Apr 1, 2023.
    //  ! Apparently, a more graceful way is needed.
    //  ! zh-CN: 显然, 需要一个更优雅的实现方式.
    private fun restoreExplorerState() {
        val storedCurrentPath = getStringOrNull(getPrefKey("explorer_current"))
        if (storedCurrentPath != null) {
            val storedRootPath = getStringOrNull(getPrefKey("explorer_root"))
            setExplorer(storedRootPath, storedCurrentPath)
        } else {
            setDefaultExplorer()
        }

        val storedHistory = getLinkedList(getPrefKey("explorer_history"))
        for (dataString in storedHistory) {
            val split = dataString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val (pagePath, scrollY, dirsCollapsed, filesCollapsed) = split
            mPageStateHistory.push(ExplorerPageState(ExplorerDirPage.createRoot(pagePath)).also { pageState ->
                pageState.scrollY = scrollY.toInt()
                pageState.dirsCollapsed = dirsCollapsed.toBoolean()
                pageState.filesCollapsed = filesCollapsed.toBoolean()
            })
        }
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

    private fun positionOfCategoryFile() = when {
        currentPageState.dirsCollapsed -> 1
        else -> explorerItemManager.groupCount() + 1
    }

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
            .map { newManager -> newManager to buildRefreshMeta(newManager) }
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

    /**
     * Dispatches an in-place refresh as a minimal diff so unchanged rows keep their views and
     * the live scroll position, falling back to a full rebind when diffing is not applicable.
     * zh-CN: 原地刷新按最小差量分发, 未变化的行保留视图与当前滚动位置, 差量不可行时回退全量重绑定.
     */
    private fun notifyRefreshedInPlace(
        oldManager: ExplorerItemManager,
        oldMeta: Map<String, Long>,
        newManager: ExplorerItemManager,
        newMeta: Map<String, Long>,
    ) {
        val dispatched = runCatching {
            val oldRows = buildRefreshRows(oldManager, oldMeta)
            val newRows = buildRefreshRows(newManager, newMeta)
            if (oldRows.size + newRows.size > MAX_REFRESH_DIFF_ROWS) return@runCatching false
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = oldRows.size
                override fun getNewListSize() = newRows.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldRows[oldItemPosition].sameItemAs(newRows[newItemPosition])
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldRows[oldItemPosition].sameContentAs(newRows[newItemPosition])
                }
            }, false)
            diff.dispatchUpdatesTo(mExplorerAdapter)
            true
        }.getOrElse { error ->
            Log.w(LOG_TAG, "Failed to dispatch Explorer refresh diff", error)
            false
        }
        if (!dispatched) {
            notifyDataSetChanged()
        }
    }

    private fun buildRefreshRows(manager: ExplorerItemManager, meta: Map<String, Long>): List<ExplorerRefreshRow> {
        val rows = ArrayList<ExplorerRefreshRow>(manager.count() + 2)
        rows += ExplorerRefreshRow.category(isDir = true, count = manager.groupCount())
        if (!currentPageState.dirsCollapsed) {
            repeat(manager.groupCount()) { i ->
                val dir = manager.getDirItem(i)
                rows += ExplorerRefreshRow.entry(
                    isDir = true,
                    path = dir.path,
                    fingerprint = meta[dir.path] ?: ExplorerRefreshRow.FINGERPRINT_UNKNOWN,
                )
            }
        }
        rows += ExplorerRefreshRow.category(isDir = false, count = manager.itemCount())
        if (!currentPageState.filesCollapsed) {
            repeat(manager.itemCount()) { i ->
                val file = manager.getFileItem(i)
                rows += ExplorerRefreshRow.entry(
                    isDir = false,
                    path = file.path,
                    fingerprint = meta[file.path] ?: ExplorerRefreshRow.FINGERPRINT_UNKNOWN,
                )
            }
        }
        return rows
    }

    /** Runs on a background scheduler; entry metadata reads may touch the filesystem. */
    private fun buildRefreshMeta(manager: ExplorerItemManager): Map<String, Long> {
        val meta = HashMap<String, Long>(manager.count() * 2)
        repeat(manager.groupCount()) { i ->
            val dir = manager.getDirItem(i)
            meta[dir.path] = ExplorerRefreshRow.fingerprintOf(dir.lastModified(), 0L)
        }
        repeat(manager.itemCount()) { i ->
            val file = manager.getFileItem(i)
            meta[file.path] = ExplorerRefreshRow.fingerprintOf(file.lastModified(), file.size)
        }
        return meta
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


    private inner class ExplorerAdapter : RecyclerView.Adapter<BindableViewHolder<Any>>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<Any> {
            val inflater = LayoutInflater.from(context)
            return this@ExplorerView.onCreateViewHolder(inflater, parent, viewType)
        }

        override fun onBindViewHolder(holder: BindableViewHolder<Any>, position: Int) {
            val positionOfCategoryFile = positionOfCategoryFile()
            when {
                position == positionOfCategoryDir || position == positionOfCategoryFile -> {
                    holder.bind(position == positionOfCategoryDir, position)
                }
                position < positionOfCategoryFile -> {
                    holder.bind(explorerItemManager.getDirItem(position - 1), position)
                }
                else -> {
                    val itemPosition = position - positionOfCategoryFile - 1
                    val item = explorerItemManager.getFileItem(itemPosition)
                    holder.bind(item, position)
                }
            }
        }

        override fun onViewRecycled(holder: BindableViewHolder<Any>) {
            super.onViewRecycled(holder)
            holder.onViewRecycled()
        }

        override fun getItemViewType(position: Int): Int {
            val positionOfCategoryFile = positionOfCategoryFile()
            return when {
                position == positionOfCategoryDir || position == positionOfCategoryFile -> {
                    VIEW_TYPE_CATEGORY
                }
                position < positionOfCategoryFile -> {
                    VIEW_TYPE_PAGE
                }
                else -> {
                    VIEW_TYPE_ITEM
                }
            }
        }

        fun getItemPosition(item: ExplorerItem?, i: Int): Int {
            if (item is ExplorerPage) {
                return i + positionOfCategoryDir + 1
            }
            return i + positionOfCategoryFile() + 1
        }

        fun notifyItemChanged(item: ExplorerItem?, i: Int) {
            notifyItemChanged(getItemPosition(item, i))
        }

        fun notifyItemRemoved(item: ExplorerItem?, i: Int) {
            notifyItemRemoved(getItemPosition(item, i))
        }

        fun notifyItemInserted(item: ExplorerItem?, i: Int) {
            notifyItemInserted(getItemPosition(item, i))
        }

        override fun getItemCount(): Int {
            var count = 0
            if (!currentPageState.dirsCollapsed) {
                count += explorerItemManager.groupCount()
            }
            if (!currentPageState.filesCollapsed) {
                count += explorerItemManager.itemCount()
            }
            return count + 2
        }
    }

    internal inner class ExplorerItemViewHolder(itemView: View) : BindableViewHolder<Any>(itemView) {

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
                ImageViewCompat.setImageTintList(view, mActionIconTint)
            }

            mItem.setOnClickListener { withItemSelected { onItemClick() } }
            mItem.setOnLongClickListener {
                if (withItemSelected { enterSelectionMode(mExplorerItem) }) {
                    blockListScrollUntilTouchRelease()
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
                    name = ExplorerViewHelper.getDisplayName(context, item),
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
                            context.getColor(R.color.item_background_dark),
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
                val primaryAction = when (item) {
                    is ArchiveExplorerFileItem -> ExplorerPrimaryActionResolver
                        .resolve(item.type)
                        ?.takeIf { item.canOpenEntry }
                    is ArchiveExplorerItem -> null
                    else -> ExplorerPrimaryActionResolver.resolve(item.type)
                }
                val pluginPrimaryAction = if (item is ArchiveExplorerItem) {
                    null
                } else {
                    runCatching {
                        mPluginActionProvider?.actionsFor(
                            mPluginActionSurface,
                            ExplorerActionEntryPoint.PRIMARY_BUTTON,
                            item,
                        )?.firstOrNull()
                    }.onFailure { error ->
                        Log.w(LOG_TAG, "Failed to resolve primary Explorer plugin action", error)
                    }.getOrNull()
                }
                val hasPrimaryAction = primaryAction != null || pluginPrimaryAction != null
                val shouldEditShow = item.isTextEditable || (item.isExternalEditable && !item.type.isImageFile())
                val shouldRunAsMedia =
                    !item.isExecutable && item.isMediaPlayable && primaryAction == null
                val shouldRunShow = item.isExecutable || shouldRunAsMedia
                val shouldInstallShow = item.isInstallable

                val alreadyHasTwoImportantIcons = listOf(
                    hasPrimaryAction,
                    shouldEditShow,
                    shouldRunShow,
                    shouldInstallShow,
                ).count { it } >= 2
                val shouldInfoShow = item.type.isImageFile() ||
                    (!alreadyHasTwoImportantIcons && (item.isInstallable || item.isMediaMenu || item.isMediaPlayable))

                ExplorerItemActionState(
                    primaryAction = primaryAction,
                    pluginPrimaryAction = pluginPrimaryAction,
                    editVisible = shouldEditShow,
                    runVisible = shouldRunShow,
                    runAsMedia = shouldRunAsMedia,
                    installVisible = shouldInstallShow,
                    infoVisible = shouldInfoShow,
                )
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
                    if (isInSelectionMode) {
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
            mItem.setBackgroundColor(context.getColor(R.color.item_background_dark))
            setVisibilityIf(mFirstChar, INVISIBLE)
            setVisibilityIf(mActionIconContainer, false)
            setVisibilityIf(mOptions, true)
        }

        private fun onItemClick() {
            if (isInSelectionMode) {
                toggleItemSelection(mExplorerItem)
                return
            }
            if (mExplorerItem is ArchiveExplorerFileItem) {
                openArchiveEntry(
                    mExplorerItem as ArchiveExplorerFileItem,
                    ExplorerPrimaryActionResolver.resolve(mExplorerItem.type),
                )
                notifyItemOperated()
                return
            }
            val pluginAction = resolvePluginAction(ExplorerActionEntryPoint.DEFAULT_CLICK)
            if (pluginAction == null) {
                dispatchHostItemClick()
                return
            }
            if (ExplorerActionExecution.withFallback(
                    attempt = { launchPluginAction(ExplorerActionEntryPoint.DEFAULT_CLICK, pluginAction) },
                    fallback = ::dispatchHostItemClick,
                )
            ) {
                notifyItemOperated()
            }
        }

        private fun dispatchHostItemClick() {
            onItemClickListener?.let { listener ->
                listener.onItemClick(itemView, mExplorerItem)
                notifyItemOperated()
            }
        }

        private fun bindSelectionState(item: ExplorerItem) {
            val selected = isItemSelected(item)
            mItem.isSelected = selected
            mItem.setBackgroundColor(context.getColor(if (selected) R.color.window_background_selected else R.color.item_background_dark))
            setVisibilityIf(
                mOptions,
                when {
                    item is ArchiveExplorerItem -> GONE
                    isInSelectionMode -> INVISIBLE
                    else -> VISIBLE
                },
            )
            if (isInSelectionMode) {
                if (mActionIconContainer.isVisible) {
                    setVisibilityIf(mActionIconContainer, INVISIBLE)
                }
            }
        }

        private fun bindPrimaryViewer(action: ExplorerPrimaryAction) {
            val (iconRes, contentDescriptionRes) = when (action) {
                ExplorerPrimaryAction.DOCUMENT_PREVIEW -> {
                    R.drawable.ic_preview_web_page to R.string.text_preview
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
            mPrimaryViewer.contentDescription = context.getString(contentDescriptionRes)
        }

        private fun bindPrimaryPluginAction(action: ResolvedExplorerAction) {
            mPrimaryViewer.setImageResource(R.drawable.ic_explorer_plugin_action)
            mPrimaryViewer.contentDescription = action.menuTitle
        }

        private fun bindRunAction(asMedia: Boolean) {
            mRun.setImageResource(
                if (asMedia) R.drawable.ic_play_media else R.drawable.ic_run_explorer_btn,
            )
            mRun.contentDescription = context.getString(
                if (asMedia) R.string.text_play else R.string.text_run,
            )
        }

        private fun openPrimaryViewer() {
            val action = ExplorerPrimaryActionResolver.resolve(mExplorerItem.type)
            val pluginAction = mPrimaryPluginAction
            if (action == null && pluginAction == null) return
            mRequestHostDialogHide?.run()
            if (mExplorerItem is ArchiveExplorerFileItem) {
                openArchiveEntry(mExplorerItem as ArchiveExplorerFileItem, action)
                notifyItemOperated()
                return
            }
            val fallback = { openHostPrimaryViewer(action) }
            if (pluginAction != null) {
                ExplorerActionExecution.withFallback(
                    attempt = { launchPluginAction(ExplorerActionEntryPoint.PRIMARY_BUTTON, pluginAction) },
                    fallback = fallback,
                )
            } else {
                fallback()
            }
            notifyItemOperated()
        }

        private fun openHostPrimaryViewer(action: ExplorerPrimaryAction?) {
            when (action) {
                ExplorerPrimaryAction.DOCUMENT_PREVIEW -> {
                    DocumentPreviewActivity.launch(context, mExplorerItem.path)
                }
                ExplorerPrimaryAction.IMAGE_VIEW,
                ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
                null,
                -> {
                    mExplorerItem.view(context)
                }
                ExplorerPrimaryAction.MEDIA_PLAY -> {
                    mExplorerItem.play(context)
                }
            }
        }

        private fun resolvePluginAction(
            entryPoint: ExplorerActionEntryPoint,
            item: ExplorerItem = mExplorerItem,
        ): ResolvedExplorerAction? {
            val provider = mPluginActionProvider
                ?: return null
            return runCatching {
                provider.actionsFor(mPluginActionSurface, entryPoint, item).firstOrNull()
            }.onFailure { error ->
                Log.w(LOG_TAG, "Failed to resolve Explorer plugin action from $entryPoint", error)
            }.getOrNull()
        }

        private fun launchPluginAction(
            entryPoint: ExplorerActionEntryPoint,
            pluginAction: ResolvedExplorerAction,
            item: ExplorerItem = mExplorerItem,
        ): Boolean {
            if (mPluginActionProvider == null) return false
            return runCatching {
                dispatchPluginAction(
                    context,
                    mPluginActionSurface,
                    entryPoint,
                    listOf(item),
                    pluginAction,
                ).getOrThrow()
            }.onFailure { error ->
                Log.w(LOG_TAG, "Failed to launch Explorer plugin action from $entryPoint", error)
            }.isSuccess
        }

        private fun run() {
            mRequestHostDialogHide?.run()
            when {
                mExplorerItem.isExecutable -> {
                    Scripts.runInteractive(context, ScriptFile(mExplorerItem.path))
                    notifyItemOperated()
                }
                mExplorerItem.isMediaPlayable -> {
                    mExplorerItem.play(this@ExplorerView)
                    notifyItemOperated()
                }
            }
        }

        private fun edit() {
            mRequestHostDialogHide?.run()

            when {
                mExplorerItem.isTextEditable -> {
                    Scripts.edit(context, ScriptFile(mExplorerItem.path))
                    notifyItemOperated()
                }
                mExplorerItem.isExternalEditable -> {
                    mExplorerItem.edit(this@ExplorerView)
                    notifyItemOperated()
                }
            }
        }

        private fun showInfo() {
            // Hide host overlay dialog so the info dialog can be shown on top.
            // zh-CN: 隐藏宿主 overlay 对话框, 让信息对话框显示在最上层.
            mRequestHostDialogHide?.run()

            when {
                mExplorerItem.type.isImageFile() -> {
                    ImageInfoDialogManager.showImageInfoDialog(context, File(mExplorerItem.path)) {
                        mRequestHostDialogShow?.run()
                    }
                    notifyItemOperated()
                }
                mExplorerItem.isInstallable -> {
                    ApkInfoDialogManager.showApkInfoDialog(context, mExplorerItem.toScriptFile(), null) {
                        // Restore host dialog after the info dialog is dismissed.
                        // zh-CN: 信息对话框关闭后恢复宿主对话框.
                        mRequestHostDialogShow?.run()
                    }
                    notifyItemOperated()
                }
                mExplorerItem.isMediaMenu || mExplorerItem.isMediaPlayable -> {
                    val mediaItem = mExplorerItem
                    MediaInfoDialogManager.showMediaInfoDialog(
                        context = context,
                        explorerItem = mediaItem,
                        builderApplier = null,
                        onDismissListener = {
                            // Restore host dialog after the info dialog is dismissed.
                            // zh-CN: 信息对话框关闭后恢复宿主对话框.
                            mRequestHostDialogShow?.run()
                        },
                        onPlayRequested = {
                            resolvePluginAction(ExplorerActionEntryPoint.MEDIA_INFO_DIALOG, mediaItem)?.let { action ->
                                launchPluginAction(ExplorerActionEntryPoint.MEDIA_INFO_DIALOG, action, mediaItem)
                            } == true
                        },
                    )
                    notifyItemOperated()
                }
            }
        }

        private fun install() {
            mRequestHostDialogHide?.run()
            when {
                mExplorerItem.isInstallable -> {
                    mExplorerItem.install(this@ExplorerView)
                    notifyItemOperated()
                }
                else -> {
                    mExplorerItem.view(this@ExplorerView)
                    notifyItemOperated()
                }
            }
        }

        @SuppressLint("CheckResult")
        private fun showOptionsMenu() {
            val actionItem = mExplorerItem
            val popupMenu = PopupMenu(context, mOptions)
            popupMenu.inflate(R.menu.menu_script_options)
            val menu = popupMenu.menu
            if (!mExplorerItem.type.isImageFile()) {
                menu.removeItem(R.id.action_edit_by_other_apps)
            }
            if (!mExplorerItem.isExecutable) {
                menu.removeItem(R.id.action_create_shortcut)
                menu.removeItem(R.id.action_timed_task)
                menu.removeItem(R.id.action_run_repeatedly)
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
            val samplePath = PFile(context.filesDir, WorkspaceFileProvider.SAMPLE_PATH).path
            if (!mExplorerItem.path.startsWith(samplePath)) {
                menu.removeItem(R.id.action_reset)
            }
            val pluginMenuItems = mutableMapOf<Int, org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction>()
            runCatching {
                mPluginActionProvider?.actionsFor(
                    mPluginActionSurface,
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
                val pluginAction = pluginMenuItems[menuItem.itemId]
                if (pluginAction == null) {
                    return@setOnMenuItemClickListener this@ExplorerView.onMenuItemClick(menuItem)
                }
                dispatchPluginAction(
                    context,
                    mPluginActionSurface,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                    listOf(actionItem),
                    pluginAction,
                )
                    .onSuccess { notifyItemOperated() }
                    .onFailure { error ->
                        ViewUtils.showToast(context, error.message ?: error.toString(), true)
                    }
                true
            }
            popupMenu.show()
        }

        private fun <R> withItemSelected(func: () -> R): R {
            selectedItem = mExplorerItem
            return func.invoke()
        }
    }

    internal inner class ExplorerPageViewHolder(itemView: View) : BindableViewHolder<Any>(itemView) {

        private val binding = ExplorerDirectoryBinding.bind(itemView).also {
            it.item.setOnClickListener { withItemSelected { onItemClick() } }
            it.item.setOnLongClickListener {
                val enteredSelectionMode = withItemSelected {
                    mExplorerPage?.let { page -> enterSelectionMode(page) }
                } == true
                if (enteredSelectionMode) {
                    blockListScrollUntilTouchRelease()
                }
                true
            }
        }

        private var mName = binding.name
        private var mDirDate = binding.scriptDirDate
        private var mIcon = binding.icon
        private var mOptions = binding.more.also {
            it.setOnClickListener { withItemSelected { showOptionsMenu() } }
        }

        private var mExplorerPage: ExplorerPage? = null

        init {
            listOf(mOptions).forEach { view ->
                ImageViewCompat.setImageTintList(view, mActionIconTint)
            }
        }

        override fun bind(data: Any, position: Int) {
            if (data !is ExplorerPage) return
            mName.text = ExplorerViewHelper.getDisplayName(context, data)
            mDirDate.text = PFile.getFullDateString(data.lastModified())
            mIcon.setImageResource(ExplorerViewHelper.getIconRes(data))
            val selected = isItemSelected(data)
            binding.item.isSelected = selected
            binding.item.setCardBackgroundColor(context.getColor(if (selected) R.color.window_background_selected else R.color.item_background))
            mOptions.visibility = when {
                data is ExplorerSamplePage -> GONE
                data is ArchiveExplorerPage -> GONE
                isInSelectionMode -> INVISIBLE
                else -> VISIBLE
            }
            mExplorerPage = data
        }

        private fun onItemClick() {
            mExplorerPage?.let {
                if (isInSelectionMode) {
                    toggleItemSelection(it)
                    return
                }
            }
            mExplorerPage?.let { enterDirectChildPage(it) }
        }

        private fun showOptionsMenu() {
            val explorerPage = mExplorerPage ?: return
            val popupMenu = PopupMenu(context, mOptions)
            val menu = popupMenu.menu
            popupMenu.inflate(R.menu.menu_dir_options)
            if (!explorerPage.canMove()) {
                menu.removeItem(R.id.action_move_to)
            }
            if (!explorerPage.canCopy()) {
                menu.removeItem(R.id.action_copy_to)
            }
            if (!explorerPage.canRename()) {
                menu.removeItem(R.id.action_rename)
            }
            if (!explorerPage.canDelete()) {
                menu.removeItem(R.id.action_delete)
            }
            if (!isProjectRecognitionEnabled || !ProjectConfig.isProject(explorerPage)) {
                menu.removeItem(R.id.action_timed_task)
            }
            if (!explorerPage.canSetAsWorkingDir()) {
                menu.removeItem(R.id.action_set_as_working_dir)
            }
            if (!explorerPage.canBuildApk()) {
                menu.removeItem(R.id.action_build_apk)
            }
            val samplePath = PFile(context.filesDir, WorkspaceFileProvider.SAMPLE_PATH).path
            if (!explorerPage.path.startsWith(samplePath)) {
                menu.removeItem(R.id.action_reset)
            }
            val pluginMenuItems = mutableMapOf<Int, ResolvedExplorerAction>()
            runCatching {
                mPluginActionProvider?.actionsFor(
                    mPluginActionSurface,
                    ExplorerActionEntryPoint.OVERFLOW_MENU,
                    explorerPage,
                ).orEmpty()
            }
                .getOrElse { error ->
                    Log.w(LOG_TAG, "Failed to resolve Explorer directory plugin actions", error)
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
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                pluginMenuItems[item.itemId]?.let { pluginAction ->
                    dispatchPluginAction(
                        context,
                        mPluginActionSurface,
                        ExplorerActionEntryPoint.OVERFLOW_MENU,
                        listOf(explorerPage),
                        pluginAction,
                    )
                        .onSuccess { notifyItemOperated() }
                        .onFailure { error ->
                            ViewUtils.showToast(context, error.message ?: error.toString(), true)
                        }
                    return@setOnMenuItemClickListener true
                }
                val selectedItem = selectedItem ?: return@setOnMenuItemClickListener false
                val explorerView = this@ExplorerView
                when (item.itemId) {
                    R.id.action_copy_to ->
                        ScriptOperations(context, explorerView, currentPage)
                            .copy(selectedItem.toScriptFile())
                            .ignoreElements()
                            .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_copy) })
                    R.id.action_move_to ->
                        ScriptOperations(context, explorerView, currentPage)
                            .move(selectedItem.toScriptFile())
                            .ignoreElements()
                            .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_move) })
                    R.id.action_rename ->
                        ScriptOperations(context, explorerView, currentPage)
                            .rename(selectedItem as? ExplorerFileItem)
                            .ignoreElements()
                            .subscribe({}, { showErrorDialog(it, R.string.error_failed_to_rename) })
                    R.id.action_delete ->
                        ScriptOperations(context, explorerView, currentPage)
                            .delete(selectedItem.toScriptFile())
                    R.id.action_timed_task -> {
                        mRequestHostDialogHide?.run()
                        ScriptOperations(context, explorerView, currentPage)
                            .timedTask(selectedItem.toScriptFile())
                        notifyItemOperated()
                    }
                    R.id.action_set_as_working_dir ->
                        ScriptOperations(context, explorerView, currentPage)
                            .setAsWorkingDir(selectedItem.toScriptFile())
                    R.id.action_build_apk -> {
                        mRequestHostDialogHide?.run()
                        BuildActivity.launch(context, selectedItem.path)
                    }
                    R.id.action_reset -> {
                        val o = Explorers.Providers.workspace()
                            .resetSample(selectedItem.toScriptFile())
                        if (o == null) {
                            resetFailed()
                        } else {
                            o.observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ file: ScriptFile ->
                                    if (file.exists()) {
                                        resetSucceeded()
                                    } else {
                                        resetFailed()
                                    }
                                }, Observers.toastMessage())
                        }
                    }
                    else -> null
                } != null
            }
            popupMenu.show()
        }

        private fun <R> withItemSelected(func: () -> R): R {
            selectedItem = mExplorerPage
            return func.invoke()
        }
    }

    internal inner class ExplorerCategoryViewHolder(itemView: View) : BindableViewHolder<Any>(itemView) {

        private val binding = ExplorerCategoryBinding.bind(itemView)

        private var mIsDir = false

        init {
            setOnClickListeners()

            ImageViewCompat.setImageTintList(binding.arrowIcon, mChevronIconTint)
        }

        private fun setOnClickListeners() {
            binding.sortOrder.setOnClickListener {
                if (mIsDir) {
                    sort(explorerItemManager.dirSortType, mIsDir, !explorerItemManager.isDirSortedAscending)
                    setDirOrderIconWithCurrentState()
                } else {
                    sort(explorerItemManager.fileSortType, mIsDir, !explorerItemManager.isFileSortedAscending)
                    setFileOrderIconWithCurrentState()
                }
            }
            binding.sortType.setOnClickListener {
                val themedCtx = ContextThemeWrapper(context, R.style.PopupMenu_HideIndicator)
                val popupMenu = PopupMenu(themedCtx, binding.sortType).apply {
                    inflate(R.menu.menu_sort_options)
                    setForceShowIconCompat()
                }

                isDirSortMenuShowing = mIsDir

                val currentSortType = if (mIsDir) explorerItemManager.dirSortType else explorerItemManager.fileSortType
                val initItemId = when (currentSortType) {
                    SORT_TYPE_DATE -> R.id.action_sort_by_date
                    SORT_TYPE_SIZE -> R.id.action_sort_by_size
                    SORT_TYPE_TYPE -> R.id.action_sort_by_type
                    else -> R.id.action_sort_by_name
                }
                popupMenu.menu.findItem(initItemId).isChecked = true
                ThemeColorHelper.setThemeColorPrimary(popupMenu.menu, themedCtx, context.getColor(R.color.item_background), initItemId)

                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    item.isChecked = true
                    when (item.itemId) {
                        R.id.action_sort_by_name -> sort(SORT_TYPE_NAME, isDirSortMenuShowing, true)
                        R.id.action_sort_by_date -> sort(SORT_TYPE_DATE, isDirSortMenuShowing, false)
                        R.id.action_sort_by_size -> sort(SORT_TYPE_SIZE, isDirSortMenuShowing, false)
                        R.id.action_sort_by_type -> sort(SORT_TYPE_TYPE, isDirSortMenuShowing, true)
                    }
                    ThemeColorHelper.setThemeColorPrimary(popupMenu.menu, themedCtx, true, item.itemId)
                    true
                }
                popupMenu.show()
            }
            binding.goUp.setOnClickListener {
                if (canGoUp()) goUp()
            }
            binding.titleContainer.setOnClickListener {
                if (mIsDir) {
                    currentPageState.dirsCollapsed = !currentPageState.dirsCollapsed
                } else {
                    currentPageState.filesCollapsed = !currentPageState.filesCollapsed
                }
                mExplorerAdapter.notifyDataSetChanged()
            }
        }

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun bind(isDirCategory: Any, position: Int) {
            if (isDirCategory !is Boolean) return
            val titleRes = if (isDirCategory) R.string.text_folder else R.string.text_file
            val count = if (isDirCategory) explorerItemManager.groupCount() else explorerItemManager.itemCount()
            binding.title.text = "${context.getString(titleRes)}  [ $count ]"
            binding.titleContainer.contentDescription = binding.title.text
            mIsDir = isDirCategory
            if (isDirCategory && canGoUp()) {
                binding.goUp.visibility = VISIBLE
            } else {
                binding.goUp.visibility = GONE
            }
            if (isDirCategory) {
                binding.arrowIcon.rotation = (if (currentPageState.dirsCollapsed) -90 else 0).toFloat()
                setDirOrderIconWithCurrentState()
            } else {
                binding.arrowIcon.rotation = (if (currentPageState.filesCollapsed) -90 else 0).toFloat()
                setFileOrderIconWithCurrentState()
            }
        }

        private fun setFileOrderIconWithCurrentState() {
            val ascending = explorerItemManager.isFileSortedAscending
            binding.sortOrder.setImageResource(if (ascending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
            updateSortOrderContentDescription(ascending)
        }

        private fun setDirOrderIconWithCurrentState() {
            val ascending = explorerItemManager.isDirSortedAscending
            binding.sortOrder.setImageResource(if (ascending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
            updateSortOrderContentDescription(ascending)
        }

        private fun updateSortOrderContentDescription(ascending: Boolean) {
            binding.sortOrder.contentDescription = buildString {
                append(context.getString(R.string.text_sort_order))
                append(": ")
                append(
                    context.getString(
                        if (ascending) R.string.text_sort_ascending else R.string.text_sort_descending,
                    ),
                )
            }
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
        private const val SELECTION_TOOLBAR_CONTENT_HEIGHT_DP = 113F
        private const val SELECTION_TOOLBAR_SHOW_DURATION = 120L
        private const val SELECTION_TOOLBAR_HIDE_DURATION = 90L
        private const val MAX_ARCHIVE_IMAGE_PREVIEW_BYTES = 256L * 1024L * 1024L
        private const val MAX_ARCHIVE_MEDIA_PREVIEW_BYTES = 512L * 1024L * 1024L

        // Upper bound for diff-based refresh dispatching; larger snapshots fall back to
        // a full rebind to keep the main-thread diff cost negligible.
        // zh-CN: 差量刷新的行数上限, 超出时回退全量重绑定, 确保主线程差量计算开销可忽略.
        private const val MAX_REFRESH_DIFF_ROWS = 6000

        @Suppress("ConstPropertyName")
        private const val positionOfCategoryDir = 0

        protected const val VIEW_TYPE_ITEM: Int = 0
        protected const val VIEW_TYPE_PAGE: Int = 1

        // @Hint by Stardust (https://github.com/hyb1996) on Aug 20, 2017.
        //  ! Category 是类别, 例如 "文件", "文件夹" 这样的文本.
        protected const val VIEW_TYPE_CATEGORY: Int = 2

        fun clearViewStates() {
            remove(getPrefKey("page_state"))
            remove(getPrefKey("explorer_current"))
            remove(getPrefKey("explorer_root"))
            remove(getPrefKey("explorer_history"))
        }

        fun getPrefKey(key: String): String = ExplorerView::class.java.simpleName + '.' + key

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
