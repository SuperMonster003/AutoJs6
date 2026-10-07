// Smoke test of colors.theme (roadmap P6.2): libraries, activation, next, script states, deactivation and reset.
// The script state part needs a user intelligent library named "Script smoke" whose state source is "Script states"
// (idle, busy) with different colors for the two states; it is skipped otherwise. Changes the theme color.
// zh-CN: colors.theme 的冒烟测试 (Roadmap P6.2): 库列表, 激活, 换组, 脚本状态, 停用与恢复默认.
// 脚本状态部分需要一个名为 "Script smoke" 的用户智能库, 其状态源为 "脚本状态" (idle, busy) 且两个状态颜色不同; 否则跳过该部分. 会修改主题色.

(function () {

    const results = [];

    function check(name, condition, detail) {
        results.push(`${condition ? 'PASS' : 'FAIL'} ${name}${detail === undefined ? '' : ` (${detail})`}`);
    }

    function throws(fn) {
        try {
            fn();
            return false;
        } catch (e) {
            return true;
        }
    }

    const libraries = colors.theme.libraries();
    const material = libraries.find(library => library.id === 0x100);
    check('libraries', libraries.length >= 8 && material && material.intelligent && material.kind === 'preset' && material.size > 0,
        `${libraries.length} libraries, ${libraries.filter(library => library.intelligent).length} intelligent`);

    colors.theme.activate(0x100);
    const first = colors.theme.current();
    check('activate', first.intelligent && first.libraryId === 0x100 && first.groupId !== null, `${first.libraryName} / ${first.groupName}`);
    check('next', colors.theme.next() && colors.theme.current().groupId !== first.groupId, colors.theme.current().groupName);
    check('state needs script states', throws(() => colors.theme.state('busy')));
    check('activate rejects plain libraries', throws(() => colors.theme.activate(0x02)));

    const scripted = libraries.find(library => library.intelligent && library.kind !== 'preset' && library.name === 'Script smoke');
    if (scripted) {
        colors.theme.activate(scripted.id);
        colors.theme.state('busy');
        const busy = colors.theme.current();
        colors.theme.state('idle');
        const idle = colors.theme.current();
        check('script states', busy.state === 'busy' && idle.state === 'idle' && busy.color !== idle.color,
            `busy ${colors.toHex(busy.color)}, idle ${colors.toHex(idle.color)}`);
        check('state rejects unknown names', throws(() => colors.theme.state('nope')));
    } else {
        results.push('SKIP script states (no intelligent library named "Script smoke")');
    }

    check('deactivate', colors.theme.deactivate() && !colors.theme.current().intelligent && !colors.theme.deactivate());

    colors.theme.reset();
    sleep(1000);
    check('reset', colors.theme.current().color === colors.toInt('#FFDEAD'), colors.theme.current().summary);

    results.forEach(line => console.log(`colors-theme-control: ${line}`));
    console.log(`colors-theme-control: ${results.filter(line => line.startsWith('PASS')).length} / ${results.filter(line => !line.startsWith('SKIP')).length} passed`);

})();
