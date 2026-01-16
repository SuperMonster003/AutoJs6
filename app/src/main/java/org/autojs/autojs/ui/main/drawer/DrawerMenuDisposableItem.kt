package org.autojs.autojs.ui.main.drawer

import com.afollestad.materialdialogs.MaterialDialog

class DrawerMenuDisposableItem(
    private val helper: SocketItemHelper,
    icon: Int,
    title: Int,
    descriptionRes: Int,
    onTitleContainerClickListener: (MaterialDialog.Builder.(helper: SocketItemHelper) -> Any?)? = null,
) : DrawerMenuToggleableItem(
    helper,
    icon,
    title,
    descriptionRes,
    onTitleContainerClickListener = listener@{
        val listener = onTitleContainerClickListener ?: return@listener false
        when (val result = listener(this, helper)) {
            is Boolean -> result
            is Unit -> false
            else -> throw IllegalArgumentException("onTitleContainerClickListener must return Boolean or Unit")
        }
    },
) {
    fun dispose() = helper.dispose()
}