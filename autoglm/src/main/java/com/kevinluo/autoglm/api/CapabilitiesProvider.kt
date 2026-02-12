package com.kevinluo.autoglm.api

/**
 * 宿主装配入口：由 :app 在启动时注入 AutoJsCapabilities 实现。
 *
 * 设计原则：
 * - set 一次后锁死，避免被覆盖导致行为漂移
 * - 取用时若未初始化，直接抛错（开发阶段更早暴露问题）
 */
object CapabilitiesProvider {

    @Volatile
    private var autoJsCapabilities: AutoJsCapabilities? = null

    @Synchronized
    fun setAutoJsCapabilities(cap: AutoJsCapabilities) {
        check(autoJsCapabilities == null) { "AutoJsCapabilities already set" }
        autoJsCapabilities = cap
    }

    fun autoJs(): AutoJsCapabilities =
        requireNotNull(autoJsCapabilities) {
            "AutoJsCapabilities not set; call CapabilitiesProvider.setAutoJsCapabilities() in org.autojs.autojs.App.onCreate()."
        }
}