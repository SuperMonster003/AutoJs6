package org.autojs.autojs.core.activity

/** Keeps an Activity identity when a dialog/container emits a later window-state event. */
internal data class ActivityWindowIdentity(val packageName: String = "", val activityName: String = "") {
    /** A fresh window establishes its package even before the first window-state event arrives. */
    fun foreground(windowPackage: String?, ownedActivity: ActivityWindowIdentity? = null): ActivityWindowIdentity {
        val current = windowPackage?.takeIf { it.isNotBlank() } ?: ownedActivity?.packageName?.takeIf { it.isNotBlank() } ?: return this
        return ActivityWindowIdentity(current, when {
            ownedActivity?.packageName == current -> ownedActivity.activityName
            packageName == current -> activityName
            else -> ""
        })
    }
    fun observe(nextPackage: String, nextClass: String, isActivity: (String, String) -> Boolean, windowKnown: Boolean = true): ActivityWindowIdentity {
        val declared = nextClass.isNotEmpty() && isActivity(nextPackage, nextClass)
        // A late Toast/container event can name a window that has already disappeared.
        // It must not replace an established foreground app after a successful Back action.
        if (!windowKnown && !declared && packageName.isNotEmpty() && nextPackage != packageName) return this
        return ActivityWindowIdentity(nextPackage, when {
            declared -> nextClass
            nextPackage == packageName -> activityName
            else -> ""
        })
    }
}
