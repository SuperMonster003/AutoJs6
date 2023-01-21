package org.autojs.autojs.ui.main.drawer

interface PermissionItemHelper : DrawerMenuItemHelper {

    override val isActive
        get() = has()

    override fun active() = requestIfNeeded()

    fun has(): Boolean

    fun request()

    fun requestIfNeeded() {
        if (!has()) request()
    }

    fun revoke()

    fun revokeIfNeeded() {
        if (has()) revoke()
    }

    override fun toggle() = toggle(!has())

    override fun toggle(aimState: Boolean) = if (aimState) request() else revoke()

}