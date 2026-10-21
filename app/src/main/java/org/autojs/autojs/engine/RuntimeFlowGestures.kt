package org.autojs.autojs.engine

/** Portable forms of the Rhino Flow global gestures. Android validates the resulting paths. */
internal object RuntimeFlowGestures {
    val names = setOf("press", "swipe", "gesture", "gestures")
    data class Stroke(val start: Long, val duration: Long, val points: List<Pair<Int, Int>>)

    fun parse(name: String, args: List<Any?>, tapTimeout: Long): List<Stroke> {
        fun single(duration: Any?, points: List<Pair<Int, Int>>) = listOf(Stroke(0, time(duration, false), points))
        return when (name) {
            "press" -> when (args.size) {
                1 -> single(tapTimeout, listOf(point(args[0])))
                2 -> when {
                    args[0] is List<*> -> single(args[1], listOf(point(args[0])))
                    args[1] is List<*> -> single(args[0], listOf(point(args[1])))
                    else -> single(tapTimeout, listOf(coordinate(args[0]) to coordinate(args[1])))
                }
                3 -> single(args[2], listOf(coordinate(args[0]) to coordinate(args[1])))
                else -> throw IllegalArgumentException("press requires a point, x/y, and optional duration")
            }
            "swipe" -> {
                val duration: Any?
                val path: List<Pair<Int, Int>>
                when (args.size) {
                    2 -> if (args[0] is Number) { duration = args[0]; path = points(listOf(args[1])) }
                    else { duration = args[1]; path = points(listOf(args[0])) }
                    3 -> if (args[0] is Number) { duration = args[0]; path = points(args.drop(1)) }
                    else { duration = args[2]; path = points(args.take(2)) }
                    5 -> { duration = args[4]; path = points(args.take(4)) }
                    else -> throw IllegalArgumentException("Invalid swipe overload")
                }
                require(path.size == 2) { "swipe requires exactly two points" }
                single(duration, path)
            }
            "gesture" -> {
                require(args.size >= 2) { "gesture requires duration and points" }
                single(args[0], points(args.drop(1)))
            }
            "gestures" -> {
                require(args.isNotEmpty()) { "gestures requires at least one stroke" }
                args.map { value ->
                    require(value is List<*> && value.size >= 2) { "A stroke requires duration and points" }
                    val delayed = value[1] is Number
                    val offset = if (delayed) 2 else 1
                    require(value.size > offset) { "A stroke requires points" }
                    Stroke(if (delayed) time(value[0], true) else 0, time(value[offset - 1], false), value.drop(offset).map(::point))
                }
            }
            else -> throw IllegalArgumentException("Unknown gesture: $name")
        }
    }

    private fun coordinate(value: Any?): Int {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            "A gesture coordinate must be a finite 32-bit number"
        }
        return value.toInt()
    }
    private fun time(value: Any?, zero: Boolean): Long {
        require(value is Number && value.toDouble().isFinite() && value.toDouble() < Long.MAX_VALUE.toDouble()) { "A gesture time must be finite" }
        val time = value.toLong()
        require(time >= if (zero) 0 else 1) { "Gesture start must be non-negative and duration must be positive" }
        return time
    }
    private fun point(value: Any?): Pair<Int, Int> {
        require(value is List<*> && value.size == 2) { "A gesture point must be [x, y]" }
        return coordinate(value[0]) to coordinate(value[1])
    }
    private fun points(values: List<*>): List<Pair<Int, Int>> {
        val group = if (values.size == 1 && values[0] is List<*>) values[0] as List<*> else values
        require(group.isNotEmpty()) { "A gesture path must contain a point" }
        if (group.all { it is Number }) {
            require(group.size % 2 == 0) { "Flat coordinates must contain x/y pairs" }
            return group.chunked(2).map(::point)
        }
        return group.flatMap { value ->
            require(value is List<*> && value.isNotEmpty()) { "A gesture path requires points" }
            when {
                value.all { it is List<*> } -> points(value)
                value.size == 1 -> listOf(coordinate(value[0]).let { it to it })
                else -> listOf(point(value))
            }
        }
    }
}
