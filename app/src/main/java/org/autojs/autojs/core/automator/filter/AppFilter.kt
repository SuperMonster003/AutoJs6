package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.util.App

/**
 * Created by SuperMonster003 on Nov 19, 2022.
 */
class AppFilter(private val arbitraryName: String) : Filter {

    private var mApp: App? = null
    private var mName = App.values().find { app ->
        app.alias.equals(arbitraryName, true)
        || app.getAppName().equals(arbitraryName, true)
        || app.getAppNameZh().equals(arbitraryName, true)
        || app.getAppNameEn().equals(arbitraryName, true)
    }?.packageName ?: arbitraryName

    constructor(app: App) : this(app.packageName) {
        mApp = app
    }

    override fun filter(node: UiObject) = node.packageName?.toString()?.let { it == mName } ?: false

    override fun toString() = "currentApp(${mApp?.name ?: mName})"

}
