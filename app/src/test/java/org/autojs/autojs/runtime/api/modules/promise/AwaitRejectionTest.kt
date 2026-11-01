package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.promise.PromiseHarness.Impl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * docs/api-coverage/ROADMAP.md Q205: `promise.await()`, `flow.await()` (`kotlinAwait`, through [PromiseSuspension]) and
 * `continuation.await(promise)` throw the rejection at the call in a coroutine, as `await` does in Node and Bun. They handed
 * it back as their value since 6.3.4. What no script catches is an uncaught error of the script, not a warning about an
 * unhandled rejection, and an error after a finished `await()` no longer resumes the same coroutine a second time.
 *
 * zh-CN: 协程中 promise.await(), flow.await() (kotlinAwait, 经 [PromiseSuspension]) 与 continuation.await(promise) 在调用处抛出拒绝, 与 Node / Bun 的 await 相同;
 * 6.3.4 起它们曾把拒绝作为返回值交回. 脚本未捕获的错误是脚本的未捕获错误, 不只是一条未处理拒绝的警告; await() 完成之后的错误也不再二次恢复同一协程.
 */
class AwaitRejectionTest {

    private fun run(source: String, impl: Impl = Impl.KOTLIN_POSTED) = PromiseHarness(impl).use { it.run(source.trimIndent()).joinToString(" ; ") }

    private val forms = listOf("p.await()", "kotlinAwait(p)", "continuation.await(p)")

    @Test
    fun everyAwaitThrowsTheErrorAtTheCall() = forms.forEach { form ->
        Impl.entries.forEach { impl ->
            assertEquals(form, "(suspended) ; caught RangeError boom true ; after", run("""
                var p = Promise.reject(new RangeError('boom')), value = 'kept';
                try { value = $form; log('no'); } catch (e) { log('caught ' + e.name + ' ' + e.message + ' ' + (value === 'kept')); }
                log('after');
            """, impl))
        }
    }

    @Test
    fun aReasonThatIsNotAnErrorIsThrownAsWell() = forms.forEach { form ->
        listOf("'text'" to "string:text", "7" to "number:7", "false" to "boolean:false", "null" to "object:null", "undefined" to "undefined:undefined").forEach { (reason, expected) ->
            assertEquals("$form $reason", "(suspended) ; $expected", run("""
                var p = Promise.reject($reason);
                try { $form; log('no'); } catch (e) { log(typeof e + ':' + e); }
            """))
        }
    }

    @Test
    fun aLaterRejectionThrowsAfterTheScriptSuspended() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; caught late", run("""
            var p = new Promise((_, reject) => setTimeout(() => reject('late'), 5));
            try { $form; log('no'); } catch (e) { log('caught ' + e); }
        """))
    }

    @Test
    fun aRejectionFromAnotherThreadThrowsOnTheScriptThread() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; caught from other on main", run("""
            var p = new Promise((_, reject) => otherThread(() => reject('from other')));
            try { $form; log('no'); } catch (e) { log('caught ' + e + ' on ' + where()); }
        """))
    }

    @Test
    fun theHandlersOfTheCallersRun() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; finally inner ; finally outer ; caught x", run("""
            var p = Promise.reject('x');
            function inner() { try { return $form + 1; } finally { log('finally inner'); } }
            function outer() { try { return inner() + 1; } finally { log('finally outer'); } }
            try { outer(); } catch (e) { log('caught ' + e); }
        """))
    }

    @Test
    fun theScriptGoesOnAwaitingAfterCatching() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; error 0 ; ok 1 ; error 2 ; done", run("""
            for (var i = 0; i < 3; i++) {
                var p = i % 2 ? Promise.resolve(i) : Promise.reject(i);
                try { log('ok ' + $form); } catch (e) { log('error ' + e); }
            }
            log('done');
        """))
    }

    @Test
    fun aResolvedAwaitStaysAsItWas() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; value 5 ; undefined", run("""
            var p = Promise.resolve(5);
            log('value ' + $form);
            p = Promise.resolve();
            log(String($form));
        """))
    }

    @Test
    fun whatNoScriptCatchesIsAnUncaughtErrorOfTheScript() = forms.forEach { form ->
        assertEquals(form, "(suspended) ; task error: JavaScriptException Error: lost", run("""
            var p = Promise.reject(new Error('lost'));
            log('value ' + $form);
            log('not reached');
        """))
    }

    @Test
    fun anErrorAfterAFinishedAwaitDoesNotResumeItAgain() = forms.forEach { form ->
        // The script threw out of `then(resume)` into `catch(resumeError)`, so everything after the await ran a second time.
        // zh-CN: 脚本的错误从 then(resume) 抛入 catch(resumeError), await 之后的全部代码因此再运行一遍.
        assertEquals(form, "(suspended) ; got 1 ; task error: JavaScriptException Error: after", run("""
            var p = Promise.resolve(1);
            log('got ' + $form);
            throw new Error('after');
        """))
    }

    @Test
    fun aRejectionDoesNotReachTheThenOfTheAwait() = assertEquals("(suspended) ; caught x ; caught y", run("""
        function fails(reason) { return Promise.reject(reason); }
        try { fails('x').await(); } catch (e) { log('caught ' + e); }
        try { fails('y').await(); } catch (e) { log('caught ' + e); }
    """))

    @Test
    fun anAwaitThatCannotSuspendLeavesTheScriptRunning() = forms.forEach { form ->
        // A reaction of the Promise is no place to suspend: the caller gets the exception, and the resume that follows has no
        // coroutine to resume, which must not end the script as an uncaught error.
        // zh-CN: Promise 的反应不能挂起: 调用方收到异常, 随后的恢复没有协程可恢复, 不能当作未捕获错误结束脚本.
        assertEquals(form, "cannot suspend ; still running", run("""
            Promise.resolve().then(() => {
                var p = Promise.resolve(1);
                try { $form; log('no'); } catch (e) { log('cannot suspend'); }
            });
            console.warn = function () {};
            setTimeout(() => log('still running'), 5);
        """))
    }

    @Test
    fun resumeErrorThrowsAtCreatorAwait() = assertEquals("(suspended) ; caught failed ; done", run("""
        var cont = continuation.create();
        setTimeout(() => cont.resumeError('failed'), 1);
        try { cont.await(); log('no'); } catch (e) { log('caught ' + e); }
        log('done');
    """))

}
