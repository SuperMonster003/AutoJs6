package org.autojs.autojs.ui.settings.transfer

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.archive.SettingsArchiveException

/**
 * The state of a settings wizard (docs/settings/ROADMAP.md T10): which wizard runs, the step it shows and every choice
 * made so far. Pure data (R3): the page saves it with its instance state as JSON text ([toText] / [fromText]), so after
 * the activity is recreated (rotation, process death) the wizard continues at the same step with the same choices.
 * Steps update the state of the page whenever a choice changes, not only when they are left.
 *
 * [selected] are category ids in page order (appendix A.1). The fields after it belong to the later steps of the export
 * wizard (P6.4): the save location was requested ([locationRequested]) and chosen ([document], a content URI), and the
 * result of writing the archive ([fileName], [categories], [entries], [unexportable] values, or the [failure]). The
 * import wizard (P7.5) uses [locationRequested] for the system page that opens a document, [archive] for the local copy
 * of the chosen file and [fileName] for its name, [deploy] for the deployment items the user confirmed (P7.6, null
 * before the confirmation); the import, the undo and the reset (P8.1) keep the id of their running apply in [operation]
 * ([SettingsOperations]) and what it did in [outcome].
 *
 * zh-CN: 设置向导的状态 (ROADMAP T10): 运行的是哪个向导, 当前步骤, 以及到目前为止的全部选择. 纯数据 (R3): 页面把它作为 JSON
 * 文本 ([toText] / [fromText]) 保存在实例状态中, 因此 Activity 重建 (旋转屏幕, 进程被回收) 后向导停留在同一步骤, 选择不变.
 * 步骤在选择每次变化时都更新页面持有的状态, 而不只是在离开时. [selected] 为按页面顺序 (附录 A.1) 排列的分类 id. 其后的字段
 * 属于导出向导的后续步骤 (P6.4): 是否已请求 ([locationRequested]) 并选定保存位置 ([document], content URI), 以及写入归档的
 * 结果 ([fileName], [categories], [entries], [unexportable] 个值, 或 [failure]). 导入向导 (P7.5) 以 [locationRequested] 记录
 * 是否已打开系统的打开文档页面, [archive] 为所选文件的本地副本, [fileName] 为其文件名, [deploy] 为用户确认的部署项 (P7.6, 确认之前
 * 为 null); 导入, 撤销与恢复默认 (P8.1) 把运行中的应用操作的 id 记在 [operation] ([SettingsOperations]), 其结果记在 [outcome].
 */
data class SettingsWizardState(
    val kind: Kind,
    val step: Step,
    val selected: List<String> = emptyList(),
    val credentialsConfirmed: Boolean = false,
    val locationRequested: Boolean = false,
    val document: String? = null,
    val fileName: String? = null,
    val categories: Int = 0,
    val entries: Int = 0,
    val unexportable: Int = 0,
    val failure: Failure? = null,
    val failureDetail: String? = null,
    val archive: String? = null,
    val deploy: List<String>? = null,
    val operation: String? = null,
    val outcome: SettingsTransferOutcome? = null,
) {

    enum class Kind(val id: String) {
        EXPORT("export"),
        IMPORT("import"),
        UNDO("undo"),
        RESET("reset"),
    }

    /** The steps of the wizards; each wizard shows the ones it lists, in its order. zh-CN: 各向导的步骤, 由向导按自己的顺序选用. */
    enum class Step(val id: String) {
        CHOOSE_FILE("choose_file"),
        PICK_CATEGORIES("pick_categories"),
        CONFIRM_DEPLOYMENTS("confirm_deployments"),
        CHOOSE_LOCATION("choose_location"),
        WRITE("write"),
        DONE("done"),
        CONFIRM("confirm"),
        APPLY("apply"),
        RESULT("result"),
        FAILED("failed"),
    }

    /**
     * Why writing or reading an archive failed: the reasons of `SettingsArchiveException`, [IO] for any other I/O
     * error (whose message is [failureDetail]) and [INTERRUPTED] for an import, undo or reset whose process ended
     * while it ran.
     * zh-CN: 写入或读取归档失败的原因: `SettingsArchiveException` 的各原因, 其他 I/O 错误为 [IO] (消息在 [failureDetail]), 导入, 撤销或
     * 恢复默认运行期间进程结束为 [INTERRUPTED].
     */
    enum class Failure {
        TOO_LARGE,
        NOT_AN_ARCHIVE,
        INVALID_MANIFEST,
        MISSING_FILE,
        INVALID_FILE,
        LOCKED,
        IO,
        INTERRUPTED,
        ;

        companion object {

            /** The failure of [error]: its reason for a `SettingsArchiveException`, else [IO]. zh-CN: [error] 对应的失败原因. */
            @JvmStatic
            fun of(error: Throwable): Failure =
                (error as? SettingsArchiveException)?.let { e -> entries.firstOrNull { it.name == e.reason.name } } ?: IO
        }
    }

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "kind" to kind.id,
        "step" to step.id,
        "selected" to selected,
        "credentialsConfirmed" to credentialsConfirmed,
        "locationRequested" to locationRequested,
        "document" to document,
        "fileName" to fileName,
        "categories" to categories,
        "entries" to entries,
        "unexportable" to unexportable,
        "failure" to failure?.name,
        "failureDetail" to failureDetail,
        "archive" to archive,
        "deploy" to deploy,
        "operation" to operation,
        "outcome" to outcome?.toJson(),
    )

    fun toText(): String = SettingsJson.stringify(toJson())

    companion object {

        /**
         * The state of the JSON document [json], or null when it is none (an unknown wizard or step, a missing field):
         * the page then does not resume a wizard.
         * zh-CN: JSON 文档 [json] 对应的状态; 不是向导状态 (未知的向导或步骤, 缺少字段) 时为 null, 页面随之不恢复向导.
         */
        @JvmStatic
        fun fromJson(json: Any?): SettingsWizardState? {
            val root = json as? Map<*, *> ?: return null
            val kind = Kind.entries.firstOrNull { it.id == root["kind"] } ?: return null
            val step = Step.entries.firstOrNull { it.id == root["step"] } ?: return null
            val selected = (root["selected"] as? List<*> ?: return null).map { it as? String ?: return null }
            return SettingsWizardState(
                kind = kind,
                step = step,
                selected = selected,
                credentialsConfirmed = root["credentialsConfirmed"] == true,
                locationRequested = root["locationRequested"] == true,
                document = root["document"] as? String,
                fileName = root["fileName"] as? String,
                categories = SettingsJson.toInt(root["categories"]) ?: 0,
                entries = SettingsJson.toInt(root["entries"]) ?: 0,
                unexportable = SettingsJson.toInt(root["unexportable"]) ?: 0,
                failure = Failure.entries.firstOrNull { it.name == root["failure"] },
                failureDetail = root["failureDetail"] as? String,
                archive = root["archive"] as? String,
                deploy = (root["deploy"] as? List<*>)?.filterIsInstance<String>(),
                operation = root["operation"] as? String,
                outcome = SettingsTransferOutcome.fromJson(root["outcome"]),
            )
        }

        /** [fromJson] of [text]; null for null or malformed text. zh-CN: 由文本解码; 文本为 null 或格式错误时为 null. */
        @JvmStatic
        fun fromText(text: String?): SettingsWizardState? = text?.let {
            try {
                fromJson(SettingsJson.parse(it))
            } catch (_: SettingsJson.SettingsJsonException) {
                null
            }
        }
    }
}
