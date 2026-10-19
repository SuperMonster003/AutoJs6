package org.autojs.autojs.external.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.plugin.mail.api.MailActions

/** Registrations follow current database rows, including local/type/category distinctions. */
class DynamicBroadcastReceivers(private val context:Context) {
    private data class Registration(val receiver:BroadcastReceiver?=null,val job:Job?=null)
    private val registrations=linkedMapOf<Pair<String,Boolean>,List<Registration>>()
    private val defaultReceiver=BaseBroadcastReceiver()
    private val packageReceiver=BaseBroadcastReceiver()
    init {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
            registerReceiver(defaultReceiver,IntentFilter().apply { StaticBroadcastReceiver.ACTIONS.forEach(::addAction) })
            registerReceiver(packageReceiver,IntentFilter().apply { StaticBroadcastReceiver.PACKAGE_ACTIONS.forEach(::addAction);addDataScheme("package") })
        }
    }
    fun registerIntent(task:org.autojs.autojs.timing.IntentTask) { refresh(task.action) }
    @Synchronized fun register(actions:MutableList<String>,local:Boolean) { actions.distinct().forEach { rebuild(it,local) } }
    @Synchronized fun refresh(action:String?) {
        if(action==null)return
        rebuild(action,true);rebuild(action,false)
    }
    private fun rebuild(action:String,local:Boolean) {
        release(action to local)
        // Mail delivery belongs to its manifest receiver behind the host signature permission.
        if(action==MailActions.HOST_TRIGGER_ACTION)return
        if(!local&&(action in StaticBroadcastReceiver.ACTIONS||action in StaticBroadcastReceiver.PACKAGE_ACTIONS))return
        val tasks=TimedTaskManager.allIntentTasksAsList.filter { it.action==action&&it.isLocal==local }
        if(tasks.isEmpty())return
        registrations[action to local]=if(local)listOf(Registration(job=InAppEventBus.streamPayloadsOf(action).onEach { payload ->
            val intent=payload as? Intent?:Intent(action)
            TimedTaskManager.allIntentTasksAsList.filter { it.action==action&&it.isLocal }.forEach { BaseBroadcastReceiver.runTask(context,intent,it) }
        }.launchIn(appScope))) else tasks.groupBy { it.category to it.dataType }.map { (key,_) ->
            val receiver=object:BroadcastReceiver() {
                override fun onReceive(context:Context,intent:Intent) {
                    TimedTaskManager.allIntentTasksAsList.filter { !it.isLocal&&it.action==action&&(it.category to it.dataType)==key }
                        .forEach { BaseBroadcastReceiver.runTask(context,intent,it) }
                }
            }
            val filter=IntentFilter(action).apply { key.first?.let(::addCategory);key.second?.let(::addDataType) }
            registerReceiver(receiver,filter);Registration(receiver=receiver)
        }
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver(receiver:BroadcastReceiver,filter:IntentFilter) {
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver,filter)
    }
    private fun release(key:Pair<String,Boolean>) {
        registrations.remove(key)?.forEach { entry -> entry.job?.cancel();entry.receiver?.let { context.unregisterReceiver(it) } }
    }
    @Synchronized fun unregister(action:String?) { if(action!=null){release(action to true);release(action to false)} }
    @Synchronized fun unregisterAll() {
        registrations.keys.toList().forEach(::release)
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){runCatching { context.unregisterReceiver(defaultReceiver) };runCatching { context.unregisterReceiver(packageReceiver) }}
    }
    companion object {
        private val appScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        @JvmStatic val ACTION_STARTUP="org.autojs.autojs.action.startup"
    }
}
