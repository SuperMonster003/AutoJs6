package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher

/** The `xAsync` results of the mail API reach the script thread through the shared dispatcher. zh-CN: 邮件 API 的 `xAsync` 结果经共享分发器回到脚本线程. */
internal typealias MailAsyncDispatcher = ScriptAsyncDispatcher
