package org.autojs.autojs.inrt.theme.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.Toast
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.inrt.InrtStorageAccess
import org.autojs.autojs.theme.preference.ThemeColorPermissionSwitchPreference

class ManageAllFilesPermissionSwitchPreference : ThemeColorPermissionSwitchPreference {

    val perm by lazy { AllFilesAccessPermission(context) }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun has() = perm.has()

    override fun onClick() {
        // A settings launch is not a grant. Keep the switch tied to the actual state.
        if (has()) revoke() else if (InrtStorageAccess.isDeclared(context)) request() else {
            Toast.makeText(context, InrtStorageAccess.explanation(context), Toast.LENGTH_LONG).show()
        }
        sync()
    }

    override fun request() = perm.request()

    override fun revoke() = perm.revoke()

}
