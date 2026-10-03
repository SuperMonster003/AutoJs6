package org.autojs.autojs.core.accessibility.codegen.model

import com.google.gson.GsonBuilder
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.features.CandidateFeatures
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.plan.Condition
import org.autojs.autojs.core.accessibility.codegen.plan.LocatePlan
import org.autojs.autojs.core.accessibility.codegen.plan.SelectorSpec
import org.autojs.autojs.core.accessibility.codegen.verify.PlanVerifier
import org.autojs.autojs.core.accessibility.codegen.verify.Verified
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.random.Random

/** Opt-in host measurements, not substitutes for the P8 Android-device budgets.
 * zh-CN: 按需运行的宿主基准测量, 不替代 P8 的 Android 设备预算验收. */
class ModelBenchmarkTest {
    @Test
    fun recordsPackageLoadAndTwoHundredVerifiedCandidateFeatureAndScoreCosts() {
        assumeTrue("Set CODEGEN_BENCH=1 to run the model benchmark", System.getenv("CODEGEN_BENCH") == "1")
        val binary = randomModelBinary()
        val archive = ModelPackageTestFixtures.archive(binary = binary, mutateManifest = { manifest ->
            manifest.getAsJsonObject("model").apply {
                addProperty("trees", TREES)
                addProperty("depth", DEPTH)
                addProperty("features", FeatureSchema.names.size)
            }
        })
        fun load(): ObliviousTreeModel {
            val result = ModelPackageReader().read(ByteArrayInputStream(archive))
            assertTrue("Benchmark package rejected: $result", result is ModelPackage)
            return (result as ModelPackage).model
        }
        val coldStarted = System.nanoTime()
        val model = load()
        val coldLoadNanos = System.nanoTime() - coldStarted
        assertEquals(TREES, model.treeCount)
        assertEquals(DEPTH, model.maxDepth)
        val expectedStorage = TREES.toLong() * (1 + 4 + 4 + 8 * DEPTH + 4 * (1 shl DEPTH))
        assertEquals(expectedStorage, model.storageBytes)
        assertTrue(model.storageBytes <= 32L * 1024 * 1024)

        val root = FakeUiNode.root {
            id = "fixture:id/root"
            repeat(NODES - 1) { index ->
                child("item-$index") {
                    id = "fixture:id/item-$index"
                    packageName = "fixture"
                    className = "android.widget.Button"
                    text = "Item $index"
                    desc = "Button $index"
                    clickable = true
                    focusable = true
                }
            }
        }
        val request = CodeGenRequest(listOf(root.child(0)!!))
        val verifier = PlanVerifier(root)
        val optional = listOf(Condition("text", "Item 0"), Condition("desc", "Button 0"),
            Condition("className", "android.widget.Button"), Condition("packageName", "fixture"),
            Condition("clickable", true), Condition("focusable", true), Condition("enabled", true), Condition("depth", 1))
        val candidates = (0 until CANDIDATES).map { mask ->
            val conditions = listOf(Condition("id", "fixture:id/item-0")) + optional.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val plan = LocatePlan(LocatePlan.Family.SELF, SelectorSpec(conditions))
            val result = verifier.verify(request, plan)
            assertTrue("Benchmark plan must be verified: $result", result is Verified)
            result as Verified<FakeUiNode>
        }
        assertEquals(CANDIDATES, candidates.map { it.plan.planKey }.distinct().size)
        var sink = 0.0
        fun extractAndScore(): Pair<Long, Long> {
            val started = System.nanoTime()
            val context = CandidateFeatures.prepare(request, candidates, verifier.tree)
            val vectors = context.extractAll()
            val afterFeatures = System.nanoTime()
            for (vector in vectors) sink += model.score(vector)
            return (afterFeatures - started) to (System.nanoTime() - afterFeatures)
        }
        repeat(WARMUPS) { load(); extractAndScore() }
        val loadTimes = ArrayList<Long>()
        val featureTimes = ArrayList<Long>()
        val scoreTimes = ArrayList<Long>()
        repeat(RUNS) {
            val started = System.nanoTime()
            val loaded = load()
            loadTimes += System.nanoTime() - started
            assertEquals(model.storageBytes, loaded.storageBytes)
            val (features, scores) = extractAndScore()
            featureTimes += features
            scoreTimes += scores
        }
        assertTrue(sink.isFinite())
        fun stats(times: List<Long>): Map<String, Any> {
            val sorted = times.sorted()
            return linkedMapOf("samplesNanos" to times, "p50Millis" to sorted[(sorted.size - 1) / 2] / 1e6,
                "p95Millis" to sorted[(ceil(sorted.size * .95).toInt() - 1).coerceAtLeast(0)] / 1e6)
        }
        val report = linkedMapOf<String, Any>(
            "format" to 1, "environment" to "host-jvm", "seed" to SEED, "trees" to TREES,
            "depth" to DEPTH, "featureCount" to FeatureSchema.names.size, "nodes" to NODES, "candidates" to CANDIDATES,
            "warmupRuns" to WARMUPS, "measuredRuns" to RUNS, "modelBinaryBytes" to binary.size,
            "modelPackageBytes" to archive.size, "modelArrayStorageBytes" to model.storageBytes,
            "firstPackageLoadMillis" to coldLoadNanos / 1e6,
            "javaVersion" to System.getProperty("java.version"), "osName" to System.getProperty("os.name"),
            "osArch" to System.getProperty("os.arch"), "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "packageLoad" to stats(loadTimes), "featureExtraction" to stats(featureTimes), "score" to stats(scoreTimes),
            "featuresAndScore" to stats(featureTimes.zip(scoreTimes) { features, scores -> features + scores }),
            "candidateSource" to "200 distinct SELF plans verified by PlanVerifier on a synthetic 5000-node tree",
            "storageDefinition" to "Backing-array payload only; JVM object headers, temporary package buffers and feature contexts excluded",
            "deviceBudgetsVerified" to false,
        )
        val directory = System.getenv("CODEGEN_BENCH_DIR")?.takeIf(String::isNotBlank)?.let(::File) ?: File("build/codegen-benchmark")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create benchmark output directory" }
        val file = File(directory, "model-benchmark.json")
        file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n")
        println("Model benchmark: ${file.absolutePath}")
        println("Array storage: ${model.storageBytes} bytes; feature+score p95: ${report["featuresAndScore"]}")
    }

    private fun randomModelBinary(): ByteArray {
        val random = Random(SEED)
        val payloadSize = 28 + TREES * (1 + 6 * DEPTH + 4 * (1 shl DEPTH))
        val buffer = ByteBuffer.allocate(payloadSize + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(byteArrayOf('A'.code.toByte(), 'J'.code.toByte(), 'C'.code.toByte(), 'G'.code.toByte()))
        buffer.putShort(1)
        buffer.putShort(FeatureSchema.names.size.toShort())
        buffer.putInt(TREES)
        buffer.putDouble(.125)
        buffer.putDouble(.75)
        repeat(TREES) {
            buffer.put(DEPTH.toByte())
            repeat(DEPTH) {
                buffer.putShort(random.nextInt(FeatureSchema.names.size).toShort())
                buffer.putFloat(random.nextDouble(-2.0, 2.0).toFloat())
            }
            repeat(1 shl DEPTH) { buffer.putFloat(random.nextDouble(-1.0, 1.0).toFloat()) }
        }
        assertEquals(payloadSize, buffer.position())
        val crc = CRC32().apply { update(buffer.array(), 0, payloadSize) }
        buffer.putInt(crc.value.toInt())
        return buffer.array()
    }

    companion object {
        private const val SEED = 20261003
        private const val TREES = 1024
        private const val DEPTH = 6
        private const val CANDIDATES = 200
        private const val NODES = 5000
        private const val WARMUPS = 3
        private const val RUNS = 10
    }
}
