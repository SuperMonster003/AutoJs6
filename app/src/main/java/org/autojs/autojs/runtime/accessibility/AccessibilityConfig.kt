package org.autojs.autojs.runtime.accessibility

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.DeveloperUtils

/**
 * Created by Stardust on Apr 29, 2017.
 * Modified by SuperMonster003 as of Apr 2, 2024.
 * Transformed by SuperMonster003 on Apr 2, 2024.
 */
class AccessibilityConfig {

    private val mBlacklist = ArrayList<String>()
    private var mSealed = false

    init {
        if (isUnintendedGuardEnabled()) {
            addBlacklist(DeveloperUtils.selfPackage())
        }
    }

    fun isInBlacklist(packageName: String) = mBlacklist.contains(packageName)

    fun addBlacklist(packageName: String) {
        check(!mSealed) { "Sealed" }
        mBlacklist.add(packageName)
    }

    fun seal() {
        mSealed = true
    }

    companion object {

        @JvmStatic
        fun isUnintendedGuardEnabled() = Pref.isGuardModeEnabled

    }

}
