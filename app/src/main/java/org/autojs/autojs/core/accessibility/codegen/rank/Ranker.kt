package org.autojs.autojs.core.accessibility.codegen.rank

import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.UiNode

/** Sorts only verified plans; scoring must preserve the input candidates and their meaning.
 * zh-CN: 只排序通过验证的计划, 打分不得改变输入候选集合及其含义. */
interface Ranker {
    fun <N : UiNode<N>> rank(request: CodeGenRequest<N>, verified: List<Verified<N>>): List<Ranked<N>>
}

/** Higher scores rank first. Scores compare plans; they are not success probabilities.
 * zh-CN: 分数越高越靠前, 仅用于比较计划, 不是成功概率. */
class Ranked<N : UiNode<N>>(val verified: Verified<N>, val score: Double) {
    val plan get() = verified.plan
    val resolved get() = verified.resolved
    val facts get() = verified.facts
}
