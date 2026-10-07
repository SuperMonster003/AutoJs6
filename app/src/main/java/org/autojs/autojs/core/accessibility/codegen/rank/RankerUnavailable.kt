package org.autojs.autojs.core.accessibility.codegen.rank

/** An expected model-ranking failure; the caller selects the rule fallback in P7.
 * zh-CN: 模型排序不可用的结构化原因; P7 由调用方选择规则回退. */
class RankerUnavailable(val reason: Reason, cause: Exception? = null) : RuntimeException(reason.name, cause) {
    enum class Reason { INCOMPATIBLE_FEATURES, TIMEOUT, FEATURE_EXTRACTION_FAILED, SCORING_FAILED, SORTING_FAILED }
}
