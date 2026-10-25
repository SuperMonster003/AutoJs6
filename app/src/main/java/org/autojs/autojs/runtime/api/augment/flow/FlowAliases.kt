package org.autojs.autojs.runtime.api.augment.flow

/**
 * Names of the flow classes moved to the new script module layer (roadmap P5.2), kept for `augment/automator/Auto.kt` and
 * `modules/auto/AutoModule.kt` while the selector topic has them in flight; removed with the old package.
 *
 * zh-CN: 已移入新脚本模块层的 flow 类的旧名称 (P5.2), 在 selector 主题修改 augment/automator/Auto.kt 与 modules/auto/AutoModule.kt 期间保留, 随旧包删除.
 */
internal typealias FlowExecutor = org.autojs.autojs.runtime.api.modules.flow.FlowExecutor

internal typealias WaitKind = org.autojs.autojs.runtime.api.modules.flow.WaitKind
