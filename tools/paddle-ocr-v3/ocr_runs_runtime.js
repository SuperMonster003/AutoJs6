// Runs ocr detect in a Node.js or Bun script and reports each result's extras. device_check.py prepends the script mode
// and AJ6_RUNTIME ("node" or "bun"), AJ6_RUN, AJ6_CONFIG ({ images, runs, worker }) and aj6Emit: the results travel as
// console lines, because the plugin process cannot write to the external files directory of the host. images holds the
// base64 of each file, runs are those of ocr_runs.js ({ id, file, options, detect, regionInset, method }); worker names
// the run repeated in a Worker with the image handle of the main thread.
// zh-CN: 在 Node.js 或 Bun 脚本中运行 ocr detect 并回报每个结果的 extras. 结果经控制台日志回传, 因为插件进程写不了宿主的外部文件目录.
async function aj6Ocr(runtime) {
    if (runtime === "node") return require("autojs6:ocr");
    const { createHostClient } = await import(process.env.AUTOJS6_HOST_CLIENT_MODULE);
    return (await createHostClient()).ocr;
}

async function aj6Detect(ocr, image, run) {
    const options = Object.assign({}, run.options);
    if (run.regionInset) {
        const inset = run.regionInset;
        options.region = [inset, inset, image.width - 2 * inset, image.height - 2 * inset];
    }
    const module = run.detect === "auto" ? ocr : ocr[run.detect];
    if (run.method === "recognizeText") return { lines: (await module.recognizeText(image, options)).split("\n") };
    const results = await module.detect(image, options);
    return { results: results.map(item => ({
        text: item.text,
        confidence: item.confidence,
        bounds: [item.bounds.left, item.bounds.top, item.bounds.right, item.bounds.bottom],
        keys: Object.keys(item.extras).sort(),
        executionProvider: item.extras.executionProvider,
        classificationTimeMs: item.extras.classificationTimeMs,
        quad: item.extras.quad ? [...item.extras.quad] : null,
        frozen: Object.isFrozen(item.extras) && (!item.extras.quad || Object.isFrozen(item.extras.quad)),
    })) };
}

function aj6WorkerResult(runtime, image, run) {
    const source = `${aj6Ocr}\n${aj6Detect}\n` +
        `const aj6Post = ${runtime === "node" ? "m => require('node:worker_threads').parentPort.postMessage(m)" : "m => postMessage(m)"};\n` +
        `aj6Ocr(${JSON.stringify(runtime)}).then(ocr => aj6Detect(ocr, ${JSON.stringify(image)}, ${JSON.stringify(run)}))` +
        ".then(aj6Post, e => aj6Post({ error: String(e && e.stack || e) }));\n";
    return new Promise(resolve => {
        if (runtime === "node") {
            const { Worker } = require("node:worker_threads");
            const worker = new Worker(source, { eval: true });
            worker.on("message", message => { worker.terminate(); resolve(message); });
            worker.on("error", e => resolve({ error: String(e && e.stack || e) }));
        } else {
            const worker = new Worker(URL.createObjectURL(new Blob([source], { type: "application/javascript" })));
            worker.onmessage = event => { worker.terminate(); resolve(event.data); };
            worker.onerror = event => resolve({ error: String(event.message) });
        }
    });
}

(async () => {
    const handles = {};
    let images;
    try {
        const ocr = await aj6Ocr(AJ6_RUNTIME);
        images = AJ6_RUNTIME === "node" ? require("autojs6:images") : (await (await import(process.env.AUTOJS6_HOST_CLIENT_MODULE)).createHostClient()).images;
        for (const [file, data] of Object.entries(AJ6_CONFIG.images)) handles[file] = await images.fromBase64(data);
        for (let i = 0; i < AJ6_CONFIG.runs.length; i++) {
            const run = AJ6_CONFIG.runs[i];
            try {
                aj6Emit(AJ6_RUN, i, { id: run.id, ...await aj6Detect(ocr, handles[run.file], run) });
            } catch (e) {
                aj6Emit(AJ6_RUN, i, { id: run.id, error: String(e && e.message || e) });
            }
        }
        const worker = AJ6_CONFIG.runs.find(run => run.id === AJ6_CONFIG.worker);
        const image = handles[worker.file];
        const size = { id: image.id, __autojs6ImageHandle: image.__autojs6ImageHandle, width: image.width, height: image.height };
        aj6Emit(AJ6_RUN, AJ6_CONFIG.runs.length, { id: "worker", ...await aj6WorkerResult(AJ6_RUNTIME, size, worker) });
    } catch (e) {
        aj6Emit(AJ6_RUN, -2, { error: String(e && e.stack || e) });
    } finally {
        for (const image of Object.values(handles)) await images.recycle(image);
        aj6Emit(AJ6_RUN, -1, { done: 1 });
    }
})();
