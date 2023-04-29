function printMessages() {
    console.trace('This is a "normal" message for test');
    console.trace('This is an "info" message for test', 'info');
    console.trace('This is a "warn" message for test', 'warn');
    console.trace('This is an "error" message for test', 'error');
    console.launch();
}

({
    init() {
        this.intermediate();
    },
    intermediate() {
        printMessages();
    },
}).init();

// @OutputSnippet:
// 20:46:00.709/E: This is an "error" message for test
// 	at consoleTrace.js:5 (printMessages)
// 	at consoleTrace.js:14
// 	at consoleTrace.js:11
// 	at consoleTrace.js:9