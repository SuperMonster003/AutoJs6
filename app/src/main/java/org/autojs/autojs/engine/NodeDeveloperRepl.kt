package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs6.BuildConfig
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object NodeDeveloperRepl {

    private const val REPL_ROOT_DIRECTORY = "node-developer-repl"
    private const val DEFAULT_TIMEOUT_MS = 5_000L

    val available: Boolean
        get() = BuildConfig.DEBUG

    data class Options(
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val bridgeCapabilities: List<String> = emptyList(),
        val env: Map<String, String> = emptyMap(),
    )

    fun start(
        context: Context,
        options: Options = Options(),
    ): Session {
        requireDeveloperBuild()
        val appContext = context.applicationContext
        return Session(
            context = appContext,
            workingDirectory = createWorkingDirectory(appContext),
            options = options,
        )
    }

    fun evaluate(
        context: Context,
        source: String,
        options: Options = Options(),
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return start(context, options).use { session ->
            session.evaluate(source)
        }
    }

    private fun requireDeveloperBuild() {
        if (!BuildConfig.DEBUG) {
            throw SecurityException("AutoJs6 Node developer REPL is available only in debug builds.")
        }
    }

    private fun createWorkingDirectory(context: Context): File {
        val root = File(context.cacheDir, REPL_ROOT_DIRECTORY).canonicalFile
        check(root.exists() || root.mkdirs()) {
            "Failed to create Node developer REPL root: ${root.absolutePath}"
        }
        val rootPrefix = root.path + File.separator
        repeat(10) {
            val candidate = File(
                root,
                "session-${System.currentTimeMillis()}-${UUID.randomUUID()}",
            ).canonicalFile
            check(candidate.path.startsWith(rootPrefix)) {
                "Node developer REPL working directory escaped cache root: ${candidate.absolutePath}"
            }
            if (candidate.mkdirs()) {
                return candidate
            }
        }
        error("Failed to create unique Node developer REPL working directory under ${root.absolutePath}")
    }

    private fun buildRuntimeModuleSources(options: Options): Map<String, String> {
        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            capabilities = options.bridgeCapabilities,
            source = "developer-repl",
        )
        return mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson())
    }

    private fun wrapEvalSource(source: String): String {
        return """
            "nodejs";
            return (async function __autojs6_node_developer_repl_eval() {
              const __autojs6_repl_source = ${JSONObject.quote(source)};
              const __autojs6_repl_value = eval(__autojs6_repl_source);
              return await __autojs6_repl_value;
            })();
        """.trimIndent()
    }

    class Session internal constructor(
        private val context: Context,
        val workingDirectory: File,
        private val options: Options,
    ) : Closeable {

        private val closed = AtomicBoolean(false)
        private val evalCounter = AtomicInteger(0)
        private val runtimeModuleSources = buildRuntimeModuleSources(options)

        fun evaluate(source: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
            check(!closed.get()) {
                "AutoJs6 Node developer REPL session is already closed."
            }
            val evalIndex = evalCounter.incrementAndGet()
            val sourceName = File(
                workingDirectory,
                ".autojs6-node-repl-eval-$evalIndex.js",
            ).absolutePath
            return NodeJsRuntimePluginBridge.runScriptWithEmbeddedFallback(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = wrapEvalSource(source),
                    sourceName = sourceName,
                    workingDirectory = workingDirectory.absolutePath,
                    timeoutMs = options.timeoutMs.coerceAtLeast(1L),
                    env = options.env,
                    runtimeModuleSources = runtimeModuleSources,
                    workerThreadsEnabled = false,
                    javaInteropEnabled = false,
                ),
            )
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                workingDirectory.deleteRecursively()
            }
        }
    }
}
