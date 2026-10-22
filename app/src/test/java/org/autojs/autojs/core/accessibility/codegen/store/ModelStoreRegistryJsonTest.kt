package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.model.ModelBinaryMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelDatasetMetadata
import org.autojs.autojs.core.accessibility.codegen.model.ModelManifest
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetricSummary
import org.autojs.autojs.core.accessibility.codegen.model.ModelMetrics
import org.autojs.autojs.core.accessibility.codegen.model.ModelPackageReader
import org.autojs.autojs.core.accessibility.codegen.model.ModelTrainingMetadata
import org.autojs.autojs.core.pref.SettingsJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Modifier

/**
 * The model registry (`registry.json`) is read and written with SettingsJson instead of Gson (settings P5.3,
 * docs/settings/ROADMAP.md): registries written by Gson keep reading, the new text is byte-identical to Gson's, and the
 * lexical rules of the former strict reader (`ModelCatalogJson.parse`, which stays on Gson) still apply.
 * zh-CN: 模型登记表 (`registry.json`) 改由 SettingsJson 读写, 不再经由 Gson (settings P5.3): Gson 写出的登记表照常可读, 新写出的
 * 文本与 Gson 逐字节相同, 原严格读取器 (仍基于 Gson 的 `ModelCatalogJson.parse`) 的词法规则依旧适用.
 */
class ModelStoreRegistryJsonTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Selection(override var currentKey: String? = null) : CurrentModelSelection

    // The Gson instance of the former ModelStore.
    private val gson = GsonBuilder().serializeNulls().create()

    // The former MAX_REGISTRY_BYTES, the byte limit the registry was always parsed with.
    private val limit = 4 * 1024 * 1024

    private val q = Char(34).toString()
    private val bs = Char(92).toString()

    // Text that Gson escapes (HTML characters, quotes, a backslash, line and paragraph separators), non-ASCII text and a
    // slash, which Gson does not escape. Registry strings must not contain control characters.
    private val tricky = "Ranker <b>&amp;='x'</b> / ${q}q$q $bs 排序模型 ${Char(0x2028)}${Char(0x2029)}"

    private fun model(code: Int, hash: String, id: String = ModelComparisonTestFixtures.MODEL_ID, nullMetrics: Boolean = false): InstalledModel {
        val base = ModelComparisonTestFixtures.installed(code, hash, id)
        val manifest = base.manifest.copy(
            displayName = linkedMapOf("en" to tricky, "zh-CN" to "排序模型 / <&>", "ja" to "ランカー"),
            versionName = "$code.0.0 <beta> & 'rc'=/",
            // The metrics of zero groups are JSON nulls; 1.0E-4 is written in the exponent form of Double.toString.
            metrics = if (nullMetrics) {
                ModelMetrics(ModelMetricSummary(null, null, null, 0), ModelMetricSummary(1.0E-4, 1.0, 0.0, 1))
            } else base.manifest.metrics,
            license = "MPL-2.0 & <Apache-2.0>",
            publisher = "AutoJs6 / ${q}SuperMonster003$q",
        )
        return base.copy(manifest = manifest, origin = "https://example.com/a?b=1&c=<2>/模型 ${bs}x.ajcgm")
    }

    private val first = model(1, "a".repeat(64))
    private val second = model(2, "d".repeat(64), nullMetrics = true).copy(source = ModelSource.OFFICIAL, installedAtMillis = Long.MAX_VALUE)
    private val other = model(3, "e".repeat(64), id = "autojs6.codegen.other")

    // Not in key order: both writers sort the models by key.
    private val models = listOf(second, other, first)

    private val registries = listOf(
        ModelStore.Registry(emptyList()),
        ModelStore.Registry(listOf(first)),
        ModelStore.Registry(models),
        ModelStore.Registry(models, ModelStore.SelectionChange(null, first.key)),
        ModelStore.Registry(models, ModelStore.SelectionChange(second.key, null)),
        ModelStore.Registry(models, ModelStore.SelectionChange(null, null)),
    )

    // The former writeRegistryLocked. On Android the manifest tree has its members in name order (see inNameOrder); a
    // HotSpot JVM gives them in declaration order.
    private fun gsonRegistry(registry: ModelStore.Registry, android: Boolean = true): String = gson.toJson(JsonObject().apply {
        addProperty("format", 1)
        add("models", JsonArray().apply {
            registry.models.sortedBy { it.key }.forEach { model ->
                add(JsonObject().apply {
                    addProperty("modelId", model.modelId)
                    addProperty("versionCode", model.versionCode)
                    addProperty("versionName", model.manifest.versionName)
                    addProperty("featureSchemaVersion", model.manifest.featureSchemaVersion)
                    addProperty("sha256", model.sha256)
                    addProperty("size", model.sizeBytes)
                    addProperty("source", model.source.name)
                    addProperty("origin", model.origin)
                    addProperty("installedAt", model.installedAtMillis)
                    add("manifest", gson.toJsonTree(model.manifest).let { if (android) inNameOrder(it) else it })
                })
            }
        })
        registry.selectionChange?.let { change ->
            add("selectionChange", JsonObject().apply {
                addProperty("previousKey", change.previousKey)
                addProperty("nextKey", change.nextKey)
            })
        }
    })

    // ART returns the declared fields of a class sorted by name, so on Android Gson wrote the members of every object of
    // the manifest classes in name order; displayName is a map, which Gson wrote in its iteration order.
    private fun inNameOrder(element: JsonElement): JsonElement {
        if (!element.isJsonObject) return element
        return JsonObject().also { sorted ->
            element.asJsonObject.entrySet().sortedBy { it.key }.forEach { (key, value) ->
                sorted.add(key, if (key == "displayName") value else inNameOrder(value))
            }
        }
    }

    private fun sorted(registry: ModelStore.Registry) = registry.copy(models = registry.models.sortedBy { it.key })

    private fun encoded(registry: ModelStore.Registry) = String(ModelStore.encodeRegistry(registry), Charsets.UTF_8)

    private fun decode(text: String) = ModelStore.decodeRegistry(text.toByteArray(Charsets.UTF_8))

    // The former strict reader, which still parses the remote catalog.
    private fun gsonParse(text: String) = ModelCatalogJson.parse(text.toByteArray(Charsets.UTF_8), limit)

    private fun member(name: String) = "$q$name$q:"

    private fun fieldNames(type: Class<*>): List<String> =
        type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }.map { it.name }

    private fun store(root: File, selection: Selection) =
        ModelStore(root, ModelPackageReader(ModelComparisonTestFixtures.HOST_VERSION, temporary.root), { emptyList() }, selection, { 1234L })

    // Reconciliation keeps a registered model while its package file exists.
    private fun placePackage(root: File, model: InstalledModel) {
        val file = File(root, "${model.modelId}/${model.versionCode}-${model.sha256.take(8)}/model.ajcgm")
        assertTrue(file.parentFile!!.mkdirs())
        file.writeBytes(byteArrayOf(1))
    }

    @Test
    fun encodedRegistryIsByteIdenticalToGsonOnAndroid() {
        for (registry in registries) {
            assertArrayEquals(gsonRegistry(registry).toByteArray(Charsets.UTF_8), ModelStore.encodeRegistry(registry))
        }
        // The data exercises Gson's escaping: HTML characters and separators are escaped, a slash and non-ASCII text are not.
        val text = encoded(ModelStore.Registry(listOf(first)))
        listOf("u003c", "u003e", "u0026", "u003d", "u0027", "u2028", "u2029").forEach { assertTrue(it, text.contains(bs + it)) }
        assertTrue(text.contains("$bs$q" + "q$bs$q") && text.contains(bs + bs) && text.contains("/") && text.contains("排序模型"))
        assertTrue(encoded(registries[4]).contains(member("nextKey") + "null"))
    }

    @Test
    fun encoderWritesTheManifestMembersInTheNameOrderOfTheirFields() {
        fun keys(value: Any?): List<String> = (value as Map<*, *>).keys.map { it as String }
        val raw = SettingsJson.parseMap(encoded(ModelStore.Registry(listOf(first), ModelStore.SelectionChange(null, first.key))))
        assertEquals(listOf("format", "models", "selectionChange"), keys(raw))
        assertEquals(listOf("previousKey", "nextKey"), keys(raw["selectionChange"]))
        val record = (raw["models"] as List<*>).single()
        // The record members keep the order in which the former code added them.
        assertEquals(listOf("modelId", "versionCode", "versionName", "featureSchemaVersion", "sha256", "size", "source", "origin", "installedAt", "manifest"), keys(record))
        val manifest = (record as Map<*, *>)["manifest"] as Map<*, *>
        val metrics = manifest["metrics"] as Map<*, *>
        assertEquals(fieldNames(ModelManifest::class.java).sorted(), keys(manifest))
        assertEquals(fieldNames(ModelBinaryMetadata::class.java).sorted(), keys(manifest["model"]))
        assertEquals(fieldNames(ModelDatasetMetadata::class.java).sorted(), keys(manifest["dataset"]))
        assertEquals(fieldNames(ModelMetrics::class.java).sorted(), keys(metrics))
        assertEquals(fieldNames(ModelMetricSummary::class.java).sorted(), keys(metrics["test"]))
        assertEquals(fieldNames(ModelMetricSummary::class.java).sorted(), keys(metrics["realTest"]))
        assertEquals(fieldNames(ModelTrainingMetadata::class.java).sorted(), keys(manifest["training"]))
        // The display names keep their map order, as Gson wrote a map.
        assertEquals(listOf("en", "zh-CN", "ja"), keys(manifest["displayName"]))
        // Gson takes the members from Class.getDeclaredFields, which a HotSpot JVM returns in declaration order.
        assertEquals(fieldNames(ModelManifest::class.java), gson.toJsonTree(first.manifest).asJsonObject.keySet().toList())
    }

    @Test
    fun readsRegistriesWrittenByGsonInEitherFieldOrder() {
        for (registry in registries) {
            assertEquals(sorted(registry), decode(gsonRegistry(registry)))
            assertEquals(sorted(registry), decode(gsonRegistry(registry, android = false)))
        }
    }

    @Test
    fun roundTripKeepsTheRegistry() {
        for (registry in registries) {
            val bytes = ModelStore.encodeRegistry(registry)
            assertEquals(sorted(registry), ModelStore.decodeRegistry(bytes))
            // The former strict reader accepts the new text as well.
            gsonParse(String(bytes, Charsets.UTF_8))
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun unknownMembersWhitespaceAndALeadingByteOrderMarkAreAccepted() {
        val expected = ModelStore.Registry(listOf(first))
        val raw = SettingsJson.parseMap(encoded(expected))
        raw["extra"] = linkedMapOf("nested" to listOf(1, null, "x"))
        raw["selectionChange"] = null
        val record = (raw["models"] as List<*>).single() as MutableMap<String, Any?>
        record["extra"] = true
        val manifest = record["manifest"] as MutableMap<String, Any?>
        manifest["extra"] = 1.5
        val binary = manifest["model"] as MutableMap<String, Any?>
        binary["extra"] = "x"
        val texts = listOf(
            SettingsJson.stringify(raw, htmlSafe = true),
            SettingsJson.stringify(raw, htmlSafe = true, indent = 2),
            "${Char(0xFEFF)} ${Char(9)}${Char(13)}${Char(10)}${encoded(expected)}${Char(10)}",
        )
        for (text in texts) {
            gsonParse(text)
            assertEquals(expected, decode(text))
        }
    }

    @Test
    fun lexicalViolationsAreRejectedLikeTheStrictGsonReader() {
        val valid = encoded(ModelStore.Registry(listOf(first)))
        val origin = member("origin") + q
        val invalid = listOf(
            "",
            " ",
            "[]",
            "null",
            "$q" + "text$q",
            valid.dropLast(1),
            valid + "{}",
            valid + ",",
            // Escapes the strict reader rejected: an escaped quote, an escaped line break, an unknown escape, a short unicode escape.
            valid.replace(origin, origin + bs + "'"),
            valid.replace(origin, origin + bs + Char(10)),
            valid.replace(origin, origin + bs + "x"),
            valid.replace(origin, origin + bs + "u12g4"),
            // Unescaped control characters in a string, a tab included.
            valid.replace(origin, origin + Char(1)),
            valid.replace(origin, origin + Char(9)),
            // Control characters other than the four JSON whitespace characters between tokens.
            "${Char(12)}$valid",
            "${Char(0)}$valid",
            valid.replaceFirst(",", "${Char(11)},"),
            // Comments.
            "// c${Char(10)}$valid",
            "#c${Char(10)}$valid",
            "/" + "*c*" + "/" + valid,
            valid.replaceFirst(",", "/" + "*c*" + "/,"),
            // Other lenient syntax.
            valid.replaceFirst(member("format"), "'format':"),
            valid.replaceFirst(member("format"), "format:"),
            valid.replaceFirst(member("format") + "1", member("format") + "1," + member("format") + "1"),
            valid.replaceFirst(member("format") + "1", member("format") + "01"),
            valid.replaceFirst(member("format") + "1", member("format") + "1," + member("flag") + "True"),
            valid.replace(member("regret") + "0.1", member("regret") + "NaN"),
            valid.replace(member("regret") + "0.1", member("regret") + "1e999"),
        )
        for (text in invalid) {
            assertThrows(text, Exception::class.java) { gsonParse(text) }
            assertThrows(text, Exception::class.java) { decode(text) }
        }
    }

    @Test
    fun nestingIsBoundedLikeTheFormerReader() {
        val expected = ModelStore.Registry(listOf(first))
        val valid = encoded(expected)
        fun nested(arrays: Int, inner: String = "") =
            valid.replaceFirst("{", "{" + member("extra") + "[".repeat(arrays) + inner + "]".repeat(arrays) + ",")
        // The root is at depth 0 and "extra" at depth 1, so 31 arrays reach depth 31, the deepest value the former reader took.
        val deepest = nested(31)
        gsonParse(deepest)
        assertEquals(expected, decode(deepest))
        for (text in listOf(nested(32), nested(31, "0"), nested(31, "null"), nested(31, "{}"), nested(100_000))) {
            assertThrows(Exception::class.java) { gsonParse(text) }
            assertThrows(Exception::class.java) { decode(text) }
        }
    }

    @Test
    fun recordValidationAndTheByteLimitStillApply() {
        val single = encoded(ModelStore.Registry(listOf(first)))
        val full = encoded(ModelStore.Registry(models, ModelStore.SelectionChange(second.key, null)))
        val invalid = listOf(
            // Integers must be written as lexical integers within Long.
            single.replace(member("size") + "4096", member("size") + "4096.0"),
            single.replace(member("size") + "4096", member("size") + "4.096E3"),
            full.replace(Long.MAX_VALUE.toString(), "9223372036854775808"),
            single.replace(member("installedAt") + first.installedAtMillis, member("installedAt") + "-1"),
            single.replaceFirst(member("format") + "1", member("format") + "2"),
            single.replaceFirst(member("versionCode") + "1", member("versionCode") + "4"),
            single.replace(member("source") + q + "CUSTOM", member("source") + q + "custom"),
            single.replace(member("origin") + q, member("origin") + q + bs + "u0001"),
            single.replace(Regex(member("origin") + q + "[^$q]*$q"), member("origin") + q + q),
            single.replace(member("ruleTop1Survival") + "0.7,", ""),
            full.replace(member("groups") + "0," + member("regret") + "null", member("groups") + "0," + member("regret") + "0.5"),
            full.replace("," + member("nextKey") + "null", ""),
            encoded(ModelStore.Registry(models, ModelStore.SelectionChange(null, "autojs6.codegen.ranker@9-ffffffff"))),
            encoded(ModelStore.Registry(listOf(first, first))),
        )
        for (text in invalid) {
            assertTrue(text, text != single && text != full)
            assertThrows(text, Exception::class.java) { decode(text) }
        }

        val atLimit = single + " ".repeat(limit - single.toByteArray(Charsets.UTF_8).size)
        assertEquals(ModelStore.Registry(listOf(first)), decode(atLimit))
        assertThrows(Exception::class.java) { decode("$atLimit ") }

        val at = single.indexOf("https")
        val malformed = single.substring(0, at).toByteArray(Charsets.UTF_8) + byteArrayOf(0xC3.toByte(), 0x28) +
            single.substring(at).toByteArray(Charsets.UTF_8)
        assertThrows(Exception::class.java) { ModelCatalogJson.parse(malformed, limit) }
        assertThrows(Exception::class.java) { ModelStore.decodeRegistry(malformed) }
    }

    @Test
    fun storeReadsARegistryWrittenByGsonAndRewritesItAsGsonDid() {
        val root = temporary.newFolder()
        models.forEach { placePackage(root, it) }
        val registryFile = File(root, "registry.json")
        // A pending selection change, as a store interrupted between its two commits leaves it.
        registryFile.writeText(gsonRegistry(ModelStore.Registry(models, ModelStore.SelectionChange(null, first.key))))
        val selection = Selection()

        val store = store(root, selection)

        // The change was replayed and the registry rewritten without it.
        assertEquals(first.key, selection.currentKey)
        assertEquals(gsonRegistry(ModelStore.Registry(models)), registryFile.readText())
        assertEquals(models.sortedWith(compareBy<InstalledModel> { it.modelId }.thenByDescending { it.versionCode }.thenBy { it.sha256 }), store.listInstalled())
        assertEquals(first, store.current())
    }

    @Test
    fun storeWritesEveryRegistryCommitByteIdenticalToGson() {
        val root = temporary.newFolder()
        models.forEach { placePackage(root, it) }
        val registryFile = File(root, "registry.json")
        val gsonText = gsonRegistry(ModelStore.Registry(models))
        registryFile.writeText(gsonText)
        val selection = Selection(first.key)
        val store = store(root, selection)
        assertEquals(gsonText, registryFile.readText())
        val commits = mutableListOf<String>()
        store.beforeRegistryCommit = {
            commits += root.listFiles()!!.single { it.name.startsWith(".registry.json.tmp-") && it.name.endsWith(".part") }.readText()
        }

        assertTrue(store.select(second.key))

        assertEquals(second.key, selection.currentKey)
        assertEquals(listOf(gsonRegistry(ModelStore.Registry(models, ModelStore.SelectionChange(first.key, second.key))), gsonText), commits)
        assertEquals(gsonText, registryFile.readText())
    }
}
