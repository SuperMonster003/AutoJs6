package org.autojs.autojs.core.image

import android.media.Image
import org.autojs.autojs.runtime.ScriptRuntime

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 * Modified by SuperMonster003 as of May 20, 2025.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
class CapturedImage(scriptRuntime: ScriptRuntime, image: Image) : ImageWrapper(scriptRuntime, image) {

    override fun recycle() {
        /* Doing nothing to suppress default recycle method. */
    }

    fun recycleInternal() {
        super.recycle()
    }
}
