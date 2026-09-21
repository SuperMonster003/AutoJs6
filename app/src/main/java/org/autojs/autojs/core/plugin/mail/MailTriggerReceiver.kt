package org.autojs.autojs.core.plugin.mail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.runtime.api.mail.MailScriptValues
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.util.ViewUtils
import org.autojs.plugin.mail.api.MailActions
import org.autojs.plugin.mail.api.MailContract

/**
 * Receives the `org.autojs.autojs6.action.MAIL_TRIGGER` wake-up of the mail plugin's background
 * watches (mail roadmap P8, contract version 2) and launches the "mail arrived" intent tasks
 * that listen to the watch. The manifest entry is exported behind the host's signature
 * permission `org.autojs.permission.PLUGIN`, so only a package signed like the host can wake a
 * script this way; the dynamic receivers of the timed-task manager do not register this action.
 * The event document rides along as `KEY_EVENT_JSON`, becomes the launched script's
 * `execArgv.mail` and the wake-up intent its `intent` argument; the log lines carry the watch
 * id, folder and uid only, never the subject or an address.
 * zh-CN: 接收邮件插件后台守望的 `org.autojs.autojs6.action.MAIL_TRIGGER` 唤醒 (邮件路线图 P8, 契约版本 2), 启动监听该守望的 "邮件到达" 意图任务.
 * 清单项以宿主的签名权限 `org.autojs.permission.PLUGIN` 导出, 只有与宿主同签名的包才能这样唤醒脚本; 定时任务管理器的动态接收器不注册此动作.
 * 事件文档随 `KEY_EVENT_JSON` 传入, 成为被启动脚本的 `execArgv.mail`, 唤醒意图成为其 `intent` 参数; 日志只含守望 id, 文件夹与 uid, 不含主题或地址.
 */
class MailTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MailActions.HOST_TRIGGER_ACTION) return
        val version = intent.getIntExtra(MailContract.KEY_CONTRACT_VERSION, 0)
        if (version < MailContract.TRIGGER_CONTRACT_VERSION) {
            Log.w(LOG_TAG, "mail trigger with contract version $version ignored")
            return
        }
        val event = MailTriggerEvent.parse(intent.getStringExtra(MailContract.KEY_EVENT_JSON))
        if (event == null) {
            Log.w(LOG_TAG, "mail trigger without a usable event ignored")
            return
        }
        Log.d(LOG_TAG, "mail trigger: watch=${event.triggerId} folder=${event.folder} uid=${event.uid}")
        val appContext = context.applicationContext
        val argument = Intent(intent)
        val pending = goAsync()
        TimedTaskManager.getIntentTaskOfAction(MailActions.HOST_TRIGGER_ACTION)
            .toList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ tasks ->
                try {
                    dispatch(appContext, event, argument, tasks)
                } finally {
                    pending.finish()
                }
            }, { error ->
                error.printStackTrace()
                pending.finish()
            })
    }

    private fun dispatch(context: Context, event: MailTriggerEvent, argument: Intent, tasks: List<IntentTask>) {
        val targets = tasks.map { task ->
            MailTriggerDispatcher.Target(task.id, MailTriggerTaskExtras.parse(task.extras)) { launched -> launch(context, argument, task, launched) }
        }
        val launched = dispatcher.dispatch(event, targets)
        Log.d(LOG_TAG, "mail trigger: ${targets.size} task(s) listen, ${launched.size} launched")
    }

    companion object {

        private const val LOG_TAG = "MailTriggerReceiver"

        /** The name of the script argument that carries the event: `engines.myEngine().execArgv.mail`. zh-CN: 携带事件的脚本参数名: `engines.myEngine().execArgv.mail`. */
        const val ARGUMENT_MAIL = "mail"

        private val mainHandler = Handler(Looper.getMainLooper())

        /** The process-wide repeat check and throttle; used on the main thread only. zh-CN: 进程级的重复检查与节流; 仅在主线程使用. */
        val dispatcher: MailTriggerDispatcher by lazy {
            MailTriggerDispatcher(schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) })
        }

        /**
         * Launches [task] for [event] the way the timed-task receivers do, with the event
         * document as the `mail` argument of the execution config.
         * zh-CN: 像定时任务接收器那样为 [event] 启动 [task], 事件文档作为执行配置的 `mail` 参数.
         */
        fun launch(context: Context, argument: Intent, task: IntentTask, event: MailTriggerEvent) {
            try {
                val launchIntent = Intent().putExtra(ScriptIntents.EXTRA_KEY_PATH, task.scriptPath)
                task.sourceKind?.let { launchIntent.putExtra(ScriptIntents.EXTRA_KEY_SOURCE_KIND, it) }
                task.projectRoot?.let { launchIntent.putExtra(ScriptIntents.EXTRA_KEY_PROJECT_ROOT, it) }
                val argumentIntent = Intent(argument).putExtra(MailContract.KEY_EVENT_JSON, event.json)
                val prepared = ScriptIntents.prepareLaunch(context, launchIntent, argumentIntent) ?: return
                prepared.config.setArgument(ARGUMENT_MAIL, MailScriptValues.toPlain(event.json) ?: emptyMap<String, Any?>())
                Scripts.executeLaunch(context, prepared.launch, prepared.config)
            } catch (e: Exception) {
                e.printStackTrace()
                ViewUtils.showToast(context, e.message, true)
            }
        }
    }
}
