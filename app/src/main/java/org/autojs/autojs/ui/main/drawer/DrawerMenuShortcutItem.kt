package org.autojs.autojs.ui.main.drawer

class DrawerMenuShortcutItem : DrawerMenuItem {

    constructor(icon: Int, title: Int) : super(icon, title)

    constructor(icon: Int, title: Int, prefKey: Int) : super(icon, title, prefKey)

    fun setAction(action: Runnable): DrawerMenuShortcutItem = also { super.setAction { action.run() } }

}