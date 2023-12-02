const KEY = "key_test";

recorder.save(KEY);

sleep(1e3, '±700');

toastLog(`Slept for ${recorder.load(KEY)} ms`, 'l');