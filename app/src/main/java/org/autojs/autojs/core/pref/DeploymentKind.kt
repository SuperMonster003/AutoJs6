package org.autojs.autojs.core.pref

/**
 * Kinds of deployment items (docs/settings/ROADMAP.md D20, T18): an import step the user confirms before a key is
 * written. Only a `SettingKey` carries one; guard (e) of T5 checks that every kind has a registered implementation.
 *
 * zh-CN: 部署项种类 (ROADMAP D20, T18): 写入某个键之前需要用户确认的导入步骤. 只有 `SettingKey` 可以携带; T5 守卫 (e)
 * 检查每个种类都有已注册的实现.
 */
enum class DeploymentKind {

    /** `key_working_directory`: create the target directory when it does not exist (appendix C Q1). */
    CREATE_DIRECTORY,

    /** `key_ace_editor_font`: download and apply the editor font when it is not installed (appendix C Q2). */
    DOWNLOAD_EDITOR_FONT,
}
