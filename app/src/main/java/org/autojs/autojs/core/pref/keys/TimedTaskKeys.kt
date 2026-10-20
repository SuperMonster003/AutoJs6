package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.TIMED_TASK
import org.autojs.autojs.core.pref.SettingEffect.APPLY
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs6.R

/**
 * Settings > Timed tasks (docs/settings/ROADMAP.md P2.4, appendix A.1 `timed_task`): the scheduling backend, which
 * takes effect through the existing rescheduling entry (`APPLY`, T12).
 *
 * zh-CN: 设置 > 定时任务 (ROADMAP P2.4, 附录 A.1 `timed_task`): 调度后端, 经现有的重排入口生效 (`APPLY`, T12).
 */
object TimedTaskKeys : SettingsDomain {

    private val TIMED_TASK_BACKEND_VALUES = intArrayOf(
        R.string.key_timed_task_backend_alarm,
        R.string.key_timed_task_backend_work,
        R.string.key_timed_task_backend_job,
    )

    /** `TimedTaskScheduler.init` reads the stored key and rejects a key out of the candidates. */
    @JvmField
    val TIMED_TASK_BACKEND = SettingKey.Enum(
        R.string.key_timed_task_backend, TIMED_TASK, keyChoices(*TIMED_TASK_BACKEND_VALUES),
        keyResource(R.string.default_key_timed_task_backend, "default_key_timed_task_backend"), APPLY,
    )

    override val keys: List<PrefKey<*>> = listOf(TIMED_TASK_BACKEND)

    override val names: List<RegisteredName> = valueConstants(*TIMED_TASK_BACKEND_VALUES)
}
