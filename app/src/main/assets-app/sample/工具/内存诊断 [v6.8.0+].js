// 内存诊断: 对比强制回收前后的内存, 并列出已结束但未被回收的脚本.
(function () {
    let show = (title, s) => console.log(title + ': Java 堆 ' + fmt.bytes(s.javaHeapUsed) + ' / ' + fmt.bytes(s.javaHeapMax)
        + ', 原生堆 ' + fmt.bytes(s.nativeHeapAllocated) + ', PSS ' + fmt.bytes(s.pss)
        + ', 脚本线程 ' + s.threads + ', 未回收的脚本运行时 ' + s.retainedRuntimes);

    /* 制造一些临时对象, 让回收前后的差别更明显. */
    let garbage = [];
    for (let i = 0; i < 100000; i++) garbage.push({ index: i, text: 'item ' + i });
    garbage = null;

    let before = memory.snapshot();
    show('回收前', before);

    /* collect 会阻塞当前线程, 这里最多等待 3 秒, 让已结束的脚本运行时被回收. */
    let after = memory.collect(3000);
    show('回收后', after);
    console.log('Java 堆减少了 ' + fmt.bytes(Math.max(before.javaHeapUsed - after.javaHeapUsed, 0)));

    /* 回收后仍在列表中的脚本运行时被其他对象持有, 通常意味着内存泄漏. */
    let retained = memory.retained();
    if (retained.length === 0) {
        console.log('没有未回收的脚本运行时');
    }
    retained.forEach((r) => {
        console.log('未回收: ' + r.name + ' (' + r.engine + ', ' + r.exitKind + '), 已结束 ' + Math.round(r.elapsedMs / 1000) + ' 秒');
    });

    /* Node.js, Bun, QuickJS, Lua 与 Python 等插件引擎已结束但未回收的桥接会话数. */
    let bridge = Object.keys(after.bridgeSessions).filter((engine) => after.bridgeSessions[engine] > 0);
    console.log(bridge.length === 0 ? '没有未回收的桥接会话'
        : '未回收的桥接会话: ' + bridge.map((engine) => engine + ' ' + after.bridgeSessions[engine]).join(', '));

    console.launch();
})();
