// AutoJs6 6.8.0 / build 5293+. Enable 3-Stove Agent in Plugin Center; the script connects automatically.
// Configure a default model in 3-Stone AI, or pass execArgv.target explicitly.
let args = engines.myEngine().execArgv;
let options = { tools: ['observe', 'user'], budget: { maxSteps: 6 } };
if (args.target) options.target = String(args.target);

let run = ai.agent.run('读取设备信息, 告诉我 Android 版本, 并引用实际观察结果.', options);
run.on('progress', (event) => console.log(event.message));
run.result.then((result) => {
    console.log(result.status, result.summary);
    // A fulfilled result can also be partial, failed, blocked or cancelled.
    if (args.output) files.write(String(args.output), JSON.stringify(result));
}, (error) => console.error(error.code, error.message, error.hint || ''));
