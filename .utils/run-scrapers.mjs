// run-scrapers.mjs

import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import * as path from 'node:path';
import * as fs from 'node:fs/promises';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 待执行脚本 (顺序可按需调整)
const SCRIPT_LIST = [
    'scrape-and-inject-latest-gradle-wrapper.mjs',
    'scrape-and-inject-agp-releases.mjs',
    'scrape-and-inject-android-studio-agp-version-map.mjs',
    'scrape-and-inject-android-studio-codename_maps.mjs',
    'scrape-and-inject-embedded-kotlin-list.mjs',
    'scrape-and-inject-ksp-releases.mjs',
    'scrape-and-inject-agp-gradle-compatibility-list.mjs',
    'scrape-and-inject-java-gradle-compatibility-list.mjs',
    'scrape-and-inject-rhino-engine-data.mjs',
    'scrape-and-update-readme-template-contributors-table.mjs',
];

/**
 * 解析 CLI 参数.
 *
 * @param {string[]} [argv=process.argv.slice(2)]
 * @return {{ continueOnError: boolean, dryRun: boolean, nodePath: string, filters: string[] }}
 */
function parseArgs(argv = process.argv.slice(2)) {
    const opts = {
        continueOnError: false,
        dryRun: false,
        nodePath: process.execPath, // 使用当前 Node 可执行文件, 避免 PATH 问题
        filters: [],
    };
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--continue-on-error') opts.continueOnError = true;
        else if (a === '--dry-run') opts.dryRun = true;
        else if (a === '--node') opts.nodePath = argv[++i];
        else if (a === '--filter') opts.filters.push(argv[++i]);
        else if (a.startsWith('--filter=')) opts.filters.push(a.split('=').slice(1).join('='));
        else if (a.startsWith('--node=')) opts.nodePath = a.split('=').slice(1).join('=');
    }
    return opts;
}

/**
 * @param {number} ms
 * @return {string}
 */
function formatDuration(ms) {
    const sec = Math.floor(ms / 1000);
    const msPart = ms % 1000;
    return `${sec}.${String(msPart).padStart(3, '0')}s`;
}

/**
 * @param {import('fs').PathLike} filePath
 * @return {Promise<boolean>}
 */
async function ensureExists(filePath) {
    try {
        await fs.access(filePath);
        return true;
    } catch {
        return false;
    }
}

/**
 * @param {Object} options
 * @param {string} options.nodePath
 * @param {string} options.scriptPath
 * @param {string | URL | undefined} options.cwd
 * @return {Promise<{ code: number, signal: NodeJS.Signals | null, ms: number, error?: Error }>}
 */
async function runOne({ nodePath, scriptPath, cwd }) {
    return new Promise((resolve) => {
        const start = Date.now();
        const child = spawn(nodePath, [ scriptPath ], {
            cwd,
            stdio: 'inherit', // 直接把子进程的输出打到当前控制台
            env: process.env,
        });
        child.on('close', (code, signal) => {
            const end = Date.now();
            resolve({
                code: code ?? 0,
                signal: signal ?? null,
                ms: end - start,
            });
        });
        child.on('error', (err) => {
            const end = Date.now();
            resolve({ code: 1, signal: null, ms: end - start, error: err });
        });
    });
}

async function main() {
    const opts = parseArgs();

    const utilsDir = __dirname; // 运行器位于 .utils
    const scripts = SCRIPT_LIST
        .map(name => ({ name, abs: path.resolve(utilsDir, name) }))
        .filter(s => opts.filters.length === 0 || opts.filters.some(f => s.name.includes(f)));

    console.log('\n============================================================');
    console.log(' Running scrapers in sequence (Node ESM)');
    console.log(` UTILS_DIR = ${utilsDir}`);
    console.log(` NODE_EXE  = ${opts.nodePath}`);
    if (opts.filters.length) console.log(` FILTERS   = ${opts.filters.join(', ')}`);
    console.log('============================================================\n');

    if (scripts.length === 0) {
        console.log('No scripts to run after filtering.');
        return process.exit(0);
    }

    // 检查存在性
    const finalScripts = [];
    for (const s of scripts) {
        if (await ensureExists(s.abs)) {
            finalScripts.push(s);
        } else {
            console.log(`File not found: ${s.name}`);
        }
    }
    if (finalScripts.length === 0) {
        console.log('No existing scripts to run.');
        return process.exit(0);
    }

    if (opts.dryRun) {
        console.log('The following scripts would run in order:');
        finalScripts.forEach((s, i) => console.log(`  (${i + 1}/${finalScripts.length}) ${s.name}`));
        return process.exit(0);
    }

    const results = [];
    for (let i = 0; i < finalScripts.length; i++) {
        const s = finalScripts[i];
        console.log(`[${i + 1}/${finalScripts.length}] ${s.name}`);
        const res = await runOne({ nodePath: opts.nodePath, scriptPath: s.abs, cwd: utilsDir });
        if (res.code === 0) {
            console.log(`[Duration] ${formatDuration(res.ms)}\n`);
        } else {
            console.log(`[Duration] ${formatDuration(res.ms)} | [Exit Code] ${res.code}\n`);
            results.push({ ...res, name: s.name });
            if (!opts.continueOnError) break;
            continue;
        }
        results.push({ ...res, name: s.name });
    }

    const failed = results.filter(r => r.code !== 0);
    console.log('============================================================');
    if (failed.length === 0) {
        console.log(' All tasks completed successfully.');
        console.log('============================================================\n');
        process.exit(0);
    } else {
        console.log(` ${failed.length} task(s) failed:`);
        failed.forEach(r => console.log(`  - ${r.name} (code ${r.code}, ${formatDuration(r.ms)})`));
        console.log('============================================================\n');
        process.exit(1);
    }
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});