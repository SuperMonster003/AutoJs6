package org.autojs.autojs.ui.main.drawer

interface RunnableItemHelper : DrawerMenuItemHelper {

    override val isActive: Boolean
        get() = isRunning

    override fun active() = launchIfNeeded()

    val isRunning: Boolean

    fun launch()

    fun launchIfNeeded() {
        if (!isRunning) launch()
    }

    fun close()

    fun closeIfNeeded() {
        if (isRunning) close()
    }

    override fun toggle() = toggle(!isRunning)

    override fun toggle(aimState: Boolean) = when (aimState) {
        true -> launch()
        else -> close()
    }

}