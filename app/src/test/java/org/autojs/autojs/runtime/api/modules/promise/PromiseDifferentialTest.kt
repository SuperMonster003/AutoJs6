package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativePromise

/**
 * The global Promise against `assets/modules/promise.js`, which it replaced (roadmap P4.2, appendix C Q95): each scenario runs
 * in a fresh [PromiseHarness], and the log must be that of the module, recorded before P5.2 removed it
 * ([ScriptModuleExpectations]; the results of docs/augment/P4-2-promise-eval.md §2 pinned them until then), except for the
 * differences in [KNOWN]. Since P9.5 of Rhino-For-AutoJs6 the Promise is the built-in one of Rhino (T12, Q5), so [KNOWN] holds
 * what the standard does differently from the module, and appendix E "Promise (路线 A)" tells them apart. The scenarios are the
 * 23 of docs/augment/P4-2-promise-eval.md §4.1 (A order, B errors, C surface, K coroutines, W wait, T threads) and the D ones
 * of the implementation (replaced `_immediateFn` / `setImmediate` / `_unhandledRejectionFn`, receivers that are not instances,
 * thenables that misbehave, not-a-function messages, many queued reactions and `wait()`, the functions it hands out and error
 * positions).
 *
 * zh-CN: 全局 Promise 对照被它取代的 promise.js (ROADMAP P4.2, 附录 C Q95): 每个场景在新的 [PromiseHarness] 中运行, 日志必须与 P5.2 删除前记录的 promise.js 结果相同
 * ([ScriptModuleExpectations], 此前由评估文档 §2 的结果固定), [KNOWN] 中的差异除外. Rhino-For-AutoJs6 P9.5 起 Promise 是 Rhino 内建的 (T12, Q5), [KNOWN] 记录的是标准与原模块的差异,
 * 各项在附录 E "Promise (路线 A)" 中说明. 场景为评估文档 §4.1 的 23 个与实施时新增的 D 组.
 */
class PromiseDifferentialTest {

    @Test
    fun logsAreThoseOfTheModuleButForTheKnownDifferences() {
        val differences = ArrayList<String>()
        SCENARIOS.forEach { (id, source) ->
            val reference = ScriptModuleExpectations.obsolete("module: $id").replace("(suspended) ; ", "")
            val actual = runOne(source)
            val expected = KNOWN[id] ?: reference
            if (actual != expected) differences += "$id\n  module: $reference\n  expected: $expected\n  actual: $actual"
        }
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    @Test
    fun flowAwaitSuspendsFromKotlin() {
        // Q97: `FlowNativeObject.await` suspends through PromiseSuspension (it worked for the module's Promise as well).
        // zh-CN: Q97: FlowNativeObject.await 经 PromiseSuspension 挂起 (对原模块的 Promise 同样有效).
        assertEquals("kotlin await k ; later 3 ; rejected ; caught Error: no", runOne("""
            log('kotlin await ' + kotlinAwait(Promise.resolve('k')));
            log('later ' + kotlinAwait(new Promise(r => setTimeout(() => r(3), 5))));
            log('rejected');
            try { log('got ' + kotlinAwait(Promise.reject(new Error('no')))); } catch (e) { log('caught ' + e); }
        """))
    }

    @Test
    fun instancesAreNativePromises() {
        // What the Kotlin callers get (RhinoUtils.handleAsyncOperation for images.loadAsync and the http Async functions,
        // images.requestScreenCaptureAsync) is no NativeObject any more: those casts threw a ClassCastException once before (P5.1).
        // zh-CN: Kotlin 调用方得到的不再是 NativeObject (images.loadAsync 与 http 的 Async 函数所用的 RhinoUtils.handleAsyncOperation,
        // images.requestScreenCaptureAsync): 这些强制转换以前就抛过 ClassCastException (P5.1).
        PromiseHarness().use { harness ->
            listOf("new Promise(function () {})", "Promise.resolve(1)", "Promise.resolve(1).then(function (v) { return v; })", "Promise.all([])").forEach { source ->
                val value = harness.eval(source)
                assertTrue("$source: ${value?.javaClass?.name}", value is NativePromise)
            }
        }
    }

    private fun runOne(source: String): String = try {
        PromiseHarness().use { it.run(source.trimIndent()).joinToString(" ; ") }
    } catch (e: Throwable) {
        "harness error: $e"
    }

    companion object {

        /** What differs from the module, by the reason (appendix E "Promise (路线 A)"). */
        val KNOWN = mapOf(
            // Reactions are microtasks in the ticks of the standard (Q5): before the next timer callback, two more ticks to adopt a
            // Promise, the `then` of a thenable in a job of its own. `wait()` has nothing to run but them.
            "A2-vs-timers" to "p1 ; p2 ; timeout0 ; immediate ; timeout-default",
            "A4-adopt-own" to "t1 ; t2 ; t3 ; adopted x ; t4",
            "A5-thenable-sync" to "after resolve ; then called ; value 5",
            "W2-wait-order" to "wait x ; after ; immediate before",
            "D3-replaced-setImmediate" to "then 2 ; timeout path 3",
            // Rejections without a handler are reported to the console (Q6, `unhandled` here) at the end of the turn: what the turn
            // still handles is not reported, and neither `Promise._immediateFn` nor `_unhandledRejectionFn` exists.
            "B5-unhandled" to "handled at once ; unhandled Error: lost ; unhandled Error: late handled ; handled late",
            "D2-replaced-immediateFn" to "then 1 ; unhandled Error: lost ; timeout first",
            "D4-replaced-unhandled" to "unhandled x ; unhandled y",
            "D10-adoption-chains" to "caught adopted ; chain late",
            // The surface of the standard: no plain object between `Promise` and `Function.prototype`, methods that are not
            // enumerable, the tag of the class, `any` and `withResolvers`, the messages of Rhino.
            "B4-self-resolve" to "TypeError: No promise self-resolution",
            "C1-surface-ctor" to "function Promise 1 true function   function function ;  await,catch,constructor,finally,then,wait ; call TypeError \"Constructor Promise\" may only be invoked from a \"new\" expression. ; new 1 TypeError Expecting the first argument to be a function",
            "C2-surface-instance" to " [object Promise] [object Promise] true true",
            "D14-constructor-property-attributes" to "name --C, length --C, arity ---, prototype ---",
            // The statics take an iterable, not an array or anything with a length.
            "C3-statics" to "resolve identity true ; all empty 0 ; all 5 TypeError: Cannot find function Symbol(Symbol.iterator). ; settled null Cannot call method \"Symbol(Symbol.iterator)\" of null ; race {} Cannot find function Symbol(Symbol.iterator). ; all string a,b ; settled [{\"status\":\"rejected\",\"reason\":\"x\"},{\"status\":\"fulfilled\",\"value\":1}] ; race fast ; all rejects first ; all 1,2,3",
            // `allSettled` on a constructor without `resolve` rejects at once, which leaves the rejection of its argument unhandled.
            "D8-statics" to "custom ctor ; resolve 7 ; 2 1 1 0 0 1 1 then ; resolvers 1 1 function ; race replaced 7 ; all thenable threw t ; unhandled 2 ; unhandled TypeError: Expecting the first argument to be a function ; unhandled TypeError: Cannot find function Symbol(Symbol.iterator).",
            // `finally` passes on what is not a function, as `then` does.
            "D5-finally-not-function" to "undefined: undefined",
            // Only an instance has the slots of a Promise, whatever its prototype: the first `then` throws, and an object is not
            // kept by `Promise.resolve` because of its `constructor`.
            "D6-not-an-instance" to "main error: EcmaError TypeError: \"this\" is not an instance of class NativePromise",
            "D7-thenables" to "fake kept false ; getter threw getter ; function value function ; first wins 1 ; then threw before",
            // The constructor of a derived Promise is its `Symbol.species`, not its `constructor` as such.
            "D9-constructor-replaced" to "sub 1 ; plain object ctor 2 ; chained",
            "D12-not-a-function-messages" to "catch on {} TypeError: Cannot find function then. ; then 1 then is not a function, it is number. ; wait on {} Cannot find function then in object [object Object]. ; allSettled TypeError",
            // Resolving functions have no prototype; the constructor cannot be applied to an instance (the module set it up
            // again); its TypeError is at the script line (the module's at line 0).
            "D13-functions-and-positions" to "resolver prototype undefined undefined ; reapply TypeError: \"Constructor Promise\" may only be invoked from a \"new\" expression. ; new 1 at line 5 ; call at line 6 ; kept 1 ; wait threw w at line 0",
            // Q205 of docs/api-coverage/ROADMAP.md: await() throws a rejection at the call (it handed it back as its value after a
            // toast); no `try` is around this one, so the script ends with it.
            "K1-await-basic" to "await 7 ; await later t ; task error: JavaScriptException Error: no",
            // A TypeError that says what to do, once: the resume after it has nothing to resume (P9.5).
            "K6-await-in-then" to "inner await error TypeError: A coroutine cannot suspend here. Use th",
        )

        val SCENARIOS = listOf(
            // ---- A: order of reactions and timers ----
            "A1-then-order" to """
                Promise.resolve(1).then(v => log('then ' + v));
                log('sync');
            """,
            "A2-vs-timers" to """
                setTimeout(() => log('timeout0'), 0);
                Promise.resolve().then(() => log('p1')).then(() => log('p2'));
                setImmediate(() => log('immediate'));
                setTimeout(() => log('timeout-default'));
            """,
            "A3-nested-ticks" to """
                Promise.resolve().then(() => { log('a1'); Promise.resolve().then(() => log('inner')); }).then(() => log('a2'));
                Promise.resolve().then(() => log('b1')).then(() => log('b2'));
            """,
            "A4-adopt-own" to """
                var p = Promise.resolve('x');
                Promise.resolve().then(() => p).then(v => log('adopted ' + v));
                Promise.resolve().then(() => log('t1')).then(() => log('t2')).then(() => log('t3')).then(() => log('t4'));
            """,
            "A5-thenable-sync" to """
                new Promise(r => r({ then(f) { log('then called'); f(5); } })).then(v => log('value ' + v));
                log('after resolve');
            """,
            // ---- B: catch / finally / errors ----
            "B1-catch-finally" to """
                Promise.reject(new Error('e1')).catch(e => { log('caught ' + e.message); return 'r'; }).finally(() => log('fin')).then(v => log('then ' + v));
                Promise.resolve('v').finally(() => { throw new TypeError('in finally'); }).catch(e => log('finally threw ' + e.name));
                Promise.reject('plain').finally(() => 'ignored').catch(e => log('kept ' + e));
            """,
            "B2-executor-throw" to """
                new Promise(() => { throw new RangeError('ex'); }).catch(e => log(e.name + ' ' + e.message));
                new Promise((res) => { res(1); throw new Error('late'); }).then(v => log('kept ' + v));
                new Promise(() => { null.x; }).catch(e => log('typeerror ' + (e instanceof TypeError)));
            """,
            "B3-java-exception" to """
                Promise.resolve().then(() => java.lang.Integer.parseInt('x')).catch(e => log('java ' + (e instanceof Error) + ' ' + String(e).slice(0, 40)));
            """,
            "B4-self-resolve" to """
                var p = new Promise(r => setTimeout(() => r(p), 0)); p.catch(e => log(e.name + ': ' + e.message));
            """,
            "B5-unhandled" to """
                Promise.reject(new Error('lost'));
                var late = Promise.reject(new Error('late handled')); setTimeout(() => late.catch(() => log('handled late')), 5);
                var early = Promise.reject(new Error('handled')); early.catch(() => log('handled at once'));
            """,
            "B6-then-non-function" to """
                Promise.resolve(3).then(null, null).then(5).then(v => log('passed ' + v));
                Promise.reject(4).then(v => log('no')).catch(e => log('passed reject ' + e));
            """,
            // ---- C: surface ----
            "C1-surface-ctor" to """
                log(typeof Promise, Promise.name, Promise.length, Object.getPrototypeOf(Promise) === Function.prototype, typeof Promise.call,
                    Object.keys(Promise).join(), Object.keys(Object.getPrototypeOf(Promise)).join(), typeof Promise.any, typeof Promise.withResolvers);
                log(Object.keys(Promise.prototype).join(), Object.getOwnPropertyNames(Promise.prototype).sort().join());
                try { Promise(() => {}); } catch (e) { log('call', e.name, e.message); }
                try { new Promise(1); } catch (e) { log('new 1', e.name, e.message); }
            """,
            "C2-surface-instance" to """
                var p = new Promise(() => {});
                log(Object.keys(p).join(), Object.prototype.toString.call(p), String(p), p instanceof Promise, p.constructor === Promise);
            """,
            "C3-statics" to """
                Promise.all([1, Promise.resolve(2), { then(f) { f(3); } }]).then(v => log('all ' + v));
                Promise.all([]).then(v => log('all empty ' + v.length));
                Promise.all('ab').then(v => log('all string ' + v), e => log('all string rejected ' + e.message));
                Promise.all(5).catch(e => log('all 5 ' + e.name + ': ' + e.message));
                Promise.allSettled([Promise.reject('x'), 1]).then(v => log('settled ' + JSON.stringify(v)));
                Promise.allSettled(null).catch(e => log('settled null ' + e.message));
                Promise.race([new Promise(r => setTimeout(() => r('slow'), 10)), Promise.resolve('fast')]).then(v => log('race ' + v));
                Promise.race({}).catch(e => log('race {} ' + e.message));
                var q = Promise.resolve(9); log('resolve identity ' + (Promise.resolve(q) === q));
                Promise.all([Promise.reject('first'), Promise.reject('second')]).catch(e => log('all rejects ' + e));
            """,
            // ---- K: coroutines ----
            "K1-await-basic" to """
                log('await ' + Promise.resolve(7).await());
                log('await later ' + new Promise(r => setTimeout(() => r('t'), 5)).await());
                var e = Promise.reject(new Error('no')).await();
                log('await rejected returns ' + (e instanceof Error) + ' ' + e.message);
                log('done');
            """,
            "K2-await-loop" to """
                var got = [];
                for (var i = 0; i < 3; i++) got.push(new Promise(r => setTimeout(() => r(i), 1)).await());
                log('loop ' + got.join());
                log('continuation.await ' + continuation.await(Promise.resolve('ca')));
                continuation.delay(5); log('after delay');
            """,
            "K3-await-cross-thread" to """
                var v = new Promise(r => otherThread(() => r('from other'))).await();
                log('cross ' + v + ' on ' + where());
                new Promise(r => otherThread(() => r(1))).then(() => log('then on ' + where()));
            """,
            "K4-kotlin-await" to """
                try { log('kotlin await ' + kotlinAwait(Promise.resolve('k'))); } catch (e) { log('kotlin await failed ' + String(e).slice(0, 60)); }
            """,
            "K5-sample-helloworld" to """
                function delay(ms) { let cont = continuation.create(); setTimeout(() => cont.resume(), ms); cont.await(); }
                function add(a, b) { return new Promise((resolve) => resolve(a + b)); }
                setTimeout(() => log('1 s'), 1e3);
                delay(2e3); log('2 s');
                let sum = add(1, 2).await(); log('1 + 2 = ' + sum);
            """,
            "K6-await-in-then" to """
                Promise.resolve().then(() => { try { log('inner await ' + Promise.resolve(1).await()); } catch (e) { log('inner await error ' + String(e).slice(0, 50)); } });
            """,
            // ---- W: wait() ----
            "W1-wait" to """
                log('wait ' + Promise.resolve(1).wait());
                log('wait timer ' + new Promise(r => setTimeout(() => r(2), 5)).wait());
                try { Promise.reject(new Error('w')).wait(); } catch (e) { log('wait threw ' + e.message); }
                log('wait other ' + new Promise(r => otherThread(() => r(3))).wait());
            """,
            "W2-wait-order" to """
                setImmediate(() => log('immediate before'));
                log('wait ' + Promise.resolve('x').wait());
                log('after');
            """,
            // ---- T: other threads ----
            "T1-settle-on-other-thread" to """
                var p = new Promise(r => otherThread(() => r('v')));
                p.then(v => log('then ' + v + ' on ' + where()));
                otherThread(() => p.then(v => log('then registered on other, runs on ' + where())));
            """,
            // ---- D: the implementation (route A) ----
            "D1-callback-this" to """
                var g = (function () { return this; })();
                Promise.resolve().then(function () { log('this is global ' + (this === g)); });
                new Promise(function (r) { log('executor this is global ' + (this === g)); r(); });
            """,
            "D2-replaced-immediateFn" to """
                Promise._immediateFn = function (fn) { log('custom ' + typeof fn + ' ' + fn.length + ' ' + (this === Promise)); setTimeout(fn, 0); };
                setTimeout(() => log('timeout first'), 0);
                Promise.resolve(1).then(v => log('then ' + v));
                Promise.reject(new Error('lost'));
            """,
            "D3-replaced-setImmediate" to """
                var original = setImmediate;
                setImmediate = function (fn) { log('wrapped'); original(fn); };
                Promise.resolve(2).then(v => log('then ' + v));
                setImmediate = 5;
                Promise.resolve(3).then(v => log('timeout path ' + v));
            """,
            "D4-replaced-unhandled" to """
                Promise._unhandledRejectionFn = function (e) { log('unhandled ' + e + ' ' + (this === Promise)); };
                Promise.reject('x');
                Object.getPrototypeOf(Promise)._unhandledRejectionFn = 1;
                delete Promise._unhandledRejectionFn;
                Promise.reject('y');
            """,
            "D5-finally-not-function" to """
                Promise.resolve(1).finally(undefined).catch(e => log(e.name + ': ' + e.message));
                Promise.reject(2).finally(5).catch(e => log(e.name + ': ' + e.message));
            """,
            "D6-not-an-instance" to """
                var o = Object.create(Promise.prototype);
                o.then(v => log('fulfilled ' + v), e => log('rejected ' + e)).then(v => log('derived ' + v));
                o.catch(e => log('catch ' + e));
                Promise.prototype.then.call(5, null, e => log('primitive ' + e));
                Promise.prototype.catch.call({ then(a, b) { log('then via catch ' + a + ' ' + typeof b); } }, function f() {});
                Promise.resolve(1).then(() => o).then(v => log('adopted ' + v), e => log('adopted rejected ' + e));
            """,
            "D7-thenables" to """
                var t = {}; Object.defineProperty(t, 'then', { get() { throw new Error('getter'); } });
                Promise.resolve(t).catch(e => log('getter threw ' + e.message));
                new Promise(r => r({ then(f, j) { f(1); j(2); throw new Error('x'); } })).then(v => log('first wins ' + v), e => log('e ' + e));
                new Promise(r => r({ then(f, j) { throw new Error('before'); } })).catch(e => log('then threw ' + e.message));
                var fake = { constructor: Promise }; log('fake kept ' + (Promise.resolve(fake) === fake));
                Promise.resolve(function () {}).then(v => log('function value ' + typeof v));
            """,
            "D8-statics" to """
                Promise.all([{ then() { throw new Error('t'); } }, 2]).catch(e => log('all thenable threw ' + e.message));
                Promise.allSettled.call(function (ex) { log('custom ctor'); return new Promise(ex); }, [1, Promise.reject(2)]).then(v => log(JSON.stringify(v)));
                Promise.race({ length: '2', 0: 'a', 1: Promise.resolve('b') }).then(v => log('race ' + v));
                var resolve = Promise.resolve; Promise.resolve = function (v) { log('resolve ' + v); return resolve.call(Promise, v); };
                Promise.race([7]).then(v => log('race replaced ' + v));
                Promise.resolve = resolve;
                log(Promise.prototype.then.length, Promise.prototype.catch.length, Promise.prototype.finally.length, Promise.prototype.await.length,
                    Promise.prototype.wait.length, Promise.all.length, Promise.resolve.length, Promise.prototype.then.name);
                new Promise((res, rej) => log('resolvers ' + res.length + ' ' + rej.length + ' ' + typeof res));
            """,
            "D9-constructor-replaced" to """
                function Sub(ex) { log('Sub ' + ex.name + ' ' + ex.length); return new Promise(ex); }
                var p = Promise.resolve(1); p.constructor = Sub;
                p.then(v => log('sub ' + v)).then(() => log('chained'));
                var q = Promise.resolve(2); q.constructor = function () { return {}; };
                q.then(v => log('plain object ctor ' + v));
            """,
            "D10-adoption-chains" to """
                var a = new Promise(r => setTimeout(() => r('late'), 5));
                var b = Promise.resolve().then(() => a);
                var c = new Promise(r => r(b));
                c.then(v => log('chain ' + v));
                var r = Promise.reject(new Error('adopted'));
                Promise.resolve().then(() => r).catch(e => log('caught ' + e.message));
            """,
            "D12-not-a-function-messages" to """
                try { Promise.prototype.catch.call({}); } catch (e) { log('catch on {} ' + e.name + ': ' + e.message); }
                try { Promise.prototype.catch.call({ then: 1 }); } catch (e) { log('then 1 ' + e.message); }
                try { Promise.prototype.wait.call({}); } catch (e) { log('wait on {} ' + e.message); }
                try { Promise.prototype.finally.call({ constructor: {}, then(f) { f(1); } }, () => 0); } catch (e) { log('finally ' + e.message); }
                try { Promise.allSettled.call(undefined, []); } catch (e) { log('allSettled ' + e.name); }
                Promise._immediateFn = 1;
                try { Promise.resolve(1).then(() => 0); } catch (e) { log('immediateFn 1 ' + e.message); }
            """,
            "D13-functions-and-positions" to """
                new Promise((res, rej) => log('resolver prototype ' + typeof res.prototype + ' ' + typeof rej.prototype));
                var p = Promise.resolve(1);
                try { Reflect.apply(Promise, p, [function (r) { r(2); }]); log('reapplied'); } catch (e) { log('reapply ' + e.name + ': ' + e.message); }
                p.then(v => log('kept ' + v));
                try { new Promise(1); } catch (e) { log('new 1 at line ' + e.lineNumber); }
                try { Promise.call(p, () => 0); } catch (e) { log('call at line ' + e.lineNumber); }
                try { Promise.reject(new Error('w')).wait(); } catch (e) { log('wait threw ' + e.message + ' at line ' + e.lineNumber); }
            """,
            "D14-constructor-property-attributes" to """
                // As the surface snapshot sees them (arity is Rhino's own, not standard).
                log(['name', 'length', 'arity', 'prototype'].map(function (k) {
                    var d = Object.getOwnPropertyDescriptor(Promise, k);
                    return k + ' ' + (d.writable ? 'W' : '-') + (d.enumerable ? 'E' : '-') + (d.configurable ? 'C' : '-');
                }).join(', '));
            """,
            "D11-many-reactions-and-wait" to """
                var n = 0;
                for (var i = 0; i < 300; i++) Promise.resolve(i).then(() => n++);
                log('wait ' + Promise.resolve('w').wait() + ' after ' + n);
            """,
        )
    }

}
