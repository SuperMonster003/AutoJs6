package org.autojs.autojs.ui.explorer

import android.content.Context
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerProjectPage
import org.autojs.autojs.model.explorer.ExplorerSamplePage
import org.autojs.autojs.pio.PFiles.getNameWithoutExtension
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs6.R
import java.util.*

/**
 * Transformed by SuperMonster003 on Nov 29, 2024.
 */
object ExplorerViewHelper {

    @JvmStatic
    fun getDisplayName(context: Context, item: ExplorerItem): String {
        return when {
            item is ExplorerSamplePage && item.isRoot -> context.getString(R.string.text_sample)
            item is ExplorerPage -> item.getName()
            else -> when (item.type) {
                FileUtils.TYPE.JAVASCRIPT, FileUtils.TYPE.AUTO -> when {
                    Pref.isFileExtensionsShownForAll -> when (item) {
                        is ExplorerFileItem -> item.file.name
                        else -> item.name
                    }
                    else -> when (item) {
                        is ExplorerFileItem -> item.file.simplifiedName
                        else -> getNameWithoutExtension(item.name)
                    }
                }
                else -> when {
                    Pref.isFileExtensionsHidden -> getNameWithoutExtension(item.name)
                    else -> item.name
                }
            }
        }
    }

    @JvmStatic
    fun getIcon(item: ExplorerItem): FileUtils.TYPE.Icon {
        val type = item.type
        FileUtils.TYPE.entries.forEach { t ->
            if (t == type) {
                return t.icon
            }
        }
        // Fallback for project type match
        return when {
            item.name.equals(FileUtils.TYPE.PROJECT.typeName, ignoreCase = true) -> FileUtils.TYPE.PROJECT.icon
            else -> FileUtils.TYPE.Icon(type.typeName.substring(0, 1).uppercase(Locale.getDefault()))
        }
    }

    @JvmStatic
    fun getIconRes(page: ExplorerPage?): Int = when (page) {
        is ExplorerSamplePage -> R.drawable.ic_sample_dir
        is ExplorerProjectPage -> R.drawable.ic_project
        else -> R.drawable.ic_folder_yellow_100px
    }

}
