package org.autojs.autojs.ui.floating.codegen

import android.content.Context
import org.autojs.autojs.core.accessibility.codegen.CodeGenFailure
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.TargetSet
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.PlanReceiver
import org.autojs.autojs.core.accessibility.codegen.plan.PlanStep
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorText
import org.autojs.autojs.core.accessibility.codegen.rank.Explanation
import org.autojs.autojs.core.accessibility.codegen.verify.Rejected
import org.autojs.autojs.core.accessibility.codegen.verify.RejectionReason
import org.autojs.autojs6.R

/** Localizes structured engine facts; diagnostic strings are never shown to the user.
 * zh-CN: 本地化引擎的结构化事实, 不向用户显示诊断字符串. */
class CodeGenText(private val context: Context) {

    fun targetSet(targetSet: TargetSet<*>): String = targetSet(targetSet.description)

    fun targetSet(description: TargetSet.Description): String = message(
        when (description.key) {
            TargetSet.Kind.FULL_ID_CLASS -> R.string.text_codegen_target_full_id_class
            TargetSet.Kind.REPEATED_CONTAINER -> R.string.text_codegen_target_repeated_container
            TargetSet.Kind.SAME_TEXT -> R.string.text_codegen_target_same_text
        }, description.arguments,
    )

    fun requestError(error: CodeGenRequest.Error): String = context.getString(
        when (error) {
            CodeGenRequest.Error.EMPTY_TARGETS -> R.string.text_codegen_error_empty_targets
            CodeGenRequest.Error.SINGLE_REQUIRES_ONE_TARGET -> R.string.text_codegen_error_single_target
            CodeGenRequest.Error.TIMEOUT_OUT_OF_RANGE -> R.string.text_codegen_timeout_error
            CodeGenRequest.Error.TARGET_NOT_EDITABLE -> R.string.text_codegen_error_not_editable
        },
    )

    fun failure(failure: CodeGenFailure): String = buildList {
        add(context.getString(when (failure.reason) {
            CodeGenFailure.Reason.INVALID_REQUEST -> R.string.text_codegen_failure_invalid_request
            CodeGenFailure.Reason.NO_CANDIDATES -> R.string.text_codegen_failure_no_candidates
            CodeGenFailure.Reason.ALL_REJECTED -> R.string.text_codegen_failure_all_rejected
            CodeGenFailure.Reason.CANCELLED -> R.string.text_codegen_failure_cancelled
            CodeGenFailure.Reason.BUDGET_EXCEEDED -> R.string.text_codegen_failure_budget_exceeded
        }))
        addAll(failure.requestErrors.map(::requestError))
        failure.closestRejection?.let { add(rejection(it)) }
    }.distinct().joinToString("\n")

    fun rejection(rejected: Rejected): String = when (rejected.reason) {
        RejectionReason.EXTRA_TARGETS -> context.getString(R.string.text_codegen_rejection_extra_targets, rejected.extraCount)
        RejectionReason.MISSING_TARGETS -> context.getString(R.string.text_codegen_rejection_missing_targets, rejected.missingCount)
        RejectionReason.TARGET_SET_MISMATCH -> context.getString(
            R.string.text_codegen_rejection_target_set_mismatch, rejected.extraCount, rejected.missingCount,
        )
        else -> context.getString(when (rejected.reason) {
            RejectionReason.INVALID_REQUEST -> R.string.text_codegen_failure_invalid_request
            RejectionReason.TARGET_OUTSIDE_CAPTURE -> R.string.text_codegen_rejection_target_outside_capture
            RejectionReason.CARDINALITY_MISMATCH -> R.string.text_codegen_rejection_cardinality_mismatch
            RejectionReason.FORBIDDEN_ATTRIBUTE -> R.string.text_codegen_rejection_forbidden_attribute
            RejectionReason.INVALID_SELECTOR -> R.string.text_codegen_rejection_invalid_selector
            RejectionReason.EMPTY_RESULT -> R.string.text_codegen_rejection_empty_result
            RejectionReason.ANCHOR_NOT_FOUND -> R.string.text_codegen_rejection_anchor_not_found
            RejectionReason.AMBIGUOUS_ANCHOR -> R.string.text_codegen_rejection_ambiguous_anchor
            RejectionReason.INDEX_OUT_OF_RANGE -> R.string.text_codegen_rejection_index_out_of_range
            RejectionReason.UNREACHABLE_PATH -> R.string.text_codegen_rejection_unreachable_path
            RejectionReason.WRONG_TARGET -> R.string.text_codegen_rejection_wrong_target
            RejectionReason.RECEIVER_STRATEGY_MISMATCH -> R.string.text_codegen_rejection_receiver_strategy_mismatch
            RejectionReason.RECEIVER_NOT_FOUND -> R.string.text_codegen_rejection_receiver_not_found
            RejectionReason.UNSUPPORTED_ACTION -> R.string.text_codegen_rejection_unsupported_action
            else -> error("Counted rejection is handled above")
        })
    }

    fun explanation(line: Explanation.Line): String = line.messages.joinToString("; ") { explanation(it) }

    fun explanation(lines: List<Explanation.Line>): String = lines.joinToString("\n") { explanation(it) }

    fun explanation(value: Explanation.Message): String = message(
        when (value.key) {
            Explanation.Key.SELF_UNIQUE -> R.string.text_codegen_explanation_self_unique
            Explanation.Key.SELF_FIRST_MATCH -> R.string.text_codegen_explanation_self_first_match
            Explanation.Key.SELF_COLLECTION -> R.string.text_codegen_explanation_self_collection
            Explanation.Key.INDEXED_MATCH -> R.string.text_codegen_explanation_indexed_match
            Explanation.Key.INDEXED_MATCH_FROM_END -> R.string.text_codegen_explanation_indexed_match_from_end
            Explanation.Key.ANCHOR_PATH -> R.string.text_codegen_explanation_anchor_path
            Explanation.Key.ANCHOR_SEARCH_UNIQUE -> R.string.text_codegen_explanation_anchor_search_unique
            Explanation.Key.ANCHOR_SEARCH_FIRST_MATCH -> R.string.text_codegen_explanation_anchor_search_first_match
            Explanation.Key.CONTAINER_ITEMS -> R.string.text_codegen_explanation_container_items
            Explanation.Key.RELATIVE_UNIQUE -> R.string.text_codegen_explanation_relative_unique
            Explanation.Key.RELATIVE_FIRST_MATCH -> R.string.text_codegen_explanation_relative_first_match
            Explanation.Key.RELATIVE_COLLECTION -> R.string.text_codegen_explanation_relative_collection
            Explanation.Key.ORDER_DEPENDENT -> R.string.text_codegen_explanation_order_dependent
            Explanation.Key.DYNAMIC_TEXT -> R.string.text_codegen_explanation_dynamic_text
            Explanation.Key.DYNAMIC_DESCRIPTION -> R.string.text_codegen_explanation_dynamic_description
            Explanation.Key.STRUCTURAL_CONDITIONS -> R.string.text_codegen_explanation_structural_conditions
            Explanation.Key.FIXED_PATH -> R.string.text_codegen_explanation_fixed_path
            Explanation.Key.FIXED_RELATION -> R.string.text_codegen_explanation_fixed_relation
            Explanation.Key.LONG_PATH -> R.string.text_codegen_explanation_long_path
            Explanation.Key.LARGE_INDEX -> R.string.text_codegen_explanation_large_index
            Explanation.Key.RECEIVER_ANCESTOR -> R.string.text_codegen_explanation_receiver_ancestor
            Explanation.Key.ACTIVE_WINDOW_ONLY -> R.string.text_codegen_explanation_active_window_only
        }, value.arguments,
    )

    fun family(family: LocatePlan.Family): String = context.getString(when (family) {
        LocatePlan.Family.SELF -> R.string.text_codegen_family_self
        LocatePlan.Family.SELF_INDEX -> R.string.text_codegen_family_self_index
        LocatePlan.Family.ANCHOR_PATH -> R.string.text_codegen_family_anchor_path
        LocatePlan.Family.ANCHOR_SEARCH -> R.string.text_codegen_family_anchor_search
        LocatePlan.Family.CONTAINER_ITEMS -> R.string.text_codegen_family_container_items
        LocatePlan.Family.SELF_RELATIVE -> R.string.text_codegen_family_self_relative
    })

    fun candidateSummary(plan: LocatePlan): String {
        val details = buildString {
            append(SelectorText.render(plan.selector))
            plan.steps.forEach { step ->
                when (step) {
                    is PlanStep.Index -> append(".findOnce(").append(step.index).append(')')
                    is PlanStep.Up -> append(" > parent(").append(step.levels).append(')')
                    is PlanStep.Child -> append(" > child(").append(step.index).append(')')
                    is PlanStep.Sibling -> append(" > sibling(").append(if (step.offset > 0) "+" else "").append(step.offset).append(')')
                    is PlanStep.Search -> append(" > ").append(SelectorText.render(step.selector))
                    is PlanStep.Match -> append(" ~ ").append(SelectorText.render(step.selector))
                }
            }
            (plan.receiver as? PlanReceiver.Up)?.let {
                append("; ").append(context.getString(R.string.text_codegen_summary_receiver, it.levels))
            }
        }
        return context.getString(R.string.text_codegen_summary, family(plan.family), details)
    }

    fun matchCount(count: Int, collection: Boolean): String = context.getString(
        if (collection) R.string.text_codegen_matches_collection else R.string.text_codegen_matches_single, count,
    )

    private fun message(resource: Int, arguments: List<String>): String =
        context.getString(resource, *arguments.toTypedArray())

    companion object {
        fun explanation(context: Context, lines: List<Explanation.Line>): String = CodeGenText(context).explanation(lines)
        fun candidateSummary(context: Context, plan: LocatePlan): String = CodeGenText(context).candidateSummary(plan)
        fun matchCount(context: Context, count: Int, collection: Boolean): String = CodeGenText(context).matchCount(count, collection)
    }
}
