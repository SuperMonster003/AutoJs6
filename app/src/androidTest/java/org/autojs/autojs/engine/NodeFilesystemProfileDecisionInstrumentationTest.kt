package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class NodeFilesystemProfileDecisionInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun filesystemProfileKeepsSafeScopeAndRejectsUnpromotedRoots() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-32 filesystem profile decision regression")

        val workDir = File(context.cacheDir, "node-filesystem-profile-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create filesystem profile work dir: $absolutePath" }
        }
        val outsideDir = File(context.cacheDir, "node-filesystem-profile-outside-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create filesystem profile outside dir: $absolutePath" }
        }
        val outsideLink = File(workDir, "outside-link")

        try {
            File(workDir, "safe/ok.txt").apply {
                parentFile?.mkdirs()
                writeText("ok", StandardCharsets.UTF_8)
            }
            File(outsideDir, "secret.txt").writeText("secret", StandardCharsets.UTF_8)
            Files.createSymbolicLink(outsideLink.toPath(), outsideDir.toPath())

            val outsideSecret = File(outsideDir, "secret.txt").absolutePath
            val outsideRelative = "../${outsideDir.name}/secret.txt"
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const fs = require("fs");
                      const fsp = require("fs/promises");
                      const profile = require("autojs6:profile");

                      function normalizePath(value) {
                        return String(value).replace(/\\/g, "/").replace(/\/+$/, "");
                      }
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.legacyCode || error.name || ""));
                      }
                      function detailOf(error) {
                        return [
                          error && error.code,
                          error && error.autojs6Code,
                          error && error.legacyCode
                        ].filter(Boolean).join("/");
                      }
                      function expectDenied(label, action, expectedCode) {
                        try {
                          action();
                        } catch (error) {
                          const actual = codeOf(error);
                          if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                            throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                          }
                          return label + ":" + actual;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }
                      async function expectDeniedAsync(label, action, expectedCode) {
                        try {
                          await action();
                        } catch (error) {
                          const actual = codeOf(error);
                          if (actual !== expectedCode && error.code !== expectedCode && error.legacyCode !== expectedCode) {
                            throw new Error(label + " expected " + expectedCode + " but got " + detailOf(error));
                          }
                          return label + ":" + actual;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      const safeText = fs.readFileSync("safe/ok.txt", "utf8");
                      const root = normalizePath(fs.realpathSync("."));
                      const safeReal = normalizePath(fs.realpathSync("safe/ok.txt"));
                      if (safeText !== "ok" || safeReal.indexOf(root + "/") !== 0) {
                        throw new Error("safe scoped read/realpath failed: " + safeText + " / " + safeReal + " / " + root);
                      }

                      const fsProfile = profile.filesystemProfile || {};
                      console.log("p13fs.profile=" + [
                        profile.scopedFs === true,
                        profile.featureFlags.fs_streams.enabled === true,
                        fsProfile.mode || "absent",
                        fsProfile.safeProfileScoped === true,
                        Array.isArray(fsProfile.additionalRoots) && fsProfile.additionalRoots.length === 0,
                        fsProfile.userAuthorizedRoots === false,
                        fsProfile.safRoots === false,
                        fsProfile.appPrivateRoots === false,
                        fsProfile.androidSharedStorage === false,
                        Object.isFrozen(fsProfile)
                      ].join("|"));

                      const denials = [
                        expectDenied("absoluteOutside", () => fs.readFileSync(${jsStringLiteral(outsideSecret)}, "utf8"), "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"),
                        expectDenied("escapeParent", () => fs.readFileSync(${jsStringLiteral(outsideRelative)}, "utf8"), "${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"),
                        expectDenied("sdcard", () => fs.readFileSync("/sdcard/AutoJs6/p13-32.txt", "utf8"), "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"),
                        expectDenied("proc", () => fs.readFileSync("/proc/version", "utf8"), "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"),
                        expectDenied("sys", () => fs.readFileSync("/sys/kernel", "utf8"), "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"),
                        expectDenied("dev", () => fs.readFileSync("/dev/null", "utf8"), "${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}"),
                        expectDenied("nul", () => fs.readFileSync("bad" + String.fromCharCode(0) + "path", "utf8"), "${NodeRuntimeErrorCodes.FS_NUL_BYTE}"),
                        expectDenied("realpathEscape", () => fs.realpathSync("outside-link/secret.txt"), "${NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE}"),
                        expectDenied("symlinkEscape", () => fs.readFileSync("outside-link/secret.txt", "utf8"), "${NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE}"),
                        await expectDeniedAsync("promiseEscape", () => fsp.readFile(${jsStringLiteral(outsideRelative)}, "utf8"), "${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}")
                      ];
                      console.log("p13fs.denials=" + denials.join("|"));
                      console.log("p13fs.safe=" + safeText + "|" + (safeReal.indexOf(root + "/") === 0));
                      console.log("p13fs.ready=true");
                    })().catch((error) => {
                      console.error("p13fs.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-filesystem-profile-decision-p13-32.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-32 filesystem profile decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("p13fs.profile=true|true|scoped_working_directory|true|true|true|true|true|true|true"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13fs.denials=absoluteOutside:${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}" +
                        "|escapeParent:${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}" +
                        "|sdcard:${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}" +
                        "|proc:${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}" +
                        "|sys:${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}" +
                        "|dev:${NodeRuntimeErrorCodes.FS_ABSOLUTE_PATH_DENIED}" +
                        "|nul:${NodeRuntimeErrorCodes.FS_NUL_BYTE}" +
                        "|realpathEscape:${NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE}" +
                        "|symlinkEscape:${NodeRuntimeErrorCodes.FS_REALPATH_ESCAPE}" +
                        "|promiseEscape:${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13fs.safe=ok|true"))
            assertTrue(result.stdout, result.stdout.contains("p13fs.ready=true"))
        } finally {
            try {
                Files.deleteIfExists(outsideLink.toPath())
            } catch (_: Throwable) {
            }
            workDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    private fun jsStringLiteral(value: String): String {
        return buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }
}
