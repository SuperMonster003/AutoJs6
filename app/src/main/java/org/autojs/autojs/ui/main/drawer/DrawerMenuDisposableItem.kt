package org.autojs.autojs.ui.main.drawer

class DrawerMenuDisposableItem(private val helper: SocketItemHelper, icon: Int, title: Int) : DrawerMenuToggleableItem(helper, icon, title) {

    fun dispose() = helper.dispose()

}