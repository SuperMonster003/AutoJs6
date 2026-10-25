package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.Shootable
import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Undefined
import org.opencv.core.Rect

/**
 * Features (key points and descriptors) of one image, as returned by `images.detectAndComputeFeatures()`.
 * zh-CN: 单张图片的特征 (关键点与描述子), 由 `images.detectAndComputeFeatures()` 返回.
 */
class ImageFeatures(
    @JvmField var javaObject: ImageFeatureMatching.FeatureMatchingDescriptor,
    /** Scale applied to the region before detection. zh-CN: 检测前对区域应用的缩放比例. */
    @JvmField var scale: Float,
    /** Region of the source image the features belong to. zh-CN: 特征所属的原图区域. */
    @JvmField var region: Rect,
) : Shootable<ImageFeatures> {

    /** Number of detected key points. zh-CN: 检测到的关键点数量. */
    @JvmField
    val count = javaObject.keyPointCount

    /** Detection method name ("SIFT" or "ORB"). zh-CN: 检测方法名称. */
    @JvmField
    val method = when (javaObject.method) {
        ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT -> "SIFT"
        ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB -> "ORB"
        else -> ""
    }

    private var mIsOneShot = false

    @JvmField
    var recycled = false

    override fun isRecycled() = recycled

    override fun setOneShot(b: Boolean): ImageFeatures {
        mIsOneShot = b
        return this
    }

    override fun shoot() {
        if (mIsOneShot) recycle()
    }

    @JvmField
    var onRecycled: ((ImageFeatures) -> Undefined) = { _: ImageFeatures -> RhinoUtils.UNDEFINED }

    override fun recycle() {
        if (!recycled) {
            javaObject.release()
            onRecycled.invoke(this)
            recycled = true
        }
    }

    override fun toString(): String {
        return "[${ImageFeatures::class.java.simpleName}] method=$method, count=$count, scale=$scale, region=$region, recycled=$recycled"
    }

}
