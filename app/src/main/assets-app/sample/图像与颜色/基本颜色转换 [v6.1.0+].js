let color = '#BF00363A';
let colorInt = colors.toInt(color);

let cR = colors.red(colorInt);
let cG = colors.green(color);
let cB = colors.blue(colorInt);
let cA = colors.alpha(color);

console.log(`Color string: ${color}`);
console.log(`Color int: ${colorInt}`);
console.log(`Color rgba: [ ${[`R: ${cR}`, `G: ${cG}`, `B: ${cB}`, `A: ${cA}`].join(', ')} ]`);

let test = o => console.log(`Test instance ${o ? `passed` : `failed`}`);

test(colors.red(color) === colors.red(colorInt));
test(colors.toInt(color) === colors.toInt(colorInt));

test(colors.argb('#FF224466') === colors.rgba('#224466FF'));
test(colors.argb(color) === colors.rgba(cR, cG, cB, cA));

test(colors.toString(color) === colors.toString(colorInt));
test(colors.toString('#224466') === colors.toString('#FF224466', 6 /* none */));
test(colors.toString('#FF224466') === colors.toString('#224466', 8 /* keep */));

console.launch();