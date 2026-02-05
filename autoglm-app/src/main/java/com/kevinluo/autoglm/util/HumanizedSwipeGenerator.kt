package com.kevinluo.autoglm.util

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.random.Random

/**
 * Data class representing a point in 2D space.
 *
 * @property x The X coordinate in pixels
 * @property y The Y coordinate in pixels
 */
data class Point(val x: Int, val y: Int)

/**
 * Data class representing a swipe path with points and duration.
 *
 * @property points List of points that make up the swipe path
 * @property durationMs Total duration of the swipe in milliseconds
 */
data class SwipePath(val points: List<Point>, val durationMs: Int)

/**
 * Generates humanized swipe paths that simulate natural human finger movements.
 *
 * Features:
 * - Bezier curve interpolation for smooth, curved paths
 * - Micro-variations to simulate hand tremor
 * - Duration calculation based on distance with acceleration/deceleration phases
 *
 * Usage example:
 * ```kotlin
 * val generator = HumanizedSwipeGenerator()
 * val path = generator.generatePath(
 *     startX = 100, startY = 500,
 *     endX = 100, endY = 1500,
 *     screenWidth = 1080, screenHeight = 2400
 * )
 * // Use path.points and path.durationMs for swipe execution
 * ```
 *
 * @param random Random instance for generating variations (default: Random.Default)
 *
 */
class HumanizedSwipeGenerator(private val random: Random = Random.Default) {
    /**
     * Generates a humanized swipe path from start to end coordinates.
     *
     * The path uses a quadratic Bezier curve with micro-variations to simulate
     * natural human finger movement. The duration is calculated based on the
     * distance between start and end points.
     *
     * @param startX Starting X coordinate (absolute pixels)
     * @param startY Starting Y coordinate (absolute pixels)
     * @param endX Ending X coordinate (absolute pixels)
     * @param endY Ending Y coordinate (absolute pixels)
     * @param screenWidth Screen width for boundary clamping
     * @param screenHeight Screen height for boundary clamping
     * @return SwipePath containing the list of points and calculated duration
     *
     */
    fun generatePath(startX: Int, startY: Int, endX: Int, endY: Int, screenWidth: Int, screenHeight: Int): SwipePath {
        val distance = hypot((endX - startX).toDouble(), (endY - startY).toDouble())
        val durationMs = calculateDuration(distance)

        val points =
            generateBezierPath(
                startX,
                startY,
                endX,
                endY,
                screenWidth,
                screenHeight,
                DEFAULT_POINT_COUNT,
            )

        return SwipePath(points, durationMs)
    }

    /**
     * Generates a linear swipe path (no curve, direct line).
     *
     * Use this for precise swipes where curved paths might cause issues,
     * such as scrolling in lists or precise drag operations.
     *
     * @param startX Starting X coordinate (absolute pixels)
     * @param startY Starting Y coordinate (absolute pixels)
     * @param endX Ending X coordinate (absolute pixels)
     * @param endY Ending Y coordinate (absolute pixels)
     * @param screenWidth Screen width for boundary clamping
     * @param screenHeight Screen height for boundary clamping
     * @return SwipePath containing the list of points and calculated duration
     *
     */
    fun generateLinearPath(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): SwipePath {
        val distance = hypot((endX - startX).toDouble(), (endY - startY).toDouble())
        val durationMs = calculateDuration(distance)

        val points =
            generateLinearPoints(
                startX,
                startY,
                endX,
                endY,
                screenWidth,
                screenHeight,
                DEFAULT_POINT_COUNT,
            )

        return SwipePath(points, durationMs)
    }

    /**
     * Calculates swipe duration based on distance.
     *
     * Longer distances result in longer durations, with min/max bounds
     * to ensure realistic swipe timing.
     *
     * @param distance The distance of the swipe in pixels
     * @return Duration in milliseconds, clamped to [MIN_DURATION_MS, MAX_DURATION_MS]
     */
    internal fun calculateDuration(distance: Double): Int {
        val calculatedDuration = (BASE_DURATION_MS + distance * DURATION_PER_PIXEL).toInt()
        return calculatedDuration.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    }

    /**
     * Generates a bezier curve path with micro-variations for humanization.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param screenWidth Screen width for boundary clamping
     * @param screenHeight Screen height for boundary clamping
     * @param pointCount Number of points to generate along the path
     * @return List of points forming the bezier curve path
     */
    private fun generateBezierPath(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        screenWidth: Int,
        screenHeight: Int,
        pointCount: Int,
    ): List<Point> {
        // Calculate control point for quadratic bezier curve
        val controlPoint = calculateControlPoint(startX, startY, endX, endY, screenWidth, screenHeight)

        val points = mutableListOf<Point>()

        for (i in 0 until pointCount) {
            val t = i.toDouble() / (pointCount - 1)

            // Quadratic bezier formula: B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
            val oneMinusT = 1 - t
            val x =
                (
                    oneMinusT.pow(2) * startX +
                        2 * oneMinusT * t * controlPoint.first +
                        t.pow(2) * endX
                    ).toInt()
            val y =
                (
                    oneMinusT.pow(2) * startY +
                        2 * oneMinusT * t * controlPoint.second +
                        t.pow(2) * endY
                    ).toInt()

            // Add micro-variations (tremor) except for start and end points
            val (finalX, finalY) =
                if (i == 0 || i == pointCount - 1) {
                    Pair(x, y)
                } else {
                    addTremor(x, y)
                }

            // Clamp to screen bounds
            val clampedX = finalX.coerceIn(0, screenWidth - 1)
            val clampedY = finalY.coerceIn(0, screenHeight - 1)

            points.add(Point(clampedX, clampedY))
        }

        return points
    }

    /**
     * Calculates the control point for the bezier curve.
     *
     * The control point is offset perpendicular to the swipe direction
     * to create a natural curved path.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param screenWidth Screen width (unused, kept for API consistency)
     * @param screenHeight Screen height (unused, kept for API consistency)
     * @return Pair of (controlX, controlY) coordinates
     */
    private fun calculateControlPoint(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Double, Double> {
        val midX = (startX + endX) / 2.0
        val midY = (startY + endY) / 2.0

        val dx = endX - startX
        val dy = endY - startY
        val distance = hypot(dx.toDouble(), dy.toDouble())

        if (distance < 1) {
            return Pair(midX, midY)
        }

        // Calculate perpendicular offset
        // Perpendicular vector is (-dy, dx) normalized
        val perpX = -dy / distance
        val perpY = dx / distance

        // Determine curve direction based on swipe type
        val curveDirection = determineCurveDirection(dx, dy)

        // Calculate offset magnitude
        val offsetMagnitude = distance * CURVE_FACTOR * curveDirection

        // Add some randomness to the curve
        val randomFactor = 0.8 + random.nextDouble() * 0.4 // 0.8 to 1.2

        val controlX = midX + perpX * offsetMagnitude * randomFactor
        val controlY = midY + perpY * offsetMagnitude * randomFactor

        return Pair(controlX, controlY)
    }

    /**
     * Determines the curve direction based on swipe direction.
     *
     * @param dx Horizontal distance (endX - startX)
     * @param dy Vertical distance (endY - startY)
     * @return 1 or -1 to curve in different directions
     */
    private fun determineCurveDirection(dx: Int, dy: Int): Int {
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)

        return if (absDy > absDx * DIRECTION_THRESHOLD) {
            // Vertical swipe - curve based on direction
            if (dy > 0) 1 else -1
        } else {
            // Horizontal swipe - curve based on direction
            if (dx > 0) 1 else -1
        }
    }

    /**
     * Adds micro-variations to simulate hand tremor.
     *
     * @param x Original X coordinate
     * @param y Original Y coordinate
     * @return Pair of (x, y) with tremor applied
     */
    private fun addTremor(x: Int, y: Int): Pair<Int, Int> {
        val tremorX = random.nextInt(-TREMOR_AMPLITUDE, TREMOR_AMPLITUDE + 1)
        val tremorY = random.nextInt(-TREMOR_AMPLITUDE, TREMOR_AMPLITUDE + 1)
        return Pair(x + tremorX, y + tremorY)
    }

    /**
     * Generates a linear path (straight line) without curves.
     *
     * @param startX Starting X coordinate
     * @param startY Starting Y coordinate
     * @param endX Ending X coordinate
     * @param endY Ending Y coordinate
     * @param screenWidth Screen width for boundary clamping
     * @param screenHeight Screen height for boundary clamping
     * @param pointCount Number of points to generate along the path
     * @return List of points forming the linear path
     */
    private fun generateLinearPoints(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        screenWidth: Int,
        screenHeight: Int,
        pointCount: Int,
    ): List<Point> {
        val points = mutableListOf<Point>()

        for (i in 0 until pointCount) {
            val t = i.toDouble() / (pointCount - 1)

            val x = (startX + t * (endX - startX)).toInt()
            val y = (startY + t * (endY - startY)).toInt()

            // Clamp to screen bounds
            val clampedX = x.coerceIn(0, screenWidth - 1)
            val clampedY = y.coerceIn(0, screenHeight - 1)

            points.add(Point(clampedX, clampedY))
        }

        return points
    }

    companion object {
        // Duration calculation constants
        private const val BASE_DURATION_MS = 200
        private const val DURATION_PER_PIXEL = 0.3
        private const val MIN_DURATION_MS = 150
        private const val MAX_DURATION_MS = 1500

        // Path generation constants
        private const val DEFAULT_POINT_COUNT = 20

        /** How much the path curves (0 = straight, higher = more curve). */
        private const val CURVE_FACTOR = 0.15

        /** Max pixels of hand tremor variation. */
        private const val TREMOR_AMPLITUDE = 3

        // Vertical vs horizontal swipe detection threshold
        private const val DIRECTION_THRESHOLD = 0.5
    }
}
