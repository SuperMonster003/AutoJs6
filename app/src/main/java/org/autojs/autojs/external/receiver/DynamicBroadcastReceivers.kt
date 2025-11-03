package org.autojs.autojs.external.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTaskManager

class DynamicBroadcastReceivers(private val context: Context) {

    private val mActions: MutableSet<String> = LinkedHashSet()
    private val mReceiverRegistries: MutableList<ReceiverRegistry> = ArrayList<ReceiverRegistry>()
    private val mDefaultActionReceiver = BaseBroadcastReceiver()
    private val mPackageActionReceiver = BaseBroadcastReceiver()

    init {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mDefaultActionReceiver, createIntentFilter(StaticBroadcastReceiver.ACTIONS), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(mDefaultActionReceiver, createIntentFilter(StaticBroadcastReceiver.ACTIONS))
            }
            val filter: IntentFilter = createIntentFilter(StaticBroadcastReceiver.PACKAGE_ACTIONS)
            filter.addDataScheme("package")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mPackageActionReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(mPackageActionReceiver, filter)
            }
        }
    }

    fun registerIntent(task: IntentTask) {
        register(mutableListOf(task.action), task.isLocal)
    }

    @Synchronized
    fun register(actions: MutableList<String>, local: Boolean) {
        val newActions = LinkedHashSet<String>()
        for (action in actions) {
            if (!StaticBroadcastReceiver.ACTIONS.contains(action) && !StaticBroadcastReceiver.PACKAGE_ACTIONS.contains(action) && !mActions.contains(action)
            ) {
                newActions.add(action)
            }
        }
        if (newActions.isEmpty()) {
            return
        }
        val receiverRegistry = ReceiverRegistry(newActions, local)
        receiverRegistry.register()
        mReceiverRegistries.add(receiverRegistry)
        mActions.addAll(newActions)
    }

    @Synchronized
    fun unregister(action: String?) {
        if (!mActions.contains(action)) {
            return
        }
        mActions.remove(action)
        val iterator = mReceiverRegistries.iterator()
        while (iterator.hasNext()) {
            val receiverRegistry = iterator.next()
            if (!receiverRegistry.actions.contains(action)) {
                continue
            }
            receiverRegistry.actions.remove(action)
            receiverRegistry.unregister()
            if (!receiverRegistry.register()) {
                iterator.remove()
            }
            break
        }
    }

    @Synchronized
    fun unregisterAll() {
        for (registry in mReceiverRegistries) {
            registry.unregister()
        }
        mReceiverRegistries.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { context.unregisterReceiver(mDefaultActionReceiver) }
            runCatching { context.unregisterReceiver(mPackageActionReceiver) }
        }
    }

    private inner class ReceiverRegistry(var actions: LinkedHashSet<String>, var local: Boolean) {

        var receiver: BroadcastReceiver = BaseBroadcastReceiver()
        var jobs: List<Job> = emptyList()

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun register(): Boolean {
            if (actions.isEmpty()) {
                return false
            }
            if (local) {

                // @Archived by SuperMonster003 on Sep 27, 2025.
                //  ! LocalBroadcastManager is deprecated.
                //  ! zh-CN: LocalBroadcastManager 已被弃用.
                //  # LocalBroadcastManager.getInstance(mContext).registerReceiver(receiver, intentFilter);

                if (jobs.isNotEmpty()) {
                    jobs.forEach { it.cancel() }
                }
                jobs = actions.map { action ->
                    InAppEventBus.streamOf(action).onEach {
                        try {
                            TimedTaskManager.getIntentTaskOfAction(action)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ intentTask: IntentTask ->
                                    BaseBroadcastReceiver.runTask(context, Intent(action), intentTask)
                                }, { obj: Throwable? ->
                                    obj?.printStackTrace()
                                })
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }.launchIn(appScope)
                }
                Log.d(LOG_TAG, "register (in-app): $actions")
            } else {
                val intentFilter: IntentFilter = createIntentFilter(actions)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }
                Log.d(LOG_TAG, "register (system): $actions")
            }
            return true
        }

        fun unregister() {
            if (local) {

                // @Archived by SuperMonster003 on Sep 27, 2025.
                //  ! LocalBroadcastManager is deprecated.
                //  ! zh-CN: LocalBroadcastManager 已被弃用.
                //  # LocalBroadcastManager.getInstance(mContext).unregisterReceiver(receiver);

                jobs.forEach { it.cancel() }
                jobs = emptyList()
            } else {
                context.unregisterReceiver(receiver)
            }
        }
    }

    companion object {

        private const val LOG_TAG = "DynBroadcastReceivers"
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @JvmStatic
        val ACTION_STARTUP = "org.autojs.autojs.action.startup"

        private fun createIntentFilter(actions: MutableCollection<String>): IntentFilter {
            return IntentFilter().apply {
                actions.forEach { addAction(it) }
            }
        }

    }
}
