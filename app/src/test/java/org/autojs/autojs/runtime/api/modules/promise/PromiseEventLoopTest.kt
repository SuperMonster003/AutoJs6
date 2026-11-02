package org.autojs.autojs.runtime.api.modules.promise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap P9.5 of Rhino-For-AutoJs6: the built-in Promise in the event loop of a script. Reactions are microtasks that a
 * thread runs when its stack is empty, an unhandled rejection is reported once and the script goes on (appendix C Q6), a
 * coroutine cannot suspend inside an async function but works with one from outside (D15), and a settlement that no thread
 * would run goes to one that does (Q64, Q65). What needs a real looper (the warning at the end of a script, Q7) is in
 * `RhinoAsyncDeviceTest`.
 *
 * zh-CN: Rhino-For-AutoJs6 ROADMAP P9.5: 内建 Promise 在脚本事件循环中的行为. 反应是线程在调用栈清空时运行的微任务, 未处理的拒绝报告一次且脚本继续 (附录 C Q6),
 * 协程不能在 async 函数内挂起, 但能从外部与之配合 (D15), 无人运行的落定转给能运行的线程 (Q64, Q65). 需要真实 Looper 的部分 (脚本结束时的警告, Q7) 在 `RhinoAsyncDeviceTest`.
 */
class PromiseEventLoopTest {

    private fun run(source: String) = PromiseHarness().use { it.run(source.trimIndent()).joinToString(" ; ") }

    private val suspensions = listOf("p.await()", "kotlinAwait(p)", "continuation.await(p)")

    @Test
    fun reactionsRunBeforeTheNextTimerCallback() = assertEquals("sync ; micro ; first ; micro of first ; second", run("""
        setTimeout(() => { log('first'); Promise.resolve().then(() => log('micro of first')); }, 1);
        setTimeout(() => log('second'), 1);
        Promise.resolve().then(() => log('micro'));
        log('sync');
    """))

    @Test
    fun awaitOfAFunctionRunsBeforeTheNextTimerCallback() = assertEquals("sync ; after await ; timeout", run("""
        setTimeout(() => log('timeout'), 0);
        (async function () { await null; log('after await'); })();
        log('sync');
    """))

    @Test
    fun aTopCallInsideATaskRunsNoMicrotasks() = assertEquals("a ; helper ; b ; other", run("""
        // Forwarded to the main thread, the rest of `f` runs outside a top call, so `helper` is a top call of its own: a checkpoint
        // at its end would run the reaction of `then` in the middle of `f`.
        var settle, p = new Promise(r => settle = r);
        function helper() { log('helper'); }
        async function f() { await p; log('a'); helper(); log('b'); }
        f();
        p.then(() => log('other'));
        setTimeout(() => otherThread(() => settle()), 1);
    """))

    @Test
    fun anUnhandledRejectionIsReportedOnceWithItsStackAndTheScriptGoesOn() = PromiseHarness().use { h ->
        h.run("""
            function boom() { throw new RangeError('lost'); }
            Promise.resolve().then(boom);
            setTimeout(() => log('still running'), 1);
        """.trimIndent())
        assertEquals(listOf("unhandled RangeError: lost", "still running"), h.log)
        assertEquals(1, h.unhandledReports.size)
        assertTrue(h.unhandledReports[0], "main.js:1" in h.unhandledReports[0])
    }

    @Test
    fun anErrorOfAnAsyncFunctionIsReportedWithItsLine() = PromiseHarness().use { h ->
        h.run("""
            async function f() {
                await null;
                undefinedName;
            }
            f();
        """.trimIndent())
        assertEquals(1, h.unhandledReports.size)
        assertTrue(h.unhandledReports[0], h.unhandledReports[0].startsWith("ReferenceError: "))
        assertTrue(h.unhandledReports[0], "main.js:3" in h.unhandledReports[0])
    }

    @Test
    fun aReasonOfAnyKindIsReported() = assertEquals("unhandled text ; unhandled 7 ; unhandled null ; unhandled undefined ; unhandled [object Object]", run("""
        Promise.reject('text');
        Promise.reject(7);
        Promise.reject(null);
        Promise.reject();
        Promise.reject({});
    """))

    @Test
    fun aReasonThatFailsToPrintDoesNotStopTheScript() = assertEquals("unhandled NativeObject ; still running", run("""
        Promise.reject({ toString() { throw new Error('no'); } });
        setTimeout(() => log('still running'), 1);
    """))

    @Test
    fun whatTheTurnHandlesIsNotReported() = assertEquals("handled a ; handled b ; handled c", run("""
        var p = Promise.reject('a');
        p.catch(e => log('handled ' + e));
        (async function () { try { await Promise.reject('b'); } catch (e) { log('handled ' + e); } })();
        Promise.reject('c').then(() => {}).catch(e => log('handled ' + e));
    """))

    @Test
    fun waitRunsReactionsAndLeavesTheReportToTheEndOfTheTurn() = assertEquals("n 1 ; handled late", run("""
        // wait() runs the reactions it needs, inside a turn that may still handle a rejection.
        var n = 0, p = Promise.reject('late');
        Promise.resolve().then(() => n++);
        Promise.resolve().wait();
        log('n ' + n);
        p.catch(e => log('handled ' + e));
    """))

    @Test
    fun waitInsideAReaction() = assertEquals("sync ; next ; inner 2", run("""
        Promise.resolve().then(() => log('inner ' + Promise.resolve(2).wait()));
        Promise.resolve().then(() => log('next'));
        log('sync');
    """))

    @Test
    fun aCoroutineThatSuspendsAgainLeavesTheOtherReactionsQueued() = assertEquals("other ; first 1 ; second 2 ; third", run("""
        Promise.resolve().then(() => log('other'));
        log('first ' + Promise.resolve(1).await());
        log('second ' + new Promise(r => setTimeout(() => r(2), 1)).await());
        Promise.resolve().then(() => log('third'));
    """))

    @Test
    fun aSuspensionInAnAsyncFunctionIsAReadableErrorOnce() = suspensions.forEach { form ->
        assertEquals(form, "TypeError: $CANNOT_SUSPEND ; still running", run("""
            async function f() { var p = Promise.resolve(1); $form; log('no'); }
            f().catch(e => log(e.name + ': ' + e.message));
            setTimeout(() => log('still running'), 5);
        """))
    }

    @Test
    fun aSuspensionInAnAsyncFunctionIsCaughtInIt() = suspensions.forEach { form ->
        assertEquals(form, "caught TypeError ; after", run("""
            async function f() {
                var p = Promise.resolve(1);
                try { $form; log('no'); } catch (e) { log('caught ' + e.name); }
                await null;
                log('after');
            }
            f();
        """))
    }

    @Test
    fun aSuspensionNothingCatchesIsReportedOnce() = suspensions.forEach { form ->
        assertEquals(form, "unhandled TypeError: $CANNOT_SUSPEND", run("""
            (async function () { var p = new Promise(r => setTimeout(r, 1)); $form; })();
        """))
    }

    @Test
    fun theResumeOfAFailedSuspensionDoesNothing() = assertEquals("caught TypeError ; still running", run("""
        (async function () {
            var cont = continuation.create();
            setTimeout(() => cont.resume(1), 1);
            otherThread(() => cont.resume(2));
            try { cont.await(); } catch (e) { log('caught ' + e.name); }
        })();
        setTimeout(() => log('still running'), 5);
    """))

    @Test
    fun aCoroutineAwaitsAnAsyncFunction() = suspensions.forEach { form ->
        assertEquals(form, "got 2 ; caught no ; generator 1", run("""
            async function f(v) { await null; return v + 1; }
            async function g() { await null; throw 'no'; }
            async function* h() { await null; yield 1; }
            var p = f(1);
            log('got ' + $form);
            p = g();
            try { $form; } catch (e) { log('caught ' + e); }
            p = h().next();
            log('generator ' + $form.value);
        """))
    }

    @Test
    fun anAsyncFunctionAwaitsAPromiseOfTheHost() = assertEquals("timer 1 ; other 2 on main ; value 3", run("""
        (async function () {
            log('timer ' + await new Promise(r => setTimeout(() => r(1), 1)));
            log('other ' + await new Promise(r => otherThread(() => r(2))) + ' on ' + where());
            log('value ' + await 3);
        })();
    """))

    @Test
    fun aJavaFutureIsNoThenable() = assertEquals("java.util.concurrent.CompletableFuture", run("""
        (async function () {
            var future = java.util.concurrent.CompletableFuture.completedFuture(1);
            log(String((await future).getClass().getName()));
        })();
    """))

    @Test
    fun aSettlementOnAThreadWithoutATimerRunsOnTheMainThread() = assertEquals("then v on main ; await v on main", run("""
        var p = new Promise(r => setTimeout(() => otherThread(() => r('v')), 1));
        p.then(v => log('then ' + v + ' on ' + where()));
        (async function () { log('await ' + await p + ' on ' + where()); })();
    """))

    @Test
    fun anAsyncFunctionOnAThreadWithoutATimer() = assertEquals("start on other ; next on other ; ended on main", run("""
        var pending = new Promise(r => setTimeout(r, 1));
        otherThread(async function () {
            log('start on ' + where());
            await null;
            log('next on ' + where());
            await pending;
            log('ended on ' + where());
        });
    """))

    @Test
    fun aSettlementOutsideATopCallWaitsForTheNextTurn() = assertEquals("sync ; then undefined", run("""
        var settle, p = new Promise(r => settle = r);
        p.then(v => log('then ' + v));
        outsideTopCall(settle);
        log('sync');
    """))

    companion object {
        private const val CANNOT_SUSPEND = "A coroutine cannot suspend here. Use the await keyword in an async function, and then() in a callback of a Promise"
    }

}
