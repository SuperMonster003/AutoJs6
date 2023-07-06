package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import com.makeramen.roundedimageview.RoundedImageView
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.ui.inflater.util.Drawables

/**
 * Created by Stardust on 2017/11/30.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsImageView : RoundedImageView {

    var drawables: Drawables? = null

    private var mCircle = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    var isCircle: Boolean
        get() = mCircle
        set(circle) {
            mCircle = circle
            if (circle && width != 0) {
                cornerRadius = (width.coerceAtMost(height) / 2).toFloat()
            }
        }

    fun setSource(uri: String) {
        drawables?.setupWithImage(this, uri)
    }

    fun setSource(image: ImageWrapper) {
        setImageBitmap(image.bitmap)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
        if (mCircle) {
            cornerRadius = (measuredWidth / 2).toFloat()
        }
    }

}