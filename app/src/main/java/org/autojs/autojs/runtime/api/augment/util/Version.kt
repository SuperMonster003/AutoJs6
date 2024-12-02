package org.autojs.autojs.runtime.api.augment.util

import android.os.Build
import org.autojs.autojs.runtime.api.augment.Augmentable

@Suppress("unused")
object Version : Augmentable() {

    override val selfAssignmentProperties = listOf(
        "sdkInt" to Build.VERSION.SDK_INT,
    )

}
