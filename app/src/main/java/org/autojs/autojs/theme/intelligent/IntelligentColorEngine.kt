package org.autojs.autojs.theme.intelligent

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.library.ColorLibraryIds
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.FutureTask
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.script.ScriptSource

/**
 * Runs the active intelligent color library while the app process is alive (roadmap P1.4, D5, D6, D9, D10).
 * zh-CN: 在应用进程存活期间运行激活中的智能颜色库 (Roadmap P1.4, D5, D6, D9, D10).
 *
 * - The active library is kept as an [IntelligentSnapshot] in preferences, so a cold start evaluates it
 *   synchronously in `App.onCreate` before any Activity draws, without a database query.
 * - Decisions come from [IntelligentColorPlanner]; this object only feeds it state and events and applies results
 *   through [ThemeColorCoordinator] (source [ThemeColorSource.INTELLIGENT], or [ThemeColorSource.ACTIVATION]).
 * - No alarms or jobs: interval triggers use a main thread [Handler]; a cold start or a return to the foreground
 *   catches up once.
 * - Any explicit user or script color choice deactivates the library.
 * zh-CN:
 * - 激活库以 [IntelligentSnapshot] 保存在偏好中, 因此冷启动时可在 `App.onCreate` 中同步评估, 先于任何 Activity 绘制, 且无需查询数据库.
 * - 决策来自 [IntelligentColorPlanner]; 本对象只负责提供状态与事件, 并经 [ThemeColorCoordinator] 应用结果
 *   (来源为 [ThemeColorSource.INTELLIGENT] 或 [ThemeColorSource.ACTIVATION]).
 * - 不使用闹钟或后台任务: 时间间隔由主线程 [Handler] 调度; 冷启动或回到前台时补算一次.
 * - 用户或脚本的任何显式选色都会停用智能库.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
object IntelligentColorEngine : ThemeColorCoordinator.Listener, NightModeState.Listener {

    private const val LOG_TAG = "IntelligentColorEngine"

    private lateinit var appContext: Context

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Created on the first name comparison only, since creating a collator is slow (roadmap P7.4). zh-CN: 仅在首次按名称比较时创建, 因为创建排序器较慢 (Roadmap P7.4). */
    private val collator by lazy { Collator.getInstance() }

    private val planner by lazy {
        IntelligentColorPlanner(nameComparator = Comparator { a, b -> collator.compare(a, b) })
    }

    private val dueRunnable = Runnable { evaluate(IntelligentEvent.EVALUATE) }

    private var snapshot: IntelligentSnapshot? = null

    private var runtime = IntelligentRuntimeState()

    private var timeReceiverRegistered = false

    /** When the state of a time based source changes next; not persisted, a cold start resolves it again. zh-CN: 时间类状态源下一次变化的时间; 不持久化, 冷启动时重新计算. */
    private var nextStateChangeAt: Long? = null

    private val _changes = MutableLiveData(Unit)

    /** Posts whenever the active library, its group or its schedule changes. zh-CN: 激活库, 当前组或调度变化时发出通知. */
    val changes: LiveData<Unit> get() = _changes

    val activeSnapshot: IntelligentSnapshot?
        get() = snapshot

    val runtimeState: IntelligentRuntimeState
        get() = runtime

    /** The group being shown (an override group while one is active). zh-CN: 正在显示的组 (覆盖层生效时为覆盖组). */
    val shownGroupId: Long?
        get() = runtime.overrideGroupId ?: runtime.currentGroupId

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scheduleNext()
            evaluateIfNeeded()
        }
    }

    private var watchingWallpaper = false

    private val foregroundCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            refreshWallpaperIfChanged()
            evaluateIfNeeded()
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    private var preloaded: FutureTask<Pair<IntelligentSnapshot?, IntelligentRuntimeState>>? = null

    /**
     * Decodes the saved library on a background thread while the rest of `App.onCreate` runs, and warms up the planner
     * with a dry run, so that [init] finds the data ready and the classes loaded (roadmap P7.4).
     * zh-CN: 在 `App.onCreate` 的其余部分运行期间, 于后台线程解码保存的库, 并空跑一次规划器以完成预热,
     * 使 [init] 时数据已就绪且相关类已加载 (Roadmap P7.4).
     */
    fun preload() {
        if (preloaded != null) return
        val task = FutureTask {
            val saved = decodeSaved()
            saved.first?.let { active ->
                runCatching {
                    val input = dryRunInput(active, saved.second)
                    planner.plan(input)
                    // Pure calls that load the state classes as well; nothing is registered here.
                    // zh-CN: 同时加载状态相关类的纯调用; 此处不注册任何监听.
                    DeviceStateMonitor.factsFor(active.config.stateSource)
                    StateResolver.resolve(active.config.stateSource, input.now, input.zone, DeviceFacts(), saved.second.lastStateKey)
                }
            }
            saved
        }
        preloaded = task
        Thread(task, "IntelligentColorEnginePreload").start()
    }

    private fun decodeSaved(): Pair<IntelligentSnapshot?, IntelligentRuntimeState> {
        return IntelligentEngineJson.decodeSnapshot(MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.storedText()) to IntelligentEngineJson.decodeRuntime(MiscStateKeys.INTELLIGENT_COLOR_RUNTIME_STATE.storedText())
    }

    /** A planner input without side effects; its result is discarded. zh-CN: 不产生副作用的规划器输入; 结果会被丢弃. */
    private fun dryRunInput(active: IntelligentSnapshot, runtime: IntelligentRuntimeState) = PlannerInput(
        config = active.config,
        groups = active.groups,
        stateKey = runtime.lastStateKey ?: active.config.stateKeys.first(),
        now = System.currentTimeMillis(),
        event = IntelligentEvent.EVALUATE,
        runtime = runtime,
        zone = zoneOf(active.config),
    )

    /**
     * Called once from `App.onCreate`, after the theme color and night mode are initialized.
     * zh-CN: 在 `App.onCreate` 中主题色与夜间模式初始化之后调用一次.
     */
    fun init(application: Application) {
        // Timing for performance checks (roadmap P7.4), off unless `adb shell setprop log.tag.IntelligentColorEngine DEBUG`.
        // zh-CN: 用于性能检查的计时 (Roadmap P7.4), 默认关闭, 可经 `adb shell setprop log.tag.IntelligentColorEngine DEBUG` 开启.
        val startedAt = if (Log.isLoggable(LOG_TAG, Log.DEBUG)) SystemClock.elapsedRealtimeNanos() else 0L
        appContext = application.applicationContext
        ThemeColorCoordinator.listener = this
        NightModeState.addListener(this)
        application.registerActivityLifecycleCallbacks(foregroundCallbacks)
        val (savedSnapshot, savedRuntime) = preloaded?.let { task -> runCatching { task.get() }.getOrNull() } ?: decodeSaved()
        preloaded = null
        snapshot = savedSnapshot
        runtime = savedRuntime
        val decodedAt = if (startedAt != 0L) SystemClock.elapsedRealtimeNanos() else 0L
        if (snapshot != null) evaluate(IntelligentEvent.APP_START)
        // The saved preset snapshot was built by the previous run; rebuilding it is deferred past the first frame (roadmap P7.4).
        // zh-CN: 保存的预设快照由上次运行构建; 重建推迟到首帧之后 (Roadmap P7.4).
        if (snapshot?.libraryId?.let(ColorLibraryIds::isPresetIntelligent) == true) handler.post(::refreshActivePreset)
        if (startedAt != 0L) {
            val endedAt = SystemClock.elapsedRealtimeNanos()
            Log.d(LOG_TAG, "init: snapshot ${snapshot?.libraryId}, decode ${(decodedAt - startedAt) / 1000} us, evaluate ${(endedAt - decodedAt) / 1000} us, total ${(endedAt - startedAt) / 1000} us")
        }
    }

    /**
     * Makes [snapshot] the active library, starting from [startGroupId], and applies its color at once,
     * even while theme color pages hold the pause tokens.
     * zh-CN: 将 [snapshot] 设为激活库并从 [startGroupId] 开始, 立即应用其颜色 (即使主题色页面持有暂停令牌).
     */
    fun activate(snapshot: IntelligentSnapshot, startGroupId: Long?) {
        this.snapshot = snapshot
        MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.setStoredText(IntelligentEngineJson.encodeSnapshot(snapshot))
        ColorSelectBaseActivity.savePrefsForIntelligent(snapshot.libraryId, startGroupId)
        plan(IntelligentEvent.ACTIVATE, startGroupId, ThemeColorSource.ACTIVATION)
    }

    /**
     * Replaces the stored data of the active library after it was edited; the current group is kept when it still exists.
     * zh-CN: 激活库被编辑后替换其保存的数据; 当前组仍存在时保持不变.
     */
    fun onLibraryChanged(snapshot: IntelligentSnapshot) {
        val previous = this.snapshot ?: return
        if (previous.libraryId != snapshot.libraryId) return
        this.snapshot = snapshot
        MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.setStoredText(IntelligentEngineJson.encodeSnapshot(snapshot))
        // A changed configuration re-arms the schedule; a rename or group edit keeps it.
        // zh-CN: 配置变化时重新安排调度; 仅重命名或编辑组时保持原调度.
        when (previous.config) {
            snapshot.config -> plan(IntelligentEvent.EVALUATE, null, ThemeColorSource.INTELLIGENT)
            else -> plan(IntelligentEvent.ACTIVATE, runtime.currentGroupId, ThemeColorSource.INTELLIGENT)
        }
    }

    /**
     * The state name last set by `colors.theme.state`, used by [StateSource.ScriptStates] (roadmap P6.2).
     * zh-CN: 最近一次由 `colors.theme.state` 设置的状态名, 供 [StateSource.ScriptStates] 使用 (Roadmap P6.2).
     */
    val scriptState: String?
        get() = MiscStateKeys.INTELLIGENT_COLOR_SCRIPT_STATE.storedOrNull()

    /**
     * Switches the script state of the active library; call on the main thread.
     * zh-CN: 切换激活库的脚本状态; 在主线程调用.
     *
     * @throws IllegalStateException when no active library uses script states.
     * @throws IllegalArgumentException when [name] is not one of its states.
     */
    fun setScriptState(name: String) {
        val source = snapshot?.config?.stateSource as? StateSource.ScriptStates
            ?: throw IllegalStateException("No active intelligent color library uses script states")
        require(name in source.names) { "Unknown script state \"$name\", expected one of: ${source.names.joinToString(", ")}" }
        MiscStateKeys.INTELLIGENT_COLOR_SCRIPT_STATE.set(name)
        evaluate(IntelligentEvent.EVALUATE)
    }

    /**
     * Moves the active library to its next group at once, whatever its rotation triggers; call on the main thread.
     * zh-CN: 立即将激活库切换到下一组, 与其换组时机无关; 在主线程调用.
     *
     * @return false when no intelligent library is active.
     */
    fun next(): Boolean {
        if (snapshot == null) return false
        evaluate(IntelligentEvent.SCRIPT_NEXT)
        return true
    }

    /**
     * The stored snapshot and runtime of the active library, for "Undo script change" (roadmap P6.3); nulls when none is active.
     * zh-CN: 激活库保存的快照与运行状态, 用于 "撤销脚本修改" (Roadmap P6.3); 未激活时为 null.
     */
    fun exportState(): Pair<String?, String?> = when (snapshot) {
        null -> null to null
        else -> MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.storedText() to MiscStateKeys.INTELLIGENT_COLOR_RUNTIME_STATE.storedText()
    }

    /**
     * Activates the library of [snapshotJson] again from the group of [runtimeJson]; false when the data is unusable.
     * zh-CN: 按 [runtimeJson] 中的组重新激活 [snapshotJson] 对应的库; 数据不可用时返回 false.
     */
    fun restoreState(snapshotJson: String, runtimeJson: String?): Boolean {
        val restored = IntelligentEngineJson.decodeSnapshot(snapshotJson)?.let(::refreshPreset) ?: return false
        activate(restored, IntelligentEngineJson.decodeRuntime(runtimeJson).currentGroupId)
        return true
    }

    fun onLibraryDeleted(libraryId: Int) {
        if (snapshot?.libraryId == libraryId) deactivate()
    }

    fun deactivate() {
        if (snapshot == null) return
        snapshot = null
        runtime = IntelligentRuntimeState()
        MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.remove()
        MiscStateKeys.INTELLIGENT_COLOR_RUNTIME_STATE.remove()
        handler.removeCallbacks(dueRunnable)
        nextStateChangeAt = null
        updateTimeReceiver(false)
        DeviceStateMonitor.stopAll()
        if (watchingWallpaper) {
            WallpaperColorSource.watch(appContext, null)
            watchingWallpaper = false
        }
        watchScriptStarts(null)
        _changes.postValue(Unit)
    }

    private var watchingScriptStarts = false

    /** Several scripts started together rotate once. zh-CN: 同时启动的多个脚本只换一次组. */
    private val scriptStartRunnable = Runnable { evaluate(IntelligentEvent.SCRIPT_START) }

    private val scriptStartCallback = object : ScriptEngineManager.EngineLifecycleCallback {
        override fun onEngineCreate(engine: ScriptEngine<out ScriptSource>) {
            handler.removeCallbacks(scriptStartRunnable)
            handler.post(scriptStartRunnable)
        }

        override fun onEngineRemove(engine: ScriptEngine<out ScriptSource>) = Unit
    }

    /**
     * Listens to script starts only while the active library rotates on them (roadmap P6.2).
     * zh-CN: 仅当激活库在脚本启动时换组时监听脚本启动 (Roadmap P6.2).
     */
    private fun watchScriptStarts(config: IntelligentConfig?) {
        val needed = config != null && RotationTrigger.ScriptStart in config.rotation
        if (needed == watchingScriptStarts) return
        val service = ScriptEngineService.getInstance() ?: return
        if (needed) service.registerEngineLifecycleCallback(scriptStartCallback) else service.unregisterEngineLifecycleCallback(scriptStartCallback)
        watchingScriptStarts = needed
    }

    override fun onExplicitColorApplied(source: ThemeColorSource) = deactivate()

    override fun onResumed() = evaluate(IntelligentEvent.EVALUATE)

    override fun onNightModeChanged(isNight: Boolean) = evaluateIfNeeded()

    fun evaluate(event: IntelligentEvent) {
        val active = snapshot ?: return
        // Theme color pages hold pause tokens: neither the group nor the color changes meanwhile,
        // and the release of the last token evaluates once through onResumed (D6).
        // zh-CN: 主题色页面持有暂停令牌: 期间组与颜色均不变化, 最后一个令牌释放时经 onResumed 评估一次 (D6).
        if (ThemeColorCoordinator.isPaused) return
        if (!isSelectionOf(active)) {
            deactivate()
            return
        }
        plan(event, null, ThemeColorSource.INTELLIGENT)
    }

    /**
     * Applies the shown group's color again without rotating, e.g. after the derivation setting changed (D2). Works
     * while theme color pages are open, since the user asked for it there.
     * zh-CN: 不换组, 重新应用当前组的颜色, 例如在派生配色设置变化后 (D2). 主题色页面打开期间同样生效, 因为这是用户在该页面上的操作.
     *
     * @return false when no intelligent library is active.
     */
    fun reapply(): Boolean {
        val active = snapshot ?: return false
        val stateKey = runtime.lastStateKey ?: resolveState(active.config, System.currentTimeMillis(), zoneOf(active.config)).stateKey
        val themeColor = expectedThemeColor(active, stateKey) ?: return false
        ThemeColorCoordinator.apply(themeColor, ThemeColorSource.RESTORE)
        return true
    }

    /**
     * Evaluates only when a rotation is due, the state or the date override has changed, or the color drifted, so
     * frequent callers (resuming activities, battery broadcasts) stay cheap.
     * zh-CN: 仅在换组到期, 状态或日期覆盖已变化, 或颜色偏离预期时评估, 使频繁调用方 (Activity 恢复, 电池广播) 保持低开销.
     */
    private fun evaluateIfNeeded() {
        val active = snapshot ?: return
        val now = System.currentTimeMillis()
        val due = listOfNotNull(runtime.earliestDueAt, nextStateChangeAt).any { now >= it }
        val state = resolveState(active.config, now, zoneOf(active.config))
        val stateChanged = runtime.lastStateKey != state.stateKey
        val overrideChanged = runtime.overrideGroupId != overrideGroupOf(active.config, now)
        val colorDrifted = ThemeColorManager.currentThemeColor != expectedThemeColor(active, state.stateKey)
        if (due || stateChanged || overrideChanged || colorDrifted) evaluate(IntelligentEvent.EVALUATE)
    }

    private fun plan(event: IntelligentEvent, startGroupId: Long?, source: ThemeColorSource) {
        val active = snapshot ?: return
        val now = System.currentTimeMillis()
        val zone = zoneOf(active.config)
        watchDeviceFacts(active.config)
        watchWallpaper(active)
        watchScriptStarts(active.config)
        val state = resolveState(active.config, now, zone)
        nextStateChangeAt = state.nextChangeAt
        val result = planner.plan(
            PlannerInput(
                config = active.config,
                groups = active.groups,
                stateKey = state.stateKey,
                now = now,
                event = event,
                runtime = runtime,
                overrideGroupId = overrideGroupOf(active.config, now),
                startGroupId = startGroupId,
                zone = zone,
            )
        )
        if (result.runtime != runtime) {
            runtime = result.runtime
            MiscStateKeys.INTELLIGENT_COLOR_RUNTIME_STATE.setStoredText(IntelligentEngineJson.encodeRuntime(runtime))
        }
        saveSelectionNames(active.name, active.group(result.groupId)?.let(::groupDisplayName))
        result.color?.let { color ->
            val themeColor = ThemeColorManager.resolve(color.primary, color.accent, color.dark)
            if (source == ThemeColorSource.ACTIVATION || themeColor != ThemeColorManager.currentThemeColor) {
                ThemeColorCoordinator.apply(themeColor, source)
            }
        }
        scheduleNext()
        updateTimeReceiver(usesTime(active.config))
        _changes.postValue(Unit)
    }

    /**
     * The current state of the active source, read from night mode and the watched device facts (roadmap P3.1 to P3.3).
     * zh-CN: 激活库状态源的当前状态, 取自夜间模式与所监听的设备状态 (Roadmap P3.1 至 P3.3).
     */
    private fun resolveState(config: IntelligentConfig, now: Long, zone: ZoneId): StateResolution {
        val facts = DeviceStateMonitor.read(appContext, DeviceStateMonitor.factsFor(config.stateSource)).copy(isNight = NightModeState.isNight, scriptState = scriptState)
        return StateResolver.resolve(config.stateSource, now, zone, facts, runtime.lastStateKey)
    }

    /**
     * The system zone for configurations that depend on local time, UTC otherwise: the first lookup of the system zone
     * loads the time zone data, which is costly on a cold start (roadmap P7.4).
     * zh-CN: 依赖本地时间的配置使用系统时区, 其他配置使用 UTC: 首次获取系统时区会加载时区数据, 在冷启动时开销较大 (Roadmap P7.4).
     */
    private fun zoneOf(config: IntelligentConfig): ZoneId = when {
        config.interval != null || config.daily != null || config.overrides.isNotEmpty() -> ZoneId.systemDefault()
        config.stateSource.let { it is StateSource.TimePeriods || it is StateSource.SunriseSunset || it is StateSource.Weekday || it is StateSource.Seasons } -> ZoneId.systemDefault()
        else -> ZoneOffset.UTC
    }

    /** The group of the first matching date override today (roadmap P3.5). zh-CN: 今天第一个命中的日期覆盖层的组 (Roadmap P3.5). */
    private fun overrideGroupOf(config: IntelligentConfig, now: Long): Long? {
        if (config.overrides.isEmpty()) return null
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return DateOverrides.firstMatch(config.overrides, today)?.groupId
    }

    private fun usesTime(config: IntelligentConfig): Boolean {
        return config.interval != null || config.daily != null || config.overrides.isNotEmpty() || nextStateChangeAt != null
    }

    /** Watches wallpaper colors only while the wallpaper preset is active (D5). zh-CN: 仅在跟随壁纸预设激活时监听壁纸颜色 (D5). */
    private fun watchWallpaper(active: IntelligentSnapshot) {
        val needed = active.libraryId == IntelligentPresets.ID_WALLPAPER
        if (needed == watchingWallpaper) return
        WallpaperColorSource.watch(appContext, if (needed) ({ refreshWallpaperIfChanged() }) else null)
        watchingWallpaper = needed
    }

    /**
     * Rebuilds the active wallpaper preset when the wallpaper colors differ from its groups (roadmap P4.2); called when
     * the colors change and when an activity resumes, which also covers changes made while the process was dead.
     * zh-CN: 壁纸颜色与激活中的跟随壁纸预设不一致时重建该预设 (Roadmap P4.2); 在颜色变化时与 Activity 恢复时调用, 后者也覆盖了进程死亡期间的变化.
     */
    private fun refreshWallpaperIfChanged() {
        val active = snapshot ?: return
        if (active.libraryId != IntelligentPresets.ID_WALLPAPER) return
        val fresh = IntelligentPresets.wallpaper(appContext)
        if (fresh == active) return
        snapshot = fresh
        MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.setStoredText(IntelligentEngineJson.encodeSnapshot(fresh))
        _changes.postValue(Unit)
        evaluate(IntelligentEvent.EVALUATE)
    }

    private fun watchDeviceFacts(config: IntelligentConfig) {
        DeviceStateMonitor.watch(appContext, DeviceStateMonitor.factsFor(config.stateSource)) { evaluateIfNeeded() }
    }

    /**
     * Presets are defined in code, so a saved snapshot of one is rebuilt on start: their colors may change between
     * versions (D24) and their names follow the current language. Group ids are stable, so the current group is kept.
     * zh-CN: 预设定义在代码中, 因此保存的预设快照在启动时重建: 其颜色可能随版本变化 (D24), 名称跟随当前语言. 组 id 稳定, 当前组保持不变.
     */
    private fun refreshPreset(saved: IntelligentSnapshot): IntelligentSnapshot {
        if (!IntelligentPresets.isPreset(saved.libraryId)) return saved
        val fresh = IntelligentPresets.find(appContext, saved.libraryId) ?: return saved
        if (fresh != saved) MiscStateKeys.INTELLIGENT_COLOR_ACTIVE_SNAPSHOT.setStoredText(IntelligentEngineJson.encodeSnapshot(fresh))
        return fresh
    }

    /** Rebuilds the active preset after the cold start evaluation and evaluates again when it changed. zh-CN: 在冷启动评估之后重建激活的预设, 有变化时再次评估. */
    private fun refreshActivePreset() {
        val active = snapshot ?: return
        val fresh = refreshPreset(active)
        if (fresh == active) return
        snapshot = fresh
        _changes.postValue(Unit)
        evaluate(IntelligentEvent.EVALUATE)
    }

    private fun expectedThemeColor(active: IntelligentSnapshot, stateKey: String): ThemeColor? {
        val color = active.group(shownGroupId)?.resolve(stateKey, active.config.stateKeys) ?: return null
        return ThemeColorManager.resolve(color.primary, color.accent, color.dark)
    }

    /**
     * Wakes at the earliest of: a rotation due time, the next change of a time based state, and the next midnight
     * when date overrides exist.
     * zh-CN: 在以下时间中最早者唤醒: 换组到期时间, 时间类状态的下一次变化, 以及存在日期覆盖层时的下一个午夜.
     */
    private fun scheduleNext() {
        handler.removeCallbacks(dueRunnable)
        val active = snapshot ?: return
        val now = System.currentTimeMillis()
        val midnight = if (active.config.overrides.isNotEmpty()) DailySchedule.nextMidnight(now, ZoneId.systemDefault()) else null
        val dueAt = listOfNotNull(runtime.earliestDueAt, nextStateChangeAt, midnight).minOrNull() ?: return
        handler.postDelayed(dueRunnable, (dueAt - now).coerceAtLeast(0L))
    }

    private fun updateTimeReceiver(needed: Boolean) {
        if (needed == timeReceiverRegistered || !::appContext.isInitialized) return
        if (needed) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(appContext, timeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            runCatching { appContext.unregisterReceiver(timeReceiver) }
        }
        timeReceiverRegistered = needed
    }

    /**
     * Intelligent library ids have segments of their own (D9), so the stored library id alone tells whether the
     * selection is [active]; reading it directly keeps the selection pages' classes off the cold start (roadmap P7.4).
     * zh-CN: 智能库 id 有独立的分段 (D9), 因此仅凭保存的库 id 即可判断选中项是否为 [active]; 直接读取可避免冷启动时加载选择页面的类 (Roadmap P7.4).
     */
    private fun isSelectionOf(active: IntelligentSnapshot): Boolean {
        return MiscStateKeys.COLOR_SELECT_LIBRARY_ID.get() == active.libraryId
    }

    /** Writes the names shown in the theme color summary when they changed. zh-CN: 主题色摘要中显示的名称有变化时写入. */
    private fun saveSelectionNames(libraryName: String?, itemName: String?) {
        if (MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.storedOrNull() != libraryName) MiscStateKeys.COLOR_SELECT_LIBRARY_NAME.set(libraryName)
        if (MiscStateKeys.COLOR_SELECT_ITEM_NAME.storedOrNull() != itemName) MiscStateKeys.COLOR_SELECT_ITEM_NAME.set(itemName)
    }

    fun groupDisplayName(group: IntelligentGroup): String? = group.name?.takeIf { it.isNotBlank() }

}
