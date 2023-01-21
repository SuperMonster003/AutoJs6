package org.autojs.autojs.ui.main.drawer

interface IPermissionRootItem : IPermissionItem {

    fun requestWithRoot(): Boolean

    fun revokeWithRoot(): Boolean

}
