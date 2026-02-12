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
}