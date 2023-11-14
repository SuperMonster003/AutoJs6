package org.autojs.autojs.ui.main.drawer

interface PermissionItemHelper : DrawerMenuItemHelper {

    override val isActive
        get() = has()

    override fun active(): Boolean {
        if (has()) return true
        return request()
    }

    fun has(): Boolean

    fun request(): Boolean

    fun requestIfNeeded() {
        if (!has()) request()
    }

    fun revoke(): Boolean

    fun revokeIfNeeded() {
        if (has()) revoke()
    }

    override fun toggle() = toggle(!has())

    override fun toggle(aimState: Boolean): Boolean = if (aimState) request() else revoke()

}