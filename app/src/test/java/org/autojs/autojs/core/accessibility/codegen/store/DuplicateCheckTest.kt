package org.autojs.autojs.core.accessibility.codegen.store

import org.autojs.autojs.core.accessibility.codegen.store.ModelComparisonTestFixtures.installed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DuplicateCheckTest {
    private fun incoming(code: Int = 2, hash: String = "f".repeat(64), id: String = ModelComparisonTestFixtures.MODEL_ID) =
        IncomingModel(id, code, hash)

    @Test
    fun newIdentityAndNewContentNeedsNoConfirmation() {
        assertSame(DuplicateCheck.None, DuplicateCheck.evaluate(incoming(), emptyList()))
        assertSame(DuplicateCheck.None, DuplicateCheck.evaluate(incoming(), listOf(installed(id = "another.model"))))
    }

    @Test
    fun fullHashMatchAcrossIdentitiesPrecedesMatchingModelOrVersion() {
        val identical = installed(9, "f".repeat(64), "another.model")
        val result = DuplicateCheck.evaluate(incoming(hash = "F".repeat(64)), listOf(installed(2), identical))
        assertEquals(DuplicateCheck.Identical(identical), result)
    }

    @Test
    fun equalShortHashesDoNotMeanIdenticalContent() {
        val old = installed(hash = "a".repeat(12) + "b".repeat(52))
        val result = DuplicateCheck.evaluate(incoming(hash = "a".repeat(12) + "c".repeat(52)), listOf(old))
        assertEquals(old, (result as DuplicateCheck.SameModel).compareWith)
    }

    @Test
    fun sameVersionTakesPrecedenceOverCurrentAndNewestVersions() {
        val same = installed(2)
        val current = installed(1)
        val newest = installed(8)
        val result = DuplicateCheck.evaluate(incoming(), listOf(newest, current, same), current.key) as DuplicateCheck.SameModel
        assertEquals(same, result.compareWith)
        assertEquals(listOf(newest, same, current), result.existing)
    }

    @Test
    fun currentVersionIsPreferredAmongMultiplePackagesAtTheSameVersion() {
        val other = installed(2, "a".repeat(64))
        val current = installed(2, "c".repeat(64))
        val result = DuplicateCheck.evaluate(incoming(), listOf(other, current), current.key) as DuplicateCheck.SameModel
        assertEquals(current, result.compareWith)
    }

    @Test
    fun sameVersionTieBreakIsStableForEveryRegistryOrder() {
        val first = installed(2, "a".repeat(64))
        val second = installed(2, "b".repeat(64))
        val current = installed(1)
        val models = listOf(first, second, current)
        for (registry in permutations(models)) {
            val result = DuplicateCheck.evaluate(incoming(), registry, current.key) as DuplicateCheck.SameModel
            assertEquals(first, result.compareWith)
            assertEquals(listOf(first, second, current), result.existing)
        }
    }

    @Test
    fun currentIsPreferredWhenNoMatchingVersionExists() {
        val current = installed(1)
        val result = DuplicateCheck.evaluate(incoming(3), listOf(installed(9), current), current.key) as DuplicateCheck.SameModel
        assertEquals(current, result.compareWith)
    }

    @Test
    fun newestIsPreferredWhenCurrentIsAbsentOrBelongsToAnotherModel() {
        val newest = installed(9)
        val differentModel = installed(20, id = "another.model")
        val registry = listOf(installed(1), newest, differentModel)
        for (current in listOf(null, "missing@1-00000000", differentModel.key)) {
            val result = DuplicateCheck.evaluate(incoming(3), registry, current) as DuplicateCheck.SameModel
            assertEquals(newest, result.compareWith)
            assertEquals(listOf(newest, registry.first()), result.existing)
        }
    }

    @Test
    fun newestVersionTiesAlsoUseFullHashOrder() {
        val first = installed(9, "a".repeat(64))
        val second = installed(9, "b".repeat(64))
        for (registry in listOf(listOf(first, second), listOf(second, first))) {
            assertEquals(first, (DuplicateCheck.evaluate(incoming(3), registry) as DuplicateCheck.SameModel).compareWith)
        }
    }

    @Test
    fun identicalResultIsDeterministicAndPrefersTheCurrentPackage() {
        val sameModel = installed(2, "f".repeat(64))
        val current = installed(9, "f".repeat(64), "another.model")
        for (registry in listOf(listOf(sameModel, current), listOf(current, sameModel))) {
            assertEquals(DuplicateCheck.Identical(current), DuplicateCheck.evaluate(incoming(), registry, current.key))
            assertEquals(DuplicateCheck.Identical(sameModel), DuplicateCheck.evaluate(incoming(), registry))
        }
    }

    @Test
    fun evaluationReturnsAnImmutableSnapshotWithoutChangingTheRegistry() {
        val old = installed()
        val registry = mutableListOf(old)
        val result = DuplicateCheck.evaluate(incoming(), registry) as DuplicateCheck.SameModel
        assertEquals(listOf(old), registry)
        registry.clear()
        assertEquals(listOf(old), result.existing)
        assertThrows(UnsupportedOperationException::class.java) { (result.existing as MutableList<*>).clear() }
    }

    @Test
    fun adaptersUsePackageIdentityAndRejectIncompleteContentHashes() {
        val installed = installed()
        val expected = IncomingModel(installed.modelId, installed.versionCode, installed.sha256)
        assertEquals(expected, IncomingModel.fromInstalled(installed))
        assertEquals(expected, IncomingModel.fromManifest(installed.manifest, installed.sha256))
        val catalog = ModelComparisonTestFixtures.catalog()
        assertEquals(expected, IncomingModel.fromCatalog(catalog.models.single(), catalog.models.single().versions.single()))
        for (hash in listOf("", "a".repeat(12), "a".repeat(63), "g".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { incoming(hash = hash) }
        }
    }

    private fun <T> permutations(values: List<T>): List<List<T>> = if (values.isEmpty()) listOf(emptyList()) else
        values.indices.flatMap { index -> permutations(values.filterIndexed { i, _ -> i != index }).map { listOf(values[index]) + it } }
}
