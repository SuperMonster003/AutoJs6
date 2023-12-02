const KEY = "key_test";

recorder.save(KEY);

!function action() {
    sleep(1e3, '±700');
}();

let et = recorder.load(KEY);

if (et < 1e3) {
    toastLog('Abnormal elapsed time');
} else {
    toastLog('Normal elapsed time');
}