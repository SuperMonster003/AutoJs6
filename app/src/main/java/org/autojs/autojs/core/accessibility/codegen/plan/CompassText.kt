package org.autojs.autojs.core.accessibility.codegen.plan

/** The sole compass-string renderer for relation steps, shared by renderers and explanations.
 * zh-CN: 关系步骤的唯一罗盘字符串出口, 渲染器与理由文字共用. */
object CompassText {
    fun render(steps: List<PlanStep>): String = steps.joinToString("") { step ->
        when (step) {
            is PlanStep.Up -> "p${step.levels}"
            is PlanStep.Child -> "c${step.index}"
            is PlanStep.Sibling -> if (step.offset > 0) "s>${step.offset}" else "s<${-step.offset}"
            else -> error("Not a compass step")
        }
    }
}
