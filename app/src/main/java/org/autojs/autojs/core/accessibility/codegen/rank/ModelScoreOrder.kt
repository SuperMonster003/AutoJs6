package org.autojs.autojs.core.accessibility.codegen.rank

/** Shared with model parity tests; the Python evaluator uses the identical batching policy.
 * zh-CN: 与模型一致性测试共用, Python 评估器采用完全相同的分批规则. */
internal object ModelScoreOrder {
    fun order(scores: DoubleArray, ruleRanks: IntArray, checkpoint: () -> Unit = {}): IntArray {
        require(scores.size == ruleRanks.size) { "One rule rank per model score is required" }
        require(scores.all(Double::isFinite)) { "Model scores must be finite" }
        require(ruleRanks.toSet() == scores.indices.toSet()) { "Rule ranks must cover the whole group exactly once" }
        checkpoint()
        val indices = scores.indices.sortedWith(compareByDescending<Int> { scores[it] }.thenBy { ruleRanks[it] }).toMutableList()
        checkpoint()
        var start = 0
        while (start < indices.size) {
            var end = start + 1
            val maximum = scores[indices[start]]
            while (end < indices.size && maximum - scores[indices[end]] < ModelRanker.SCORE_EPSILON) end++
            indices.subList(start, end).sortBy { ruleRanks[it] }
            start = end
            checkpoint()
        }
        return indices.toIntArray()
    }
}
