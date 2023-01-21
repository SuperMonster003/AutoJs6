console.show();

// -n 'org.autojs.autojs6/Test'
// --ei 'a' 1
// --eia 'b' 2,2
// --es 'c' 'hello'
// --ez 'd' false
// --esa 'e' 'r','s','t'
// -c cat003
// -a 'android.intent.action.VIEW'
// -f 335544320
// -t application/pics-rules
// -d protocol://xxx
console.log(app.intentToShell({
    action: 'VIEW',
    className: 'Test',
    packageName: context.packageName,
    extras: { a: 1, b: [ 2, 2 ], c: 'hello', d: false, e: [ 'r', 's', 't' ] },
    flags: [ 'ACTIVITY_NEW_TASK', 'ACTIVITY_CLEAR_TOP' ],
    type: 'application/pics-rules',
    data: 'protocol://xxx',
    category: 'cat003',
}).split(/ (?=-)/).join('\n'));