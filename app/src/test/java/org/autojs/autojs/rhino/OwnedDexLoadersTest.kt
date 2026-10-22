package org.autojs.autojs.rhino

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests of the loaders that runtime.loadDex / loadJar register in the application class loader (docs/memory/ROADMAP.md H3 /
 * P4.4): an entry stays while one of the scripts that loaded its path runs and is removed when the last of them ends; loads
 * without an owner (host code) stay for the process; a failed load is undone without touching a newer loader.
 * zh-CN: runtime.loadDex / loadJar 在应用类加载器中登记的加载器的 JVM 测试 (docs/memory/ROADMAP.md H3 / P4.4): 加载过该路径的脚本中仍有一个在运行时
 * 该项保留, 最后一个结束时移除; 不带 owner 的加载 (宿主代码) 在进程内保留; 加载失败时撤销, 且不影响更新的加载器.
 */
class OwnedDexLoadersTest {

    private val table = OwnedDexLoaders<Any>()

    @Test
    fun releasingTheOnlyOwnerRemovesItsLoaders() {
        val first = Any()
        val second = Any()
        table.put("/cache/safe_a.jar", first, "runtime@1")
        table.put("/cache/safe_b.jar", second, "runtime@1")

        val released = table.releaseOwner("runtime@1")

        assertEquals(setOf(first, second), released.toSet())
        assertEquals(0, table.size)
        assertTrue(table.values.isEmpty())
    }

    @Test
    fun aPathLoadedByTwoScriptsStaysUntilBothEnded() {
        val older = Any()
        val newer = Any()
        table.put("/cache/safe_a.jar", older, "runtime@1")
        // The second load of the same path replaces the loader, as before P4.4.
        table.put("/cache/safe_a.jar", newer, "runtime@2")

        assertTrue(table.releaseOwner("runtime@1").isEmpty())
        assertSame(newer, table.loader("/cache/safe_a.jar"))
        assertEquals(setOf("runtime@2"), table.owners("/cache/safe_a.jar"))

        assertEquals(listOf(newer), table.releaseOwner("runtime@2"))
        assertNull(table.loader("/cache/safe_a.jar"))
    }

    @Test
    fun releasingAnotherOwnerLeavesTheLoadersOfRunningScripts() {
        val loader = Any()
        table.put("/cache/safe_a.jar", loader, "runtime@1")

        assertTrue(table.releaseOwner("runtime@9").isEmpty())

        assertSame(loader, table.loader("/cache/safe_a.jar"))
        assertEquals(1, table.size)
    }

    @Test
    fun loadsWithoutAnOwnerStayForTheProcess() {
        val hostLoader = Any()
        val scriptLoader = Any()
        table.put("/cache/host.jar", hostLoader, null)
        table.put("/cache/shared.jar", scriptLoader, "runtime@1")
        table.put("/cache/shared.jar", scriptLoader, null)

        assertTrue(table.releaseOwner("runtime@1").isEmpty())

        assertSame(hostLoader, table.loader("/cache/host.jar"))
        assertSame(scriptLoader, table.loader("/cache/shared.jar"))
    }

    @Test
    fun releasingTwiceReleasesOnce() {
        val loader = Any()
        table.put("/cache/safe_a.jar", loader, "runtime@1")

        assertEquals(listOf(loader), table.releaseOwner("runtime@1"))
        assertTrue(table.releaseOwner("runtime@1").isEmpty())
        assertTrue(table.owners("/cache/safe_a.jar").isEmpty())
    }

    @Test
    fun aFailedLoadIsUndoneWithoutRemovingANewerLoader() {
        val failed = Any()
        val newer = Any()
        table.put("/cache/safe_a.jar", failed, "runtime@1")
        assertTrue(table.remove("/cache/safe_a.jar", failed))
        assertNull(table.loader("/cache/safe_a.jar"))

        table.put("/cache/safe_a.jar", newer, "runtime@2")
        assertFalse(table.remove("/cache/safe_a.jar", failed))
        assertSame(newer, table.loader("/cache/safe_a.jar"))
    }

    @Test
    fun lookupsSeeLoadersRegisteredWhileTheyIterate() {
        table.put("/cache/safe_a.jar", "a", "runtime@1")
        val values = table.values
        table.put("/cache/safe_b.jar", "b", "runtime@2")

        // values is a live view of the concurrent map: class lookups never take the lock.
        assertEquals(setOf("a", "b"), values.toSet())
    }
}
