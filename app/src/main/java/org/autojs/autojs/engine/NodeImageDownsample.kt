package org.autojs.autojs.engine

/**
 * Decode budget for images that model-facing plugin sessions (MCP Server, 3-Stove Agent) read from
 * disk (MCP roadmap P11 D45): a bounded longest edge instead of whatever a gallery photo happens to
 * be. Pure Kotlin so the sample-size arithmetic is unit-testable; the Android decode happens in the
 * provider.
 */
internal object NodeImageDownsample {

    const val MAX_EDGE_PX = 4096

    /**
     * The power-of-two `BitmapFactory.Options.inSampleSize` that brings the longest edge within
     * [maxEdge]; 1 when it already is. Each halving rounds up, so the decoded edge never exceeds the budget.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE_PX): Int {
        require(width > 0 && height > 0) { "image dimensions must be positive: ${width}x$height" }
        require(maxEdge > 0) { "maxEdge must be positive: $maxEdge" }
        var sample = 1
        var longest = maxOf(width, height)
        while (longest > maxEdge) {
            sample *= 2
            longest = (longest + 1) / 2
        }
        return sample
    }
}
