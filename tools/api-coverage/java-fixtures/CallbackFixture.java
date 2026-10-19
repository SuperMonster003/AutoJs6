package org.autojs.fixture.runtime;

import java.util.Comparator;
import java.util.concurrent.Callable;

public abstract class CallbackFixture {
    public final String seed;
    public final String constructed;
    protected CallbackFixture(String value) { seed = value; constructed = decorate(value); }
    public abstract String decorate(String value);
    public String greet(String value) { return seed + ":" + value; }
    protected long twice(long value) { return value * 2; }
    public long callProtected(long value) { return twice(value); }
    public final int fixed() { return 7; }
    public static String run(Runnable callback) { callback.run(); return "ran"; }
    public static int compare(Comparator<String> callback) { return callback.compare("a", "b"); }
    public static long callLong(LongCallback callback, long value) { return callback.apply(value); }
    public static String callOnThread(Callable<String> callback) throws Exception {
        final String[] value = new String[1];
        final Throwable[] error = new Throwable[1];
        Thread thread = new Thread(() -> { try { value[0] = callback.call(); } catch (Throwable e) { error[0] = e; } }, "owned-java-callback");
        thread.start(); thread.join(10000);
        if (thread.isAlive()) throw new IllegalStateException("Callback thread did not finish");
        if (error[0] != null) throw new IllegalStateException(error[0]);
        return value[0];
    }
    public static int changeArray(ArrayCallback callback) { int[] values = {1, 2}; callback.change(values); return values[1]; }
    public interface ArrayCallback { void change(int[] values); }
    public interface LongCallback { long apply(long value); }
}
