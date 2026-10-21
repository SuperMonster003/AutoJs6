package org.autojs.autojs.core.looper

/**
 * Handler rejected a callback because its Looper is quitting; other timer failures remain distinct.
 * The cause is a ScriptInterruptedException when the script is being stopped, so that the rejection is reported as an
 * interrupt rather than a script error (docs/memory/ROADMAP.md Q32).
 *
 * zh-CN: Handler 因 Looper 正在退出而拒绝回调; 其他定时器故障仍与之区分. 脚本正在被停止时 cause 为 ScriptInterruptedException,
 * 使这次拒绝按中断而非脚本错误报告 (docs/memory/ROADMAP.md Q32).
 */
class TimerCallbackRejectedException(cause: Throwable? = null) : IllegalStateException(
    "Unable to schedule a timer callback because its looper is exiting",
    cause,
)
