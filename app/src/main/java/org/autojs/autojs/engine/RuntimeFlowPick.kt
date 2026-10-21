package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.PickResultType
import org.json.JSONArray
import org.json.JSONObject

/** Scoped selector reads use private nodes, the core compass and the core result alias table. */
internal class RuntimeFlowPick(private val arena: RuntimeFlowNodes) {
    fun probe(target: JSONObject, options: JSONObject): Any? {
        require(options.keys().asSequence().all { it in setOf("root", "compass", "resultType") }) { "Unknown Flow scope option" }
        val compass = RuntimeFlowJson.nullable(options.opt("compass"))?.also {
            require(it is String && Compass.isCompass(it)) { "Invalid Flow compass" }
        } as? String
        val requested = RuntimeFlowJson.plain(options.opt("resultType")) ?: "#"
        val type = PickResultType.of(requested)
        val root = RuntimeFlowJson.nullable(options.opt("root"))?.let {
            require(it is JSONObject && it.has("windowId")) { "Flow scope root must be a node snapshot" }
            arena.reference(it)
        }
        val direct = target.has("windowId")
        val filter = if (direct) null else RuntimeFlowQuery.filter(target)
        fun nodes(all: Boolean): List<RuntimeFlowNode> = if (direct) listOf(arena.reference(target)) else {
            arena.search(target, root, all).filter { it.value.refresh() && filter!!.filter(it) }
        }
        fun navigated(all: Boolean) = nodes(all).mapNotNull { Compass.navigate(it, compass) }
        fun node() = navigated(false).firstOrNull()
        fun shape(type: PickResultType): Any? = when (type) {
            PickResultType.Nothing -> null
            PickResultType.Widget -> node()
            PickResultType.Widgets, PickResultType.WidgetCollection -> navigated(true)
            PickResultType.Content -> node()?.value?.content().orEmpty()
            PickResultType.Contents -> navigated(true).map { it.value.content() }
            PickResultType.Point -> node()?.let(::point)
            PickResultType.Points -> navigated(true).map(::point)
            PickResultType.Existence -> node() != null
            PickResultType.Selector -> {
                require(compass.isNullOrEmpty() || compass == Compass.PASS_ON) { "Selector resultType cannot use a compass" }
                require(!direct) { "A snapshot is not a selector descriptor" }
                target
            }
            else -> throw IllegalArgumentException("Unsupported portable resultType: $requested")
        }
        val result = if (type is PickResultType.Invocation) {
            val plural = if (requested is List<*> && type.arguments.isEmpty() && type.name !in READS) PickResultType.arrayFormOf(type.name) else null
            if (plural != null) shape(plural) else {
                require(type.name in READS) { "Unsupported portable node projection: ${type.name}" }
                val count = if (type.name in setOf("child", "compass")) 1 else 0
                require(type.arguments.size == count) { "Invalid projection arguments for ${type.name}" }
                val argument = type.arguments.firstOrNull()
                if (type.name == "child") require(argument is Number && argument.toDouble() == argument.toInt().toDouble()) { "child index must be an integer" }
                if (type.name == "compass") require(argument is String && Compass.isCompass(argument)) { "Invalid projection compass" }
                node()?.let { value ->
                    when (type.name) {
                        "child" -> (argument as Number).toInt().let { if (it in 0 until value.childCount()) value.child(it) else null }
                        "parent" -> value.parent()
                        "compass" -> Compass.navigate(value, argument as String)
                        "point" -> point(value)
                        "content" -> value.value.content()
                        "childCount" -> value.childCount()
                        "width" -> value.width(); "height" -> value.height()
                        "centerX" -> value.centerX(); "centerY" -> value.centerY()
                        "left" -> value.left(); "top" -> value.top(); "right" -> value.right(); "bottom" -> value.bottom()
                        else -> RuntimeFlowQuery.snapshot(value.value).opt(type.name)
                    }
                }
            }
        } else shape(type)
        // Selector waits follow Rhino pickup truthiness, distinct from predicate truthiness.
        return result.takeUnless { it == null || it === JSONObject.NULL || it == false || it is String && it.isEmpty() || it is List<*> && it.isEmpty() }
    }

    companion object {
        private val READS = setOf("text", "desc", "id", "className", "packageName", "bounds", "depth", "indexInParent", "clickable", "enabled", "scrollable", "visibleToUser", "checked", "checkable", "editable", "focused", "selected", "parent", "child", "childCount", "compass", "point", "content", "width", "height", "centerX", "centerY", "left", "top", "right", "bottom")
        private fun point(node: RuntimeFlowNode) = mapOf("x" to node.centerX(), "y" to node.centerY())
        fun json(value: Any?): Any = when (value) {
            null -> JSONObject.NULL
            is RuntimeFlowNode -> RuntimeFlowQuery.snapshot(value.value)
            is List<*> -> JSONArray().also { out -> value.forEach { out.put(json(it)) } }
            is Map<*, *> -> JSONObject().also { out -> value.forEach { (key, item) -> out.put(key.toString(), json(item)) } }
            else -> value
        }
    }
}
