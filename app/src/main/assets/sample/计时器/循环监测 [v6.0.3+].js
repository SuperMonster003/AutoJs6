const KEY = "wait_for_test";
const timeout = 3; // seconds

toast(`${timeout} seconds counting down...`, 'l');

recorder.save(KEY);

// @EmptyLoopBody
// noinspection StatementWithEmptyBodyJS
while (recorder.isLessThan(KEY, timeout * 1e3)) ;

toast(`Finished.\nSpent ${recorder.load(KEY) / 1e3} s.`, 'l', 'f');