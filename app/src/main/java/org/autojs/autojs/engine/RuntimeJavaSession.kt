package org.autojs.autojs.engine

import android.content.Context
import android.os.Looper
import dalvik.system.DexClassLoader
import org.autojs.autojs.rhino.ClassFileDexer
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Execution-local class loading and return-valued callbacks, shared by all three JS transports. */
internal class RuntimeJavaSession(
    private val context: Context,
    private val cwd: String,
    private val handles: ControlledJavaInteropPolicy.Handles,
    private val release: (String) -> Unit,
) : Closeable {
    private val executor = Executors.newCachedThreadPool { task -> Thread(task, "autojs-java-bridge").apply { isDaemon = true } }
    private val active = AtomicInteger()
    @Volatile private var closed = false
    private val loaderLock = Any()
    @Volatile private var latestLoader = RuntimeJavaSession::class.java.classLoader!!
    private var cache: File? = null
    private val registrations = ConcurrentHashMap<String, Registration>()
    private val owners = ConcurrentHashMap<String, String>()
    private val definitions = ConcurrentHashMap<Class<*>, Definition>()
    private val frames = ConcurrentHashMap<String, Frame>()
    private val events = ConcurrentHashMap<String, LinkedBlockingQueue<JSONObject>>()
    private val nativeDepth = ThreadLocal.withInitial { 0 }
    private data class Definition(val type: Class<*>, val methods: List<Method>, val registration: Registration)
    private data class Registration(val id: String, val client: String, val keys: Set<String>, val timeout: Long,
                                    @Volatile var closed: Boolean = false)
    private data class CallbackReply(val value:Any?=null,val error:String?=null)
    private data class Reentry(val run: () -> Unit, val cancel: () -> Unit)
    private data class Frame(val id: String, val registration: Registration, val receiver: Any, val method: Method,
                             val index: Int, val main: Boolean, val result: CompletableFuture<CallbackReply> = CompletableFuture(),
                             val reentry: LinkedBlockingQueue<Reentry> = LinkedBlockingQueue(), val borrowed: MutableList<String> = mutableListOf())

    fun submit(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit, action: () -> NodeBridgeResponse) {
        val payload = JSONObject(JSONArray(request.argsJson).optJSONObject(0)?.toString() ?: "{}")
        val parent = payload.optString("callbackInvocationId").takeIf { it.isNotEmpty() }
        if (parent != null && request.method !in controlMethods) {
            val frame = frames[parent]
            if (frame == null || frame.result.isDone || frame.registration.closed) { callback(failure(request, "Callback invocation is no longer active"));return }
            if (payload.optString("thread") != "main" || frame.main) {
                synchronized(frame) {
                    if(frame.result.isDone)callback(failure(request,"Callback invocation is no longer active"))
                    else {
                        active.incrementAndGet()
                        frame.reentry.offer(Reentry(
                            { try { callback(nativeCall(action)) } finally { finishActive() } },
                            { try { callback(failure(request,"Callback invocation ended before reentry")) } finally { finishActive() } },
                        ))
                    }
                }
                return
            }
        }
        if (closed) { callback(failure(request, "Java execution has finished"));return }
        active.incrementAndGet()
        try { executor.execute {
            try { callback(nativeCall(action)) } catch (error: Throwable) { callback(failure(request, error.toString())) }
            finally { finishActive() }
        }} catch (error: java.util.concurrent.RejectedExecutionException) { active.decrementAndGet();callback(failure(request, "Java execution has finished")) }
    }
    private fun finishActive() { if(active.decrementAndGet()==0 && closed)cleanup() }
    fun <T> nativeCall(action: () -> T): T {
        nativeDepth.set(nativeDepth.get()+1)
        return try { action() } finally { nativeDepth.set(nativeDepth.get()-1) }
    }
    fun loadClass(name: String, loader: String?): Class<*> {
        if (name.endsWith("[]")) return java.lang.reflect.Array.newInstance(loadClass(name.dropLast(2),loader),0).javaClass
        val selected = if (loader == null) latestLoader else handles.resolve(loader) as? ClassLoader ?: error("Class loader handle is unavailable")
        return if (name in setOf("boolean","byte","short","int","long","float","double","char","void")) ControlledJavaInteropPolicy.loadClass(name)
            else Class.forName(name,false,selected)
    }
    fun constructorPrefix(type: Class<*>): Array<Any?> = definitions[type]?.let {
        check(!it.registration.closed) { "Generated class callbacks have been released" };arrayOf(handler(it.registration,it.methods))
    } ?: emptyArray()
    fun describeClass(type:Class<*>):JSONObject {
        val result=JavaInteropClassSpec(type).toJson()
        if(definitions.containsKey(type)) {
            result.put("constructors",JSONArray(type.constructors.map { it.parameterTypes.drop(1).joinToString(",","(",")"){t->t.name} }.sorted()))
            for(key in listOf("methods","instanceMethods")) {
                val original=result.getJSONArray(key)
                result.put(key,JSONArray((0 until original.length()).map { original.getString(it) }.filterNot { it.startsWith("__autojs6_super_") }))
            }
        }
        return result
    }
    fun diagnostics() = mapOf("embedded_script.java.callback_registrations" to registrations.size.toString(),"embedded_script.java.pending_callbacks" to frames.size.toString())
    fun onRelease(handle: String) { owners.remove(handle)?.let(::closeRegistration) }

    fun invoke(method: String, payload: JSONObject): ControlledJavaInteropPolicy.Result = try {
        check(!closed) { "Java execution has finished" }
        val value: Any? = when (method) {
            "loadDex", "loadJar" -> loadFiles(method,payload)
            "proxy", "defineClass" -> define(method,payload)
            "drainCallbacks" -> drain(payload)
            "completeCallback" -> {
                val frame = frames[payload.getString("id")]
                if (frame == null || frame.registration.client != payload.getString("client") || frame.result.isDone) false
                else {
                    val reply=try { if(payload.has("error"))CallbackReply(error=payload.getString("error"))
                        else CallbackReply(value=ControlledJavaInteropPolicy.callbackValue(payload.opt("value"),frame.method.returnType,handles))
                    } catch(error:Throwable) {frame.result.complete(CallbackReply(error=error.toString()));throw error}
                    frame.result.complete(reply)
                }
            }
            "closeCallbacks" -> {
                val registration = registrations[payload.getString("callback")]
                if (registration == null || registration.client != payload.getString("client")) false else {closeRegistration(registration.id);true}
            }
            "retain" -> {
                val value = handles.resolve(payload.getString("handle")) ?: error("Java handle is unavailable")
                handles.register(value,spec(value))
            }
            "callSuper" -> {
                val frame = frames[payload.getString("callbackInvocationId")] ?: error("callSuper requires an active callback")
                val data = JSONObject().put("handle",payload.getString("handle")).put("method",RuntimeJavaSubclassGenerator.superName(frame.index))
                    .put("args",payload.optJSONArray("args") ?: JSONArray()).put("parameterTypes",JSONArray(frame.method.parameterTypes.map { it.typeName }))
                val result = ControlledJavaInteropPolicy.execute("call",data,handles,context)
                result.error?.let { error(it.message) };result.value
            }
            else -> error("Unsupported Java session method: $method")
        }
        ControlledJavaInteropPolicy.Result(value=value)
    } catch (error: Exception) {
        ControlledJavaInteropPolicy.Result(error=ControlledJavaInteropPolicy.ErrorDescriptor(NodeRuntimeErrorCodes.JAVA_CALL_FAILED,error.toString()))
    }
    private fun spec(value: Any) = JavaInteropClassSpec(value.javaClass)
    private fun root(): File = synchronized(loaderLock) {
        check(!closed)
        cache ?: File(context.codeCacheDir,"runtime-java-${UUID.randomUUID()}").canonicalFile.also {
            check(it.parentFile == context.codeCacheDir.canonicalFile && it.mkdirs());cache = it
        }
    }
    private fun readonly(source: File, destination: File): File {
        FileOutputStream(destination).use { output ->
            check(destination.setReadOnly()) { "Cannot mark generated code read-only" }
            source.inputStream().use { it.copyTo(output) }
        }
        return destination
    }
    private fun loadFiles(method: String, payload: JSONObject): JSONObject = synchronized(loaderLock) {
        val input = payload.getJSONArray("paths");require(input.length()>0)
        val directory=File(root(),UUID.randomUUID().toString()).apply { check(mkdirs()) }
        try {
            val paths = (0 until input.length()).map { index ->
                val path=File(input.getString(index)).let { if(it.isAbsolute)it else File(cwd.ifEmpty { context.filesDir.path },it.path) }.canonicalFile
                require(path.isFile && path.canRead()) { "Code file is not readable: $path" }
                val program=File(directory,"program-$index.jar")
                if(method=="loadJar") {
                    ClassFileDexer.convertToDexJar(path,program,directory)
                    readonly(program,File(directory,"code-$index.jar")).also { check(program.delete()) }
                } else {validateDex(path);readonly(path,File(directory,"code-$index.dex"))}
            }
            check(!closed)
            val loader=DexClassLoader(paths.joinToString(File.pathSeparator){it.path},directory.path,null,latestLoader)
            latestLoader=loader
            handles.register(loader,spec(loader)).put("__autojs6JavaLoader",true).put("paths",JSONArray(paths.map { it.path }))
        } catch(error:Throwable) { directory.deleteRecursively();throw error }
    }
    private fun validateDex(file:File) {
        fun magic(input:java.io.InputStream):Boolean {
            val bytes=ByteArray(8);var offset=0
            while(offset<bytes.size) { val count=input.read(bytes,offset,bytes.size-offset);if(count<0)return false;offset+=count }
            return bytes.copyOfRange(0,4).contentEquals(byteArrayOf(100,101,120,10)) && bytes[7]==0.toByte()
        }
        if(file.inputStream().use(::magic))return
        java.util.zip.ZipFile(file).use { archive ->
            val dex=archive.entries().asSequence().filter { it.name.matches(Regex("classes(?:[0-9]+)?\\.dex")) }.toList()
            require(dex.isNotEmpty() && dex.all { archive.getInputStream(it).use(::magic) }) { "loadDex requires DEX bytes or an archive containing classes.dex" }
        }
    }
    private fun define(mode:String,payload:JSONObject):JSONObject = synchronized(loaderLock) {
        val registration=Registration(payload.getString("callback"),payload.getString("client"),
            payload.getJSONArray("methods").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },payload.optLong("callbackTimeoutMs",5000))
        require(registration.timeout in 1..120000 && registration.id.isNotBlank() && registration.client.isNotBlank())
        check(registrations.putIfAbsent(registration.id,registration)==null) { "Duplicate callback registration" }
        try {
            val base=loadClass(payload.optString("extends","java.lang.Object"),payload.optString("loader").takeIf { it.isNotEmpty() })
            val interfaces=payload.optJSONArray("interfaces")?.let { a -> (0 until a.length()).map { loadClass(a.getString(it),null) } } ?: emptyList()
            if(mode=="proxy")require(interfaces.isNotEmpty()) { "A proxy requires at least one interface" }
            val methods=RuntimeJavaSubclassGenerator.methods(base,interfaces,registration.keys)
            val name=payload.optString("name").ifBlank { "org.autojs.runtime.generated.Adapter_${UUID.randomUUID().toString().replace('-','_')}" }
            require(RuntimeJavaSubclassGenerator.validName(name)) { "Invalid generated class name" }
            require(runCatching { loadClass(name,null) }.isFailure) { "Class name is already loaded: $name" }
            val directory=File(root(),UUID.randomUUID().toString()).apply { check(mkdirs()) }
            val jar=File(directory,"adapter.jar")
            ZipOutputStream(jar.outputStream()).use { out -> out.putNextEntry(ZipEntry(name.replace('.','/')+".class"));out.write(RuntimeJavaSubclassGenerator.generate(name,base,interfaces,methods));out.closeEntry() }
            val dex=File(directory,"compiled.jar");ClassFileDexer.convertToDexJar(jar,dex,directory)
            val safe=readonly(dex,File(directory,"code.jar"));check(jar.delete());check(dex.delete());check(!closed)
            val loader=DexClassLoader(safe.path,directory.path,null,latestLoader);val type=loader.loadClass(name);latestLoader=loader
            definitions[type]=Definition(type,methods,registration)
            val value=if(mode=="proxy")type.getConstructor(RuntimeJavaCallbackHandler::class.java).newInstance(handler(registration,methods)) else type
            val result=handles.register(value,spec(value));owners[result.getString("__autojs6JavaHandle")]=registration.id
            if(mode=="defineClass")result.put("__autojs6JavaClass",name)
            result.put("__autojs6JavaCallbackOwner",registration.id)
        } catch(error:Throwable) { closeRegistration(registration.id);throw error }
    }
    private fun handler(registration:Registration,methods:List<Method>) = object:RuntimeJavaCallbackHandler {
        override fun invoke(receiver:Any,method:Int,arguments:Array<Any?>):Any? {
            val member=methods[method]
            if(closed || registration.closed) {
                if(nativeDepth.get()>0)error("Java callback owner is closed")
                return defaultValue(member.returnType)
            }
            val frame=Frame(UUID.randomUUID().toString(),registration,receiver,member,method,Looper.myLooper()==Looper.getMainLooper())
            val borrowed=object:ControlledJavaInteropPolicy.Handles by handles {
                override fun register(value:Any,spec:JavaInteropClassSpec):JSONObject = handles.register(value,spec).also { frame.borrowed.add(it.getString("__autojs6JavaHandle")) }
            }
            frames[frame.id]=frame
            try {
                val receiverHandle=borrowed.register(receiver,spec(receiver))
                events.computeIfAbsent(registration.client){LinkedBlockingQueue()}.offer(JSONObject().put("id",frame.id).put("callback",registration.id)
                    .put("method",member.name).put("signature",RuntimeJavaSubclassGenerator.key(member))
                    .put("key",if("*" in registration.keys)"*" else if(RuntimeJavaSubclassGenerator.key(member) in registration.keys)RuntimeJavaSubclassGenerator.key(member)else member.name)
                    .put("receiver",receiverHandle).put("args",JSONArray(arguments.map { if(it!=null && it.javaClass.isArray)borrowed.register(it,spec(it)) else ControlledJavaInteropPolicy.marshal(it,borrowed) })))
                val deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(registration.timeout)
                while(!frame.result.isDone) {
                    if(closed || registration.closed) return defaultValue(member.returnType)
                    val remaining=deadline-System.nanoTime();if(remaining<=0)throw TimeoutException("JavaScript callback timed out: ${member.name}")
                    frame.reentry.poll(minOf(remaining,TimeUnit.MILLISECONDS.toNanos(10)),TimeUnit.NANOSECONDS)?.run?.invoke()
                }
                val reply=frame.result.get();reply.error?.let { error(it) };return reply.value
            } catch(error:Exception) {
                if((closed || registration.closed) && nativeDepth.get()==0)return defaultValue(member.returnType)
                if(nativeDepth.get()>0)throw IllegalStateException("JavaScript callback ${member.name}: ${error.message}",error)
                android.util.Log.e("RuntimeJavaCallback","Callback ${member.name} failed",error)
                return defaultValue(member.returnType)
            } finally {
                synchronized(frame) {
                    frames.remove(frame.id);frame.result.cancel(false)
                    while(true)(frame.reentry.poll() ?: break).cancel()
                }
                frame.borrowed.forEach(release)
                if(registration.closed && registrations.values.none { it.client==registration.client })events.remove(registration.client)?.clear()
            }
        }
    }
    private fun drain(payload:JSONObject):JSONArray {
        val client=payload.getString("client");val wait=payload.optLong("waitMs",500).coerceIn(0,1000)
        val queue=events[client] ?: if(registrations.values.any { it.client==client })events.computeIfAbsent(client){LinkedBlockingQueue()} else LinkedBlockingQueue();val output=JSONArray()
        val first=queue.poll(wait,TimeUnit.MILLISECONDS)
        if(first!=null && frames[first.optString("id")]?.result?.isDone==false)output.put(first)
        repeat(15) { val next=queue.poll() ?: return@repeat;if(frames[next.optString("id")]?.result?.isDone==false)output.put(next) }
        return output
    }
    private fun closeRegistration(id:String) {
        val registration=registrations.remove(id) ?: return;registration.closed=true
        frames.values.filter { it.registration===registration }.forEach { it.result.cancel(false) }
        if(registrations.values.none { it.client==registration.client })events.remove(registration.client)?.clear()
    }
    override fun close() {
        closed=true;registrations.keys.toList().forEach(::closeRegistration);executor.shutdownNow()
        if(active.get()==0)cleanup()
    }
    private fun cleanup() = synchronized(loaderLock) {
        definitions.clear();owners.clear();events.clear();latestLoader=RuntimeJavaSession::class.java.classLoader!!
        cache?.let { require(it.parentFile==context.codeCacheDir.canonicalFile);it.deleteRecursively() };cache=null
    }
    private fun failure(request:NodeBridgeRequest,message:String)=NodeBridgeResponse.failure(request.id,
        NodeBridgeError.javaInterop(message,NodeRuntimeErrorCodes.JAVA_CALL_FAILED,request.module,request.method))
    companion object {
        val controlMethods=setOf("drainCallbacks","completeCallback","closeCallbacks")
        val sessionMethods=controlMethods+setOf("proxy","defineClass","loadDex","loadJar","callSuper","retain")
        private fun defaultValue(type:Class<*>):Any? = when(type) {
            Boolean::class.javaPrimitiveType->false;Byte::class.javaPrimitiveType->0.toByte();Short::class.javaPrimitiveType->0.toShort()
            Char::class.javaPrimitiveType->0.toChar();Int::class.javaPrimitiveType->0;Long::class.javaPrimitiveType->0L
            Float::class.javaPrimitiveType->0f;Double::class.javaPrimitiveType->0.0;else->null
        }
    }
}
