// 选择一个智能颜色库并激活, 每隔 3 秒换到下一组, 结束后可恢复之前的主题色.
// 主题色页面打开期间, 智能颜色库暂停换组.
(function () {
    let libraries = colors.theme.libraries().filter(lib => lib.intelligent);
    let index = dialogs.select('选择智能颜色库', libraries.map(lib => `${lib.name} (${lib.size} 组)`));
    if (index < 0) {
        return;
    }
    let before = colors.theme.current();

    colors.theme.activate(libraries[index].id);
    toastLog(colors.theme.current().summary);
    for (let i = 0; i < 5; i++) {
        sleep(3000);
        colors.theme.next();
        toastLog(colors.theme.current().summary);
    }

    if (!dialogs.confirm('恢复主题色', '是否恢复之前的主题色?')) {
        return;
    }
    if (before.intelligent) {
        colors.theme.activate(before.libraryId, { group: before.groupId });
    } else {
        /* 设置颜色时, 激活的智能颜色库会自动停用. */
        colors.theme.set(before.color, { accent: before.accent, dark: before.dark });
    }
    toastLog('已恢复: ' + colors.theme.current().summary);
})();
