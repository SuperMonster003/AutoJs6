package org.autojs.autojs.runtime.api.augment.util

import android.os.Build
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey

@Suppress("unused")
object Version : Augmentable() {

    override val keys = listOf(AugmentableKey("version", withDollarPrefix = false))

    override val selfAssignmentProperties = listOf(
        "sdkInt" to Build.VERSION.SDK_INT,
    )

}
