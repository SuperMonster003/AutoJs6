package org.autojs.autojs.engine

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Scoped MediaStore access for Node scripts. Cursors, Uri objects and descriptors stay on the media
 * store executor; only JSON rows, URI strings and workspace file paths cross the bridge. Filters are
 * structured JSON compiled into parameterised selections, so scripts never submit raw SQL. Android's
 * own rules apply: on Android 10+ scripts read what the granted media permissions allow and may only
 * modify items AutoJs6 created; below Android 10 the storage permissions gate reads and writes.
 */
internal class NodeMediaStore(private val context: Context, private val resolveFile: (String) -> File?) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AutoJs6NodeMediaStore").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    private val resolver: ContentResolver get() = context.contentResolver

    private enum class Collection(val id: String, val minSdk: Int, val defaultRelativePath: String) {
        AUDIO("audio", 1, "Music/"),
        IMAGES("images", 1, "Pictures/"),
        VIDEO("video", 1, "Movies/"),
        DOWNLOADS("downloads", 29, "Download/");

        val uri: Uri
            get() = when (this) {
                AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                DOWNLOADS -> downloadsUri()
            }

        val insertUri: Uri
            get() = if (Build.VERSION.SDK_INT >= 29) primaryVolumeUri() else uri

        @RequiresApi(29)
        private fun primaryVolumeUri(): Uri = when (this) {
            AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            IMAGES -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        private fun downloadsUri(): Uri {
            check(Build.VERSION.SDK_INT >= 29) { "The downloads collection requires Android 10 or newer." }
            return MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
    }

    private enum class Kind { LONG, STRING, BOOLEAN }

    private class Column(
        val key: String,
        val name: String,
        val kind: Kind,
        val minSdk: Int = 1,
        val collections: Set<Collection> = Collection.values().toSet(),
    ) {
        fun availableFor(collection: Collection): Boolean = Build.VERSION.SDK_INT >= minSdk && collection in collections
    }

    private class Selection(val clause: String?, val args: List<String>)

    fun dispatch(method: String, args: JSONArray, callback: (Result<Any?>) -> Unit) {
        if (closed) {
            callback(Result.failure(IllegalStateException("The script has ended.")))
            return
        }
        val action = Runnable {
            callback(
                runCatching {
                    check(!closed) { "The script has ended." }
                    when (method) {
                        "capabilities" -> capabilities()
                        "query" -> query(args)
                        "get" -> get(args) ?: JSONObject.NULL
                        "insert" -> insert(args)
                        "update" -> update(args)
                        "delete" -> delete(args)
                        "scanFile" -> scanFile(args)
                        "exportFile" -> exportFile(args)
                        else -> throw IllegalArgumentException("Unsupported media_store method: $method")
                    }
                },
            )
        }
        try {
            executor.execute(action)
        } catch (_: RejectedExecutionException) {
            callback(Result.failure(IllegalStateException("The script has ended.")))
        }
    }

    fun close() {
        if (closed) return
        closed = true
        executor.shutdownNow()
    }

    // ------------------------------------------------------------------ methods

    private fun capabilities(): JSONObject {
        val sdk = Build.VERSION.SDK_INT
        val collections = Collection.values().filter { sdk >= it.minSdk }
        val readPermissions = JSONObject()
        val columns = JSONObject()
        collections.forEach { collection ->
            val permission = readPermissionFor(collection)
            readPermissions.put(collection.id, permission?.let(::granted) ?: false)
            columns.put(collection.id, JSONArray(columnsFor(collection).map { it.key }))
        }
        return JSONObject()
            .put("schema", CAPABILITIES_SCHEMA)
            .put("sdkInt", sdk)
            .put("scopedStorage", sdk >= 29)
            .put("collections", JSONArray(collections.map { it.id }))
            .put("columns", columns)
            .put("readPermissions", readPermissions)
            .put("legacyWritePermissionGranted", sdk < 29 && granted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            .put("allFilesAccess", sdk >= 30 && Environment.isExternalStorageManager())
            .put("queryPolicy", if (sdk >= 29) "granted_media_permissions_or_app_owned_items" else "read_external_storage_permission")
            .put("mutatePolicy", if (sdk >= 29) "app_owned_items_only" else "write_external_storage_permission")
            .put("filterPolicy", "structured_json_filters_only")
            .put("itemSchema", ITEM_SCHEMA)
            .put("defaultLimit", DEFAULT_LIMIT)
            .put("maxLimit", MAX_LIMIT)
    }

    private fun query(args: JSONArray): JSONObject {
        val collection = collectionOf(args.opt(0))
        val options = args.optJSONObject(1) ?: JSONObject()
        val available = columnsFor(collection)
        val projection = selectedColumns(options.opt("columns"), available)
        val selection = compileFilter(options.optJSONObject("filter"), available, collection)
        val limit = options.optInt("limit", DEFAULT_LIMIT)
        require(limit in 1..MAX_LIMIT) { "media_store.query limit must be between 1 and $MAX_LIMIT." }
        val offset = options.optInt("offset", 0)
        require(offset >= 0) { "media_store.query offset must not be negative." }
        val sortOrder = compileSort(options.opt("sort"), available)
        val items = JSONArray()
        openCursor(collection.uri, projection, selection, sortOrder, limit, offset).use { cursor ->
            while (cursor.moveToNext()) {
                items.put(row(collection, cursor, projection))
            }
        }
        return JSONObject()
            .put("schema", QUERY_SCHEMA)
            .put("collection", collection.id)
            .put("count", items.length())
            .put("limit", limit)
            .put("offset", offset)
            .put("items", items)
    }

    private fun get(args: JSONArray): JSONObject? {
        val collection = collectionOf(args.opt(0))
        return queryById(collection, idOf(args.opt(1)))
    }

    private fun insert(args: JSONArray): JSONObject {
        val collection = collectionOf(args.opt(0))
        val options = args.optJSONObject(1) ?: throw IllegalArgumentException("media_store.insert requires an options object.")
        val displayName = validDisplayName(options.optString("displayName"))
        val mimeType = options.optString("mimeType")
        require(mimeType.isNotBlank() && mimeType.contains('/')) { "media_store.insert requires a mimeType such as audio/wav." }
        val rawSource = options.optString("source")
        require(rawSource.isNotBlank()) { "media_store.insert requires a source file path." }
        val source = resolveFile(rawSource)
            ?: throw IllegalArgumentException("media_store.insert requires a non-empty source path without NUL bytes.")
        require(source.isFile && source.canRead()) { "Cannot read the source file: ${source.name}" }
        val relativePath = options.optString("relativePath").takeIf { it.isNotBlank() }
            ?.let(::normalizeRelativePath) ?: collection.defaultRelativePath
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            val uri = resolver.insert(collection.insertUri, values)
                ?: throw IllegalStateException("MediaStore did not create the ${collection.id} item.")
            try {
                val output = resolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("MediaStore did not open the new ${collection.id} item for writing.")
                output.use { target -> source.inputStream().use { it.copyTo(target) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
            return checkNotNull(queryById(collection, ContentUris.parseId(uri))) { "MediaStore lost the new ${collection.id} item." }
        }
        requireLegacyWrite()
        @Suppress("DEPRECATION")
        val target = File(File(Environment.getExternalStorageDirectory(), relativePath), displayName)
        require(!target.exists()) { "The target file already exists: ${target.absolutePath}" }
        target.parentFile?.let { if (!it.isDirectory) require(it.mkdirs()) { "Cannot create ${it.absolutePath}" } }
        source.copyTo(target)
        try {
            @Suppress("DEPRECATION")
            values.put(MediaStore.MediaColumns.DATA, target.absolutePath)
            values.put(MediaStore.MediaColumns.SIZE, target.length())
            val uri = resolver.insert(collection.uri, values)
                ?: throw IllegalStateException("MediaStore did not create the ${collection.id} item.")
            return checkNotNull(queryById(collection, ContentUris.parseId(uri))) { "MediaStore lost the new ${collection.id} item." }
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun update(args: JSONArray): JSONObject {
        val collection = collectionOf(args.opt(0))
        val id = idOf(args.opt(1))
        val fields = args.optJSONObject(2) ?: throw IllegalArgumentException("media_store.update requires a values object.")
        val values = ContentValues()
        fields.keys().forEach { key ->
            when (key) {
                // TITLE and the other metadata columns are derived by MediaProvider and silently dropped on Android 10+,
                // so only the file name and its relative directory are writable through the bridge.
                "displayName" -> values.put(MediaStore.MediaColumns.DISPLAY_NAME, validDisplayName(fields.optString(key)))
                "relativePath" -> {
                    require(Build.VERSION.SDK_INT >= 29) { "relativePath requires Android 10 or newer." }
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, normalizeRelativePath(fields.optString(key)))
                }
                else -> throw IllegalArgumentException("media_store.update does not support the $key column.")
            }
        }
        require(values.size() > 0) { "media_store.update requires displayName or relativePath." }
        requireLegacyWrite()
        val updated = resolver.update(ContentUris.withAppendedId(collection.uri, id), values, null, null)
        require(updated > 0) { "No ${collection.id} item with id $id." }
        return checkNotNull(queryById(collection, id)) { "MediaStore lost ${collection.id} item $id after the update." }
    }

    private fun delete(args: JSONArray): JSONObject {
        val collection = collectionOf(args.opt(0))
        val id = idOf(args.opt(1))
        requireLegacyWrite()
        val deleted = resolver.delete(ContentUris.withAppendedId(collection.uri, id), null, null)
        return JSONObject()
            .put("schema", DELETE_SCHEMA)
            .put("collection", collection.id)
            .put("id", id)
            .put("deleted", deleted > 0)
    }

    private fun scanFile(args: JSONArray): JSONObject {
        val rawPath = args.opt(0)
        require(rawPath is String && rawPath.isNotBlank()) { "media_store.scanFile requires a file path." }
        val file = resolveFile(rawPath)
            ?: throw IllegalArgumentException("media_store.scanFile requires a non-empty file path without NUL bytes.")
        require(file.exists()) { "The file to scan does not exist: ${file.absolutePath}" }
        val mimeType = args.optJSONObject(1)?.optString("mimeType")?.takeIf { it.isNotBlank() }
        val future = CompletableFuture<Uri?>()
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), mimeType?.let { arrayOf(it) }) { _, uri ->
            future.complete(uri)
        }
        val uri = try {
            future.get(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: TimeoutException) {
            throw IllegalStateException("The media scanner did not answer within $SCAN_TIMEOUT_SECONDS seconds for ${file.name}.", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("The media scan was interrupted.", error)
        } catch (error: ExecutionException) {
            throw IllegalStateException("The media scanner failed for ${file.name}.", error.cause ?: error)
        }
        return JSONObject()
            .put("schema", SCAN_SCHEMA)
            .put("path", file.absolutePath)
            .put("uri", uri?.toString() ?: JSONObject.NULL)
            .put("scanned", uri != null)
    }

    private fun exportFile(args: JSONArray): JSONObject {
        val collection = collectionOf(args.opt(0))
        val id = idOf(args.opt(1))
        val rawDestination = args.opt(2)
        require(rawDestination is String && rawDestination.isNotBlank()) { "media_store.exportFile requires a destination file path." }
        val destination = resolveFile(rawDestination)
            ?: throw IllegalArgumentException("media_store.exportFile requires a non-empty destination path without NUL bytes.")
        require(!destination.isDirectory) { "The destination is a directory: ${destination.absolutePath}" }
        destination.parentFile?.let { if (!it.isDirectory) require(it.mkdirs()) { "Cannot create ${it.absolutePath}" } }
        val itemUri = ContentUris.withAppendedId(collection.uri, id)
        val input = try {
            resolver.openInputStream(itemUri)
        } catch (error: FileNotFoundException) {
            throw IllegalArgumentException("No readable ${collection.id} item with id $id.", error)
        } ?: throw IllegalStateException("MediaStore did not open ${collection.id} item $id.")
        val bytes = input.use { source -> destination.outputStream().use { source.copyTo(it) } }
        return JSONObject()
            .put("schema", EXPORT_SCHEMA)
            .put("collection", collection.id)
            .put("id", id)
            .put("path", destination.absolutePath)
            .put("sizeBytes", bytes)
    }

    // ------------------------------------------------------------------ helpers

    private fun queryById(collection: Collection, id: Long): JSONObject? {
        val projection = columnsFor(collection)
        val selection = Selection("${MediaStore.MediaColumns._ID} = ?", listOf(id.toString()))
        openCursor(collection.uri, projection, selection, "${MediaStore.MediaColumns._ID} ASC", 1, 0).use { cursor ->
            return if (cursor.moveToFirst()) row(collection, cursor, projection) else null
        }
    }

    private fun openCursor(uri: Uri, projection: List<Column>, selection: Selection, sortOrder: String, limit: Int, offset: Int): Cursor {
        val names = projection.map { it.name }.toTypedArray()
        val cursor = if (Build.VERSION.SDK_INT >= 30) {
            val queryArgs = Bundle().apply {
                selection.clause?.let {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selection.args.toTypedArray())
                }
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(uri, names, queryArgs, null)
        } else {
            resolver.query(
                uri,
                names,
                selection.clause,
                selection.args.takeIf { it.isNotEmpty() }?.toTypedArray(),
                "$sortOrder LIMIT $limit OFFSET $offset",
            )
        }
        return cursor ?: throw IllegalStateException("MediaStore returned no cursor for $uri.")
    }

    private fun row(collection: Collection, cursor: Cursor, projection: List<Column>): JSONObject {
        val item = JSONObject().put("schema", ITEM_SCHEMA).put("collection", collection.id)
        var id: Long? = null
        projection.forEach { column ->
            val index = cursor.getColumnIndex(column.name)
            if (index < 0) return@forEach
            val value: Any = when {
                cursor.isNull(index) -> JSONObject.NULL
                column.kind == Kind.LONG -> cursor.getLong(index)
                column.kind == Kind.BOOLEAN -> cursor.getInt(index) != 0
                else -> cursor.getString(index)
            }
            if (column.key == "id" && value is Long) id = value
            item.put(column.key, value)
        }
        id?.let { item.put("uri", ContentUris.withAppendedId(collection.uri, it).toString()) }
        return item
    }

    private fun columnsFor(collection: Collection): List<Column> = COLUMNS.filter { it.availableFor(collection) }

    private fun columnNamed(key: String, available: List<Column>): Column =
        available.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown media_store column: $key.")

    private fun selectedColumns(raw: Any?, available: List<Column>): List<Column> {
        if (raw == null || raw == JSONObject.NULL) return available
        require(raw is JSONArray) { "media_store.query columns must be an array of column names." }
        val selected = (0 until raw.length()).map { index ->
            val key = raw.opt(index) as? String ?: throw IllegalArgumentException("media_store.query columns must be strings.")
            columnNamed(key, available)
        }.distinct()
        return if (selected.any { it.key == "id" }) selected else listOf(columnNamed("id", available)) + selected
    }

    private fun compileFilter(filter: JSONObject?, available: List<Column>, collection: Collection): Selection {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        filter?.keys()?.forEach { key ->
            // Declared as Any? on purpose: with the platform type of JSONObject.opt, Kotlin 2.4 reports the when below
            // as non-exhaustive (UNEXHAUSTIVE_WHEN_BASED_ON_JAVA_ANNOTATIONS) even though it has an else branch.
            // zh-CN: 有意声明为 Any?: 若保留 JSONObject.opt 的平台类型, Kotlin 2.4 会对下方的 when 误报非穷尽 (尽管它有 else 分支).
            val value: Any? = filter.opt(key)
            if (key == "ownedOnly") {
                require(value is Boolean) { "The ownedOnly filter must be a boolean." }
                if (value && Build.VERSION.SDK_INT >= 29) {
                    clauses += "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?"
                    args += context.packageName
                }
                return@forEach
            }
            val column = available.firstOrNull { it.key == key }
                ?: throw IllegalArgumentException("Unknown media_store filter column for ${collection.id}: $key.")
            when (value) {
                null, JSONObject.NULL -> clauses += "${column.name} IS NULL"
                is JSONArray -> {
                    clauses += inClause(column, value)
                    args += (0 until value.length()).map { literal(column, value.opt(it)) }
                }
                is JSONObject -> value.keys().forEach { operator ->
                    val operand = value.opt(operator)
                    when (operator) {
                        "eq" -> { clauses += "${column.name} = ?"; args += literal(column, operand) }
                        "ne" -> { clauses += "${column.name} != ?"; args += literal(column, operand) }
                        "gt" -> { clauses += "${column.name} > ?"; args += literal(column, operand) }
                        "gte" -> { clauses += "${column.name} >= ?"; args += literal(column, operand) }
                        "lt" -> { clauses += "${column.name} < ?"; args += literal(column, operand) }
                        "lte" -> { clauses += "${column.name} <= ?"; args += literal(column, operand) }
                        "like" -> { clauses += "${column.name} LIKE ?"; args += text(column, operand) }
                        "contains" -> { clauses += "${column.name} LIKE ? ESCAPE '\\'"; args += "%" + escapeLike(text(column, operand)) + "%" }
                        "startsWith" -> { clauses += "${column.name} LIKE ? ESCAPE '\\'"; args += escapeLike(text(column, operand)) + "%" }
                        "endsWith" -> { clauses += "${column.name} LIKE ? ESCAPE '\\'"; args += "%" + escapeLike(text(column, operand)) }
                        "in" -> {
                            require(operand is JSONArray) { "The in operator for ${column.key} requires an array." }
                            clauses += inClause(column, operand)
                            args += (0 until operand.length()).map { literal(column, operand.opt(it)) }
                        }
                        "isNull" -> {
                            require(operand is Boolean) { "The isNull operator for ${column.key} requires a boolean." }
                            clauses += if (operand) "${column.name} IS NULL" else "${column.name} IS NOT NULL"
                        }
                        else -> throw IllegalArgumentException("Unsupported media_store filter operator for ${column.key}: $operator.")
                    }
                }
                else -> { clauses += "${column.name} = ?"; args += literal(column, value) }
            }
        }
        return Selection(clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ") { "($it)" }, args)
    }

    private fun inClause(column: Column, values: JSONArray): String {
        require(values.length() in 1..MAX_IN_VALUES) { "The in operator for ${column.key} requires 1..$MAX_IN_VALUES values." }
        return "${column.name} IN (" + (0 until values.length()).joinToString(",") { "?" } + ")"
    }

    private fun compileSort(raw: Any?, available: List<Column>): String {
        val entries = when (raw) {
            null, JSONObject.NULL -> listOf("id")
            is String -> listOf(raw)
            is JSONArray -> (0 until raw.length()).map {
                raw.opt(it) as? String ?: throw IllegalArgumentException("media_store.query sort entries must be strings.")
            }
            else -> throw IllegalArgumentException("media_store.query sort must be a string or an array of strings.")
        }
        require(entries.isNotEmpty()) { "media_store.query sort must not be empty." }
        return entries.joinToString(", ") { entry ->
            var text = entry.trim()
            var descending = false
            if (text.startsWith("-")) {
                descending = true
                text = text.substring(1).trim()
            }
            val parts = text.split(Regex("\\s+"))
            require(parts.size in 1..2 && parts[0].isNotBlank()) { "Invalid media_store sort entry: $entry" }
            if (parts.size == 2) {
                when (parts[1].lowercase(Locale.US)) {
                    "asc" -> Unit
                    "desc" -> descending = true
                    else -> throw IllegalArgumentException("Invalid media_store sort direction: $entry")
                }
            }
            columnNamed(parts[0], available).name + if (descending) " DESC" else " ASC"
        }
    }

    private fun literal(column: Column, value: Any?): String = when (column.kind) {
        Kind.LONG -> when (value) {
            is Number -> value.toDouble().let { number ->
                require(number.isFinite()) { "Filter value for ${column.key} must be a finite number." }
                if (number == Math.floor(number)) value.toLong().toString() else number.toString()
            }
            is String -> value.toLongOrNull()?.toString() ?: value.toDoubleOrNull()?.toString()
                ?: throw IllegalArgumentException("Filter value for ${column.key} must be a number.")
            else -> throw IllegalArgumentException("Filter value for ${column.key} must be a number.")
        }
        Kind.BOOLEAN -> when (value) {
            is Boolean -> if (value) "1" else "0"
            is Number -> if (value.toInt() != 0) "1" else "0"
            else -> throw IllegalArgumentException("Filter value for ${column.key} must be a boolean.")
        }
        Kind.STRING -> text(column, value)
    }

    private fun text(column: Column, value: Any?): String = when (value) {
        is String -> value
        is Number, is Boolean -> value.toString()
        else -> throw IllegalArgumentException("Filter value for ${column.key} must be a string.")
    }

    private fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun collectionOf(raw: Any?): Collection {
        require(raw is String && raw.isNotBlank()) { "media_store requires a collection name (audio, images, video or downloads)." }
        val collection = Collection.values().firstOrNull { it.id == raw.trim().lowercase(Locale.US) }
            ?: throw IllegalArgumentException("Unknown media_store collection: $raw.")
        require(Build.VERSION.SDK_INT >= collection.minSdk) { "The ${collection.id} collection requires Android API ${collection.minSdk} or newer." }
        return collection
    }

    private fun idOf(raw: Any?): Long = when (raw) {
        is Number -> raw.toDouble().let { number ->
            require(number.isFinite() && number >= 0 && number == Math.floor(number)) { "media_store item ids must be non-negative integers." }
            raw.toLong()
        }
        is String -> raw.trim().toLongOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("media_store item ids must be non-negative integers.")
        else -> throw IllegalArgumentException("media_store item ids must be non-negative integers.")
    }

    private fun validDisplayName(value: String): String {
        require(value.isNotBlank() && value.indexOf('/') < 0 && value.indexOf('\\') < 0 && value.indexOf('\u0000') < 0 && value != "." && value != "..") {
            "displayName must be a plain file name."
        }
        return value
    }

    private fun normalizeRelativePath(raw: String): String {
        val normalized = raw.trim().replace('\\', '/').trimStart('/')
        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.isNotEmpty() && segments.none { it == "." || it == ".." || it.indexOf('\u0000') >= 0 }) {
            "relativePath must be a relative directory such as Music/AutoJs6/."
        }
        return segments.joinToString("/") + "/"
    }

    private fun requireLegacyWrite() {
        if (Build.VERSION.SDK_INT < 29 && !granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            throw SecurityException("Grant AutoJs6 storage permission in Android app settings before modifying the media library on Android 9 and below.")
        }
    }

    private fun readPermissionFor(collection: Collection): String? = if (Build.VERSION.SDK_INT >= 33) {
        when (collection) {
            Collection.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
            Collection.IMAGES -> Manifest.permission.READ_MEDIA_IMAGES
            Collection.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            Collection.DOWNLOADS -> null
        }
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CAPABILITIES_SCHEMA = "autojs6-node-media-store-capabilities-v1"
        const val QUERY_SCHEMA = "autojs6-node-media-store-query-v1"
        const val ITEM_SCHEMA = "autojs6-node-media-store-item-v1"
        const val SCAN_SCHEMA = "autojs6-node-media-store-scan-v1"
        const val EXPORT_SCHEMA = "autojs6-node-media-store-export-v1"
        const val DELETE_SCHEMA = "autojs6-node-media-store-delete-v1"
        private const val DEFAULT_LIMIT = 100
        private const val MAX_LIMIT = 1000
        private const val MAX_IN_VALUES = 100
        private const val SCAN_TIMEOUT_SECONDS = 30L

        private val COLUMNS: List<Column> = listOf(
            Column("id", MediaStore.MediaColumns._ID, Kind.LONG),
            Column("displayName", MediaStore.MediaColumns.DISPLAY_NAME, Kind.STRING),
            Column("title", MediaStore.MediaColumns.TITLE, Kind.STRING),
            Column("mimeType", MediaStore.MediaColumns.MIME_TYPE, Kind.STRING),
            Column("size", MediaStore.MediaColumns.SIZE, Kind.LONG),
            Column("dateAdded", MediaStore.MediaColumns.DATE_ADDED, Kind.LONG),
            Column("dateModified", MediaStore.MediaColumns.DATE_MODIFIED, Kind.LONG),
            Column("durationMs", "duration", Kind.LONG, collections = setOf(Collection.AUDIO, Collection.VIDEO)),
            Column("album", "album", Kind.STRING, collections = setOf(Collection.AUDIO)),
            Column("artist", "artist", Kind.STRING, collections = setOf(Collection.AUDIO)),
            Column("width", "width", Kind.LONG, collections = setOf(Collection.IMAGES, Collection.VIDEO)),
            Column("height", "height", Kind.LONG, collections = setOf(Collection.IMAGES, Collection.VIDEO)),
            Column("bucketDisplayName", "bucket_display_name", Kind.STRING, collections = setOf(Collection.IMAGES, Collection.VIDEO)),
            Column("relativePath", "relative_path", Kind.STRING, minSdk = 29),
            Column("ownerPackageName", "owner_package_name", Kind.STRING, minSdk = 29),
            Column("isPending", "is_pending", Kind.BOOLEAN, minSdk = 29),
            Column("path", "_data", Kind.STRING),
        )
    }
}
