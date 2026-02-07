package org.autojs.autojs.ui.storage

import android.content.Context
import android.content.Intent
import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.core.pref.PrefRx
import org.autojs.autojs.storage.history.HistoryDatabase
import org.autojs.autojs.storage.history.HistoryPrefs
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashRepository
import org.autojs.autojs.storage.history.TrashRestoreController
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityTrashBinding
import java.io.File
import java.util.Locale

/**
 * Trash page.
 * zh-CN: 回收站页面.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 5, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
class TrashActivity : BaseSearchSortListActivity<ActivityTrashBinding, TrashEntities.TrashItem>() {

    private val adapter = TrashAdapter(
        onClick = { item ->
            val optionMenuItemRemove = MaterialDialog.OptionMenuItemSpec(getString(R.string.text_remove_from_trash)) { d ->
                confirmAndRemoveFromTrash(item, d)
            }
            TrashRestoreController(this).startRestoreFlow(item, listOf(optionMenuItemRemove)) {
                load()
            }
        },
        onLongClick = { item ->
            confirmAndRemoveFromTrash(item)
        },
    )

    /**
     * Disposables for header observing.
     * zh-CN: header 监听订阅管理器.
     */
    private val headerDisposables: CompositeDisposable = CompositeDisposable()

    /**
     * Latest computed subtitle from observers.
     * zh-CN: 来自监听器的最新 subtitle 文本.
     */
    private var observedSubtitle: String? = null

    override fun onStart() {
        super.onStart()
        startObserveHeaderSubtitleIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        // Avoid leaking Activity via subscriptions.
        // zh-CN: 避免订阅持有 Activity 导致泄露.
        headerDisposables.clear()
    }

    private fun startObserveHeaderSubtitleIfNeeded() {
        headerDisposables.clear()

        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()

        val limitBytesFlow = PrefRx.observeLong(
            keyRes = R.string.key_trash_max_total_bytes,
            defaultValue = HistoryPrefs.DEFAULT_TRASH_MAX_TOTAL_BYTES,
        )

        val disposable = Observable
            .combineLatest(
                dao.observeTrashStats().toObservable(),
                limitBytesFlow.toObservable(),
            ) { stats, limitBytes ->
                StorageUsageSummaryFormatter.formatHeader(
                    context = this,
                    count = stats.count,
                    totalBytes = (stats.totalBytes ?: 0L),
                    limitBytes = limitBytes,
                )
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { subtitle ->
                observedSubtitle = subtitle
                toolbar.subtitle = subtitle
            }

        headerDisposables.add(disposable)
    }

    override fun createBinding(): ActivityTrashBinding {
        return ActivityTrashBinding.inflate(layoutInflater)
    }

    override fun provideViews(binding: ActivityTrashBinding): ListPageViews {
        return ListPageViews(
            toolbar = binding.toolbar,
            recyclerView = binding.recyclerView,
            swipeRefreshLayout = binding.swipeRefreshLayout,
            emptyHint = binding.emptyHint,
        )
    }

    override fun getToolbarTitleText(): String {
        return getString(R.string.text_trash)
    }

    override fun menuResId(): Int {
        return R.menu.menu_trash
    }

    override fun createRecyclerAdapter(): RecyclerView.Adapter<*> {
        return adapter
    }

    override fun submitToAdapter(items: List<TrashEntities.TrashItem>) {
        adapter.submit(items)
    }

    override fun loadAllItemsInBackground(): List<TrashEntities.TrashItem> {
        val dao = HistoryDatabase.getInstance(applicationContext).historyDao()
        return dao.listTrashItemsDesc()
    }

    override fun matchesQuery(item: TrashEntities.TrashItem, qLower: String): Boolean {
        val path = item.originalPath
        val name = File(path).name
        val locale = Locale.getDefault()
        return path.lowercase(locale).contains(qLower) || name.lowercase(locale).contains(qLower)
    }

    override fun sortItems(
        items: List<TrashEntities.TrashItem>,
        sortMode: CommonSortMode,
    ): List<TrashEntities.TrashItem> {
        val locale = Locale.getDefault()
        return when (sortMode) {
            CommonSortMode.TIME_DESC -> items.sortedByDescending { it.trashedAt }
            CommonSortMode.TIME_ASC -> items.sortedBy { it.trashedAt }

            CommonSortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            CommonSortMode.SIZE_ASC -> items.sortedBy { it.sizeBytes }

            CommonSortMode.NAME_ASC -> items.sortedBy { File(it.originalPath).name.lowercase(locale) }
            CommonSortMode.PATH_ASC -> items.sortedBy { it.originalPath.lowercase(locale) }
        }
    }

    override fun updateStats(
        allItems: List<TrashEntities.TrashItem>,
        shownItems: List<TrashEntities.TrashItem>,
    ) {
        // Header subtitle is driven by Room observe + Pref observe.
        // zh-CN: header subtitle 由 Room 监听 + Pref 监听驱动.
        observedSubtitle?.let { toolbar.subtitle = it }
    }

    override fun onExtraMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_trash -> true.also { confirmAndClearTrash() }
            else -> false
        }
    }

    private fun confirmAndRemoveFromTrash(item: TrashEntities.TrashItem, parentDialog: MaterialDialog? = null) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_remove_from_trash_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ ->
                    parentDialog?.dismiss()
                    removeFromTrash(item)
                }
                .cancelable(true)
                .build()
        }
    }

    private fun removeFromTrash(item: TrashEntities.TrashItem) {
        Schedulers.io().scheduleDirect {
            runCatching {
                TrashRepository(applicationContext).deleteTrashItem(item)
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, getString(R.string.text_already_deleted))
                    load()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showToast(this, it.message, true)
                }
            }
        }
    }

    private fun confirmAndClearTrash() {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(this)
                .title(R.string.text_prompt)
                .content(R.string.text_clear_trash_confirm)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { _, _ -> clearTrash() }
                .cancelable(true)
                .build()
        }
    }

    private fun clearTrash() {
        Schedulers.io().scheduleDirect {
            runCatching {
                TrashRepository(applicationContext).clearTrash()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showSnack(recyclerView, getString(R.string.text_done), true)
                    load()
                }
            }.onFailure {
                it.printStackTrace()
                AndroidSchedulers.mainThread().scheduleDirect {
                    ViewUtils.showSnack(recyclerView, it.message, true)
                }
            }
        }
    }

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(
                Intent(context, TrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
