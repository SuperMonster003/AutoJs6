package org.autojs.autojs.pluginclient

import java.io.IOException

/**
 * The peer announced its close with `bye(reason)` and the reason (shutdown, restart, replaced) permits
 * reconnecting later. `bye(user)` never produces this exception: it is a clean disconnect.
 * zh-CN: 对端以 `bye(reason)` 宣告关闭, 且原因 (退出, 重启, 被替换) 允许稍后重连. `bye(user)` 不会产生此异常, 它属于正常断开.
 */
class PeerClosedException(val reason: String) : IOException("Closed by peer ($reason)")

/**
 * The extension rejected the handshake (version mismatch or an explicit error message). Such a failure
 * is permanent for the current extension version, so it is never retried automatically.
 * zh-CN: 插件拒绝了握手 (版本不匹配或明确的错误消息). 对当前插件版本而言该失败是永久性的, 因此不会自动重试.
 */
class HandshakeRejectedException(message: String) : IOException(message)
