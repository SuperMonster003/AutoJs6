package org.autojs.autojs.core.accessibility.codegen.model

/** Compact immutable symmetric trees, with CatBoost's strict threshold and leaf-bit ordering.
 * Scoring performs no allocations; validation and defensive array copies happen at construction.
 * zh-CN: 紧凑且不可变的对称树, 使用 CatBoost 的严格阈值比较与叶索引位序; 打分过程不分配对象. */
class ObliviousTreeModel(
    val featureCount: Int,
    depths: ByteArray,
    splitOffsets: IntArray,
    leafOffsets: IntArray,
    featureIndices: IntArray,
    thresholds: FloatArray,
    leafValues: FloatArray,
    val bias: Double,
    val scale: Double,
) {
    private val depths = depths.copyOf()
    private val splitOffsets = splitOffsets.copyOf()
    private val leafOffsets = leafOffsets.copyOf()
    private val featureIndices = featureIndices.copyOf()
    private val thresholds = thresholds.copyOf()
    private val leafValues = leafValues.copyOf()

    val treeCount: Int get() = depths.size
    val maxDepth: Int

    /** Exact backing-array payload bytes, excluding JVM object headers and scalar fields.
     * zh-CN: 内部数组数据的精确字节总数, 不含 JVM 对象头与标量字段. */
    val storageBytes: Long = this.depths.size.toLong() +
        4L * (this.splitOffsets.size + this.leafOffsets.size + this.featureIndices.size + this.thresholds.size + this.leafValues.size)

    init {
        require(featureCount in 1..65535) { "Feature count must fit the model format" }
        require(this.depths.size in 1..4096) { "Tree count must be between 1 and 4096" }
        require(this.splitOffsets.size == treeCount && this.leafOffsets.size == treeCount) { "One offset per tree is required" }
        require(this.featureIndices.size == this.thresholds.size) { "Split feature/threshold lengths differ" }
        require(bias.isFinite() && scale.isFinite()) { "Model bias and scale must be finite" }
        var nextSplit = 0
        var nextLeaf = 0
        var maximum = 0
        for (tree in this.depths.indices) {
            val depth = this.depths[tree].toInt()
            require(depth in 1..8) { "Tree depth must be between 1 and 8" }
            require(this.splitOffsets[tree] == nextSplit && this.leafOffsets[tree] == nextLeaf) { "Tree offsets must be contiguous" }
            nextSplit += depth
            nextLeaf += 1 shl depth
            maximum = maxOf(maximum, depth)
        }
        require(nextSplit == this.featureIndices.size && nextLeaf == this.leafValues.size) { "Tree payload lengths differ from depths" }
        require(this.featureIndices.all { it in 0 until featureCount }) { "Split feature is outside the schema" }
        require(this.thresholds.all(Float::isFinite) && this.leafValues.all(Float::isFinite)) { "Model payload must be finite" }
        maxDepth = maximum
    }

    fun score(features: FloatArray): Double {
        require(features.size == featureCount) { "Feature vector length differs from model" }
        var index = 0
        while (index < features.size) {
            require(features[index].isFinite()) { "Feature vector must be finite" }
            index++
        }
        var sum = 0.0
        var tree = 0
        while (tree < depths.size) {
            var leaf = 0
            var bit = 0
            val offset = splitOffsets[tree]
            while (bit < depths[tree].toInt()) {
                val split = offset + bit
                if (features[featureIndices[split]] > thresholds[split]) leaf = leaf or (1 shl bit)
                bit++
            }
            sum += leafValues[leafOffsets[tree] + leaf].toDouble()
            tree++
        }
        val result = bias + scale * sum
        check(result.isFinite()) { "Model score overflow" }
        return result
    }
}
