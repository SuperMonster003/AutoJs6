// Appendix C item 13 (roadmap P6.3): 100 calls of colors.theme.set in a tight loop make one visible change, the last
// color. Afterwards "Undo script change" on the theme color page restores the selection before the script, including
// an intelligent library that was active. Changes the theme color.
// zh-CN: 附录 C 第 13 项 (Roadmap P6.3): 紧密循环中 100 次调用 colors.theme.set 只产生一次可见变化, 即最后一个颜色.
// 之后主题色页面的 "撤销脚本修改" 恢复脚本运行前的选择, 包括当时激活的智能库. 会修改主题色.

(function () {

    const before = colors.theme.current();
    console.log(`colors-theme-loop: before ${before.summary} (intelligent: ${before.intelligent})`);

    const palette = colors.tones('#3F51B5', 10);
    for (let i = 0; i < 100; i++) {
        colors.theme.set(palette[i % palette.length]);
    }
    const last = palette[99 % palette.length];
    console.log(`colors-theme-loop: pending ${colors.toHex(colors.theme.get())}, expected ${colors.toHex(last)}`);

    // The merge window is 500 ms. zh-CN: 合并窗口为 500 ms.
    sleep(1000);
    const after = colors.theme.current();
    console.log(`colors-theme-loop: after ${after.summary} (intelligent: ${after.intelligent})`);
    console.log(`colors-theme-loop: ${after.color === last && !after.intelligent ? 'PASS' : 'FAIL'} the last color applies and the intelligent library stops`);

})();
