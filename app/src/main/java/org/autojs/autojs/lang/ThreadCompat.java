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
        super(target);
    }

    public ThreadCompat(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public ThreadCompat(String name) {
        super(name);
    }

    public ThreadCompat(ThreadGroup group, String name) {
        super(group, name);
    }

    public ThreadCompat(Runnable target, String name) {
        super(target, name);
    }

    public ThreadCompat(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public ThreadCompat(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
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
