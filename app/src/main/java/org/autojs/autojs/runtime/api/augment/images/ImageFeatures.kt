package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Undefined
import org.opencv.core.Rect

class ImageFeatures(
    @JvmField var javaObject: ImageFeatureMatching.FeatureMatchingDescriptor,
    @JvmField var scale: Float,
    @JvmField var region: Rect,
) {

    @JvmField
    var recycled = false

    @JvmField
    var onRecycled: ((ImageFeatures) -> Undefined) = { _: ImageFeatures -> RhinoUtils.UNDEFINED }

    fun recycle() {
        if (!recycled) {
            javaObject.release()
            onRecycled.invoke(this)
            recycled = true
        }
    }

}