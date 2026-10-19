// Device checks for the pipe-based Shell, shell.spawn / shizuku.spawn and the Auto.js Pro shell options.
// Run through device_shell_stream_test.py, which fills in __RUN_ID__ / __PHASE__ and reads the results from logcat.
// Each result is one logcat line: "<run id> {json}" with tag AJ6ShellStream; the last line of a phase is "<run id> DONE".

const TAG = 'AJ6ShellStream';
const RUN_ID = '__RUN_ID__';
const PHASE = '__PHASE__';
const TOUCH_SECONDS = Number('__TOUCH_SECONDS__');

function emitLine(text) {
    android.util.Log.i(TAG, RUN_ID + ' ' + text);
}

function report(name, pass, detail) {
    emitLine(JSON.stringify({ name: name, pass: !!pass, detail: detail === undefined ? '' : String(detail) }));
}

function check(name, fn) {
    try {
        let r = fn();
        report(name, r.pass, r.detail);
    } catch (e) {
        report(name, false, 'threw: ' + e);
    }
}

function throwsWith(fn, fragment) {
    try {
        fn();
        return false;
    } catch (e) {
        return String(e).indexOf(fragment) >= 0;
    }
}

const appUid = android.os.Process.myUid();
const adbAvailable = !!shizuku.state.isOperational;
const rootAvailable = PHASE === 'main' && $shell.isRootAvailable() && $shell.checkAccess('root');

function modes() {
    let list = ['sh'];
    if (adbAvailable) list.push('adb');
    if (rootAvailable) list.push('root');
    return list;
}

function optionsFor(mode) {
    return { root: mode === 'root', adb: mode === 'adb' };
}

function uidOk(mode, uid) {
    if (mode === 'sh') return uid === appUid;
    if (mode === 'root') return uid === 0;
    return uid === 2000 || uid === 0;
}

// Closes a Shell that a blocking read loop is waiting on, so a missing line fails the check instead of hanging the run.
function guardShell(sh, millis) {
    return threads.start(() => {
        try {
            sleep(millis);
            sh.exit();
        } catch (e) {
            // Interrupted: the loop finished in time.
        }
    });
}

function processArgs(mode) {
    let r = mode === 'adb' ? shizuku('ps -A -o ARGS') : shell('ps -A -o ARGS', mode === 'root');
    return String(r.result);
}

// ------------------------------------------------------------------ async runner

let asyncQueue = [];

function asyncCheck(name, timeoutMillis, fn) {
    asyncQueue.push({ name: name, timeoutMillis: timeoutMillis, fn: fn });
}

function runNextAsync() {
    let t = asyncQueue.shift();
    if (!t) {
        emitLine('DONE');
        return;
    }
    let finished = false;
    let finish = (pass, detail) => {
        if (finished) return;
        finished = true;
        clearTimeout(timer);
        report(t.name, pass, detail);
        setTimeout(runNextAsync, 0);
    };
    let timer = setTimeout(() => finish(false, 'timeout'), t.timeoutMillis);
    try {
        t.fn(finish);
    } catch (e) {
        finish(false, 'threw: ' + e);
    }
}

// ------------------------------------------------------------------ phase: main

function mainPhase() {
    emitLine(JSON.stringify({ info: 'modes', value: modes().join(','), appUid: appUid }));

    modes().forEach((mode) => {
        let opts = optionsFor(mode);

        // The pattern from the request: exec, then read getInputStream through a BufferedReader.
        check('pipe.' + mode + '.stream', () => {
            let sh = new Shell(opts);
            sh.exec('for i in 1 2 3; do echo line$i; sleep 0.3; done; echo END');
            let guard = guardShell(sh, 10000);
            let reader = new java.io.BufferedReader(new java.io.InputStreamReader(sh.getInputStream()));
            let lines = [];
            let times = [];
            let start = Date.now();
            let line;
            while ((line = reader.readLine()) != null) {
                lines.push(String(line));
                times.push(Date.now() - start);
                if (String(line) === 'END') break;
            }
            guard.interrupt();
            sh.exit();
            let streamed = times.length === 4 && times[0] < 300 && times[3] - times[0] >= 600;
            return { pass: lines.join(',') === 'line1,line2,line3,END' && streamed, detail: 'ms=' + times.join('/') };
        });

        check('pipe.' + mode + '.execForResult', () => {
            let sh = new Shell(opts);
            let uid = Number(String(sh.execForResult('id -u').result).trim());
            let split = sh.execForResult("sh -c 'echo out; echo err >&2; exit 3'");
            let partial = sh.execForResult('printf abc');
            let text = sh.execAndWaitFor('echo x');
            sh.exitAndWaitFor();
            let ok = uidOk(mode, uid) &&
                split.code === 3 && String(split.result) === 'out\n' && String(split.error) === 'err\n' &&
                partial.code === 0 && String(partial.result) === 'abc' &&
                // new Shell(options) is a Java object (Auto.js Pro compatible): exitCode() is a java.lang.Integer.
                String(text) === 'x\n' && sh.exitCode() == 0;
            return { pass: ok, detail: 'uid=' + uid + ' code=' + split.code + ' partial=' + JSON.stringify(String(partial.result)) };
        });

        check('pipe.' + mode + '.callback', () => {
            let sh = new Shell(opts);
            let got = [];
            sh.setCallback({ onNewLine: (l) => got.push(String(l)) });
            sh.exec('echo cb1');
            sh.exec('echo cb2 >&2');
            let start = Date.now();
            while (got.length < 2 && Date.now() - start < 3000) sleep(50);
            let conflict = throwsWith(() => sh.getInputStream(), 'already read');
            sh.exit();
            return { pass: got.sort().join(',') === 'cb1,cb2' && conflict, detail: 'got=' + got.join(',') + ' conflict=' + conflict };
        });

        check('pipe.' + mode + '.exitAndReadOutput', () => {
            let sh = new Shell(opts);
            sh.exec('echo r1');
            sh.exec('echo r2');
            let text = String(sh.exitAndReadOutput());
            return { pass: text === 'r1\nr2\n', detail: JSON.stringify(text) };
        });

        check('spawn.' + mode + '.readLine', () => {
            let p = shell.spawn('echo a; echo b; echo e >&2', opts);
            let lines = [String(p.readLine()), String(p.readLine()), p.readLine()];
            let err = String(p.readErrorLine());
            let code = p.waitFor();
            return { pass: lines[0] === 'a' && lines[1] === 'b' && lines[2] === null && err === 'e' && code === 0 && p.exitCode === 0, detail: 'mode=' + p.mode + ' code=' + code };
        });

        check('spawn.' + mode + '.stdin', () => {
            let p = shell.spawn('cat', opts);
            p.writeLine('hello').closeInput();
            let line = String(p.readLine());
            return { pass: line === 'hello' && p.waitFor() === 0, detail: line };
        });

        check('spawn.' + mode + '.envCwd', () => {
            let p = shell.spawn('echo "$FOO"; pwd', Object.assign({ env: { FOO: "b a'r" }, cwd: '/system' }, opts));
            let lines = [String(p.readLine()), String(p.readLine())];
            p.waitFor();
            return { pass: lines[0] === "b a'r" && lines[1] === '/system', detail: lines.join('|') };
        });

        check('spawn.' + mode + '.exitCodeAndTimeout', () => {
            let p = shell.spawn('exit 7', opts);
            let code = p.waitFor();
            let q = shell.spawn('sleep 5', opts);
            let early = q.waitFor(200);
            let alive = q.isAlive();
            let killed = q.kill();
            let late = q.waitFor(3000);
            return { pass: code === 7 && early === null && alive && killed && late !== null && !q.isAlive(), detail: 'code=' + code + ' late=' + late };
        });

        check('spawn.' + mode + '.killTree', () => {
            let marker = 'aj6kill' + RUN_ID.slice(-6) + mode;
            let p = shell.spawn('M=' + marker + '; while true; do echo $M | cat; sleep 0.2; done', opts);
            sleep(600);
            let before = processArgs(mode).indexOf(marker) >= 0;
            let start = Date.now();
            p.kill();
            // A root process is ended by another root shell, which takes a moment to start.
            let after = true;
            while (after && Date.now() - start < 5000) {
                sleep(250);
                after = processArgs(mode).indexOf(marker) >= 0;
            }
            return { pass: before && !after && !p.isAlive(), detail: 'before=' + before + ' after=' + after + ' ms=' + (Date.now() - start) };
        });

        // awk buffers its output on a pipe; on a pseudo-terminal it prints every line at once.
        check('spawn.' + mode + '.pty', () => {
            let command = "for i in 1 2 3; do echo $i; sleep 0.3; done | awk '{ print }'";
            let timesOf = (pty) => {
                let p = shell.spawn(command, Object.assign({ pty: pty }, opts));
                let start = Date.now();
                let times = [];
                let line;
                while ((line = p.readLine()) !== null) {
                    if (String(line).length) times.push(Date.now() - start);
                }
                p.waitFor();
                return times;
            };
            let piped = timesOf(false);
            let tty = timesOf(true);
            let spread = (t) => t.length === 3 ? t[2] - t[0] : -1;
            return { pass: tty.length === 3 && spread(tty) >= 450, detail: 'pipeMs=' + piped.join('/') + ' ptyMs=' + tty.join('/') };
        });

        check('pipe.' + mode + '.pty', () => {
            // Documented limitation: Magisk's root terminal still echoes commands on Android 9 and below.
            let echoAllowed = mode === 'root' && device.sdkInt < 29;
            let lastLine = (text) => String(text).trim().split('\n').pop().trim();
            let sh = new Shell(Object.assign({ pty: true }, opts));
            let uid = Number(lastLine(sh.execForResult('id -u').result));
            let tty = lastLine(sh.execForResult('[ -t 1 ] && echo tty || echo notty').result);
            sh.exit();
            let streamed = new Shell(Object.assign({ pty: true }, opts));
            streamed.exec("for i in 1 2 3; do echo p$i; sleep 0.3; done | awk '{ print }'; echo END");
            let guard = guardShell(streamed, 10000);
            let reader = new java.io.BufferedReader(new java.io.InputStreamReader(streamed.getInputStream()));
            let lines = [];
            let times = [];
            let start = Date.now();
            let line;
            let echoed = 0;
            while ((line = reader.readLine()) != null) {
                line = String(line);
                if (echoAllowed && line.indexOf('echo ') >= 0) {
                    echoed++;
                    continue;
                }
                lines.push(line);
                times.push(Date.now() - start);
                if (line === 'END') break;
            }
            guard.interrupt();
            streamed.exit();
            let ok = uidOk(mode, uid) && tty === 'tty' && lines.join(',') === 'p1,p2,p3,END' && times[2] - times[0] >= 450;
            return { pass: ok, detail: 'uid=' + uid + ' ' + tty + ' lines=' + lines.join(',') + ' ms=' + times.join('/') + (echoAllowed ? ' echoed=' + echoed : '') };
        });

        asyncCheck('spawn.' + mode + '.events', 20000, (finish) => {
            let p = shell.spawn('seq 1 20000; echo done >&2; exit 5', opts);
            let count = 0;
            let last = null;
            let err = null;
            p.on('output', (line) => {
                count++;
                last = String(line);
            });
            p.on('errorOutput', (line) => err = String(line));
            p.on('exit', (code) => finish(count === 20000 && last === '20000' && err === 'done' && code === 5, 'count=' + count + ' code=' + code));
        });

        asyncCheck('spawn.' + mode + '.killFromEvent', 10000, (finish) => {
            let p = shell.spawn('while true; do echo t; sleep 0.1; done', opts);
            let lines = 0;
            p.on('output', () => {
                if (++lines === 3) p.kill();
            });
            p.on('exit', (code) => finish(lines >= 3 && code !== 0, 'lines=' + lines + ' code=' + code));
        });
    });

    check('pipe.stringConstructor', () => {
        let sh = new Shell('sh');
        let text = String(sh.execAndWaitFor('echo k'));
        sh.exit();
        return { pass: text === 'k\n' && !sh.isAdb(), detail: JSON.stringify(text) };
    });

    check('pipe.unknownOption', () => ({ pass: throwsWith(() => new Shell({ foo: 1 }), 'Unknown shell option'), detail: '' }));

    check('spawn.streamConflict', () => {
        let p = shell.spawn('echo a', false);
        p.readLine();
        let conflict = throwsWith(() => p.on('output', () => null), 'already read');
        p.waitFor();
        return { pass: conflict, detail: '' };
    });

    check('legacy.termSession', () => {
        let sh = new Shell();
        sh.exec('true');
        let hasSession = sh.getTermSession() != null;
        let refused = throwsWith(() => sh.getInputStream(), 'terminal session');
        sh.exit();
        return { pass: hasSession && refused && sh.isInitialized(), detail: 'session=' + hasSession + ' refused=' + refused };
    });

    check('shell.aliasAndAccess', () => {
        let ok = $shell === shell && typeof $shell.spawn === 'function' &&
            $shell.checkAccess('adb') === adbAvailable &&
            typeof $shell.isRootAvailable() === 'boolean' &&
            throwsWith(() => $shell.checkAccess('nope'), 'Unknown access type');
        return { pass: ok, detail: 'adb=' + adbAvailable + ' rootAvailable=' + $shell.isRootAvailable() };
    });

    check('shell.argumentsAdbKeyWithoutShizuku', () => {
        // With Shizuku the command runs there; without it the reserved key must not turn into "--adb".
        let command = shell.getCommand('pm list packages', { adb: true });
        return { pass: String(command) === 'pm list packages', detail: String(command) };
    });

    if (adbAvailable) {
        check('shell.adbOption', () => {
            let r = shell('id -u', { adb: true });
            let uid = Number(String(r.result).trim());
            return { pass: r.code === 0 && uidOk('adb', uid), detail: 'uid=' + uid };
        });

        check('shell.setDefaultOptions', () => {
            $shell.setDefaultOptions({ adb: true });
            let viaDefault = Number(String(shell('id -u').result).trim());
            let spawned = Number(String(shell.spawn('id -u').readLine()).trim());
            let explicitRoot = shell('id -u', false);
            $shell.setDefaultOptions(null);
            let cleared = Number(String(shell('id -u').result).trim());
            let unknown = throwsWith(() => $shell.setDefaultOptions({ cmd: 'su' }), 'Unknown default shell option');
            let ok = uidOk('adb', viaDefault) && uidOk('adb', spawned) && uidOk('adb', Number(String(explicitRoot.result).trim())) &&
                cleared === appUid && unknown;
            return { pass: ok, detail: 'default=' + viaDefault + ' spawned=' + spawned + ' cleared=' + cleared };
        });

        check('shizuku.spawn', () => {
            let p = shizuku.spawn('id -u');
            let uid = Number(String(p.readLine()).trim());
            return { pass: uidOk('adb', uid) && p.waitFor() === 0 && p.mode === 'adb', detail: 'uid=' + uid + ' pid=' + p.pid };
        });

        if (TOUCH_SECONDS > 0) addTouchCheck();
    }

    runNextAsync();
}

// ------------------------------------------------------------------ touch

// The exact command and reading pattern from the request, on pipes (as written) and on a pseudo-terminal side by side;
// needs someone touching the screen. getevent buffers its output on a pipe, so the piped reader gets lines late.
function addTouchCheck() {
    asyncCheck('pipe.adb.getevent', (TOUCH_SECONDS + 5) * 1000, (finish) => {
        let start = Date.now();
        let watch = (options) => {
            let sh = new Shell(options);
            sh.exec('getevent -tl | grep -E "ABS_MT_POSITION|UP|DOWN|ffffff"');
            let inputStream = sh.getInputStream();
            let bufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            let stats = { sh: sh, lines: 0, downs: 0, first: -1, firstDown: -1, sample: '' };
            threads.start(() => {
                let line;
                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        line = String(line);
                        if (stats.lines++ === 0) {
                            stats.first = Date.now() - start;
                            stats.sample = line.trim().replace(/ +/g, ' ').slice(0, 80);
                        }
                        if (line.indexOf('DOWN') >= 0 && stats.downs++ === 0) stats.firstDown = Date.now() - start;
                    }
                } catch (e) {
                    // Closed by exit().
                }
            });
            return stats;
        };
        let piped = watch({ adb: true });
        let tty = watch({ adb: true, pty: true });
        emitLine('TOUCH_NOW');
        setTimeout(() => {
            piped.sh.exit();
            tty.sh.exit();
            let describe = (s) => 'lines=' + s.lines + ' downs=' + s.downs + ' firstDownMs=' + s.firstDown;
            finish(tty.lines > 0, 'pipe[' + describe(piped) + '] pty[' + describe(tty) + '] sample=' + tty.sample);
        }, TOUCH_SECONDS * 1000);
    });
}

function touchPhase() {
    if (!adbAvailable) {
        report('pipe.adb.getevent', false, 'Shizuku is not operational');
        emitLine('DONE');
        return;
    }
    addTouchCheck();
    runNextAsync();
}

// ------------------------------------------------------------------ phase: keepalive

// Only a spawn listener is pending once the main code returns: the script must stay alive until "exit".
function keepalivePhase() {
    let p = shell.spawn('sleep 1; echo hi', false);
    let got = null;
    p.on('output', (line) => got = String(line));
    p.on('exit', (code) => {
        report('keepalive.untilExit', got === 'hi' && code === 0, 'got=' + got + ' code=' + code);
        emitLine('DONE');
    });
}

// ------------------------------------------------------------------ phase: cleanup

// Leaves processes running and ends; the runner then checks that the script's exit ended them.
function cleanupPhase() {
    let markers = [];
    let spawnLoop = (mode, opts) => {
        let marker = 'aj6cleanup' + RUN_ID.slice(-6) + mode;
        shell.spawn('M=' + marker + '; while true; do echo $M; sleep 0.3; done', opts);
        markers.push(marker);
    };
    // A pipe Shell runs the loop in a child "sh -c", so the marker shows in a process command line.
    let pipeLoop = (mode, opts) => {
        let marker = 'aj6cleanup' + RUN_ID.slice(-6) + mode;
        new Shell(opts).exec("sh -c 'M=" + marker + "; while true; do echo $M > /dev/null; sleep 0.3; done'");
        markers.push(marker);
    };
    let rootUsable = $shell.isRootAvailable() && $shell.checkAccess('root');
    spawnLoop('sh', false);
    pipeLoop('pipe', {});
    if (adbAvailable) {
        spawnLoop('adb', { adb: true });
        pipeLoop('adbpipe', { adb: true });
    }
    if (rootUsable) {
        spawnLoop('root', true);
        pipeLoop('rootpipe', { root: true });
    }
    sleep(1000);
    // Shizuku (shell or root) sees every process; root sees every process; the app sees its own only.
    let listing = adbAvailable ? processArgs('adb') : processArgs(rootUsable ? 'root' : 'sh');
    let missing = markers.filter((m) => listing.indexOf(m) < 0);
    report('cleanup.started', missing.length === 0, 'markers=' + markers.length + ' missing=' + (missing.join(',') || '-'));
    emitLine(JSON.stringify({ info: 'markers', value: markers.join(',') }));
    emitLine('DONE');
}

if (PHASE === 'touch') touchPhase();
else if (PHASE === 'keepalive') keepalivePhase();
else if (PHASE === 'cleanup') cleanupPhase();
else mainPhase();
