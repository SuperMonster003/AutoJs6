package org.autojs.autojs.core.image

import android.media.Image

/**
 * Created by SuperMonster003 on Dec 15, 2023.
 */
// @Reference to Auto.js Pro 9.3.11 by SuperMonster003 on Dec 15, 2023.
class CapturedImage(image: Image) : ImageWrapper(image) {

    override fun recycle() {
        /* Doing nothing to suppress default recycle method. */
    }

    fun recycleInternal() {
        super.recycle()
    }
}
