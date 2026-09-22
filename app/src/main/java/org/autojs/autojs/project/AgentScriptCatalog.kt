package org.autojs.autojs.project

import com.google.gson.JsonObject
import org.autojs.autojs.script.JavaScriptSource
import org.autojs.plugin.ai.agent.api.AiAgentContract
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * A bounded catalog of explicitly registered scripts. Callers supply host-approved extra roots;
 * per-request roots can only narrow those roots. No script source is returned to the plugin.
 *
 * Directory depth is counted below a scan root: work-directory depth 0..4, its agent subtree
 * through total depth 8, and configured roots through depth 4. Canonical containment is checked
 * again for each directory, registration file and executable, including cached entries.
 */
class AgentScriptCatalog(
    workingDirectory: File,
    userVisibleRoots: List<File>,
    configuredRoots: List<File> = emptyList(),
) {
    private val workingRoot = canonical(workingDirectory)
    private val visibleRoots = userVisibleRoots.map(::canonical).distinctBy(File::getPath)
    private var additionalRoots = validateConfiguredRoots(configuredRoots)
    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > MAX_CACHED_FILES
    }

    /** Atomically replaces the approved roots; a failed validation retains the previous roots. */
    @Synchronized
    fun updateConfiguredRoots(roots: List<File>) {
        val validated = validateConfiguredRoots(roots)
        additionalRoots = validated
        cache.clear()
    }

    /** Invalid registrations are skipped; explicit read/execute requests fail instead. */
    @Synchronized
    fun listScripts(roots: List<String>? = null, query: String? = null, limit: Int = AiAgentContract.MAX_SCRIPT_CATALOG_ENTRIES): List<AgentScriptEntry> {
        if (limit !in 1..AiAgentContract.MAX_SCRIPT_CATALOG_ENTRIES ||
            (query?.toByteArray(Charsets.UTF_8)?.size ?: 0) > AiAgentContract.MAX_GOAL_BYTES) {
            throw AgentManifestException("INVALID_REQUEST", "Script catalog query is invalid.")
        }
        val plans = requestedPlans(roots)
        val matches = mutableListOf<AgentScriptEntry>()
        val visitedDepths = HashMap<String, Int>()
        val emitted = HashSet<String>()
        val needle = query?.trim()?.lowercase(Locale.ROOT).orEmpty()
        var bytes = 2
        var visitedCount = 0
        var stopped = false
        fun append(file: File) {
            if (stopped) return
            val candidate = try { readResolved(file) } catch (_: AgentManifestException) { return }
            if (!emitted.add(candidate.entry.path)) return
            val entry = candidate.entry
            if (needle.isNotEmpty() && !(entry.id + " " + entry.description + " " +
                    entry.tags.joinToString(" ") + " " + entry.examples.joinToString(" ")).lowercase(Locale.ROOT).contains(needle)) return
            val next = entry.toJsonObject().toString().toByteArray(Charsets.UTF_8).size + if (matches.isEmpty()) 0 else 1
            if (next > AiAgentContract.MAX_SCRIPT_CATALOG_BYTES - bytes) {
                stopped = true
                return
            }
            matches += entry
            bytes += next
            if (matches.size >= limit) stopped = true
        }
        fun scan(directory: File, plan: ScanRoot, depth: Int) {
            if (stopped || depth > plan.depth || Thread.currentThread().isInterrupted) return
            val current = runCatching { directory.canonicalFile }.getOrNull() ?: return
            if (!contains(plan.root, current) || !current.isDirectory) return
            val remainingDepth = plan.depth - depth
            // Overlapping roots may authorize a deeper independent scan of the same directory.
            // A shallower visit must not consume the later root's remaining depth budget.
            if ((visitedDepths[current.path] ?: -1) >= remainingDepth) return
            visitedDepths[current.path] = remainingDepth
            // A directory can contain many unregistered files. Bound traversal independently
            // of result count; exhausting this budget returns only the already collected entries.
            if (++visitedCount > MAX_VISITED_ENTRIES) { stopped = true; return }
            append(current)
            if (stopped) return
            var accepted = 0
            val remaining = MAX_VISITED_ENTRIES - visitedCount
            // java.io keeps API 24 compatibility. Limit File objects retained for sorting even
            // though the platform's directory listing may internally allocate all names.
            val children = current.listFiles { _, name ->
                !name.startsWith(".") && accepted++ < remaining
            }?.sortedWith(compareBy<File> { it.name.lowercase(Locale.ROOT) }.thenBy { it.name }).orEmpty()
            for (child in children) {
                if (stopped || Thread.currentThread().isInterrupted) return
                if (++visitedCount > MAX_VISITED_ENTRIES) { stopped = true; return }
                if (child.name.startsWith(".")) continue
                val target = runCatching { child.canonicalFile }.getOrNull() ?: continue
                if (!contains(plan.root, target)) continue
                if (target.isDirectory) {
                    // Give agent/ its own deeper plan instead of marking it visited at depth 4.
                    if (current == workingRoot && child.name == "agent") continue
                    scan(target, plan, depth + 1)
                } else if (isScript(target)) append(target)
            }
        }
        plans.forEach { if (!stopped) scan(it.root, it, 0) }
        return matches
    }

    @Synchronized
    fun readManifest(path: String): AgentScriptEntry = readResolved(resolveAllowed(path)).entry

    /** Reads the current registration again before execution; cache invalidates on mtime or size. */
    @Synchronized
    fun resolveForExecution(path: String, arguments: JsonObject): AgentScriptInvocation {
        val resolved = readResolved(resolveAllowed(path))
        val checkedArguments = AgentManifestParser.validateArguments(resolved.entry.manifest, arguments)
        return AgentScriptInvocation(resolved.entry, resolved.scriptFile, resolved.entry.path, checkedArguments)
    }

    private fun readResolved(path: File): Resolved = try {
        val canonicalPath = canonical(path)
        requireAllowed(canonicalPath)
        val projectRoot = when {
            canonicalPath.isDirectory -> canonicalPath
            canonicalPath.name == ProjectConfig.CONFIG_FILE_NAME -> canonicalPath.parentFile
            else -> null
        }
        if (projectRoot != null) {
            requireAllowed(projectRoot)
            val manifestFile = File(projectRoot, ProjectConfig.CONFIG_FILE_NAME).canonicalFile
            if (!contains(projectRoot, manifestFile) || !manifestFile.isFile) missing()
            requireAllowed(manifestFile)
            val cached = cached(manifestFile, project = true, fallbackId = projectRoot.name)
            val manifest = cached.manifest ?: missing()
            val main = cached.main ?: missing()
            val declared = File(main)
            if (declared.isAbsolute || main.replace('\\', '/').split('/').any { it == ".." }) missing()
            val script = File(projectRoot, main).canonicalFile
            if (!contains(projectRoot, script) || !isScript(script)) missing()
            requireAllowed(script)
            Resolved(AgentScriptEntry(projectRoot.path, "project", manifest,
                maxOf(manifestFile.lastModified(), script.lastModified())), script)
        } else {
            if (!isScript(canonicalPath)) missing()
            val manifest = cached(canonicalPath, project = false, fallbackId = canonicalPath.nameWithoutExtension).manifest ?: missing()
            Resolved(AgentScriptEntry(canonicalPath.path, "file", manifest, canonicalPath.lastModified()), canonicalPath)
        }
    } catch (failure: AgentManifestException) {
        throw failure
    } catch (_: Exception) {
        missing()
    }

    private fun cached(file: File, project: Boolean, fallbackId: String): Cached {
        val key = file.path
        val mtime = file.lastModified()
        val length = file.length()
        cache[key]?.takeIf { it.modified == mtime && it.length == length }?.let {
            if (it.invalid) missing()
            // Do not give a mutable Gson tree shared ownership across catalog invocations.
            return it.copy(manifest = it.manifest?.snapshot())
        }
        val value = try {
            val bytes = readPrefix(file, if (project) MAX_PROJECT_JSON_BYTES + 1 else AiAgentContract.MAX_MANIFEST_HEADER_BYTES)
            if (project && bytes.size > MAX_PROJECT_JSON_BYTES) missing()
            if (project) {
                val json = decode(bytes)
                val manifest = AgentManifestParser.parseProject(json, fallbackId)
                Cached(mtime, length, manifest, manifest?.let { AgentManifestParser.projectMain(json) })
            } else Cached(mtime, length, AgentManifestParser.parseHeader(bytes, fallbackId), null)
        } catch (_: Exception) {
            cache[key] = Cached(mtime, length, null, null, invalid = true)
            missing()
        }
        // A concurrent writer must not make the cache label new bytes with an earlier timestamp.
        if (file.lastModified() != mtime || file.length() != length) missing()
        cache[key] = value
        return value.copy(manifest = value.manifest?.snapshot())
    }

    private fun requestedPlans(requested: List<String>?): List<ScanRoot> {
        val base = mutableListOf(ScanRoot(workingRoot, AiAgentContract.MAX_SCRIPT_SCAN_DEPTH))
        val agent = File(workingRoot, "agent").canonicalFile
        if (contains(workingRoot, agent) && agent.isDirectory) base += ScanRoot(agent, 7)
        additionalRoots.forEach { base += ScanRoot(it, AiAgentContract.MAX_SCRIPT_SCAN_DEPTH) }
        if (requested == null) return base.distinctBy { it.root.path }
        if (requested.isEmpty()) return emptyList()
        return requested.flatMap { path ->
            val root = resolveAllowed(path)
            if (!root.isDirectory) denied()
            // Narrowing the root must not reset the configured scan depth.
            val containing = base.filter { contains(it.root, root) }.maxByOrNull { it.depth - relativeDepth(it.root, root) } ?: denied()
            val remaining = containing.depth - relativeDepth(containing.root, root)
            if (remaining < 0) denied()
            listOf(ScanRoot(root, remaining)) + base.filter { it.root != root && contains(root, it.root) }
        }.distinctBy { it.root.path }
    }

    private fun validateConfiguredRoots(roots: List<File>): List<File> = roots.map { raw ->
        val root = canonical(raw)
        if (!root.isDirectory || contains(root, workingRoot) ||
            visibleRoots.none { visible -> contains(visible, root) && visibleDirectory(visible, root) }) denied()
        root
    }.distinctBy(File::getPath)

    private fun visibleDirectory(visible: File, candidate: File): Boolean {
        val parts = relativeSegments(visible, candidate)
        if (parts.any { it.startsWith(".") }) return false
        return parts.windowed(2).none { it[0].equals("Android", true) &&
            (it[1].equals("data", true) || it[1].equals("obb", true)) }
    }

    private fun resolveAllowed(path: String): File {
        if (path.isBlank() || path.contains('\u0000') || path.replace('\\', '/').split('/').any { it == ".." }) denied()
        val file = File(path).let { if (it.isAbsolute) it else File(workingRoot, path) }
        return canonical(file).also(::requireAllowed)
    }

    private fun requireAllowed(file: File) {
        if ((listOf(workingRoot) + additionalRoots).none { contains(it, file) }) denied()
    }

    private fun relativeDepth(root: File, file: File): Int =
        relativeSegments(root, file).size

    private data class ScanRoot(val root: File, val depth: Int)
    private data class Resolved(val entry: AgentScriptEntry, val scriptFile: File)
    private data class Cached(val modified: Long, val length: Long, val manifest: AgentManifest?, val main: String?, val invalid: Boolean = false)

    companion object {
        private const val MAX_CACHED_FILES = 1000
        private const val MAX_VISITED_ENTRIES = 20_000
        private const val MAX_PROJECT_JSON_BYTES = AiAgentContract.MAX_SCRIPT_CATALOG_BYTES

        private fun canonical(file: File): File = try { file.canonicalFile } catch (_: Exception) { denied() }
        private fun contains(root: File, file: File): Boolean = root == file || file.path.startsWith(
            root.path.trimEnd(File.separatorChar) + File.separator,
            ignoreCase = File.separatorChar == '\\',
        )
        private fun relativeSegments(root: File, file: File): List<String> =
            if (root == file) emptyList() else file.path.substring(root.path.length).trimStart(File.separatorChar)
                .split(File.separatorChar).filter(String::isNotEmpty)
        private fun isScript(file: File) = file.isFile && JavaScriptSource.isJavaScriptFileName(file.name)
        private fun denied(): Nothing = throw AgentManifestException("CAPABILITY_DENIED", "Script path is outside the approved roots.")
        private fun missing(): Nothing = throw AgentManifestException("SCRIPT_NOT_REGISTERED", "Script registration is missing or invalid.")
        private fun decode(bytes: ByteArray) = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        private fun readPrefix(file: File, maximumBytes: Int): ByteArray = FileInputStream(file).use { input ->
            val bytes = ByteArray(minOf(file.length().coerceAtLeast(0), maximumBytes.toLong()).toInt())
            var count = 0
            while (count < bytes.size) {
                val read = input.read(bytes, count, bytes.size - count)
                if (read < 0) break
                count += read
            }
            bytes.copyOf(count)
        }
        private fun AgentManifest.snapshot() = copy(parameters = parameters.deepCopy(), result = result?.deepCopy(),
            examples = examples.toList(), tags = tags.toList())
    }
}
