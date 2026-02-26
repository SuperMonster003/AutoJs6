package org.autojs.autojs.autoglm

import android.content.Context
import android.view.View
import android.widget.Toast
import com.afollestad.materialdialogs.MaterialDialog
import com.kevinluo.autoglm.api.AutoJsCapabilities
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.util.WorkingDirectoryUtils
import android.view.ContextThemeWrapper
import org.autojs.autojs6.R as AutoJsR
import com.kevinluo.autoglm.api.ScriptExecutionEvent
import com.kevinluo.autoglm.api.Subscription
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import android.content.Intent
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.script.JavaScriptFileSource
import org.autojs.autojs.util.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AutoJs 在宿主侧对 AutoGLM 暴露的能力实现。
 */
class AutoJsCapabilitiesImpl : AutoJsCapabilities {

    override fun pickWorkspaceScript(context: Context, onPicked: (String) -> Unit) {
        // 关键：用 AutoJs 的 theme 包装，避免 explorer_file inflate 时解析属性失败
        val themedContext = ContextThemeWrapper(context, AutoJsR.style.AppTheme)

        val explorer = Explorer(ExplorerFileProvider(Scripts.FILE_FILTER), 0)

        val explorerView = ExplorerView(themedContext).apply {
            setExplorer(explorer, ExplorerDirPage.createRoot(WorkingDirectoryUtils.path))
            setDirectorySpanSize(2)
            setProjectToolbarRunnableOnly(true)
        }

        val dialog = MaterialDialog.Builder(context)
            .title("选择脚本")
            .customView(explorerView, false)
            .negativeText("关闭")
            .cancelable(true)
            .build()

        explorerView.setOnItemClickListener(object : ExplorerView.OnItemClickListener {
            override fun onItemClick(view: View?, item: ExplorerItem) {
                if (!item.isExecutable) {
                    Toast.makeText(context, "该文件不可执行", Toast.LENGTH_SHORT).show()
                    return
                }
                onPicked(item.path)
                dialog.dismiss()
            }
        })

        explorerView.setOnItemOperateListener(object : ExplorerView.OnItemOperateListener {
            override fun onItemOperated(item: ExplorerItem) {
                dialog.dismiss()
            }
        })

        dialog.show()
    }

    override fun observeScriptExecutions(onEvent: (ScriptExecutionEvent) -> Unit): Subscription {
        val listener = object : ScriptExecutionListener {

            override fun onStart(execution: ScriptExecution) {
                onEvent(
                    ScriptExecutionEvent(
                        type = ScriptExecutionEvent.Type.START,
                        id = execution.id, // 若编译不过改成 execution.getId()
                        scriptPath = runCatching { execution.source.elegantPath }.getOrNull()
                            ?: runCatching { execution.source.fullPath }.getOrNull(),
                    )
                )
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                onEvent(
                    ScriptExecutionEvent(
                        type = ScriptExecutionEvent.Type.SUCCESS,
                        id = execution.id,
                        scriptPath = runCatching { execution.source.elegantPath }.getOrNull()
                            ?: runCatching { execution.source.fullPath }.getOrNull(),
                        message = result?.toString(),
                    )
                )
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                onEvent(
                    ScriptExecutionEvent(
                        type = ScriptExecutionEvent.Type.EXCEPTION,
                        id = execution.id,
                        scriptPath = runCatching { execution.source.elegantPath }.getOrNull()
                            ?: runCatching { execution.source.fullPath }.getOrNull(),
                        message = e.message ?: e::class.java.name,
                    )
                )
            }
        }

        AutoJs.instance.scriptEngineService.registerGlobalScriptExecutionListener(listener)

        return Subscription {
            AutoJs.instance.scriptEngineService.unregisterGlobalScriptExecutionListener(listener)
        }
    }

    override fun listWorkspaceScriptsJson(): JSONObject {
        val root = WorkingDirectoryUtils.path
        val rootDir = File(root)

        val scripts = JSONArray()

        // 根目录即可：只遍历一层
        val files = rootDir.listFiles() ?: emptyArray()
        for (f in files) {
            if (!f.isFile) continue
            val name = f.name
            val type = when {
                name.endsWith(FileUtils.TYPE.JAVASCRIPT.extensionWithDot, ignoreCase = true) -> "js"
                name.endsWith(FileUtils.TYPE.AUTO.extensionWithDot, ignoreCase = true) -> "auto"
                else -> null
            } ?: continue

            scripts.put(
                JSONObject()
                    .put("name", name)
                    .put("path", f.absolutePath)
                    .put("type", type)
            )
        }

        return JSONObject()
            .put("ok", true)
            .put("root", root)
            .put("count", scripts.length())
            .put("scripts", scripts)
    }

    override fun runScriptJson(path: String, params: JSONObject?): JSONObject {
        ensureGlobalExecutionCacheListenerRegistered()

        val file = File(path)
        if (!file.exists() || !file.isFile) {
            return errorJson("FILE_NOT_FOUND", "Script file not found: $path")
        }

        // 仅允许 .js/.auto（避免模型乱跑别的文件）
        val isJs = path.endsWith(FileUtils.TYPE.JAVASCRIPT.extensionWithDot, ignoreCase = true)
        val isAuto = path.endsWith(FileUtils.TYPE.AUTO.extensionWithDot, ignoreCase = true)

        return try {
            val intent = Intent().apply {
                putExtra(EXTRA_GLM_PARAMS_JSON, params?.toString())
                // 可选：你也可以加 taskId/sessionId，方便脚本回传
                // putExtra("autoglm_task_id", ...)
            }

            val workingDir = file.parentFile?.absolutePath ?: WorkingDirectoryUtils.path
            val config = ExecutionConfig(workingDirectory = workingDir).apply {
                setArgument(ARGUMENT_INTENT, intent)
            }

            val execution = if (isJs) {
                AutoJs.instance.scriptEngineService.execute(JavaScriptFileSource(path), config)
            } else {
                // .auto 也可以用 Scripts.run(context, ScriptFile(path))，但这里走统一 execute
                AutoJs.instance.scriptEngineService.execute(ScriptFile(file).toSource(), config)
            }

            // 先把 “启动信息”写入缓存，避免模型立刻 poll 时查不到
            markStarted(execution)

            JSONObject()
                .put("ok", true)
                .put("executionId", execution.id)
                .put("path", path)
                .put("startedAt", System.currentTimeMillis())
        } catch (e: Throwable) {
            errorJson("RUN_FAILED", e.message ?: e::class.java.name)
        }
    }

    override fun getExecutionStatusJson(executionId: Int): JSONObject {
        ensureGlobalExecutionCacheListenerRegistered()

        val status = latestStatusById[executionId]
        val lastEvent = latestEventById[executionId]

        if (status == null && lastEvent == null) {
            return errorJson("NOT_FOUND", "No execution found for id=$executionId")
        }

        val state = status?.state ?: "unknown"
        val finished = status?.finished ?: false
        val path = status?.path ?: lastEvent?.scriptPath

        val lastEventJson = lastEvent?.let {
            JSONObject()
                .put("type", it.type.name)
                .put("timestamp", it.timestamp)
                .put("message", it.message)
        }

        return JSONObject()
            .put("ok", true)
            .put("executionId", executionId)
            .put("path", path)
            .put("state", state)
            .put("finished", finished)
            .put("lastEvent", lastEventJson)
    }

    // GLOBAL_EXECUTION CACHE
    private data class Status(
        val path: String?,
        val state: String,     // running/success/exception
        val finished: Boolean,
        val updatedAt: Long,
    )

    companion object {
        const val EXTRA_GLM_PARAMS_JSON = "autoglm_params_json"
        const val ARGUMENT_INTENT = "intent"

        private val latestStatusById = ConcurrentHashMap<Int, Status>()
        private val latestEventById = ConcurrentHashMap<Int, ScriptExecutionEvent>()
        private val cacheListenerRegistered = AtomicBoolean(false)
    }

    private fun safePath(execution: ScriptExecution): String? {
        return runCatching { execution.source.elegantPath }.getOrNull()
            ?: runCatching { execution.source.fullPath }.getOrNull()
    }

    private fun ensureGlobalExecutionCacheListenerRegistered() {
        if (!cacheListenerRegistered.compareAndSet(false, true)) return

        val listener = object : ScriptExecutionListener {
            override fun onStart(execution: ScriptExecution) {
                val id = execution.id
                val path = safePath(execution)
                latestStatusById[id] = Status(path, "running", finished = false, updatedAt = System.currentTimeMillis())
                latestEventById[id] = ScriptExecutionEvent(ScriptExecutionEvent.Type.START, id, path)
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                val id = execution.id
                val path = safePath(execution)
                latestStatusById[id] = Status(path, "success", finished = true, updatedAt = System.currentTimeMillis())
                latestEventById[id] = ScriptExecutionEvent(ScriptExecutionEvent.Type.SUCCESS, id, path, message = result?.toString())
            }

            override fun onException(execution: ScriptExecution, e: Throwable) {
                val id = execution.id
                val path = safePath(execution)
                latestStatusById[id] = Status(path, "exception", finished = true, updatedAt = System.currentTimeMillis())
                latestEventById[id] = ScriptExecutionEvent(ScriptExecutionEvent.Type.EXCEPTION, id, path, message = e.message ?: e::class.java.name)
            }
        }

        AutoJs.instance.scriptEngineService.registerGlobalScriptExecutionListener(listener)
    }

    private fun errorJson(code: String, message: String): JSONObject {
        return JSONObject()
            .put("ok", false)
            .put("error", JSONObject().put("code", code).put("message", message))
    }

    private fun markStarted(execution: ScriptExecution) {
        val id = execution.id
        latestStatusById[id] = Status(safePath(execution), "running", finished = false, updatedAt = System.currentTimeMillis())
        latestEventById[id] = ScriptExecutionEvent(ScriptExecutionEvent.Type.START, id, safePath(execution))
    }
}