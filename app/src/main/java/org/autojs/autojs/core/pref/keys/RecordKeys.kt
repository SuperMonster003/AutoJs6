package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.RECORD
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Record scripts (docs/settings/ROADMAP.md P2.4, appendix A.1 `record`): the volume key, the toast and the
 * output file type of root recording.
 *
 * zh-CN: 设置 > 录制脚本 (ROADMAP P2.4, 附录 A.1 `record`): 音量键, 提示与 Root 录制的输出文件类型.
 */
object RecordKeys : SettingsDomain {

    private val ROOT_RECORD_OUT_FILE_TYPE_VALUES = intArrayOf(
        R.string.key_root_record_out_file_type_binary,
        R.string.key_root_record_out_file_type_js,
    )

    @JvmField
    val USE_VOLUME_CONTROL_RECORD = SettingKey.Bool(
        R.string.key_use_volume_control_record, RECORD,
        boolResource(R.bool.pref_use_volume_control_record, "pref_use_volume_control_record"),
    )

    @JvmField
    val RECORD_TOAST = SettingKey.Bool(R.string.key_record_toast, RECORD, boolResource(R.bool.pref_record_toast, "pref_record_toast"))

    @JvmField
    val ROOT_RECORD_OUT_FILE_TYPE = SettingKey.Enum(
        R.string.key_root_record_out_file_type, RECORD, keyChoices(*ROOT_RECORD_OUT_FILE_TYPE_VALUES),
        keyResource(R.string.default_key_root_record_out_file_type, "default_key_root_record_out_file_type"),
    )

    override val keys: List<PrefKey<*>> = listOf(USE_VOLUME_CONTROL_RECORD, RECORD_TOAST, ROOT_RECORD_OUT_FILE_TYPE)

    override val names: List<RegisteredName> = valueConstants(*ROOT_RECORD_OUT_FILE_TYPE_VALUES)

    /** The former `Pref.rootRecordGeneratesBinary`: only the "binary" key generates a binary file (R2). */
    @JvmStatic
    fun rootRecordGeneratesBinary(): Boolean = ROOT_RECORD_OUT_FILE_TYPE.storedKeyOrDefault() == keyOf(R.string.key_root_record_out_file_type_binary)
}
