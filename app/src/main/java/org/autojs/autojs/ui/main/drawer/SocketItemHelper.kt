package org.autojs.autojs.ui.main.drawer

interface SocketItemHelper : DrawerMenuItemHelper {

    override val isActive
        get() = isConnected

    override fun active() = connectIfNeeded()

    val isConnected: Boolean

    fun connect()

    fun dispose()

    fun connectIfNeeded() {
        if (!isConnected) connect()
    }

    fun disconnect()

    fun disconnectIfNeeded() {
        if (isConnected) disconnect()
    }

    override fun toggle() = toggle(!isConnected)

    override fun toggle(aimState: Boolean) = if (aimState) connect() else disconnect()

}