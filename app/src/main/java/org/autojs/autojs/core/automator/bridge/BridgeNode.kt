package org.autojs.autojs.core.automator.bridge

import org.autojs.autojs.core.automator.UiNode

/**
 * The node descriptor of the Node Bridge's frozen `accessibility` protocol (roadmap P5.4): the
 * seven fields `findOne` / `findAll` / `findByText` return, read through [UiNode]. Missing strings
 * are empty (never null), the bounds are the platform's screen rectangle as is. The Android side
 * encodes it as JSON; nothing else of the node crosses the bridge.
 * zh-CN: Node Bridge 冻结的 `accessibility` 协议的节点描述符 (Roadmap P5.4): `findOne` / `findAll` / `findByText` 返回的七个字段,
 * 经 [UiNode] 读取. 缺失的字符串为空串 (从不为 null), 边界为平台的屏幕矩形原样. Android 侧把它编码为 JSON; 节点的其它内容不跨过 bridge.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
data class BridgeNode(
    val text: String,
    val desc: String,
    val id: String,
    val className: String,
    val bounds: BridgeBounds,
    val clickable: Boolean,
    val enabled: Boolean,
) {

    companion object {

        fun of(node: UiNode<*>) = BridgeNode(
            text = node.text(),
            desc = node.desc().orEmpty(),
            id = node.id().orEmpty(),
            className = node.className().orEmpty(),
            bounds = BridgeBounds.of(node),
            clickable = node.clickable(),
            enabled = node.enabled(),
        )

    }

}
