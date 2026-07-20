package org.autojs.autojs.ui.edit.editor.ace.font

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.PublicKey

fun interface FontCatalogFetcher {
    @Throws(IOException::class)
    fun fetch(url: String, maxBytes: Long): ByteArray
}

class OkHttpFontCatalogFetcher(
    private val client: OkHttpClient = OkHttpClient(),
) : FontCatalogFetcher {
    override fun fetch(url: String, maxBytes: Long): ByteArray {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} while retrieving $url")
            }
            val body = response.body ?: throw IOException("Empty response body from $url")
            val declaredLength = body.contentLength()
            if (declaredLength > maxBytes) {
                throw IOException("Response from $url exceeds $maxBytes bytes")
            }
            body.byteStream().use { input ->
                val output = ByteArrayOutputStream(
                    declaredLength.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: DEFAULT_BUFFER_SIZE,
                )
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > maxBytes) throw IOException("Response from $url exceeds $maxBytes bytes")
                    output.write(buffer, 0, read)
                }
                return output.toByteArray()
            }
        }
    }
}

/**
 * Owns the signed remote catalog cache. [bootstrapCatalogBytes] is trusted APK data and is never
 * treated as a substitute for signature verification of network data.
 */
class FontCatalogRepository(
    private val directory: File,
    private val catalogUrl: String,
    private val signatureUrl: String,
    hostVersionCode: Int,
    private val signatureVerifier: FontCatalogSignatureVerifier,
    private val fetcher: FontCatalogFetcher = OkHttpFontCatalogFetcher(),
    bootstrapCatalogBytes: ByteArray? = null,
    private val maxCatalogBytes: Long = MAX_CATALOG_BYTES,
    private val maxSignatureBytes: Long = MAX_SIGNATURE_BYTES,
) {
    private val parser = FontCatalogParser(hostVersionCode)
    private val cacheFile = File(directory, CACHE_FILE_NAME)
    private val acceptedStateFile = File(directory, ACCEPTED_STATE_FILE_NAME)
    private val catalogLockFile = FontIo.catalogLockFile(directory)
    private val bootstrapSnapshot = bootstrapCatalogBytes?.copyOf()?.let { bytes ->
        Snapshot(parser.parse(bytes), bytes, null, sha256(bytes))
    }

    @Volatile
    private var memorySnapshot: Snapshot? = null

    private var observedCacheStamp: CacheStamp? = null

    constructor(
        context: Context,
        catalogUrl: String,
        signatureUrl: String,
        publicKey: PublicKey,
        expectedKeyId: String,
        hostVersionCode: Int,
        bootstrapCatalogBytes: ByteArray? = null,
        client: OkHttpClient = OkHttpClient(),
    ) : this(
        directory = File(context.noBackupFilesDir, FontStore.DIRECTORY_NAME),
        catalogUrl = catalogUrl,
        signatureUrl = signatureUrl,
        hostVersionCode = hostVersionCode,
        signatureVerifier = EcdsaP256CatalogSignatureVerifier(
            publicKey = publicKey,
            signatureEncoding = EcdsaP256CatalogSignatureVerifier.SignatureEncoding.JSON_ENVELOPE,
            expectedKeyId = expectedKeyId,
        ),
        fetcher = OkHttpFontCatalogFetcher(client),
        bootstrapCatalogBytes = bootstrapCatalogBytes,
    )

    /** Parses APK-owned catalog bytes without applying remote signature semantics. */
    @Throws(FontCatalogException::class)
    fun parseTrusted(catalogBytes: ByteArray): FontCatalog = parser.parse(catalogBytes)

    fun bootstrap(): FontCatalog? = bootstrapSnapshot?.catalog

    /** Returns only the previously verified remote cache, or null when it has never been stored. */
    @Throws(FontCatalogException::class)
    fun loadCached(): FontCatalog? = FontIo.withFileLock(catalogLockFile) {
        loadCachedLocked()
    }

    private fun loadCachedLocked(
        acceptedStatus: AcceptedStateStatus = readAcceptedStateStatus(),
    ): FontCatalog? {
        FontIo.recoverBackup(cacheFile)
        if (!cacheFile.isFile) return null
        val snapshot = readCache(cacheFile)
        signatureVerifier.verify(snapshot.rawBytes, snapshot.signatureBytes!!)
        val parsed = parser.parse(snapshot.rawBytes)
        if (parsed.catalogVersion != snapshot.catalog.catalogVersion) {
            throw FontCatalogParseException("Cached catalog version header does not match its payload")
        }
        val parsedSnapshot = snapshot.copy(catalog = parsed)
        enforceBootstrapBaseline(parsedSnapshot)
        val verified = enforceAcceptedState(parsedSnapshot, acceptedStatus)
        memorySnapshot = verified
        observedCacheStamp = cacheStamp()
        return verified.catalog
    }

    /**
     * Verified cache first, then trusted APK metadata. After a different catalog was accepted,
     * bootstrap remains visible but is marked display-only so revocations fail closed.
     */
    fun loadBestAvailable(): FontCatalog? = FontIo.withFileLock(catalogLockFile) {
        val acceptedStatus = readAcceptedStateStatus()
        FontIo.recoverBackup(cacheFile)
        val diskCacheStamp = cacheStamp()
        val memory = memorySnapshot
        if (memory != null && mayAuthorizeArtifacts(memory, acceptedStatus) &&
            (diskCacheStamp == null || diskCacheStamp == observedCacheStamp)
        ) {
            val authorized = memory.copy(catalog = memory.catalog.copy(remoteArtifactsAllowed = true))
            memorySnapshot = authorized
            return@withFileLock authorized.catalog
        }
        if (memory == null && diskCacheStamp == null) {
            bootstrapSnapshot?.takeIf { mayAuthorizeArtifacts(it, acceptedStatus) }?.let { bootstrap ->
                val authorized = bootstrap.copy(catalog = bootstrap.catalog.copy(remoteArtifactsAllowed = true))
                memorySnapshot = authorized
                return@withFileLock authorized.catalog
            }
        }

        // A durable generation change invalidates the memory fast path. Verify and parse the cache
        // once, then subsequent calls compare only the tiny accepted-state marker and file stamp.
        val cached = runCatching { loadCachedLocked(acceptedStatus) }.getOrNull()
        if (cached != null) return@withFileLock cached
        val candidates = listOfNotNull(memorySnapshot, bootstrapSnapshot)
        val selected = candidates
            .map { snapshot ->
                snapshot.copy(
                    catalog = snapshot.catalog.copy(
                        remoteArtifactsAllowed = mayAuthorizeArtifacts(snapshot, acceptedStatus),
                    ),
                )
            }
            .sortedWith(
                compareByDescending<Snapshot> { it.catalog.remoteArtifactsAllowed }
                    .thenByDescending { it.catalog.catalogVersion },
            )
            .firstOrNull()
            ?: return@withFileLock null
        memorySnapshot = selected
        observedCacheStamp = diskCacheStamp
        selected.catalog
    }

    private fun mayAuthorizeArtifacts(
        snapshot: Snapshot,
        acceptedStatus: AcceptedStateStatus,
    ): Boolean = when (acceptedStatus) {
        AcceptedStateStatus.Absent -> true
        AcceptedStateStatus.Invalid -> false
        is AcceptedStateStatus.Present -> when {
            snapshot.catalog.catalogVersion > acceptedStatus.state.catalogVersion -> true
            snapshot.catalog.catalogVersion == acceptedStatus.state.catalogVersion ->
                snapshot.digest == acceptedStatus.state.digest
            else -> false
        }
    }

    fun current(): FontCatalog? = loadBestAvailable()

    /** Downloads, verifies, validates, rollback-checks and then durably caches a remote catalog. */
    @Throws(FontCatalogException::class)
    fun refresh(): FontCatalog {
        val catalogBytes = fetch(catalogUrl, maxCatalogBytes)
        val signatureBytes = fetch(signatureUrl, maxSignatureBytes)
        signatureVerifier.verify(catalogBytes, signatureBytes)
        val catalog = parser.parse(catalogBytes)
        val candidate = Snapshot(catalog, catalogBytes, signatureBytes, sha256(catalogBytes))
        FontIo.withFileLock(catalogLockFile) {
            // Re-read rollback state only after taking the inter-process commit lock.
            enforceNoRollback(candidate)
            writeCache(candidate)
            writeAcceptedState(AcceptedState(catalog.catalogVersion, candidate.digest))
            memorySnapshot = candidate
            observedCacheStamp = cacheStamp()
        }
        return catalog
    }

    private fun fetch(url: String, maxBytes: Long): ByteArray = try {
        fetcher.fetch(url, maxBytes)
    } catch (e: FontCatalogException) {
        throw e
    } catch (e: Exception) {
        throw FontCatalogNetworkException("Unable to retrieve font catalog resource $url", e)
    }

    private fun enforceNoRollback(candidate: Snapshot) {
        val acceptedStatus = readAcceptedStateStatus()
        if (acceptedStatus is AcceptedStateStatus.Invalid) {
            throw FontCatalogRollbackException("Font catalog rollback state is corrupt")
        }
        val baselines = buildList {
            if (acceptedStatus is AcceptedStateStatus.Present) add(acceptedStatus.state)
            bootstrapSnapshot?.let { add(AcceptedState(it.catalog.catalogVersion, it.digest)) }
            memorySnapshot?.let { add(AcceptedState(it.catalog.catalogVersion, it.digest)) }
            if (memorySnapshot == null && cacheFile.isFile) {
                runCatching { readAndVerifyCache() }.getOrNull()?.let {
                    add(AcceptedState(it.catalog.catalogVersion, it.digest))
                }
            }
        }
        val baseline = baselines.maxByOrNull { it.catalogVersion } ?: return
        if (candidate.catalog.catalogVersion < baseline.catalogVersion) {
            throw FontCatalogRollbackException(
                "Catalog rollback rejected: ${candidate.catalog.catalogVersion} < ${baseline.catalogVersion}",
            )
        }
        if (candidate.catalog.catalogVersion == baseline.catalogVersion && candidate.digest != baseline.digest) {
            throw FontCatalogRollbackException(
                "Catalog version ${candidate.catalog.catalogVersion} changed without a version increment",
            )
        }
    }

    private fun enforceAcceptedState(
        snapshot: Snapshot,
        acceptedStatus: AcceptedStateStatus,
    ): Snapshot = when (acceptedStatus) {
        AcceptedStateStatus.Absent -> {
            writeAcceptedState(AcceptedState(snapshot.catalog.catalogVersion, snapshot.digest))
            snapshot
        }
        AcceptedStateStatus.Invalid -> snapshot.copy(
            catalog = snapshot.catalog.copy(remoteArtifactsAllowed = false),
        )
        is AcceptedStateStatus.Present -> {
            val accepted = acceptedStatus.state
            if (snapshot.catalog.catalogVersion < accepted.catalogVersion) {
                throw FontCatalogRollbackException(
                    "Cached catalog rollback rejected: ${snapshot.catalog.catalogVersion} < ${accepted.catalogVersion}",
                )
            }
            if (snapshot.catalog.catalogVersion == accepted.catalogVersion && snapshot.digest != accepted.digest) {
                throw FontCatalogRollbackException("Cached catalog digest conflicts with accepted catalog state")
            }
            if (snapshot.catalog.catalogVersion > accepted.catalogVersion) {
                writeAcceptedState(AcceptedState(snapshot.catalog.catalogVersion, snapshot.digest))
            }
            snapshot
        }
    }

    private fun enforceBootstrapBaseline(snapshot: Snapshot) {
        val bootstrap = bootstrapSnapshot ?: return
        if (snapshot.catalog.catalogVersion < bootstrap.catalog.catalogVersion ||
            snapshot.catalog.catalogVersion == bootstrap.catalog.catalogVersion && snapshot.digest != bootstrap.digest
        ) {
            throw FontCatalogRollbackException("Cached catalog conflicts with the trusted APK catalog baseline")
        }
    }

    private fun readAndVerifyCache(): Snapshot {
        val snapshot = readCache(cacheFile)
        signatureVerifier.verify(snapshot.rawBytes, snapshot.signatureBytes!!)
        return snapshot.copy(catalog = parser.parse(snapshot.rawBytes))
    }

    private fun writeCache(snapshot: Snapshot) {
        ensureDirectory()
        try {
            FontIo.writeAndReplace(cacheFile) { stream ->
                val output = DataOutputStream(stream)
                output.writeInt(CACHE_MAGIC)
                output.writeInt(CACHE_FORMAT_VERSION)
                output.writeLong(snapshot.catalog.catalogVersion)
                output.writeInt(snapshot.rawBytes.size)
                output.writeInt(snapshot.signatureBytes!!.size)
                output.write(snapshot.rawBytes)
                output.write(snapshot.signatureBytes)
                output.flush()
            }
        } catch (e: Exception) {
            throw FontCatalogException("Unable to persist verified font catalog", e)
        }
    }

    private fun readCache(file: File): Snapshot {
        try {
            DataInputStream(FileInputStream(file).buffered()).use { input ->
                if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_FORMAT_VERSION) {
                    throw FontCatalogParseException("Unknown font catalog cache format")
                }
                val catalogVersion = input.readLong()
                val catalogLength = input.readInt()
                val signatureLength = input.readInt()
                if (catalogLength !in 1..maxCatalogBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() ||
                    signatureLength !in 1..maxSignatureBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                ) {
                    throw FontCatalogParseException("Invalid font catalog cache lengths")
                }
                val raw = ByteArray(catalogLength).also(input::readFully)
                val signature = ByteArray(signatureLength).also(input::readFully)
                if (input.read() != -1) throw FontCatalogParseException("Unexpected trailing catalog cache data")
                return Snapshot(
                    catalog = FontCatalog(0, catalogVersion, 0, null, null, emptySet(), emptyList()),
                    rawBytes = raw,
                    signatureBytes = signature,
                    digest = sha256(raw),
                )
            }
        } catch (e: FontCatalogException) {
            throw e
        } catch (e: EOFException) {
            throw FontCatalogParseException("Truncated font catalog cache", e)
        } catch (e: Exception) {
            throw FontCatalogParseException("Unable to read font catalog cache", e)
        }
    }

    private fun readAcceptedStateStatus(): AcceptedStateStatus {
        FontIo.recoverBackup(acceptedStateFile)
        val backup = File(acceptedStateFile.parentFile, "${acceptedStateFile.name}.bak")
        if (!acceptedStateFile.isFile) {
            return if (acceptedStateFile.exists() || backup.exists()) {
                AcceptedStateStatus.Invalid
            } else {
                AcceptedStateStatus.Absent
            }
        }
        if (acceptedStateFile.length() !in 1..MAX_ACCEPTED_STATE_BYTES) {
            return AcceptedStateStatus.Invalid
        }
        val accepted = runCatching {
            val lines = acceptedStateFile.readLines(Charsets.US_ASCII)
            val version = lines.getOrNull(0)?.toLongOrNull() ?: return@runCatching null
            val digest = lines.getOrNull(1)?.trim()?.lowercase() ?: return@runCatching null
            if (version < 1 || !AceFontIds.validSha256.matches(digest)) null else AcceptedState(version, digest)
        }.getOrNull()
        return accepted?.let(AcceptedStateStatus::Present) ?: AcceptedStateStatus.Invalid
    }

    private fun cacheStamp(): CacheStamp? = cacheFile.takeIf(File::isFile)?.let {
        CacheStamp(it.length(), it.lastModified())
    }

    private fun writeAcceptedState(state: AcceptedState) {
        ensureDirectory()
        try {
            FontIo.writeAndReplace(acceptedStateFile) { output ->
                output.write("${state.catalogVersion}\n${state.digest}\n".toByteArray(Charsets.US_ASCII))
            }
        } catch (e: Exception) {
            throw FontCatalogException("Unable to persist font catalog rollback state", e)
        }
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw FontCatalogException("Unable to create font directory ${directory.absolutePath}")
        }
    }

    private data class Snapshot(
        val catalog: FontCatalog,
        val rawBytes: ByteArray,
        val signatureBytes: ByteArray?,
        val digest: String,
    )

    private data class AcceptedState(val catalogVersion: Long, val digest: String)

    private sealed interface AcceptedStateStatus {
        data object Absent : AcceptedStateStatus
        data object Invalid : AcceptedStateStatus
        data class Present(val state: AcceptedState) : AcceptedStateStatus
    }

    private data class CacheStamp(val length: Long, val lastModified: Long)

    companion object {
        const val DEFAULT_CATALOG_FILE_NAME = "catalog-v1.json"
        const val DEFAULT_SIGNATURE_FILE_NAME = "catalog-v1.sig.json"
        const val MAX_CATALOG_BYTES = 2L * 1024L * 1024L
        const val MAX_SIGNATURE_BYTES = 16L * 1024L
        private const val MAX_ACCEPTED_STATE_BYTES = 512L

        private const val CACHE_FILE_NAME = "catalog.cache"
        private const val ACCEPTED_STATE_FILE_NAME = "catalog.accepted"
        private const val CACHE_MAGIC = 0x41464331
        private const val CACHE_FORMAT_VERSION = 1

        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
