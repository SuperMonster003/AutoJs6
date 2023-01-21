package org.autojs.autojs.ui.main.drawer

interface IPermissionAdbItem : IPermissionItem {

    fun requestWithAdb(): Boolean

    fun revokeWithAdb(): Boolean

}
