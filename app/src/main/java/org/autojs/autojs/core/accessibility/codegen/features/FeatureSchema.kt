package org.autojs.autojs.core.accessibility.codegen.features

import java.security.MessageDigest
import java.util.Collections

/** The shared host/training feature contract. Encoding changes require a new schema version.
 * zh-CN: 宿主与训练共用的特征约定. 修改编码必须升级 schema 版本. */
object FeatureSchema {
    const val featureSchemaVersion = 1
    val SUPPORTED_SCHEMA: IntRange = 1..1

    val names: List<String> = Collections.unmodifiableList(listOf(
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
    ))

    val namesHash: String = MessageDigest.getInstance("SHA-256")
        .digest(names.joinToString("\n").toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
