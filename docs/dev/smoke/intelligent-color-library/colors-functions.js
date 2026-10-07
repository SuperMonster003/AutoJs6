// Smoke test of the perceptual colors functions (roadmap P6.1). Prints PASS / FAIL lines; changes nothing.
// zh-CN: 感知色彩相关 colors 函数的冒烟测试 (Roadmap P6.1). 输出 PASS / FAIL 行; 不做任何修改.

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

    const teal = colors.toInt('#00897B');
    const white = colors.toInt('#FFFFFF');

    const lab = colors.toOklab(teal);
    check('toOklab', lab.length === 3 && lab[0] > 0.5 && lab[0] < 0.6, lab.map(v => v.toFixed(4)).join(', '));
    check('oklab round trip', colors.oklab(lab) === teal && colors.oklab(lab[0], lab[1], lab[2]) === teal);
    const lch = colors.toOklch(teal);
    check('oklch round trip', colors.oklch(lch[0], lch[1], lch[2]) === teal, lch.map(v => v.toFixed(3)).join(', '));
    check('oklch alpha and percentage', colors.alpha(colors.oklch('50%', 0.1, 270, 0.5)) === 128);
    check('oklch rejects lightness above 1', throws(() => colors.oklch(1.5, 0.1, 0)));

    const text = colors.ensureContrast(teal, white);
    check('ensureContrast 4.5 by default', colors.contrast(text, white) >= 4.5, colors.toHex(text));
    check('ensureContrast keeps enough contrast', colors.ensureContrast('#000000', white, 3) === colors.toInt('#000000'));
    check('ensureContrast needs an opaque background', throws(() => colors.ensureContrast(teal, '#80FFFFFF')));

    const triadic = colors.harmony(teal, 'triadic');
    check('harmony', triadic.length === 3 && triadic[0] === teal, triadic.map(c => colors.toHex(c)).join(' '));
    check('harmony rejects unknown types', throws(() => colors.harmony(teal, 'square')));
    check('tones', colors.tones(teal).length === 10 && colors.tones(teal, 5).length === 5);
    check('tones rejects 1', throws(() => colors.tones(teal, 1)));

    const derived = colors.derive(teal);
    check('derive', derived.primary === teal && colors.contrast(derived.accent, '#FAFAFA') >= 3 && colors.contrast(derived.accent, '#212121') >= 3,
        `accent ${colors.toHex(derived.accent)}, dark ${colors.toHex(derived.dark)}`);
    check('nightVariant', colors.nightVariant('#E0F2F1') !== colors.toInt('#E0F2F1'), colors.toHex(colors.nightVariant('#E0F2F1')));
    check('simulate', colors.simulate('#FF0000', 'protanopia') !== colors.toInt('#FF0000'), colors.toHex(colors.simulate('#FF0000', 'protanopia')));
    check('simulate rejects unknown deficiencies', throws(() => colors.simulate(teal, 'achromatopsia')));

    check('distance oklab', Math.abs(colors.distance('#000000', '#FFFFFF', 'oklab') - 255) < 0.01);
    check('isSimilar oklab', colors.isSimilar('#00897B', '#008A7B', { threshold: 4, algorithm: 'oklab' }));

    // 70% red and 30% blue. zh-CN: 70% 红色与 30% 蓝色.
    const bitmap = android.graphics.Bitmap.createBitmap(120, 100, android.graphics.Bitmap.Config.ARGB_8888);
    const canvas = new android.graphics.Canvas(bitmap);
    const paint = new android.graphics.Paint();
    paint['setColor(int)'](colors.toInt('#E53935'));
    canvas.drawRect(0, 0, 84, 100, paint);
    paint['setColor(int)'](colors.toInt('#1E88E5'));
    canvas.drawRect(84, 0, 120, 100, paint);
    const stream = new java.io.ByteArrayOutputStream();
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
    const image = images.fromBytes(stream.toByteArray());
    const extracted = colors.extract(image, { count: 3 });
    check('extract', extracted.length === 2 && extracted[0] === colors.toInt('#E53935') && extracted[1] === colors.toInt('#1E88E5'),
        extracted.map(c => colors.toHex(c)).join(' '));
    check('extract region', colors.extract(image, { region: [90, 0, 30, 100] })[0] === colors.toInt('#1E88E5'));
    check('extract rejects count 17', throws(() => colors.extract(image, { count: 17 })));
    image.recycle();

    results.forEach(line => console.log(`colors-functions: ${line}`));
    console.log(`colors-functions: ${results.filter(line => line.startsWith('PASS')).length} / ${results.length} passed`);

})();
