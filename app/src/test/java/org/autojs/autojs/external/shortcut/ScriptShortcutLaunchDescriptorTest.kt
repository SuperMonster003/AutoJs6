package org.autojs.autojs.external.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptShortcutLaunchDescriptorTest {

    @Test
    fun forPathEmitsFixedV1FieldsOnlyForLua() {
        val expected = ScriptShortcutLaunchDescriptor(
            schema = ScriptShortcutLaunchDescriptor.SCHEMA_V1,
            engine = ScriptShortcutLaunchDescriptor.ENGINE_LUA,
            sourceKind = ScriptShortcutLaunchDescriptor.SOURCE_KIND_FILE,
        )

        assertEquals(expected, ScriptShortcutLaunchDescriptor.forPath("/scripts/main.lua"))
        assertEquals(expected, ScriptShortcutLaunchDescriptor.forPath("C:\\Scripts\\MAIN.LUA"))
        assertNull(ScriptShortcutLaunchDescriptor.forPath("/scripts/main.js"))
        assertNull(ScriptShortcutLaunchDescriptor.forPath("/scripts/main.auto"))
    }

    @Test
    fun absentDescriptorPreservesLegacyRouting() {
        assertEquals(
            ScriptShortcutLaunchDescriptor.Evaluation.LEGACY,
            evaluate(
                descriptorPresent = false,
                schema = null,
                engine = null,
                sourceKind = null,
                path = "/scripts/legacy.lua",
                actualEngine = "lua",
            ),
        )
    }

    @Test
    fun completeV1LuaDescriptorIsAcceptedCaseInsensitivelyByPath() {
        listOf("/scripts/main.lua", "/scripts/MAIN.LUA").forEach { path ->
            assertEquals(
                ScriptShortcutLaunchDescriptor.Evaluation.ACCEPTED,
                evaluate(path = path),
            )
        }
    }

    @Test
    fun partialOrInconsistentlyAbsentDescriptorIsRejected() {
        val partialFields = listOf(
            Triple(1, null, null),
            Triple(null, "lua", null),
            Triple(null, null, "file"),
            Triple(null, "lua", "file"),
            Triple(1, null, "file"),
            Triple(1, "lua", null),
        )
        partialFields.forEach { (schema, engine, sourceKind) ->
            assertEquals(
                ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
                evaluate(schema = schema, engine = engine, sourceKind = sourceKind),
            )
        }
        assertEquals(
            ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
            evaluate(schema = null, engine = null, sourceKind = null),
        )
        assertEquals(
            ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
            evaluate(descriptorPresent = false),
        )
    }

    @Test
    fun unknownSchemaFieldTypesAndValuesAreRejected() {
        val mutations = listOf(
            Triple<Any?, Any?, Any?>(2, "lua", "file"),
            Triple<Any?, Any?, Any?>("1", "lua", "file"),
            Triple<Any?, Any?, Any?>(1, "javascript", "file"),
            Triple<Any?, Any?, Any?>(1, 7, "file"),
            Triple<Any?, Any?, Any?>(1, "lua", "project"),
            Triple<Any?, Any?, Any?>(1, "lua", 7),
        )
        mutations.forEach { (schema, engine, sourceKind) ->
            assertEquals(
                ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
                evaluate(schema = schema, engine = engine, sourceKind = sourceKind),
            )
        }
    }

    @Test
    fun luaDescriptorRejectsPathOrResolvedEngineMismatch() {
        listOf("/scripts/main.js", "/scripts/main.auto").forEach { path ->
            assertEquals(
                ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
                evaluate(path = path),
            )
        }
        listOf("js", "auto").forEach { actualEngine ->
            assertEquals(
                ScriptShortcutLaunchDescriptor.Evaluation.REJECTED,
                evaluate(actualEngine = actualEngine),
            )
        }
    }

    private fun evaluate(
        descriptorPresent: Boolean = true,
        schema: Any? = 1,
        engine: Any? = "lua",
        sourceKind: Any? = "file",
        path: String? = "/scripts/main.lua",
        actualEngine: String? = "lua",
    ): ScriptShortcutLaunchDescriptor.Evaluation {
        return ScriptShortcutLaunchDescriptor.evaluate(
            descriptorPresent = descriptorPresent,
            schema = schema,
            engine = engine,
            sourceKind = sourceKind,
            path = path,
            actualEngine = actualEngine,
        )
    }
}
