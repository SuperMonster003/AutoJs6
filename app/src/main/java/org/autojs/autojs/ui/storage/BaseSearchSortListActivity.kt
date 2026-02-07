package org.autojs.autojs.ui.storage

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs.util.ViewUtils.onceGlobalLayout
import org.autojs.autojs.util.ViewUtils.setColorsByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setTitlesTextColorByThemeColorLuminance
import org.autojs.autojs6.R
import java.util.Locale

/**
 * Base activity for a list page that supports swipe refresh, search, sort, and empty hint.
 * zh-CN: 支持下拉刷新/搜索/排序/空态提示的列表页面基类.
 *
 * Notes.
 * zh-CN: 说明.
 * 1) This class inflates ViewBinding by subclass factory method.
 * zh-CN: 1) 本类通过子类提供的工厂方法创建并持有 ViewBinding.
 * 2) Subclass provides view references (toolbar/recycler/swipe/hint) via a holder.
 * zh-CN: 2) 子类通过 holder 提供 toolbar/recycler/swipe/hint 等引用.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 */
abstract class BaseSearchSortListActivity<VB : ViewBinding, T> : BaseActivity() {

    protected data class ListPageViews(
        val toolbar: ThemeColorToolbar,
        val recyclerView: RecyclerView,
        val swipeRefreshLayout: ThemeColorSwipeRefreshLayout,
        val emptyHint: TextView,
    )

    protected lateinit var binding: VB

    protected lateinit var toolbar: ThemeColorToolbar
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var swipeRefreshLayout: ThemeColorSwipeRefreshLayout
    protected lateinit var hint: TextView

    protected var searchView: SearchView? = null

    protected var allItems: List<T> = emptyList()
    protected var queryText: String = ""
    protected var internalSortMode: CommonSortMode = CommonSortMode.TIME_DESC

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = createBinding()
        setContentView(binding.root)

        val views = provideViews(binding)
        toolbar = views.toolbar
        recyclerView = views.recyclerView
        swipeRefreshLayout = views.swipeRefreshLayout
        hint = views.emptyHint

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BaseSearchSortListActivity)
            adapter = createRecyclerAdapter()
            addItemDecoration(DividerItemDecoration(context, VERTICAL))
            excludePaddingClippableViewFromBottomNavigationBar()
        }

        swipeRefreshLayout.setOnRefreshListener { load() }

        setToolbarAsBack(getToolbarTitleText())

        load()
    }

    override fun initThemeColors() {
        super.initThemeColors()
        updateToolbarColors()
        updateSearchViewColors()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(menuResId(), menu)
        setUpSearchMenuItem(menu)
        updateToolbarColors()
        updateSearchViewColors()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val mappedSortMode = sortModeFromMenuItemIdOrNull(item.itemId)
        if (mappedSortMode != null) {
            setSortMode(mappedSortMode)
            return true
        }
        if (onExtraMenuItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun setSortMode(mode: CommonSortMode) {
        internalSortMode = mode
        applyFilterAndSort()
    }

    protected open fun applyFilterAndSort() {
        val q = queryText.trim()
        val filtered = if (q.isBlank()) {
            allItems
        } else {
            val qLower = q.lowercase(Locale.getDefault())
            allItems.filter { matchesQuery(it, qLower) }
        }

        val sorted = sortItems(filtered, internalSortMode)

        updateHintVisibility(sorted)
        swipeRefreshLayout.isRefreshing = false

        submitToAdapter(sorted)
        updateStats(allItems = allItems, shownItems = sorted)
    }

    protected fun load() {
        Schedulers.io().scheduleDirect {
            runCatching {
                loadAllItemsInBackground()
            }.onSuccess { items ->
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (isFinishing || isDestroyed) return@scheduleDirect
                    allItems = items

                    // Hook after items loaded (before filter/sort/submit).
                    // zh-CN: 数据加载完成后的钩子 (在过滤/排序/提交之前调用).
                    onItemsLoaded(items)

                    applyFilterAndSort()
                }
            }.onFailure { t ->
                t.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    if (isFinishing || isDestroyed) return@scheduleDirect
                    swipeRefreshLayout.isRefreshing = false
                    DialogUtils.buildAndShowAdaptive {
                        MaterialDialog.Builder(this)
                            .title(R.string.text_prompt)
                            .content(t.message ?: t.toString())
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .cancelable(true)
                            .build()
                    }
                }
            }
        }
    }

    /**
     * Called when allItems is updated on main thread, before applyFilterAndSort.
     * zh-CN: 当 allItems 在主线程更新后, 且在调用 applyFilterAndSort 之前触发.
     */
    protected open fun onItemsLoaded(items: List<T>) = Unit

    protected fun updateHintVisibility(items: List<T>) {
        hint.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    protected fun setUpSearchMenuItem(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search) ?: return

        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true.also {
                toolbar.onceGlobalLayout { updateToolbarColors() }
            }

            override fun onMenuItemActionCollapse(item: MenuItem) = true
        })

        val sv = searchItem.actionView as? SearchView
        searchView = sv
        sv?.queryHint = getString(R.string.text_search)

        sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                queryText = query.orEmpty()
                applyFilterAndSort()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                queryText = newText.orEmpty()
                applyFilterAndSort()
                return true
            }
        })
    }

    protected fun updateToolbarColors() {
        toolbar.setMenuIconsColorByThemeColorLuminance(this)
        toolbar.setNavigationIconColorByThemeColorLuminance(this)
        toolbar.setTitlesTextColorByThemeColorLuminance(this)
    }

    protected fun updateSearchViewColors() {
        searchView?.setColorsByThemeColorLuminance(this)
    }

    /**
     * Create view binding instance.
     * zh-CN: 创建 ViewBinding 实例.
     */
    protected abstract fun createBinding(): VB

    /**
     * Provide view references for list page.
     * zh-CN: 提供列表页所需的 view 引用.
     */
    protected abstract fun provideViews(binding: VB): ListPageViews

    /**
     * Title text for toolbar back mode.
     * zh-CN: 返回模式下 toolbar 的标题文本.
     */
    protected abstract fun getToolbarTitleText(): String

    /**
     * Menu resource id.
     * zh-CN: 菜单资源 ID.
     */
    @MenuRes
    protected abstract fun menuResId(): Int

    /**
     * Create RecyclerView adapter.
     * zh-CN: 创建 RecyclerView adapter.
     */
    protected abstract fun createRecyclerAdapter(): RecyclerView.Adapter<*>

    /**
     * Submit items to adapter.
     * zh-CN: 提交列表数据到 adapter.
     */
    protected abstract fun submitToAdapter(items: List<T>)

    /**
     * Load all items on background thread.
     * zh-CN: 在后台线程加载全量数据.
     */
    protected abstract fun loadAllItemsInBackground(): List<T>

    /**
     * Query predicate.
     * zh-CN: 查询匹配规则.
     */
    protected abstract fun matchesQuery(item: T, qLower: String): Boolean

    /**
     * Sort items.
     * zh-CN: 对条目排序.
     */
    protected abstract fun sortItems(items: List<T>, sortMode: CommonSortMode): List<T>

    /**
     * Update toolbar subtitle stats.
     * zh-CN: 更新 toolbar 子标题统计信息.
     */
    protected abstract fun updateStats(allItems: List<T>, shownItems: List<T>)

    /**
     * Map menu item id to sort mode (or null if not a sort item).
     * zh-CN: 将菜单项 ID 映射为排序模式 (如果不是排序项则返回 null).
     */
    protected open fun sortModeFromMenuItemIdOrNull(itemId: Int): CommonSortMode? = when (itemId) {
        R.id.action_sort_time_desc -> CommonSortMode.TIME_DESC
        R.id.action_sort_time_asc -> CommonSortMode.TIME_ASC
        R.id.action_sort_size_desc -> CommonSortMode.SIZE_DESC
        R.id.action_sort_size_asc -> CommonSortMode.SIZE_ASC
        R.id.action_sort_name_asc -> CommonSortMode.NAME_ASC
        R.id.action_sort_path_asc -> CommonSortMode.PATH_ASC
        else -> null
    }

    /**
     * Handle extra menu items (non-search/non-sort).
     * zh-CN: 处理额外菜单项 (非搜索/非排序).
     */
    protected open fun onExtraMenuItemSelected(item: MenuItem): Boolean = false
}
