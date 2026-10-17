package org.autojs.autojs.engine

import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.global.CoordinateScale
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.json.JSONArray
import org.json.JSONObject

/** One execution's coordinate bases, shared by the Node and Bun JSON adapters. */
internal class RuntimeCoordinateBridge(
    private val display: () -> Display = {
        ScreenMetrics.dimensions().let { Display(it.width, it.height, it.rotation) }
    },
) {
    data class Display(val width: Int, val height: Int, val rotation: Int)

    private val scale = Scale()

    @Synchronized
    fun invoke(method: String, args: JSONArray): Any? {
        require(method in METHODS) { "Unknown screen metrics method: $method" }
        val values = Array<Any?>(args.length()) { index -> args.opt(index).takeUnless { it == JSONObject.NULL } }
        val axis = CoordinateScale.Axis.entries.firstOrNull { it.functionName == method }
        if (axis != null) {
            val current = display()
            return CoordinateScale.convert(axis, current.width, current.height, scale, values)
        }
        val expected = when (method) {
            "setScaleBases" -> 2
            "setScaleBaseX", "setScaleBaseY" -> 1
            else -> 0
        }
        require(values.size == expected) { "$method requires $expected arguments" }
        return when (method) {
            "getScaleBaseX" -> scale.baseX
            "getScaleBaseY" -> scale.baseY
            "getScaleBases" -> scale.snapshotBases().let { (x, y) -> JSONObject().put("x", x).put("y", y) }
            "setScaleBaseX" -> { scale.baseX = coerceIntNumber(values[0]); null }
            "setScaleBaseY" -> { scale.baseY = coerceIntNumber(values[0]); null }
            "setScaleBases" -> { scale.setBases(coerceIntNumber(values[0]), coerceIntNumber(values[1])); null }
            else -> {
                val current = display()
                val (baseX, baseY) = scale.snapshotBases()
                JSONObject().put("width", current.width).put("height", current.height)
                    .put("rotation", current.rotation).put("baseX", baseX).put("baseY", baseY)
            }
        }
    }

    companion object {
        val METHODS = setOf(
            "cX", "cY", "cXy", "cYx", "getInfo", "getScaleBases", "getScaleBaseX", "getScaleBaseY",
            "setScaleBases", "setScaleBaseX", "setScaleBaseY",
        )
    }
}
