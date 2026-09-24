// Copy this sample to a writable script file before scheduling it.
// Add that file to AutoJs6's daily tasks; each trigger starts one background run.
// To install a daily task programmatically, opt in with execArgv.installSchedule:
// engines.execScriptFile('/sdcard/scripts/agent-detached.js', {
//     arguments: { installSchedule: true, time: '09:00' },
// });
// No schedule is created by a normal launch. Repeated opt-in creates more tasks.
// To observe the latest run instead: arguments: { observe: true }.
let args = engines.myEngine().execArgv;
let source = String(engines.myEngine().source);
let idFile = source + '.run-id';

if (args.installSchedule === true) {
    let task = tasks.addDailyTask({ path: source, time: args.time || '09:00' });
    console.log('Daily task:', task.id);
} else if (args.observe === true) {
    let id = files.read(idFile).trim();
    let run = ai.agent.get(id);
    if (run === null) throw Error('Task is no longer retained: ' + id);
    // Explicit result observation keeps this script alive until settlement.
    run.result.then((result) => {
        console.log(result.status, result.summary);
        if (args.output) files.write(String(args.output), JSON.stringify(result));
    }, (error) => console.error(error.code, error.message));
} else {
    let options = { detached: true, tools: ['observe', 'user'], budget: { maxSteps: 6 } };
    if (args.target) options.target = String(args.target);
    let run = ai.agent.run('读取设备信息并报告 Android 版本, 用实际观察结果作为证据.', options);
    if (run.error) throw Error(run.error.code + ': ' + run.error.message);
    files.write(idFile, run.id);
    console.log('Background task:', run.id);
    // Do not await result here. The task continues after this script exits.
    // Host/plugin disconnection can still interrupt it. No automatic restart.
}
