package org.autojs.autojs.core.accessibility.codegen.features

import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import java.security.MessageDigest
import java.util.Collections

/**
 * The shared host/training feature contract. Encoding changes require a new schema version. The
 * host extracts the current schema and can project it onto an earlier supported schema for models
 * trained before a family existed; such models never rank the families they have not seen.
 * zh-CN: 宿主与训练共用的特征约定. 修改编码必须升级 schema 版本. 宿主按当前 schema 提取特征, 并可投影到
 * 更早的受支持 schema, 供某个计划族出现之前训练的模型使用; 这类模型不会为其未见过的族排序.
 */
object FeatureSchema {
    const val featureSchemaVersion = 2
    val SUPPORTED_SCHEMA: IntRange = 1..2

    private val version1: List<String> = listOf(
        "scope_collection",
        "action_none", "action_click", "action_long_click", "action_set_text", "action_scroll_forward", "action_scroll_backward",
        "attr_id", "attr_text", "attr_desc",
        "family_self", "family_self_index", "family_anchor_path", "family_anchor_search", "family_container_items",
        "receiver_up_levels",
        "self_cond_count", "self_has_full_id", "self_has_entry_id", "self_has_text", "self_has_desc", "self_has_class", "self_has_package", "self_structural_cond_count",
        "anchor_cond_count", "anchor_has_full_id", "anchor_has_entry_id", "anchor_has_text", "anchor_has_desc", "anchor_has_class", "anchor_has_package", "anchor_structural_cond_count",
        "id_entry_len", "id_looks_obfuscated", "id_cross_package_dup",
        "text_len", "text_digit_ratio", "text_dynamic", "text_cjk_ratio", "desc_len", "desc_dynamic",
        "index_abs", "index_negative", "index_rel", "selector_match_count",
        "path_steps", "path_up_levels", "path_child_steps", "path_sibling_steps", "path_max_child_index", "anchor_distance",
        "container_item_count", "container_item_uniform",
        "target_depth", "target_has_id", "target_has_text", "target_clickable", "target_in_list", "target_same_class_siblings", "target_set_size",
        "rule_rank_norm", "rule_cost_gap", "group_size", "family_rank_in_group",
    )

    private val version2: List<String> = listOf(
        "scope_collection",
        "action_none", "action_click", "action_long_click", "action_set_text", "action_scroll_forward", "action_scroll_backward",
        "attr_id", "attr_text", "attr_desc",
        "family_self", "family_self_index", "family_anchor_path", "family_anchor_search", "family_container_items", "family_self_relative",
        "receiver_up_levels",
        "self_cond_count", "self_has_full_id", "self_has_entry_id", "self_has_text", "self_has_desc", "self_has_class", "self_has_package", "self_structural_cond_count",
        "anchor_cond_count", "anchor_has_full_id", "anchor_has_entry_id", "anchor_has_text", "anchor_has_desc", "anchor_has_class", "anchor_has_package", "anchor_structural_cond_count",
        "relative_cond_count", "relative_has_full_id", "relative_has_entry_id", "relative_has_text", "relative_has_desc", "relative_has_class", "relative_has_package", "relative_structural_cond_count",
        "id_entry_len", "id_looks_obfuscated", "id_cross_package_dup",
        "text_len", "text_digit_ratio", "text_dynamic", "text_cjk_ratio", "desc_len", "desc_dynamic",
        "index_abs", "index_negative", "index_rel", "selector_match_count", "relative_match_count", "relative_match_ratio",
        "path_steps", "path_up_levels", "path_child_steps", "path_sibling_steps", "path_max_child_index", "anchor_distance",
        "container_item_count", "container_item_uniform",
        "target_depth", "target_has_id", "target_has_text", "target_clickable", "target_in_list", "target_same_class_siblings", "target_set_size",
        "rule_rank_norm", "rule_cost_gap", "group_size", "family_rank_in_group",
    )

    private val versions: Map<Int, List<String>> = mapOf(1 to version1, 2 to version2)
        .mapValues { Collections.unmodifiableList(it.value) }

    /** The families a model trained on each schema has seen. zh-CN: 各 schema 上训练的模型见过的计划族. */
    private val familiesByVersion: Map<Int, Set<LocatePlan.Family>> = mapOf(
        1 to setOf(LocatePlan.Family.SELF, LocatePlan.Family.SELF_INDEX, LocatePlan.Family.ANCHOR_PATH, LocatePlan.Family.ANCHOR_SEARCH, LocatePlan.Family.CONTAINER_ITEMS),
        2 to LocatePlan.Family.entries.toSet(),
    )

    val names: List<String> = names(featureSchemaVersion)
    val namesHash: String = namesHash(featureSchemaVersion)

    fun names(version: Int): List<String> = requireNotNull(versions[version]) { "Unsupported feature schema version: $version" }

    fun namesHash(version: Int): String = MessageDigest.getInstance("SHA-256")
        .digest(names(version).joinToString("\n").toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** The names hash of a supported [version], or null for an unknown one. zh-CN: 受支持 [version] 的名称哈希, 未知版本为 null. */
    fun namesHashOrNull(version: Int): String? = if (version in versions) namesHash(version) else null

    fun families(version: Int): Set<LocatePlan.Family> = requireNotNull(familiesByVersion[version]) { "Unsupported feature schema version: $version" }

    /** Positions in the current vector of each column of [version], in that version's order.
     * zh-CN: [version] 各列在当前特征向量中的位置, 按该版本的顺序排列. */
    fun projection(version: Int): IntArray {
        val current = names.withIndex().associate { it.value to it.index }
        return names(version).map { requireNotNull(current[it]) { "Feature $it is not in the current schema" } }.toIntArray()
    }

    /** Projects a current-schema vector onto [version]. zh-CN: 将当前 schema 的向量投影到 [version]. */
    fun project(vector: FloatArray, version: Int): FloatArray {
        require(vector.size == names.size) { "Feature vector length differs from the current schema" }
        if (version == featureSchemaVersion) return vector
        val indices = projection(version)
        return FloatArray(indices.size) { vector[indices[it]] }
    }
}
