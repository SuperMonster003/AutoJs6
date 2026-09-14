package org.autojs.autojs.core.media

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs.ui.log.LogActivity
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Silent local PCM exercises real focus, service, session, and runtime cleanup without changing volume. */
class ScriptMusicDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val players = mutableListOf<ScriptMusicPlayer>()
    private lateinit var activity: Activity
    private lateinit var file: File

    @Before fun setUp() {
        activity = instrumentation.startActivitySync(Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        file = File.createTempFile("script-music-", ".wav", context.cacheDir)
        val dataSize = 8000 * 2 * 5
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize)
        file.writeBytes(buffer.array())
    }

    @After fun tearDown() {
        players.forEach { it.close() }
        instrumentation.waitForIdleSync()
        try { eventually { musicNotification() == null } } finally {
            if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
            if (::file.isInitialized) file.delete()
        }
    }

    @Test fun controlsKeepSynchronousDurationAndReleaseNotification() {
        val player = player()
        player.play(file.path, 0f, true)
        assertEquals(5000, player.duration())
        assertTrue(player.isPlaying())
        player.pause()
        assertFalse(player.isPlaying())
        player.seekTo(2000)
        eventually { player.position() in 1950..2050 }
        player.resume()
        eventually { player.isPlaying() && player.position() > 2050 }
        eventually { musicNotification()?.actions?.isNotEmpty() == true }
        player.stop()
        assertFalse(player.isPlaying())
        assertEquals(0, player.duration())
        assertEquals(-1, player.position())
        eventually { musicNotification() == null }
    }

    @Test fun supersededRuntimeCannotControlOrReleaseTheNewOwner() {
        val first = player()
        val second = player()
        first.play(file.path, 0f, true)
        second.play(file.path, 0f, true)
        assertFalse(first.isPlaying())
        assertEquals(-1, first.position())
        first.pause()
        first.resume()
        first.seekTo(4000)
        first.stop()
        first.close()
        assertTrue(second.isPlaying())
        assertTrue(second.position() < 4000)
        assertEquals(5000, second.duration())
        eventually { musicNotification() != null }
    }

    @Test fun repeatedPlayReplacesOnlyThePreviousRequest() {
        val player = player()
        repeat(4) {
            player.play(file.path, 0f, true)
            assertTrue(player.isPlaying())
            assertEquals(5000, player.duration())
        }
    }

    @Test fun invalidRequestDoesNotTakeAnotherScriptsPlayback() {
        val first = player()
        val second = player()
        first.play(file.path, 0f, true)
        assertNotNull(runCatching { second.play(file.path + ".missing", 0f, false) }.exceptionOrNull())
        assertTrue(first.isPlaying())
        assertNotNull(runCatching { second.play(file.path, Float.NaN, false) }.exceptionOrNull())
        assertTrue(first.isPlaying())
    }

    @Test fun unreadableAndInvalidAudioCleanUpAndAllowRetry() {
        val player = player()
        assertNotNull(runCatching { player.play(file.path + ".missing", 0f, false) }.exceptionOrNull())
        eventually { musicNotification() == null }
        val invalid = File.createTempFile("script-music-invalid-", ".wav", context.cacheDir)
        try {
            invalid.writeText("not an audio file")
            assertNotNull(runCatching { player.play(invalid.path, 0f, false) }.exceptionOrNull())
            eventually { musicNotification() == null }
            player.play(file.path, 0f, true)
            assertTrue(player.isPlaying())
        } finally { invalid.delete() }
    }

    @Test fun uiThreadAndClosedRuntimeRejectWithoutStartingMusic() {
        val player = player()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync { error.set(runCatching { player.play(file.path, 0f, false) }.exceptionOrNull()) }
        assertTrue(error.get().toString(), error.get() is IllegalArgumentException)
        player.close()
        assertNotNull(runCatching { player.play(file.path, 0f, false) }.exceptionOrNull())
        assertFalse(player.isPlaying())
    }

    @Test fun interruptedQueuedRequestCannotStartAfterCallerHasExited() {
        val player = player()
        val error = AtomicReference<Throwable>()
        val started = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val worker = thread {
                started.countDown()
                error.set(runCatching { player.play(file.path, 0f, true) }.exceptionOrNull())
            }
            assertTrue(started.await(1, TimeUnit.SECONDS))
            worker.interrupt()
            worker.join(2000)
            assertFalse("Interrupted music caller is still blocked", worker.isAlive)
        }
        assertTrue(error.get().toString(), error.get() is ScriptInterruptedException)
        instrumentation.waitForIdleSync()
        assertFalse(player.isPlaying())
        assertNull(musicNotification())
    }

    @Test fun systemSessionControlsUseTheOwnedPlayer() {
        val player = player()
        player.play(file.path, 0f, true)
        eventually { musicNotification()?.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true }
        @Suppress("DEPRECATION")
        val token = musicNotification()!!.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)!!
        val controller = MediaController(context, token)
        controller.transportControls.pause()
        eventually { !player.isPlaying() }
        controller.transportControls.seekTo(3000)
        eventually { player.position() in 2950..3050 }
        controller.transportControls.play()
        eventually { player.isPlaying() }
        controller.transportControls.stop()
        eventually { !player.isPlaying() && musicNotification() == null }
    }

    @Test fun transientAudioFocusLossPausesAndFocusGainResumes() {
        val player = player()
        player.play(file.path, 0f, true)
        val manager = context.getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnAudioFocusChangeListener { }
        try {
            @Suppress("DEPRECATION")
            val focus = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, focus)
            eventually { !player.isPlaying() }
        } finally {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(listener)
        }
        eventually { player.isPlaying() }
    }

    @Test fun naturalCompletionRetainsDurationAndCanRestart() {
        val player = player()
        player.play(file.path, 0f, false)
        player.seekTo(4700)
        // A seek briefly buffers too; isPlaying=false alone does not mean the file has ended.
        eventually { !player.isPlaying() && player.position() >= 4999 }
        assertEquals(5000, player.duration())
        player.resume()
        eventually { player.isPlaying() && player.position() < 4700 }
    }

    @Test fun rhinoRuntimeExitReleasesItsMusic() {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val source = """
            media.playMusic(${JSONObject.quote(file.path)}, 0, true);
            if (media.getMusicDuration() !== 5000) throw Error('Duration was not ready');
            media.pauseMusic();
            media.musicSeekTo(2000);
            media.resumeMusic();
        """.trimIndent()
        val execution = AutoJs.instance.scriptEngineService.execute(StringScriptSource("music-lifecycle", source), object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) { done.countDown() }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); done.countDown() }
        }, ExecutionConfig())
        try {
            assertTrue("Rhino script did not finish", done.await(35, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Rhino music failed", it) }
            eventually { musicNotification() == null }
        } finally { execution.engine?.forceStop() }
    }

    private fun player() = ScriptMusicPlayer(context).also { players.add(it) }
    private fun musicNotification(): Notification? = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.firstOrNull { it.id == 0x534D }?.notification
    private fun eventually(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!condition()) {
            assertTrue("Music condition timed out", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(40)
        }
    }
}
