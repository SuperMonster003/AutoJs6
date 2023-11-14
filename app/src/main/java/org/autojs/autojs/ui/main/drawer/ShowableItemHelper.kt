package org.autojs.autojs.ui.main.drawer

interface ShowableItemHelper : DrawerMenuItemHelper {

    override val isActive: Boolean
        get() = isShowing

    override fun active(): Boolean {
        if (isShowing) return true
        return show()
    }

    val isShowing: Boolean

    fun show(): Boolean

    fun showIfNeeded() {
        if (!isShowing) show()
    }

    fun hide(): Boolean

    fun closeIfNeeded() {
        if (isShowing) hide()
    }

    override fun toggle(): Boolean = toggle(!isShowing)

    override fun toggle(aimState: Boolean): Boolean = when (aimState) {
        true -> show()
        else -> hide()
    }

}