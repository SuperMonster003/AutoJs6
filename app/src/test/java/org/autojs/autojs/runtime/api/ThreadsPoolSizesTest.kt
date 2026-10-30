package org.autojs.autojs.runtime.api

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import sun.misc.Unsafe

/**
 * The size check of threads.pool: maxPoolSize 0 (or none) means no limit, so any corePoolSize fits (api-coverage Q180).
 * zh-CN: threads.pool 的大小校验: maxPoolSize 为 0 (或未指定) 表示不限, 任何 corePoolSize 都合法 (api-coverage Q180).
 */
class ThreadsPoolSizesTest {

    private fun newThreads(): Threads {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null) as Unsafe
        }
        val runtime = unsafe.allocateInstance(ScriptRuntime::class.java) as ScriptRuntime
        ScriptRuntime::class.java.getDeclaredField("resourceScope").run {
            isAccessible = true
            set(runtime, RuntimeResourceScope("ThreadsPoolSizesTest"))
        }
        return Threads(runtime)
    }

    @Test
    fun `a core size without a maximum has no upper bound`() {
        val pool = newThreads().internalPool(3, 0, 1000L)
        assertEquals(3, pool.corePoolSize)
        assertEquals(Int.MAX_VALUE, pool.maximumPoolSize)
        pool.shutdown()
    }

    @Test
    fun `no sizes at all keep the defaults`() {
        val pool = newThreads().internalPool(0, 0, 60000L)
        assertEquals(0, pool.corePoolSize)
        assertEquals(Int.MAX_VALUE, pool.maximumPoolSize)
        pool.shutdown()
    }

    @Test
    fun `a core size within the maximum is kept`() {
        val pool = newThreads().internalPool(2, 3, 1000L)
        assertEquals(2, pool.corePoolSize)
        assertEquals(3, pool.maximumPoolSize)
        pool.shutdown()
    }

    @Test
    fun `a core size above a given maximum throws`() {
        assertThrows(IllegalArgumentException::class.java) { newThreads().internalPool(3, 2, 1000L) }
    }

}
