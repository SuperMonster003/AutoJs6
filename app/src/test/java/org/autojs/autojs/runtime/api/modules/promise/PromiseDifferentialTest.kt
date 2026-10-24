package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.promise.PromiseHarness.Impl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The global Promise of both switch states run side by side (roadmap P4.2, appendix C Q95): each scenario runs in a
 * fresh [PromiseHarness] with `assets/modules/promise.js` and with [PromiseConstructor] (through the global `setImmediate`
 * and posting its jobs itself), and the logs must be the same, except for the instance properties that Q96 dropped.
 * The scenarios are the 23 of docs/augment/P4-2-promise-eval.md §4.1 (A order, B errors, C surface, K coroutines, W wait,
 * T threads) and the D ones of the implementation (replaced `_immediateFn` / `setImmediate` / `_unhandledRejectionFn`,
 * receivers that are not instances, thenables that misbehave, many queued reactions and `wait()`).
 *
 * zh-CN: 开关两态的全局 Promise 并排运行 (ROADMAP P4.2, 附录 C Q95): 每个场景在新的 [PromiseHarness] 中分别以 promise.js 与 [PromiseConstructor]
 * (经全局 setImmediate, 以及自行投递任务) 运行, 日志必须相同, Q96 去掉的实例属性除外. 场景为评估文档 §4.1 的 23 个与实施时新增的 D 组.
 */
class PromiseDifferentialTest {

    @Test
    fun bothStatesLogTheSame() {
        val differences = ArrayList<String>()
        SCENARIOS.forEach { (id, source) ->
            val reference = runOne(Impl.MODULE, source)
            listOf(Impl.KOTLIN, Impl.KOTLIN_POSTED).forEach { impl ->
                val actual = runOne(impl, source)
                val expected = KNOWN[id] ?: reference
                if (actual != expected) differences += "$id $impl\n  module: $reference\n  expected: $expected\n  actual: $actual"
            }
        }
        assertTrue(differences.joinToString("\n"), differences.isEmpty())
    }

    @Test
    fun theModuleStillBehavesAsTheEvaluationRecorded() {
        // The reference itself, so that both columns cannot drift together.
        // zh-CN: 参照列本身的结果, 防止两列一起漂移.
        PINNED.forEach { (id, expected) ->
            assertEquals(id, expected, runOne(Impl.MODULE, SCENARIOS.toMap().getValue(id)))
        }
    }

    @Test
    fun flowAwaitSuspendsFromKotlinInBothStates() {
        // Q97: `FlowNativeObject.await` suspends through PromiseSuspension, which works for the module's Promise as well.
        // zh-CN: Q97: FlowNativeObject.await 经 PromiseSuspension 挂起, 对原模块的 Promise 同样有效.
        Impl.entries.forEach { impl ->
            assertEquals(impl.name, "(suspended) ; kotlin await k ; later 3 ; rejected ; popException Error: no ; got Error: no", runOne(impl, """
                log('kotlin await ' + kotlinAwait(Promise.resolve('k')));
                log('later ' + kotlinAwait(new Promise(r => setTimeout(() => r(3), 5))));
                log('rejected');
                log('got ' + kotlinAwait(Promise.reject(new Error('no'))));
            """))
        }
    }

    private fun runOne(impl: Impl, source: String): String = try {
        PromiseHarness(impl).use { it.run(source.trimIndent()).joinToString(" ; ") }
    } catch (e: Throwable) {
        "harness error: $e"
    }

    companion object {

        /** What [PromiseConstructor] knowingly does differently (appendix E "Promise (路线 A)"). */
        val KNOWN = mapOf(
            // Q96: no own `_state` / `_handled` / `_value` / `_deferreds`.
            "C2-surface-instance" to " [object Object] [object Object] true true",
            // Settling what is not an instance does nothing; the module wrote `_state` on it and failed in `_.finale`.
            "D6-not-an-instance" to "then via catch null function ; rejected undefined ; catch undefined ; primitive undefined ; derived undefined ; adopted rejected undefined",
            "D9-constructor-replaced" to "Sub noop 0 ; sub 1 ; plain object ctor 2 ; chained",
        )

        /** Results of the module that the evaluation recorded (docs/augment/P4-2-promise-eval.md §2). */
        val PINNED = mapOf(
            "A2-vs-timers" to "timeout0 ; p1 ; immediate ; p2 ; timeout-default",
            "A4-adopt-own" to "t1 ; adopted x ; t2 ; t3 ; t4",
            "A5-thenable-sync" to "then called ; after resolve ; value 5",
            "C2-surface-instance" to "_state,_handled,_value,_deferreds [object Object] [object Object] true true",
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
                    Promise.prototype.wait.length, Promise.all.length, Promise.resolve.length, Promise._immediateFn.length, Promise.prototype.then.name);
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
            "D11-many-reactions-and-wait" to """
                var n = 0;
                for (var i = 0; i < 300; i++) Promise.resolve(i).then(() => n++);
                log('wait ' + Promise.resolve('w').wait() + ' after ' + n);
            """,
        )
    }

}
