package org.autojs.autojs.engine

import java.io.File
import java.nio.charset.StandardCharsets

object EmbeddedNodeFixtureCorpus {

    data class NpmToolchainCorpus(
        val packageDir: File,
        val workingDirectory: File,
        val packageJson: File,
        val scopedCliEntry: File,
        val helperCliEntry: File,
        val configFile: File,
        val configPathFromWorkingDirectory: String,
        val files: List<File>,
    )

    data class PackageBinStringCliCorpus(
        val projectRoot: File,
        val packageDir: File,
        val workingDirectory: File,
        val packageJson: File,
        val cliPackageDir: File,
        val cliEntry: File,
        val configFile: File,
        val configPathFromWorkingDirectory: String,
        val files: List<File>,
    )

    fun writeRealNpmCorpus(projectDir: File) {
        writeRealNpmBatch1(projectDir)
        writeRealNpmBatch2(projectDir)
    }

    fun writePackageBinStringCliCorpus(
        projectRoot: File,
        packageDirRelativePath: String = "packages/bin-string-app",
        workingDirectoryRelativePath: String = "workspace/bin-string-run",
        shortScriptName: String = "short-name",
        fullScriptName: String = "full-name",
        shortScriptLabel: String = "runner.bin_string.short",
        fullScriptLabel: String = "runner.bin_string.full",
        scriptRequestArg: String = "--from-bin-string-script-request",
        envKey: String = "BIN_STRING_REQUEST_ENV",
        descriptorPathFromWorkingDirectory: String? = null,
        descriptorLaunchKind: String? = null,
        expectMainNotRun: Boolean = false,
    ): PackageBinStringCliCorpus {
        val packageDir = File(projectRoot, packageDirRelativePath).apply {
            check(mkdirs() || isDirectory) { "Failed to create package bin string app dir: $absolutePath" }
        }
        val workingDirectory = File(projectRoot, workingDirectoryRelativePath).apply {
            check(mkdirs() || isDirectory) { "Failed to create package bin string working dir: $absolutePath" }
        }
        val written = mutableListOf<File>()
        written += File(workingDirectory, ".keep").apply {
            writeText("bin-string-workspace\n", StandardCharsets.UTF_8)
        }
        written += writeNpmPackage(
            packageDir,
            "@autojs6/string-bin-cli",
            """
            {
              "name": "@autojs6/string-bin-cli",
              "version": "1.0.0",
              "bin": "./bin/cli.js",
              "exports": {
                ".": "./index.js",
                "./feature": "./lib/feature.js"
              },
              "imports": {
                "#feature": "./lib/feature.js"
              }
            }
            """,
            mapOf(
                "bin/cli.js" to packageBinStringCliSource(),
                "index.js" to """
                "use strict";
                const feature = require("#feature");
                exports.value = "self-index";
                exports.feature = feature.value;
                """,
                "lib/feature.js" to """
                "use strict";
                exports.value = "imports-feature";
                """,
                "lib/local.js" to """
                "use strict";
                exports.value = "local-helper";
                """,
            ),
        )
        written += writeNpmPackage(
            packageDir,
            "@autojs6/bin-string-workspace-helper",
            """
            {
              "name": "@autojs6/bin-string-workspace-helper",
              "version": "1.0.0",
              "exports": {
                ".": "./index.js",
                "./cache-key": "./cache-key.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const cacheKey = require("@autojs6/bin-string-workspace-helper/cache-key");
                const local = require("./local");
                exports.value = "scoped-workspace-helper";
                exports.local = local.value;
                exports.self = cacheKey.kind;
                exports.cacheKey = cacheKey.cacheKey;
                exports.partition = function partition(input) {
                  return cacheKey.cacheKey(input).slice(0, 10);
                };
                """,
                "cache-key.js" to """
                "use strict";
                const crypto = require("crypto");
                exports.kind = "cache-key";
                exports.cacheKey = function cacheKey(input) {
                  return crypto.createHash("sha1").update(String(input)).digest("hex");
                };
                """,
                "local.js" to """
                "use strict";
                exports.value = "scoped-local-helper";
                """,
            ),
        )
        written += writeNpmPackage(
            packageDir,
            "bin-string-plugin",
            """
            {
              "name": "bin-string-plugin",
              "version": "1.0.0",
              "exports": {
                ".": "./index.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const helper = require("./helper");
                exports.name = "bin-string-plugin";
                exports.value = "plugin-value";
                exports.helper = helper.value;
                exports.apply = function apply(config) {
                  return [config.name, helper.value, (config.items || []).join("+")].join(":");
                };
                """,
                "helper.js" to """
                "use strict";
                exports.value = "plugin-helper";
                """,
            ),
        )
        val workspacePackageDir = File(packageDir.parentFile, "bin-string-workspace-helper").apply {
            check(mkdirs() || isDirectory) { "Failed to create package bin string workspace helper dir: $absolutePath" }
        }
        written += File(workspacePackageDir, "package.json").apply {
            writeText(
                """
                {
                  "name": "bin-string-workspace-helper",
                  "version": "1.0.0",
                  "exports": {
                    ".": "./index.js",
                    "./config": "./config.js"
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += File(workspacePackageDir, "index.js").apply {
            writeText(
                """
                "use strict";
                const selfConfig = require("bin-string-workspace-helper/config");
                exports.name = "bin-string-workspace-helper";
                exports.self = selfConfig.value;
                exports.discover = function discover(config) {
                  const items = Array.isArray(config.items) ? config.items.join("+") : "";
                  const packageJson = require("./package.json");
                  return {
                    packageName: packageJson.name,
                    value: selfConfig.value + ":" + items,
                    self: selfConfig.value,
                    cachePartition: selfConfig.partition(config.name || "")
                  };
                };
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += File(workspacePackageDir, "config.js").apply {
            writeText(
                """
                "use strict";
                const crypto = require("crypto");
                exports.value = "workspace-config";
                exports.partition = function partition(input) {
                  return crypto.createHash("sha1").update(String(input)).digest("hex").slice(0, 10);
                };
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        val workspacePackagePathFromPackageDir = packageDir.toPath()
            .relativize(workspacePackageDir.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        val packageParentRelativePath = packageDirRelativePath.substringBeforeLast('/', missingDelimiterValue = "")
        val workspacePackageRelativePath = listOf(packageParentRelativePath, "bin-string-workspace-helper")
            .filter { it.isNotEmpty() }
            .joinToString("/")
        val workspaceRootConfigFile = File(projectRoot, "bin-string.config.json").apply {
            writeText(
                """
                {
                  "name": "autojs6-bin-string-workspace-root",
                  "plugin": "./$packageDirRelativePath/node_modules/bin-string-plugin",
                  "scopedHelper": "./$packageDirRelativePath/node_modules/@autojs6/bin-string-workspace-helper",
                  "workspacePackage": "./$workspacePackageRelativePath",
                  "items": [
                    "workspace",
                    "root",
                    "fallback"
                  ],
                  "priority": "workspace-root",
                  "options": {
                    "profile": "workspace-root-profile",
                    "count": 1,
                    "enabled": true,
                    "features": [
                      "workspace-root-feature"
                    ],
                    "envFallback": "workspace-root-env",
                    "modeDefaults": {
                      "short": "workspace-root-short",
                      "full": "workspace-root-full",
                      "bin": "workspace-root-bin"
                    }
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += workspaceRootConfigFile
        val configFile = File(packageDir, "bin-string.config.json").apply {
            writeText(
                """
                {
                  "name": "autojs6-bin-string-config",
                  "plugin": "bin-string-plugin",
                  "scopedHelper": "@autojs6/bin-string-workspace-helper",
                  "workspacePackage": "$workspacePackagePathFromPackageDir",
                  "items": [
                    "exports",
                    "imports",
                    "createRequire"
                  ],
                  "options": {
                    "profile": "config-profile",
                    "count": 3,
                    "enabled": false,
                    "features": [
                      "config-feature",
                      "workspace-feature"
                    ],
                    "envFallback": "config-env-fallback",
                    "modeDefaults": {
                      "short": "config-short-default",
                      "full": "config-full-default",
                      "bin": "config-bin-default"
                    }
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += configFile

        val configPathFromWorkingDirectory = workingDirectory.toPath()
            .relativize(configFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        val cliEntryRelativePath = "$packageDirRelativePath/node_modules/@autojs6/string-bin-cli/bin/cli.js"
        val commonScriptArgs = buildList {
            add("--from-package-script")
            add("--expect-entry=$cliEntryRelativePath")
            add("--expect-cwd=$workingDirectoryRelativePath")
            add("--expect-init-cwd=$workingDirectoryRelativePath")
            add("--expect-package-dir=$packageDirRelativePath")
            add("--expect-package-json-dir=$packageDirRelativePath")
            add("--expect-package-name=autojs6-bin-string-app")
            add("--expect-package-version=1.0.0")
            add("--expect-config=$configPathFromWorkingDirectory")
            add("--expect-script-arg=--from-package-script")
            add("--expect-request-arg=$scriptRequestArg")
            add("--expect-env-key=$envKey")
            add("--merge-profile=argv-profile")
            add("--merge-count=7")
            add("--merge-enabled")
            add("--merge-features=argv-feature,script-feature")
            add("--merge-invalid-count=not-a-number")
            add("--merge-invalid-enabled=maybe")
            descriptorPathFromWorkingDirectory?.let { add("--expect-descriptor=$it") }
            descriptorLaunchKind?.let { add("--expect-launch-kind=$it") }
            if (expectMainNotRun) {
                add("--expect-main-not-run")
            }
        }
        val shortCommand = (
                listOf("string-bin-cli", "--label=$shortScriptLabel", "--mode=short") +
                        commonScriptArgs +
                        listOf("--expect-bin=string-bin-cli", "--expect-script=$shortScriptName")
                ).joinToString(" ")
        val fullCommand = (
                listOf("@autojs6/string-bin-cli", "--label=$fullScriptLabel", "--mode=full") +
                        commonScriptArgs +
                        listOf("--expect-bin=@autojs6/string-bin-cli", "--expect-script=$fullScriptName")
                ).joinToString(" ")
        val packageJson = File(packageDir, "package.json").apply {
            writeText(
                """
                {
                  "name": "autojs6-bin-string-app",
                  "version": "1.0.0",
                  "scripts": {
                    "$shortScriptName": "$shortCommand",
                    "$fullScriptName": "$fullCommand"
                  },
                  "dependencies": {
                    "@autojs6/string-bin-cli": "1.0.0",
                    "@autojs6/bin-string-workspace-helper": "1.0.0",
                    "bin-string-plugin": "1.0.0"
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += packageJson

        return PackageBinStringCliCorpus(
            projectRoot = projectRoot,
            packageDir = packageDir,
            workingDirectory = workingDirectory,
            packageJson = packageJson,
            cliPackageDir = File(packageDir, "node_modules/@autojs6/string-bin-cli"),
            cliEntry = File(packageDir, "node_modules/@autojs6/string-bin-cli/bin/cli.js"),
            configFile = configFile,
            configPathFromWorkingDirectory = configPathFromWorkingDirectory,
            files = written,
        )
    }

    private fun packageBinStringCliSource(): String {
        return """
            #!/usr/bin/env node
            "nodejs";
            const fs = require("fs");
            const fsp = require("fs/promises");
            const path = require("path");
            const nodePath = require("node:path");
            const os = require("os");
            const nodeOs = require("node:os");
            const { Buffer } = require("buffer");
            const { createRequire } = require("module");
            const url = require("url");
            const nodeUrl = require("node:url");
            const querystring = require("querystring");
            const nodeQuerystring = require("node:querystring");
            const stringDecoder = require("string_decoder");
            const nodeStringDecoder = require("node:string_decoder");
            const assert = require("assert");
            const nodeAssert = require("node:assert");
            const punycode = require("punycode");
            const nodePunycode = require("node:punycode");
            const crypto = require("crypto");
            const nodeCrypto = require("node:crypto");
            const zlib = require("zlib");
            const nodeZlib = require("node:zlib");
            const stream = require("stream");
            const nodeStream = require("node:stream");
            const streamPromises = require("stream/promises");
            const nodeStreamPromises = require("node:stream/promises");
            const streamWeb = require("stream/web");
            const nodeStreamWeb = require("node:stream/web");
            const streamConsumers = require("stream/consumers");
            const nodeStreamConsumers = require("node:stream/consumers");
            const events = require("events");
            const nodeEvents = require("node:events");
            const util = require("util");
            const nodeUtil = require("node:util");
            const feature = require("#feature");
            const local = require("../lib/local");
            const self = require("@autojs6/string-bin-cli");

            (async function main() {
            function parse(argv) {
              const result = {};
              argv.forEach(function parseArg(arg) {
                if (String(arg).indexOf("--") !== 0) {
                  return;
                }
                const body = String(arg).slice(2);
                const eq = body.indexOf("=");
                const key = eq >= 0 ? body.slice(0, eq) : body;
                const value = eq >= 0 ? body.slice(eq + 1) : true;
                const camelKey = key.replace(/-([a-z])/g, function camel(_, char) {
                  return char.toUpperCase();
                });
                if (eq >= 0) {
                  result[key] = value;
                  result[camelKey] = value;
                } else {
                  result[key] = value;
                  result[camelKey] = value;
                }
              });
              return result;
            }

            const parsed = parse(process.argv.slice(2));
            const label = String(parsed.label || "runner.bin_string");

            function option(name) {
              const value = parsed[name];
              return value === undefined || value === true ? "" : String(value);
            }

            function normalize(value) {
              return String(value || "")
                .replace(/\\/g, "/")
                .replace(/^\/data\/user\/0\//, "/data/data/");
            }

            function endsWithPath(value, suffix) {
              if (!suffix) {
                return true;
              }
              return normalize(value).endsWith("/" + suffix) || normalize(value).endsWith(suffix);
            }

            function hasArg(value) {
              return !value || process.argv.indexOf(value) >= 0;
            }

            function checked(block) {
              try {
                block();
                return true;
              } catch (_) {
                return false;
              }
            }

            function captureError(block) {
              try {
                block();
                return {
                  ok: false,
                  name: "NoError",
                  code: "UNEXPECTED_SUCCESS",
                  message: "unexpected success",
                  stack: ""
                };
              } catch (error) {
                return {
                  ok: true,
                  name: String(error && error.name || ""),
                  code: String(error && error.code || ""),
                  message: String(error && error.message || error),
                  stack: String(error && error.stack || "")
                };
              }
            }

            function errorText(error) {
              return [
                error.name || "",
                error.code || "",
                error.message || "",
                error.stack || ""
              ].join("\n");
            }

            function messageHas(error, token) {
              return errorText(error).indexOf(token) >= 0;
            }

            function messageHasAny(error, tokens) {
              return tokens.some(function hasToken(token) {
                return messageHas(error, token);
              });
            }

            async function runFsPromisesWorkflow() {
              const root = "./fs-promises-surface";
              const sourceDir = path.join(root, "source");
              const outputDir = path.join(root, "output");
              const configFile = path.join(sourceDir, "config.json");
              const tempFile = path.join(outputDir, "result.tmp");
              const resultFile = path.join(outputDir, "result.json");
              const copyFile = path.join(outputDir, "copy.json");

              await fsp.rm(root, { recursive: true, force: true });
              await fsp.mkdir(sourceDir, { recursive: true });
              await fsp.mkdir(outputDir, { recursive: true });
              await fsp.writeFile(configFile, JSON.stringify({
                name: "fs-promises",
                mode: parsed.mode || ""
              }), "utf8");
              const workflowConfig = JSON.parse(await fsp.readFile(configFile, "utf8"));
              await fsp.writeFile(tempFile, JSON.stringify({
                ok: workflowConfig.name === "fs-promises",
                mode: workflowConfig.mode,
                argvCount: process.argv.slice(2).length
              }), "utf8");
              await fsp.rename(tempFile, resultFile);

              const resultUrl = url.pathToFileURL(resultFile);
              const copyUrl = url.pathToFileURL(copyFile);
              await fsp.copyFile(resultUrl, copyUrl);
              const result = JSON.parse(await fsp.readFile(resultUrl, "utf8"));
              const copied = JSON.parse(await fsp.readFile(copyFile, "utf8"));
              const statInfo = await fsp.stat(resultFile);
              const entries = await fsp.readdir(outputDir, { withFileTypes: true });
              const realPath = await fsp.realpath(resultUrl);
              await fsp.unlink(copyFile);
              await fsp.rm(path.join(root, "missing.tmp"), { force: true });

              const atomicOk = fs.existsSync(resultFile) && !fs.existsSync(tempFile);
              const fileUrlOk =
                resultUrl.protocol === "file:" &&
                normalize(url.fileURLToPath(resultUrl)).endsWith("/fs-promises-surface/output/result.json") &&
                normalize(realPath).endsWith("/fs-promises-surface/output/result.json") &&
                copied.ok === true;
              const readdirOk =
                entries.some(function hasResult(entry) {
                  return entry.name === "result.json" && entry.isFile();
                }) &&
                entries.some(function hasCopy(entry) {
                  return entry.name === "copy.json" && entry.isFile();
                });

              return {
                ready: result.ok === true && statInfo.isFile() && atomicOk && fileUrlOk && readdirOk,
                atomic: atomicOk,
                fileUrl: fileUrlOk,
                readdir: readdirOk
              };
            }

            async function readAllWebStream(readable) {
              const reader = readable.getReader();
              const values = [];
              while (true) {
                const next = await reader.read();
                if (next.done) {
                  return values;
                }
                values.push(next.value);
              }
            }

            async function runContentProcessingWorkflow() {
              const root = "./content-processing-surface";
              const inputFile = path.join(root, "input.txt");
              const gzipFile = path.join(root, "payload.json.gz");
              const manifestFile = path.join(root, "manifest.json");
              const writeFile = util.promisify(fs.writeFile);
              const readFile = util.promisify(fs.readFile);
              const stat = util.promisify(fs.stat);

              await fsp.rm(root, { recursive: true, force: true });
              await fsp.mkdir(root, { recursive: true });

              const payloadText = [
                "AutoJs6 embedded node content",
                "label=" + label,
                "mode=" + (parsed.mode || ""),
                "entry=" + path.basename(process.argv[1] || "")
              ].join("\n");
              const payload = Buffer.from(payloadText, "utf8");
              await writeFile(inputFile, payload);

              const inputBuffer = await readFile(inputFile);
              const sha256 = crypto.createHash("sha256").update(inputBuffer).digest("hex");
              const sha1 = nodeCrypto.createHash("sha1").update(inputBuffer).digest("hex");
              const compressed = zlib.gzipSync(inputBuffer);
              await writeFile(gzipFile, compressed);

              const gzipUrl = url.pathToFileURL(gzipFile);
              const restored = zlib.gunzipSync(await fsp.readFile(gzipUrl)).toString("utf8");
              const deflated = nodeZlib.deflateSync(Buffer.from(restored, "utf8"));
              const inflated = nodeZlib.inflateSync(deflated).toString("utf8");

              const upper = new stream.Transform({
                transform(chunk, encoding, callback) {
                  callback(null, chunk.toString("utf8").toUpperCase());
                }
              });
              let streamed = "";
              const sink = new nodeStream.Writable({
                write(chunk, encoding, callback) {
                  streamed += chunk.toString("utf8");
                  callback();
                }
              });
              const streamDone = events.once(sink, "finish");
              stream.Readable.from(["stream:", sha256.slice(0, 12)]).pipe(upper).pipe(sink);
              await streamDone;

              const streamFile = path.join(root, "stream-file.txt");
              const streamCopyFile = path.join(root, "stream-copy.txt");
              const streamFileText = "file-stream:" + sha1.slice(0, 8);
              await writeFile(streamFile, streamFileText, "utf8");
              await new Promise(function copyWithFileStreams(resolve, reject) {
                const readable = fs.createReadStream(streamFile, { encoding: "utf8" });
                const writable = fs.createWriteStream(streamCopyFile, { encoding: "utf8" });
                readable.on("error", reject);
                writable.on("error", reject);
                writable.on("finish", resolve);
                readable.pipe(writable);
              });
              const fileStreamed = await fsp.readFile(streamCopyFile, "utf8");

              const streamModernFile = path.join(root, "stream-modern.txt");
              await writeFile(streamModernFile, "0123456789", "utf8");
              const fileStreamModernRead = await new Promise(function readModernFileStream(resolve, reject) {
                let text = "";
                const readable = fs.createReadStream(streamModernFile, {
                  encoding: "utf8",
                  start: 2,
                  end: 5,
                  highWaterMark: 2,
                  emitClose: false
                });
                readable.on("data", function onData(chunk) {
                  text += chunk;
                });
                readable.on("error", reject);
                readable.on("end", function onEnd() {
                  resolve(text);
                });
              });
              await new Promise(function writeModernFileStream(resolve, reject) {
                const writable = fs.createWriteStream(streamModernFile, {
                  encoding: "utf8",
                  flags: "r+",
                  start: 6,
                  highWaterMark: 1,
                  emitClose: false,
                  flush: true
                });
                writable.on("error", reject);
                writable.on("finish", resolve);
                writable.end("XYZ");
              });
              const fileStreamModernWritten = await fsp.readFile(streamModernFile, "utf8");
              const fileStreamAbortRead = await new Promise(function abortFileStream(resolve) {
                const controller = new AbortController();
                controller.abort();
                const readable = fs.createReadStream(streamModernFile, {
                  encoding: "utf8",
                  signal: controller.signal
                });
                readable.on("error", function onError(error) {
                  resolve(String(error && error.code || ""));
                });
                readable.resume();
              });

              let promised = "";
              await streamPromises.pipeline(
                stream.Readable.from(["promise:", sha1.slice(0, 8)]),
                new stream.Writable({
                  write(chunk, encoding, callback) {
                    promised += chunk.toString("utf8");
                    callback();
                  }
                })
              );
              const promisedFinished = new stream.PassThrough();
              const promisedFinishedWait = streamPromises.finished(promisedFinished);
              promisedFinished.resume();
              promisedFinished.end("done");
              await promisedFinishedWait;

              const consumedText = await streamConsumers.text(
                stream.Readable.from(["consumer:", sha1.slice(0, 8)])
              );
              const consumedJson = await streamConsumers.json(
                stream.Readable.from(['{"ok":', 'true}'])
              );
              const consumedBuffer = await streamConsumers.buffer(
                stream.Readable.from([Buffer.from("buffer:"), sha256.slice(0, 8)])
              );
              const consumedArrayBuffer = await streamConsumers.arrayBuffer(
                stream.Readable.from([Buffer.from("array:" + sha1.slice(0, 4))])
              );
              const consumedBlob = await streamConsumers.blob(
                stream.Readable.from([Buffer.from("blob:" + sha1.slice(0, 4))])
              );
              const consumedBlobText = await consumedBlob.text();

              const webText = (await readAllWebStream(new streamWeb.ReadableStream({
                start(controller) {
                  controller.enqueue("web:");
                  controller.enqueue(sha1.slice(0, 6));
                  controller.close();
                }
              }))).join("");
              let webWritten = "";
              const webWritable = new streamWeb.WritableStream({
                write(chunk) {
                  webWritten += String(chunk);
                }
              });
              const webWritableWriter = webWritable.getWriter();
              await webWritableWriter.write("write:");
              await webWritableWriter.write(sha256.slice(0, 6));
              await webWritableWriter.close();
              const webTransform = new streamWeb.TransformStream({
                transform(chunk, controller) {
                  controller.enqueue(String(chunk).toUpperCase());
                }
              });
              const webTransformedValues = readAllWebStream(webTransform.readable);
              const webTransformWriter = webTransform.writable.getWriter();
              await webTransformWriter.write("transform:");
              await webTransformWriter.write(sha1.slice(0, 4));
              await webTransformWriter.close();
              const webTransformed = (await webTransformedValues).join("");
              const webEncoder = new streamWeb.TextEncoderStream();
              const webEncodedValues = readAllWebStream(webEncoder.readable);
              const webEncoderWriter = webEncoder.writable.getWriter();
              await webEncoderWriter.write("encoded:" + sha1.slice(0, 4));
              await webEncoderWriter.close();
              const webEncodedText = Buffer.concat((await webEncodedValues).map((chunk) => Buffer.from(chunk))).toString("utf8");
              const webDecoder = new streamWeb.TextDecoderStream();
              const webDecodedValues = readAllWebStream(webDecoder.readable);
              const webDecoderWriter = webDecoder.writable.getWriter();
              await webDecoderWriter.write(Buffer.from("decoded:" + sha256.slice(0, 4)));
              await webDecoderWriter.close();
              const webDecodedText = (await webDecodedValues).join("");
              const webCountStrategy = new streamWeb.CountQueuingStrategy({ highWaterMark: 2 });
              const webByteStrategy = new streamWeb.ByteLengthQueuingStrategy({ highWaterMark: 8 });

              const promisified = await nodeUtil.promisify(function contentCallback(value, callback) {
                callback(null, value + ":" + sha1.slice(0, 8));
              })("content");
              const gzipStat = await stat(gzipFile);
              const manifest = {
                sha256,
                sha1,
                restoredLength: restored.length,
                gzipSize: gzipStat.size,
                streamed,
                fileStreamed,
                fileStreamModernRead,
                fileStreamModernWritten,
                promised,
                consumedText,
                consumedBuffer: consumedBuffer.toString("utf8"),
                webText,
                webTransformed,
                promisified
              };
              await fsp.writeFile(manifestFile, JSON.stringify(manifest, null, 2), "utf8");
              const manifestRoundTrip = JSON.parse(await fsp.readFile(manifestFile, "utf8"));

              const aliasesOk =
                crypto === nodeCrypto &&
                zlib === nodeZlib &&
                stream === nodeStream &&
                streamWeb === nodeStreamWeb &&
                events === nodeEvents &&
                util === nodeUtil;
              const cryptoOk =
                sha256.length === 64 &&
                sha1.length === 40 &&
                crypto.createHash("sha256").update("abc").digest("hex") ===
                  "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
              const zlibOk =
                Buffer.isBuffer(compressed) &&
                restored === payloadText &&
                inflated === payloadText &&
                normalize(url.fileURLToPath(gzipUrl)).endsWith("/content-processing-surface/payload.json.gz");
              const streamOk =
                streamed === "STREAM:" + sha256.slice(0, 12).toUpperCase() &&
                stream.Transform === nodeStream.Transform &&
                stream.Writable === nodeStream.Writable &&
                typeof stream.pipeline === "function" &&
                typeof stream.finished === "function" &&
                typeof stream.isReadable === "function" &&
                typeof stream.isWritable === "function" &&
                typeof stream.isErrored === "function" &&
                typeof stream.isDestroyed === "function" &&
                typeof stream.isDisturbed === "function" &&
                typeof stream.promises === "undefined";
              const fileStreamOk =
                fileStreamed === streamFileText &&
                fileStreamModernRead === "2345" &&
                fileStreamModernWritten === "012345XYZ9" &&
                fileStreamAbortRead === "ABORT_ERR" &&
                typeof fs.createReadStream === "function" &&
                typeof fs.createWriteStream === "function";
              const streamPromisesOk =
                streamPromises === nodeStreamPromises &&
                promised === "promise:" + sha1.slice(0, 8) &&
                Object.keys(streamPromises).sort().join(",") === "finished,pipeline" &&
                require.resolve("stream/promises") === "stream/promises" &&
                require.resolve("node:stream/promises") === "stream/promises" &&
                typeof streamPromises.Readable === "undefined" &&
                Object.isFrozen(streamPromises);
              const streamWebOk =
                streamWeb === nodeStreamWeb &&
                webText === "web:" + sha1.slice(0, 6) &&
                webWritten === "write:" + sha256.slice(0, 6) &&
                webTransformed === ("transform:" + sha1.slice(0, 4)).toUpperCase() &&
                webEncodedText === "encoded:" + sha1.slice(0, 4) &&
                webDecodedText === "decoded:" + sha256.slice(0, 4) &&
                webCountStrategy.highWaterMark === 2 &&
                webCountStrategy.size("value") === 1 &&
                webByteStrategy.highWaterMark === 8 &&
                webByteStrategy.size(new Uint8Array([1, 2, 3])) === 3 &&
                Object.keys(streamWeb).sort().join(",") === "ByteLengthQueuingStrategy,CountQueuingStrategy,ReadableStream,TextDecoderStream,TextEncoderStream,TransformStream,WritableStream" &&
                require.resolve("stream/web") === "stream/web" &&
                require.resolve("node:stream/web") === "stream/web" &&
                typeof streamWeb.CompressionStream === "undefined" &&
                Object.isFrozen(streamWeb);
              const streamConsumersOk =
                streamConsumers === nodeStreamConsumers &&
                consumedText === "consumer:" + sha1.slice(0, 8) &&
                consumedJson.ok === true &&
                Buffer.isBuffer(consumedBuffer) &&
                consumedBuffer.toString("utf8") === "buffer:" + sha256.slice(0, 8) &&
                consumedArrayBuffer instanceof ArrayBuffer &&
                Buffer.from(consumedArrayBuffer).toString("utf8") === "array:" + sha1.slice(0, 4) &&
                consumedBlobText === "blob:" + sha1.slice(0, 4) &&
                require.resolve("stream/consumers") === "stream/consumers" &&
                require.resolve("node:stream/consumers") === "stream/consumers" &&
                Object.keys(streamConsumers).sort().join(",") === "arrayBuffer,blob,buffer,json,text" &&
                typeof streamConsumers.pipeline === "undefined" &&
                Object.isFrozen(streamConsumers);
              const promisifyOk =
                promisified === "content:" + sha1.slice(0, 8) &&
                typeof util.promisify === "function";
              const manifestOk =
                manifestRoundTrip.sha256 === sha256 &&
                manifestRoundTrip.sha1 === sha1 &&
                manifestRoundTrip.streamed === streamed &&
                manifestRoundTrip.fileStreamed === fileStreamed &&
                manifestRoundTrip.fileStreamModernRead === fileStreamModernRead &&
                manifestRoundTrip.fileStreamModernWritten === fileStreamModernWritten &&
                manifestRoundTrip.promised === promised &&
                manifestRoundTrip.consumedText === consumedText &&
                manifestRoundTrip.consumedBuffer === consumedBuffer.toString("utf8") &&
                manifestRoundTrip.webText === webText &&
                manifestRoundTrip.webTransformed === webTransformed &&
                manifestRoundTrip.promisified === promisified &&
                manifestRoundTrip.gzipSize === gzipStat.size;

              return {
                ready: aliasesOk && cryptoOk && zlibOk && streamOk && fileStreamOk && streamPromisesOk && streamWebOk && streamConsumersOk && promisifyOk && manifestOk,
                aliases: aliasesOk,
                crypto: cryptoOk,
                zlib: zlibOk,
                stream: streamOk,
                fileStream: fileStreamOk,
                streamPromises: streamPromisesOk,
                streamWeb: streamWebOk,
                streamConsumers: streamConsumersOk,
                promisify: promisifyOk,
                manifest: manifestOk
              };
            }

            async function runConfigCacheWorkflow() {
              const tmpDir = os.tmpdir();
              const cwd = process.cwd();
              const tmpdirOk = normalize(tmpDir) === normalize(cwd);
              const relativeCacheRoot = tmpdirOk
                ? path.join(".autojs6-node-cache", "bin-string-cli")
                : path.relative(cwd, path.join(tmpDir, ".autojs6-node-cache", "bin-string-cli"));
              const relativeCacheRootNormalized = normalize(relativeCacheRoot);
              const relativePathOk =
                tmpdirOk &&
                relativeCacheRootNormalized.length > 0 &&
                relativeCacheRootNormalized.indexOf("..") !== 0 &&
                !path.isAbsolute(relativeCacheRoot);

              if (!relativePathOk) {
                return {
                  ready: false,
                  config: false,
                  tmpdir: tmpdirOk,
                  relativePath: false,
                  missHit: false,
                  manifest: false
                };
              }

              await fsp.mkdir(relativeCacheRoot, { recursive: true });
              const configName = String(config.name || "");
              const configItems = Array.isArray(config.items) ? config.items.join("|") : "";
              const source = JSON.stringify({
                label,
                mode: parsed.mode || "",
                entry: path.basename(process.argv[1] || ""),
                configName,
                configItems,
                plugin: config.plugin || "",
                pluginResolved
              });
              const cacheKey = crypto.createHash("sha256").update(source).digest("hex").slice(0, 16);
              const cacheFile = path.join(relativeCacheRoot, cacheKey + ".cache.json");
              const cacheUrl = url.pathToFileURL(cacheFile);

              await fsp.rm(cacheFile, { force: true });
              const missOk = !fs.existsSync(cacheFile);
              const manifest = {
                key: cacheKey,
                label,
                mode: parsed.mode || "",
                configName,
                configItems,
                pluginResolved,
                tmpdir: relativeCacheRootNormalized,
                cwd: expectedCwd || path.basename(cwd),
                entry: path.basename(process.argv[1] || "")
              };
              await fsp.writeFile(cacheFile, JSON.stringify(manifest, null, 2), "utf8");
              const firstRead = JSON.parse(await fsp.readFile(cacheFile, "utf8"));
              const secondRead = JSON.parse(await fsp.readFile(cacheUrl, "utf8"));
              const cacheStat = await fsp.stat(cacheFile);
              const cacheEntries = await fsp.readdir(relativeCacheRoot);

              const configOk =
                configName === "autojs6-bin-string-config" &&
                configItems === "exports|imports|createRequire" &&
                pluginResolved.indexOf("node_modules/bin-string-plugin/index.js") >= 0;
              const cachePathOk =
                normalize(url.fileURLToPath(cacheUrl)).endsWith(
                  "/.autojs6-node-cache/bin-string-cli/" + cacheKey + ".cache.json"
                ) &&
                firstRead.tmpdir === relativeCacheRootNormalized;
              const missHitOk =
                missOk &&
                fs.existsSync(cacheFile) &&
                firstRead.key === cacheKey &&
                secondRead.key === cacheKey &&
                cacheEntries.indexOf(cacheKey + ".cache.json") >= 0;
              const manifestOk =
                cacheStat.isFile() &&
                firstRead.label === label &&
                firstRead.mode === (parsed.mode || "") &&
                firstRead.configName === configName &&
                firstRead.configItems === configItems &&
                secondRead.pluginResolved === pluginResolved &&
                secondRead.entry === path.basename(process.argv[1] || "");

              return {
                ready: configOk && tmpdirOk && relativePathOk && cachePathOk && missHitOk && manifestOk,
                config: configOk,
                tmpdir: tmpdirOk,
                relativePath: relativePathOk && cachePathOk,
                missHit: missHitOk,
                manifest: manifestOk
              };
            }

            function normalizeRelativePath(value) {
              let normalized = normalize(value).replace(/\/+$/g, "");
              while (normalized.indexOf("./") === 0) {
                normalized = normalized.slice(2);
              }
              return normalized || ".";
            }

            function sameRelativePath(left, right) {
              return normalizeRelativePath(left) === normalizeRelativePath(right);
            }

            function discoverConfigPath(startDir, stopAt) {
              let current = normalizeRelativePath(startDir || ".");
              const stop = stopAt ? normalizeRelativePath(stopAt) : "";
              for (let depth = 0; depth < 12; depth += 1) {
                const candidate = current === "."
                  ? "bin-string.config.json"
                  : path.join(current, "bin-string.config.json");
                if (fs.existsSync(candidate)) {
                  return candidate;
                }
                if (stop && sameRelativePath(current, stop)) {
                  return "";
                }
                current = path.join(current, "..");
              }
              return "";
            }

            function loadDiscoveredConfig(configFile) {
              return {
                path: configFile,
                config: JSON.parse(fs.readFileSync(configFile, "utf8")),
                require: createRequire(configFile)
              };
            }

            function runConfigDiscoveryWorkflow() {
              if (!configPath) {
                return {
                  ready: false,
                  cwd: false,
                  packagePriority: false,
                  workspaceFallback: false,
                  mixedPlugins: false,
                  missingDiagnostic: false
                };
              }

              const cwdConfigPath = discoverConfigPath(".");
              const packageConfigPath = discoverConfigPath(path.dirname(configPath));
              const missingRoot = path.join("config-discovery", "missing-root");
              const missingStart = path.join(missingRoot, "nested");
              fs.mkdirSync(missingStart, { recursive: true });
              const missingConfigPath = discoverConfigPath(missingStart, missingRoot);

              const cwdDiscoveredOk =
                !!cwdConfigPath &&
                !sameRelativePath(cwdConfigPath, configPath) &&
                sameRelativePath(cwdConfigPath, "../../bin-string.config.json");
              const packageDiscoveredOk =
                !!packageConfigPath &&
                sameRelativePath(packageConfigPath, configPath);
              const missingDiagnosticOk = missingConfigPath === "";

              if (!cwdDiscoveredOk || !packageDiscoveredOk) {
                return {
                  ready: false,
                  cwd: cwdDiscoveredOk,
                  packagePriority: packageDiscoveredOk,
                  workspaceFallback: false,
                  mixedPlugins: false,
                  missingDiagnostic: missingDiagnosticOk
                };
              }

              const cwdLoaded = loadDiscoveredConfig(cwdConfigPath);
              const packageLoaded = loadDiscoveredConfig(packageConfigPath);
              const packagePlugin = packageLoaded.require(packageLoaded.config.plugin);
              const packagePluginResult = packagePlugin.apply(packageLoaded.config);
              const rootPlugin = cwdLoaded.require(cwdLoaded.config.plugin);
              const rootPluginResult = rootPlugin.apply(cwdLoaded.config);
              const rootScopedHelper = cwdLoaded.require(cwdLoaded.config.scopedHelper);
              const rootWorkspaceTool = cwdLoaded.require(cwdLoaded.config.workspacePackage);
              const rootWorkspaceInfo = rootWorkspaceTool.discover(cwdLoaded.config);

              const packagePriorityOk =
                packageDiscoveredOk &&
                packageLoaded.config.name === "autojs6-bin-string-config" &&
                packagePlugin.name === "bin-string-plugin" &&
                packagePluginResult === "autojs6-bin-string-config:plugin-helper:exports+imports+createRequire";
              const workspaceFallbackOk =
                cwdDiscoveredOk &&
                cwdLoaded.config.name === "autojs6-bin-string-workspace-root" &&
                cwdLoaded.config.priority === "workspace-root" &&
                rootWorkspaceInfo.packageName === "bin-string-workspace-helper" &&
                rootWorkspaceInfo.value === "workspace-config:workspace+root+fallback";
              const mixedPluginsOk =
                rootPlugin.name === "bin-string-plugin" &&
                rootPluginResult === "autojs6-bin-string-workspace-root:plugin-helper:workspace+root+fallback" &&
                rootScopedHelper.value === "scoped-workspace-helper" &&
                rootScopedHelper.local === "scoped-local-helper" &&
                rootWorkspaceInfo.self === "workspace-config";

              return {
                ready:
                  cwdDiscoveredOk &&
                  packagePriorityOk &&
                  workspaceFallbackOk &&
                  mixedPluginsOk &&
                  missingDiagnosticOk,
                cwd: cwdDiscoveredOk,
                packagePriority: packagePriorityOk,
                workspaceFallback: workspaceFallbackOk,
                mixedPlugins: mixedPluginsOk,
                missingDiagnostic: missingDiagnosticOk
              };
            }

            async function runWorkspacePackageWorkflow() {
              const workspaceRequest = String(config.workspacePackage || "");
              const scopedRequest = String(config.scopedHelper || "");
              if (!workspaceRequest || !scopedRequest) {
                return {
                  ready: false,
                  configDiscovery: false,
                  scopedHelper: false,
                  selfReference: false,
                  cachePartition: false
                };
              }

              const workspaceTool = configRequire(workspaceRequest);
              const workspaceToolAgain = configRequire(workspaceRequest);
              const workspaceResolved = normalize(configRequire.resolve(workspaceRequest));
              const workspaceInfo = workspaceTool.discover(config);
              const scopedHelper = configRequire(scopedRequest);
              const scopedHelperAgain = configRequire(scopedRequest);
              const scopedCacheKey = configRequire(scopedRequest + "/cache-key");
              const selfFeature = require("@autojs6/string-bin-cli/feature");
              const selfAgain = require("@autojs6/string-bin-cli");
              const selfFeatureResolved = normalize(require.resolve("@autojs6/string-bin-cli/feature"));

              const scopedPartition = scopedHelper.partition(label + ":" + (parsed.mode || ""));
              const workspacePartition = workspaceInfo.cachePartition;
              const partitionRoot = path.join(".autojs6-node-cache", "bin-string-cli", "workspace-partitions");
              await fsp.mkdir(partitionRoot, { recursive: true });
              const scopedCacheFile = path.join(partitionRoot, scopedPartition + ".json");
              const workspaceCacheFile = path.join(partitionRoot, workspacePartition + ".json");
              await fsp.rm(scopedCacheFile, { force: true });
              await fsp.rm(workspaceCacheFile, { force: true });
              await fsp.writeFile(scopedCacheFile, JSON.stringify({
                kind: "scoped",
                partition: scopedPartition,
                helper: scopedHelper.value,
                mode: parsed.mode || ""
              }), "utf8");
              await fsp.writeFile(workspaceCacheFile, JSON.stringify({
                kind: "workspace",
                partition: workspacePartition,
                helper: workspaceInfo.packageName,
                mode: parsed.mode || ""
              }), "utf8");
              const scopedCache = JSON.parse(await fsp.readFile(scopedCacheFile, "utf8"));
              const workspaceCache = JSON.parse(await fsp.readFile(url.pathToFileURL(workspaceCacheFile), "utf8"));

              const configDiscoveryOk =
                workspaceTool === workspaceToolAgain &&
                workspaceResolved.endsWith("/packages/bin-string-workspace-helper/index.js") &&
                workspaceInfo.packageName === "bin-string-workspace-helper" &&
                workspaceInfo.value === "workspace-config:exports+imports+createRequire" &&
                workspaceInfo.self === "workspace-config";
              const scopedHelperOk =
                scopedHelper === scopedHelperAgain &&
                scopedHelper.value === "scoped-workspace-helper" &&
                scopedHelper.local === "scoped-local-helper" &&
                scopedHelper.self === "cache-key" &&
                scopedCacheKey.kind === "cache-key" &&
                scopedHelper.cacheKey("workspace") === scopedCacheKey.cacheKey("workspace");
              const selfReferenceOk =
                self === selfAgain &&
                selfFeature.value === "imports-feature" &&
                selfFeatureResolved.endsWith("/node_modules/@autojs6/string-bin-cli/lib/feature.js");
              const cachePartitionOk =
                scopedPartition.length === 10 &&
                workspacePartition.length === 10 &&
                scopedPartition !== workspacePartition &&
                scopedCache.kind === "scoped" &&
                workspaceCache.kind === "workspace" &&
                scopedCache.partition === scopedPartition &&
                workspaceCache.partition === workspacePartition &&
                fs.existsSync(scopedCacheFile) &&
                fs.existsSync(workspaceCacheFile);

              return {
                ready: configDiscoveryOk && scopedHelperOk && selfReferenceOk && cachePartitionOk,
                configDiscovery: configDiscoveryOk,
                scopedHelper: scopedHelperOk,
                selfReference: selfReferenceOk,
                cachePartition: cachePartitionOk
              };
            }

            function runWorkspaceFailureDiagnosticsWorkflow() {
              const workspaceRequest = String(config.workspacePackage || "");
              const scopedRequest = String(config.scopedHelper || "");
              if (!workspaceRequest || !scopedRequest || !configPath) {
                return {
                  ready: false,
                  missingPackage: false,
                  missingScopedHelper: false,
                  missingScopedExport: false,
                  selfMissingExport: false,
                  relativeEscape: false,
                  fsAbsoluteDenied: false,
                  envRedacted: false
                };
              }

              const missingPackage = captureError(function requireMissingWorkspacePackage() {
                configRequire("../missing-workspace-helper");
              });
              const missingScopedHelper = captureError(function requireMissingScopedHelper() {
                configRequire("@autojs6/missing-bin-string-workspace-helper");
              });
              const missingScopedExport = captureError(function requireMissingScopedExport() {
                configRequire(scopedRequest + "/missing");
              });
              const selfMissingExport = captureError(function requireMissingSelfExport() {
                const workspaceResolved = configRequire.resolve(workspaceRequest);
                createRequire(workspaceResolved)("bin-string-workspace-helper/missing");
              });
              const relativeEscape = captureError(function requireEscapingWorkspacePackage() {
                configRequire("../../../outside-workspace-helper");
              });
              const fsAbsolute = captureError(function readConfigWithAbsolutePath() {
                fs.readFileSync(path.resolve(configPath), "utf8");
              });

              const missingPackageOk =
                missingPackage.ok &&
                missingPackage.code === "MODULE_NOT_FOUND" &&
                messageHas(missingPackage, "missing-workspace-helper");
              const missingScopedHelperOk =
                missingScopedHelper.ok &&
                missingScopedHelper.code === "MODULE_NOT_FOUND" &&
                messageHas(missingScopedHelper, "@autojs6/missing-bin-string-workspace-helper");
              const missingScopedExportOk =
                missingScopedExport.ok &&
                (
                  missingScopedExport.code === "ERR_PACKAGE_PATH_NOT_EXPORTED" ||
                  messageHas(missingScopedExport, "exports does not define subpath") ||
                  messageHas(missingScopedExport, "package subpath is not exported")
                ) &&
                messageHas(missingScopedExport, "/missing");
              const selfMissingExportOk =
                selfMissingExport.ok &&
                (
                  selfMissingExport.code === "ERR_PACKAGE_PATH_NOT_EXPORTED" ||
                  messageHas(selfMissingExport, "exports does not define subpath") ||
                  messageHas(selfMissingExport, "package subpath is not exported")
                ) &&
                messageHas(selfMissingExport, "bin-string-workspace-helper");
              const relativeEscapeOk =
                relativeEscape.ok &&
                messageHas(relativeEscape, "outside-workspace-helper") &&
                messageHasAny(relativeEscape, [
                  "Relative require escapes Embedded Node working directory",
                  "dynamic require fallback denied",
                  "MODULE_NOT_FOUND",
                  "Cannot find module"
                ]);
              const fsAbsoluteDeniedOk =
                fsAbsolute.ok &&
                messageHas(fsAbsolute, "rejects absolute path");
              const secretValue = expectedEnvKey ? expectedEnvValueFor(expectedEnvKey) : "";
              const capturedFailures = [
                missingPackage,
                missingScopedHelper,
                missingScopedExport,
                selfMissingExport,
                relativeEscape,
                fsAbsolute
              ];
              const envRedactedOk =
                !secretValue ||
                capturedFailures.every(function errorDoesNotLeakSecret(error) {
                  return errorText(error).indexOf(secretValue) < 0;
                });

              return {
                ready:
                  missingPackageOk &&
                  missingScopedHelperOk &&
                  missingScopedExportOk &&
                  selfMissingExportOk &&
                  relativeEscapeOk &&
                  fsAbsoluteDeniedOk &&
                  envRedactedOk,
                missingPackage: missingPackageOk,
                missingScopedHelper: missingScopedHelperOk,
                missingScopedExport: missingScopedExportOk,
                selfMissingExport: selfMissingExportOk,
                relativeEscape: relativeEscapeOk,
                fsAbsoluteDenied: fsAbsoluteDeniedOk,
                envRedacted: envRedactedOk
              };
            }

            function expectedEnvValueFor(key) {
              const explicit = option("expectEnvValue");
              if (explicit) {
                return explicit;
              }
              if (key === "BIN_STRING_REQUEST_ENV") {
                return "visible-string";
              }
              if (key === "BIN_STRING_BIN_ENV") {
                return "visible-bin-string";
              }
              if (key === "PACKAGED_BIN_STRING_ENV") {
                return "packaged-bin-string-secret";
              }
              return "";
            }

            function optionError(name, value, expected) {
              const error = new Error("Invalid option " + name + ": expected " + expected + ", got " + String(value));
              error.code = "ERR_AUTOJS6_OPTION_PARSE";
              return error;
            }

            function splitOptionList(value) {
              if (Array.isArray(value)) {
                return value.map(function stringifyItem(item) {
                  return String(item);
                }).filter(Boolean);
              }
              if (value === undefined || value === null || value === false) {
                return [];
              }
              if (value === true) {
                return [];
              }
              return String(value).split(",").map(function trimItem(item) {
                return item.trim();
              }).filter(Boolean);
            }

            function hasOptionListItem(items, item) {
              return items.indexOf(item) >= 0;
            }

            function parseNumberOption(name, value, fallback) {
              const candidate = value === undefined ? fallback : value;
              const numeric = typeof candidate === "number" ? candidate : Number(candidate);
              if (!Number.isFinite(numeric)) {
                throw optionError(name, candidate, "finite number");
              }
              return numeric;
            }

            function parseBooleanOption(name, value, fallback) {
              const candidate = value === undefined ? fallback : value;
              if (candidate === true || candidate === "true") {
                return true;
              }
              if (candidate === false || candidate === "false") {
                return false;
              }
              throw optionError(name, candidate, "boolean");
            }

            function runOptionMergeWorkflow() {
              const options = config.options || {};
              const mode = String(parsed.mode || "");
              const configFeatures = splitOptionList(options.features);
              const argvFeatures = splitOptionList(parsed.mergeFeatures);
              const mergedFeatures = configFeatures.concat(argvFeatures);
              const profile = parsed.mergeProfile === undefined ? String(options.profile || "") : String(parsed.mergeProfile);
              const count = parseNumberOption("merge-count", parsed.mergeCount, options.count);
              const enabled = parseBooleanOption("merge-enabled", parsed.mergeEnabled, options.enabled);
              const modeDefaults = options.modeDefaults || {};
              const modeDefault = String(modeDefaults[mode] || "");
              const expectedSecret = expectedEnvKey ? expectedEnvValueFor(expectedEnvKey) : "";
              const envValue = expectedEnvKey ? String(process.env[expectedEnvKey] || "") : "";
              const envMerged = envValue || String(options.envFallback || "");
              const invalidNumber = captureError(function parseInvalidNumberOption() {
                parseNumberOption("merge-invalid-count", parsed.mergeInvalidCount, 0);
              });
              const invalidBoolean = captureError(function parseInvalidBooleanOption() {
                parseBooleanOption("merge-invalid-enabled", parsed.mergeInvalidEnabled, false);
              });
              const diagnostics = [invalidNumber, invalidBoolean];
              const diagnosticsRedactedOk =
                !expectedSecret ||
                diagnostics.every(function diagnosticDoesNotLeakSecret(error) {
                  return errorText(error).indexOf(expectedSecret) < 0;
                });
              const configOk =
                options.profile === "config-profile" &&
                options.count === 3 &&
                options.enabled === false &&
                hasOptionListItem(configFeatures, "config-feature") &&
                hasOptionListItem(configFeatures, "workspace-feature") &&
                modeDefault === "config-" + mode + "-default";
              const argvOk =
                profile === "argv-profile" &&
                count === 7 &&
                enabled === true;
              const listOk =
                hasOptionListItem(mergedFeatures, "config-feature") &&
                hasOptionListItem(mergedFeatures, "workspace-feature") &&
                hasOptionListItem(mergedFeatures, "argv-feature") &&
                (
                  hasOptionListItem(mergedFeatures, "script-feature") ||
                  hasOptionListItem(mergedFeatures, "bin-feature")
                );
              const precedenceOk =
                profile !== options.profile &&
                count !== options.count &&
                enabled !== options.enabled;
              const envOk =
                !expectedEnvKey ||
                (
                  envMerged === expectedSecret &&
                  envMerged !== String(options.envFallback || "")
                );
              const invalidOk =
                invalidNumber.ok &&
                invalidNumber.code === "ERR_AUTOJS6_OPTION_PARSE" &&
                messageHas(invalidNumber, "merge-invalid-count") &&
                invalidBoolean.ok &&
                invalidBoolean.code === "ERR_AUTOJS6_OPTION_PARSE" &&
                messageHas(invalidBoolean, "merge-invalid-enabled") &&
                diagnosticsRedactedOk;

              return {
                ready:
                  configOk &&
                  argvOk &&
                  listOk &&
                  precedenceOk &&
                  envOk &&
                  invalidOk,
                config: configOk,
                argv: argvOk,
                list: listOk,
                precedence: precedenceOk,
                env: envOk,
                invalid: invalidOk,
                modeDefault: modeDefault === "config-" + mode + "-default",
                diagnosticsRedacted: diagnosticsRedactedOk,
                profile: profile,
                count: count,
                enabled: enabled,
                features: mergedFeatures
              };
            }

            const expectedEntry = option("expectEntry");
            const expectedCwd = option("expectCwd");
            const expectedInitCwd = option("expectInitCwd");
            const expectedPackageDir = option("expectPackageDir");
            const expectedPackageJsonDir = option("expectPackageJsonDir");
            const expectedPackageName = option("expectPackageName");
            const expectedPackageVersion = option("expectPackageVersion");
            const expectedScript = option("expectScript");
            const expectedBin = option("expectBin");
            const expectedBinPathToken = option("expectBinPathToken") || (expectedBin ? expectedBin.split("/").pop() : "");
            const expectedScriptArg = option("expectScriptArg");
            const expectedRequestArg = option("expectRequestArg");
            const expectedEnvKey = option("expectEnvKey");
            const expectedDescriptor = option("expectDescriptor");
            const expectedLaunchKind = option("expectLaunchKind");
            const expectedMode = option("mode");
            const configPath = option("expectConfig");
            const config = configPath ? JSON.parse(fs.readFileSync(configPath, "utf8")) : {};
            const configRequire = configPath ? createRequire(configPath) : createRequire(__filename);
            const plugin = config.plugin ? configRequire(config.plugin) : {};
            const pluginAgain = config.plugin ? configRequire(config.plugin) : {};
            const pluginResolved = config.plugin ? configRequire.resolve(config.plugin) : "";
            const pluginResult = plugin.apply ? plugin.apply(config) : "";
            const output = [
              config.name || "",
              feature.value,
              self.value,
              self.feature,
              plugin.value || "",
              plugin.helper || "",
              pluginResult,
              local.value
            ].join("|");

            let descriptorOk = !expectedDescriptor;
            if (expectedDescriptor) {
              const descriptor = JSON.parse(fs.readFileSync(expectedDescriptor, "utf8"));
              descriptorOk =
                (!expectedLaunchKind || descriptor.launchKind === expectedLaunchKind) &&
                (!expectedPackageDir || descriptor.packageDir === expectedPackageDir) &&
                (!expectedCwd || descriptor.workingDirectory === expectedCwd) &&
                (!expectedScript || descriptor.scriptName === expectedScript);
            }

            const entryOk = endsWithPath(process.argv[1], expectedEntry);
            const cwdOk = endsWithPath(process.cwd(), expectedCwd);
            const packageDirOk =
              !expectedPackageDir ||
              normalize(process.argv[1]).indexOf("/" + expectedPackageDir + "/") >= 0 ||
              normalize(process.argv[1]).indexOf(expectedPackageDir + "/") >= 0;
            const packageJsonOk =
              !expectedPackageJsonDir ||
              endsWithPath(process.env.npm_package_json, expectedPackageJsonDir + "/package.json");
            const packageNameOk = !expectedPackageName || process.env.npm_package_name === expectedPackageName;
            const packageVersionOk = !expectedPackageVersion || process.env.npm_package_version === expectedPackageVersion;
            const initCwdOk = !expectedInitCwd || endsWithPath(process.env.INIT_CWD, expectedInitCwd);
            const lifecycleOk = !expectedScript || process.env.npm_lifecycle_event === expectedScript;
            const binOk = !expectedBinPathToken || normalize(process.argv[1]).indexOf(expectedBinPathToken) >= 0;
            const scriptArgOk = hasArg(expectedScriptArg);
            const requestArgOk = hasArg(expectedRequestArg);
            const envOk = !expectedEnvKey || process.env[expectedEnvKey] === expectedEnvValueFor(expectedEnvKey);
            const requireMainOk = require.main === module && module.parent === null;
            const mainNotRunOk = !parsed.expectMainNotRun || !fs.existsSync("./unexpected-main.txt");
            const exportsOk = self.value === "self-index" && self.feature === "imports-feature";
            const importsOk = feature.value === "imports-feature";
            const createRequireOk =
              pluginResolved.indexOf("node_modules/bin-string-plugin/index.js") >= 0 &&
              plugin === pluginAgain;
            const pluginOk =
              plugin.name === "bin-string-plugin" &&
              plugin.helper === "plugin-helper" &&
              pluginResult === "autojs6-bin-string-config:plugin-helper:exports+imports+createRequire";
            const bufferOk =
              Buffer.from("bin-string").toString("hex") === "62696e2d737472696e67" &&
              Buffer.from(output, "utf8").toString("utf8") === output;
            const parsedUrl = new url.URL("https://example.test/tools?name=AutoJs6&mode=node#fragment");
            const parsedParams = new nodeUrl.URLSearchParams("name=AutoJs6&mode=node");
            const urlOk =
              url === nodeUrl &&
              typeof url.URL === "function" &&
              typeof nodeUrl.URLSearchParams === "function" &&
              parsedUrl.hostname === "example.test" &&
              parsedUrl.searchParams.get("name") === "AutoJs6" &&
              parsedParams.get("mode") === "node";
            const parsedQuery = querystring.parse("name=AutoJs6&mode=node&space=a+b&repeat=one&repeat=two");
            const queryText = querystring.stringify({ name: "AutoJs6", mode: "node", space: "a b" });
            const querystringOk =
              querystring === nodeQuerystring &&
              parsedQuery.name === "AutoJs6" &&
              parsedQuery.space === "a b" &&
              Array.isArray(parsedQuery.repeat) &&
              parsedQuery.repeat[0] === "one" &&
              parsedQuery.repeat[1] === "two" &&
              queryText.indexOf("space=a%20b") >= 0;
            const decoder = new stringDecoder.StringDecoder("utf8");
            const nodeDecoder = new nodeStringDecoder.StringDecoder("utf8");
            const decoded =
              decoder.write(Buffer.from([0xe4, 0xbd])) +
              decoder.write(Buffer.from([0xa0, 0xe5, 0xa5, 0xbd])) +
              decoder.end();
            const stringDecoderOk =
              stringDecoder === nodeStringDecoder &&
              decoded.length === 2 &&
              decoded.charCodeAt(0) === 20320 &&
              decoded.charCodeAt(1) === 22909 &&
              nodeDecoder.write(Buffer.from([0x41])) + nodeDecoder.end() === "A";
            const assertOk =
              assert === nodeAssert &&
              checked(function checkAssertBuiltin() {
                assert.strictEqual(parsedUrl.protocol, "https:");
                nodeAssert.deepStrictEqual(parsedQuery.repeat, ["one", "two"]);
              });
            const unicodeDomain = "ma" + String.fromCharCode(241) + "ana.com";
            const asciiDomain = punycode.toASCII(unicodeDomain);
            const punycodeOk =
              punycode === nodePunycode &&
              asciiDomain.indexOf("xn--") === 0 &&
              punycode.toUnicode(asciiDomain) === unicodeDomain &&
              typeof punycode.ucs2.decode === "function";
            const processOk =
              typeof process.version === "string" &&
              process.version.indexOf("v") === 0 &&
              process.versions &&
              typeof process.versions.node === "string" &&
              typeof process.platform === "string" &&
              process.platform.length > 0 &&
              typeof process.arch === "string" &&
              process.arch.length > 0 &&
              process.argv[0] === "autojs6-node";
            const osOk =
              os === nodeOs &&
              os.platform() === process.platform &&
              os.arch() === process.arch &&
              typeof os.EOL === "string" &&
              (!expectedCwd || endsWithPath(os.tmpdir(), expectedCwd));
            const resolvedProbe = path.resolve("./runtime-surface/probe.txt");
            const pathOk =
              path === nodePath &&
              path.basename(process.argv[1] || "") === "cli.js" &&
              path.join("runtime-surface", "probe.txt") === "runtime-surface/probe.txt" &&
              path.relative(process.cwd(), resolvedProbe) === "runtime-surface/probe.txt";
            fs.mkdirSync("./runtime-surface", { recursive: true });
            fs.writeFileSync("./runtime-surface/probe.txt", "runtime-surface", "utf8");
            const probeUrl = url.pathToFileURL("./runtime-surface/probe.txt");
            const urlFileOk =
              probeUrl.protocol === "file:" &&
              normalize(url.fileURLToPath(probeUrl)).endsWith("/runtime-surface/probe.txt") &&
              fs.readFileSync(probeUrl, "utf8") === "runtime-surface";
            const runtimeSurfaceOk =
              processOk &&
              osOk &&
              pathOk &&
              urlFileOk;
            const fsPromisesResult = await runFsPromisesWorkflow();
            const fsPromisesOk = fsPromisesResult.ready;
            const contentProcessingResult = await runContentProcessingWorkflow();
            const contentProcessingOk = contentProcessingResult.ready;
            const configCacheResult = await runConfigCacheWorkflow();
            const configCacheOk = configCacheResult.ready;
            const configDiscoveryResult = runConfigDiscoveryWorkflow();
            const configDiscoveryOk = configDiscoveryResult.ready;
            const optionMergeResult = runOptionMergeWorkflow();
            const optionMergeOk = optionMergeResult.ready;
            const workspacePackageResult = await runWorkspacePackageWorkflow();
            const workspacePackageOk = workspacePackageResult.ready;
            const workspaceFailureDiagnosticsResult = runWorkspaceFailureDiagnosticsWorkflow();
            const workspaceFailureDiagnosticsOk = workspaceFailureDiagnosticsResult.ready;
            const coreBuiltinsOk =
              urlOk &&
              querystringOk &&
              stringDecoderOk &&
              assertOk &&
              punycodeOk;
            const modeOk = !expectedMode || parsed.mode === expectedMode;
            const ready =
              entryOk &&
              cwdOk &&
              packageDirOk &&
              packageJsonOk &&
              packageNameOk &&
              packageVersionOk &&
              initCwdOk &&
              lifecycleOk &&
              binOk &&
              scriptArgOk &&
              requestArgOk &&
              envOk &&
              requireMainOk &&
              mainNotRunOk &&
              descriptorOk &&
              exportsOk &&
              importsOk &&
              createRequireOk &&
              pluginOk &&
              bufferOk &&
              coreBuiltinsOk &&
              runtimeSurfaceOk &&
              fsPromisesOk &&
              contentProcessingOk &&
              configCacheOk &&
              configDiscoveryOk &&
              optionMergeOk &&
              workspacePackageOk &&
              workspaceFailureDiagnosticsOk &&
              modeOk;

            fs.writeFileSync(
              "./bin-string-" + (parsed.mode || process.env.npm_lifecycle_event || "bin") + ".json",
              JSON.stringify({
                ready,
                mode: parsed.mode || "",
                entry: path.basename(process.argv[1] || ""),
                output,
                pluginResolved,
                coreBuiltins: coreBuiltinsOk,
                runtimeSurface: runtimeSurfaceOk,
                fsPromises: fsPromisesOk,
                fsPromisesDetails: fsPromisesResult,
                contentProcessing: contentProcessingResult,
                configCache: configCacheResult,
                configDiscovery: configDiscoveryResult,
                optionMerge: optionMergeResult,
                workspacePackages: workspacePackageResult,
                workspaceFailureDiagnostics: workspaceFailureDiagnosticsResult
              }, null, 2),
              "utf8"
            );

            console.log(label + ".ready=" + ready);
            console.log(label + ".mode=" + parsed.mode);
            console.log(label + ".workspace_failure_env_redacted=" + workspaceFailureDiagnosticsResult.envRedacted);
            console.log(label + ".entry=" + expectedEntry + ":" + entryOk);
            console.log(label + ".cwd=" + expectedCwd + ":" + cwdOk);
            console.log(label + ".argv=" + (scriptArgOk && requestArgOk));
            console.log(label + ".env=" + envOk);
            console.log(label + ".lifecycle=" + lifecycleOk);
            console.log(label + ".package_json=" + (packageJsonOk && packageNameOk && packageVersionOk && initCwdOk && packageDirOk));
            console.log(label + ".bin_name=" + expectedBin + ":" + binOk);
            console.log(label + ".require_main=" + requireMainOk);
            console.log(label + ".main_not_run=" + mainNotRunOk);
            console.log(label + ".descriptor_launch_kind=" + (expectedLaunchKind || "") + ":" + descriptorOk);
            console.log(label + ".exports=" + exportsOk);
            console.log(label + ".imports=" + importsOk);
            console.log(label + ".create_require=" + createRequireOk);
            console.log(label + ".plugin=" + pluginOk);
            console.log(label + ".buffer=" + bufferOk);
            console.log(label + ".core_builtins=" + coreBuiltinsOk);
            console.log(label + ".url=" + urlOk);
            console.log(label + ".querystring=" + querystringOk);
            console.log(label + ".string_decoder=" + stringDecoderOk);
            console.log(label + ".assert=" + assertOk);
            console.log(label + ".punycode=" + punycodeOk);
            console.log(label + ".runtime_surface=" + runtimeSurfaceOk);
            console.log(label + ".process=" + processOk);
            console.log(label + ".os=" + osOk);
            console.log(label + ".path=" + pathOk);
            console.log(label + ".url_file=" + urlFileOk);
            console.log(label + ".fs_promises=" + fsPromisesOk);
            console.log(label + ".fs_promises_file_url=" + fsPromisesResult.fileUrl);
            console.log(label + ".fs_promises_atomic=" + fsPromisesResult.atomic);
            console.log(label + ".fs_promises_readdir=" + fsPromisesResult.readdir);
            console.log(label + ".content_processing=" + contentProcessingOk);
            console.log(label + ".content_aliases=" + contentProcessingResult.aliases);
            console.log(label + ".content_crypto=" + contentProcessingResult.crypto);
            console.log(label + ".content_zlib=" + contentProcessingResult.zlib);
            console.log(label + ".content_stream=" + contentProcessingResult.stream);
            console.log(label + ".content_file_stream=" + contentProcessingResult.fileStream);
            console.log(label + ".content_stream_promises=" + contentProcessingResult.streamPromises);
            console.log(label + ".content_stream_web=" + contentProcessingResult.streamWeb);
            console.log(label + ".content_stream_consumers=" + contentProcessingResult.streamConsumers);
            console.log(label + ".content_promisify=" + contentProcessingResult.promisify);
            console.log(label + ".content_manifest=" + contentProcessingResult.manifest);
            console.log(label + ".config_cache=" + configCacheOk);
            console.log(label + ".config_cache_config=" + configCacheResult.config);
            console.log(label + ".config_cache_tmpdir=" + configCacheResult.tmpdir);
            console.log(label + ".config_cache_relative_path=" + configCacheResult.relativePath);
            console.log(label + ".config_cache_miss_hit=" + configCacheResult.missHit);
            console.log(label + ".config_cache_manifest=" + configCacheResult.manifest);
            console.log(label + ".config_discovery=" + configDiscoveryOk);
            console.log(label + ".config_discovery_cwd=" + configDiscoveryResult.cwd);
            console.log(label + ".config_package_priority=" + configDiscoveryResult.packagePriority);
            console.log(label + ".config_workspace_fallback=" + configDiscoveryResult.workspaceFallback);
            console.log(label + ".config_mixed_plugins=" + configDiscoveryResult.mixedPlugins);
            console.log(label + ".config_missing_diagnostic=" + configDiscoveryResult.missingDiagnostic);
            console.log(label + ".option_merge=" + optionMergeOk);
            console.log(label + ".option_merge_config=" + optionMergeResult.config);
            console.log(label + ".option_merge_argv=" + optionMergeResult.argv);
            console.log(label + ".option_merge_list=" + optionMergeResult.list);
            console.log(label + ".option_merge_precedence=" + optionMergeResult.precedence);
            console.log(label + ".option_merge_env=" + optionMergeResult.env);
            console.log(label + ".option_merge_invalid=" + optionMergeResult.invalid);
            console.log(label + ".option_merge_mode_default=" + optionMergeResult.modeDefault);
            console.log(label + ".option_merge_diagnostics_redacted=" + optionMergeResult.diagnosticsRedacted);
            console.log(label + ".workspace_packages=" + workspacePackageOk);
            console.log(label + ".workspace_config_discovery=" + workspacePackageResult.configDiscovery);
            console.log(label + ".workspace_scoped_helper=" + workspacePackageResult.scopedHelper);
            console.log(label + ".workspace_self_reference=" + workspacePackageResult.selfReference);
            console.log(label + ".workspace_cache_partition=" + workspacePackageResult.cachePartition);
            console.log(label + ".workspace_failure_diagnostics=" + workspaceFailureDiagnosticsOk);
            console.log(label + ".workspace_missing_package=" + workspaceFailureDiagnosticsResult.missingPackage);
            console.log(label + ".workspace_missing_scoped_helper=" + workspaceFailureDiagnosticsResult.missingScopedHelper);
            console.log(label + ".workspace_missing_scoped_export=" + workspaceFailureDiagnosticsResult.missingScopedExport);
            console.log(label + ".workspace_self_missing_export=" + workspaceFailureDiagnosticsResult.selfMissingExport);
            console.log(label + ".workspace_relative_escape=" + workspaceFailureDiagnosticsResult.relativeEscape);
            console.log(label + ".workspace_fs_absolute_denied=" + workspaceFailureDiagnosticsResult.fsAbsoluteDenied);
            })().catch(function reportPackageBinStringCliError(error) {
              console.error("runner.bin_string.error=" + String(error && (error.stack || error.message) || error));
              throw error;
            });
        """.trimIndent()
    }

    fun writeNpmToolchainCorpus(
        projectRoot: File,
        packageDirRelativePath: String = "packages/toolchain-app",
        workingDirectoryRelativePath: String = "workspace/toolchain-run",
        scriptName: String = "toolchain",
        scriptLabel: String = "runner.toolchain.script",
        scriptRequestArg: String = "--from-request",
        envKey: String = "TOOLCHAIN_REQUEST_ENV",
        packageBinName: String = "toolchain-app",
        descriptorPathFromWorkingDirectory: String? = null,
        descriptorLaunchKind: String? = null,
        expectMainNotRun: Boolean = false,
    ): NpmToolchainCorpus {
        val packageDir = File(projectRoot, packageDirRelativePath).apply {
            check(mkdirs() || isDirectory) { "Failed to create npm toolchain package dir: $absolutePath" }
        }
        val workingDirectory = File(projectRoot, workingDirectoryRelativePath).apply {
            check(mkdirs() || isDirectory) { "Failed to create npm toolchain working dir: $absolutePath" }
        }
        val written = mutableListOf<File>()
        written += File(workingDirectory, ".keep").apply {
            writeText("toolchain-workspace\n", StandardCharsets.UTF_8)
        }
        written += writeRealNpmBatch2(packageDir)
        written += writeNpmPackage(
            packageDir,
            "@autojs6/toolchain-shared",
            """{"name":"@autojs6/toolchain-shared","version":"1.0.0","main":"index.js"}""",
            mapOf(
                "index.js" to """
                "use strict";
                const braceExpansion = require("brace-expansion-lite");
                const stringWidth = require("string-width");
                const stripAnsi = require("strip-ansi");

                exports.inspect = function inspect() {
                  const esc = String.fromCharCode(27);
                  const fullwidth = String.fromCodePoint(0x8868);
                  const emoji = String.fromCodePoint(0x1f600);
                  const colored = esc + "[31mred" + esc + "[39m";
                  return {
                    braces: braceExpansion("src/{index,test}.js").join("|"),
                    stripped: stripAnsi(colored),
                    width: stringWidth("a" + fullwidth + emoji)
                  };
                };

                exports.discover = function discover(configPath) {
                  const fs = require("fs");
                  const config = JSON.parse(fs.readFileSync(configPath, "utf8"));
                  const ignored = {};
                  (config.ignore || []).forEach(function markIgnored(pattern) {
                    braceExpansion(pattern).forEach(function mark(file) {
                      ignored[file] = true;
                    });
                  });
                  const files = [];
                  (config.include || []).forEach(function expand(pattern) {
                    braceExpansion(pattern).forEach(function add(file) {
                      if (!ignored[file]) {
                        files.push(file);
                      }
                    });
                  });
                  const pluginName = (config.plugins || [])[0] || "";
                  const pluginResult = pluginName ? require(pluginName).apply(config) : {};
                  return {
                    name: config.name || "",
                    files: files.join("|"),
                    ruleSemi: !!(config.rules && config.rules.semi),
                    pluginName: pluginResult.name || "",
                    pluginHelper: pluginResult.helper || "",
                    pluginOk: pluginResult.ok === true && pluginResult.includeCount === 2
                  };
                };
                """,
            ),
        )
        written += writeNpmPackage(
            packageDir,
            "@autojs6/toolchain-plugin",
            """
            {
              "name": "@autojs6/toolchain-plugin",
              "version": "1.0.0",
              "main": "index.js",
              "exports": {
                ".": "./index.js",
                "./helper": "./helper.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const helper = require("@autojs6/toolchain-plugin/helper");
                exports.apply = function apply(config) {
                  return {
                    name: "@autojs6/toolchain-plugin",
                    helper: helper.value,
                    includeCount: (config.include || []).length,
                    ok: helper.value === "toolchain-plugin-helper" && !!(config.rules && config.rules.semi)
                  };
                };
                """,
                "helper.js" to """
                "use strict";
                exports.value = "toolchain-plugin-helper";
                """,
            ),
        )
        written += writeNpmPackage(
            packageDir,
            "@autojs6/toolchain-cli",
            """{"name":"@autojs6/toolchain-cli","version":"1.0.0","bin":{"@autojs6/toolchain-cli":"./bin/cli.js"}}""",
            mapOf(
                "bin/cli.js" to npmToolchainCliSource(),
            ),
        )
        written += writeNpmPackage(
            packageDir,
            "toolchain-helper",
            """{"name":"toolchain-helper","version":"1.0.0","bin":{"toolchain-helper":"./cli.js"}}""",
            mapOf(
                "cli.js" to npmToolchainCliSource(),
            ),
        )
        val configFile = File(packageDir, "toolchain.config.json").apply {
            writeText(
                """
                {
                  "name": "autojs6-toolchain-config",
                  "include": [
                    "src/{index,test}.js",
                    "plugins/local.js"
                  ],
                  "ignore": [
                    "src/test.js"
                  ],
                  "plugins": [
                    "@autojs6/toolchain-plugin"
                  ],
                  "rules": {
                    "semi": true
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += configFile
        written += File(packageDir, "src/index.js").apply {
            parentFile?.mkdirs()
            writeText(
                """
                "use strict";
                module.exports = "toolchain-source-index";
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += File(packageDir, "src/test.js").apply {
            parentFile?.mkdirs()
            writeText(
                """
                "use strict";
                module.exports = "toolchain-source-test";
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += File(packageDir, "plugins/local.js").apply {
            parentFile?.mkdirs()
            writeText(
                """
                "use strict";
                module.exports = "toolchain-local-plugin";
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        val configPathFromWorkingDirectory = workingDirectory.toPath()
            .relativize(configFile.toPath())
            .toString()
            .replace(File.separatorChar, '/')

        val scriptCommand = buildList {
            add("node_modules/.bin/@autojs6/toolchain-cli")
            add("--label=$scriptLabel")
            add("--mode=build")
            add("--from-package-json-script")
            add("--pattern=src/{index,test}.js")
            add("--expect-config=$configPathFromWorkingDirectory")
            add("--expect-entry=$packageDirRelativePath/node_modules/@autojs6/toolchain-cli/bin/cli.js")
            add("--expect-cwd=$workingDirectoryRelativePath")
            add("--expect-init-cwd=$workingDirectoryRelativePath")
            add("--expect-package-dir=$packageDirRelativePath")
            add("--expect-package-name=autojs6-toolchain-app")
            add("--expect-package-version=1.0.0")
            add("--expect-script=$scriptName")
            add("--expect-bin=@autojs6/toolchain-cli")
            add("--expect-script-arg=--from-package-json-script")
            add("--expect-request-arg=$scriptRequestArg")
            add("--expect-env-key=$envKey")
            descriptorPathFromWorkingDirectory?.let { add("--expect-descriptor=$it") }
            descriptorLaunchKind?.let { add("--expect-launch-kind=$it") }
            if (expectMainNotRun) {
                add("--expect-main-not-run")
            }
        }.joinToString(" ")
        val helperCommand = buildList {
            add("node_modules/.bin/toolchain-helper")
            add("--label=runner.toolchain.helper")
            add("--mode=helper")
            add("--from-helper-script")
            add("--expect-config=$configPathFromWorkingDirectory")
            add("--expect-entry=$packageDirRelativePath/node_modules/toolchain-helper/cli.js")
            add("--expect-cwd=$workingDirectoryRelativePath")
            add("--expect-init-cwd=$workingDirectoryRelativePath")
            add("--expect-package-dir=$packageDirRelativePath")
            add("--expect-package-name=autojs6-toolchain-app")
            add("--expect-package-version=1.0.0")
            add("--expect-script=toolchain-helper")
            add("--expect-bin=toolchain-helper")
            add("--expect-script-arg=--from-helper-script")
            add("--expect-request-arg=$scriptRequestArg")
            add("--expect-env-key=$envKey")
        }.joinToString(" ")
        val packageJson = File(packageDir, "package.json").apply {
            writeText(
                """
                {
                  "name": "autojs6-toolchain-app",
                  "version": "1.0.0",
                  "bin": {
                    "$packageBinName": "./node_modules/@autojs6/toolchain-cli/bin/cli.js"
                  },
                  "scripts": {
                    "$scriptName": "$scriptCommand",
                    "toolchain-helper": "$helperCommand"
                  },
                  "dependencies": {
                    "@autojs6/toolchain-cli": "1.0.0",
                    "@autojs6/toolchain-plugin": "1.0.0",
                    "@autojs6/toolchain-shared": "1.0.0",
                    "brace-expansion-lite": "1.0.0",
                    "string-width": "7.2.0",
                    "strip-ansi": "7.1.0",
                    "supports-color-lite": "1.0.0",
                    "toolchain-helper": "1.0.0",
                    "yargs-parser-lite": "1.0.0"
                  }
                }
                """.trimIndent() + "\n",
                StandardCharsets.UTF_8,
            )
        }
        written += packageJson

        return NpmToolchainCorpus(
            packageDir = packageDir,
            workingDirectory = workingDirectory,
            packageJson = packageJson,
            scopedCliEntry = File(packageDir, "node_modules/@autojs6/toolchain-cli/bin/cli.js"),
            helperCliEntry = File(packageDir, "node_modules/toolchain-helper/cli.js"),
            configFile = configFile,
            configPathFromWorkingDirectory = configPathFromWorkingDirectory,
            files = written,
        )
    }

    private fun npmToolchainCliSource(): String {
        return """
            #!/usr/bin/env node
            "nodejs";
            const fs = require("fs");
            const path = require("path");
            const parse = require("yargs-parser-lite");
            const supportsColor = require("supports-color-lite");
            const shared = require("@autojs6/toolchain-shared");

            const parsed = parse(process.argv.slice(2));
            const label = String(parsed.label || "runner.toolchain");

            function option(name) {
              const value = parsed[name];
              return value === undefined || value === true ? "" : String(value);
            }

            function normalize(value) {
              return String(value || "")
                .replace(/\\/g, "/")
                .replace(/^\/data\/user\/0\//, "/data/data/");
            }

            function endsWithPath(value, suffix) {
              if (!suffix) {
                return true;
              }
              return normalize(value).endsWith("/" + suffix) || normalize(value).endsWith(suffix);
            }

            function hasArg(value) {
              return !value || process.argv.indexOf(value) >= 0;
            }

            function expectedEnvValueFor(key) {
              const explicit = option("expectEnvValue");
              if (explicit) {
                return explicit;
              }
              if (key === "TOOLCHAIN_REQUEST_ENV") {
                return "visible";
              }
              if (key === "TOOLCHAIN_BIN_ENV") {
                return "visible-bin";
              }
              if (key === "PACKAGED_SCRIPT_ENV") {
                return "packaged-script-secret";
              }
              if (key === "PACKAGED_BIN_ENV") {
                return "packaged-bin-secret";
              }
              return "";
            }

            const expectedEntry = option("expectEntry");
            const expectedCwd = option("expectCwd");
            const expectedInitCwd = option("expectInitCwd");
            const expectedPackageDir = option("expectPackageDir");
            const expectedPackageJsonDir = option("expectPackageJsonDir") || (option("expectScript") ? expectedPackageDir : "");
            const expectedPackageName = option("expectPackageName");
            const expectedPackageVersion = option("expectPackageVersion");
            const expectedScript = option("expectScript");
            const expectedBin = option("expectBin");
            const expectedBinPathToken = option("expectBinPathToken") || (expectedBin ? expectedBin.split("/").pop() : "");
            const expectedScriptArg = option("expectScriptArg");
            const expectedRequestArg = option("expectRequestArg");
            const expectedEnvKey = option("expectEnvKey");
            const expectedDescriptor = option("expectDescriptor");
            const expectedLaunchKind = option("expectLaunchKind");
            const expectedDescriptorBin = option("expectDescriptorBin");
            const expectedMode = option("mode");
            const expectedConfig = option("expectConfig");
            const inspection = shared.inspect();
            const discovery = expectedConfig ? shared.discover(expectedConfig) : {};
            const configOk = !expectedConfig || (
              discovery.name === "autojs6-toolchain-config" &&
              discovery.files === "src/index.js|plugins/local.js" &&
              discovery.ruleSemi === true
            );
            const pluginOk = !expectedConfig || (
              discovery.pluginName === "@autojs6/toolchain-plugin" &&
              discovery.pluginHelper === "toolchain-plugin-helper" &&
              discovery.pluginOk === true
            );
            let descriptorOk = !expectedDescriptor;
            if (expectedDescriptor) {
              const descriptor = JSON.parse(fs.readFileSync(expectedDescriptor, "utf8"));
              const descriptorScriptOk = expectedScript
                ? descriptor.scriptName === expectedScript
                : (expectedLaunchKind !== "package-bin" || !Object.prototype.hasOwnProperty.call(descriptor, "scriptName"));
              const descriptorBinOk = expectedDescriptorBin
                ? descriptor.binName === expectedDescriptorBin
                : (!expectedBin || !Object.prototype.hasOwnProperty.call(descriptor, "binName"));
              descriptorOk =
                (!expectedLaunchKind || descriptor.launchKind === expectedLaunchKind) &&
                (!expectedPackageDir || descriptor.packageDir === expectedPackageDir) &&
                (!expectedCwd || descriptor.workingDirectory === expectedCwd) &&
                descriptorScriptOk &&
                descriptorBinOk;
            }

            const entryOk = endsWithPath(process.argv[1], expectedEntry);
            const cwdOk = endsWithPath(process.cwd(), expectedCwd);
            const packageDirOk =
              !expectedPackageDir ||
              normalize(process.argv[1]).indexOf("/" + expectedPackageDir + "/") >= 0 ||
              normalize(process.argv[1]).indexOf(expectedPackageDir + "/") >= 0;
            const packageJsonOk =
              !expectedPackageJsonDir ||
              endsWithPath(process.env.npm_package_json, expectedPackageJsonDir + "/package.json");
            const packageNameOk = !expectedPackageName || process.env.npm_package_name === expectedPackageName;
            const packageVersionOk = !expectedPackageVersion || process.env.npm_package_version === expectedPackageVersion;
            const initCwdOk = !expectedInitCwd || endsWithPath(process.env.INIT_CWD, expectedInitCwd);
            const lifecycleOk = !expectedScript || process.env.npm_lifecycle_event === expectedScript;
            const binOk = !expectedBinPathToken || String(process.argv[1]).indexOf(expectedBinPathToken) >= 0;
            const scriptArgOk = hasArg(expectedScriptArg);
            const requestArgOk = hasArg(expectedRequestArg);
            const envOk = !expectedEnvKey || process.env[expectedEnvKey] === expectedEnvValueFor(expectedEnvKey);
            const requireMainOk = require.main === module && module.parent === null;
            const mainNotRunOk = !parsed.expectMainNotRun || !fs.existsSync("./unexpected-main.txt");
            const npmOk =
              inspection.braces === "src/index.js|src/test.js" &&
              inspection.stripped === "red" &&
              inspection.width === 5 &&
              supportsColor.optional === "true/true/true";
            const modeOk = !expectedMode || parsed.mode === expectedMode;
            const ready =
              entryOk &&
              cwdOk &&
              packageDirOk &&
              packageJsonOk &&
              packageNameOk &&
              packageVersionOk &&
              initCwdOk &&
              lifecycleOk &&
              binOk &&
              scriptArgOk &&
              requestArgOk &&
              envOk &&
              requireMainOk &&
              mainNotRunOk &&
              descriptorOk &&
              npmOk &&
              configOk &&
              pluginOk &&
              modeOk;

            fs.writeFileSync(
              "./toolchain-" + (process.env.npm_lifecycle_event || parsed.mode || "bin") + ".json",
              JSON.stringify({
                ready,
                mode: parsed.mode || "",
                entry: path.basename(process.argv[1] || ""),
                braces: inspection.braces,
                discovery: discovery.files || "",
                plugin: discovery.pluginName || "",
                width: inspection.width
              }, null, 2),
              "utf8"
            );

            console.log(label + ".ready=" + ready);
            console.log(label + ".entry=" + expectedEntry + ":" + entryOk);
            console.log(label + ".cwd=" + expectedCwd + ":" + cwdOk);
            console.log(label + ".argv=" + (scriptArgOk && requestArgOk));
            console.log(label + ".env=" + envOk);
            console.log(label + ".lifecycle=" + lifecycleOk);
            console.log(label + ".package_dir=" + (packageDirOk && packageJsonOk));
            console.log(label + ".working_directory=" + cwdOk);
            console.log(label + ".bin_name=" + expectedBin + ":" + binOk);
            console.log(label + ".require_main=" + requireMainOk);
            console.log(label + ".main_not_run=" + mainNotRunOk);
            console.log(label + ".descriptor_launch_kind=" + (expectedLaunchKind || "") + ":" + descriptorOk);
            console.log(label + ".toolchain.npm=" + npmOk);
            console.log(label + ".toolchain.config=" + configOk);
            console.log(label + ".toolchain.plugin=" + pluginOk);
            console.log(label + ".toolchain.discovery=" + (discovery.files || ""));
            console.log(label + ".toolchain.mode=" + parsed.mode);
        """.trimIndent()
    }


    fun writeRealNpmBatch1(projectDir: File) {
        writeNpmPackage(
            projectDir,
            "is-number",
            """{"name":"is-number","version":"7.0.0","main":"index.js","exports":"./index.js"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function isNumber(value) {
                  if (typeof value === "number") {
                    return Number.isFinite(value);
                  }
                  if (typeof value === "string" && value.trim() !== "") {
                    return Number.isFinite(Number(value));
                  }
                  return false;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "is-odd",
            """{"name":"is-odd","version":"3.0.1"}""",
            mapOf(
                "index.js" to """
                "use strict";
                const isNumber = require("is-number");
                module.exports = function isOdd(value) {
                  if (!isNumber(value)) {
                    return false;
                  }
                  return Math.abs(Number(value)) % 2 === 1;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "kind-of",
            """{"name":"kind-of","version":"6.0.3"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function kindOf(value) {
                  if (value === null) {
                    return "null";
                  }
                  if (Buffer.isBuffer(value)) {
                    return "buffer";
                  }
                  if (Array.isArray(value)) {
                    return "array";
                  }
                  return typeof value;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "wrappy",
            """{"name":"wrappy","version":"1.0.2","main":"wrappy.js"}""",
            mapOf(
                "wrappy.js" to """
                "use strict";
                module.exports = function wrappy(fn) {
                  return function wrapped() {
                    return fn.apply(this, arguments);
                  };
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "once",
            """{"name":"once","version":"1.4.0","main":"once.js"}""",
            mapOf(
                "once.js" to """
                "use strict";
                const wrappy = require("wrappy");
                module.exports = wrappy(once);
                function once(fn) {
                  let called = false;
                  let value;
                  return function onceWrapper() {
                    if (called) {
                      return value;
                    }
                    called = true;
                    value = fn.apply(this, arguments);
                    return value;
                  };
                }
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "ms",
            """{"name":"ms","version":"2.1.3"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function ms(value) {
                  if (typeof value === "number") {
                    return value + "ms";
                  }
                  const match = /^(\d+)s$/.exec(String(value));
                  return match ? Number(match[1]) * 1000 : undefined;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "debug",
            """{"name":"debug","version":"4.3.7","main":"src/index.js"}""",
            mapOf(
                "src/index.js" to """
                "use strict";
                module.exports = require("./common");
                """.trimIndent(),
                "src/common.js" to """
                "use strict";
                const util = require("util");
                const ms = require("ms");
                let optionalMissing = false;
                try {
                  require("supports-color");
                } catch (error) {
                  optionalMissing = !!error;
                }
                function enabled(namespace) {
                  const debugEnv = process.env.DEBUG || "";
                  return debugEnv === "*" || debugEnv.split(",").some(pattern => {
                    return pattern.endsWith("*") ? namespace.startsWith(pattern.slice(0, -1)) : namespace === pattern;
                  });
                }
                module.exports = function createDebug(namespace) {
                  function debug() {
                    if (!enabled(namespace)) {
                      return false;
                    }
                    return namespace + " " + util.format.apply(util, arguments) + " +" + ms(2) + " optional=" + optionalMissing;
                  }
                  debug.enabled = enabled(namespace);
                  return debug;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            File(projectDir, "node_modules/debug"),
            "ms",
            """{"name":"ms","version":"2.1.3"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function ms(value) {
                  if (typeof value === "number") {
                    return value + "ms";
                  }
                  return value;
                };
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "picocolors",
            """{"name":"picocolors","version":"1.1.1","exports":{"require":"./picocolors.js","default":"./picocolors.js"}}""",
            mapOf(
                "picocolors.js" to """
                "use strict";
                exports.red = string => "\\x1b[31m" + string + "\\x1b[39m";
                """.trimIndent(),
            ),
        )
        writeNpmPackage(
            projectDir,
            "safe-buffer",
            """{"name":"safe-buffer","version":"5.2.1"}""",
            mapOf(
                    "index.js" to """
                    "use strict";
                    Object.defineProperty(exports, "__esModule", { value: true });
                    exports.Buffer = Buffer;
                """.trimIndent(),
            ),
        )
    }

    fun writeRealNpmBatch2(projectDir: File): List<File> {
        val written = mutableListOf<File>()
        written += writeNpmPackage(
            projectDir,
            "camelcase",
            """
            {
              "name": "camelcase",
              "version": "8.0.0",
              "exports": {
                ".": {
                  "require": "./index.js",
                  "default": "./index.js"
                }
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                function camelcase(value) {
                  return String(value)
                    .replace(/^[_.\-\s]+/, "")
                    .toLowerCase()
                    .replace(/[_.\-\s]+([a-z0-9])/g, function upper(_, character) {
                      return character.toUpperCase();
                    });
                }
                module.exports = camelcase;
                module.exports.default = camelcase;
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "decamelize",
            """{"name":"decamelize","version":"6.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function decamelize(value, separator) {
                  const sep = separator || "_";
                  return String(value)
                    .replace(/([a-z\d])([A-Z])/g, "${'$'}1" + sep + "${'$'}2")
                    .replace(/([A-Z]+)([A-Z][a-z\d]+)/g, "${'$'}1" + sep + "${'$'}2")
                    .toLowerCase();
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "ansi-regex",
            """
            {
              "name": "ansi-regex",
              "version": "6.1.0",
              "exports": {
                ".": "./index.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function ansiRegex() {
                  return new RegExp(String.fromCharCode(27) + "\\[[0-?]*[ -/]*[@-~]", "g");
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "strip-ansi",
            """
            {
              "name": "strip-ansi",
              "version": "7.1.0",
              "exports": {
                ".": {
                  "require": "./index.js",
                  "default": "./index.js"
                }
              },
              "dependencies": {
                "ansi-regex": "^6.0.1"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const ansiRegex = require("ansi-regex");
                module.exports = function stripAnsi(value) {
                  return String(value).replace(ansiRegex(), "");
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "is-fullwidth-code-point",
            """{"name":"is-fullwidth-code-point","version":"5.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function isFullwidthCodePoint(codePoint) {
                  return Number(codePoint) >= 0x1100 && (
                    codePoint <= 0x115f ||
                    codePoint === 0x2329 ||
                    codePoint === 0x232a ||
                    (codePoint >= 0x2e80 && codePoint <= 0xa4cf)
                  );
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "east-asian-width-lite",
            """{"name":"east-asian-width-lite","version":"1.0.0","main":"source/index.js"}""",
            mapOf(
                "source/index.js" to """
                "use strict";
                const isFullwidthCodePoint = require("is-fullwidth-code-point");
                exports.charWidth = function charWidth(character) {
                  const codePoint = character.codePointAt(0);
                  if (codePoint >= 0x1f300 && codePoint <= 0x1faff) return 2;
                  return isFullwidthCodePoint(codePoint) ? 2 : 1;
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "emoji-regex-lite",
            """
            {
              "name": "emoji-regex-lite",
              "version": "1.0.0",
              "exports": {
                ".": "./index.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function emojiRegexLite() {
                  return new RegExp("[" + String.fromCharCode(0xd83d) + "][" + String.fromCharCode(0xde00) + "-" + String.fromCharCode(0xde4f) + "]");
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "string-width",
            """
            {
              "name": "string-width",
              "version": "7.2.0",
              "dependencies": {
                "strip-ansi": "^7.1.0",
                "east-asian-width-lite": "1.0.0",
                "emoji-regex-lite": "1.0.0"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const stripAnsi = require("strip-ansi");
                const width = require("east-asian-width-lite");
                module.exports = function stringWidth(value) {
                  return Array.from(stripAnsi(String(value))).reduce(function sum(total, character) {
                    return total + width.charWidth(character);
                  }, 0);
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "has-flag",
            """{"name":"has-flag","version":"5.0.1"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function hasFlag(flag, argv) {
                  const prefix = flag.length === 1 ? "-" : "--";
                  return (argv || process.argv).indexOf(prefix + flag) >= 0;
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "supports-color-lite",
            """
            {
              "name": "supports-color-lite",
              "version": "1.0.0",
              "exports": {
                ".": {
                  "require": "./index.js",
                  "node": "./node.js",
                  "default": "./index.js"
                }
              },
              "dependencies": {
                "has-flag": "^5.0.1"
              },
              "optionalDependencies": {
                "supports-color": "*",
                "fsevents": "*",
                "node-gyp-build": "*"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const hasFlag = require("has-flag");
                function optional(name) {
                  try {
                    require(name);
                    return "loaded";
                  } catch (error) {
                    return !!(error && (
                      error.code === "MODULE_NOT_FOUND" ||
                      error.code === "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" ||
                      String(error.message || "").includes(name)
                    ));
                  }
                }
                module.exports = {
                  stdout: hasFlag("color"),
                  optional: [
                    optional("supports-color"),
                    optional("fsevents"),
                    optional("node-gyp-build")
                  ].join("/")
                };
                """,
                "node.js" to """module.exports = require("./index");""",
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "yargs-parser-lite",
            """
            {
              "name": "yargs-parser-lite",
              "version": "1.0.0",
              "exports": {
                ".": {
                  "require": "./build/index.cjs",
                  "default": "./build/index.cjs"
                }
              },
              "dependencies": {
                "camelcase": "^8.0.0"
              }
            }
            """,
            mapOf(
                "build/index.cjs" to """
                "use strict";
                const camelcase = require("camelcase");
                module.exports = function parse(args) {
                  const result = { _: [] };
                  for (let i = 0; i < args.length; i += 1) {
                    const arg = args[i];
                    if (arg.indexOf("--") === 0) {
                      const body = arg.slice(2);
                      const eq = body.indexOf("=");
                      const rawKey = eq >= 0 ? body.slice(0, eq) : body;
                      const key = camelcase(rawKey);
                      result[key] = eq >= 0 ? body.slice(eq + 1) : (
                        args[i + 1] && args[i + 1].indexOf("-") !== 0 ? args[++i] : true
                      );
                    } else {
                      result._.push(arg);
                    }
                  }
                  return result;
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "escape-string-regexp",
            """{"name":"escape-string-regexp","version":"5.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function escapeStringRegexp(value) {
                  return String(value).replace(/[|\\{}()[\]^$+*?.]/g, "\\$&").replace(/-/g, "\\x2d");
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "balanced-match",
            """{"name":"balanced-match","version":"2.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function balanced(a, b, value) {
                  const source = String(value);
                  const start = source.indexOf(a);
                  const end = source.indexOf(b, start + a.length);
                  if (start < 0 || end < 0) return null;
                  return {
                    start,
                    end,
                    pre: source.slice(0, start),
                    body: source.slice(start + a.length, end),
                    post: source.slice(end + b.length)
                  };
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "concat-map",
            """{"name":"concat-map","version":"0.0.1"}""",
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = function concatMap(array, mapper) {
                  return Array.prototype.concat.apply([], array.map(mapper));
                };
                """,
            ),
        )
        written += writeNpmPackage(
            projectDir,
            "brace-expansion-lite",
            """
            {
              "name": "brace-expansion-lite",
              "version": "1.0.0",
              "dependencies": {
                "balanced-match": "^2.0.0",
                "concat-map": "0.0.1"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const balanced = require("balanced-match");
                const concatMap = require("concat-map");
                module.exports = function expand(pattern) {
                  const match = balanced("{", "}", pattern);
                  if (!match) return [pattern];
                  return concatMap(match.body.split(","), function replace(part) {
                    return [match.pre + part + match.post];
                  });
                };
                """,
            ),
        )
        return written
    }

    fun writeCommonJsResolverCorpus(projectDir: File) {
        writeNpmPackage(
            projectDir,
            "resolver-edge-pkg",
            """
            {
              "name": "resolver-edge-pkg",
              "version": "1.0.0",
              "module": "./esm.mjs",
              "browser": "./browser.js",
              "types": "./index.d.ts",
              "exports": {
                ".": "./index.js",
                "./feature/*": "./features/*.js",
                "./condition": {
                  "browser": "./condition/browser.js",
                  "import": "./condition/import.mjs",
                  "require": "./condition/require.js",
                  "node": "./condition/node.js",
                  "default": "./condition/default.js"
                },
                "./private/*": null,
                "./esm": "./esm.mjs"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                const selfFeature = require("resolver-edge-pkg/feature/a");
                exports.root = "exports-root";
                exports.self = "self+" + selfFeature.value;
                """,
                "features/a.js" to """
                "use strict";
                exports.value = "feature-a";
                """,
                "condition/require.js" to """
                "use strict";
                exports.mode = "condition-require";
                """,
                "condition/node.js" to """
                "use strict";
                exports.mode = "condition-node";
                """,
                "condition/default.js" to """
                "use strict";
                exports.mode = "condition-default";
                """,
                "private/secret.js" to """
                "use strict";
                exports.value = "should-not-load";
                """,
                "esm.mjs" to """
                export default "should-not-load";
                """,
            ),
        )
        writeNpmPackage(
            projectDir,
            "imports-edge-pkg",
            """
            {
              "name": "imports-edge-pkg",
              "version": "1.0.0",
              "main": "index.js",
              "module": "./esm.mjs",
              "browser": "./browser.js",
              "types": "./index.d.ts",
              "imports": {
                "#alias": "./lib/alias.js",
                "#wild/*": "./lib/wild/*.js",
                "#condition": {
                  "browser": "./lib/condition/browser.js",
                  "import": "./lib/condition/import.mjs",
                  "require": "./lib/condition/require.js",
                  "node": "./lib/condition/node.js",
                  "default": "./lib/condition/default.js"
                },
                "#blocked/*": null,
                "#esm": "./esm.mjs"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                function errorCodeOf(fn) {
                  try {
                    fn();
                    return "ok";
                  } catch (error) {
                    return error && error.code || String(error && error.message || error);
                  }
                }
                const alias = require("#alias");
                const wild = require("#wild/a");
                const condition = require("#condition");
                exports.value = alias.value + "+" + wild.value + "+" + condition.value;
                exports.resolve = require.resolve("#wild/a").endsWith("node_modules/imports-edge-pkg/lib/wild/a.js");
                exports.blocked = errorCodeOf(() => require("#blocked/secret"));
                exports.esm = errorCodeOf(() => require("#esm"));
                """,
                "lib/alias.js" to """
                "use strict";
                exports.value = "import-alias";
                """,
                "lib/wild/a.js" to """
                "use strict";
                exports.value = "import-wild-a";
                """,
                "lib/condition/require.js" to """
                "use strict";
                exports.value = "import-require";
                """,
                "lib/condition/node.js" to """
                "use strict";
                exports.value = "import-node";
                """,
                "lib/condition/default.js" to """
                "use strict";
                exports.value = "import-default";
                """,
                "lib/private.js" to """
                "use strict";
                exports.value = "should-not-load";
                """,
                "esm.mjs" to """
                export default "should-not-load";
                """,
            ),
        )
        writeNpmPackage(
            projectDir,
            "@scope/resolver-self-pkg",
            """
            {
              "name": "@scope/resolver-self-pkg",
              "version": "1.0.0",
              "exports": {
                ".": "./index.js",
                "./feature": "./feature.js"
              }
            }
            """,
            mapOf(
                "index.js" to """
                "use strict";
                module.exports = "scoped+" + require("@scope/resolver-self-pkg/feature").value;
                """,
                "feature.js" to """
                "use strict";
                const crypto = require("node:crypto");
                exports.value = "self-feature";
                exports.cachePayload = function cachePayload(name) {
                  const text = "cache-entry:".repeat(128);
                  return {
                    name,
                    scoped: exports.value,
                    scopedHash: crypto.createHash("sha1").update(name + ":" + text).digest("hex"),
                    text
                  };
                };
                """,
            ),
        )
        writeNpmPackage(
            projectDir,
            "fallback-main-pkg",
            """{"name":"fallback-main-pkg","version":"1.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                exports.value = "fallback-index";
                """,
            ),
        )
    }

    fun writeDependencyResolutionCorpus(projectDir: File) {
        listOf("path", "fs", "util").forEach { name ->
            writeNpmPackage(
                projectDir,
                name,
                """{"name":"$name","version":"0.0.0-shadow"}""",
                mapOf(
                    "index.js" to """
                    "use strict";
                    module.exports = { __autojs6Shadow: true, name: "$name-shadow" };
                    """,
                ),
            )
        }
        writeNpmPackage(
            projectDir,
            "resolution-shared",
            """{"name":"resolution-shared","version":"1.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                exports.value = "root-shared";
                """,
                "subpath.js" to """
                "use strict";
                exports.value = "root-subpath";
                """,
            ),
        )
        writeNpmPackage(
            projectDir,
            "resolution-parent",
            """{"name":"resolution-parent","version":"1.0.0"}""",
            mapOf(
                "index.js" to """
                "use strict";
                const fs = require("fs");
                const fsp = require("fs/promises");
                const path = require("path");
                const url = require("url");
                const crypto = require("node:crypto");
                const shared = require("resolution-shared");
                const subpath = require("resolution-shared/subpath");
                exports.shared = shared.value;
                exports.sharedModule = shared;
                exports.subpath = subpath.value;
                exports.cache = shared === require("resolution-shared");
                exports.writeCache = function writeCache(root, payload) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  fs.mkdirSync(cacheDir, { recursive: true });
                  const encoded = shared.encode(payload);
                  const hash = crypto.createHash("sha256").update(encoded).digest("hex");
                  const file = hash.slice(0, 12) + ".cache";
                  const target = path.join(cacheDir, file);
                  fs.writeFileSync(target, encoded);
                  const cached = fs.readFileSync(target);
                  const decoded = shared.decode(cached);
                  const stat = fs.statSync(target);
                  return {
                    ok:
                      decoded.checksumOk &&
                      decoded.value.name === payload.name &&
                      decoded.value.scoped === payload.scoped &&
                      decoded.value.scopedHash === payload.scopedHash,
                    file,
                    hash,
                    statSize: stat.size,
                    compressed: encoded.length < Buffer.from(JSON.stringify(payload), "utf8").length,
                    nested: decoded.nested,
                    nestedAlias: shared.alias,
                    nestedResolve: require.resolve("resolution-shared")
                  };
                };
                exports.readCacheBlob = async function readCacheBlob(root, file, payload) {
                  const target = path.join(root, "packaged-npm-cache", file);
                  const blob = await fs.openAsBlob(target, { type: "application/gzip" });
                  const buffer = Buffer.from(await blob.arrayBuffer());
                  const decoded = shared.decode(buffer);
                  return {
                    ok:
                      blob.type === "application/gzip" &&
                      blob.size === buffer.length &&
                      decoded.checksumOk &&
                      decoded.value.name === payload.name &&
                      decoded.value.scoped === payload.scoped &&
                      decoded.value.scopedHash === payload.scopedHash &&
                      decoded.nested === shared.value,
                    type: blob.type,
                    size: blob.size,
                    nested: decoded.nested
                  };
                };
                exports.streamCacheFile = async function streamCacheFile(root, file) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  const target = path.join(cacheDir, file);
                  const slice = await new Promise((resolve, reject) => {
                    const chunks = [];
                    const readable = fs.createReadStream(target, {
                      start: 0,
                      end: 7,
                      highWaterMark: 2,
                      emitClose: false
                    });
                    readable.on("data", chunk => { chunks.push(chunk); });
                    readable.on("error", reject);
                    readable.on("end", () => resolve(Buffer.concat(chunks)));
                  });
                  const patchTarget = path.join(cacheDir, "stream-modern.txt");
                  fs.writeFileSync(patchTarget, "0123456789", "utf8");
                  await new Promise((resolve, reject) => {
                    const writable = fs.createWriteStream(patchTarget, {
                      encoding: "utf8",
                      flags: "r+",
                      start: 6,
                      highWaterMark: 1,
                      emitClose: false,
                      flush: true
                    });
                    writable.on("error", reject);
                    writable.on("finish", resolve);
                    writable.end("XYZ");
                  });
                  const abortCode = await new Promise((resolve) => {
                    const controller = new AbortController();
                    controller.abort();
                    const readable = fs.createReadStream(target, {
                      signal: controller.signal
                    });
                    readable.on("error", error => {
                      resolve(String(error && error.code || ""));
                    });
                    readable.resume();
                  });
                  return {
                    sliceLength: slice.length,
                    patched: fs.readFileSync(patchTarget, "utf8"),
                    abortCode,
                    streamTypes: [typeof fs.createReadStream, typeof fs.createWriteStream].join("/")
                  };
                };
                exports.copyCacheTree = async function copyCacheTree(root, file) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  const copyDir = path.join(root, "packaged-npm-cache-copy");
                  await fsp.rm(copyDir, { recursive: true, force: true });
                  fs.writeFileSync(path.join(cacheDir, "skip.tmp"), "skip", "utf8");
                  const linkPath = path.join(cacheDir, "latest.cache.link");
                  try {
                    fs.unlinkSync(linkPath);
                  } catch (error) {
                    if (!error || error.code !== "ENOENT") {
                      throw error;
                    }
                  }
                  fs.symlinkSync(file, linkPath);
                  await fsp.cp(cacheDir, copyDir, {
                    recursive: true,
                    verbatimSymlinks: true,
                    filter(source) {
                      return !source.endsWith(".tmp");
                    }
                  });
                  const copiedFile = path.join(copyDir, file);
                  const copiedLink = path.join(copyDir, "latest.cache.link");
                  return {
                    copied: shared.decode(fs.readFileSync(copiedFile)).checksumOk,
                    skipped: !fs.existsSync(path.join(copyDir, "skip.tmp")),
                    link: fs.lstatSync(copiedLink).isSymbolicLink(),
                    linkTarget: fs.readlinkSync(copiedLink),
                    linkTextOk: fs.readFileSync(copiedLink).equals(fs.readFileSync(copiedFile))
                  };
                };
                exports.watchCacheConfig = async function watchCacheConfig(root) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  fs.mkdirSync(cacheDir, { recursive: true });
                  const watchTarget = path.join(cacheDir, "watch-config.json");
                  const promisesWatchTarget = path.join(cacheDir, "watch-config-promises.json");
                  fs.writeFileSync(watchTarget, JSON.stringify({ value: 1 }), "utf8");
                  fs.writeFileSync(promisesWatchTarget, JSON.stringify({ value: 1 }), "utf8");
                  const watchOk = await new Promise((resolve, reject) => {
                    let watcher;
                    const timeout = setTimeout(() => {
                      if (watcher) watcher.close();
                      reject(new Error("cache fs.watch timeout"));
                    }, 2000);
                    watcher = fs.watch(
                      url.pathToFileURL(path.resolve(watchTarget)),
                      { persistent: true },
                      (eventType) => {
                        if (eventType !== "change" && eventType !== "rename") {
                          return;
                        }
                        clearTimeout(timeout);
                        watcher.close();
                        resolve(true);
                      }
                    );
                    setTimeout(() => fs.writeFileSync(watchTarget, JSON.stringify({ value: 2 }), "utf8"), 120);
                  });
                  const promisesWatchOk = await new Promise((resolve, reject) => {
                    let iterator;
                    let settled = false;
                    const finish = (error, value) => {
                      if (settled) {
                        return;
                      }
                      settled = true;
                      clearTimeout(timeout);
                      if (iterator && typeof iterator.return === "function") {
                        Promise.resolve(iterator.return()).catch(() => {});
                      }
                      if (error) {
                        reject(error);
                      } else {
                        resolve(value);
                      }
                    };
                    const timeout = setTimeout(() => {
                      finish(new Error("cache fs.promises.watch timeout"));
                    }, 2000);
                    iterator = fsp.watch(Buffer.from(promisesWatchTarget), { persistent: true });
                    iterator.next().then((result) => {
                      const event = result && result.value;
                      finish(null, !!(
                        result &&
                        result.done === false &&
                        event &&
                        (event.eventType === "change" || event.eventType === "rename")
                      ));
                    }, finish);
                    setTimeout(() => fs.writeFileSync(promisesWatchTarget, JSON.stringify({ value: 2 }), "utf8"), 120);
                  });
                  return {
                    watch: watchOk,
                    promises: promisesWatchOk,
                    values:
                      JSON.parse(fs.readFileSync(watchTarget, "utf8")).value + "/" +
                      JSON.parse(fs.readFileSync(promisesWatchTarget, "utf8")).value
                  };
                };
                exports.scanCacheTree = async function scanCacheTree(root, file) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  const nestedDir = path.join(cacheDir, "nested");
                  fs.mkdirSync(nestedDir, { recursive: true });
                  fs.writeFileSync(path.join(nestedDir, "info.json"), JSON.stringify({ file }), "utf8");
                  const normalize = (value) => String(value).replace(/\\/g, "/");
                  const recursiveNames = (await fsp.readdir(cacheDir, { recursive: true }))
                    .map(normalize)
                    .sort();
                  const dir = await fsp.opendir(Buffer.from(cacheDir), { recursive: true, bufferSize: 1 });
                  const direntNames = [];
                  for await (const entry of dir) {
                    const full = path.join(String(entry.parentPath), String(entry.name));
                    direntNames.push(normalize(path.relative(cacheDir, full) || String(entry.name)));
                  }
                  direntNames.sort();
                  return {
                    readdir:
                      recursiveNames.includes(file) &&
                      recursiveNames.includes("nested") &&
                      recursiveNames.includes("nested/info.json"),
                    opendir:
                      direntNames.includes(file) &&
                      direntNames.includes("nested") &&
                      direntNames.includes("nested/info.json"),
                    count: recursiveNames.length + "/" + direntNames.length
                  };
                };
                exports.useTempCacheWorkspace = async function useTempCacheWorkspace(root, file) {
                  const cacheDir = path.join(root, "packaged-npm-cache");
                  const sourceFile = path.join(cacheDir, file);
                  const gone = (target) => {
                    try {
                      fs.lstatSync(target);
                      return false;
                    } catch (error) {
                      return error && error.code === "ENOENT";
                    }
                  };
                  const syncDisposable = fs.mkdtempDisposableSync(Buffer.from(path.join(cacheDir, "sync-work-")));
                  const syncPath = syncDisposable.path;
                  fs.mkdirSync(path.join(syncPath, "nested"), { recursive: true });
                  fs.writeFileSync(path.join(syncPath, "nested", "meta.json"), JSON.stringify({ file }), "utf8");
                  fs.symlinkSync(path.relative(syncPath, sourceFile), path.join(syncPath, "latest.cache.link"));
                  const syncTextOk = fs.readFileSync(path.join(syncPath, "latest.cache.link")).equals(fs.readFileSync(sourceFile));
                  syncDisposable.remove();
                  syncDisposable.remove();
                  const asyncDisposable = await fsp.mkdtempDisposable(path.join(cacheDir, "async-work-"));
                  const asyncPath = asyncDisposable.path;
                  await fsp.mkdir(path.join(asyncPath, "nested"), { recursive: true });
                  await fsp.writeFile(path.join(asyncPath, "nested", "meta.json"), JSON.stringify({ file, async: true }), "utf8");
                  await asyncDisposable[Symbol.asyncDispose]();
                  await asyncDisposable.remove();
                  return {
                    sync: syncTextOk && gone(syncPath),
                    promise: gone(asyncPath),
                    source: fs.existsSync(sourceFile) && shared.decode(fs.readFileSync(sourceFile)).checksumOk
                  };
                };
                exports.rejectOpenAsBlob = async function rejectOpenAsBlob(pathValue) {
                  try {
                    await fs.openAsBlob(pathValue);
                    return false;
                  } catch (error) {
                    return error && error.code === "EPERM" &&
                      String(error.message).includes("escapes working directory");
                  }
                };
                exports.listCacheFiles = async function listCacheFiles(root) {
                  const matches = [];
                  for await (const entry of fsp.glob("packaged-npm-cache/**/*.cache", {
                    cwd: root,
                    exclude: (entry) => String(entry).endsWith(".tmp")
                  })) {
                    matches.push(String(entry).replace(/\\/g, "/"));
                  }
                  return matches.sort();
                };
                exports.rejectCacheGlob = async function rejectCacheGlob(root) {
                  try {
                    for await (const _ of fsp.glob("**/*.cache", { cwd: root })) {
                    }
                    return false;
                  } catch (error) {
                    return error && error.code === "EPERM" &&
                      String(error.message).includes("escapes working directory");
                  }
                };
                """,
            ),
        )
        writeNpmPackage(
            File(projectDir, "node_modules/resolution-parent"),
            "resolution-shared",
            """{"name":"resolution-shared","version":"1.0.0-nested"}""",
            mapOf(
                "index.js" to """
                "use strict";
                const zlib = require("node:zlib");
                const crypto = require("crypto");
                exports.alias = zlib === require("zlib") && crypto === require("node:crypto");
                exports.value = "nested-shared";
                exports.encode = function encode(value) {
                  const payload = JSON.stringify(value);
                  const checksum = crypto.createHash("sha1").update(payload).digest("hex");
                  return zlib.gzipSync(Buffer.from(JSON.stringify({ checksum, payload }), "utf8"));
                };
                exports.decode = function decode(buffer) {
                  const envelope = JSON.parse(zlib.gunzipSync(buffer).toString("utf8"));
                  const checksum = crypto.createHash("sha1").update(envelope.payload).digest("hex");
                  return {
                    value: JSON.parse(envelope.payload),
                    checksumOk: checksum === envelope.checksum,
                    nested: exports.value
                  };
                };
                """,
                "subpath.js" to """
                "use strict";
                exports.value = "nested-subpath";
                """,
            ),
        )
    }

    fun writeNpmPackage(projectRoot: File, name: String, packageJson: String, files: Map<String, String>): List<File> {
        val packageDir = File(projectRoot, "node_modules/${name.replace('/', File.separatorChar)}").apply {
            check(mkdirs() || isDirectory) { "Failed to create package dir: $absolutePath" }
        }
        val written = mutableListOf<File>()
        written += File(packageDir, "package.json").apply {
            writeText(packageJson + "\n", StandardCharsets.UTF_8)
        }
        files.forEach { (relativePath, content) ->
            require(!relativePath.startsWith("/") && !relativePath.contains("..")) {
                "Unsafe npm fixture path: $relativePath"
            }
            written += File(packageDir, relativePath).apply {
                parentFile?.mkdirs()
                writeText(content.trimIndent() + "\n", StandardCharsets.UTF_8)
            }
        }
        return written
    }
}
