// run-scrapers.mjs

import * as fs from 'node:fs';
import * as path from 'node:path';
import * as process from 'node:process';
import * as readline from 'node:readline';
import stringWidth from 'string-width';
import stripAnsi from 'strip-ansi';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const SCRIPT_LIST = [
    'scrape-and-inject-rhino-engine-data.mjs',
    'scrape-and-inject-latest-gradle-wrapper.mjs',
    'scrape-and-inject-gradle-kotlin-compatibility-map.mjs',
    'scrape-and-inject-android-studio-codename-maps.mjs',
    'scrape-and-inject-android-studio-agp-version-map.mjs',
    'scrape-and-inject-java-gradle-compatibility-map.mjs',
    'scrape-and-inject-agp-gradle-compatibility-map.mjs',
    'scrape-and-inject-agp-releases-list.mjs',
    'scrape-and-inject-ksp-releases-map.mjs',
    'scrape-and-update-foojay-resolver-version.mjs',
    'scrape-and-update-android-releases.mjs',
    'scrape-and-update-readme-template-contributors-table.mjs',
];

const childProcessOutput = [];

const cols = () => process.stdout.columns || 80;

/**
 * Parse CLI arguments.<br>
 * zh-CN: 解析 CLI 参数.
 *
 * @returns {{ nodePath: string }}
 */
function parseArgs() {
    const argv = process.argv.slice(2);
    const opts = {
        nodePath: process.execPath,
    };
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--node') {
            opts.nodePath = argv[++i];
        } else if (a.startsWith('--node=')) {
            opts.nodePath = a.split('=').slice(1).join('=');
        }
    }
    return opts;
}

/**
 * @param {number} ms
 * @returns {string}
 */
function formatDuration(ms) {
    const sec = Math.floor(ms / 1000);
    const msPart = ms % 1000;
    return `${sec}.${String(msPart).padStart(3, '0')}s`;
}

/**
 * @param {import('fs').PathLike} filePath
 * @returns {boolean}
 */
function ensureExists(filePath) {
    try {
        fs.accessSync(filePath);
        return true;
    } catch {
        return false;
    }
}

/**
 * @param {ScriptItem[]} scripts
 * @param {number} idx
 * @param {string} [extra='']
 * @returns {string}
 */
function oneLineHeader(scripts, idx, extra = '') {
    const total = scripts.length;
    const order = `${idx + 1}`.padStart(`${total}`.length, '0');
    const base = scripts[idx].name;
    const suffix = extra ? ` (${extra})` : '';
    const core = `[${order}/${total}] ${base}${suffix}`;
    const width = cols();
    if (core.length <= width) return core;
    const ellipsis = ' ... ';
    const keep = Math.max(4, width - ellipsis.length);
    const left = Math.ceil(keep / 2);
    const right = Math.floor(keep / 2);
    return core.slice(0, left) + ellipsis + core.slice(core.length - right);
}

/**
 * @param {string} text
 * @returns {number}
 */
function visualRowsOf(text) {
    const width = stringWidth(stripAnsi(text).replace(/\r?\n$/, ''));
    return Math.max(1, Math.ceil(width / Math.max(1, cols())));
}

/**
 * @param {Object} options
 * @param {string} options.nodePath
 * @param {string} options.scriptPath
 * @param {string | URL | undefined} options.cwd
 * @returns {Promise<{ code: number, signal: NodeJS.Signals | null, ms: number, error?: Error }>}
 */
async function runOne({ nodePath, scriptPath, cwd }) {
    return new Promise((resolve) => {
        const start = Date.now();
        const child = spawn(nodePath, [ scriptPath ], {
            cwd,
            // Capture child process output.
            // zh-CN: 捕获子进程输出.
            stdio: [ 'inherit', 'pipe', 'pipe' ],
            env: process.env,
        });

        child.stdout.on('data', (chunk) => {
            childProcessOutput.push(chunk);
        });
        child.stderr.on('data', (chunk) => {
            process.stderr.write(chunk);
        });

        child.on('close', (code, signal) => {
            const end = Date.now();
            resolve({ code: code ?? 0, signal: signal ?? null, ms: end - start });
        });
        child.on('error', (err) => {
            const end = Date.now();
            resolve({ code: 1, signal: null, ms: end - start, error: err });
        });
    });
}

async function main() {
    const opts = parseArgs();

    const utilsDir = path.dirname(fileURLToPath(import.meta.url));
    /** @type {ScriptItem[]} */
    const scripts = SCRIPT_LIST.map((name) => {
        const abs = path.resolve(utilsDir, name);
        if (!ensureExists(abs)) {
            throw new Error(`File not found: ${abs}`);
        }
        return { name, abs };
    });

    const title = 'Running scrapers in sequence (Node ESM)';
    const exhibition = {
        UTILS_DIR: utilsDir,
        NODE_EXE: opts.nodePath,
    };
    const exhibitionItems = (/* @IIFE */ () => {
        const maxKeyLength = Math.max(...Object.keys(exhibition).map(k => k.length));
        return Object.entries(exhibition).map(([ key, value ]) => {
            return ` ${key.padEnd(maxKeyLength)} : ${value}`;
        });
    })();

    const lineLength = Math.min(Math.max(
        title.length,
        ...exhibitionItems.map(s => s.length - 1),
    ) + 2, cols());
    const lineDouble = '='.repeat(lineLength);
    const lineSingle = '-'.repeat(lineLength);

    console.log('\n');
    console.log(lineDouble);
    console.log(` ${title}`);
    console.log(lineSingle);
    console.log(exhibitionItems.join('\n'));
    console.log(lineDouble);
    console.log('\n');

    if (scripts.length === 0) {
        console.log('No existing scripts to run.');
        return process.exit(0);
    }

    const results = [];
    for (let i = 0; i < scripts.length; i++) {
        const script = scripts[i];
        const startLine = oneLineHeader(scripts, i);

        process.stdout.write(`\r${startLine}\n`);

        const res = await runOne({ nodePath: opts.nodePath, scriptPath: script.abs, cwd: utilsDir });
        const endLine = oneLineHeader(scripts, i, formatDuration(res.ms));

        const lineCountToMove = childProcessOutput.map(chunk => `${chunk}`).join('').split('\n').reduce((acc, line) => {
            return acc + visualRowsOf(line)
        }, 0);

        if (res.code === 0) {
            childProcessOutput.forEach((chunk) => {
                process.stdout.write(chunk);
            });
            readline.moveCursor(process.stdout, 0, -lineCountToMove);
            readline.clearLine(process.stdout, 0);
            process.stdout.write(`\r${endLine}`);
            readline.moveCursor(process.stdout, 0, lineCountToMove);
            process.stdout.write('\n');
            results.push({ ...res, name: script.name });
            childProcessOutput.splice(0);
        } else {
            // readline.moveCursor(process.stdout, 0, -childProcessOutput.length - 1);
            // process.stdout.write(`\r${endLine} [code: ${res.code}]`);
            // process.stdout.write('\n');
            results.push({ ...res, name: script.name });
            break;
        }
    }

    process.stdout.write('\n');

    const failed = results.filter(r => r.code !== 0);
    if (failed.length === 0) {
        const title = 'All tasks completed successfully';
        const lineLength = Math.min(Math.max(title.length) + 1, cols());
        const line = '='.repeat(lineLength);
        console.log(line);
        console.log(` ${title}`);
        console.log(line);
        process.stdout.write('\n');
        process.exit(0);
    } else {
        const title = `${failed.length} task(s) failed`;
        const messages = failed.map(r => {
            return ` - ${r.name} (${formatDuration(r.ms)}) [code: ${r.code}]`;
        });
        const lineLength = Math.min(Math.max(
            title.length,
            ...messages.map(s => s.length - 1),
        ) + 2, cols());
        const lineDouble = '='.repeat(lineLength);
        const lineSingle = '-'.repeat(lineLength);
        console.log(lineDouble);
        console.log(` ${failed.length} task(s) failed:`);
        console.log(lineSingle);
        console.log(messages.join('\n'));
        console.log(lineDouble);
        process.stdout.write('\n');
        process.exit(1);
    }
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});
