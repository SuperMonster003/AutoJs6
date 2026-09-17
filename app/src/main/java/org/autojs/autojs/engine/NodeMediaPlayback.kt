package org.autojs.autojs.engine

import android.content.Context
import org.autojs.autojs.core.media.ScriptMusicPlayer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * Node script music sessions over the shared [ScriptMusicPlayer] lease, the same ExoPlayer and
 * foreground media notification Rhino's media.playMusic uses. Player calls run on the playback
 * executor (never on the bridge or main thread); only JSON status snapshots cross the bridge.
 * A newer play() replaces the previous session; controls that name a stale session id are no-ops
 * that report the session as inactive instead of touching the current one.
 */
internal class NodeMediaPlayback(private val context: Context, private val resolveFile: (String) -> File?) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AutoJs6NodeMediaPlayback").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    private var player: ScriptMusicPlayer? = null
    private var session: Session? = null

    private class Session(val id: String, val path: String, val volume: Double, val looping: Boolean)

    fun dispatch(method: String, args: JSONArray, callback: (Result<JSONObject>) -> Unit) {
        if (closed) {
            callback(Result.failure(IllegalStateException("The script has ended.")))
            return
        }
        val action = Runnable {
            callback(
                runCatching {
                    check(!closed) { "The script has ended." }
                    when (method) {
                        "play" -> play(args)
                        "pause" -> control(requestedId(args.opt(0))) { it.pause() }
                        "resume" -> control(requestedId(args.opt(0))) { it.resume(); awaitPlaying(it) }
                        "stop" -> stop(requestedId(args.opt(0)))
                        "seekTo" -> seekTo(args)
                        "getPlaybackStatus" -> status(requestedId(args.opt(0)))
                        else -> throw IllegalArgumentException("Unsupported media playback method: $method")
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

    private fun play(args: JSONArray): JSONObject {
        val rawPath = args.opt(0)
        require(rawPath is String && rawPath.isNotBlank()) { "media.play requires an audio file path." }
        val file = resolveFile(rawPath)
            ?: throw IllegalArgumentException("media.play requires a non-empty file path without NUL bytes.")
        require(file.isFile && file.canRead()) { "Cannot read the requested audio file: ${file.name}" }
        val options = args.optJSONObject(1) ?: JSONObject()
        val volume = options.optDouble("volume", 1.0)
        require(volume.isFinite() && volume in 0.0..1.0) { "media.play volume must be in 0..1." }
        val looping = options.optBoolean("looping", false)
        val player = this.player ?: ScriptMusicPlayer(context).also { this.player = it }
        val next = Session("media-playback-${UUID.randomUUID()}", file.absolutePath, volume, looping)
        session = next
        try {
            player.play(file.absolutePath, volume.toFloat(), looping)
        } catch (error: Exception) {
            if (session === next) session = null
            throw error
        }
        return snapshot(next)
    }

    private fun control(id: String?, action: (ScriptMusicPlayer) -> Unit): JSONObject {
        val current = active(id) ?: return inactive(id)
        action(checkNotNull(player))
        return snapshot(current)
    }

    private fun stop(id: String?): JSONObject {
        val current = active(id) ?: return inactive(id)
        session = null
        player?.stop()
        return inactive(current.id, current)
    }

    private fun seekTo(args: JSONArray): JSONObject {
        val raw = args.opt(0)
        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() >= 0) {
            "media.seekTo requires a non-negative position in milliseconds."
        }
        val id = requestedId(args.opt(1))
        val current = active(id) ?: return inactive(id)
        val player = checkNotNull(player)
        val wasPlaying = player.isPlaying()
        player.seekTo(raw.toDouble().coerceAtMost(Int.MAX_VALUE.toDouble()).toInt())
        if (wasPlaying) awaitPlaying(player)
        return snapshot(current)
    }

    /** ExoPlayer reports isPlaying=false while it re-buffers after resume()/seekTo(); wait briefly so snapshots are settled. */
    private fun awaitPlaying(player: ScriptMusicPlayer) {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (player.isPlaying() || player.hasEnded() || player.position() < 0) return
            Thread.sleep(SETTLE_POLL_MS)
        }
    }

    private fun status(id: String?): JSONObject = active(id)?.let(::snapshot) ?: inactive(id)

    private fun active(id: String?): Session? = session?.takeIf { id == null || id == it.id }

    private fun requestedId(raw: Any?): String? = when (raw) {
        is String -> raw.takeIf { it.isNotBlank() }
        is JSONObject -> raw.optString("id").takeIf { it.isNotBlank() }
        else -> null
    }

    private fun snapshot(current: Session): JSONObject {
        val player = this.player ?: return inactive(current.id, current)
        if (session !== current) return inactive(current.id, current)
        // position() is -1 once the service lease is gone (stopped, superseded, focus loss or error).
        val position = player.position()
        if (position < 0) return inactive(current.id, current)
        return base(current.id, current.path, current.looping, current.volume)
            .put("active", true)
            .put("playing", player.isPlaying())
            .put("ended", player.hasEnded())
            .put("durationMs", player.duration())
            .put("positionMs", position)
    }

    private fun inactive(id: String?, known: Session? = null): JSONObject =
        base(known?.id ?: id.orEmpty(), known?.path, known?.looping ?: false, known?.volume)
            .put("active", false)
            .put("playing", false)
            .put("ended", false)
            .put("durationMs", 0)
            .put("positionMs", -1)

    private fun base(id: String, path: String?, looping: Boolean, volume: Double?): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("id", id)
        .put("path", path ?: JSONObject.NULL)
        .put("looping", looping)
        .put("volume", volume ?: JSONObject.NULL)

    @Synchronized fun close() {
        if (closed) return
        closed = true
        val finished = executor.submit {
            session = null
            player?.close()
            player = null
        }
        executor.shutdown()
        try {
            finished.get(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Exception) {
            // The music service releases on the main looper; cleanup continues without the bridge.
        }
    }

    companion object {
        private const val SETTLE_TIMEOUT_MS = 2_000L
        private const val SETTLE_POLL_MS = 50L
        const val SCHEMA = "autojs6-node-media-playback-v1"
    }
}
