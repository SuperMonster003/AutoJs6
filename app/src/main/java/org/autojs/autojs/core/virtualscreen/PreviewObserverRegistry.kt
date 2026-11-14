package org.autojs.autojs.core.virtualscreen

/**
 * Thread-safe set of live-preview callbacks for one [VirtualScreenSession].
 *
 * A virtual display has exactly one [android.media.ImageReader] with two buffers, so it must have
 * exactly one [VirtualScreenFramePump] consuming them. Several surfaces may still want the frames at
 * the same time (the full viewer, the home-screen floating window). This registry keeps the callbacks
 * separate from the pump: the session starts the pump on the first subscriber and stops it only when
 * the last one leaves, so closing one surface never blacks out the other.
 *
 * Kept free of Android types so the hand-off can be unit tested on the JVM.
 *
 * zh-CN: 单块虚拟屏的实时预览回调集合 (线程安全). 一块屏只有一个双缓冲 ImageReader, 因此只能有一个帧泵消费,
 * 但完整查看器与首页悬浮小窗可能同时要看画面. 本注册表把「订阅者」与「帧泵」解耦: 第一个订阅者到来时起泵,
 * 最后一个离开时才停泵, 关闭其中一个界面不会让另一个黑屏. 不含 Android 类型, 便于 JVM 单测.
 *
 * Created for the virtual screen floating window on Nov 14, 2026.
 */
internal class PreviewObserverRegistry {

    private val lock = Any()
    private val callbacks = LinkedHashMap<Any, () -> Unit>()

    /** Registers a callback and returns an opaque token to pass to [remove]. */
    fun add(callback: () -> Unit): Any = synchronized(lock) {
        val token = Any()
        callbacks[token] = callback
        token
    }

    /** Returns true when the token was known; unknown or already removed tokens are ignored. */
    fun remove(token: Any): Boolean = synchronized(lock) {
        callbacks.remove(token) != null
    }

    fun clear() = synchronized(lock) {
        callbacks.clear()
    }

    fun size(): Int = synchronized(lock) { callbacks.size }

    /**
     * Invokes a snapshot of the callbacks. Called on the pump's background thread; one failing
     * observer must not stop the others.
     */
    fun dispatch() {
        val snapshot = synchronized(lock) { callbacks.values.toList() }
        snapshot.forEach { runCatching { it() } }
    }

}
