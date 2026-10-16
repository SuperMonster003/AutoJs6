package org.autojs.autojs.core.ui.inflater

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.LayoutDirection
import kotlin.math.roundToInt

/**
 * A `<vector>` drawable of a script project (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A8, decision
 * D8). The platform's VectorDrawable can only be inflated from compiled XML, so the host draws the parsed tree itself
 * the way the platform's native renderer (hwui, API 24 and later) does: the canvas is scaled from the viewport to the
 * bounds and every group concatenates its transform (pivot, scale, rotation, translation), so stroke widths and
 * gradients are in viewport units and scale with it; trim offsets wrap around, clip paths clip the rest of their group,
 * and the tint, the drawable alpha and a color filter apply to the composed image. `autoMirrored` flips it in
 * right-to-left layouts.
 *
 * zh-CN: 脚本工程中的 `<vector>` 矢量图 (宿主路线图 A8, 决策 D8). 平台 VectorDrawable 只能从编译后的 XML 创建, 因此宿主按平台渲染器的方式
 * 按平台原生渲染器 (hwui, API 24 起) 的方式自行绘制解析后的树: 画布从视口缩放到边界, 各组依次叠加变换, 描边宽度与渐变以视口单位随之缩放;
 * 裁剪路径作用于组内其后的内容, 着色, 透明度与颜色过滤作用于合成结果.
 */
internal class ScriptVectorDrawable private constructor(private val state: State) : Drawable() {

    constructor(
        width: Int,
        height: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        tint: ColorStateList?,
        tintMode: PorterDuff.Mode,
        alpha: Float,
        autoMirrored: Boolean,
        root: Group,
    ) : this(State(width, height, viewportWidth, viewportHeight, tint, tintMode, alpha, autoMirrored, root))

    /** A `<group>`: its own transform, then its children in order. */
    class Group(
        val rotation: Float = 0f,
        val pivotX: Float = 0f,
        val pivotY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val translateX: Float = 0f,
        val translateY: Float = 0f,
        val children: List<Any>,
    ) {
        val localMatrix = Matrix().apply {
            postTranslate(-pivotX, -pivotY)
            postScale(scaleX, scaleY)
            postRotate(rotation, 0f, 0f)
            postTranslate(translateX + pivotX, translateY + pivotY)
        }
    }

    /** A `<path>`; a transparent fill or stroke color without a shader is not drawn. */
    class PathItem(
        val path: Path,
        val fillColor: Int = Color.TRANSPARENT,
        val fillShader: Shader? = null,
        val fillAlpha: Float = 1f,
        val evenOdd: Boolean = false,
        val strokeColor: Int = Color.TRANSPARENT,
        val strokeShader: Shader? = null,
        val strokeAlpha: Float = 1f,
        val strokeWidth: Float = 0f,
        val strokeCap: Paint.Cap = Paint.Cap.BUTT,
        val strokeJoin: Paint.Join = Paint.Join.MITER,
        val strokeMiter: Float = 4f,
        val trimStart: Float = 0f,
        val trimEnd: Float = 1f,
        val trimOffset: Float = 0f,
    ) {
        init {
            path.fillType = if (evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        }
    }

    /** A `<clip-path>`: clips what its group draws after it. */
    class ClipItem(val path: Path)

    class State(
        val width: Int,
        val height: Int,
        val viewportWidth: Float,
        val viewportHeight: Float,
        val tint: ColorStateList?,
        val tintMode: PorterDuff.Mode,
        val alpha: Float,
        val autoMirrored: Boolean,
        val root: Group,
    ) : ConstantState() {
        override fun newDrawable() = ScriptVectorDrawable(this)
        override fun getChangingConfigurations() = 0
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val trimmedPath = Path()
    private val pathMeasure = PathMeasure()
    private var drawableAlpha = 255
    private var colorFilter: ColorFilter? = null
    private var tint: ColorStateList? = state.tint
    private var tintMode: PorterDuff.Mode = state.tintMode
    private var tintFilter: ColorFilter? = null
    private var tintColor: Int? = null

    init {
        updateTintFilter()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val alpha = (state.alpha * drawableAlpha).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        val filter = colorFilter ?: tintFilter
        val saveCount = if (alpha < 255 || filter != null) {
            layerPaint.alpha = alpha
            layerPaint.colorFilter = filter
            canvas.saveLayer(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), layerPaint)
        } else {
            canvas.save()
        }
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        if (state.autoMirrored && layoutDirection == LayoutDirection.RTL) {
            canvas.translate(bounds.width().toFloat(), 0f)
            canvas.scale(-1f, 1f)
        }
        canvas.clipRect(0, 0, bounds.width(), bounds.height())
        canvas.scale(bounds.width() / state.viewportWidth, bounds.height() / state.viewportHeight)
        drawGroup(canvas, state.root)
        canvas.restoreToCount(saveCount)
    }

    private fun drawGroup(canvas: Canvas, group: Group) {
        canvas.save()
        canvas.concat(group.localMatrix)
        for (child in group.children) {
            when (child) {
                is Group -> drawGroup(canvas, child)
                is ClipItem -> canvas.clipPath(child.path)
                is PathItem -> drawPath(canvas, child)
            }
        }
        canvas.restore()
    }

    /** hwui's `FullPath::draw`: the fill, then the stroke, in viewport units under the canvas transform. */
    private fun drawPath(canvas: Canvas, item: PathItem) {
        val path = trimmed(item) ?: return
        if (item.fillShader != null || item.fillColor != Color.TRANSPARENT) {
            fillPaint.shader = item.fillShader
            fillPaint.color = if (item.fillShader != null) Color.BLACK else item.fillColor
            fillPaint.alpha = (Color.alpha(fillPaint.color) * item.fillAlpha).roundToInt().coerceIn(0, 255)
            canvas.drawPath(path, fillPaint)
        }
        if (item.strokeShader != null || item.strokeColor != Color.TRANSPARENT) {
            strokePaint.shader = item.strokeShader
            strokePaint.color = if (item.strokeShader != null) Color.BLACK else item.strokeColor
            strokePaint.alpha = (Color.alpha(strokePaint.color) * item.strokeAlpha).roundToInt().coerceIn(0, 255)
            strokePaint.strokeCap = item.strokeCap
            strokePaint.strokeJoin = item.strokeJoin
            strokePaint.strokeMiter = item.strokeMiter
            strokePaint.strokeWidth = item.strokeWidth
            canvas.drawPath(path, strokePaint)
        }
    }

    /** hwui's `applyTrim`: the part between the trimmed start and end of the first contour, wrapping past its end; null when empty. */
    private fun trimmed(item: PathItem): Path? {
        if (item.trimStart == 0f && item.trimEnd == 1f) return item.path
        if (item.trimStart == item.trimEnd) return null
        pathMeasure.setPath(item.path, false)
        val length = pathMeasure.length
        val start = length * ((item.trimStart + item.trimOffset) % 1f)
        val end = length * ((item.trimEnd + item.trimOffset) % 1f)
        trimmedPath.reset()
        trimmedPath.fillType = item.path.fillType
        if (start > end) {
            pathMeasure.getSegment(start, length, trimmedPath, true)
            if (end > 0f) pathMeasure.getSegment(0f, end, trimmedPath, true)
        } else {
            pathMeasure.getSegment(start, end, trimmedPath, true)
        }
        return trimmedPath
    }

    /** The tint filter for the current state; true when its color changed. */
    private fun updateTintFilter(): Boolean {
        val previous = tintColor
        tintColor = tint?.let { it.getColorForState(getState(), it.defaultColor) }
        tintFilter = tintColor?.let { PorterDuffColorFilter(it, tintMode) }
        return previous != tintColor
    }

    override fun getIntrinsicWidth() = state.width

    override fun getIntrinsicHeight() = state.height

    override fun setAlpha(alpha: Int) {
        if (drawableAlpha != alpha) {
            drawableAlpha = alpha
            invalidateSelf()
        }
    }

    override fun getAlpha() = drawableAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        this.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getColorFilter() = colorFilter

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        updateTintFilter()
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        this.tintMode = tintMode ?: PorterDuff.Mode.SRC_IN
        updateTintFilter()
        invalidateSelf()
    }

    override fun isStateful() = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean = tint?.isStateful == true && updateTintFilter()

    override fun isAutoMirrored() = state.autoMirrored

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun getConstantState(): ConstantState = state
}
