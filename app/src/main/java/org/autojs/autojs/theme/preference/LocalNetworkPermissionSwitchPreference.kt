package org.autojs.autojs.theme.preference

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.permission.LocalNetworkPermission

class LocalNetworkPermissionSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ThemeColorPermissionSwitchPreference(context, attrs) {

    private val permission = LocalNetworkPermission(context)

    init {
        isPersistent = false
        isVisible = LocalNetworkPermission.isRequired(context)
        sync()
    }

    override fun has() = permission.has()

    override fun request(): Boolean = permission.has().also {
        if (!it) permission.request { sync() }
    }

    override fun revoke() = permission.revoke()

    override fun onClick() {
        if (has()) revoke() else request()
        sync()
    }
}
