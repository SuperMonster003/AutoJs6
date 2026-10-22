package org.autojs.autojs.core.plugin

import android.os.IBinder
import android.os.IInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import sun.misc.Unsafe
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * JVM stand-in for the remote side of a plugin session: the android.jar of unit tests has no working Binder, so the AIDL
 * interface and its [binder] are dynamic proxies that record the calls they receive in [calls] (`binder.` marks the calls
 * on the Binder; String arguments are kept, as in `cancelScript(id)`).
 * zh-CN: 插件会话远端的 JVM 替身: 单元测试的 android.jar 没有可用的 Binder, 因此 AIDL 接口及其 [binder] 都是动态代理, 收到的调用记录在 [calls]
 * (`binder.` 表示 Binder 上的调用; 保留 String 参数, 如 `cancelScript(id)`).
 */
internal class FakeRemote(
    private val descriptor: String? = null,
    private val localInterface: () -> IInterface? = { null },
) {
    val calls = CopyOnWriteArrayList<String>()

    val binder: IBinder = recording(IBinder::class.java, "binder.") { name ->
        when (name) {
            "isBinderAlive", "pingBinder" -> true
            "getInterfaceDescriptor" -> descriptor
            "queryLocalInterface" -> localInterface()
            else -> null
        }
    }

    fun <I : IInterface> remote(type: Class<I>): I = recording(type, "") { name -> if (name == "asBinder") binder else null }

    private fun <I> recording(type: Class<I>, prefix: String, answer: (String) -> Any?): I =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, args ->
            when {
                method.declaringClass == Any::class.java -> when (method.name) {
                    "equals" -> self === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(self)
                    else -> "${type.simpleName}@fake"
                }
                else -> {
                    val strings = args.orEmpty().filterIsInstance<String>()
                    if (method.name != "asBinder") calls += prefix + method.name + if (strings.isEmpty()) "" else strings.joinToString(",", "(", ")")
                    answer(method.name) ?: defaultOf(method.returnType)
                }
            }
        })

    private fun defaultOf(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }
}

internal fun registeredLease(onUnbind: () -> Unit = {}): ServiceBindingLease =
    ServiceBindingLease(onUnbind).apply { markRegistered() }

/** An instance made without running its constructor, for the Android types the opening calls only carry. zh-CN: 不运行构造函数而创建的实例, 用于打开调用只携带而不使用的 Android 类型. */
internal fun <T> allocate(type: Class<T>): T = type.cast(unsafe.allocateInstance(type))

private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
    isAccessible = true
    get(null) as Unsafe
}

/**
 * Runs [open] through [AidlPluginHost.withContextAbandoningLate] on `Dispatchers.IO` as the plugin hosts do, lets it answer only
 * after the caller was cancelled (the script stopped while the session opened) and waits until [abandon] is done with it.
 * zh-CN: 像插件宿主那样经 [AidlPluginHost.withContextAbandoningLate] 在 `Dispatchers.IO` 上运行 [open], 让它在调用方被取消之后
 * (会话打开期间脚本已停止) 才应答, 并等待 [abandon] 处理完毕.
 */
internal fun <T> openAfterTheCallerWasCancelled(abandon: (T) -> Unit, open: suspend CoroutineScope.() -> T) {
    val calling = CountDownLatch(1)
    val answer = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    runBlocking {
        val job = launch(Dispatchers.Default) {
            try {
                AidlPluginHost.withContextAbandoningLate(Dispatchers.IO, abandon) {
                    calling.countDown()
                    answer.await()
                    open()
                }
            } catch (e: Throwable) {
                failure.set(e)
                throw e
            }
        }
        assertTrue(calling.await(10, TimeUnit.SECONDS))
        job.cancel()
        answer.countDown()
        job.join()
    }
    assertTrue(failure.get() is CancellationException)
}
