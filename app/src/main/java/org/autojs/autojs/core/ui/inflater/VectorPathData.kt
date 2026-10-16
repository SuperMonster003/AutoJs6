package org.autojs.autojs.core.ui.inflater

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The `android:pathData` of a vector drawable (host roadmap `docs/dev/android-xml-attributes-roadmap.md` A8): SVG path
 * syntax read the way Android's `PathParser` reads it, as absolute move / line / quadratic / cubic / close operations.
 * Relative commands, implicit repetition (pairs after a move are lines), compact numbers (`1.5.5` is two numbers,
 * `1-2` too, `1e-3` one) and smooth curves (the reflected control point) follow the platform; an elliptical arc becomes
 * cubic segments of at most 90 degrees each, an arc with a zero radius a line.
 *
 * zh-CN: 矢量图 `android:pathData` 的解析 (宿主路线图 A8): 按 Android `PathParser` 的方式读取 SVG 路径语法, 输出绝对坐标的
 * 移动 / 直线 / 二次 / 三次 / 闭合操作. 相对命令, 隐式重复, 紧凑数字与平滑曲线同平台; 椭圆弧转为三次曲线段, 半径为 0 的弧为直线.
 */
internal object VectorPathData {

    sealed interface Op

    data class MoveTo(val x: Float, val y: Float) : Op

    data class LineTo(val x: Float, val y: Float) : Op

    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : Op

    data class CubicTo(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : Op

    data object Close : Op

    /** Number of parameters each command takes per repetition. */
    private val ARITY = mapOf(
        'z' to 0, 'm' to 2, 'l' to 2, 't' to 2, 'h' to 1, 'v' to 1, 'c' to 6, 's' to 4, 'q' to 4, 'a' to 7,
    )

    fun parse(data: String): List<Op> {
        val ops = ArrayList<Op>()
        val state = State()
        var index = 0
        val text = data.trim()
        while (index < text.length) {
            val command = text[index]
            require(command.lowercaseChar() in ARITY) { "Unknown path command '$command' at $index in \"$data\"" }
            var end = index + 1
            while (end < text.length && !isCommand(text[end])) end++
            val parameters = numbers(text.substring(index + 1, end))
            add(ops, state, command, parameters)
            index = end
        }
        return ops
    }

    private fun isCommand(c: Char) = c.isLetter() && c != 'e' && c != 'E'

    /** The numbers of one command, split the way Android's PathParser splits them. */
    internal fun numbers(text: String): FloatArray {
        val result = ArrayList<Float>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == ' ' || c == ',' || c.isWhitespace()) {
                i++
                continue
            }
            val start = i
            var seenDot = false
            var previousIsExponent = false
            while (i < text.length) {
                val current = text[i]
                val endsHere = when {
                    current == ' ' || current == ',' || current.isWhitespace() -> true
                    (current == '-' || current == '+') && i != start && !previousIsExponent -> true
                    current == '.' -> seenDot.also { seenDot = true }
                    else -> false
                }
                if (endsHere) break
                previousIsExponent = current == 'e' || current == 'E'
                i++
            }
            val number = text.substring(start, i)
            result += number.toFloatOrNull() ?: throw IllegalArgumentException("\"$number\" is not a number in path data")
        }
        return result.toFloatArray()
    }

    private class State {
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var controlX = 0f
        var controlY = 0f
        var previous = 'm'
    }

    private fun add(ops: MutableList<Op>, s: State, command: Char, p: FloatArray) {
        val arity = ARITY.getValue(command.lowercaseChar())
        if (arity == 0) {
            ops += Close
            s.x = s.startX
            s.y = s.startY
            s.controlX = s.startX
            s.controlY = s.startY
            s.previous = command
            return
        }
        require(p.size >= arity && p.size % arity == 0) { "Path command '$command' takes $arity numbers at a time, got ${p.size}" }
        val relative = command.isLowerCase()
        var k = 0
        while (k < p.size) {
            val ox = if (relative) s.x else 0f
            val oy = if (relative) s.y else 0f
            when (command.lowercaseChar()) {
                'm' -> {
                    s.x = ox + p[k]
                    s.y = oy + p[k + 1]
                    if (k > 0) {
                        ops += LineTo(s.x, s.y)
                    } else {
                        ops += MoveTo(s.x, s.y)
                        s.startX = s.x
                        s.startY = s.y
                    }
                }
                'l' -> {
                    s.x = ox + p[k]
                    s.y = oy + p[k + 1]
                    ops += LineTo(s.x, s.y)
                }
                'h' -> {
                    s.x = ox + p[k]
                    ops += LineTo(s.x, s.y)
                }
                'v' -> {
                    s.y = oy + p[k]
                    ops += LineTo(s.x, s.y)
                }
                'c' -> {
                    val x2 = ox + p[k + 2]
                    val y2 = oy + p[k + 3]
                    ops += CubicTo(ox + p[k], oy + p[k + 1], x2, y2, ox + p[k + 4], oy + p[k + 5])
                    s.x = ox + p[k + 4]
                    s.y = oy + p[k + 5]
                    s.controlX = x2
                    s.controlY = y2
                }
                's' -> {
                    val smooth = s.previous.lowercaseChar() == 'c' || s.previous.lowercaseChar() == 's'
                    val x1 = if (smooth) 2 * s.x - s.controlX else s.x
                    val y1 = if (smooth) 2 * s.y - s.controlY else s.y
                    val x2 = ox + p[k]
                    val y2 = oy + p[k + 1]
                    ops += CubicTo(x1, y1, x2, y2, ox + p[k + 2], oy + p[k + 3])
                    s.x = ox + p[k + 2]
                    s.y = oy + p[k + 3]
                    s.controlX = x2
                    s.controlY = y2
                }
                'q' -> {
                    val x1 = ox + p[k]
                    val y1 = oy + p[k + 1]
                    ops += QuadTo(x1, y1, ox + p[k + 2], oy + p[k + 3])
                    s.x = ox + p[k + 2]
                    s.y = oy + p[k + 3]
                    s.controlX = x1
                    s.controlY = y1
                }
                't' -> {
                    val smooth = s.previous.lowercaseChar() == 'q' || s.previous.lowercaseChar() == 't'
                    val x1 = if (smooth) 2 * s.x - s.controlX else s.x
                    val y1 = if (smooth) 2 * s.y - s.controlY else s.y
                    ops += QuadTo(x1, y1, ox + p[k], oy + p[k + 1])
                    s.x = ox + p[k]
                    s.y = oy + p[k + 1]
                    s.controlX = x1
                    s.controlY = y1
                }
                'a' -> {
                    val endX = ox + p[k + 5]
                    val endY = oy + p[k + 6]
                    arc(ops, s.x, s.y, endX, endY, abs(p[k]), abs(p[k + 1]), p[k + 2], p[k + 3] != 0f, p[k + 4] != 0f)
                    s.x = endX
                    s.y = endY
                    s.controlX = endX
                    s.controlY = endY
                }
            }
            s.previous = command
            k += arity
        }
    }

    /** Android's `PathParser.drawArc`: the arc from (x0, y0) to (x1, y1) as cubic segments. */
    private fun arc(ops: MutableList<Op>, x0: Float, y0: Float, x1: Float, y1: Float, a: Float, b: Float, theta: Float, largeArc: Boolean, sweepPositive: Boolean) {
        if (x0 == x1 && y0 == y1) return
        if (a == 0f || b == 0f) {
            ops += LineTo(x1, y1)
            return
        }
        val thetaRadians = Math.toRadians(theta.toDouble())
        val cosTheta = cos(thetaRadians)
        val sinTheta = sin(thetaRadians)
        val x0p = (x0 * cosTheta + y0 * sinTheta) / a
        val y0p = (-x0 * sinTheta + y0 * cosTheta) / b
        val x1p = (x1 * cosTheta + y1 * sinTheta) / a
        val y1p = (-x1 * sinTheta + y1 * cosTheta) / b
        val dx = x0p - x1p
        val dy = y0p - y1p
        val xm = (x0p + x1p) / 2
        val ym = (y0p + y1p) / 2
        val dsq = dx * dx + dy * dy
        if (dsq == 0.0) return
        val disc = 1.0 / dsq - 1.0 / 4.0
        if (disc < 0.0) {
            // The radii are too small for the two points: scale them up just enough.
            val adjust = (sqrt(dsq) / 1.99999).toFloat()
            arc(ops, x0, y0, x1, y1, a * adjust, b * adjust, theta, largeArc, sweepPositive)
            return
        }
        val root = sqrt(disc)
        val sdx = root * dx
        val sdy = root * dy
        var cx: Double
        var cy: Double
        if (largeArc == sweepPositive) {
            cx = xm - sdy
            cy = ym + sdx
        } else {
            cx = xm + sdy
            cy = ym - sdx
        }
        val eta0 = atan2(y0p - cy, x0p - cx)
        val eta1 = atan2(y1p - cy, x1p - cx)
        var sweep = eta1 - eta0
        if (sweepPositive != (sweep >= 0)) {
            sweep += if (sweep > 0) -2 * Math.PI else 2 * Math.PI
        }
        cx *= a
        cy *= b
        val tcx = cx
        cx = cx * cosTheta - cy * sinTheta
        cy = tcx * sinTheta + cy * cosTheta
        arcToBezier(ops, cx, cy, a.toDouble(), b.toDouble(), x0.toDouble(), y0.toDouble(), thetaRadians, eta0, sweep)
    }

    /** Android's `PathParser.arcToBezier` (equations from spaceroots.org): at most 45 degrees per cubic segment. */
    private fun arcToBezier(ops: MutableList<Op>, cx: Double, cy: Double, a: Double, b: Double, startX: Double, startY: Double, theta: Double, start: Double, sweep: Double) {
        val segments = ceil(abs(sweep * 4 / Math.PI)).toInt()
        var eta1 = start
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        var e1x = startX
        var e1y = startY
        var ep1x = -a * cosTheta * sin(eta1) - b * sinTheta * cos(eta1)
        var ep1y = -a * sinTheta * sin(eta1) + b * cosTheta * cos(eta1)
        val perSegment = sweep / segments
        repeat(segments) {
            val eta2 = eta1 + perSegment
            val sinEta2 = sin(eta2)
            val cosEta2 = cos(eta2)
            val e2x = cx + a * cosTheta * cosEta2 - b * sinTheta * sinEta2
            val e2y = cy + a * sinTheta * cosEta2 + b * cosTheta * sinEta2
            val ep2x = -a * cosTheta * sinEta2 - b * sinTheta * cosEta2
            val ep2y = -a * sinTheta * sinEta2 + b * cosTheta * cosEta2
            val tanDiff2 = tan((eta2 - eta1) / 2)
            val alpha = sin(eta2 - eta1) * (sqrt(4 + 3 * tanDiff2 * tanDiff2) - 1) / 3
            ops += CubicTo(
                (e1x + alpha * ep1x).toFloat(), (e1y + alpha * ep1y).toFloat(),
                (e2x - alpha * ep2x).toFloat(), (e2y - alpha * ep2y).toFloat(),
                e2x.toFloat(), e2y.toFloat(),
            )
            eta1 = eta2
            e1x = e2x
            e1y = e2y
            ep1x = ep2x
            ep1y = ep2y
        }
    }
}
