package com.kevinluo.autoglm.util

/**
 * Utility object for converting relative coordinates (0-999) to absolute screen coordinates.
 *
 * The model outputs coordinates in a normalized range of [0, 999] which need to be
 * converted to actual screen pixel coordinates based on the device's screen dimensions.
 * This allows the model to work with a consistent coordinate system regardless of
 * the actual screen resolution.
 *
 * Usage example:
 * ```kotlin
 * val (absX, absY) = CoordinateConverter.toAbsolute(
 *     relativeX = 500,
 *     relativeY = 500,
 *     screenWidth = 1080,
 *     screenHeight = 2400
 * )
 * // absX = 540, absY = 1200 (center of screen)
 * ```
 *
 */
object CoordinateConverter {
    /**
     * Converts a relative X coordinate to an absolute screen X coordinate.
     *
     * @param relativeX The relative X coordinate in range [0, 999]
     * @param screenWidth The actual screen width in pixels
     * @return The absolute X coordinate in range [0, screenWidth)
     *
     */
    fun toAbsoluteX(relativeX: Int, screenWidth: Int): Int = relativeX * screenWidth / RELATIVE_MAX

    /**
     * Converts a relative Y coordinate to an absolute screen Y coordinate.
     *
     * @param relativeY The relative Y coordinate in range [0, 999]
     * @param screenHeight The actual screen height in pixels
     * @return The absolute Y coordinate in range [0, screenHeight)
     *
     */
    fun toAbsoluteY(relativeY: Int, screenHeight: Int): Int = relativeY * screenHeight / RELATIVE_MAX

    /**
     * Converts relative coordinates to absolute screen coordinates.
     *
     * @param relativeX The relative X coordinate in range [0, 999]
     * @param relativeY The relative Y coordinate in range [0, 999]
     * @param screenWidth The actual screen width in pixels
     * @param screenHeight The actual screen height in pixels
     * @return A Pair of (absoluteX, absoluteY) coordinates
     *
     */
    fun toAbsolute(relativeX: Int, relativeY: Int, screenWidth: Int, screenHeight: Int): Pair<Int, Int> = Pair(
        toAbsoluteX(relativeX, screenWidth),
        toAbsoluteY(relativeY, screenHeight),
    )

    /**
     * Converts an absolute X coordinate back to a relative coordinate.
     *
     * @param absoluteX The absolute X coordinate in pixels
     * @param screenWidth The actual screen width in pixels
     * @return The relative X coordinate in range [0, 999]
     *
     */
    fun toRelativeX(absoluteX: Int, screenWidth: Int): Int = absoluteX * RELATIVE_MAX / screenWidth

    /**
     * Converts an absolute Y coordinate back to a relative coordinate.
     *
     * @param absoluteY The absolute Y coordinate in pixels
     * @param screenHeight The actual screen height in pixels
     * @return The relative Y coordinate in range [0, 999]
     *
     */
    fun toRelativeY(absoluteY: Int, screenHeight: Int): Int = absoluteY * RELATIVE_MAX / screenHeight

    /**
     * The maximum value for relative coordinates used by the model.
     * Coordinates range from 0 to 999 (inclusive), so the divisor is 1000.
     */
    const val RELATIVE_MAX = 1000
}
