package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Guard tests T5 (a) (b) (c) (e) of docs/settings/ROADMAP.md (P1.3). The registry ([SettingsRegistry.describe]) and
 * the registered stores ([SettingsStores.registeredIds]) are what is migrated; `tools/settings/classification.json`
 * (P0.3) is the initial allow list of what is still "pending migration". A test fails only for names that are in
 * neither (a new `key_*` resource, a new `getSharedPreferences(` file name, a new deployment kind), for stale
 * allow-list entries and for declarations that disagree with the allow list; each test prints its pending count.
 *
 * - (a) every `key_*` string resource is registered (Setting / State / Cache / ValueConstant / Excluded) or pending; a
 *   candidate value may also name one stored key (P4.1), which its classification entry states as "storedAs".
 * - (b) every `getSharedPreferences(` file name in the sources is registered or pending (the store layer itself in
 *   `core/pref/store/` is exempt); names are resolved from literals, constants, string resources and
 *   `prefix + ...` concatenations, and the few calls that need more are listed in [MANUAL_STORE_NAMES]. A registered
 *   store ([SettingsRegistry.stores], P3) agrees with its classification.json entry: an excluded one is Excluded and
 *   holds no declared keys, any other one holds declared keys of the classified category and kind, and every key in a
 *   named store belongs to a registered one.
 * - (c) every key has exactly one category, every category except the data ones has at least one entry, and only
 *   `credentials` holds sensitive entries.
 * - (e) every deployment kind in use has a registered implementation or is pending (P7.6).
 *
 * zh-CN: ROADMAP T5 守卫 (a) (b) (c) (e) (P1.3). 注册表与已登记存储为 "已迁移", `classification.json` (P0.3) 为 "待迁移" 的初始
 * 允许清单. 只有两边都没有的名称 (新的 `key_*` 资源, 新的 `getSharedPreferences(` 文件名, 新的部署项种类), 允许清单中失效的条目,
 * 以及声明与允许清单不一致时才失败; 每个测试输出待迁移计数.
 */
class SettingsRegistryGuardTest {

    @Test
    fun a_everyKeyResourceIsRegisteredOrPending() {
        val rows = registryRows()
        val classified = classification.section("keys")
        val registeredByName = rows.filter { it.store == DEFAULT_STORE }.groupBy { it.name }
        val unknown = mutableListOf<String>()
        val mismatched = mutableListOf<String>()
        var pending = 0
        keyResources.forEach { (resName, value) ->
            val named = registeredByName[value]
            val entry = classified[resName]
            when {
                named != null -> entry?.let { mismatch(resName, named, it) }?.let(mismatched::add)
                entry != null -> pending++
                else -> unknown.add(resName)
            }
        }
        val stale = classified.keys - keyResources.keys
        val literals = classification.section("literalKeys")
        val strayRows = rows.filter { it.store == DEFAULT_STORE && it.name !in keyResources.values }.filter { row ->
            val entry = literals.entries.firstOrNull { matchesLiteral(it.key, row.name, row.family) }
            if (entry != null) mismatch(row.name, row, entry.value)?.let(mismatched::add)
            entry == null
        }
        println("T5 a: key_* resources ${keyResources.size}, registered ${keyResources.size - pending - unknown.size}, pending migration $pending ${kindCounts(classified, keyResources.keys.filter { registeredByName[keyResources[it]] == null })}")
        assertNone("key_* resources neither registered nor in classification.json (classify them, then declare them)", unknown)
        assertNone("classification.json keys without a key_* resource", stale.toList())
        assertNone("registered default-store names that are neither key_* values nor classification.json literal keys", strayRows.map { it.name })
        assertNone("declarations that disagree with classification.json (update one of them)", mismatched)
    }

    @Test
    fun b_everySharedPreferencesFileIsRegisteredOrPending() {
        val classified = classification.section("namedStores")
        // Reading SettingsRegistry.stores registers the declared stores (NamedStores).
        val declared = SettingsRegistry.stores
        val registeredIds = (declared + SettingsStores.registeredIds).filter { !it.isDefault }.associateBy { it.name }
        val registered = registeredIds.keys
        val calls = storeCalls()
        val unresolved = calls.filter { it.names.isEmpty() }
        val unknown = calls.filter { it.names.isNotEmpty() }.flatMap { call ->
            call.names.filter { it !in registered && it !in classified }.map { "$it (${call.where})" }
        }
        val seen = calls.flatMap { it.names }.toSet()
        val stale = classified.keys - seen - registered
        val pending = classified.keys - registered
        val rows = registryRows()
        val mismatched = registeredIds.values.mapNotNull { id -> storeMismatch(id, classified[id.name], rows.filter { it.store == id.name }) }
        val undeclaredStores = rows.filter { it.store != DEFAULT_STORE && it.store !in declared.map(StoreId::name) }.map { "${it.store}:${it.name}" }
        println("T5 b: getSharedPreferences( calls ${calls.size} in ${calls.map { it.file }.toSet().size} files, store names ${seen.size}, registered ${registered.size} ${kindCounts(classified, registered)}, pending migration ${pending.size} ${kindCounts(classified, pending)}")
        assertNone("getSharedPreferences( calls whose file name cannot be resolved (register the store or add the call to MANUAL_STORE_NAMES)", unresolved.map { "${it.where}: ${it.arg}" })
        assertNone("SharedPreferences files neither registered in SettingsStores nor in classification.json", unknown)
        assertNone("classification.json named stores neither registered nor opened by a getSharedPreferences( call", stale.toList())
        assertNone("registered stores that disagree with classification.json (update one of them)", mismatched)
        assertNone("declared keys in a named store that NamedStores does not declare", undeclaredStores)
    }

    @Test
    fun c_categoriesAreConsistent() {
        val rows = registryRows()
        // A candidate value may also name a stored key (P4.1); any other name is registered once.
        val duplicates = rows.groupBy { it.store to it.name }.filterValues { !isOnceOrCandidateAndKey(it) }.keys.map { "${it.first}:${it.second}" }
        assertNone("names registered more than once", duplicates)
        val categoryIds = SettingCategory.ALL.map { it.id }.toSet()
        val dataIds = SettingCategory.ALL.filter { it.isData }.map { it.id }.toSet()
        val problems = mutableListOf<String>()
        rows.forEach { row ->
            when (row.kind) {
                SettingKind.SETTING -> if (row.category == null || row.category == SettingCategory.OTHER.id || row.category in dataIds) problems.add("${row.name}: Setting in ${row.category}")
                SettingKind.STATE -> if (row.category != SettingCategory.OTHER.id) problems.add("${row.name}: State in ${row.category}")
                else -> if (row.category != null) problems.add("${row.name}: ${row.kind.label} with category ${row.category}")
            }
        }
        val declared = (SettingsRegistry.keys + SettingsRegistry.families.map { it.template }).filterIsInstance<SettingKey<*>>()
        declared.filter { it.sensitive != (it.category == SettingCategory.CREDENTIALS) }.forEach { problems.add("${it.store}:${it.name}: sensitive outside credentials") }
        val sensitive = declared.filter { it.sensitive }

        // The combined view: registered rows and the pending Setting / State entries of classification.json.
        val entries = mutableListOf<Pair<String, String>>()
        rows.filter { it.category != null }.forEach { entries.add(it.category!! to "${it.store}:${it.name}") }
        listOf("keys", "literalKeys", "namedStores").forEach { section ->
            classification.section(section).forEach { (name, entry) ->
                val kind = entry["kind"] as String
                val category = entry["category"] as String?
                if (kind == "Setting" || kind == "State") {
                    if (category == null || category !in categoryIds || category in dataIds) problems.add("$section/$name: $kind in $category")
                    if (kind == "State" && category != SettingCategory.OTHER.id) problems.add("$section/$name: State in $category")
                    if (kind == "Setting" && category == SettingCategory.OTHER.id) problems.add("$section/$name: Setting in other")
                    entries.add((category ?: "?") to "$section/$name")
                } else if (category != null) {
                    problems.add("$section/$name: $kind with category $category")
                }
                storedAs(entry)?.let { stored ->
                    if (kind != SettingKind.VALUE_CONSTANT.label || stored["kind"] != SettingKind.STATE.label || stored["category"] != SettingCategory.OTHER.id) {
                        problems.add("$section/$name: storedAs ${stored["kind"]} in ${stored["category"]} for a $kind (only a ValueConstant may also name a State)")
                    } else {
                        entries.add(SettingCategory.OTHER.id to "$section/$name (storedAs)")
                    }
                }
            }
        }
        val empty = SettingCategory.ALL.filter { !it.isData && entries.none { e -> e.first == it.id } }.map { it.id }
        val pendingKeyNames = classification.section("keys").filterValues { it["kind"] == "Setting" || it["kind"] == "State" }.keys.mapNotNull { keyResources[it] }
        val overlap = classification.section("literalKeys").filter { (name, entry) -> (entry["kind"] == "Setting" || entry["kind"] == "State") && entry["store"] == DEFAULT_STORE && name in pendingKeyNames }.keys
        println("T5 c: categories ${SettingCategory.ALL.size}, entries ${entries.size} (registered ${rows.count { it.category != null }}), sensitive declarations ${sensitive.size} in ${sensitive.map { it.store }.toSet().size} stores, all in credentials")
        assertNone("category problems", problems)
        assertNone("categories without any entry", empty)
        assertNone("names in two places", overlap.toList())
    }

    @Test
    fun e_deploymentKindsHaveImplementationsOrArePending() {
        val declared = SettingsRegistry.deploymentKinds(registryRows())
        val classified = listOf("keys", "literalKeys").flatMap { classification.section(it).values }.mapNotNull { it["deployment"] as String? }
        val known = DeploymentKind.values().map { it.name }.toSet()
        assertNone("classification.json deployment kinds that DeploymentKind does not define", classified.filter { it !in known })
        val used = declared + classified.map(DeploymentKind::valueOf)
        val pending = used - SettingsRegistry.DEPLOYMENT_IMPLEMENTATIONS
        println("T5 e: deployment kinds in use ${used.size}, implemented ${used.size - pending.size}, pending implementation (P7.6) ${pending.size} $pending")
        // Until P7.6 every kind in use is pending; afterwards the implementations must cover all of them.
        assertTrue(SettingsRegistry.DEPLOYMENT_IMPLEMENTATIONS.isEmpty() || pending.isEmpty())
    }

    @Test
    fun describeTurnsDeclarationsIntoRows() {
        val domain = object : SettingsDomain {
            override val keys = listOf<PrefKey<*>>(
                SettingKey.Str(name = "key_\$_working_directory", category = SettingCategory.EXPLORER, default = SettingDefault.computed("R.string.default_key_working_directory") { "Scripts" }, deployment = DeploymentKind.CREATE_DIRECTORY),
                SettingKey.Bool(name = "key_\$_foreground_service", category = SettingCategory.GENERAL, default = SettingDefault.of(false), effect = SettingEffect.APPLY),
                StateKey.long(name = "key_\$_last_updates_checked", default = SettingDefault.of(-1L)),
                CacheKey.str(name = "key_\$_root_manager_apps_cache", default = SettingDefault.None),
            )
            override val names = listOf(RegisteredName(kind = SettingKind.VALUE_CONSTANT, literalName = "key_\$_night_mode_follow_system"))
        }
        val rows = SettingsRegistry.describe(listOf(domain))
        assertEquals(
            listOf(
                SettingDescriptor("key_\$_working_directory", "string", "default", "explorer", "R.string.default_key_working_directory", SettingEffect.NONE, SettingKind.SETTING, DeploymentKind.CREATE_DIRECTORY),
                SettingDescriptor("key_\$_foreground_service", "bool", "default", "general", "false", SettingEffect.APPLY, SettingKind.SETTING),
                SettingDescriptor("key_\$_last_updates_checked", "long", "default", "other", "-1", SettingEffect.NONE, SettingKind.STATE),
                SettingDescriptor("key_\$_root_manager_apps_cache", "string", "default", null, null, SettingEffect.NONE, SettingKind.CACHE),
                SettingDescriptor("key_\$_night_mode_follow_system", "string", "default", null, null, SettingEffect.NONE, SettingKind.VALUE_CONSTANT),
            ),
            rows,
        )
        val grouped = SettingsRegistry.byCategory(rows)
        assertEquals(SettingCategory.ALL, grouped.keys.toList())
        assertEquals(listOf("key_\$_last_updates_checked"), grouped.getValue(SettingCategory.OTHER).map { it.name })
        assertEquals(setOf(DeploymentKind.CREATE_DIRECTORY), SettingsRegistry.deploymentKinds(rows))
        assertEquals(rows.map { it.toSpec() }, rows.map { it.toSpec().toDescriptor().toSpec() })
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private class StoreCall(val file: String, val line: Int, val arg: String, val names: List<String>) {
        val where get() = "$file:$line"
    }

    private fun registryRows(): List<SettingDescriptor> = SettingsRegistry.describe()

    /** One row, or a candidate value ([SettingKind.VALUE_CONSTANT]) and a stored key of the same name (P4.1). */
    private fun isOnceOrCandidateAndKey(rows: List<SettingDescriptor>): Boolean =
        rows.size == 1 || rows.size == 2 && rows.count { it.kind == SettingKind.VALUE_CONSTANT } == 1 && rows.none { it.kind == SettingKind.EXCLUDED }

    @Suppress("UNCHECKED_CAST")
    private fun storedAs(entry: Map<String, Any?>): Map<String, Any?>? = entry["storedAs"] as Map<String, Any?>?

    /**
     * The rows of one `key_*` value against its classification [entry]. A candidate value that also names a stored key
     * (P4.1: `key_timed_task_backend_alarm` is a candidate of the timed task backend and the name of the exact alarm
     * prompt) has two rows: the ValueConstant row agrees with the entry and the other one with its "storedAs" part.
     * zh-CN: 一个 `key_*` 值的登记行与其分类条目的核对. 同时作为存储键名的候选值 (P4.1) 有两行: 设置值常量行对应条目本身,
     * 另一行对应条目的 "storedAs" 部分.
     */
    private fun mismatch(name: String, rows: List<SettingDescriptor>, entry: Map<String, Any?>): String? {
        val stored = storedAs(entry)
        if (rows.size == 1) {
            val row = rows.single()
            return if (stored != null) "$name: classified with storedAs ${stored["kind"]}, declared only as ${row.kind.label}" else mismatch(name, row, entry)
        }
        if (!isOnceOrCandidateAndKey(rows)) return "$name: registered ${rows.size} times (${rows.map { it.kind.label }})"
        stored ?: return "$name: also declared as ${rows.first { it.kind != SettingKind.VALUE_CONSTANT }.kind.label}, classification.json has no storedAs"
        return mismatch(name, rows.first { it.kind == SettingKind.VALUE_CONSTANT }, entry)
            ?: mismatch(name, rows.first { it.kind != SettingKind.VALUE_CONSTANT }, stored)
    }

    private fun mismatch(name: String, row: SettingDescriptor, entry: Map<String, Any?>): String? {
        val kind = entry["kind"] as String
        val category = entry["category"] as String?
        val deployment = entry["deployment"] as String?
        val effect = entry["effect"] as String? ?: SettingEffect.NONE.name
        return when {
            row.kind.label != kind -> "$name: declared ${row.kind.label}, classified $kind"
            row.category != category -> "$name: declared in ${row.category}, classified in $category"
            row.deployment?.name != deployment -> "$name: declared deployment ${row.deployment}, classified $deployment"
            row.kind == SettingKind.SETTING && row.effect.name != effect -> "$name: declared effect ${row.effect}, classified $effect"
            else -> null
        }
    }

    /** Why a registered store [id] disagrees with its classification [entry], given the declared [rows] in it; null when it agrees. */
    private fun storeMismatch(id: StoreId, entry: Map<String, Any?>?, rows: List<SettingDescriptor>): String? {
        val kind = entry?.get("kind") as String? ?: return "${id.name}: registered but not in classification.json"
        val category = entry["category"] as String?
        return when {
            id.excluded != (kind == SettingKind.EXCLUDED.label) -> "${id.name}: registered ${if (id.excluded) "excluded" else "as a store"}, classified $kind"
            id.excluded -> if (rows.isNotEmpty()) "${id.name}: excluded but declares ${rows.size} keys" else null
            rows.isEmpty() -> "${id.name}: registered without declared keys"
            rows.none { it.category == category } -> "${id.name}: classified in $category, declared in ${rows.map { it.category }.toSet()}"
            kind == SettingKind.STATE.label && rows.any { it.kind != SettingKind.STATE } -> "${id.name}: classified State, declares ${rows.map { it.kind.label }.toSet()}"
            kind == SettingKind.SETTING.label && rows.none { it.kind == SettingKind.SETTING } -> "${id.name}: classified Setting, declares no Setting"
            else -> null
        }
    }

    private fun kindCounts(section: Map<String, Map<String, Any?>>, names: Collection<String>): String =
        names.mapNotNull { section[it]?.get("kind") as String? }.groupingBy { it }.eachCount().toSortedMap().toString()

    private fun assertNone(message: String, items: List<String>) {
        if (items.isNotEmpty()) fail("$message (${items.size}):\n  " + items.sorted().joinToString("\n  "))
    }

    companion object {

        private const val DEFAULT_STORE = "default"

        /**
         * `getSharedPreferences(` calls whose argument the generic rules cannot resolve: (file, argument) -> names.
         * Same cases as `MANUAL_STORE_NAMES` in tools/settings/inventory.py.
         */
        private val MANUAL_STORE_NAMES = mapOf(
            ("runtime/api/augment/storages/Storages.kt" to "it") to listOf("autojs.localstorage.*"),
            ("ui/wizard/WizardProfileStore.kt" to "spName") to listOf("permission_wizard", "plugin_install_wizard"),
            ("engine/NodeBridgeProtocol.kt" to "storagePreferencesName(storeName)") to listOf("autojs.node.storage.*.*.*"),
        )

        private lateinit var root: File
        private lateinit var classification: Map<String, Any?>
        private lateinit var keyResources: Map<String, String>
        private lateinit var sources: Map<String, String>
        private lateinit var resources: AutoCloseable

        @BeforeClass
        @JvmStatic
        fun load() {
            // Declared keys resolve their names from res/values in JVM tests (P2).
            resources = SettingsTestResources.install()
            root = SettingsTestResources.root
            @Suppress("UNCHECKED_CAST")
            classification = SettingsJson.parse(File(root, "tools/settings/classification.json").readText()) as Map<String, Any?>
            val resources = LinkedHashMap<String, String>()
            File(root, "app/src/main/res/values").listFiles { f -> f.name.endsWith(".xml") }!!.sorted().forEach { file ->
                Regex("<string\\s+name=\"(key_[^\"]+)\"[^>]*>([^<]*)</string>").findAll(file.readText()).forEach { resources[it.groupValues[1]] = it.groupValues[2] }
            }
            keyResources = resources
            val base = File(root, "app/src/main/java/org/autojs/autojs")
            sources = base.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
                .associate { it.relativeTo(base).invariantSeparatorsPath to it.readText() }
        }

        @AfterClass
        @JvmStatic
        fun restore() {
            resources.close()
        }

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.section(name: String): Map<String, Map<String, Any?>> =
            (this[name] as Map<String, Any?>).filterKeys { !it.startsWith("_") }.mapValues { it.value as Map<String, Any?> }

        /**
         * A classification literal key matches a name exactly, as a prefix (ends with "_") or as a glob ("*"); the row
         * of a key family (named by its prefix, P3) matches the prefix entry itself or a glob that starts with it.
         */
        private fun matchesLiteral(pattern: String, name: String, family: Boolean = false): Boolean = when {
            family -> pattern == name || ("*" in pattern && pattern.substringBefore("*") == name)
            "*" in pattern -> Regex(pattern.split("*").joinToString(".*") { Regex.escape(it) }).matches(name)
            pattern.endsWith("_") -> name.startsWith(pattern)
            else -> name == pattern
        }

        private fun storeCalls(): List<StoreCall> = sources.filterKeys { !it.startsWith("core/pref/store/") }.flatMap { (file, text) ->
            Regex("""\bgetSharedPreferences\(""").findAll(text).mapNotNull { match ->
                val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                val lineText = text.lineSequence().drop(line - 1).first().trim()
                if (lineText.startsWith("//") || lineText.startsWith("*") || lineText.startsWith("/*")) return@mapNotNull null
                val arg = firstArgument(text, match.range.last + 1)
                StoreCall(file, line, arg, MANUAL_STORE_NAMES[file to arg] ?: listOfNotNull(resolveName(arg, file, text)))
            }.toList()
        }

        private fun firstArgument(text: String, start: Int): String {
            var depth = 0
            var i = start
            var quote: Char? = null
            while (i < text.length) {
                val c = text[i]
                when {
                    quote != null -> if (c == '\\') i++ else if (c == quote) quote = null
                    c == '"' || c == '\'' -> quote = c
                    c == '(' || c == '[' || c == '{' -> depth++
                    (c == ')' || c == ']' || c == '}') && depth > 0 -> depth--
                    (c == ',' || c == ')') && depth == 0 -> return text.substring(start, i).trim()
                }
                i++
            }
            return text.substring(start).trim()
        }

        /** Resolves a file name expression: "literal", CONSTANT / Owner.CONSTANT, R.string.x, prefix + rest. */
        private fun resolveName(expr: String, file: String, text: String): String? {
            Regex("^\"([^\"]*)\"$").find(expr)?.let { return it.groupValues[1] }
            Regex("""R\.string\.(\w+)""").find(expr)?.let { return keyResources[it.groupValues[1]] }
            val plus = expr.indexOf(" + ")
            if (plus > 0) return resolveName(expr.substring(0, plus).trim(), file, text)?.let { "$it*" }
            val m = Regex("""^(?:(\w+)\.)?([A-Za-z_]\w*)$""").find(expr) ?: return null
            val owner = m.groupValues[1]
            val name = m.groupValues[2]
            val decl = Regex("""\b${Regex.escape(name)}\b\s*(?::\s*String\s*)?=\s*"([^"]*)"""")
            decl.find(text)?.let { return it.groupValues[1] }
            if (owner.isNotEmpty()) {
                sources.entries.firstOrNull { it.key.substringAfterLast('/').substringBeforeLast('.') == owner }?.value?.let { ownerText ->
                    decl.find(ownerText)?.let { return it.groupValues[1] }
                }
            }
            return null
        }
    }
}
