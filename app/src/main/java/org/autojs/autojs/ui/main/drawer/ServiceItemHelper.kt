package org.autojs.autojs.ui.main.drawer

interface ServiceItemHelper : DrawerMenuItemHelper {

    override val isActive: Boolean
        get() = isRunning

    override fun active() {
        startIfNeeded()
    }

    val isRunning: Boolean

    fun start(): Boolean

    fun startIfNeeded() {
        if (!isRunning) start()
    }

    fun stop(): Boolean

    fun stopIfNeeded() {
        if (isRunning) stop()
    }

    override fun toggle() = toggle(!isRunning)

    override fun toggle(aimState: Boolean) = when(aimState) {
        true -> start()
        else -> stop()
    }

}