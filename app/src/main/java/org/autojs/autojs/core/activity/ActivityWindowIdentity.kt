package org.autojs.autojs.core.activity

/** Keeps an Activity identity when a dialog/container emits a later window-state event. */
internal data class ActivityWindowIdentity(val packageName: String = "", val activityName: String = "") {
    fun observe(nextPackage: String, nextClass: String, isActivity: (String, String) -> Boolean): ActivityWindowIdentity =
        ActivityWindowIdentity(nextPackage, when {
            nextClass.isNotEmpty() && isActivity(nextPackage, nextClass) -> nextClass
            nextPackage == packageName -> activityName
            else -> ""
        })
}
