package org.autojs.autojs.core.accessibility.diagnostics

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.format.DateUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.A11yEventRecord
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.pref.keys.A11yKeys
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.SettingsUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects the status rows of the accessibility service manager dialog and renders them as the
 * diagnostics text that can be copied and pasted into issue reports or the roadmap evidence.
 * Every value is read fresh on each [collect]; nothing about the service instance is cached here.
 *
 * zh-CN: 收集无障碍服务管理器对话框状态区的各行, 并渲染为可复制的诊断文本 (用于问题报告或 Roadmap 证据回填).
 * 每次 [collect] 都重新读取全部数值, 不缓存任何服务实例信息.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yServiceDiagnostics(private val context: Context, private val tool: AccessibilityTool) {

    /**
     * "Enabled in system settings" combined with the in-process registry state.
     * zh-CN: "系统设置中已启用" 与进程内注册表状态的组合.
     */
    enum class Status(@StringRes val textRes: Int) {
        /** Not enabled in system settings. zh-CN: 系统设置中未启用. */
        NOT_ENABLED(R.string.text_disabled),

        /** Enabled in system settings but no instance is bound in this process. zh-CN: 系统设置中已启用, 但进程内没有绑定实例. */
        MALFUNCTIONING(R.string.text_malfunctioning),

        /** Enabled and bound, no event received yet. zh-CN: 已启用且已绑定, 尚未收到事件. */
        CONNECTED(R.string.text_connected),

        /** Enabled, bound and at least one event received. zh-CN: 已启用, 已绑定且至少收到过一个事件. */
        OPERATIONAL(R.string.text_operational),
    }

    /** Rows of the status section, in display order. zh-CN: 状态区的各行, 按显示顺序排列. */
    enum class Field(@StringRes val labelRes: Int) {
        SERVICE_STATUS(R.string.text_service_status),
        SYSTEM_SETTINGS(R.string.text_system_settings),
        IN_PROCESS_INSTANCE(R.string.text_in_process_instance),
        UPTIME(R.string.text_uptime),
        EVENTS(R.string.text_events),
        LAST_EVENT(R.string.text_last_event),
        WINDOWS(R.string.text_windows),
        ACTIVE_WINDOW_ROOT(R.string.text_active_window_root),
        LATEST_PACKAGE(R.string.text_latest_package),
        LATEST_ACTIVITY(R.string.text_latest_activity),
        CONFIG(R.string.text_config),
        ROOT_ACCESS(R.string.text_root_access),
        WRITE_SECURE_SETTINGS(R.string.text_write_secure_settings),
        SHIZUKU(R.string.text_shizuku_access),
        LISTENERS(R.string.text_listeners),
        SELF_CHECK(R.string.text_self_check),
        STABLE_MODE(R.string.text_stable_mode),
    }

    data class Entry(val field: Field, val label: String, val value: String)

    /**
     * Values that are expensive or asynchronous and therefore supplied by the caller:
     * root availability is probed once on a worker thread (it may spawn a shell), the self-check
     * result appears after the first self-check.
     * zh-CN: 由调用方提供的昂贵或异步数值: root 可用性只在 worker 线程探测一次 (可能会起 shell), 自检结果在首次自检后出现.
     */
    data class Probes(val rootAvailable: Boolean?, val selfCheck: String?)

    class Report(val status: Status, val header: List<String>, val sectionTitle: String, val entries: List<Entry>) {

        fun toText(): String = buildString {
            header.forEach { appendLine(it) }
            appendLine()
            appendLine("== $sectionTitle ==")
            entries.forEach { entry ->
                append(entry.label).append(": ").appendLine(entry.value.replace("\n", "\n  "))
            }
        }.trimEnd()

    }

    val status: Status
        get() {
            val snapshot = AccessibilityService.registry.snapshot
            return when {
                !tool.hasService() -> Status.NOT_ENABLED
                !snapshot.isBound -> Status.MALFUNCTIONING
                snapshot.isOperational -> Status.OPERATIONAL
                else -> Status.CONNECTED
            }
        }

    /** May be called on any thread; window and root queries are binder calls. zh-CN: 可在任意线程调用; 窗口与根节点查询为 binder 调用. */
    fun collect(probes: Probes): Report {
        val now = SystemClock.elapsedRealtime()
        val snapshot = AccessibilityService.registry.snapshot
        val service = snapshot.instance
        val status = status
        val none = string(R.string.text_none)
        val unavailable = string(R.string.text_unavailable)

        val entries = mutableListOf<Entry>()
        fun add(field: Field, value: String) {
            entries += Entry(field, string(field.labelRes), value)
        }

        add(Field.SERVICE_STATUS, string(status.textRes))
        add(Field.SYSTEM_SETTINGS, enabledText(tool.hasService()))
        val instanceState = when (snapshot.state) {
            State.UNBOUND -> string(R.string.text_unbound)
            State.CONNECTED -> string(R.string.text_connected)
            State.OPERATIONAL -> string(R.string.text_operational)
        }
        add(
            Field.IN_PROCESS_INSTANCE,
            service?.let { "$instanceState | ${identityOf(it)}" + (if (it.isAdoptedByEvent) " | ${string(R.string.text_adopted_by_first_event)}" else "") } ?: instanceState,
        )
        add(
            Field.UPTIME, service?.boundAtElapsedRealtime?.takeIf { it > 0L }
                ?.let { DateUtils.formatElapsedTime((now - it) / 1000L) } ?: none,
        )
        add(Field.EVENTS, service?.eventCount?.get()?.toString() ?: none)
        add(
            Field.LAST_EVENT, service?.takeIf { it.eventCount.get() > 0L }?.let {
                "${A11yEventRecord.typeNameOf(it.lastEventType)} | ${A11yServiceInfoFormat.formatElapsed(now - it.lastEventElapsedRealtime)}"
            } ?: none,
        )
        add(
            Field.WINDOWS, service?.let {
                runCatching { it.windows.size.toString() }.getOrDefault(unavailable)
            } ?: none,
        )
        add(Field.ACTIVE_WINDOW_ROOT, service?.let { describeActiveWindowRoot(it) } ?: none)

        val infoProvider = AutoJs.instance.infoProvider
        add(Field.LATEST_PACKAGE, runCatching { infoProvider.latestPackage }.getOrDefault("").ifEmpty { none })
        add(Field.LATEST_ACTIVITY, runCatching { infoProvider.latestActivity }.getOrDefault("").ifEmpty { none })

        add(
            Field.CONFIG, service?.let { svc ->
                runCatching { svc.serviceInfo }.getOrNull()?.let { describeServiceInfo(it) } ?: unavailable
            } ?: none,
        )

        add(
            Field.ROOT_ACCESS, "${enabledText(A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get())} | " + when (probes.rootAvailable) {
                true -> string(R.string.text_available)
                false -> unavailable
                null -> string(R.string.text_unknown)
            },
        )
        add(
            Field.WRITE_SECURE_SETTINGS, "${enabledText(A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get())} | " + when {
                SettingsUtils.SecureSettings.isGranted(context) -> string(R.string.text_granted)
                else -> string(R.string.text_not_granted)
            },
        )
        add(
            Field.SHIZUKU, "${enabledText(A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get())} | " + when {
                !WrappedShizuku.isInstalled(context) -> string(R.string.text_not_installed)
                !WrappedShizuku.isRunning() -> string(R.string.text_not_running)
                !WrappedShizuku.hasPermission() -> string(R.string.text_not_granted)
                else -> string(R.string.text_available)
            },
        )
        add(
            Field.LISTENERS, "state ${AccessibilityService.registry.listenerCount}" +
                    " | event ${AccessibilityService.scriptCallbackCount}" +
                    " | delegate ${AccessibilityService.delegateCount}" +
                    " | observer ${AccessibilityService.eventObserverCount}",
        )
        probes.selfCheck?.let { add(Field.SELF_CHECK, it) }
        add(Field.STABLE_MODE, enabledText(A11yKeys.STABLE_MODE.get()))

        return Report(status, header(), string(R.string.text_label_state), entries)
    }

    private fun header(): List<String> = listOf(
        "${string(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
    )

    private fun describeActiveWindowRoot(service: AccessibilityService): String {
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return string(R.string.text_unavailable)
        try {
            val packageName = runCatching { root.packageName?.toString() }.getOrNull()
            return string(R.string.text_available) + (packageName?.let { " | $it" } ?: "")
        } finally {
            recycle(root)
        }
    }

    // The framework names unknown bits "UNKNOWN" / "TYPE_UNKNOWN" or not at all; both become `0x..`.
    // zh-CN: 框架把未知位命名为 "UNKNOWN" / "TYPE_UNKNOWN" 或不命名; 两者统一显示为 `0x..`.
    private fun describeServiceInfo(info: AccessibilityServiceInfo): String {
        fun String?.known() = this?.takeUnless { it == "UNKNOWN" || it == "TYPE_UNKNOWN" }
        val eventTypes = when (info.eventTypes) {
            AccessibilityEvent.TYPES_ALL_MASK -> "TYPES_ALL_MASK"
            else -> A11yServiceInfoFormat.format(info.eventTypes, "TYPE_") { AccessibilityEvent.eventTypeToString(it).known() }
        }
        val feedbackType = A11yServiceInfoFormat.format(info.feedbackType, "FEEDBACK_") {
            AccessibilityServiceInfo.feedbackTypeToString(it).trim('[', ']').known()
        }
        val flags = A11yServiceInfoFormat.format(info.flags, "FLAG_") { AccessibilityServiceInfo.flagToString(it).known() }
        val capabilities = A11yServiceInfoFormat.format(info.capabilities, "CAPABILITY_") { AccessibilityServiceInfo.capabilityToString(it).known() }
        return listOf(
            "flags: $flags",
            "eventTypes: $eventTypes",
            "feedbackType: $feedbackType",
            "notificationTimeout: ${info.notificationTimeout} ms",
            "capabilities: $capabilities",
        ).joinToString("\n")
    }

    private fun identityOf(service: AccessibilityService): String {
        return "${service.javaClass.simpleName}@${Integer.toHexString(System.identityHashCode(service))}"
    }

    private fun enabledText(enabled: Boolean) = string(if (enabled) R.string.text_enabled else R.string.text_disabled)

    private fun string(@StringRes res: Int) = context.getString(res)

    private fun recycle(node: AccessibilityNodeInfo) {
        NodeLifecycle.default.release(node)
    }

}
