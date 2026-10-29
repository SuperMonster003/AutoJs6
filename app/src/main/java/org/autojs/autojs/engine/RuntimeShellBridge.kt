package org.autojs.autojs.engine

import android.util.Base64
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.plugin.concurrentSnapshot
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.shell.ProcessTree
import org.autojs.autojs.runtime.api.shell.ShellLauncher
import org.autojs.autojs.util.RootUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Execution-owned host processes. Queues apply backpressure; binary output is never decoded in transit. */
internal class RuntimeShellBridge(
    private val cwd: String,
    private val registry: NodeBridgeResourceRegistry,
    private val acquire: () -> Boolean,
    private val release: () -> Unit,
    private val permissions: () -> NodeBridgePermissionManifest.Manifest,
) : Closeable {
    private class LimitReached:Exception("Shell active process limit reached")
    private val lock = Any()
    private var closed = false
    private val processes = ConcurrentHashMap<String, Entry>()
    private inner class Pending { val cancelled=AtomicBoolean(false);@Volatile var entry:Entry?=null }
    private val pending=ConcurrentHashMap<String,Pending>()
    fun cancelRequest(id:String){pending[id]?.let { it.cancelled.set(true);it.entry?.close() }}
    private val timers = Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"AutoJs6ShellTimeout").apply { isDaemon=true } }
    val activeCount get() = processes.size

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val operation=if(request.method=="exec")Pending().also{pending[request.id]=it}else null
        thread(name="AutoJs6Shell-${request.method}",isDaemon=true) {
            val response=try { NodeBridgeResponse.success(request.id,NodeBridgeJson.valueToJson(invoke(request,operation))) }
            catch(e: LimitReached){NodeBridgeResponse.failure(request.id,NodeBridgeError.resourceLimit(e.message!!,request.module,request.method))}
            catch(e: SecurityException){NodeBridgeResponse.failure(request.id,NodeBridgeError.permissionDenied(e.message ?: "Shell access denied",request.module,request.method))}
            catch(e: IllegalArgumentException){NodeBridgeResponse.failure(request.id,NodeBridgeError.invalidRequest(e.message ?: "Invalid shell request",request.module,request.method))}
            catch(e: Exception){NodeBridgeResponse.failure(request.id,NodeBridgeError.providerFailed(e.message ?: "Shell operation failed",request.module,request.method))}
            pending.remove(request.id)
            callback(response)
        }
    }
    private fun invoke(request: NodeBridgeRequest, operation: Pending?): Any? {
        val args=JSONArray(request.argsJson)
        return when(request.method) {
            "requestShizukuPermission" -> {
                if(!WrappedShizuku.isRunning())throw SecurityException("Shizuku is not running")
                if(WrappedShizuku.hasPermission())true else {
                    val latch=java.util.concurrent.CountDownLatch(1);val code=permissionCodes.incrementAndGet();var granted=false
                    val listener=rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode,result ->
                        if(requestCode==code){granted=result==android.content.pm.PackageManager.PERMISSION_GRANTED;latch.countDown()}
                    }
                    rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
                    try { rikka.shizuku.Shizuku.requestPermission(code);check(latch.await(request.timeoutMs.coerceAtMost(60000),TimeUnit.MILLISECONDS)){"Shizuku permission request timed out"};granted }
                    finally { rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener) }
                }
            }
            "getAccess" -> {
                val declared=permissions();val required=listOf("shell","shell.root","shell.shizuku")
                val missing=if(declared.enforced)NodeBridgePermissionManifest.missingCapabilities(declared.permissions,required)else emptyList()
                JSONObject().put("shell","shell" !in missing).put("rootAvailable",RootUtils.isRootAvailable())
                    .put("shizukuRunning",WrappedShizuku.isRunning()).put("shizukuGranted",WrappedShizuku.hasPermission())
                    .put("shizuku",WrappedShizuku.isOperational() && "shell.shizuku" !in missing)
                    .put("shizukuInstalled",WrappedShizuku.isInstalled(GlobalAppContext.get())).put("shizukuHasService",WrappedShizuku.hasService())
                    .put("shizukuOperational",WrappedShizuku.isOperational())
                    .put("missingCapabilities",JSONArray(missing)).put("uid",android.os.Process.myUid())
            }
            "spawn", "open", "exec" -> {
                val opts=if(request.method=="open")args.optJSONObject(0) ?: JSONObject()else args.optJSONObject(1) ?: JSONObject()
                val command=if(request.method=="open")opts.optString("cmd","sh /proc/self/fd/0")else args.opt(0) as? String ?: throw IllegalArgumentException("command must be a string")
                if(request.method=="exec" && !opts.has("timeoutMs"))opts.put("timeoutMs",30000)
                val entry=start(command,opts,operation)
                if(request.method=="exec")collect(entry,opts)else entry.record()
            }
            "close" -> {
                val entry=processes[args.getString(0)]
                if(entry==null)JSONObject().put("wasAlive",false).put("code",JSONObject.NULL)
                else { val alive=entry.isAlive();entry.close();JSONObject().put("wasAlive",alive).put("code",entry.code ?: -1) }
            }
            else -> {
                if(request.method=="closeInput" && !processes.containsKey(args.getString(0)))return null
                if(request.method=="kill" && !processes.containsKey(args.getString(0)))return false
                val entry=processes[args.getString(0)] ?: throw IllegalArgumentException("Shell process is closed or belongs to another execution")
                when(request.method){
                    "drain" -> entry.drain()
                    "write" -> { val encoded=args.getString(1);require(encoded.length<=49152){"Shell write chunks must fit the bridge request"};entry.write(Base64.decode(encoded,Base64.DEFAULT));null }
                    "closeInput" -> { entry.closeInput();null }
                    "kill" -> { val alive=entry.isAlive();entry.terminate(false);alive }
                    else -> throw IllegalArgumentException("Unsupported shell method")
                }
            }
        }
    }
    private fun start(command: String, options: JSONObject, operation: Pending?): Entry {
        require(command.isNotBlank() && '\u0000' !in command){"command must be non-empty without NUL"}
        fun flag(name:String):Boolean {val v=options.opt(name);require(v==null || v===JSONObject.NULL || v is Boolean){"$name must be boolean"};return v==true}
        val root=flag("root");val shizuku=flag("shizuku") || flag("adb");val pty=flag("pty")
        if(shizuku && !WrappedShizuku.isOperational())throw SecurityException("Shizuku must be running and must authorize this application")
        val environment=linkedMapOf<String,String>();val env=options.opt("env")
        require(env==null || env===JSONObject.NULL || env is JSONObject){"env must be an object"}
        if(env is JSONObject)env.keys().forEach { key ->
            val value=env.get(key);require(value is String && '\u0000' !in value){"env values must be strings without NUL"};environment[key]=value
        }
        val raw=options.opt("cwd");require(raw==null || raw===JSONObject.NULL || raw is String){"cwd must be a string"}
        val directory=if(raw is String && raw.isNotEmpty())File(raw).let{if(it.isAbsolute)it else File(cwd,raw)}else File(if(shizuku)"/" else cwd)
        require('\u0000' !in directory.path){"cwd must not contain NUL"}
        val timeout=options.opt("timeoutMs")?.takeUnless{it===JSONObject.NULL} ?: 0
        require(timeout is Number && timeout.toDouble().isFinite() && timeout.toDouble()==timeout.toLong().toDouble() && timeout.toLong() in 0..Int.MAX_VALUE.toLong()){ "timeoutMs must be a nonnegative integer" }
        synchronized(lock){check(!closed && operation?.cancelled?.get()!=true){"Shell execution is closed"};if(!acquire())throw LimitReached()}
        var entry:Entry?=null
        try {
            val config=ShellLauncher.Options(root=root,adb=shizuku,pty=pty,env=environment,cwd=directory.canonicalPath)
            val process=ShellLauncher.startCommand(command,config)
            val created=Entry("host-shell-${UUID.randomUUID()}",process,command,config.mode);entry=created;operation?.entry=created
            synchronized(lock){
                if(closed || operation?.cancelled?.get()==true){created.close();throw IllegalStateException("Shell execution ended during process creation")}
                processes[created.id]=created
                created.resource=registry.register(NodeBridgeResourceType.SHELL_EXECUTION,created.id,mapOf("mode" to config.mode,"command" to command.take(80)),resource=created){created.close()}
                created.start(timeout.toLong())
            }
            return created
        } catch(e:Throwable){if(entry==null)release() else entry.close();throw e}
    }
    private fun collect(entry:Entry, options:JSONObject):JSONObject {
        val max=NodeBridgeShellOutput.limit(options.opt("maxOutputBytes")) ?: 1024*1024
        val out=ByteArrayOutputStream();val err=ByteArrayOutputStream();var truncated=false
        try {
            while(true){
                val batch=entry.drain()
                if(batch.length()==0 && entry.ended.get())throw java.util.concurrent.CancellationException("Shell execution was closed")
                for(i in 0 until batch.length()){
                    val event=batch.getJSONObject(i)
                    when(event.getString("type")){
                        "stdout","stderr" -> {
                            val bytes=Base64.decode(event.getString("data"),Base64.DEFAULT);val target=if(event.getString("type")=="stdout")out else err
                            val n=minOf(bytes.size,(max-out.size()-err.size()).coerceAtLeast(0));target.write(bytes,0,n);if(n<bytes.size)truncated=true
                        }
                        "exit" -> {
                            val message=if(event.getBoolean("timedOut"))"\nAutoJs6 shell command timed out after ${options.optLong("timeoutMs",30000)}ms."else ""
                            val text=NodeBridgeShellOutput.bound(out.toString("UTF-8"),err.toString("UTF-8")+message,max)
                            return JSONObject().put("code",event.getInt("code")).put("stdout",text.first).put("stderr",text.second)
                                .put("timedOut",event.getBoolean("timedOut")).put("truncated",truncated)
                        }
                    }
                }
            }
        }finally{entry.close()}
    }
    override fun close(){val copy=synchronized(lock){if(closed)return;closed=true;processes.values.concurrentSnapshot()};copy.forEach{it.close()};timers.shutdownNow()}

    companion object { private val permissionCodes=java.util.concurrent.atomic.AtomicInteger(26000) }

    private inner class Entry(val id:String,val process:Process,val command:String,val mode:String):Closeable {
        val ended=AtomicBoolean(false)
        val queueLock=Object();val queue=ArrayDeque<JSONObject>()
        private val writeLock=Any()
        @Volatile var code:Int?=null
        @Volatile var timedOut=false
        @Volatile var terminating=false
        var resource:NodeBridgeResourceHandle?=null
        var timeout:java.util.concurrent.ScheduledFuture<*>?=null
        fun record()=JSONObject().put("id",id).put("pid",ProcessTree.pidOf(process)).put("command",command).put("mode",mode)
        fun isAlive()=runCatching{process.exitValue();false}.getOrDefault(true)
        fun offer(event:JSONObject){synchronized(queueLock){while(queue.size>=32&&!ended.get())queueLock.wait();if(!ended.get()){queue.addLast(event);queueLock.notifyAll()}}}
        fun start(timeoutMs:Long){
            if(timeoutMs>0)timeout=timers.schedule({terminate(true)},timeoutMs,TimeUnit.MILLISECONDS)
            fun pump(input:java.io.InputStream,type:String)=thread(name="AutoJs6Shell-$type",isDaemon=true){
                try{input.use{val bytes=ByteArray(4096);while(!ended.get()){val n=it.read(bytes);if(n<0)break;offer(JSONObject().put("type",type).put("data",Base64.encodeToString(bytes,0,n,Base64.NO_WRAP)))}}}
                catch(e:Exception){if(!ended.get() && !terminating && isAlive())offer(JSONObject().put("type","error").put("message",e.message.orEmpty()))}
                finally{offer(JSONObject().put("type","end").put("stream",type))}
            }
            val out=pump(process.inputStream,"stdout");val err=pump(process.errorStream,"stderr")
            thread(name="AutoJs6ShellExit",isDaemon=true){
                try{code=process.waitFor();timeout?.cancel(false);out.join();err.join();offer(JSONObject().put("type","exit").put("code",if(timedOut)124 else code).put("timedOut",timedOut))}
                catch(_:InterruptedException){close()}
            }
        }
        fun drain():JSONArray {
            val result=JSONArray();var finished=false
            synchronized(queueLock){
                if(queue.isEmpty()&&!ended.get())queueLock.wait(250)
                while(queue.isNotEmpty()&&result.length()<8){val event=queue.removeFirst();result.put(event);if(event.optString("type")=="exit")finished=true}
                queueLock.notifyAll()
            }
            if(finished)close()
            return result
        }
        fun write(bytes:ByteArray){check(!ended.get()){ "Shell process is closed" };synchronized(writeLock){process.outputStream.write(bytes);process.outputStream.flush()}}
        fun closeInput(){synchronized(writeLock){process.outputStream.close()}}
        fun terminate(expired:Boolean){if(isAlive()){terminating=true;if(expired)timedOut=true;ProcessTree.terminate(process)}}
        override fun close(){
            if(!ended.compareAndSet(false,true))return
            timeout?.cancel(false);synchronized(queueLock){queue.clear();queueLock.notifyAll()}
            ProcessTree.terminate(process)
            runCatching{ProcessTree.waitFor(process,500)};runCatching{code=process.exitValue()}
            runCatching{process.outputStream.close()};runCatching{process.inputStream.close()};runCatching{process.errorStream.close()}
            processes.remove(id,this);resource?.release("shell.closed");release()
        }
    }
}
