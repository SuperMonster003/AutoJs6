// Custom script interaction. This example grants only the user tool group.
let args = engines.myEngine().execArgv;
let options = { interaction: 'script', tools: ['user'], budget: { maxSteps: 6 } };
if (args.target) options.target = String(args.target);

let run = ai.agent.run('先询问我的演示称呼, 收到答案后用这个称呼问好, 并引用答案作为证据.', options);
run.on('input', (event) => {
    let answer;
    if (event.kind === 'text') {
        answer = args.answer !== undefined ? String(args.answer) : dialogs.rawInput(event.question);
    } else if (event.kind === 'choice') {
        let index = dialogs.select(event.question, event.choices);
        answer = index < 0 ? null : event.choices[index];
    } else {
        answer = dialogs.confirm(event.question);
    }
    if (answer === null) run.cancel('user-dismissed-input');
    else run.respond(event.requestId, answer);
});
// A model question is separate from permission to execute an operation.
run.on('confirmation', (event) => run.confirm(event.requestId, false));
run.result.then((result) => {
    console.log(result.status, result.summary);
    if (args.output) files.write(String(args.output), JSON.stringify(result));
}, (error) => console.error(error.code, error.message));
