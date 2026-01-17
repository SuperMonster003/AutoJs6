package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.min

class PluginIndexRepository {

    companion object {
        private const val TAG = "PluginIndexRepository"

        private const val INDEX_URL = "https://raw.githubusercontent.com/SuperMonster005/autojs6-plugin-index/main/index.json"

        private const val SP = "plugin_center_index"
        private const val KEY_ETAG = "etag"
        private const val KEY_LAST_SUCCESS_TS = "last_success_ts"
        private const val KEY_LAST_FAILURE_TS = "last_failure_ts"
        private const val KEY_RETRY_ATTEMPTS = "retry_attempts"

        private const val CACHE_FILE_NAME = "autojs6_plugin_index.json"

        private const val MIN_RETRY_INTERVAL_MS = 30_000L // 30 sec
        private const val MAX_RETRY_INTERVAL_MS = 10 * 60_000L // 10 min
        private const val MIN_REFRESH_INTERVAL_MS = 1 * 60_000L // 1 min
    }

    @Volatile
    private var memoryCache: List<PluginIndexEntry>? = null

    suspend fun fetchOfficialIndex(context: Context, forceRefresh: Boolean = false): List<PluginIndexEntry> =
        withContext(Dispatchers.IO) {
            memoryCache?.let { return@withContext it }

            val sp = context.getSharedPreferences(SP, Context.MODE_PRIVATE)
            val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)

            val now = System.currentTimeMillis()
            val lastFailureTs = sp.getLong(KEY_LAST_FAILURE_TS, 0L)
            val retryAttempts = sp.getInt(KEY_RETRY_ATTEMPTS, 0)
            val lastSuccessTs = sp.getLong(KEY_LAST_SUCCESS_TS, 0L)

            val cachedEntries: List<PluginIndexEntry>? = readCacheSafely(cacheFile)

            // Throttle for MIN_REFRESH_INTERVAL_MS time.
            // zh-CN: 节流 MIN_REFRESH_INTERVAL_MS 时间.
            if (!forceRefresh && lastSuccessTs > 0 && now - lastSuccessTs < MIN_REFRESH_INTERVAL_MS) {
                cachedEntries?.let {
                    memoryCache = it
                    Log.i(TAG, "Using cached index (within 1 minutes).")
                    return@withContext it
                }
            }

            val withinBackoffWindow = !forceRefresh && isWithinBackoffWindow(now, lastFailureTs, retryAttempts)
            if (withinBackoffWindow) {
                cachedEntries?.let { fromCache ->
                    memoryCache = fromCache
                    Log.i(TAG, "Within backoff window, skip network and use cache.")
                    return@withContext fromCache
                }
            }

            val etag = sp.getString(KEY_ETAG, null)
            val netResult = runCatching { fetchFromNetwork(context, etag) }

            when (val r = netResult.getOrNull()) {
                is NetResult.NotModified -> {
                    resetFailureState(sp, now)
                    cachedEntries?.let { fromCache ->
                        memoryCache = fromCache
                        Log.i(TAG, "Index not modified, use cached.")
                        return@withContext fromCache
                    }

                    // Server returned 304, but no local cache file exists.
                    // In this case, need to re-initiate a complete request without ETag to fetch the latest index data.
                    // zh-CN:
                    // 服务器返回 304, 但本地无缓存文件.
                    // 此时需要重新发起一次无 ETag 的完整请求, 拉取最新索引数据.
                    Log.w(TAG, "Index not modified, but no cache found. Force fetching fresh data without ETag.")
                    val freshResult = runCatching { fetchFromNetwork(context, null) }

                    when (val fr = freshResult.getOrNull()) {
                        is NetResult.Fresh -> {
                            cacheFile.writeText(fr.body)
                            sp.edit {
                                putString(KEY_ETAG, fr.etag)
                                putLong(KEY_LAST_SUCCESS_TS, now)
                            }
                            val parsed = parseIndexJson(fr.body)
                            memoryCache = parsed
                            Log.i(TAG, "Fetched fresh index after missing cache on 304.")
                            return@withContext parsed
                        }
                        is NetResult.NotModified -> {
                            Log.w(TAG, "Got 304 again even without ETag, fall back to empty list.")
                        }
                        null -> {
                            val th = freshResult.exceptionOrNull()
                            Log.w(TAG, "Force fetch after missing cache failed: ${th?.message}")
                        }
                    }
                }
                is NetResult.Fresh -> {
                    resetFailureState(sp, now)
                    cacheFile.writeText(r.body)
                    sp.edit {
                        putString(KEY_ETAG, r.etag)
                        putLong(KEY_LAST_SUCCESS_TS, now)
                    }
                    val parsed = parseIndexJson(r.body)
                    memoryCache = parsed
                    Log.i(TAG, "Fetched fresh index from network.")
                    return@withContext parsed
                }
                null -> {
                    val throwable = netResult.exceptionOrNull()
                    Log.w(TAG, "fetchOfficialIndex network failed: ${throwable?.message}")

                    // Check if it is a "no network connection" type exception,
                    // if so, do not increment the backoff counter.
                    // zh-CN: 判断是否为 "无网络连接" 类型异常, 若是则不累加退避计数.
                    // @formatter:off
                    val isOffline = throwable is UnknownHostException
                                 || throwable is ConnectException
                                 || throwable is NoRouteToHostException
                    // @formatter:on

                    if (!isOffline) {
                        increaseFailureState(sp, now, retryAttempts)
                    } else {
                        Log.i(TAG, "Detected offline state, skip increasing backoff counters.")
                    }

                    cachedEntries?.let { fallback ->
                        memoryCache = fallback
                        return@withContext fallback
                    }
                }
            }
            return@withContext emptyList()
        }

    private sealed interface NetResult {
        data class Fresh(val body: String, val etag: String?) : NetResult
        data object NotModified : NetResult
    }

    private fun fetchFromNetwork(context: Context, etag: String?): NetResult {
        val conn = (URL(INDEX_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            etag?.let { setRequestProperty("If-None-Match", it) }
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} (PluginCenter)")
        }
        return try {
            conn.connect()
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> NetResult.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val newEtag = conn.getHeaderField("ETag")
                    NetResult.Fresh(body, newEtag)
                }
                else -> throw IllegalStateException("HTTP $code: ${conn.responseMessage}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun readCacheSafely(file: File): List<PluginIndexEntry>? =
        runCatching {
            if (!file.exists() || !file.canRead()) return null
            val text = file.readText()
            parseIndexJson(text)
        }.getOrNull()

    private fun isWithinBackoffWindow(now: Long, lastFailureTs: Long, attempts: Int): Boolean {
        if (lastFailureTs <= 0 || attempts <= 0) return false

        // Avoid overflow.
        // zh-CN: 避免溢出.
        val exp = min(attempts - 1, 6)
        val base = MIN_RETRY_INTERVAL_MS

        // Backoff duration = MIN_RETRY_INTERVAL * 2^(attempts-1), capped at MAX_RETRY_INTERVAL.
        // zh-CN: 退避时长 = MIN_RETRY_INTERVAL * 2^(attempts-1), 最大不超过 MAX_RETRY_INTERVAL.
        val delay = min(base shl exp, MAX_RETRY_INTERVAL_MS)

        return now - lastFailureTs < delay
    }

    private fun resetFailureState(sp: android.content.SharedPreferences, now: Long) {
        sp.edit {
            putLong(KEY_LAST_FAILURE_TS, 0L)
            putInt(KEY_RETRY_ATTEMPTS, 0)
            putLong(KEY_LAST_SUCCESS_TS, now)
        }
    }

    private fun increaseFailureState(sp: android.content.SharedPreferences, now: Long, prevAttempts: Int) {
        val attempts = (prevAttempts + 1).coerceAtMost(10)
        sp.edit {
            putLong(KEY_LAST_FAILURE_TS, now)
            putInt(KEY_RETRY_ATTEMPTS, attempts)
        }
    }

    private fun parseIndexJson(json: String): List<PluginIndexEntry> {
        val root = JSONObject(json)
        val list = mutableListOf<PluginIndexEntry>()

        // The index structure is { "plugins/items": PluginIndexEntry[] }.
        // zh-CN: 索引结构为 { "plugins/items": PluginIndexEntry[] }.
        val arr: JSONArray = when {
            root.has("plugins") -> root.getJSONArray("plugins")
            root.has("items") -> root.getJSONArray("items")
            else -> JSONArray().also {
                Log.w(TAG, "Invalid index JSON: missing plugins/items field.")
            }
        }

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val pkg = obj.optString("packageName").takeIf { it.isNotBlank() } ?: continue
            val title = obj.optString("title", pkg)
            val desc = obj.optString("description", "")
            val author = obj.optString("author").takeIf { it.isNotBlank() }
            val collaborators = obj.optJSONArray("collaborators")?.let { ja ->
                List(ja.length()) { idx -> ja.optString(idx) }.filter { it.isNotBlank() }
            } ?: emptyList()
            val engine = obj.optString("engine").takeIf { it.isNotBlank() }
            val variant = obj.optString("variant").takeIf { it.isNotBlank() }
            val engineId = obj.optString("engineId").takeIf { it.isNotBlank() }
            val versionName = obj.optString("versionName", "0.0.0")
            val versionCode = obj.optLong("versionCode", -1L).takeIf { it > 0 }
            val versionDate = obj.optString("versionDate").takeIf { it.isNotBlank() }
            val apkUrl = obj.optString("apkUrl").takeIf { it.isNotBlank() }
            val apkSha256 = obj.optString("apkSha256").takeIf { it.isNotBlank() }
            val apkSize = obj.optLong("apkSizeBytes", -1L).takeIf { it > 0 }

            list += PluginIndexEntry(
                packageName = pkg,
                // TODO M2: 若索引提供 iconUrl 再解析为 Uri.
                iconUrl = null,
                title = title,
                description = desc,
                author = author,
                collaborators = collaborators,
                engine = engine,
                variant = variant,
                engineId = engineId,
                releases = listOf(
                    PluginIndexRelease(
                        versionName = versionName,
                        versionCode = versionCode ?: 0L,
                        versionDate = versionDate,
                        apkUrl = apkUrl,
                        apkSha256 = apkSha256,
                        apkSizeBytes = apkSize,
                    ),
                ),
                tags = emptyList(),
            )
        }

        return list
    }

}
