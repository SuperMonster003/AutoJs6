/**
 * Smoke test of the legacy (synchronous) accessibility automation API surface.
 *
 * Run this on a real device before and after each phase of
 * docs/dev/accessibility-automation-roadmap.md. It covers the entry points that
 * must keep their behavior: text().findOne(timeout), find(n), pickup, detect,
 * compass, wait, click(UiObject), scrollDown(), auto.registerEvent.
 *
 * zh-CN: 传统 (同步) 无障碍自动化 API 的冒烟测试.
 * 在 docs/dev/accessibility-automation-roadmap.md 的每个阶段前后于真机运行一次.
 * 覆盖必须保持行为不变的入口: text().findOne(timeout), find(n), pickup, detect,
 * compass, wait, click(UiObject), scrollDown(), auto.registerEvent.
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

home();
sleep(1000);
app.launch(autojs.packageName);
sleep(1500);

check("auto.service is available after auto()", () => auto.service !== null);
check("WINDOW_STATE_CHANGED events were delivered", () => windowEvents.length > 0);

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
    const delta = index === 0 ? 0 : event.eventTime - windowEvents[index - 1].eventTime;
    console.log(`  #${index + 1} +${delta}ms ${event.packageName}/${event.className}`);
});

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

/* Summary. */

const failed = results.filter((result) => !result.passed);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
if (failed.length > 0) {
    throw new Error(`${failed.length} smoke check(s) failed: ${failed.map((result) => result.name).join("; ")}`);
}
