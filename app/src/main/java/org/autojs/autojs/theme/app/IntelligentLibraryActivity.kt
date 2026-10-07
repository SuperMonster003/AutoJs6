package org.autojs.autojs.theme.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.ThemeChangeNotifier
import org.autojs.autojs.theme.intelligent.DateOverrides
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntelligentPresets
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.intelligent.StateSource
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtActivityIntelligentLibraryBinding
import java.time.LocalDate

/**
 * One intelligent library: its groups and, when active, its running state (roadmap P1.2).
 * Picking a group activates the library starting from that group.
 * zh-CN: 单个智能库: 其中的组, 以及激活时的运行状态 (Roadmap P1.2). 选择一组即从该组开始激活此库.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class IntelligentLibraryActivity : ColorSelectBaseActivity() {

    private lateinit var binding: MtActivityIntelligentLibraryBinding

    private val repository by lazy { ColorLibraryRepository.get(this) }

    private val operations by lazy { IntelligentLibraryOperations(this) }

    private val adapter = IntelligentGroupAdapter(::onGroupClick)

    private val libraryId by lazy { intent.getIntExtra(EXTRA_LIBRARY_ID, ColorLibraryIds.SELECT_NONE) }

    private val isPreset: Boolean
        get() = ColorLibraryIds.isPresetIntelligent(libraryId)

    private val isActive: Boolean
        get() = IntelligentColorEngine.activeSnapshot?.libraryId == libraryId

    private var snapshot: IntelligentSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MtActivityIntelligentLibraryBinding.inflate(layoutInflater).let {
            binding = it
            setContentView(it.root)
            setUpAppBar(it.appBar, it.appBarContainer)
            setUpToolbar(it.toolbar)
        }

        binding.groupsRecyclerView.let {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
            it.addItemDecoration(DividerItemDecoration(this, VERTICAL))
            it.excludePaddingClippableViewFromBottomNavigationBar()
        }

        IntelligentColorEngine.changes.observe(this) { refresh() }
        ThemeChangeNotifier.themeChanged.observe(this) { refresh() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun getSubtitle(withHexSuffix: Boolean): String? = when {
        isActive -> getCurrentColorSummary(this, withIdentifierPrefix = false, withHexSuffix = withHexSuffix)
        else -> null
    }

    private fun load() {
        lifecycleScope.launch {
            val loaded = when {
                isPreset -> IntelligentPresets.find(this@IntelligentLibraryActivity, libraryId)
                else -> ColorLibraryIds.userRowIdOf(libraryId)?.let { rowId -> withContext(Dispatchers.IO) { repository.loadIntelligent(rowId) } }
            }
            if (loaded == null) {
                finish()
                return@launch
            }
            snapshot = loaded
            binding.toolbar.title = loaded.name
            refresh()
        }
    }

    private fun refresh() {
        val current = snapshot ?: return
        adapter.submit(current, IntelligentColorEngine.shownGroupId.takeIf { isActive })
        binding.emptyView.isVisible = current.groups.isEmpty()
        binding.infoView.text = buildInfo(current)
        updateSubtitle(binding.toolbar)
    }

    private fun buildInfo(current: IntelligentSnapshot): String {
        val lines = mutableListOf(IntelligentSummaryFormatter.summary(this, current.config, current.groups))
        IntelligentSummaryFormatter.presetNote(this, current)?.let { lines += it }
        if (isActive) {
            val runtime = IntelligentColorEngine.runtimeState
            lines += getString(R.string.text_in_use)
            if (current.config.stateSource !is StateSource.None) {
                val state = runtime.lastStateKey ?: current.config.stateKeys.first()
                lines += getString(R.string.text_current_state, IntelligentSummaryFormatter.stateLabel(this, current.config.stateSource, state))
            }
            if (runtime.overrideGroupId != null) {
                DateOverrides.firstMatch(current.config.overrides, LocalDate.now())?.let { override ->
                    lines += getString(R.string.text_current_override, IntelligentSummaryFormatter.overrideLabel(this, override.condition))
                }
            }
            current.group(IntelligentColorEngine.shownGroupId)?.let { group ->
                lines += getString(R.string.text_current_group, IntelligentSummaryFormatter.groupName(current.config, group))
            }
            // Rotation pauses while a date override is shown (D4). zh-CN: 日期覆盖层生效期间暂停换组 (D4).
            runtime.earliestDueAt?.takeIf { runtime.overrideGroupId == null }?.let { dueAt ->
                val flags = DateUtils.FORMAT_SHOW_TIME or (if (DateUtils.isToday(dueAt)) 0 else DateUtils.FORMAT_SHOW_DATE)
                lines += getString(R.string.text_next_rotation_at, DateUtils.formatDateTime(this, dueAt, flags))
            }
        }
        return lines.joinToString("\n")
    }

    private fun onGroupClick(group: IntelligentGroup) {
        val current = snapshot ?: return
        if (!group.enabled || !group.hasColor) {
            ViewUtils.showToast(this, R.string.text_group_unavailable)
            return
        }
        activateIntelligentLibrary(current, group.id)
    }

    private fun locateShownGroup() {
        if (!isActive) {
            checkAndGetTargetInfoForThemeColorLocate()
            return
        }
        val position = adapter.groups.indexOfFirst { it.id == IntelligentColorEngine.shownGroupId }
        if (position >= 0) {
            (binding.groupsRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_intelligent_library, menu)
        listOf(R.id.action_edit_intelligent_library, R.id.action_rename_intelligent_library, R.id.action_delete_intelligent_library).forEach {
            menu.findItem(it)?.isVisible = !isPreset
        }
        // Cloning the wallpaper preset keeps the colors of the current wallpaper (roadmap P4.2).
        // zh-CN: 克隆跟随壁纸预设即保存当前壁纸的颜色 (Roadmap P4.2).
        if (libraryId == IntelligentPresets.ID_WALLPAPER) menu.findItem(R.id.action_clone_intelligent_library)?.setTitle(R.string.text_save_as_my_intelligent_library)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val current = snapshot ?: return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.action_edit_intelligent_library -> true.also { IntelligentLibraryEditorLauncher.open(this, libraryId) }
            R.id.action_locate_current_theme_color -> true.also { locateShownGroup() }
            R.id.action_clone_intelligent_library -> true.also { operations.clone(current) { launch(this, it.libraryId) } }
            R.id.action_rename_intelligent_library -> true.also { operations.rename(current) { load() } }
            R.id.action_delete_intelligent_library -> true.also { operations.delete(current) { finish() } }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val EXTRA_LIBRARY_ID = "INTELLIGENT_LIBRARY_ID"

        fun launch(context: Context, libraryId: Int) {
            Intent(context, IntelligentLibraryActivity::class.java)
                .putExtra(EXTRA_LIBRARY_ID, libraryId)
                .startSafely(context)
        }

    }

}
