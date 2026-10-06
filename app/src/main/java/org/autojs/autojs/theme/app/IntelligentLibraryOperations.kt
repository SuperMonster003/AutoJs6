package org.autojs.autojs.theme.app

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.autojs.autojs6.R

/**
 * Clone, rename and delete for intelligent libraries, shared by the list and the detail page (roadmap P1.2).
 * zh-CN: 智能库的克隆, 重命名与删除, 供列表页与详情页共用 (Roadmap P1.2).
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class IntelligentLibraryOperations(private val activity: AppCompatActivity) {

    private val repository by lazy { ColorLibraryRepository.get(activity) }

    /**
     * Copies a preset or user library into a new user library; groups get new ids.
     * zh-CN: 将预设库或用户库复制为新的用户库; 组使用新的 id.
     */
    fun clone(source: IntelligentSnapshot, onCloned: (IntelligentSnapshot) -> Unit) {
        activity.lifecycleScope.launch {
            val name = activity.getString(R.string.text_color_library_copy_name, source.name)
            val groups = source.groups.mapIndexed { index, group -> group.copy(id = -(index + 1L), createdTime = 0L, modifiedTime = 0L) }
            val cloned = withContext(Dispatchers.IO) {
                repository.saveIntelligent(null, name, source.config, groups, ColorLibrary.Kind.CLONED, origin = source.name)
            }
            onCloned(cloned)
        }
    }

    fun rename(snapshot: IntelligentSnapshot, onRenamed: (IntelligentSnapshot) -> Unit) {
        val rowId = ColorLibraryIds.userRowIdOf(snapshot.libraryId) ?: return
        ColorLibraryDialogs.showNameInput(activity, R.string.text_rename_color_library, snapshot.name) { name ->
            name ?: return@showNameInput
            activity.lifecycleScope.launch {
                val renamed = withContext(Dispatchers.IO) {
                    repository.renameLibrary(rowId, name)
                    repository.loadIntelligent(rowId)
                } ?: return@launch
                IntelligentColorEngine.onLibraryChanged(renamed)
                onRenamed(renamed)
            }
        }
    }

    fun delete(snapshot: IntelligentSnapshot, onDeleted: () -> Unit) {
        val rowId = ColorLibraryIds.userRowIdOf(snapshot.libraryId) ?: return
        ColorLibraryDialogs.showConfirm(activity, activity.getString(R.string.text_delete_color_library_confirm, snapshot.name)) {
            activity.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    repository.findLibrary(rowId)?.let { repository.deleteLibrary(it) }
                }
                if (IntelligentColorEngine.activeSnapshot?.libraryId == snapshot.libraryId) {
                    IntelligentColorEngine.deactivate()
                    ColorSelectBaseActivity.fallBackToPaletteSelection(activity)
                }
                onDeleted()
            }
        }
    }

}
