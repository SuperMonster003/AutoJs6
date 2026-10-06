package org.autojs.autojs.theme.app

import android.content.Context
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Opens the intelligent library editor; [libraryId] null creates a new library.
 * zh-CN: 打开智能库编辑器; [libraryId] 为 null 时新建智能库.
 */
object IntelligentLibraryEditorLauncher {

    fun open(context: Context, libraryId: Int?) {
        ViewUtils.showToast(context, R.string.text_under_development)
    }

}
