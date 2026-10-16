package org.autojs.plugin.batchrename.api

/**
 * Keys of the preset transactions [IBatchRenamePlugin.presets] and [IBatchRenamePlugin.importPresets]
 * ([BatchRenameValues.FEATURE_PRESETS]). Requests also carry [BatchRenameRequestKeys.CONTRACT_VERSION]; answers carry
 * [BatchRenameResultKeys.CONTRACT_VERSION] and, when the call failed, [BatchRenameResultKeys.ERROR_CODE] and
 * [BatchRenameResultKeys.ERROR_MESSAGE]. The plugin owns the presets and the JSON format of a preset document.
 *
 * zh-CN: 预设事务 [IBatchRenamePlugin.presets] 与 [IBatchRenamePlugin.importPresets] 的键. 请求同样携带契约版本; 应答携带
 * 契约版本, 失败时携带错误码与错误信息. 预设及预设文档的 JSON 格式归插件所有.
 */
object BatchRenamePresetKeys {

    /** String (importPresets request): the preset document, JSON as the plugin exports it. */
    const val DOCUMENT = "document"

    /** String (importPresets request): `PRESET_CONFLICT_*` of [BatchRenameValues]; default [BatchRenameValues.PRESET_CONFLICT_SKIP]. */
    const val ON_CONFLICT = "onConflict"

    /** ArrayList of Bundle (presets answer): every preset in the plugin's order, keyed by [NAME] and [OPTIONS]. */
    const val PRESETS = "presets"

    /** String: the name of a preset, at most [BatchRenameLimits.MAX_PRESET_NAME_CHARS] characters. */
    const val NAME = "name"

    /** Bundle: the complete options of a preset, keyed by [BatchRenameOptionKeys] without [BatchRenameOptionKeys.PRESET]. */
    const val OPTIONS = "options"

    /** ArrayList of String (importPresets answer): the names stored, in document order; renamed entries under their new name. */
    const val IMPORTED = "imported"

    /** ArrayList of String (importPresets answer): names kept unchanged because a preset of that name exists. */
    const val SKIPPED = "skipped"

    /** ArrayList of Bundle (importPresets answer): refused entries, keyed by [INDEX], [NAME] (when readable) and [REASON]. */
    const val REJECTED = "rejected"

    /** Int: position of a refused entry in the document's preset list. */
    const val INDEX = "index"

    /** String: English reason of a refused entry. */
    const val REASON = "reason"
}
