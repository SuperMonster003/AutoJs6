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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorCoordinator
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorSource
import org.autojs.autojs.theme.app.ColorSelectBaseActivity
import org.autojs.autojs.theme.library.ColorSelection
import java.text.Collator
import java.time.Instant
import java.time.ZoneId

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

    private const val KEY_SNAPSHOT = "INTELLIGENT_COLOR_ACTIVE_SNAPSHOT"
    private const val KEY_RUNTIME = "INTELLIGENT_COLOR_RUNTIME_STATE"

    private lateinit var appContext: Context

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val planner by lazy {
        val collator = Collator.getInstance()
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

    /**
     * Called once from `App.onCreate`, after the theme color and night mode are initialized.
     * zh-CN: 在 `App.onCreate` 中主题色与夜间模式初始化之后调用一次.
     */
    fun init(application: Application) {
        appContext = application.applicationContext
        ThemeColorCoordinator.listener = this
        NightModeState.addListener(this)
        application.registerActivityLifecycleCallbacks(foregroundCallbacks)
        snapshot = IntelligentEngineJson.decodeSnapshot(Pref.getStringOrNull(KEY_SNAPSHOT))?.let(::refreshPreset)
        runtime = IntelligentEngineJson.decodeRuntime(Pref.getStringOrNull(KEY_RUNTIME))
        if (snapshot != null) evaluate(IntelligentEvent.APP_START)
    }

    /**
     * Makes [snapshot] the active library, starting from [startGroupId], and applies its color at once,
     * even while theme color pages hold the pause tokens.
     * zh-CN: 将 [snapshot] 设为激活库并从 [startGroupId] 开始, 立即应用其颜色 (即使主题色页面持有暂停令牌).
     */
    fun activate(snapshot: IntelligentSnapshot, startGroupId: Long?) {
        this.snapshot = snapshot
        Pref.putString(KEY_SNAPSHOT, IntelligentEngineJson.encodeSnapshot(snapshot))
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
        Pref.putString(KEY_SNAPSHOT, IntelligentEngineJson.encodeSnapshot(snapshot))
        // A changed configuration re-arms the schedule; a rename or group edit keeps it.
        // zh-CN: 配置变化时重新安排调度; 仅重命名或编辑组时保持原调度.
        when (previous.config) {
            snapshot.config -> plan(IntelligentEvent.EVALUATE, null, ThemeColorSource.INTELLIGENT)
            else -> plan(IntelligentEvent.ACTIVATE, runtime.currentGroupId, ThemeColorSource.INTELLIGENT)
        }
    }

    fun onLibraryDeleted(libraryId: Int) {
        if (snapshot?.libraryId == libraryId) deactivate()
    }

    fun deactivate() {
        if (snapshot == null) return
        snapshot = null
        runtime = IntelligentRuntimeState()
        Pref.remove(KEY_SNAPSHOT)
        Pref.remove(KEY_RUNTIME)
        handler.removeCallbacks(dueRunnable)
        nextStateChangeAt = null
        updateTimeReceiver(false)
        DeviceStateMonitor.stopAll()
        if (watchingWallpaper) {
            WallpaperColorSource.watch(appContext, null)
            watchingWallpaper = false
        }
        _changes.postValue(Unit)
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
     * Evaluates only when a rotation is due, the state or the date override has changed, or the color drifted, so
     * frequent callers (resuming activities, battery broadcasts) stay cheap.
     * zh-CN: 仅在换组到期, 状态或日期覆盖已变化, 或颜色偏离预期时评估, 使频繁调用方 (Activity 恢复, 电池广播) 保持低开销.
     */
    private fun evaluateIfNeeded() {
        val active = snapshot ?: return
        val now = System.currentTimeMillis()
        val due = listOfNotNull(runtime.earliestDueAt, nextStateChangeAt).any { now >= it }
        val state = resolveState(active.config, now)
        val stateChanged = runtime.lastStateKey != state.stateKey
        val overrideChanged = runtime.overrideGroupId != overrideGroupOf(active.config, now)
        val colorDrifted = ThemeColorManager.currentThemeColor != expectedThemeColor(active, state.stateKey)
        if (due || stateChanged || overrideChanged || colorDrifted) evaluate(IntelligentEvent.EVALUATE)
    }

    private fun plan(event: IntelligentEvent, startGroupId: Long?, source: ThemeColorSource) {
        val active = snapshot ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        watchDeviceFacts(active.config)
        watchWallpaper(active)
        val state = resolveState(active.config, now)
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
        runtime = result.runtime
        Pref.putString(KEY_RUNTIME, IntelligentEngineJson.encodeRuntime(runtime))
        ColorSelectBaseActivity.savePrefsForSelectionNames(active.name, active.group(result.groupId)?.let(::groupDisplayName))
        result.color?.let { color ->
            val themeColor = ThemeColor(color.primary, color.dark ?: color.primary, color.accent ?: color.primary)
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
    private fun resolveState(config: IntelligentConfig, now: Long): StateResolution {
        val facts = DeviceStateMonitor.read(appContext, DeviceStateMonitor.factsFor(config.stateSource)).copy(isNight = NightModeState.isNight)
        return StateResolver.resolve(config.stateSource, now, ZoneId.systemDefault(), facts, runtime.lastStateKey)
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
        Pref.putString(KEY_SNAPSHOT, IntelligentEngineJson.encodeSnapshot(fresh))
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
        if (fresh != saved) Pref.putString(KEY_SNAPSHOT, IntelligentEngineJson.encodeSnapshot(fresh))
        return fresh
    }

    private fun expectedThemeColor(active: IntelligentSnapshot, stateKey: String): ThemeColor? {
        val color = active.group(shownGroupId)?.resolve(stateKey, active.config.stateKeys) ?: return null
        return ThemeColor(color.primary, color.dark ?: color.primary, color.accent ?: color.primary)
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

    private fun isSelectionOf(active: IntelligentSnapshot): Boolean {
        return (ColorSelectBaseActivity.currentColorSelection() as? ColorSelection.Intelligent)?.libraryId == active.libraryId
    }

    fun groupDisplayName(group: IntelligentGroup): String? = group.name?.takeIf { it.isNotBlank() }

}
