package org.autojs.autojs.core.automator.flow

/** Script call sites retained across asynchronous Flow boundaries, without retaining nodes or scopes.
 * zh-CN: 跨异步 Flow 边界保留的脚本调用位置, 不持有节点或脚本作用域. */
internal class FlowTaskStack private constructor() : RuntimeException("Flow task stack", null, false, false) {

    private val frames = LinkedHashSet<StackTraceElement>()

    @Synchronized
    private fun append(sites: List<StackTraceElement>) {
        sites.forEach { if (frames.size < MAX_FRAMES) frames += it }
    }

    @Synchronized
    private fun render(): String = buildString {
        append("Flow task stack:")
        frames.forEach { frame ->
            append("\n    at ")
            if (frame.className.isNotEmpty()) append(frame.className).append('.')
            append(frame.methodName).append(" (").append(frame.fileName ?: "<unknown>")
            if (frame.lineNumber > 0) append(':').append(frame.lineNumber)
            append(')')
        }
        if (frames.size == MAX_FRAMES) append("\n    ... task stack limited to $MAX_FRAMES frames")
    }

    companion object {
        private const val MAX_FRAMES = 64

        /** Passing a rejection through skipped steps must not attribute it to those steps.
         * zh-CN: 拒绝透传经过跳过的步骤时, 不把错误归因到这些步骤. */
        fun record(node: FlowNode, error: Throwable) {
            if ((node.parent?.stateInternal as? FlowNode.State.Rejected)?.error === error) return
            val sites = ArrayList<StackTraceElement>()
            var current: FlowNode? = node
            var visited = 0
            while (current != null && sites.size < MAX_FRAMES && visited++ < MAX_FRAMES) {
                val source = current.sourceStack
                source.firstOrNull()?.let { first ->
                    sites += StackTraceElement("Flow", current.step.name, first.fileName, first.lineNumber)
                    sites += source.filter { it.methodName != "<script>" }
                }
                current = current.parent
            }
            if (sites.isEmpty()) return
            synchronized(error) {
                val trace = error.suppressed.filterIsInstance<FlowTaskStack>().firstOrNull()
                    ?: FlowTaskStack().also { error.addSuppressed(it) }
                trace.append(sites)
            }
        }

        fun format(error: Throwable): String? = error.suppressed.filterIsInstance<FlowTaskStack>().firstOrNull()?.render()
    }
}
