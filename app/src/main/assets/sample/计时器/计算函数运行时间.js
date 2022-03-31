// Sample A - Simple time-consumed function

const f = function test() {
    sleep(500, '±200');
};

console.log(`Function "${f.name}" spent ${recorder(f)} ms`);

// Sample B - Fibonacci without memoization

const fibonacci = (x) => x <= 2 ? 1 : fibonacci(x - 2) + fibonacci(x - 1);

recorder.save('fibonacci');

let num = 1;
let limit = 3e3; // 3 seconds

while (1) {
    let et = recorder(fibonacci.bind(null, num));
    toastLog(`Fibonacci ${num} spent ${et} ms`, false, 'f');
    if (et > limit) {
        break;
    }
    num += 1;
}

toastLog('Finished');