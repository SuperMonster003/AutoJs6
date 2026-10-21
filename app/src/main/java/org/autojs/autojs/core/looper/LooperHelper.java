package org.autojs.autojs.core.looper;

import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import static org.autojs.autojs.util.RhinoUtils.isMainThread;

/**
 * Created by Stardust on Dec 27, 2017.
 */
public class LooperHelper {

    // Threads prepared through this class and their loopers. Keys and values are both weak: a Looper references its own thread,
    // so a weak key alone would never be cleared, and an entry left behind by an exit path which never calls quitForThread
    // would keep the thread, its MessageQueue and everything posted or registered on it (a whole ScriptRuntime) reachable
    // for the lifetime of the process (docs/memory/ROADMAP.md H1). A live thread keeps its Looper reachable through
    // its own thread-local storage, so a live entry is never cleared early.
    // zh-CN: 经本类准备过 Looper 的线程及其 Looper. 键与值都是弱引用: Looper 引用自己的线程, 只有弱键的话条目永远不会被清除;
    // 而任何未调用 quitForThread 的退出路径留下的条目, 都会让该线程, 它的 MessageQueue 以及其上投递或登记的一切 (整个 ScriptRuntime)
    // 在进程生命周期内一直可达 (docs/memory/ROADMAP.md H1). 存活的线程经自身的线程局部存储保持 Looper 可达, 因此存活线程的条目不会被提前清除.
    private static final Map<Thread, WeakReference<Looper>> sLoopers = new WeakHashMap<>();

    public static void prepare() {
        if (isMainThread()) {
            return;
        }
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Looper l = Looper.myLooper();
        if (l != null) {
            synchronized (sLoopers) {
                sLoopers.put(Thread.currentThread(), new WeakReference<>(l));
            }
        }
    }

    public static void quitForThread(Thread thread) {
        Looper looper;
        synchronized (sLoopers) {
            WeakReference<Looper> ref = sLoopers.remove(thread);
            looper = ref != null ? ref.get() : null;
        }
        if (looper != null && looper != Looper.getMainLooper()) {
            looper.quitSafely();
        }
    }

    public static void quit(Looper looper) {
        if (looper != null && looper != Looper.getMainLooper()) {
            // Remove the entry of this looper as well, so that quitting by looper is as complete as quitting by thread.
            // zh-CN: 同时移除该 Looper 的条目, 使按 Looper 退出与按线程退出同样彻底.
            synchronized (sLoopers) {
                Thread thread = looper.getThread();
                WeakReference<Looper> ref = sLoopers.get(thread);
                if (ref != null && ref.get() == looper) {
                    sLoopers.remove(thread);
                }
            }
            looper.quitSafely();
        }
    }

    public static boolean contains(Thread thread) {
        synchronized (sLoopers) {
            return sLoopers.containsKey(thread);
        }
    }

    /**
     * Threads with a looper prepared here and not yet quit or collected, for the memory gate (docs/memory/ROADMAP.md P4.2).
     * zh-CN: 经本类准备 Looper 且尚未退出或被回收的线程数, 供内存门禁使用 (docs/memory/ROADMAP.md P4.2).
     */
    public static int size() {
        synchronized (sLoopers) {
            return sLoopers.size();
        }
    }

}
