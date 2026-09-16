package org.autojs.autojs.storage.history

/**
 * Minimal line-based diff without external dependencies: Myers' O((N+M)D) algorithm in its
 * linear-space form (middle snake + divide and conquer), driven by an explicit work stack so deep
 * recursion cannot overflow. Lines are interned to ints before comparison.
 *
 * Callers are expected to check [exceedsLimits] first: the running time grows with the number of
 * differing lines, so very large or completely different inputs should degrade to statistics only.
 *
 * zh-CN: 无外部依赖的最小行级 diff: Myers O((N+M)D) 算法的线性空间形式 (中间蛇 + 分治), 用显式工作栈驱动
 * 以避免深递归溢出. 比较前先把行 intern 为整数.
 * 调用方应先用 [exceedsLimits] 检查: 运行时间随差异行数增长, 过大或完全不同的输入应退化为仅显示统计.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object LineDiff {

    enum class Kind { EQUAL, INSERT, DELETE }

    /**
     * One output line. [leftLineNo] is the 1-based line number in the left input (0 for INSERT),
     * [rightLineNo] the 1-based line number in the right input (0 for DELETE).
     * zh-CN: 一行输出. [leftLineNo] 为左侧 1 起始行号 (INSERT 时为 0), [rightLineNo] 为右侧 1 起始行号 (DELETE 时为 0).
     */
    data class Line(val kind: Kind, val text: String, val leftLineNo: Int, val rightLineNo: Int)

    data class Result(val lines: List<Line>, val inserted: Int, val deleted: Int, val unchanged: Int) {
        val hasChanges: Boolean get() = inserted > 0 || deleted > 0
    }

    const val DEFAULT_MAX_LINES_PER_SIDE: Int = 6_000
    const val DEFAULT_MAX_BYTES_PER_SIDE: Long = 1L * 1024L * 1024L

    /**
     * Split on LF, CRLF or CR. A trailing line terminator does not produce an extra empty line.
     * zh-CN: 按 LF / CRLF / CR 切分. 末尾的换行符不会产生多余的空行.
     */
    fun splitLines(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c == '\n' || c == '\r') {
                out.add(text.substring(start, i))
                if (c == '\r' && i + 1 < n && text[i + 1] == '\n') i++
                start = i + 1
            }
            i++
        }
        if (start < n) out.add(text.substring(start))
        return out
    }

    fun exceedsLimits(
        leftLines: Int,
        rightLines: Int,
        leftBytes: Long,
        rightBytes: Long,
        maxLinesPerSide: Int = DEFAULT_MAX_LINES_PER_SIDE,
        maxBytesPerSide: Long = DEFAULT_MAX_BYTES_PER_SIDE,
    ): Boolean {
        return leftLines > maxLinesPerSide || rightLines > maxLinesPerSide ||
            leftBytes > maxBytesPerSide || rightBytes > maxBytesPerSide
    }

    fun diff(left: List<String>, right: List<String>): Result {
        val ids = HashMap<String, Int>()
        val a = IntArray(left.size) { ids.getOrPut(left[it]) { ids.size } }
        val b = IntArray(right.size) { ids.getOrPut(right[it]) { ids.size } }
        val builder = Builder(left, right, a, b)
        builder.run()
        return Result(groupHunks(builder.out), builder.inserted, builder.deleted, builder.unchanged)
    }

    /**
     * Within each run of changed lines, list deletions before insertions (the conventional diff
     * layout); relative order on each side is preserved.
     * zh-CN: 每段变更内先列删除行再列新增行 (常规 diff 布局); 各侧内部的相对顺序不变.
     */
    private fun groupHunks(lines: List<Line>): List<Line> {
        val out = ArrayList<Line>(lines.size)
        var i = 0
        while (i < lines.size) {
            if (lines[i].kind == Kind.EQUAL) {
                out.add(lines[i])
                i++
                continue
            }
            var j = i
            while (j < lines.size && lines[j].kind != Kind.EQUAL) j++
            for (k in i until j) if (lines[k].kind == Kind.DELETE) out.add(lines[k])
            for (k in i until j) if (lines[k].kind == Kind.INSERT) out.add(lines[k])
            i = j
        }
        return out
    }

    private class Snake(val xStart: Int, val yStart: Int, val xEnd: Int, val yEnd: Int)

    private sealed class Task {
        class Solve(val aLo: Int, val aHi: Int, val bLo: Int, val bHi: Int) : Task()
        class Equal(val x: Int, val y: Int, val count: Int) : Task()
    }

    private class Builder(
        private val left: List<String>,
        private val right: List<String>,
        private val a: IntArray,
        private val b: IntArray,
    ) {
        val out = ArrayList<Line>(maxOf(a.size, b.size))
        var inserted = 0
        var deleted = 0
        var unchanged = 0

        // Scratch arrays for the middle-snake search, sized once for the whole problem.
        // zh-CN: 中间蛇搜索用的暂存数组, 按整个问题规模一次性分配.
        private val vf = IntArray(a.size + b.size + 4)
        private val vb = IntArray(a.size + b.size + 4)

        fun run() {
            val stack = ArrayDeque<Task>()
            stack.addLast(Task.Solve(0, a.size, 0, b.size))
            while (stack.isNotEmpty()) {
                when (val task = stack.removeLast()) {
                    is Task.Equal -> repeat(task.count) { i -> emitEqual(task.x + i, task.y + i) }
                    is Task.Solve -> solve(task, stack)
                }
            }
        }

        private fun emitEqual(x: Int, y: Int) {
            out.add(Line(Kind.EQUAL, left[x], x + 1, y + 1))
            unchanged++
        }

        private fun emitDelete(x: Int) {
            out.add(Line(Kind.DELETE, left[x], x + 1, 0))
            deleted++
        }

        private fun emitInsert(y: Int) {
            out.add(Line(Kind.INSERT, right[y], 0, y + 1))
            inserted++
        }

        private fun solve(task: Task.Solve, stack: ArrayDeque<Task>) {
            var aLo = task.aLo
            var bLo = task.bLo
            var aHi = task.aHi
            var bHi = task.bHi

            // Common prefix is emitted right away; the common suffix is queued behind the middle part.
            // zh-CN: 公共前缀直接输出; 公共后缀排在中间部分之后输出.
            while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) {
                emitEqual(aLo, bLo)
                aLo++
                bLo++
            }
            var suffix = 0
            while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) {
                aHi--
                bHi--
                suffix++
            }
            if (suffix > 0) stack.addLast(Task.Equal(aHi, bHi, suffix))

            if (aLo >= aHi) {
                for (y in bLo until bHi) emitInsert(y)
                return
            }
            if (bLo >= bHi) {
                for (x in aLo until aHi) emitDelete(x)
                return
            }

            val snake = middleSnake(aLo, aHi, bLo, bHi)
            // Pushed in reverse so that they pop as: left part, snake, right part.
            // zh-CN: 逆序压栈, 弹出顺序为: 左半部分, 蛇, 右半部分.
            stack.addLast(Task.Solve(snake.xEnd, aHi, snake.yEnd, bHi))
            if (snake.xEnd > snake.xStart) stack.addLast(Task.Equal(snake.xStart, snake.yStart, snake.xEnd - snake.xStart))
            stack.addLast(Task.Solve(aLo, snake.xStart, bLo, snake.yStart))
        }

        /**
         * Middle snake of the shortest edit script for a[aLo, aHi) vs b[bLo, bHi), in absolute
         * coordinates. Requires both ranges non-empty with no common prefix or suffix.
         * zh-CN: a[aLo, aHi) 与 b[bLo, bHi) 最短编辑脚本的中间蛇 (绝对坐标). 要求两侧均非空且无公共前后缀.
         */
        private fun middleSnake(aLo: Int, aHi: Int, bLo: Int, bHi: Int): Snake {
            val n = aHi - aLo
            val m = bHi - bLo
            val delta = n - m
            val odd = (delta and 1) != 0
            val max = (n + m + 1) / 2
            val off = max + 1
            vf[off + 1] = 0
            vb[off + 1] = 0

            for (d in 0..max) {
                var k = -d
                while (k <= d) {
                    var x = if (k == -d || (k != d && vf[off + k - 1] < vf[off + k + 1])) vf[off + k + 1] else vf[off + k - 1] + 1
                    var y = x - k
                    val xs = x
                    val ys = y
                    while (x < n && y < m && a[aLo + x] == b[bLo + y]) {
                        x++
                        y++
                    }
                    vf[off + k] = x
                    if (odd) {
                        val kb = delta - k
                        if (kb >= -(d - 1) && kb <= d - 1 && x + vb[off + kb] >= n) {
                            return Snake(aLo + xs, bLo + ys, aLo + x, bLo + y)
                        }
                    }
                    k += 2
                }

                k = -d
                while (k <= d) {
                    var x = if (k == -d || (k != d && vb[off + k - 1] < vb[off + k + 1])) vb[off + k + 1] else vb[off + k - 1] + 1
                    var y = x - k
                    val xs = x
                    val ys = y
                    while (x < n && y < m && a[aHi - 1 - x] == b[bHi - 1 - y]) {
                        x++
                        y++
                    }
                    vb[off + k] = x
                    if (!odd) {
                        val kf = delta - k
                        if (kf >= -d && kf <= d && vf[off + kf] + x >= n) {
                            // Reverse coordinates back: reversed (x', y') is original (n - x', m - y').
                            // zh-CN: 把反向坐标换算回来: 反向的 (x', y') 对应原坐标 (n - x', m - y').
                            return Snake(aLo + n - x, bLo + m - y, aLo + n - xs, bLo + m - ys)
                        }
                    }
                    k += 2
                }
            }
            throw IllegalStateException("Middle snake not found (n=$n, m=$m)")
        }
    }
}
