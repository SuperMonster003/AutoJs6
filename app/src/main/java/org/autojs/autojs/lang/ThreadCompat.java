package org.autojs.autojs.lang;

import org.autojs.autojs.annotation.ScriptInterface;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Created by Stardust on Apr 30, 2017.
 */
public class ThreadCompat extends Thread {

    // FIXME by Stardust on Dec 29, 2017.
    //  ! 是否需要用 synchronizedMap?
    //  ! 这里虽然线程不安全, 但竞争很小.
    //  ! zh-CN (translated by SuperMonster003 on Jul 29, 2024):
    //  ! Is synchronizedMap needed here?
    //  ! Competition is very little although it's thread-unsafe here.
    private static final Set<Thread> interruptedThreads = Collections.newSetFromMap(new WeakHashMap<>());

    public ThreadCompat() {
        /* Empty body. */
    }

    public ThreadCompat(Runnable target) {
        super(releasing(target));
    }

    public ThreadCompat(ThreadGroup group, Runnable target) {
        super(group, releasing(target));
    }

    public ThreadCompat(String name) {
        super(name);
    }

    public ThreadCompat(ThreadGroup group, String name) {
        super(group, name);
    }

    public ThreadCompat(Runnable target, String name) {
        super(releasing(target), name);
    }

    public ThreadCompat(ThreadGroup group, Runnable target, String name) {
        super(group, releasing(target), name);
    }

    public ThreadCompat(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, releasing(target), name, stackSize);
    }

    /**
     * Hands the runnable to Thread through a holder which drops it once it has run. ART does not clear Thread.target when a
     * thread terminates, so an ended thread that is still referenced, for example through the Looper of a Message that was
     * never recycled, kept its runnable (a script execution) and the whole ScriptRuntime reachable (docs/memory/ROADMAP.md H21).
     * zh-CN: 经由运行完即丢弃的持有者把 Runnable 交给 Thread. ART 在线程结束时不会清除 Thread.target, 因此仍被引用的已结束线程
     * (例如经由一个未被回收的 Message 的 Looper) 曾让它的 Runnable (脚本执行) 及整个 ScriptRuntime 保持可达 (docs/memory/ROADMAP.md H21).
     */
    private static Runnable releasing(Runnable target) {
        return target == null ? null : new ReleasingTarget(target);
    }

    private static final class ReleasingTarget implements Runnable {

        private volatile Runnable mTarget;

        ReleasingTarget(Runnable target) {
            mTarget = target;
        }

        @Override
        public void run() {
            Runnable target = mTarget;
            if (target == null) {
                return;
            }
            try {
                target.run();
            } finally {
                mTarget = null;
            }
        }
    }

    @Override
    public boolean isInterrupted() {
        return super.isInterrupted() || interruptedThreads.contains(this);
    }

    public static boolean interrupted() {
        boolean interrupted = Thread.currentThread().isInterrupted();
        interruptedThreads.remove(Thread.currentThread());
        Thread.interrupted();
        return interrupted;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        interruptedThreads.add(this);
    }

    @ScriptInterface
    public void safeJoin() throws InterruptedException {
        super.join();
    }

    @ScriptInterface
    public void safeJoin(long millis) throws InterruptedException {
        if (millis > 0) {
            super.join(millis);
        }
    }

    @ScriptInterface
    public void safeJoin(long millis, int nanos) throws InterruptedException {
        if (millis > 0) {
            super.join(millis, nanos);
        }
    }

}
