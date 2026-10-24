package org.autojs.autojs.runtime.api.modules.promise

import org.autojs.autojs.runtime.api.modules.promise.PromiseHarness.Impl
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P4.2 / appendix C Q95: JVM micro benchmark of the global Promise, `assets/modules/promise.js` against
 * [PromiseConstructor] (interpreted Rhino, as on the device), with the cases of the evaluation (`PromiseEvalBenchTest`
 * of the prototype, appendix A "Promise (路线 A)"). Each case is a script run [ROUNDS] times in a fresh harness after
 * [WARMUP] runs; the median per operation in microseconds goes to `build/reports/promise/bench.md`. Runs only when
 * `app/build/promise-bench` exists.
 *
 * zh-CN: ROADMAP P4.2 / 附录 C Q95: 全局 Promise 的 JVM 微基准, `assets/modules/promise.js` 对 [PromiseConstructor] (解释模式 Rhino, 同真机),
 * 用例同评估 (原型的 `PromiseEvalBenchTest`, 附录 A "Promise (路线 A)"). 每个用例是一段脚本, 在新的测试环境中先运行 [WARMUP] 次, 再运行 [ROUNDS] 次,
 * 每次操作耗时 (微秒) 的中位数写入 `build/reports/promise/bench.md`. 仅当 `app/build/promise-bench` 存在时运行.
 */
class PromiseBenchTest {

    @Test
    fun bench() {
        assumeTrue(File("build/promise-bench").exists())
        val out = StringBuilder("| case (us / op) | ${Impl.entries.joinToString(" | ")} |\n|---|${"---|".repeat(Impl.entries.size)}\n")
        CASES.forEach { (name, ops, body) ->
            out.append("| $name ")
            Impl.entries.forEach { impl -> out.append("| %.2f ".format(measure(impl, ops, body))) }
            out.append("|\n")
        }
        out.append("| await() in a coroutine (suspend + resume) ")
        Impl.entries.forEach { impl -> out.append("| %.2f ".format(measureAwait(impl))) }
        out.append("|\n")
        File("build/reports/promise").mkdirs()
        File("build/reports/promise/bench.md").writeText(out.toString())
        println(out)
    }

    private fun measure(impl: Impl, ops: Int, body: String): Double {
        PromiseHarness(impl).use { h ->
            h.eval("var __n = $ops; var __sink = 0; function __case() { $body }")
            repeat(WARMUP) { h.eval("__case()"); h.drain() }
            val samples = (1..ROUNDS).map {
                val t0 = System.nanoTime()
                h.eval("__case()")
                h.drain()
                (System.nanoTime() - t0) / 1000.0 / ops
            }.sorted()
            return samples[samples.size / 2]
        }
    }

    private fun measureAwait(impl: Impl): Double {
        val ops = 500
        val source = "var __s = 0; for (var i = 0; i < $ops; i++) __s += Promise.resolve(i).await();"
        PromiseHarness(impl).use { h ->
            repeat(WARMUP / 3) { h.run(source) }
            val samples = (1..ROUNDS).map {
                val t0 = System.nanoTime()
                h.run(source)
                (System.nanoTime() - t0) / 1000.0 / ops
            }.sorted()
            return samples[samples.size / 2]
        }
    }

    companion object {
        const val WARMUP = 10
        const val ROUNDS = 9

        val CASES = listOf(
            Triple("resolve + then (success)", 2000, "for (var i = 0; i < __n; i++) Promise.resolve(i).then(v => { __sink += v; });"),
            Triple("new + resolve + then x3 chain", 1000, "for (var i = 0; i < __n; i++) new Promise(r => r(i)).then(v => v + 1).then(v => v * 2).then(v => { __sink += v; });"),
            Triple("reject + catch (failure)", 2000, "for (var i = 0; i < __n; i++) Promise.reject(new Error('x')).catch(e => { __sink++; });"),
            Triple("then throws -> catch (failure)", 1000, "for (var i = 0; i < __n; i++) Promise.resolve(i).then(() => { throw new TypeError('t'); }).catch(e => { __sink++; });"),
            Triple("Promise.all of 10", 200, "for (var i = 0; i < __n; i++) Promise.all([1,2,3,4,5,6,7,8,9,10].map(x => Promise.resolve(x))).then(a => { __sink += a.length; });"),
            Triple("wait() resolved", 1000, "for (var i = 0; i < __n; i++) __sink += Promise.resolve(i).wait();"),
            Triple("then queued, then wait()", 300, "for (var i = 0; i < __n; i++) Promise.resolve(i).then(v => { __sink += v; }); __sink += Promise.resolve(0).wait();"),
        )
    }

}
