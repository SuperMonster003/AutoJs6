/**
 * Smoke test of the legacy (synchronous) accessibility automation API surface.
 *
 * Run this on a real device before and after each phase of
 * docs/dev/accessibility-automation-roadmap.md. It covers the entry points that
 * must keep their behavior: text().findOne(timeout), find(n), pickup, detect,
 * compass, wait, click(UiObject), scrollDown(), auto.registerEvent, and (when the
 * service can be restarted unattended) auto.stateListener plus selectors across a
 * service restart.
 *
 * zh-CN: 传统 (同步) 无障碍自动化 API 的冒烟测试.
 * 在 docs/dev/accessibility-automation-roadmap.md 的每个阶段前后于真机运行一次.
 * 覆盖必须保持行为不变的入口: text().findOne(timeout), find(n), pickup, detect,
 * compass, wait, click(UiObject), scrollDown(), auto.registerEvent, 以及 (服务可无人值守重启时)
 * 服务重启前后的 auto.stateListener 与选择器.
 */

"use strict";

const results = [];

function check(name, body) {
    let passed = false;
    let detail = "";
    try {
        const value = typeof body === "function" ? body() : body;
        passed = value === true;
        if (!passed) detail = `returned ${value}`;
    } catch (e) {
        detail = `threw ${e}`;
    }
    results.push({ name, passed, detail });
    console[passed ? "log" : "error"](`[${passed ? "PASS" : "FAIL"}] ${name}${detail ? ` (${detail})` : ""}`);
    return passed;
}

function doesNotThrow(body) {
    body();
    return true;
}

/* 1. Service and event registration. */

auto();

const windowEvents = [];

auto.registerEvent("WINDOW_STATE_CHANGED", function (event) {
    windowEvents.push({ packageName: String(event.packageName), className: String(event.className), eventTime: event.eventTime });
});

events.on("exit", function () {
    auto.removeEvent("WINDOW_STATE_CHANGED");
});

// Go home, then bring AutoJs6 back so that every run starts from the same window transition.
// On API <= 28 the HOME key that home() injects starts the system's 5 s app-switch lock
// ("Activity start request from <uid> stopped" in logcat), so a launch requested by a
// background app inside that window is only executed once the lock expires: wait for the
// package to actually come to the front instead of sleeping a fixed time.
// zh-CN: 先回到桌面再拉起 AutoJs6, 使每次运行都从同一次窗口切换开始. API <= 28 上 home() 注入的 HOME 键
// 会开启系统 5 s 的应用切换锁 (logcat 中为 "Activity start request from <uid> stopped"), 后台应用在该窗口内
// 发起的启动要等锁过期后才会执行, 因此这里等待包名真正前置, 而非固定时长的 sleep.
function bringAutoJs6ToFront() {
    home();
    sleep(1000);
    app.launch(autojs.packageName);
    const landed = wait(() => currentPackage() === autojs.packageName, 8000);
    sleep(landed ? 1000 : 500);
    return landed;
}

device.wakeUpIfNeeded();
bringAutoJs6ToFront();

/*
 * Environment snapshot. When the "finds something on screen" checks below fail, this is what
 * explains it (screen off, keyguard, no window roots, service enabled but not operational, ...).
 *
 * zh-CN: 环境快照. 下方 "屏幕上能找到内容" 的检查失败时, 由这里解释原因
 * (熄屏, 锁屏, 无窗口根节点, 服务已启用但不可操作等).
 */
function safely(body) {
    try {
        return String(body());
    } catch (e) {
        return `error: ${e}`;
    }
}

// Guard mode (AutoJs6 settings > Script running > Guard mode) blacklists AutoJs6's own windows
// for selectors and node actions. This script deliberately runs against AutoJs6's main screen,
// so with guard mode on every "finds something on screen" check fails by design.
// zh-CN: 保护模式 (AutoJs6 设置 > 脚本运行 > 保护模式) 会让选择器与节点动作忽略 AutoJs6 自身的窗口.
// 本脚本刻意以 AutoJs6 主界面为目标, 因此保护模式开启时所有 "屏幕上能找到内容" 的检查都会按设计失败.
function isGuardModeEnabled() {
    return Boolean(Packages.org.autojs.autojs.runtime.accessibility.AccessibilityConfig.isUnintendedGuardEnabled());
}

// One entry per accessibility window: type, package of its root, active / focused flags.
// zh-CN: 每个无障碍窗口一项: 类型, 根节点所属包名, 活动 / 焦点标志.
function describeWindows() {
    const typeNames = { 1: "APPLICATION", 2: "INPUT_METHOD", 3: "SYSTEM", 4: "ACCESSIBILITY_OVERLAY", 5: "SPLIT_SCREEN_DIVIDER", 6: "MAGNIFICATION_OVERLAY" };
    return auto.windows.map((window) => {
        const root = window.getRoot();
        const packageName = root === null ? "no-root" : String(root.getPackageName());
        if (root !== null && device.sdkInt < 33) {
            try {
                root.recycle();
            } catch (e) {
                // Already recycled or unsupported; nothing to release.
            }
        }
        const flags = `${window.isActive() ? " active" : ""}${window.isFocused() ? " focused" : ""}`;
        return `[${typeNames[window.getType()] || window.getType()} ${packageName}${flags}]`;
    }).join(" ") || "(none)";
}

function describeEnvironment() {
    const Secure = android.provider.Settings.Secure;
    const resolver = context.getContentResolver();
    return [
        `device=${device.brand} ${device.model} api=${device.sdkInt}`,
        `hasService=${safely(() => auto.hasService())} hasInstance=${safely(() => auto.hasInstance())} isRunning=${safely(() => auto.isRunning())} isOperational=${safely(() => auto.isOperational())}`,
        `screenOn=${safely(() => device.isScreenOn())} keyguardLocked=${safely(() => context.getSystemService(context.KEYGUARD_SERVICE).isKeyguardLocked())} currentPackage=${safely(() => currentPackage())} guardMode=${safely(isGuardModeEnabled)}`,
        `windowRoots=${safely(() => auto.windowRoots.length)} rootInActiveWindow=${safely(() => auto.rootInActiveWindow !== null)} windows=${safely(() => auto.windows.length)} ${safely(describeWindows)}`,
        `accessibility_enabled=${safely(() => Secure.getInt(resolver, "accessibility_enabled", -1))} enabled_accessibility_services=${safely(() => Secure.getString(resolver, "enabled_accessibility_services"))}`,
    ].join("\n  ");
}

console.log(`environment:\n  ${describeEnvironment()}`);

check("auto.service is available after auto()", () => auto.service !== null);
check("WINDOW_STATE_CHANGED events were delivered", () => windowEvents.length > 0);
check("screen is on and unlocked", () => device.isScreenOn() && !context.getSystemService(context.KEYGUARD_SERVICE).isKeyguardLocked());
if (!check("guard mode is off (it makes selectors ignore AutoJs6's own windows, which this script searches)", () => !isGuardModeEnabled())) {
    console.warn("[HINT] disable guard mode in AutoJs6 settings (Script running > Guard mode) and rerun; the checks that search the screen are expected to fail below");
}
check("auto.rootInActiveWindow is available", () => auto.rootInActiveWindow !== null);
check("auto.windowRoots is not empty", () => auto.windowRoots.length > 0);

// Root queries and their settings (roadmap P1.4, WindowRootPolicy): a window filter selects from
// auto.windows, auto.root honours it while auto.rootInActiveWindow does not; setMode('fast') reads the
// root cached from the latest window event; the 'appWindowsFallback' flag only matters while the
// system does not know the active window, so here it must simply not change the result.
// auto.setWindowFilter(null) installs an accept-all filter (every window), so the default
// "active window only" state is restored through the bridge, whose filter is nullable.
// zh-CN: 根节点查询及其设置 (路线图 P1.4, WindowRootPolicy): 窗口过滤器从 auto.windows 中选取, auto.root 遵循它而
// auto.rootInActiveWindow 不遵循; setMode('fast') 读取最近一次窗口事件缓存的根节点; 'appWindowsFallback' 标志只在系统
// 不知道活动窗口时起作用, 因此这里它不应改变结果. auto.setWindowFilter(null) 装的是 "接受全部窗口" 的过滤器,
// 因此默认的 "仅活动窗口" 状态经 bridge (其过滤器可为 null) 恢复.
function clearWindowFilter() {
    runtime.accessibilityBridge.setWindowFilter(null);
}
check("auto.windowRoots with a rejecting window filter is empty", () => {
    auto.setWindowFilter(() => false);
    try {
        return auto.windowRoots.length === 0 && auto.root === null;
    } finally {
        clearWindowFilter();
    }
});
check("auto.rootInActiveWindow ignores the window filter", () => {
    auto.setWindowFilter(() => false);
    try {
        return auto.rootInActiveWindow !== null;
    } finally {
        clearWindowFilter();
    }
});
check("auto.windowRoots with a package window filter yields only that package", () => {
    auto.setWindowFilter((window) => {
        const root = window.getRoot();
        return root !== null && String(root.getPackageName()) === autojs.packageName;
    });
    try {
        const roots = auto.windowRoots;
        return roots.length > 0 && roots.every((root) => String(root.packageName()) === autojs.packageName);
    } finally {
        clearWindowFilter();
    }
});
check("auto.setMode('fast') provides a root after a window event", () => {
    auto.setMode("fast");
    try {
        return auto.rootInActiveWindow !== null && auto.windowRoots.length > 0;
    } finally {
        auto.setMode("normal");
    }
});
check("auto.setFlags(['appWindowsFallback']) leaves the roots unchanged while the active window is known", () => {
    auto.setFlags(["appWindowsFallback"]);
    try {
        return auto.windowRoots.length === 1 && auto.rootInActiveWindow !== null;
    } finally {
        auto.setFlags([]);
    }
});

/*
 * Android usually emits more than one WINDOW_STATE_CHANGED event per window transition
 * (distinct eventTime values a few dozen milliseconds apart), and the service is configured
 * with notificationTimeout=0, so nothing is coalesced. Scripts that need "one callback per
 * window" should de-duplicate on (packageName, className) within a short time window:
 *
 * zh-CN: Android 通常会为一次窗口切换发出多个 WINDOW_STATE_CHANGED 事件 (eventTime 相差几十毫秒),
 * 且服务配置 notificationTimeout=0, 系统不会合并. 需要 "每个窗口只回调一次" 的脚本应按
 * (packageName, className) 在短时间窗口内去重:
 */
function distinctWindows(events, windowMillis) {
    const kept = [];
    events.forEach((event) => {
        const last = kept[kept.length - 1];
        const same = last && last.packageName === event.packageName && last.className === event.className;
        if (!same || event.eventTime - last.eventTime > windowMillis) kept.push(event);
    });
    return kept;
}

console.log(`WINDOW_STATE_CHANGED: ${windowEvents.length} raw, ${distinctWindows(windowEvents, 500).length} distinct`);
windowEvents.forEach((event, index) => {
    // eventTime is stamped by the sending window, so it is not monotonic across windows.
    // zh-CN: eventTime 由发送事件的窗口打点, 跨窗口时不保证单调.
    const delta = index === 0 ? 0 : event.eventTime - windowEvents[index - 1].eventTime;
    console.log(`  #${index + 1} ${delta < 0 ? "" : "+"}${delta}ms ${event.packageName}/${event.className}`);
});

/*
 * 1b. The same de-duplication is available as registerEvent options (one callback slot per
 * event type and script, so re-registering replaces the raw callback above):
 * distinct drops a repeated (package, class) within the window, debounce delivers only the
 * last event of a burst once it has been quiet for the given time.
 *
 * zh-CN: 同样的去重可作为 registerEvent 的选项 (每种事件类型每个脚本一个回调槽, 重新注册即替换上面的原始回调):
 * distinct 在窗口内丢弃重复的 (包名, 类名), debounce 在一串事件安静给定时长后只投递最后一个.
 */
function transitionWindows() {
    bringAutoJs6ToFront();
}

function recordWindowEvents(target) {
    return function (event) {
        target.push({ packageName: String(event.packageName), className: String(event.className), eventTime: event.eventTime });
    };
}

const distinctEvents = [];
auto.registerEvent("WINDOW_STATE_CHANGED", recordWindowEvents(distinctEvents), { distinct: "window", within: 500 });
transitionWindows();
check("registerEvent({ distinct: 'window' }) delivered events", () => distinctEvents.length > 0);
// eventTime is stamped at send time while the option works on the delivery clock, hence the slightly smaller window here.
// zh-CN: eventTime 在发送时打点而选项按投递时刻计时, 因此这里用略小的窗口校验.
check("registerEvent({ distinct: 'window' }) delivered no repeated window within 400 ms", () => distinctWindows(distinctEvents, 400).length === distinctEvents.length);
console.log(`WINDOW_STATE_CHANGED with { distinct: "window", within: 500 }: ${distinctEvents.length} delivered`);

const debouncedEvents = [];
auto.registerEvent("WINDOW_STATE_CHANGED", recordWindowEvents(debouncedEvents), { debounce: 300 });
transitionWindows();
check("registerEvent({ debounce: 300 }) delivered at least one and no more than the raw events", () => debouncedEvents.length > 0 && debouncedEvents.length <= windowEvents.length);
console.log(`WINDOW_STATE_CHANGED with { debounce: 300 }: ${debouncedEvents.length} delivered`);

/* 2. Global entry points exist. */

check("text() is defined", () => typeof text === "function");
check("textMatch() and textMatches() are defined", () => typeof textMatch === "function" && typeof textMatches === "function");
check("pickup() is defined", () => typeof pickup === "function");
check("detect() is defined", () => typeof detect === "function");
check("wait() is defined", () => typeof wait === "function");
check("click() is defined", () => typeof click === "function");
check("scrollDown() is defined", () => typeof scrollDown === "function");
check("auto.registerEvent() is defined", () => typeof auto.registerEvent === "function");

/* 3. Selectors and search. */

check("textMatch(/.+/).findOne(1000) finds something on screen", () => textMatch(/.+/).findOne(1000) !== null);
check("textMatches(/.+/).findOne(1000) finds something on screen", () => textMatches(/.+/).findOne(1000) !== null);
check("text('anything').findOnce() returns null without throwing", () => text("this text should never exist on screen").findOnce() === null);
check("find(1) returns exactly one node with DFS", () => textMatches(/.+/).find(1).size() === 1);
check("find(1) returns exactly one node with BFS", () => textMatches(/.+/).algorithm("BFS").find(1).size() === 1);
check("find(2) returns at most two nodes with BFS", () => textMatches(/.+/).algorithm("BFS").find(2).size() <= 2);
check("find(0) returns an empty collection", () => textMatches(/.+/).find(0).size() === 0);

/* 4. pickup / detect / compass. */

check("pickup(/.+/) finds something on screen", () => pickup(/.+/) !== null);
check("pickup() returns null without throwing when nothing matches", () => pickup(/this text should never exist on screen/) === null);

const anchor = pickup(/AutoJs6/) || pickup(/.+/);

check("detect(w, 'p') does not throw", () => doesNotThrow(() => detect(anchor, "p")));
check("detect(w, '%') passes the node through", () => detect(anchor, "%") === anchor);
check("compass('p') returns the parent or null", () => {
    const parent = anchor.compass("p");
    return parent === null || parent.childCount() > 0;
});
check("compass('k9') returns a clickable node or null", () => {
    const clickable = anchor.compass("k9");
    return clickable === null || clickable.clickable() === true;
});
check("compass('c0') on a leaf returns null", () => {
    let leaf = anchor;
    while (leaf.childCount() > 0) leaf = leaf.child(0);
    return leaf.compass("c0") === null;
});

/* 5. wait. */

check("wait(content, 1000) resolves to true for a visible element", () => wait(anchor.content(), 1000) === true);
check("wait(missing, 400) resolves to false", () => wait("this text should never exist on screen", 400) === false);
check("wait(fn, 3) counts attempts when limit < 100", () => {
    let attempts = 0;
    const result = wait(() => ++attempts >= 3, 3, 10);
    return result === true && attempts === 3;
});
check("wait(cond, {then, else}) callback object form", () => {
    let branch = "";
    wait("this text should never exist on screen", 300, { then: () => branch = "then", else: () => branch = "else" });
    return branch === "else";
});

/* 6. Actions. */

check("click(UiObject) does not throw", () => doesNotThrow(() => click(anchor)));
check("scrollDown() does not throw", () => doesNotThrow(() => scrollDown()));

/*
 * 7. Service restart (roadmap P1.1): selectors and auto.stateListener across a service restart.
 *
 * Only runs when the service can be restarted without user interaction, i.e. AutoJs6 is
 * configured to start the service with root or secure settings and that way is available.
 * Otherwise the checks are skipped (not failed), because bringing the service back would
 * require the user to toggle it in system settings.
 *
 * zh-CN: 服务重启 (路线图 P1.1): 服务重启前后的选择器与 auto.stateListener.
 * 仅当服务可以无人值守地重启 (AutoJs6 已配置以 root 或安全设置方式启动服务且该方式可用) 时运行,
 * 否则跳过 (不计失败), 因为恢复服务需要用户在系统设置中手动开关.
 */

const stateLog = [];

auto.stateListener({
    onConnected: () => stateLog.push("connected"),
    onDisconnected: () => stateLog.push("disconnected"),
});

function canRestartServiceUnattended() {
    const A11yKeys = Packages.org.autojs.autojs.core.pref.keys.A11yKeys;
    const RootUtils = Packages.org.autojs.autojs.util.RootUtils;
    const SecureSettings = Packages.org.autojs.autojs.util.SettingsUtils.SecureSettings;
    const WrappedShizuku = Packages.org.autojs.autojs.runtime.api.WrappedShizuku.INSTANCE;
    return Boolean(A11yKeys.ENABLE_A11Y_SERVICE_WITH_ROOT_ACCESS.get() && RootUtils.isRootAvailable()
        || A11yKeys.ENABLE_A11Y_SERVICE_WITH_SECURE_SETTINGS.get() && SecureSettings.isGranted(context)
        || A11yKeys.ENABLE_A11Y_SERVICE_WITH_SHIZUKU.get() && WrappedShizuku.isOperational());
}

if (canRestartServiceUnattended()) {
    const tool = new Packages.org.autojs.autojs.core.accessibility.AccessibilityTool(context);
    const serviceBefore = auto.service;

    // Stop and start are issued separately with the unbind observed in between. Re-enabling the
    // service before the system has unbound it is coalesced into "still enabled" on some platforms
    // (seen on API 26), in which case nothing restarts and the listener never fires.
    // AccessibilityTool.restartService() applies the same wait internally.
    // zh-CN: 停止与启动分开执行, 中间观测解绑. 若在系统解绑前重新启用, 部分平台 (API 26 上观察到)
    // 会把两次写入合并为 "仍然启用", 实际不会重启, 监听器也不会触发. AccessibilityTool.restartService() 内部有同样的等待.
    check("stopService(false) succeeds", () => tool.stopService(false) === true);
    check("service is unbound within 5 s after stopService", () => wait(() => !auto.hasInstance(), 5000) === true);
    check("service is not listed in enabled_accessibility_services after stopService", () => auto.hasService() === false);
    check("startService(false) succeeds", () => tool.startService(false) === true);
    check("service is running again within 10 s", () => wait(() => auto.isRunning(), 10000) === true);
    check("a new service instance is bound", () => auto.service !== null && auto.service !== serviceBefore);
    check("stateListener saw disconnected followed by connected", () => {
        const disconnected = stateLog.indexOf("disconnected");
        return disconnected >= 0 && stateLog.lastIndexOf("connected") > disconnected;
    });

    // Window content can take a moment to become available again after a rebind. On API 26 (AVD)
    // the system does not know the active window after the rebind at all until the next window
    // change, so when the root stays unavailable the window list is dumped and a window change is
    // provoked to tell that case apart from a dead service (roadmap P1.4 decides on a fallback).
    // zh-CN: 重新绑定后窗口内容可能需要片刻才能再次可用. API 26 (AVD) 上系统在重新绑定后完全不知道活动窗口,
    // 直到下一次窗口变化; 因此根节点持续不可用时打印窗口列表并主动制造一次窗口变化, 以区分 "服务已死" 的情况
    // (路线图 P1.4 决定兜底策略).
    if (!check("rootInActiveWindow is available again within 5 s", () => wait(() => auto.rootInActiveWindow !== null, 5000) === true)) {
        console.warn(`windows after restart: ${safely(describeWindows)}`);
        app.launch(autojs.packageName);
        const recovered = wait(() => auto.rootInActiveWindow !== null, 5000);
        console.warn(`rootInActiveWindow after a window change (re-launching AutoJs6): ${recovered ? "available" : "still unavailable"}`);
    }
    check("pickup(/.+/) still finds something after restart", () => wait(() => pickup(/.+/) !== null, 5000) === true);
    check("new UiSelector() still finds something after restart", () => new UiSelector().textMatches(/.+/).findOne(3000) !== null);
    check("text().findOne(timeout) still works after restart", () => textMatches(/.+/).findOne(3000) !== null);
    console.log(`stateListener log: ${JSON.stringify(stateLog)}`);
} else {
    console.warn("[SKIP] service restart checks: no unattended start strategy (enable root, secure-settings or Shizuku start in AutoJs6 settings)");
}

/* Summary. */

const failed = results.filter((result) => !result.passed);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
if (failed.length > 0) {
    console.error(`environment at the end of the run:\n  ${describeEnvironment()}`);
    throw new Error(`${failed.length} smoke check(s) failed: ${failed.map((result) => `${result.name}${result.detail ? ` (${result.detail})` : ""}`).join("; ")}`);
}
