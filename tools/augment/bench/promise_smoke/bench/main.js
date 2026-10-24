// ROADMAP P4.2: cost of the global Promise on the device, in a project with the continuation feature. Each case queues
// `n` operations, then awaits a Promise settled after them: the script suspends and its looper runs the queued reactions
// in order (wait() is not used for this, as SettlementLatch scans all pending callbacks for each one it takes). Prints
// "AJ6P42B <case> <us per operation>", the median of 7 runs after 3 warm-up runs.
var MARK = 'AJ6P42B';
var sink = 0;
var fin = 0;

var cases = [
    ['resolve+then', 500, function (n) { for (var i = 0; i < n; i++) Promise.resolve(i).then(function (v) { sink += v; fin++; }); }],
    ['new+then3', 300, function (n) { for (var i = 0; i < n; i++) new Promise(function (r) { r(i); }).then(function (v) { return v + 1; }).then(function (v) { return v * 2; }).then(function (v) { sink += v; fin++; }); }],
    ['reject+catch', 500, function (n) { for (var i = 0; i < n; i++) Promise.reject(new Error('x')).catch(function () { fin++; }); }],
    ['then-throws+catch', 300, function (n) { for (var i = 0; i < n; i++) Promise.resolve(i).then(function () { throw new TypeError('t'); }).catch(function () { fin++; }); }],
    ['all10', 60, function (n) { for (var i = 0; i < n; i++) Promise.all([1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(function (x) { return Promise.resolve(x); })).then(function (a) { sink += a.length; fin++; }); }],
    ['wait-resolved', 300, function (n) { for (var i = 0; i < n; i++) { sink += Promise.resolve(i).wait(); fin++; } }],
    ['await-resolved', 300, function (n) { for (var i = 0; i < n; i++) { sink += Promise.resolve(i).await(); fin++; } }],
];

// Waits (suspended, so the looper runs the queued reactions) until all `n` operations of the case finished.
function settle(n) {
    while (fin < n) new Promise(function (r) { setTimeout(r, 0); }).await();
}

function runOnce(c) {
    fin = 0;
    var t0 = java.lang.System.nanoTime();
    c[2](c[1]);
    settle(c[1]);
    return (java.lang.System.nanoTime() - t0) / 1000 / c[1];
}

// A plain loop: a coroutine cannot suspend inside a callback of the native Array.prototype.forEach.
for (var k = 0; k < cases.length; k++) {
    var c = cases[k];
    for (var w = 0; w < 3; w++) runOnce(c);
    var samples = [];
    for (var r = 0; r < 7; r++) samples.push(runOnce(c));
    samples.sort(function (a, b) { return a - b; });
    console.log(MARK + ' ' + c[0] + ' ' + samples[3].toFixed(2));
}
console.log(MARK + ' done ' + sink);
