package org.autojs.autojs.ui.main.drawer

interface RunnableItemHelper : DrawerMenuItemHelper {

    override val isActive: Boolean
        get() = isRunning

    override fun active(): Boolean {
        if (isRunning) return true
        return launch()
    }

    val isRunning: Boolean

    fun launch(): Boolean

    fun launchIfNeeded() {
        if (!isRunning) launch()
    }

    fun close(): Boolean

    fun closeIfNeeded() {
        if (isRunning) close()
    }

    override fun toggle(): Boolean = toggle(!isRunning)

    override fun toggle(aimState: Boolean): Boolean = when (aimState) {
        true -> launch()
        else -> close()
    }

}