package org.autojs.autojs.ui.explorer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Pref.getInt
import org.autojs.autojs.core.pref.Pref.getLinkedList
import org.autojs.autojs.core.pref.Pref.getStringOrNull
import org.autojs.autojs.core.pref.Pref.putInt
import org.autojs.autojs.core.pref.Pref.putLinkedList
import org.autojs.autojs.core.pref.Pref.putString
import org.autojs.autojs.core.pref.Pref.remove
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.groundwork.WrapContentGridLayoutManger
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
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout
import org.autojs.autojs.ui.common.ScriptLoopDialog
import org.autojs.autojs.ui.common.ScriptOperations
import org.autojs.autojs.ui.explorer.ExplorerProjectToolbar.OnOperateListener
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager
import org.autojs.autojs.ui.main.scripts.MediaInfoDialogManager
import org.autojs.autojs.ui.project.BuildActivity
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager
import org.autojs.autojs.ui.widget.BindableViewHolder
import org.autojs.autojs.ui.widget.FirstCharView
import org.autojs.autojs.util.EnvironmentUtils.externalStoragePath
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.Observers
import org.autojs.autojs.util.ViewUtils.showSnack
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs.util.WorkingDirectoryUtils.path
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ExplorerCategoryBinding
import org.autojs.autojs6.databinding.ExplorerDirectoryBinding
import org.autojs.autojs6.databinding.ExplorerFileBinding
import org.autojs.autojs6.databinding.ExplorerFirstCharIconBinding
import org.autojs.autojs6.databinding.ExplorerViewBinding
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.util.*
import java.util.concurrent.Callable

/**
 * Created by Stardust on Aug 21, 2017.
 * Modified by SuperMonster003 as of Apr 1, 2023.
 * Transformed by SuperMonster003 on Nov 23, 2024.
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

    protected var explorerItemListView: RecyclerView? = null
        private set

    private lateinit var mProjectToolbar: ExplorerProjectToolbar

    private var mExplorer: Explorer? = null

    private val mExplorerAdapter = ExplorerAdapter()
    private var mFilter: ((ExplorerItem) -> Boolean)? = null

    private var mOnItemOperateListener: OnItemOperateListener? = null

    private val mPageStateHistories = Stack<ExplorerPageState>()
    private var mDirectorySpanSize = 2

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        Log.d(LOG_TAG, "item bg = " + Integer.toHexString(ContextCompat.getColor(context, R.color.item_background)))
        ExplorerViewBinding.inflate(LayoutInflater.from(context), this, true).also { binding ->
            this.binding = binding
            explorerItemListView = initExplorerItemListView(binding.explorerItemList)
            mProjectToolbar = binding.projectToolbar
        }
        restoreSortConfig()
        setOnRefreshListener(this)
    }

    private fun initExplorerItemListView(explorerItemListView: RecyclerView) = explorerItemListView.also {
        it.adapter = mExplorerAdapter
        it.setItemViewCacheSize(4)
        it.layoutManager = WrapContentGridLayoutManger(context, 2).also { manager ->
            manager.setDebugInfo("ExplorerView")
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
                }
                ExplorerChangeEvent.REMOVE -> {
                    i = explorerItemManager.remove(item)
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i)
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
        mExplorer?.unregisterChangeListener(this)
        mPageStateHistories.clear()
        binding = null
    }

    override fun onRefresh() {
        currentPageState.scrollY = 0
        mExplorer?.notifyChildrenChanged(currentPage)
        mProjectToolbar.refresh()
        mProjectToolbar.updateVisibility()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.rename -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .rename(selectedItem as ExplorerFileItem?)
                    .subscribe(Observers.emptyObserver())
            }
            R.id.delete -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .delete(selectedItem!!.toScriptFile())
            }
            R.id.run_repeatedly -> {
                ScriptLoopDialog(context, selectedItem!!.toScriptFile())
                    .show()
                notifyItemOperated()
            }
            R.id.create_shortcut -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .createShortcut(selectedItem!!.toScriptFile())
            }
            R.id.open_by_other_apps -> {
                Scripts.openByOtherApps(selectedItem!!.toScriptFile())
                notifyItemOperated()
            }
            R.id.send -> {
                Scripts.send(context, selectedItem!!.toScriptFile())
                notifyItemOperated()
            }
            R.id.timed_task -> {
                ScriptOperations(context, this@ExplorerView, currentPage)
                    .timedTask(selectedItem!!.toScriptFile())
                notifyItemOperated()
            }
            R.id.action_build_apk -> {
                BuildActivity.launch(context, selectedItem!!.path)
                notifyItemOperated()
            }
            R.id.reset -> {
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

    fun setProjectToolbarRunnableOnly(b: Boolean) {
        mProjectToolbar.setRunnableOnly(b)
    }

    fun enterDirectChildPage(childItemGroup: ExplorerPage) {
        val layoutManager = explorerItemListView!!.layoutManager as LinearLayoutManager?
        if (layoutManager != null) {
            // @Overwrite by SuperMonster003 on Apr 3, 2023.
            //  ! Should be "first" instead of "last".
            //  ! zh-CN: 应该是 "first" 而非 "last".
            //  # mCurrentPageState.scrollY = layoutManager.findLastCompletelyVisibleItemPosition();
            currentPageState.scrollY = layoutManager.findFirstCompletelyVisibleItemPosition()
        }
        mPageStateHistories.push(currentPageState)
        setCurrentPageState(ExplorerPageState(childItemGroup))
        refreshCurrentPage()
    }

    fun setOnItemClickListener(onItemClickListener: OnItemClickListener?) {
        this.onItemClickListener = onItemClickListener
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage) {
        if (mExplorer != null) {
            mExplorer!!.unregisterChangeListener(this)
        }
        mExplorer = explorer
        setRootPage(rootPage)
        mExplorer!!.registerChangeListener(this)
    }

    fun setExplorer(explorer: Explorer?, rootPage: ExplorerPage, currentPage: ExplorerPage) {
        mExplorer?.unregisterChangeListener(this)
        mExplorer = explorer
        setCurrentPageState(ExplorerPageState(rootPage))
        mExplorer?.registerChangeListener(this)
        enterChildPage(currentPage)
    }

    fun setExplorer(rootPath: String?, currentPath: String) {
        setExplorer(Explorers.workspace(), ExplorerDirPage.createRoot(Objects.requireNonNullElseGet(rootPath, WorkingDirectoryUtils::path)), ExplorerDirPage.createRoot(currentPath))
    }

    private fun setRootPage(page: ExplorerPage) {
        setCurrentPageState(ExplorerPageState(page))
        refreshCurrentPage()
    }

    private fun setDefaultExplorer() {
        setExplorer(externalStoragePath, path)
    }

    private fun setCurrentPageState(pageState: ExplorerPageState) {
        currentPageState = pageState
    }

    private fun enterChildPage(childPage: ExplorerPage) {
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
        return !mPageStateHistories.empty()
    }

    fun goBack() {
        setCurrentPageState(mPageStateHistories.pop())
        refreshCurrentPage()
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
            val currentScrollY = layoutManager.findFirstCompletelyVisibleItemPosition()
            putInt(getPrefKey("page_state"), currentScrollY)
        }
    }

    private fun restorePageState() {
        val scrollY = getInt(getPrefKey("page_state"), -1)
        if (scrollY > 0) {
            currentPageState.scrollY = scrollY
            Observable.empty<Any>()
                .subscribeOn(Schedulers.io())
                .subscribe {
                    notifyDataSetChanged()
                    post { scrollToPositionOrdinarily() }
                }
        }
    }

    private fun scrollToPositionOrdinarily() {
        val layoutManager = explorerItemListView!!.layoutManager
        val position = currentPageState.scrollY

        layoutManager?.scrollToPosition(position)
    }

    // TODO by SuperMonster003 on Apr 1, 2023.
    //  ! Apparently, a more graceful way is needed.
    //  ! zh-CN: 显然, 需要一个更优雅的实现方式.
    private fun saveExplorerState() {
        val currentPath = currentPage.path
        putString(getPrefKey("explorer_current"), currentPath)

        val rootPath = if (mPageStateHistories.isEmpty()) path else mPageStateHistories.firstElement().page!!.path
        putString(getPrefKey("explorer_root"), rootPath)

        val histories = LinkedList<String>()
        for (explorerPageState in mPageStateHistories) {
            histories.add(
                (explorerPageState.page!!.path
                        + "," + explorerPageState.scrollY
                        + "," + explorerPageState.dirsCollapsed
                        + "," + explorerPageState.filesCollapsed)
            )
        }
        putLinkedList(getPrefKey("explorer_histories"), histories)
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

        val storedHistories = getLinkedList(getPrefKey("explorer_histories"))
        for (dataString in storedHistories) {
            val split = dataString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val (pagePath, scrollY, dirsCollapsed, filesCollapsed) = split
            mPageStateHistories.push(ExplorerPageState(ExplorerDirPage.createRoot(pagePath)).also { pageState ->
                pageState.scrollY = scrollY.toInt()
                pageState.dirsCollapsed = dirsCollapsed.toBoolean()
                pageState.filesCollapsed = filesCollapsed.toBoolean()
            })
        }
    }

    fun canGoUp(): Boolean {
        return !externalStoragePath.startsWith(currentPage.path)
    }

    fun goUp() {
        mPageStateHistories.push(currentPageState)
        val currentPagePath = currentPage.path
        val nextPagePath = if (Explorers.Providers.workspace().isCurrentSampleDir(currentDirectory)) {
            path
        } else {
            File(currentPagePath).parent
        }
        setCurrentPageState(ExplorerPageState(ExplorerDirPage.createRoot(nextPagePath)))
        refreshCurrentPage()
    }

    fun setDirectorySpanSize(directorySpanSize: Int) {
        mDirectorySpanSize = directorySpanSize
    }

    fun setFilter(filter: ((ExplorerItem) -> Boolean)?) {
        mFilter = filter
        reload()
    }

    fun reload() {
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
        explorer.fetchChildren(currentPage)
            .subscribeOn(Schedulers.io())
            .flatMapObservable { page: ExplorerPage? ->
                currentPageState.page = page
                Observable.fromIterable(page)
            }
            .filter { f: ExplorerItem -> mFilter == null || mFilter!!.invoke(f) }
            .collectInto(explorerItemManager.cloneConfig(), ExplorerItemManager::add)
            .observeOn(Schedulers.computation())
            .doOnSuccess(ExplorerItemManager::sort)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { newManager: ExplorerItemManager ->
                mProjectToolbar.updateVisibility()
                explorerItemManager = newManager
                notifyDataSetChanged()
                isRefreshing = false
                post { scrollToPositionSmoothly() }
            }
    }

    private fun scrollToPositionSmoothly() {
        val layoutManager = explorerItemListView!!.layoutManager
        val position = currentPageState.scrollY

        val smoothScroller: RecyclerView.SmoothScroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = position
        layoutManager?.startSmoothScroll(smoothScroller)
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

        private val globalAppContext by lazy { GlobalAppContext.get() }

        private val mName: TextView
        private val mFileDate: TextView
        private val mFileSize: TextView
        private val mOptions: View
        private val mInstall: View
        private val mRun: View
        private val mEdit: View
        private val mInfo: View
        private val mFirstChar: FirstCharView

        private lateinit var mExplorerItem: ExplorerItem

        init {
            val explorerFileBinding = ExplorerFileBinding.bind(itemView)
            val firstCharIconBinding = ExplorerFirstCharIconBinding.bind(itemView)

            mName = explorerFileBinding.name
            mFileDate = explorerFileBinding.scriptFileDate
            mFileSize = explorerFileBinding.scriptFileSize
            mFirstChar = firstCharIconBinding.firstChar

            mRun = explorerFileBinding.run
            mRun.setOnClickListener { withItemSelected { run() } }

            mEdit = explorerFileBinding.edit
            mEdit.setOnClickListener { withItemSelected { edit() } }

            mInfo = explorerFileBinding.info
            mInfo.setOnClickListener { withItemSelected { showInfo() } }

            mInstall = explorerFileBinding.install
            mInstall.setOnClickListener { withItemSelected { install() } }

            mOptions = explorerFileBinding.more
            mOptions.setOnClickListener { withItemSelected { showOptionsMenu() } }

            explorerFileBinding.item.setOnClickListener { withItemSelected { onItemClick() } }
        }

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun bind(item: Any, position: Int) {
            if (item !is ExplorerItem) return
            mExplorerItem = item

            mName.text = ExplorerViewHelper.getDisplayName(context, item)
            mFileDate.text = PFile.getFullDateString(item.lastModified())
            mFileSize.text = PFiles.getHumanReadableSize(item.size)

            setFirstChar(item)

            updateVisibility(mEdit, item.isTextEditable || item.isExternalEditable)
            updateVisibility(mRun, item.isExecutable || item.isMediaPlayable)
            updateVisibility(mInstall, item.isInstallable)
            updateVisibility(mInfo, item.isInstallable || item.isMediaMenu || item.isMediaPlayable)

            when /* listOf(mEdit, mRun, mInstall, mInfo).filter { it.isVisible }.size > 2 */ {
                listOf(mEdit, mRun, mInfo).all { it.isVisible } -> {
                    mInfo.isVisible = false
                }
                listOf(mEdit, mInstall, mInfo).all { it.isVisible } -> {
                    mInfo.isVisible = false
                }
                // listOf(mEdit, mRun, mInstall).all { it.isVisible } -> { /* Should not happen. */ }
                // listOf(mRun, mInstall, mInfo).all { it.isVisible } -> { /* Should not happen. */ }
                // listOf(mEdit, mRun, mInstall, mInfo).all { it.isVisible } -> { /* Should not happen. */ }
            }
        }

        private fun updateVisibility(view: View, visible: Boolean) {
            val visibility = if (visible) View.VISIBLE else View.GONE
            if (view.visibility != visibility) {
                view.visibility = visibility
            }
        }

        private fun setFirstChar(item: ExplorerItem) {
            mFirstChar.setIcon(ExplorerViewHelper.getIcon(item))
            when (item.type) {
                FileUtils.TYPE.JAVASCRIPT, FileUtils.TYPE.AUTO -> mFirstChar
                    .setIconTextColorNightDay()
                    .setStrokeThemeColor()
                    .setFillThemeColor()
                else -> mFirstChar
                    .setIconTextColorDayNight()
                    .setStrokeColorDayNight()
                    .setFillTransparent()
            }
        }

        private fun onItemClick() {
            onItemClickListener?.let {
                it.onItemClick(itemView, mExplorerItem)
                notifyItemOperated()
            }
        }

        private fun run() {
            when {
                mExplorerItem.isExecutable -> {
                    Scripts.run(context, ScriptFile(mExplorerItem.path))
                    notifyItemOperated()
                }
                mExplorerItem.isMediaPlayable -> {
                    mExplorerItem.play(context)
                    notifyItemOperated()
                }
            }
        }

        private fun edit() {
            when {
                mExplorerItem.isTextEditable -> {
                    Scripts.edit(context, ScriptFile(mExplorerItem.path))
                    notifyItemOperated()
                }
                mExplorerItem.isExternalEditable -> {
                    IntentUtils.editFile(globalAppContext, mExplorerItem.path, AppFileProvider.AUTHORITY)
                    notifyItemOperated()
                }
            }
        }

        private fun showInfo() {
            when {
                mExplorerItem.isInstallable -> {
                    ApkInfoDialogManager.showApkInfoDialog(context, mExplorerItem)
                    notifyItemOperated()
                }
                mExplorerItem.isMediaMenu || mExplorerItem.isMediaPlayable -> {
                    MediaInfoDialogManager.showMediaInfoDialog(context, mExplorerItem)
                    notifyItemOperated()
                }
            }
        }

        private fun install() {
            when {
                mExplorerItem.isInstallable -> {
                    mExplorerItem.install(context)
                    notifyItemOperated()
                }
                else -> {
                    IntentUtils.viewFile(globalAppContext, mExplorerItem.path)
                    notifyItemOperated()
                }
            }
        }

        @SuppressLint("CheckResult")
        private fun showOptionsMenu() {
            val popupMenu = PopupMenu(context, mOptions)
            popupMenu.inflate(R.menu.menu_script_options)
            val menu = popupMenu.menu
            if (!mExplorerItem.isExecutable) {
                menu.removeItem(R.id.create_shortcut)
                menu.removeItem(R.id.timed_task)
                menu.removeItem(R.id.run_repeatedly)
            }
            if (!mExplorerItem.canDelete()) {
                menu.removeItem(R.id.delete)
            }
            if (!mExplorerItem.canRename()) {
                menu.removeItem(R.id.rename)
            }
            if (!mExplorerItem.canBuildApk()) {
                menu.removeItem(R.id.action_build_apk)
            }
            if (!mExplorerItem.canSetAsWorkingDir()) {
                menu.removeItem(R.id.action_set_as_working_dir)
            }
            val samplePath = PFile(context.filesDir, WorkspaceFileProvider.SAMPLE_PATH).path
            if (!(mExplorerItem.path.startsWith(samplePath))) {
                menu.removeItem(R.id.reset)
            }
            popupMenu.setOnMenuItemClickListener(this@ExplorerView)
            popupMenu.show()
        }

        private fun <R> withItemSelected(func: () -> R): R {
            selectedItem = mExplorerItem
            return func.invoke()
        }
    }

    internal inner class ExplorerPageViewHolder(itemView: View) : BindableViewHolder<Any>(itemView) {
        var mName: TextView
        var mDirDate: TextView
        var mOptions: View
        var mIcon: ImageView

        private var mExplorerPage: ExplorerPage? = null

        init {
            val binding = ExplorerDirectoryBinding.bind(itemView)

            mName = binding.name
            mDirDate = binding.scriptDirDate
            mIcon = binding.icon

            mOptions = binding.more
            mOptions.setOnClickListener { withItemSelected { showOptionsMenu() } }

            binding.item.setOnClickListener { withItemSelected { onItemClick() } }
        }

        override fun bind(data: Any, position: Int) {
            if (data !is ExplorerPage) return
            mName.text = ExplorerViewHelper.getDisplayName(context, data)
            mDirDate.text = PFile.getFullDateString(data.lastModified())
            mIcon.setImageResource(ExplorerViewHelper.getIconRes(data))
            mOptions.visibility = if (data is ExplorerSamplePage) View.GONE else View.VISIBLE
            mExplorerPage = data
        }

        private fun onItemClick() {
            mExplorerPage?.let { enterDirectChildPage(it) }
        }

        private fun showOptionsMenu() {
            val popupMenu = PopupMenu(context, mOptions)
            val menu = popupMenu.menu
            popupMenu.inflate(R.menu.menu_dir_options)
            if (!mExplorerPage!!.canRename()) {
                menu.removeItem(R.id.action_rename)
            }
            if (!mExplorerPage!!.canDelete()) {
                menu.removeItem(R.id.action_delete)
            }
            if (!mExplorerPage!!.canSetAsWorkingDir()) {
                menu.removeItem(R.id.action_set_as_working_dir)
            }
            if (!mExplorerPage!!.canBuildApk()) {
                menu.removeItem(R.id.action_build_apk)
            }
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_rename -> {
                        ScriptOperations(context, this@ExplorerView, currentPage)
                            .rename(selectedItem as ExplorerFileItem?)
                            .subscribe(Observers.emptyObserver())
                    }
                    R.id.action_delete -> {
                        ScriptOperations(context, this@ExplorerView, currentPage)
                            .delete(selectedItem!!.toScriptFile())
                    }
                    R.id.action_set_as_working_dir -> {
                        ScriptOperations(context, this@ExplorerView, currentPage)
                            .setAsWorkingDir(selectedItem!!.toScriptFile())
                    }
                    R.id.action_build_apk -> {
                        BuildActivity.launch(context, selectedItem!!.path)
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
        val binding: ExplorerCategoryBinding = ExplorerCategoryBinding.bind(itemView)

        private var mIsDir = false

        init {
            setOnClickListeners()
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
                val popupMenu = PopupMenu(context, binding.sortType)
                popupMenu.inflate(R.menu.menu_sort_options)

                isDirSortMenuShowing = mIsDir

                val currentSortType = if (mIsDir) explorerItemManager.dirSortType else explorerItemManager.fileSortType
                when (currentSortType) {
                    ExplorerItemManager.SORT_TYPE_DATE -> popupMenu.menu.findItem(R.id.action_sort_by_date).setChecked(true)
                    ExplorerItemManager.SORT_TYPE_SIZE -> popupMenu.menu.findItem(R.id.action_sort_by_size).setChecked(true)
                    ExplorerItemManager.SORT_TYPE_TYPE -> popupMenu.menu.findItem(R.id.action_sort_by_type).setChecked(true)
                    else -> popupMenu.menu.findItem(R.id.action_sort_by_name).setChecked(true)
                }

                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    item.setChecked(true)
                    when (item.itemId) {
                        R.id.action_sort_by_name -> {
                            sort(ExplorerItemManager.SORT_TYPE_NAME, isDirSortMenuShowing, true)
                        }
                        R.id.action_sort_by_date -> {
                            sort(ExplorerItemManager.SORT_TYPE_DATE, isDirSortMenuShowing, false)
                        }
                        R.id.action_sort_by_size -> {
                            sort(ExplorerItemManager.SORT_TYPE_SIZE, isDirSortMenuShowing, false)
                        }
                        R.id.action_sort_by_type -> {
                            sort(ExplorerItemManager.SORT_TYPE_TYPE, isDirSortMenuShowing, true)
                        }
                        else -> null
                    } != null
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
            binding.title.setText(if (isDirCategory) R.string.text_directory else R.string.text_file)
            mIsDir = isDirCategory
            if (isDirCategory && canGoUp()) {
                binding.goUp.visibility = View.VISIBLE
            } else {
                binding.goUp.visibility = View.GONE
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
            binding.sortOrder.setImageResource(if (explorerItemManager.isFileSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
        }

        private fun setDirOrderIconWithCurrentState() {
            binding.sortOrder.setImageResource(if (explorerItemManager.isDirSortedAscending) R.drawable.ic_ascending_order else R.drawable.ic_descending_order)
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

    companion object {
        private const val LOG_TAG = "ExplorerView"

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
            remove(getPrefKey("explorer_histories"))
        }

        fun getPrefKey(key: String): String = ExplorerView::class.java.simpleName + '.' + key

    }

    private fun ExplorerProjectToolbar.updateVisibility() {
        if (isProjectRecognitionEnabled && ProjectConfig.isProject(currentPage)) {
            visibility = VISIBLE
            setProject(currentPage.toScriptFile())
        } else {
            visibility = GONE
        }
    }

}
