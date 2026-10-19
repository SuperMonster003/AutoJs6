package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.ipc.InAppEventBus
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTaskManager
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persistent user-visible tasks use the same database and scheduler as the task UI. */
internal class RuntimeTasksBridge(private val context: Context, private val cwd: String) {
    fun invoke(method: String, args: JSONArray): Any? = synchronized(lock) {
        when (method) {
            "addTimedTask" -> createTimed(args.getJSONObject(0)).let { TimedTaskManager.addTaskSync(it); timed(it) }
            "addIntentTask" -> createIntent(args.getJSONObject(0)).let { TimedTaskManager.addTaskSync(it); intent(it) }
            "queryTimedTasks" -> {
                val options=args.optJSONObject(0)?:JSONObject()
                JSONArray(TimedTaskManager.allTasksAsList.filter { matchPath(it.scriptPath,options) && (!options.has("repeatMode") || it.repeatMode==mode(options.get("repeatMode"))) }.map(::timed))
            }
            "queryIntentTasks" -> {
                val options=args.optJSONObject(0)?:JSONObject()
                JSONArray(TimedTaskManager.allIntentTasksAsList.filter { matchPath(it.scriptPath,options) && (!options.has("action") || it.action==options.getString("action")) }.map(::intent))
            }
            "getTimedTask" -> TimedTaskManager.getTimedTask(id(args.get(0)))?.let(::timed)
            "getIntentTask" -> TimedTaskManager.getIntentTask(id(args.get(0)))?.let(::intent)
            "removeTimedTask" -> TimedTaskManager.getTimedTask(id(args.get(0)))?.let(TimedTaskManager::removeTaskSync)?:false
            "removeIntentTask" -> TimedTaskManager.getIntentTask(id(args.get(0)))?.let { task ->
                TimedTaskManager.removeTaskSync(task).also { removed -> if(removed)triggerStore(context).edit().remove(task.id.toString()).apply() }
            }?:false
            "updateTask" -> update(args.getJSONObject(0))
            "sendLocalBroadcastSync" -> {
                val descriptor=args.optJSONObject(0)?:JSONObject().put("action",args.getString(0))
                val action=descriptor.getString("action");require(action.isNotBlank()) { "Broadcast action must not be empty." }
                val intent=Intent(action)
                val type=optionalText(descriptor,"type")
                val data=optionalText(descriptor,"data")?.let(Uri::parse)
                intent.setDataAndType(data,type)
                optionalText(descriptor,"category")?.let(intent::addCategory)
                descriptor.optJSONArray("categories")?.let { categories->for(i in 0 until categories.length())intent.addCategory(categories.getString(i)) }
                descriptor.optJSONObject("extras")?.let { extras->for(key in extras.keys())when(val value=extras.get(key)){
                    is String->intent.putExtra(key,value);is Boolean->intent.putExtra(key,value)
                    is Number->if(value.toDouble()==value.toLong().toDouble())intent.putExtra(key,value.toLong())else intent.putExtra(key,value.toDouble())
                    JSONObject.NULL->intent.putExtra(key,null as String?)
                    else->throw IllegalArgumentException("Local broadcast extras must be JSON primitives.")
                } }
                InAppEventBus.tryEmitPayload(action,intent)
            }
            else -> throw IllegalArgumentException("Unknown task method: $method")
        }
    }

    private fun path(value: String): String {
        require(value.isNotBlank() && !value.contains('\u0000')) { "Task path must not be empty." }
        val requested=if(value.startsWith("file:"))File(Uri.parse(value).path?:error("Invalid file URI"))else File(value)
        return (if(requested.isAbsolute)requested else File(cwd,requested.path)).canonicalPath
    }
    private fun script(options: JSONObject): String {
        val raw=sequenceOf("path","scriptPath","entry").firstOrNull { options.has(it) }?.let(options::getString)?:error("A task script path is required.")
        return path(raw).also { require(File(it).isFile || File(it,"project.json").isFile) { "Task source does not exist: $it" } }
    }
    private fun matchPath(value: String?, options: JSONObject): Boolean {
        val raw=optionalText(options,"path")?:optionalText(options,"scriptPath").orEmpty()
        return raw.isEmpty() || value==path(raw)
    }
    private fun createTimed(options: JSONObject): TimedTask {
        val source=script(options)
        val loops=number(options,"loopTimes",1).also { require(it in 1..Int.MAX_VALUE.toLong()) }
        val config=ExecutionConfig(delay=number(options,"delay",0),interval=number(options,"interval",0),loopTimes=loops.toInt())
        require(config.delay>=0 && config.interval>=0 && config.loopTimes>0) { "Invalid task execution options." }
        val task=when(mode(options.get("repeatMode"))) {
            TimedTask.REPEAT_MODE_DISPOSABLE -> TimedTask.disposableTask(dateTime(options.opt("date")?:options.get("time")),source,config)
            TimedTask.REPEAT_MODE_COUNTDOWN -> TimedTask.countdownTask(number(options,"delayMs",-1).also { require(it>0) { "delayMs must be positive." } },source,config)
            TimedTask.REPEAT_MODE_DAILY -> TimedTask.dailyTask(time(options.opt("time")?:options.opt("date")),source,config)
            TimedTask.REPEAT_MODE_WEEKLY -> TimedTask.weeklyTask(time(options.opt("time")?:options.opt("date")),weekdays(options.getJSONArray("daysOfWeek").let { values->(0 until values.length()).map(values::get) }),source,config)
            TimedTask.REPEAT_MODE_MONTHLY -> TimedTask.monthlyTask(time(options.opt("time")),number(options,"dayOfMonth",-1).also { require(it in 1..31) }.toInt(),source,config)
            TimedTask.REPEAT_MODE_YEARLY -> {
                val month=number(options,"monthOfYear",-1).also { require(it in 1..12) }.toInt();val day=number(options,"dayOfMonth",-1).also { require(it in 1..31) }.toInt();LocalDate(2000,month,day)
                TimedTask.yearlyTask(time(options.opt("time")),month,day,source,config)
            }
            else -> throw IllegalArgumentException("Unsupported calendar mode.")
        }
        if(!task.isDisposable) {
            val end=options.optString("endMode",if(options.has("untilDate"))"until_date" else if(options.has("times"))"count" else "forever")
            when(end) {
                "forever" -> { task.endMode=TimedTask.END_MODE_FOREVER;task.endValue=0 }
                "count" -> { task.endMode=TimedTask.END_MODE_COUNT;task.endValue=number(options,"times",-1).also { require(it>0) } }
                "until_date", "date" -> { task.endMode=TimedTask.END_MODE_UNTIL_DATE;task.endValue=LocalDate.parse(options.getString("untilDate")).toDateTimeAtStartOfDay().millis }
                else -> throw IllegalArgumentException("Unknown endMode: $end")
            }
        }
        return task
    }
    private fun createIntent(options: JSONObject): IntentTask = IntentTask().apply {
        setScriptPathAndCaptureContext(script(options))
        action=options.getString("action").also { require(it.isNotBlank()) { "IntentTask action must not be empty." } }
        category=optionalText(options,"category")
        dataType=optionalText(options,"dataType")
        dataType?.let { type -> try { android.content.IntentFilter().addDataType(type) } catch(_:android.content.IntentFilter.MalformedMimeTypeException) { throw IllegalArgumentException("Invalid IntentTask MIME type: $type") } }
        isLocal=options.optBoolean("local",options.optBoolean("isLocal",action==DynamicBroadcastReceivers.ACTION_STARTUP))
        extras=JSONObject().put("runtimeTasks",true).toString()
    }
    private fun update(patch: JSONObject): JSONObject? {
        val id=id(patch.get("id"))
        return when(patch.getString("kind")) {
            "timed" -> {
                val old=TimedTaskManager.getTimedTask(id)?:return null
                val options=timed(old).getJSONObject("options")
                patch.keys().forEach { if(it!="id"&&it!="kind")options.put(it,patch.get(it)) }
                if(old.isCountdown&&!patch.has("delayMs"))options.put("delayMs",maxOf(1L,old.millis-System.currentTimeMillis()))
                val next=createTimed(options);next.id=id;next.finishedTimes=old.finishedTimes;next.isScheduled=false
                if(old.isCountdown&&next.isCountdown&&!patch.has("delayMs"))next.millis=old.millis
                if(TimedTaskManager.updateTaskSync(next))timed(next)else null
            }
            "intent" -> {
                val old=TimedTaskManager.getIntentTask(id)?:return null
                val options=intent(old).getJSONObject("options")
                patch.keys().forEach { if(it!="id"&&it!="kind")options.put(it,patch.get(it)) }
                val next=createIntent(options);next.id=id;next.extras=old.extras
                if(TimedTaskManager.updateTaskSync(next))intent(next)else null
            }
            else -> throw IllegalArgumentException("updateTask requires kind=timed or intent.")
        }
    }
    private fun timed(task: TimedTask): JSONObject {
        val options=JSONObject().put("path",task.scriptPath).put("repeatMode",modeName(task.repeatMode))
            .put("delay",task.delay).put("interval",task.interval).put("loopTimes",task.loopTimes)
        if(task.isDisposable)options.put("date",task.millis).put("delayMs",(task.millis-System.currentTimeMillis()).coerceAtLeast(0))
        else options.put("time",LocalTime.fromMillisOfDay(task.millis).toString()).put("daysOfWeek",JSONArray((0..6).filter { task.timeFlag and (1L shl it)!=0L }))
            .put("dayOfMonth",task.dayOfMonth).put("monthOfYear",task.monthOfYear)
            .put("endMode",when(task.endMode){TimedTask.END_MODE_COUNT->"count";TimedTask.END_MODE_UNTIL_DATE->"until_date";else->"forever"})
            .put("times",task.endValue).apply { if(task.endMode==TimedTask.END_MODE_UNTIL_DATE)put("untilDate",LocalDate(task.endValue).toString()) }
        return JSONObject().put("id",task.id).put("kind","timed").put("path",task.scriptPath).put("repeatMode",modeName(task.repeatMode))
            .put("millis",task.millis).put("timeFlag",task.timeFlag).put("nextTime",task.getNextTime(context)).put("scheduled",task.isScheduled)
            .put("finishedTimes",task.finishedTimes).put("sourceKind",task.sourceKind?:JSONObject.NULL).put("projectRoot",task.projectRoot?:JSONObject.NULL).put("options",options)
    }
    private fun intent(task: IntentTask): JSONObject {
        val options=JSONObject().put("path",task.scriptPath).put("action",task.action).put("local",task.isLocal)
        task.category?.let { options.put("category",it) };task.dataType?.let { options.put("dataType",it) }
        return JSONObject().put("id",task.id).put("kind","intent").put("path",task.scriptPath).put("action",task.action).put("local",task.isLocal)
            .put("category",task.category?:JSONObject.NULL).put("dataType",task.dataType?:JSONObject.NULL).put("options",options)
            .put("lastIntent",triggerStore(context).getString(task.id.toString(),null)?.let(::JSONObject)?:JSONObject.NULL)
    }
    companion object {
        private val lock=Any()
        val methods=setOf("addTimedTask","addIntentTask","queryTimedTasks","queryIntentTasks","getTimedTask","getIntentTask","removeTimedTask","removeIntentTask","updateTask","sendLocalBroadcastSync")
        private fun triggerStore(context:Context)=context.getSharedPreferences("runtime_task_intents",Context.MODE_PRIVATE)
        @JvmStatic fun forgetIntent(context:Context,id:Long) { triggerStore(context).edit().remove(id.toString()).apply() }
        @JvmStatic fun recordIntent(context:Context,task:IntentTask,intent:Intent) {
            if(runCatching { JSONObject(task.extras?:"{}").optBoolean("runtimeTasks") }.getOrDefault(false))
                triggerStore(context).edit().putString(task.id.toString(),RuntimeAndroidObjects.intentSnapshot(intent).put("receivedAt",System.currentTimeMillis()).toString()).apply()
        }
        internal fun number(options:JSONObject,key:String,default:Long):Long {
            if(!options.has(key))return default
            val value=options.get(key);require(value is Number && value.toDouble().isFinite() && value.toDouble()==value.toLong().toDouble()) { "$key must be an integer." };return value.toLong()
        }
        private fun optionalText(options:JSONObject,key:String):String? {
            if(options.isNull(key))return null
            val value=options.get(key);require(value is String) { "$key must be a string or null." }
            return value.takeIf(String::isNotBlank)
        }
        internal fun id(value:Any):Long=if(value is Number)value.toLong().also { require(it>0 && it.toDouble()==value.toDouble()) }else value.toString().toLong().also { require(it>0) }
        internal fun mode(value:Any):Int=if(value is Number)value.toInt().also { require(it.toDouble()==value.toDouble()&&it in 0..5) }else when(value.toString().lowercase()) {
            "disposable","once"->0;"daily"->1;"weekly"->2;"monthly"->3;"yearly"->4;"countdown"->5;else->throw IllegalArgumentException("Unknown task mode: $value")
        }
        internal fun modeName(value:Int)=listOf("disposable","daily","weekly","monthly","yearly","countdown")[value]
        internal fun time(value:Any?):LocalTime=when(value){is Number->LocalTime(value.toLong());is String->LocalTime.parse(value);else->throw IllegalArgumentException("time must be local HH:mm[:ss] or an epoch timestamp.")}
        internal fun dateTime(value:Any):LocalDateTime=when(value){
            is Number->LocalDateTime(value.toLong())
            is String->value.replace(' ','T').let { text->if(Regex("(Z|[+-]\\d{2}:?\\d{2})$").containsMatchIn(text))LocalDateTime(org.joda.time.DateTime.parse(text).millis)else LocalDateTime.parse(text) }
            else->throw IllegalArgumentException("date must be an epoch timestamp or local date-time.")
        }
        internal fun weekdays(values:List<Any>):Long {
            val names=listOf("sunday","monday","tuesday","wednesday","thursday","friday","saturday");var flag=0L
            for(value in values) {
                val day=if(value is Number)value.toInt().also { require(it.toDouble()==value.toDouble()&&it in 0..7) }%7 else names.indexOfFirst { it==value.toString().lowercase()||it.take(3)==value.toString().lowercase() }.also { require(it>=0) { "Invalid weekday: $value" } }
                flag=flag or (1L shl day)
            }
            require(flag!=0L) { "daysOfWeek must not be empty." };return flag
        }
    }
}
