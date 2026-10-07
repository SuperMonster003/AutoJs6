// 感知色彩函数: OKLCH 调整, 对比度, 和谐色, 色阶, 派生配色与色觉模拟.
(function () {
    let seed = '#2196F3';
    let hex = c => colors.toHex(c);

    /* 在 OKLCH 中单独调整亮度或色相. */
    let [ l, c, h ] = colors.toOklch(seed);
    console.log('更亮: ' + hex(colors.oklch(Math.min(l + 0.15, 1), c, h)));
    console.log('色相旋转 180 度: ' + hex(colors.oklch(l, c, h + 180)));

    /* 白色背景上的文字颜色, 对比度不低于 4.5 (WCAG AA). */
    let text = colors.ensureContrast('#FFC107', 'white');
    console.log('白底文字: ' + hex(text) + ', 对比度 ' + colors.contrast(text, 'white').toFixed(2));

    console.log('三分色: ' + colors.harmony(seed, 'triadic').map(hex).join(' '));
    console.log('色阶: ' + colors.tones(seed, 5).map(hex).join(' '));

    let { accent, dark } = colors.derive(seed);
    console.log('派生配色: 强调色 ' + hex(accent) + ', 深色 ' + hex(dark));
    console.log('夜间对应色: ' + hex(colors.nightVariant(seed)));

    /* 红色与绿色在绿色盲下的感知距离明显缩短. */
    let normal = colors.distance('red', 'green', 'oklab');
    let simulated = colors.distance(colors.simulate('red', 'deuteranopia'), colors.simulate('green', 'deuteranopia'), 'oklab');
    console.log('红绿感知距离: 正常 ' + normal.toFixed(1) + ', 绿色盲 ' + simulated.toFixed(1));

    console.launch();
})();
