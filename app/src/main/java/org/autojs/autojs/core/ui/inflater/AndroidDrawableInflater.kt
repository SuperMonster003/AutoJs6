package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.NinePatch
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.LevelListDrawable
import android.graphics.drawable.NinePatchDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.RotateDrawable
import android.graphics.drawable.ScaleDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import org.autojs.autojs.core.ui.inflater.ScriptResourceRepository.Companion.childElements
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.Ids
import org.w3c.dom.Element
import java.io.File
import java.util.LinkedHashSet
import kotlin.math.roundToInt

/**
 * Inflates common Android drawable XML directly from an uncompiled script resource directory.
 */
internal class AndroidDrawableInflater(
    private val resolver: AndroidResourceResolver,
) {

    private val resolvingFiles = ThreadLocal<LinkedHashSet<String>>()
    private val inflatingDepth = ThreadLocal<Int>()

    fun inflate(context: Context, file: File): Drawable {
        val isRootCall = resolvingFiles.get() == null
        val stack = resolvingFiles.get() ?: LinkedHashSet<String>().also(resolvingFiles::set)
        val canonicalPath = file.canonicalPath
        check(stack.size < MAX_DRAWABLE_DEPTH) {
            "Android drawable reference depth exceeds $MAX_DRAWABLE_DEPTH"
        }
        check(stack.add(canonicalPath)) {
            "Circular Android drawable reference: ${
                (stack.toList() + canonicalPath).joinToString(" -> ") { File(it).nameWithoutExtension }
            }"
        }
        return try {
            if (!file.extension.equals("xml", true)) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(canonicalPath, bounds)
                require(
                    bounds.outWidth > 0 &&
                        bounds.outHeight > 0 &&
                        bounds.outWidth.toLong() * bounds.outHeight <= MAX_BITMAP_PIXELS
                ) {
                    "Drawable bitmap dimensions are invalid or exceed $MAX_BITMAP_PIXELS pixels: $canonicalPath"
                }
                val bitmap = BitmapFactory.decodeFile(canonicalPath)
                    ?: error("Unable to decode drawable file: $canonicalPath")
                val chunk = bitmap.ninePatchChunk
                if (file.name.endsWith(".9.png", true)) {
                    require(chunk != null && NinePatch.isNinePatchChunk(chunk)) {
                        "Source .9.png drawables require an aapt-compiled NinePatch chunk: $canonicalPath"
                    }
                    NinePatchDrawable(context.resources, bitmap, chunk, Rect(), file.name)
                } else {
                    BitmapDrawable(context.resources, bitmap)
                }
            } else {
                val root = resolver.repository.parseDocument(file).documentElement
                    ?: error("Drawable XML has no root element: $canonicalPath")
                inflateElement(context, root)
            }
        } finally {
            stack.remove(canonicalPath)
            if (isRootCall) {
                resolvingFiles.remove()
            }
        }
    }

    private fun inflateElement(context: Context, element: Element): Drawable {
        val depth = inflatingDepth.get() ?: 0
        check(depth < MAX_DRAWABLE_DEPTH) {
            "Android drawable element depth exceeds $MAX_DRAWABLE_DEPTH at <${element.tagName}>"
        }
        inflatingDepth.set(depth + 1)
        return try {
            when (element.tagName) {
                "shape" -> inflateShape(context, element)
                "selector" -> inflateSelector(context, element)
                "layer-list" -> inflateLayerList(context, element)
                "level-list" -> inflateLevelList(context, element)
                "transition" -> inflateTransition(context, element)
                "inset" -> inflateInset(context, element)
                "clip" -> inflateClip(context, element)
                "scale" -> inflateScale(context, element)
                "rotate" -> inflateRotate(context, element)
                "ripple" -> inflateRipple(context, element)
                "animation-list" -> inflateAnimationList(context, element)
                "bitmap", "nine-patch" -> inflateBitmap(context, element)
                "vector" -> inflateVector(context, element)
                "color" -> ColorDrawable(parseColor(context, element.attr("color") ?: element.textContent))
                else -> error("Unsupported Android drawable XML element: <${element.tagName}>")
            }
        } finally {
            if (depth == 0) {
                inflatingDepth.remove()
            } else {
                inflatingDepth.set(depth)
            }
        }
    }

    private fun inflateShape(context: Context, element: Element): Drawable {
        val drawable = PaddedGradientDrawable()
        drawable.shape = when (element.attr("shape")) {
            null, "rectangle" -> GradientDrawable.RECTANGLE
            "oval" -> GradientDrawable.OVAL
            "line" -> GradientDrawable.LINE
            "ring" -> GradientDrawable.RING
            else -> error("Unknown shape type: ${element.attr("shape")}")
        }
        element.attr("dither")?.let { drawable.setDither(boolean(context, it)) }
        element.attr("useLevel")?.let { drawable.useLevel = boolean(context, it) }

        element.childElements().forEach { child ->
            when (child.tagName) {
                "solid" -> child.requireAttr("color").let {
                    drawable.setColor(resolver.parseColorStateList(context, it))
                }
                "stroke" -> {
                    val width = dimension(context, child.requireAttr("width"))
                    val color = resolver.parseColorStateList(context, child.requireAttr("color"))
                    val dashWidth = child.attr("dashWidth")?.let { dimensionFloat(context, it) } ?: 0f
                    val dashGap = child.attr("dashGap")?.let { dimensionFloat(context, it) } ?: 0f
                    drawable.setStroke(width, color, dashWidth, dashGap)
                }
                "corners" -> applyCorners(context, drawable, child)
                "gradient" -> applyGradient(context, drawable, child)
                "padding" -> drawable.setSourcePadding(
                    left = child.attr("left")?.let { dimension(context, it) } ?: 0,
                    top = child.attr("top")?.let { dimension(context, it) } ?: 0,
                    right = child.attr("right")?.let { dimension(context, it) } ?: 0,
                    bottom = child.attr("bottom")?.let { dimension(context, it) } ?: 0,
                )
                "size" -> drawable.setSize(
                    child.attr("width")?.let { dimension(context, it) } ?: -1,
                    child.attr("height")?.let { dimension(context, it) } ?: -1,
                )
            }
        }

        if (drawable.shape == GradientDrawable.RING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            element.attr("innerRadius")?.let { drawable.innerRadius = dimension(context, it) }
            element.attr("innerRadiusRatio")?.let { drawable.innerRadiusRatio = scalar(context, it).toFloat() }
            element.attr("thickness")?.let { drawable.thickness = dimension(context, it) }
            element.attr("thicknessRatio")?.let { drawable.thicknessRatio = scalar(context, it).toFloat() }
        }
        return drawable
    }

    private fun applyCorners(context: Context, drawable: GradientDrawable, element: Element) {
        val radiusSource = element.attr("radius")
        val radius = radiusSource?.let { dimensionFloat(context, it) } ?: 0f
        val topLeftSource = element.attr("topLeftRadius")
        val topRightSource = element.attr("topRightRadius")
        val bottomRightSource = element.attr("bottomRightRadius")
        val bottomLeftSource = element.attr("bottomLeftRadius")
        if (listOf(topLeftSource, topRightSource, bottomRightSource, bottomLeftSource).all { it == null }) {
            if (radiusSource != null) {
                drawable.cornerRadius = radius
            }
            return
        }
        val topLeft = topLeftSource?.let { dimensionFloat(context, it) } ?: radius
        val topRight = topRightSource?.let { dimensionFloat(context, it) } ?: radius
        val bottomRight = bottomRightSource?.let { dimensionFloat(context, it) } ?: radius
        val bottomLeft = bottomLeftSource?.let { dimensionFloat(context, it) } ?: radius
        drawable.cornerRadii = floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft,
        )
    }

    private fun applyGradient(context: Context, drawable: GradientDrawable, element: Element) {
        val start = parseColor(context, element.requireAttr("startColor"))
        val center = element.attr("centerColor")?.let { parseColor(context, it) }
        val end = parseColor(context, element.requireAttr("endColor"))
        drawable.colors = center?.let { intArrayOf(start, it, end) } ?: intArrayOf(start, end)
        drawable.gradientType = when (element.attr("type")?.let { scalar(context, it) }) {
            null, "linear" -> GradientDrawable.LINEAR_GRADIENT
            "radial" -> GradientDrawable.RADIAL_GRADIENT
            "sweep" -> GradientDrawable.SWEEP_GRADIENT
            else -> error("Unknown gradient type: ${element.attr("type")}")
        }
        drawable.orientation = gradientOrientation(element.attr("angle")?.let { scalar(context, it).toInt() } ?: 0)
        drawable.setGradientCenter(
            fraction(context, element.attr("centerX"), 0.5f),
            fraction(context, element.attr("centerY"), 0.5f),
        )
        element.attr("gradientRadius")?.let { drawable.gradientRadius = dimensionFloat(context, it) }
        element.attr("useLevel")?.let { drawable.useLevel = boolean(context, it) }
    }

    private fun inflateSelector(context: Context, element: Element): Drawable {
        val drawable = StateListDrawable()
        element.childElements().filter { it.tagName == "item" }.forEach { item ->
            val states = item.attributes.let { attributes ->
                buildList {
                    for (index in 0 until attributes.length) {
                        val attribute = attributes.item(index)
                        val name = attribute.localName ?: attribute.nodeName.removePrefix("android:")
                        val stateId = STATE_ATTRIBUTES[name]
                        if (stateId == null) {
                            require(!name.startsWith("state_")) {
                                "Unsupported Android selector state: $name"
                            }
                            continue
                        }
                        add(if (boolean(context, attribute.nodeValue)) stateId else -stateId)
                    }
                }.toIntArray()
            }
            drawable.addState(states, itemDrawable(context, item))
        }
        return drawable
    }

    private fun inflateLayerList(context: Context, element: Element): Drawable {
        val items = element.childElements().filter { it.tagName == "item" }
        val drawable = LayerDrawable(items.map { itemDrawable(context, it) }.toTypedArray())
        items.forEachIndexed { index, item ->
            drawable.setLayerInset(
                index,
                item.attr("left")?.let { dimension(context, it) } ?: 0,
                item.attr("top")?.let { dimension(context, it) } ?: 0,
                item.attr("right")?.let { dimension(context, it) } ?: 0,
                item.attr("bottom")?.let { dimension(context, it) } ?: 0,
            )
            val start = item.attr("start")
            val end = item.attr("end")
            if (start != null || end != null) {
                drawable.setLayerInsetRelative(
                    index,
                    start?.let { dimension(context, it) } ?: 0,
                    item.attr("top")?.let { dimension(context, it) } ?: 0,
                    end?.let { dimension(context, it) } ?: 0,
                    item.attr("bottom")?.let { dimension(context, it) } ?: 0,
                )
            }
            item.attr("width")?.let { width ->
                drawable.setLayerSize(
                    index,
                    dimension(context, width),
                    item.attr("height")?.let { dimension(context, it) } ?: -1,
                )
            }
            item.attr("height")?.takeIf { item.attr("width") == null }?.let {
                drawable.setLayerSize(index, -1, dimension(context, it))
            }
            item.attr("gravity")?.let { drawable.setLayerGravity(index, parseGravity(it)) }
            item.attr("id")?.let { drawable.setId(index, Ids.parse(it)) }
        }
        element.attr("paddingMode")?.let {
            drawable.paddingMode = when (it) {
                "nest" -> LayerDrawable.PADDING_MODE_NEST
                "stack" -> LayerDrawable.PADDING_MODE_STACK
                else -> error("Unknown layer-list padding mode: $it")
            }
        }
        return drawable
    }

    private fun inflateLevelList(context: Context, element: Element): Drawable {
        return LevelListDrawable().also { drawable ->
            element.childElements().filter { it.tagName == "item" }.forEach { item ->
                drawable.addLevel(
                    item.attr("minLevel")?.let { scalar(context, it).toInt() } ?: 0,
                    item.attr("maxLevel")?.let { scalar(context, it).toInt() } ?: 0,
                    itemDrawable(context, item),
                )
            }
        }
    }

    private fun inflateTransition(context: Context, element: Element): Drawable {
        val drawables = element.childElements()
            .filter { it.tagName == "item" }
            .map { itemDrawable(context, it) }
            .toTypedArray()
        require(drawables.size >= 2) { "<transition> requires at least two <item> drawables" }
        return TransitionDrawable(drawables).also { drawable ->
            element.attr("crossFade")?.let { drawable.isCrossFadeEnabled = boolean(context, it) }
        }
    }

    private fun inflateInset(context: Context, element: Element): Drawable {
        val child = elementDrawable(context, element)
        val uniform = element.attr("inset")?.let { dimension(context, it) }
        return InsetDrawable(
            child,
            element.attr("insetLeft")?.let { dimension(context, it) } ?: uniform ?: 0,
            element.attr("insetTop")?.let { dimension(context, it) } ?: uniform ?: 0,
            element.attr("insetRight")?.let { dimension(context, it) } ?: uniform ?: 0,
            element.attr("insetBottom")?.let { dimension(context, it) } ?: uniform ?: 0,
        )
    }

    private fun inflateClip(context: Context, element: Element): Drawable {
        val orientation = when (element.attr("clipOrientation")?.let { scalar(context, it) }) {
            "vertical" -> ClipDrawable.VERTICAL
            else -> ClipDrawable.HORIZONTAL
        }
        return ClipDrawable(
            elementDrawable(context, element),
            parseGravity(element.attr("gravity")?.let { scalar(context, it) } ?: "left"),
            orientation,
        )
    }

    private fun inflateScale(context: Context, element: Element): Drawable {
        return ScaleDrawable(
            elementDrawable(context, element),
            parseGravity(
                (element.attr("scaleGravity") ?: element.attr("gravity"))
                    ?.let { scalar(context, it) }
                    ?: "left",
            ),
            fraction(context, element.attr("scaleWidth"), -1f),
            fraction(context, element.attr("scaleHeight"), -1f),
        )
    }

    private fun inflateRotate(context: Context, element: Element): Drawable {
        return RotateDrawable().also { drawable ->
            drawable.drawable = elementDrawable(context, element)
            drawable.fromDegrees = element.attr("fromDegrees")?.let { scalar(context, it).toFloat() } ?: 0f
            drawable.toDegrees = element.attr("toDegrees")?.let { scalar(context, it).toFloat() } ?: 360f
            applyPivot(context, drawable, element.attr("pivotX"), true)
            applyPivot(context, drawable, element.attr("pivotY"), false)
        }
    }

    private fun inflateRipple(context: Context, element: Element): Drawable {
        val color = resolver.parseColorStateList(context, element.requireAttr("color"))
        var mask: Drawable? = null
        val contentItems = mutableListOf<Pair<Drawable, Int?>>()
        element.childElements().filter { it.tagName == "item" }.forEach { item ->
            val drawable = itemDrawable(context, item)
            when (item.attr("id")) {
                "@android:id/mask" -> {
                    require(mask == null) { "<ripple> may contain only one @android:id/mask item" }
                    mask = drawable
                }
                else -> contentItems += drawable to item.attr("id")?.let(Ids::parse)
            }
        }
        val content = when (contentItems.size) {
            0 -> null
            1 -> contentItems.single().first
            else -> LayerDrawable(contentItems.map { it.first }.toTypedArray()).also { layers ->
                contentItems.forEachIndexed { index, (_, id) ->
                    id?.let { layers.setId(index, it) }
                }
            }
        }
        return RippleDrawable(color, content, mask)
    }

    private fun inflateAnimationList(context: Context, element: Element): Drawable {
        return AnimationDrawable().also { drawable ->
            drawable.isOneShot = element.attr("oneshot")?.let { boolean(context, it) } ?: false
            element.childElements().filter { it.tagName == "item" }.forEach { item ->
                drawable.addFrame(
                    itemDrawable(context, item),
                    scalar(context, item.requireAttr("duration")).toInt(),
                )
            }
        }
    }

    private fun inflateBitmap(context: Context, element: Element): Drawable {
        val source = element.requireAttr("src")
        val drawable = resolver.resolveDrawable(context, source)
            ?: error("Bitmap drawable not found: $source")
        if (drawable !is BitmapDrawable) {
            return drawable
        }
        element.attr("antialias")?.let { drawable.setAntiAlias(boolean(context, it)) }
        element.attr("dither")?.let { drawable.setDither(boolean(context, it)) }
        element.attr("filter")?.let { drawable.setFilterBitmap(boolean(context, it)) }
        element.attr("gravity")?.let { drawable.gravity = parseGravity(scalar(context, it)) }
        element.attr("tileMode")?.let { mode ->
            tileMode(mode).let { drawable.setTileModeXY(it, it) }
        }
        element.attr("tileModeX")?.let { drawable.setTileModeX(tileMode(it)) }
        element.attr("tileModeY")?.let { drawable.setTileModeY(tileMode(it)) }
        return drawable
    }

    private fun itemDrawable(context: Context, item: Element): Drawable {
        item.attr("drawable")?.let { reference ->
            return resolver.resolveDrawable(context, reference)
                ?: error("Drawable resource not found: $reference")
        }
        return item.childElements().singleOrNull()?.let { inflateElement(context, it) }
            ?: error("<item> must contain exactly one drawable or an android:drawable attribute")
    }

    private fun elementDrawable(context: Context, element: Element): Drawable {
        element.attr("drawable")?.let { reference ->
            return resolver.resolveDrawable(context, reference)
                ?: error("Drawable resource not found: $reference")
        }
        return element.childElements().singleOrNull()?.let { inflateElement(context, it) }
            ?: error("<${element.tagName}> must contain exactly one drawable or an android:drawable attribute")
    }

    /**
     * A `<vector>` (host roadmap A8): the size, viewport, tint, alpha and mirroring of the root, then its groups,
     * paths and clip paths in document order; colors may be inline `<aapt:attr>` gradients. Elements a vector
     * cannot contain are skipped with a log line, like the attributes Android ignores.
     */
    private fun inflateVector(context: Context, element: Element): Drawable {
        val width = dimensionFloat(context, element.requireAttr("width")).roundToInt().coerceAtLeast(1)
        val height = dimensionFloat(context, element.requireAttr("height")).roundToInt().coerceAtLeast(1)
        val viewportWidth = number(context, element.requireAttr("viewportWidth"))
        val viewportHeight = number(context, element.requireAttr("viewportHeight"))
        require(viewportWidth > 0f && viewportHeight > 0f) { "<vector> requires a positive viewportWidth and viewportHeight" }
        return ScriptVectorDrawable(
            width = width,
            height = height,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            tint = element.attr("tint")?.let { resolver.parseColorStateList(context, it) },
            tintMode = element.attr("tintMode")?.let { porterDuffMode(scalar(context, it)) } ?: PorterDuff.Mode.SRC_IN,
            alpha = element.attr("alpha")?.let { number(context, it) } ?: 1f,
            autoMirrored = element.attr("autoMirrored")?.let { boolean(context, it) } ?: false,
            root = vectorGroup(context, element, isRoot = true),
        )
    }

    private fun vectorGroup(context: Context, element: Element, isRoot: Boolean): ScriptVectorDrawable.Group {
        val children = element.childElements().mapNotNull { child ->
            when (child.tagName.substringAfterLast(':')) {
                "group" -> vectorGroup(context, child, isRoot = false)
                "path" -> vectorPath(context, child)
                "clip-path" -> ScriptVectorDrawable.ClipItem(vectorPathData(context, child))
                "attr" -> null
                else -> null.also { Log.w(TAG, "<${child.tagName}> is not supported in a <vector> and was skipped") }
            }
        }
        if (isRoot) return ScriptVectorDrawable.Group(children = children)
        fun value(name: String, default: Float) = element.attr(name)?.let { number(context, it) } ?: default
        return ScriptVectorDrawable.Group(
            rotation = value("rotation", 0f),
            pivotX = value("pivotX", 0f),
            pivotY = value("pivotY", 0f),
            scaleX = value("scaleX", 1f),
            scaleY = value("scaleY", 1f),
            translateX = value("translateX", 0f),
            translateY = value("translateY", 0f),
            children = children,
        )
    }

    private fun vectorPath(context: Context, element: Element): ScriptVectorDrawable.PathItem {
        fun value(name: String, default: Float) = element.attr(name)?.let { number(context, it) } ?: default
        val fillShader = inlineGradient(context, element, "fillColor")
        val strokeShader = inlineGradient(context, element, "strokeColor")
        return ScriptVectorDrawable.PathItem(
            path = vectorPathData(context, element),
            fillColor = if (fillShader != null) Color.TRANSPARENT else element.attr("fillColor")?.let { vectorColor(context, it) } ?: Color.TRANSPARENT,
            fillShader = fillShader,
            fillAlpha = value("fillAlpha", 1f),
            evenOdd = element.attr("fillType")?.let { scalar(context, it) } == "evenOdd",
            strokeColor = if (strokeShader != null) Color.TRANSPARENT else element.attr("strokeColor")?.let { vectorColor(context, it) } ?: Color.TRANSPARENT,
            strokeShader = strokeShader,
            strokeAlpha = value("strokeAlpha", 1f),
            strokeWidth = value("strokeWidth", 0f),
            strokeCap = when (element.attr("strokeLineCap")?.let { scalar(context, it) }) {
                null, "butt" -> Paint.Cap.BUTT
                "round" -> Paint.Cap.ROUND
                "square" -> Paint.Cap.SQUARE
                else -> error("Unknown strokeLineCap: ${element.attr("strokeLineCap")}")
            },
            strokeJoin = when (element.attr("strokeLineJoin")?.let { scalar(context, it) }) {
                null, "miter" -> Paint.Join.MITER
                "round" -> Paint.Join.ROUND
                "bevel" -> Paint.Join.BEVEL
                else -> error("Unknown strokeLineJoin: ${element.attr("strokeLineJoin")}")
            },
            strokeMiter = value("strokeMiterLimit", 4f),
            trimStart = value("trimPathStart", 0f),
            trimEnd = value("trimPathEnd", 1f),
            trimOffset = value("trimPathOffset", 0f),
        )
    }

    private fun vectorPathData(context: Context, element: Element): Path = Path().apply {
        for (op in VectorPathData.parse(scalar(context, element.requireAttr("pathData")))) {
            when (op) {
                is VectorPathData.MoveTo -> moveTo(op.x, op.y)
                is VectorPathData.LineTo -> lineTo(op.x, op.y)
                is VectorPathData.QuadTo -> quadTo(op.x1, op.y1, op.x, op.y)
                is VectorPathData.CubicTo -> cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
                VectorPathData.Close -> close()
            }
        }
    }

    /** A color, or the default color of a color state list (as the platform takes complex colors). */
    private fun vectorColor(context: Context, value: String): Int =
        runCatching { parseColor(context, value) }.getOrElse { resolver.parseColorStateList(context, value).defaultColor }

    /** The `<gradient>` of an `<aapt:attr name="android:[name]">` child of [element], as a shader in viewport units. */
    private fun inlineGradient(context: Context, element: Element, name: String): Shader? {
        val gradient = element.childElements()
            .firstOrNull { it.tagName.substringAfterLast(':') == "attr" && it.getAttribute("name").substringAfterLast(':') == name }
            ?.childElements()?.firstOrNull { it.tagName == "gradient" }
            ?: return null
        fun value(attr: String) = gradient.attr(attr)?.let { number(context, it) } ?: 0f
        val items = gradient.childElements().filter { it.tagName == "item" }
        val colors: IntArray
        val positions: FloatArray?
        if (items.isNotEmpty()) {
            colors = items.map { vectorColor(context, it.requireAttr("color")) }.toIntArray()
            positions = items.map { item -> item.attr("offset")?.let { number(context, it) } ?: 0f }.toFloatArray()
        } else {
            val start = gradient.attr("startColor")?.let { vectorColor(context, it) } ?: Color.TRANSPARENT
            val end = gradient.attr("endColor")?.let { vectorColor(context, it) } ?: Color.TRANSPARENT
            val center = gradient.attr("centerColor")?.let { vectorColor(context, it) }
            colors = if (center != null) intArrayOf(start, center, end) else intArrayOf(start, end)
            positions = if (center != null) floatArrayOf(0f, 0.5f, 1f) else null
        }
        val tileMode = when (gradient.attr("tileMode")?.let { scalar(context, it) }) {
            null, "clamp", "disabled" -> Shader.TileMode.CLAMP
            "repeated" -> Shader.TileMode.REPEAT
            "mirror" -> Shader.TileMode.MIRROR
            else -> error("Unknown gradient tileMode: ${gradient.attr("tileMode")}")
        }
        return when (gradient.attr("type")?.let { scalar(context, it) } ?: "linear") {
            "linear" -> LinearGradient(value("startX"), value("startY"), value("endX"), value("endY"), colors, positions, tileMode)
            "radial" -> RadialGradient(value("centerX"), value("centerY"), value("gradientRadius").coerceAtLeast(Float.MIN_VALUE), colors, positions, tileMode)
            "sweep" -> SweepGradient(value("centerX"), value("centerY"), colors, positions)
            else -> error("Unknown gradient type: ${gradient.attr("type")}")
        }
    }

    private fun number(context: Context, value: String): Float {
        val resolved = scalar(context, value)
        return resolved.toFloatOrNull() ?: error("Invalid Android number value: $resolved")
    }

    private fun porterDuffMode(value: String) = when (value) {
        "src_over" -> PorterDuff.Mode.SRC_OVER
        "src_in" -> PorterDuff.Mode.SRC_IN
        "src_atop" -> PorterDuff.Mode.SRC_ATOP
        "multiply" -> PorterDuff.Mode.MULTIPLY
        "screen" -> PorterDuff.Mode.SCREEN
        "add" -> PorterDuff.Mode.ADD
        else -> error("Unknown tintMode: $value")
    }

    private fun parseColor(context: Context, value: String) = resolver.parseColor(context, value)

    private fun dimension(context: Context, value: String) =
        resolver.parseDimension(context, value).toInt()

    private fun dimensionFloat(context: Context, value: String) =
        resolver.parseDimension(context, value)

    private fun scalar(context: Context, value: String) = resolver.resolveScalar(context, value)

    private fun boolean(context: Context, value: String): Boolean {
        val resolved = scalar(context, value)
        return resolved.toBooleanStrictOrNull()
            ?: error("Invalid Android boolean value: $resolved")
    }

    private fun fraction(context: Context, value: String?, default: Float): Float {
        value ?: return default
        val resolved = scalar(context, value)
        val isPercent = resolved.endsWith("%") || resolved.endsWith("%p")
        return resolved
            .removeSuffix("%p")
            .removeSuffix("%")
            .toFloat() / if (isPercent) 100f else 1f
    }

    private fun parseGravity(value: String): Int {
        return value.split('|').fold(Gravity.NO_GRAVITY) { gravity, part ->
            gravity or when (part.trim()) {
                "fill" -> Gravity.FILL
                "fill_horizontal" -> Gravity.FILL_HORIZONTAL
                "fill_vertical" -> Gravity.FILL_VERTICAL
                "clip_horizontal" -> Gravity.CLIP_HORIZONTAL
                "clip_vertical" -> Gravity.CLIP_VERTICAL
                else -> Gravities.parse(part)
            }
        }
    }

    private fun tileMode(value: String) = when (value) {
        "clamp" -> Shader.TileMode.CLAMP
        "mirror" -> Shader.TileMode.MIRROR
        "repeat" -> Shader.TileMode.REPEAT
        else -> error("Unknown bitmap tile mode: $value")
    }

    private fun gradientOrientation(angle: Int) = when (angle.mod(360)) {
        0 -> GradientDrawable.Orientation.LEFT_RIGHT
        45 -> GradientDrawable.Orientation.BL_TR
        90 -> GradientDrawable.Orientation.BOTTOM_TOP
        135 -> GradientDrawable.Orientation.BR_TL
        180 -> GradientDrawable.Orientation.RIGHT_LEFT
        225 -> GradientDrawable.Orientation.TR_BL
        270 -> GradientDrawable.Orientation.TOP_BOTTOM
        315 -> GradientDrawable.Orientation.TL_BR
        else -> error("Gradient angle must be a multiple of 45: $angle")
    }

    private fun applyPivot(context: Context, drawable: RotateDrawable, source: String?, horizontal: Boolean) {
        source ?: return
        val resolved = scalar(context, source)
        val relative = resolved.endsWith("%") || resolved.endsWith("%p")
        val value = fraction(context, resolved, 0.5f)
        if (horizontal) {
            drawable.pivotX = value
            drawable.isPivotXRelative = relative
        } else {
            drawable.pivotY = value
            drawable.isPivotYRelative = relative
        }
    }

    private fun Element.attr(name: String): String? {
        return getAttributeNS(ANDROID_NAMESPACE, name).ifBlank {
            getAttribute("android:$name")
        }.ifBlank {
            getAttribute(name)
        }.ifBlank { null }
    }

    private fun Element.requireAttr(name: String) =
        requireNotNull(attr(name)) { "<$tagName> requires android:$name" }

    private class PaddedGradientDrawable : GradientDrawable() {

        private var sourcePadding: Rect? = null

        fun setSourcePadding(left: Int, top: Int, right: Int, bottom: Int) {
            sourcePadding = Rect(left, top, right, bottom)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setPadding(left, top, right, bottom)
            }
        }

        override fun getPadding(padding: Rect): Boolean {
            sourcePadding?.let {
                padding.set(it)
                return it.left != 0 || it.top != 0 || it.right != 0 || it.bottom != 0
            }
            return super.getPadding(padding)
        }
    }

    companion object {

        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private const val MAX_DRAWABLE_DEPTH = 64
        private const val TAG = "AndroidDrawableInflater"
        private const val MAX_BITMAP_PIXELS = 16L * 1024L * 1024L

        @Suppress("DEPRECATION")
        private val STATE_ATTRIBUTES = mapOf(
            "state_above_anchor" to android.R.attr.state_above_anchor,
            "state_accelerated" to android.R.attr.state_accelerated,
            "state_active" to android.R.attr.state_active,
            "state_activated" to android.R.attr.state_activated,
            "state_checkable" to android.R.attr.state_checkable,
            "state_checked" to android.R.attr.state_checked,
            "state_drag_can_accept" to android.R.attr.state_drag_can_accept,
            "state_drag_hovered" to android.R.attr.state_drag_hovered,
            "state_enabled" to android.R.attr.state_enabled,
            "state_empty" to android.R.attr.state_empty,
            "state_expanded" to android.R.attr.state_expanded,
            "state_first" to android.R.attr.state_first,
            "state_focused" to android.R.attr.state_focused,
            "state_hovered" to android.R.attr.state_hovered,
            "state_last" to android.R.attr.state_last,
            "state_long_pressable" to android.R.attr.state_long_pressable,
            "state_middle" to android.R.attr.state_middle,
            "state_multiline" to android.R.attr.state_multiline,
            "state_pressed" to android.R.attr.state_pressed,
            "state_selected" to android.R.attr.state_selected,
            "state_single" to android.R.attr.state_single,
            "state_window_focused" to android.R.attr.state_window_focused,
        )
    }
}
