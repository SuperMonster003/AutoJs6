package org.autojs.autojs.core.accessibility.codegen.store

import com.google.gson.JsonObject
import org.autojs.autojs.core.accessibility.codegen.features.FeatureSchema
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.array
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.flag
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.integer
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.obj
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogJson.string
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogFetcher
import org.autojs.autojs.ui.edit.editor.ace.font.FontCatalogSignatureVerifier
import org.autojs.autojs.ui.edit.editor.ace.font.FontIo
import org.autojs.autojs.ui.edit.editor.ace.font.OkHttpFontCatalogFetcher
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.CancellationException

/** Explicit refresh only: constructors/local reads never fetch. The signed APK bootstrap is
 * display-only, so an empty v1 bootstrap does not occupy the first remote v1 catalog identity.
 * Remote trust history is committed before cache replacement; interrupted writes cannot revive
 * withdrawn versions through an older cache or bootstrap.
 * zh-CN: 仅显式刷新才联网. 已签名 APK 引导目录仅供离线展示, 不占用首发远端目录版本号.
 * 远端信任历史先于缓存持久化, 写入中断时旧缓存或引导目录不能恢复已撤回版本的下载权限. */
class ModelCatalogRepository(
    private val directory: File,
    private val catalogUrl: String = DEFAULT_CATALOG_URL,
    private val signatureUrl: String = DEFAULT_SIGNATURE_URL,
    hostVersionCode: Int,
    private val signatureVerifier: FontCatalogSignatureVerifier,
    private val fetcher: FontCatalogFetcher = OkHttpFontCatalogFetcher(),
    bootstrapEnvelopeBytes: ByteArray? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val expectedRepository: String = ModelCatalogParser.OFFICIAL_REPOSITORY,
    supportedSchemas: IntRange = FeatureSchema.SUPPORTED_SCHEMA,
    expectedNamesHash: (Int) -> String? = FeatureSchema::namesHashOrNull,
) {
    private val parser = ModelCatalogParser(hostVersionCode, supportedSchemas, expectedNamesHash, expectedRepository)
    private val cacheFile = File(directory, CACHE_FILE_NAME)
    private val stateFile = File(directory, STATE_FILE_NAME)
    private val lockFile = FontIo.catalogLockFile(directory)
    private val bootstrapSnapshot = bootstrapEnvelopeBytes?.copyOf()?.let(::readBootstrap)

    @Volatile
    private var memorySnapshot: Snapshot? = null

    /** Signed bootstrap metadata never authorizes downloading and never advances remote history. */
    fun bootstrap(): ModelCatalog? = bootstrapSnapshot?.catalog?.copy(remoteArtifactsAllowed = false)

    fun current(): ModelCatalog? = loadBestAvailable()

    /** Holds catalog -> store lock order across an authorized installation. No network access.
     * zh-CN: 安装期间按目录锁 -> 存储锁的顺序保持授权有效, 本方法不联网. */
    fun <T> withDownloadAuthorization(
        modelId: String,
        requested: CatalogModelVersion,
        action: (catalogVersion: Int, version: CatalogModelVersion) -> T,
    ): T {
        val result = locked {
            val catalog = bestAvailableLocked()?.catalog
                ?: throw ModelDownloadAuthorizationException("No verified model catalog is available")
            val current = catalog.find(modelId)?.versions?.firstOrNull { it.versionCode == requested.versionCode }
                ?: throw ModelDownloadAuthorizationException("Model version is no longer present in the catalog")
            if (!requested.canDownload || !catalog.canDownload(modelId, current.versionCode)) {
                throw ModelDownloadAuthorizationException("Model version is not authorized for download")
            }
            // Compare all immutable values, not just a caller-copyable fingerprint. Deprecation may
            // change while an explicit download is in progress; revoked/incompatible versions cannot.
            if (requested.copy(deprecated = current.deprecated, revoked = current.revoked,
                    compatibility = current.compatibility) != current) {
                throw ModelDownloadAuthorizationException("Requested artifact differs from the verified catalog")
            }
            // Preserve installation/cancellation exceptions instead of mislabelling them as
            // catalog-storage failures in locked().
            runCatching { action(catalog.catalogVersion, current) }
        }
        return result.getOrThrow()
    }

    /** Reads the verified remote cache. A stale/uncertain cache remains explicitly display-only. */
    fun loadCached(): ModelCatalog? = locked {
        val snapshot = if (recover(cacheFile)) readCache() else null
        snapshot?.let { authorizeCached(it, readState()).catalog }
    }

    /** Local cache, verified memory, then signed display-only bootstrap; no network access. */
    fun loadBestAvailable(): ModelCatalog? = try {
        locked { bestAvailableLocked()?.catalog }
    } catch (_: ModelCatalogStorageException) {
        // If even the lock is inaccessible, the durable head cannot be checked. Retain display
        // metadata without allowing an old in-memory/bootstrap catalog to authorize downloads.
        listOfNotNull(memorySnapshot, bootstrapSnapshot).maxByOrNull { it.catalog.catalogVersion }
            ?.catalog?.copy(remoteArtifactsAllowed = false)
    }

    /** A user-triggered refresh reuses remote data for 24 h; force=true performs an explicit check. */
    fun refresh(force: Boolean = false): ModelCatalog {
        if (!force) {
            val fresh = locked {
                val snapshot = bestAvailableLocked()
                val fetchedAt = snapshot?.fetchedAtMillis
                val now = now()
                snapshot?.takeIf { it.catalog.remoteArtifactsAllowed && fetchedAt != null && now >= fetchedAt && now - fetchedAt < CACHE_TTL_MILLIS }
            }
            if (fresh != null) return fresh.catalog
        }
        val raw = fetch(catalogUrl, ModelCatalogParser.MAX_CATALOG_BYTES)
        val signature = fetch(signatureUrl, MAX_SIGNATURE_BYTES)
        verify(raw, signature)
        val candidate = Snapshot(parser.parse(raw), raw, signature, ModelCatalogJson.digest(raw), now())
        return locked {
            val status = readState()
            if (status is StateStatus.Invalid) throw ModelCatalogRollbackException("Model catalog trust history is corrupt")
            val durable = (status as? StateStatus.Present)?.state
            var previous = durable
            val stored = attempt { if (recover(cacheFile)) readCache() else null }
            for (baseline in listOfNotNull(memorySnapshot, stored).sortedBy { it.catalog.catalogVersion }) {
                val mergedBaseline = attempt { mergeState(baseline, previous) }
                if (mergedBaseline != null) previous = mergedBaseline
            }
            val merged = mergeState(candidate, previous)
            // Accept revocations durably first. A cache write failure may reduce availability,
            // but a surviving old cache can then only be displayed.
            if (merged != durable) writeState(merged)
            writeCache(candidate)
            memorySnapshot = candidate
            candidate.catalog
        }
    }

    private fun bestAvailableLocked(): Snapshot? {
        val status = readState()
        val disk = attempt { if (recover(cacheFile)) readCache() else null }
        if (disk != null) {
            val authorized = attempt { authorizeCached(disk, status) }
            if (authorized?.catalog?.remoteArtifactsAllowed == true) return authorized
        }
        val memory = memorySnapshot
        if (memory != null && status is StateStatus.Present && matchesHead(memory, status.state)) return memory
        // Every fallback is display-only. In particular, bootstrap is never treated as remote,
        // including when it happens to share a catalog version or digest with the remote head.
        return listOfNotNull(disk, memory, bootstrapSnapshot).maxByOrNull { it.catalog.catalogVersion }
            ?.let { it.copy(catalog = it.catalog.copy(remoteArtifactsAllowed = false)) }
    }

    private fun authorizeCached(snapshot: Snapshot, status: StateStatus): Snapshot {
        if (status is StateStatus.Invalid) return snapshot.copy(catalog = snapshot.catalog.copy(remoteArtifactsAllowed = false))
        return try {
            val previous = (status as? StateStatus.Present)?.state
            val merged = mergeState(snapshot, previous)
            if (merged != previous) writeState(merged)
            snapshot.also { memorySnapshot = it }
        } catch (error: ModelCatalogRollbackException) {
            snapshot.copy(catalog = snapshot.catalog.copy(remoteArtifactsAllowed = false))
        }
    }

    private fun mergeState(snapshot: Snapshot, previous: AcceptedState?): AcceptedState {
        val catalog = snapshot.catalog
        if (previous != null) {
            if (catalog.catalogVersion < previous.catalogVersion) throw ModelCatalogRollbackException("Catalog rollback rejected")
            if (catalog.catalogVersion == previous.catalogVersion && snapshot.digest != previous.digest) {
                throw ModelCatalogRollbackException("Same catalog version has different signed bytes")
            }
            if (ModelCatalogParser.timestamp(catalog.generatedAt).isBefore(ModelCatalogParser.timestamp(previous.generatedAt))) {
                throw ModelCatalogRollbackException("Catalog timestamp moved backwards")
            }
        }
        val known = LinkedHashMap(previous?.versions.orEmpty())
        for (model in catalog.models) {
            val highest = previous?.versions?.keys?.filter { it.modelId == model.modelId }?.maxOfOrNull { it.versionCode } ?: 0
            for (version in model.versions) {
                val key = VersionKey(model.modelId, version.versionCode)
                val old = known[key]
                if (old != null) {
                    if (old.sha256 != version.artifact.sha256 || old.fingerprint != version.immutableFingerprint) {
                        throw ModelCatalogRollbackException("Previously observed model version metadata changed")
                    }
                    if (old.revoked && !version.revoked || old.deprecated && !version.deprecated) {
                        throw ModelCatalogRollbackException("Model revocation/deprecation cannot be reversed")
                    }
                } else if (version.versionCode <= highest) {
                    throw ModelCatalogRollbackException("New model version does not exceed the observed version history")
                }
                known[key] = KnownVersion(version.artifact.sha256, version.immutableFingerprint, version.revoked, version.deprecated)
            }
        }
        if (known.size > ModelCatalogParser.MAX_HISTORY_ENTRIES) throw ModelCatalogStorageException("Model version history limit exceeded")
        return AcceptedState(catalog.catalogVersion, snapshot.digest, catalog.generatedAt, known)
    }

    private fun matchesHead(snapshot: Snapshot, state: AcceptedState): Boolean =
        snapshot.catalog.catalogVersion == state.catalogVersion && snapshot.digest == state.digest &&
            snapshot.catalog.models.all { model -> model.versions.all { version ->
                val known = state.versions[VersionKey(model.modelId, version.versionCode)]
                known != null && known.sha256 == version.artifact.sha256 && known.fingerprint == version.immutableFingerprint &&
                    known.revoked == version.revoked && known.deprecated == version.deprecated
            } }

    private fun fetch(url: String, limit: Int): ByteArray = try {
        fetcher.fetch(url, limit.toLong()).copyOf().also { require(it.isNotEmpty() && it.size <= limit) { "Fetched catalog resource exceeds its size bounds" } }
    } catch (error: Exception) {
        rethrowCancellation(error)
        throw ModelCatalogNetworkException("Unable to retrieve model catalog resource", error)
    }

    private fun verify(raw: ByteArray, signature: ByteArray) {
        try {
            val envelope = ModelCatalogJson.parse(signature, MAX_SIGNATURE_BYTES)
            require(envelope.keySet() == setOf("algorithm", "catalog", "keyId", "signature")) { "Invalid signature envelope fields" }
            require(string(envelope["algorithm"]) == "ECDSA_P256_SHA256" && string(envelope["catalog"]) == "catalog-v1.json") { "Wrong signature namespace" }
            require(Regex("[A-Za-z0-9._-]{1,64}").matches(string(envelope["keyId"], 64))) { "Invalid signature key id" }
            string(envelope["signature"], MAX_SIGNATURE_BYTES)
            signatureVerifier.verify(raw, signature)
        } catch (error: Exception) {
            rethrowCancellation(error)
            throw ModelCatalogSignatureException("Model catalog signature verification failed", error)
        }
    }

    private fun readBootstrap(bytes: ByteArray): Snapshot = try {
        val envelope = ModelCatalogJson.parse(bytes, MAX_BOOTSTRAP_BYTES)
        require(envelope.keySet() == setOf("format", "catalogBase64", "signature") && integer(envelope["format"]) == 1L) { "Invalid signed bootstrap envelope" }
        val raw = ModelCatalogJson.base64(string(envelope["catalogBase64"], MAX_BOOTSTRAP_BYTES), ModelCatalogParser.MAX_CATALOG_BYTES)
        val signature = obj(envelope["signature"]).toString().toByteArray(Charsets.UTF_8)
        verify(raw, signature)
        Snapshot(parser.parse(raw).copy(remoteArtifactsAllowed = false), raw, signature, ModelCatalogJson.digest(raw), null)
    } catch (error: ModelCatalogException) {
        throw error
    } catch (error: Exception) {
        throw ModelCatalogParseException("Invalid signed model bootstrap", error)
    }

    private fun readCache(): Snapshot = try {
        require(cacheFile.length() in 25..(ModelCatalogParser.MAX_CATALOG_BYTES.toLong() + MAX_SIGNATURE_BYTES + 24)) { "Cached catalog exceeds size bounds" }
        DataInputStream(cacheFile.inputStream().buffered()).use { input ->
            require(input.readInt() == CACHE_MAGIC && input.readInt() == 1) { "Unsupported model catalog cache" }
            val fetchedAt = input.readLong().also { require(it >= 0) }
            val rawLength = input.readInt().also { require(it in 1..ModelCatalogParser.MAX_CATALOG_BYTES) }
            val signatureLength = input.readInt().also { require(it in 1..MAX_SIGNATURE_BYTES) }
            val raw = ByteArray(rawLength).also(input::readFully)
            val signature = ByteArray(signatureLength).also(input::readFully)
            require(input.read() == -1) { "Trailing cached catalog bytes" }
            verify(raw, signature)
            Snapshot(parser.parse(raw), raw, signature, ModelCatalogJson.digest(raw), fetchedAt)
        }
    } catch (error: ModelCatalogException) {
        throw error
    } catch (error: Exception) {
        rethrowCancellation(error)
        throw ModelCatalogStorageException("Unable to read cached model catalog", error)
    }

    private fun writeCache(snapshot: Snapshot) = store("model catalog cache") {
        FontIo.writeAndReplace(cacheFile) { stream ->
            val output = DataOutputStream(stream)
            output.writeInt(CACHE_MAGIC)
            output.writeInt(1)
            output.writeLong(requireNotNull(snapshot.fetchedAtMillis))
            output.writeInt(snapshot.raw.size)
            output.writeInt(snapshot.signature.size)
            output.write(snapshot.raw)
            output.write(snapshot.signature)
        }
    }

    private fun readState(): StateStatus {
        return try {
            if (!recover(stateFile)) return StateStatus.Absent
            val raw = DataInputStream(stateFile.inputStream().buffered()).use { input ->
                val length = stateFile.length()
                require(length in 1..MAX_STATE_BYTES.toLong()) { "Model history exceeds size bounds" }
                ByteArray(length.toInt()).also {
                    input.readFully(it)
                    require(input.read() == -1) { "Model history grew while reading" }
                }
            }
            val root = ModelCatalogJson.parse(raw, MAX_STATE_BYTES)
            require(integer(root["format"]) == 1L && string(root["repository"]) == expectedRepository) { "Invalid model history namespace" }
            val version = integer(root["catalogVersion"]).toInt()
            val digest = string(root["digest"]).also { require(ModelCatalogParser.SHA256.matches(it)) }
            val generated = string(root["generatedAt"]).also { ModelCatalogParser.timestamp(it) }
            val versions = LinkedHashMap<VersionKey, KnownVersion>()
            for (element in array(root["versions"], maximum = ModelCatalogParser.MAX_HISTORY_ENTRIES)) {
                val item = obj(element)
                val id = string(item["modelId"], 64).also { require(ModelCatalogParser.MODEL_ID.matches(it)) }
                val code = integer(item["versionCode"]).toInt()
                val sha = string(item["sha256"]).also { require(ModelCatalogParser.SHA256.matches(it)) }
                val fingerprint = string(item["fingerprint"]).also { require(ModelCatalogParser.SHA256.matches(it)) }
                val key = VersionKey(id, code)
                require(key !in versions && item.has("revoked") && item.has("deprecated")) { "Invalid model version history" }
                versions[key] = KnownVersion(sha, fingerprint, flag(item["revoked"]), flag(item["deprecated"]))
            }
            StateStatus.Present(AcceptedState(version, digest, generated, versions))
        } catch (error: Exception) {
            rethrowCancellation(error)
            StateStatus.Invalid
        }
    }

    private fun writeState(state: AcceptedState) = store("model catalog trust history") {
        val root = JsonObject().apply {
            addProperty("format", 1)
            addProperty("repository", expectedRepository)
            addProperty("catalogVersion", state.catalogVersion)
            addProperty("digest", state.digest)
            addProperty("generatedAt", state.generatedAt)
            add("versions", com.google.gson.JsonArray().apply {
                for ((key, value) in state.versions.entries.sortedWith(compareBy({ it.key.modelId }, { it.key.versionCode }))) {
                    add(JsonObject().apply {
                        addProperty("modelId", key.modelId)
                        addProperty("versionCode", key.versionCode)
                        addProperty("sha256", value.sha256)
                        addProperty("fingerprint", value.fingerprint)
                        addProperty("revoked", value.revoked)
                        addProperty("deprecated", value.deprecated)
                    })
                }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        require(root.size <= MAX_STATE_BYTES) { "Model version history exceeds its byte limit" }
        FontIo.writeAndReplace(stateFile) { it.write(root) }
    }

    private fun recover(file: File): Boolean {
        FontIo.recoverBackup(file)
        if (!file.exists()) return false
        require(file.isFile) { "Catalog storage path is not a file" }
        return true
    }

    private fun now(): Long = clock().also { require(it >= 0) { "Clock returned a negative epoch timestamp" } }

    private fun <T> locked(action: () -> T): T = try {
        FontIo.withFileLock(lockFile, action)
    } catch (error: ModelCatalogException) {
        throw error
    } catch (error: Exception) {
        rethrowCancellation(error)
        throw ModelCatalogStorageException("Unable to access model catalog storage", error)
    }

    private fun <T> store(name: String, action: () -> T): T = try {
        action()
    } catch (error: Exception) {
        rethrowCancellation(error)
        throw ModelCatalogStorageException("Unable to persist $name", error)
    }

    private fun <T> attempt(action: () -> T): T? = try { action() } catch (error: Exception) {
        rethrowCancellation(error)
        null
    }

    private fun rethrowCancellation(error: Exception) {
        if (error is CancellationException) throw error
        if (error is InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        }
    }

    private data class Snapshot(val catalog: ModelCatalog, val raw: ByteArray, val signature: ByteArray, val digest: String, val fetchedAtMillis: Long?)
    private data class VersionKey(val modelId: String, val versionCode: Int)
    private data class KnownVersion(val sha256: String, val fingerprint: String, val revoked: Boolean, val deprecated: Boolean)
    private data class AcceptedState(val catalogVersion: Int, val digest: String, val generatedAt: String, val versions: Map<VersionKey, KnownVersion>)
    private sealed interface StateStatus {
        data object Absent : StateStatus
        data object Invalid : StateStatus
        data class Present(val state: AcceptedState) : StateStatus
    }

    companion object {
        const val CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000
        const val DEFAULT_CATALOG_URL = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Shared-Assets/main/codegen-models/catalogs/catalog-v1.json"
        const val DEFAULT_SIGNATURE_URL = "https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Shared-Assets/main/codegen-models/catalogs/catalog-v1.sig.json"
        const val CACHE_FILE_NAME = "catalog-cache.bin"
        const val STATE_FILE_NAME = "accepted-state.json"
        private const val CACHE_MAGIC = 0x43474d43
        private const val MAX_SIGNATURE_BYTES = 64 * 1024
        private const val MAX_BOOTSTRAP_BYTES = 12 * 1024 * 1024
        private const val MAX_STATE_BYTES = 32 * 1024 * 1024
    }
}
