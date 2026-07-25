package org.autojs.autojs.runtime.api.augment

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.RhinoTestRuntime
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable.NOT_FOUND
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY

class AugmentableKeysTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope
    private lateinit var target: ScriptableObject

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply {
            initStandardObjects(context, false)
        }
        with(RhinoUtils) {
            context.registerStandardObjects(scope)
        }
        target = scope.globalThis
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun defaultKeyPublishesPlainAndDollarPrefixedNames() {
        val augmented = DefaultAugmentable().augment(target)

        assertSame(augmented, target.get("defaultAugmentable", target))
        assertSame(augmented, target.get("\$defaultAugmentable", target))
    }

    @Test
    fun multipleKeysApplyTheirDollarPrefixPoliciesIndependently() {
        val augmentable = MultipleKeysAugmentable()
        val augmented = augmentable.augment(target)

        assertEquals("primary", augmentable.key)
        assertSame(augmented, target.get("primary", target))
        assertSame(augmented, target.get("\$primary", target))
        assertSame(augmented, target.get("legacy", target))
        assertSame(NOT_FOUND, target.get("\$legacy", target))
        assertSame(NOT_FOUND, target.get("multipleKeysAugmentable", target))
    }

    @Test
    fun primaryKeyNamesInvokableObject() {
        val augmentable = MultipleKeysInvokable()
        val augmented = augmentable.augment(target) as BaseFunction

        assertEquals("callable", augmentable.key)
        assertEquals("callable", augmented.functionName)
        assertSame(augmented, target.get("callable", target))
        assertSame(augmented, target.get("invokeLegacy", target))
    }

    @Test
    fun allPublishedNamesReceiveTheSameAttributes() {
        MultipleKeysAugmentable().augment(target, additionalAttributes = READONLY)

        val expected = PERMANENT or READONLY
        listOf("primary", "\$primary", "legacy").forEach { name ->
            assertEquals(expected, target.getAttributes(name))
        }
    }

    @Test
    fun protoOverloadStillAttachesTheSpecifiedPrototype() {
        val prototype = RhinoUtils.newNativeObject(scope)
        val augmented = MultipleKeysAugmentable().augment(target, prototype)

        assertSame(prototype, augmented.prototype)
    }

    @Test
    fun invalidKeySpecificationsFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            AugmentableKey(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EmptyKeysAugmentable().augment(target)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DuplicateKeysAugmentable().augment(target)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CollidingExpandedKeysAugmentable().augment(target)
        }
    }

    @Test
    fun proxyUsesTheSameMultipleKeyMountingRules() {
        val runtime = RhinoTestRuntime.create(scope)
        val augmentableProxy = MultipleKeysProxy(runtime)
        assertEquals("proxy", augmentableProxy.key)
        val proxy = augmentableProxy.proxying(target, emptyList<Any>())

        assertEquals(1, augmentableProxy.keysAccessCount)
        assertSame(proxy, target.get("proxy", target))
        assertSame(proxy, target.get("\$proxy", target))
        assertSame(proxy, target.get("proxyLegacy", target))
        assertSame(NOT_FOUND, target.get("\$proxyLegacy", target))
        listOf("proxy", "\$proxy", "proxyLegacy").forEach { name ->
            assertEquals(ScriptableObject.EMPTY, target.getAttributes(name))
        }
    }

    private class DefaultAugmentable : Augmentable()

    private class MultipleKeysAugmentable : Augmentable() {

        override val keys = listOf(
            AugmentableKey("primary"),
            AugmentableKey("legacy", withDollarPrefix = false),
        )

    }

    private class MultipleKeysInvokable : Augmentable(), Invokable {

        override val keys = listOf(
            AugmentableKey("callable", withDollarPrefix = false),
            AugmentableKey("invokeLegacy", withDollarPrefix = false),
        )

    }

    private class EmptyKeysAugmentable : Augmentable() {

        override val keys = emptyList<AugmentableKey>()

    }

    private class DuplicateKeysAugmentable : Augmentable() {

        override val keys = listOf(
            AugmentableKey("duplicate", withDollarPrefix = false),
            AugmentableKey("duplicate", withDollarPrefix = false),
        )

    }

    private class CollidingExpandedKeysAugmentable : Augmentable() {

        override val keys = listOf(
            AugmentableKey("collision"),
            AugmentableKey("\$collision", withDollarPrefix = false),
        )

    }

    private class MultipleKeysProxy(scriptRuntime: ScriptRuntime) : AugmentableProxy(scriptRuntime) {

        var keysAccessCount = 0
            private set

        override val keys: List<AugmentableKey>
            get() {
                keysAccessCount += 1
                return listOf(
                    AugmentableKey("proxy"),
                    AugmentableKey("proxyLegacy", withDollarPrefix = false),
                )
            }

    }

}
