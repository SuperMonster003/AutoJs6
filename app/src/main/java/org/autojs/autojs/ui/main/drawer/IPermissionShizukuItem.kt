package org.autojs.autojs.ui.main.drawer

/**
 * Created by SuperMonster003 on Dec 12, 2023.
 */
interface IPermissionShizukuItem : IPermissionItem {

    fun requestWithShizuku(): Boolean

    fun revokeWithShizuku(): Boolean

}
