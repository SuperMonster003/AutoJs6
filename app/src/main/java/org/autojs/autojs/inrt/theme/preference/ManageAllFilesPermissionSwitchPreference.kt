package org.autojs.autojs.inrt.theme.preference

import android.content.Context
import android.util.AttributeSet
import org.autojs.autojs.permission.ManageAllFilesPermission
import org.autojs.autojs.theme.preference.ThemeColorPermissionSwitchPreference

class ManageAllFilesPermissionSwitchPreference : ThemeColorPermissionSwitchPreference {

    val perm by lazy { ManageAllFilesPermission(context) }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun has() = perm.has()

    override fun request() = perm.request()

    override fun revoke() = perm.revoke()

}