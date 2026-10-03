package org.autojs.autojs.engine;

import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.execution.ScriptExecutionListener;
import org.autojs.autojs.script.ScriptSource;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ScriptExecutionRegistrationTest {
    private Map<Integer, ScriptExecution> registry() {
        return Collections.synchronizedMap(new LinkedHashMap<>());
    }

    @Test
    public void activityLookupCanRunBeforeTheBackgroundStartCallReturns() throws Exception {
        var executions = registry();
        var execution = new Execution(17);
        var requested = new CountDownLatch(1);
        var activityRead = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var result = new AtomicReference<ScriptExecution>();
        var ioCaller = new Thread(() -> {
            try {
                result.set(ScriptEngineService.startRegisteredExecution(executions, execution, () -> {
                    assertFalse("A main-thread lookup must not wait for the caller's table lock", Thread.holdsLock(executions));
                    requested.countDown();
                    try {
                        assertTrue(activityRead.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(error);
                    }
                    return true;
                }));
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "inrt-background-launch");
        ioCaller.start();
        try {
            assertTrue("Android launch has been requested", requested.await(5, TimeUnit.SECONDS));
            // Simulates Activity.onCreate on main while the Dispatchers.IO start call is pending.
            assertSame(execution, executions.get(execution.getId()));
            assertNull("The start call has not returned yet", result.get());
            activityRead.countDown();
            ioCaller.join(5000);
            assertFalse(ioCaller.isAlive());
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertSame(execution, result.get());
        } finally {
            activityRead.countDown();
            ioCaller.join(5000);
        }
    }

    @Test
    public void aRejectedActivityLaunchRemovesItsPublishedExecution() {
        var executions = registry();
        var execution = new Execution(23);
        assertSame(execution, ScriptEngineService.startRegisteredExecution(executions, execution, () -> {
            assertSame(execution, executions.get(23));
            return false;
        }));
        assertTrue(executions.isEmpty());
    }

    @Test
    public void aThrowingStarterRollsBackAndPreservesTheOriginalFailure() {
        var executions = registry();
        var execution = new Execution(31);
        var failure = new IllegalStateException("Thread could not start");
        try {
            ScriptEngineService.startRegisteredExecution(executions, execution, () -> {
                assertSame(execution, executions.get(31));
                throw failure;
            });
            fail("The launch error must propagate");
        } catch (IllegalStateException actual) {
            assertSame(failure, actual);
        }
        assertTrue(executions.isEmpty());
    }

    @Test
    public void finishingBeforeStartReturnsDoesNotResurrectTheExecution() {
        var executions = registry();
        var execution = new Execution(47);
        ScriptEngineService.startRegisteredExecution(executions, execution, () -> {
            assertSame(execution, executions.remove(47));
            return true;
        });
        assertTrue("There must be no second registration after a fast worker/Activity finishes", executions.isEmpty());
    }

    @Test
    public void rejectedStartOnlyRemovesTheSamePublishedObject() {
        var executions = registry();
        var rejected = new Execution(53);
        var replacement = new Execution(53);
        ScriptEngineService.startRegisteredExecution(executions, rejected, () -> {
            executions.put(53, replacement);
            return false;
        });
        assertSame(replacement, executions.get(53));
    }

    private static final class Execution implements ScriptExecution {
        private final int id;
        private Execution(int id) { this.id = id; }
        @Override public ScriptEngine<? extends ScriptSource> getEngine() { return null; }
        @Override public ScriptSource getSource() { return null; }
        @Override public ScriptExecutionListener getListener() { return null; }
        @Override public ExecutionConfig getConfig() { return null; }
        @Override public int getId() { return id; }
        @Override public boolean equals(Object other) { return other instanceof Execution execution && id == execution.id; }
        @Override public int hashCode() { return id; }
    }
}
