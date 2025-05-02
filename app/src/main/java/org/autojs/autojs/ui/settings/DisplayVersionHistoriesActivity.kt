package org.autojs.autojs.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.Language.Companion.getPrefLanguageOrNull
import org.autojs.autojs.theme.widget.ThemeColorToolbar
import org.autojs.autojs.ui.BaseActivity
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
            ViewUtils.excludePaddingClippableViewFromNavigationBar(it)
        }

        lifecycleScope.launch {
            val language = getPrefLanguageOrNull() ?: Language.EN
            val languageTag = language.getLocalCompatibleLanguageTag()
            val urlSuffix = "app/src/main/assets-app/doc/CHANGELOG-$languageTag.md"
            val urlRaw = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6/master/$urlSuffix"
            val urlBlob = "https://github.com/SuperMonster003/AutoJs6/blob/master/$urlSuffix"
            repo.loadVersionHistoriesFlow(
                activity = this@DisplayVersionHistoriesActivity,
                urlRaw = urlRaw,
                urlBlob = urlBlob
            ).collectLatest { item ->
                mAdapter.add(item)
                hideLoadingTextContainerIfNeeded()
            }
        }

        setToolbarAsBack(R.string.text_version_histories)
    }

    private suspend fun hideLoadingTextContainerIfNeeded() {
        if (!binding.loadingContainer.isVisible) return
        withContext(Dispatchers.Main) {
            binding.loadingContainer.isVisible = false
        }
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
        }
        return super.onOptionsItemSelected(item)
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