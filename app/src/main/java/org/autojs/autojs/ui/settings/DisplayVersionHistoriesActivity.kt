@file:Suppress("EnumValuesSoftDeprecate")

package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.util.Linkify
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.Language.Companion.getPrefLanguageOrNull
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.Category
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.DEFAULT_FILTER
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.DEFAULT_VERSION_NAME
import org.autojs.autojs.util.ProcessLogger
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.setMenuIconsColorByThemeColorLuminance
import org.autojs.autojs.util.ViewUtils.setNavigationIconColorByThemeColorLuminance
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivityDisplayVersionHistoriesBinding

class DisplayVersionHistoriesActivity : BaseActivity() {

    private val repo by lazy { VersionHistoryRepository() }

    private lateinit var binding: ActivityDisplayVersionHistoriesBinding

    private lateinit var mAdapter: VersionHistoryAdapter
    private lateinit var mToolbar: ThemeColorToolbar

    private var mAllDataHandled = false
    private val mSelectedCategories = MutableStateFlow<Set<Category>>(DEFAULT_FILTER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityDisplayVersionHistoriesBinding.inflate(layoutInflater).also {
            binding = it
            mToolbar = binding.toolbar
            setContentView(it.root)
        }

        val adapter = VersionHistoryAdapter(this, Markwon.create(this)).also {
            mAdapter = it
        }

        binding.recycler.also {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
            it.itemAnimator = NoFadeItemAnimator().apply {
                addDuration = 200
                supportsChangeAnimations = false
            }
            ViewUtils.excludePaddingClippableViewFromBottomNavigationBar(it)
        }

        lifecycleScope.launch {
            val language = getPrefLanguageOrNull() ?: Language.EN
            val languageTag = language.getLocalCompatibleLanguageTag()
            val urlSuffix = "app/src/main/assets-app/doc/CHANGELOG-$languageTag.md"
            val urlRaw = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6/master/$urlSuffix"
            val urlBlob = "https://github.com/SuperMonster003/AutoJs6/blob/master/$urlSuffix"

            val localItems = withContext(Dispatchers.IO) {
                VersionHistoryRepository.readBestLocalSample(
                    context = this@DisplayVersionHistoriesActivity,
                    languageTag = languageTag,
                )
            }
            if (localItems.isNotEmpty()) {
                ProcessLogger.i("${getString(R.string.logger_ver_history_load_local_data)} (${resources.getQuantityString(R.plurals.text_items_total_sum, localItems.size, localItems.size).lowercase(Language.getPrefLanguage().locale)})")
                hideLoadingTextContainerIfNeeded()
                mAdapter.submit(localItems.toMutableList())
            } else {
                ProcessLogger.i(getString(R.string.logger_ver_history_local_data_empty))
            }

            val localItemLatestVersion = localItems.firstOrNull()?.version ?: DEFAULT_VERSION_NAME
            var onlineItemLatestVersion: String? = null
            var shouldContinueCollect = true

            repo.loadVersionHistoriesFlow(
                context = this@DisplayVersionHistoriesActivity,
                languageTag = languageTag,
                urlRaw = urlRaw,
                urlBlob = urlBlob
            ).collectLatest { onlineItem ->
                hideLoadingTextContainerIfNeeded()
                if (!shouldContinueCollect) {
                    return@collectLatest
                }
                if (onlineItemLatestVersion == null) {
                    onlineItemLatestVersion = onlineItem.version
                    if (VersionHistoryRepository.compareVersion(localItemLatestVersion, onlineItemLatestVersion) > 0) {
                        shouldContinueCollect = false
                        ProcessLogger.i(getString(R.string.logger_ver_history_local_data_newer_stop_online))
                        return@collectLatest
                    }
                }
                mAdapter.addOrUpdate(onlineItem)
            }

            ProcessLogger.i(getString(R.string.logger_ver_history_data_loaded))
            mAllDataHandled = true
        }

        setToolbarAsBack(R.string.text_version_histories)
    }

    private suspend fun hideLoadingTextContainerIfNeeded() {
        if (!binding.loadingContainer.isVisible) return
        withContext(Dispatchers.Main) {
            binding.loadingContainer.isVisible = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLogger.clear()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_display_version_histories, menu)
        setUpToolbarColors()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_expand_all -> mAdapter.expandAll()
            R.id.action_collapse_all -> mAdapter.collapseAll()
            R.id.action_filter_category -> showCategoryFilterDialog()
            R.id.action_show_logs -> showProcessLogs()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showCategoryFilterDialog() {
        if (!mAllDataHandled) {
            val dialog = MaterialDialog.Builder(this)
                .title(R.string.text_please_wait)
                .content(R.string.text_waiting_for_all_data_processing_to_complete)
                .positiveText(R.string.dialog_button_cancel)
                .positiveColorRes(R.color.dialog_button_default)
                .show()

            lifecycleScope.launch {
                while (!mAllDataHandled && dialog.isShowing) {
                    delay(100)
                }
                dialog.dismiss()
                if (mAllDataHandled && !isFinishing) {
                    showCategoryFilterDialog()
                }
            }
            return
        }
        val categories = Category.values()
        val totalItems = categories.map { getString(it.labelRes) }
        val checkedItemsIndices = categories.indices.filter { categories[it] in mSelectedCategories.value }.toTypedArray()
        MaterialDialog.Builder(this)
            .title(R.string.text_category_filter)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { dialog, _ -> dialog.dismiss() }
            .negativeText(R.string.dialog_button_cancel)
            .neutralColorRes(R.color.dialog_button_default)
            .onNegative { dialog, _ -> dialog.dismiss() }
            .neutralText(R.string.dialog_button_use_default)
            .neutralColorRes(R.color.dialog_button_reset)
            .choiceWidgetColor(ThemeColorHelper.getThemeColorStateList(this))
            .onNeutral { dialog, _ ->
                dialog.setSelectedIndices(categories.indices.filter { categories[it] in DEFAULT_FILTER }.toTypedArray())
            }
            .autoDismiss(false)
            .items(totalItems)
            .itemsCallbackMultiChoice(checkedItemsIndices) { dialog, which, text ->
                lifecycleScope.launch {
                    mSelectedCategories.value = which.map { categories[it] }.toSet()
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        mSelectedCategories.collect { newFilter ->
                            mAdapter.updateFilter(newFilter)
                        }
                    }
                }
                true
            }
            .show()
    }

    private fun showProcessLogs() {
        val dialog = MaterialDialog.Builder(this)
            .title(R.string.text_process_log)
            .content(ProcessLogger.dump())
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .show()

        val tv = dialog.contentView?.apply {
            autoLinkMask = Linkify.WEB_URLS
            text = text
        } ?: return

        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ProcessLogger.flow.collect { newLine ->
                    if (!dialog.isShowing) {
                        this@repeatOnLifecycle.cancel()
                        return@collect
                    }
                    tv.append(newLine)

                    (tv.parent as? ScrollView)?.post {
                        (tv.parent as ScrollView).fullScroll(View.FOCUS_DOWN)
                    }
                }
            }
        }
        dialog.setOnDismissListener { job.cancel() }
    }

    private fun setUpToolbarColors() {
        mToolbar.setMenuIconsColorByThemeColorLuminance(this)
        mToolbar.setNavigationIconColorByThemeColorLuminance(this)
    }

    companion object {

        @JvmStatic
        fun launch(context: Context) {
            Intent(context, DisplayVersionHistoriesActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }.let { context.startActivity(it) }
        }

        class NoFadeItemAnimator : DefaultItemAnimator() {
            override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
                // 透明度始终为 1
                holder.itemView.alpha = 1f
                // 立即回调, 结束动画
                dispatchAddFinished(holder)
                // 无需后续动画
                return false
            }
        }

    }

}