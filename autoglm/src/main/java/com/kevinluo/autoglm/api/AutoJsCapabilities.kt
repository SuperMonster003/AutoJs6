package com.kevinluo.autoglm.api

import android.content.Context

/**
 * AutoGLM 使用的 AutoJs 能力集合（面向接口编程）。
 *
 * 由宿主模块（:app / AutoJs）提供实现并在 Application 启动时注入。
 */
interface AutoJsCapabilities {

    /**
     * 打开“工作区脚本选择器”，选中脚本后回调返回脚本绝对路径。
     *
     * 要求：只需要列出工作区下的普通脚本文件（.js / .auto），暂不包含项目。
     */
    fun pickWorkspaceScript(
        context: Context,
        onPicked: (scriptAbsolutePath: String) -> Unit,
    )

    /**
     * 监听脚本执行事件（全局）。
     * 返回 Subscription，用于取消监听。
     */
    fun observeScriptExecutions(
        onEvent: (ScriptExecutionEvent) -> Unit,
    ): Subscription
}