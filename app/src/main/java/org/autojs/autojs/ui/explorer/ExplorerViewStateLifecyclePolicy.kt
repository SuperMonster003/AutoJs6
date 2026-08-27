package org.autojs.autojs.ui.explorer

internal object ExplorerViewStateLifecyclePolicy {

    fun shouldClearOnCreate(hasSavedInstanceState: Boolean): Boolean = !hasSavedInstanceState

    fun shouldClearOnDestroy(
        isActivityFinishing: Boolean,
        isChangingConfigurations: Boolean,
    ): Boolean = isActivityFinishing && !isChangingConfigurations

}
