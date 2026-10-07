package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityColorLibrariesBinding

/**
 * The intelligent color libraries: built-in presets and the user's own (roadmap P1.2).
 * zh-CN: 智能颜色库: 内置预设与用户自建的智能库 (Roadmap P1.2).
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class IntelligentLibrariesActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityColorLibrariesBinding

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val operations by lazy { IntelligentLibraryOperations(this) }

    private val exchange = ColorLibraryExchange(this) { load() }

    private val adapter = IntelligentLibraryAdapter(::openLibrary, ::onLibraryLongClick)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MtActivityColorLibrariesBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            setUpToolbar(it.toolbar)
            it.toolbar.title = getString(R.string.color_library_title_intelligent_colors)
        }

        binding.colorLibrariesRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()
        }

        IntelligentColorEngine.changes.observe(this) { adapter.notifyDataSetChanged() }
        ThemeChangeNotifier.themeChanged.observe(this) { adapter.notifyDataSetChanged() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun getSubtitle(withHexSuffix: Boolean) = getCurrentColorSummary(this, withHexSuffix = withHexSuffix)

    private fun load() {
        lifecycleScope.launch {
            val presets = IntelligentPresets.all(this@IntelligentLibrariesActivity)
            val mine = withContext(Dispatchers.IO) { repository.listIntelligentLibraries() }
            adapter.submit(buildList {
                add(IntelligentLibraryRow.Header(getString(R.string.text_intelligent_presets)))
                presets.forEach { add(IntelligentLibraryRow.Library(it, isPreset = true)) }
                add(IntelligentLibraryRow.Header(getString(R.string.text_intelligent_mine)))
                when {
                    mine.isEmpty() -> add(IntelligentLibraryRow.Empty(getString(R.string.text_no_intelligent_libraries)))
                    else -> mine.forEach { add(IntelligentLibraryRow.Library(it, isPreset = false)) }
                }
            })
        }
    }

    private fun openLibrary(row: IntelligentLibraryRow.Library) {
        IntelligentLibraryActivity.launch(this, row.snapshot.libraryId)
    }

    private fun onLibraryLongClick(row: IntelligentLibraryRow.Library) {
        val snapshot = row.snapshot
        val actions = buildList {
            add(getString(R.string.text_open) to { openLibrary(row) })
            if (!row.isPreset) add(getString(R.string.text_edit) to { openEditor(snapshot.libraryId) })
            if (!row.isPreset) add(getString(R.string.text_rename) to { operations.rename(snapshot) { load() } })
            val cloneLabel = if (snapshot.libraryId == IntelligentPresets.ID_WALLPAPER) R.string.text_save_as_my_intelligent_library else R.string.text_clone
            add(getString(cloneLabel) to { operations.clone(snapshot) { IntelligentLibraryActivity.launch(this@IntelligentLibrariesActivity, it.libraryId) } })
            exportSourceOf(row)?.let { source -> add(getString(R.string.text_export) to { exchange.export(listOf(source)) }) }
            if (!row.isPreset) add(getString(R.string.text_delete) to { operations.delete(snapshot) { load() } })
        }
        ColorLibraryDialogs.showActions(this, snapshot.name, actions)
    }

    private fun exportSourceOf(row: IntelligentLibraryRow.Library): ColorLibraryExchange.Source? = when {
        row.isPreset -> ColorLibraryExchange.Source.PresetIntelligent(row.snapshot.libraryId)
        else -> ColorLibraryIds.userRowIdOf(row.snapshot.libraryId)?.let { ColorLibraryExchange.Source.User(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_intelligent_libraries, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_new_intelligent_color_library -> true.also { openEditor(null) }
        R.id.action_locate_current_theme_color -> true.also {
            when (val active = IntelligentColorEngine.activeSnapshot) {
                null -> checkAndGetTargetInfoForThemeColorLocate()
                else -> IntelligentLibraryActivity.launch(this, active.libraryId)
            }
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun openEditor(libraryId: Int?) {
        IntelligentLibraryEditorLauncher.open(this, libraryId)
    }

    companion object {

        fun launch(context: Context) {
            Intent(context, IntelligentLibrariesActivity::class.java).startSafely(context)
        }

    }

}
