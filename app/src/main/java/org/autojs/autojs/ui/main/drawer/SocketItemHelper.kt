package org.autojs.autojs.ui.main.drawer

interface SocketItemHelper : DrawerMenuItemHelper {

    override val isActive
        get() = isConnected

    override fun active(): Boolean {
        if (isConnected) return true
        return connect()
    }

    val isConnected: Boolean

    fun connect(): Boolean

    fun dispose()

    fun connectIfNeeded() {
        if (!isConnected) connect()
    }

    fun disconnect(): Boolean

    fun disconnectIfNeeded() {
        if (isConnected) disconnect()
    }

    override fun toggle() = toggle(!isConnected)

    override fun toggle(aimState: Boolean): Boolean = if (aimState) connect() else disconnect()

}