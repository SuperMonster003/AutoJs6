package org.autojs.autojs.core.accessibility.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRegistryTest {

    private class Node(val name: String) {
        // Framework nodes compare equal when they refer to the same view; the registry must not merge them.
        // zh-CN: 框架节点指向同一视图时相等; 注册表不得把它们合并.
        override fun equals(other: Any?) = other is Node && other.name == name
        override fun hashCode() = name.hashCode()
    }

    private val registry = NodeRegistry<Node>()

    @Test
    fun registersByIdentityNotEquality() {
        val first = Node("view")
        val second = Node("view")
        registry.register(first)
        registry.register(second, "origin")
        assertEquals(2, registry.size)
        assertTrue(registry.unregister(first))
        assertFalse("already gone", registry.unregister(first))
        assertEquals(1, registry.size)
        assertEquals(mapOf(second to "origin"), registry.drain())
    }

    @Test
    fun drainEmptiesTheRegistry() {
        registry.register(Node("a"))
        registry.register(Node("b"))
        assertEquals(2, registry.drain().size)
        assertEquals(0, registry.size)
        assertTrue(registry.drain().isEmpty())
    }

}
