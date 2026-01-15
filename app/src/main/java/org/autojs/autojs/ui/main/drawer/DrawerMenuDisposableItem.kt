package org.autojs.autojs.ui.main.drawer

import com.afollestad.materialdialogs.MaterialDialog

class DrawerMenuDisposableItem : DrawerMenuToggleableItem {

    private val helper: SocketItemHelper

    constructor(
        helper: SocketItemHelper,
        icon: Int,
        title: Int,
        onTitleContainerClickListener: (MaterialDialog.Builder.(menuItem: DrawerMenuToggleableItem) -> Unit)? = null,
    ) : super(
        helper,
        icon,
        title,
        onTitleContainerClickListener = onTitleContainerClickListener,
    ) {
        this.helper = helper
    }

    constructor(
        helper: SocketItemHelper,
        icon: Int,
        title: Int,
        descriptionRes: Int,
        onTitleContainerClickListener: (MaterialDialog.Builder.(menuItem: DrawerMenuToggleableItem) -> Unit)? = null,
    ) : super(
        helper,
        icon,
        title,
        descriptionRes,
        onTitleContainerClickListener = onTitleContainerClickListener,
    ) {
        this.helper = helper
    }

    fun dispose() = helper.dispose()

}