package org.autojs.autojs.runtime.api;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;

import java.io.IOException;

/**
 * Created by Stardust on Feb 12, 2018.
 */
public class Media implements MediaScannerConnection.MediaScannerConnectionClient {

    private MediaScannerConnection mScannerConnection;
    private MediaPlayerWrapper mMediaPlayer;
    private final ScriptRuntime mRuntime;

    public Media(Context context, ScriptRuntime runtime) {
        mScannerConnection = new MediaScannerConnection(context, this);
        mRuntime = runtime;
        mScannerConnection.connect();
    }

    public void scanFile(String path) {
        String mimeType = Mime.fromFileOrWildcard(path);
        mScannerConnection.scanFile(mRuntime.files.nonNullPath(path), mimeType);
    }

    @Override
    public void onMediaScannerConnected() {
        /* Empty body. */
    }

    public void playMusic(String path, float volume) {
        playMusic(path, volume, false);
    }

    public void playMusic(String path) {
        playMusic(path, 1.0f);
    }

    public void playMusic(String path, float volume, boolean looping) {
        path = mRuntime.files.nonNullPath(path);
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayerWrapper();
        }
        mMediaPlayer.stopAndReset();
        try {
            mMediaPlayer.setDataSource(path);
            mMediaPlayer.setVolume(volume, volume);
            mMediaPlayer.setLooping(looping);
            mMediaPlayer.prepare();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        mMediaPlayer.start();
    }

    public void musicSeekTo(int m) {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.seekTo(m);
    }

    public boolean isMusicPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    public void pauseMusic() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.pause();
    }

    public void resumeMusic() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.start();
    }

    public int getMusicDuration() {
        if (mMediaPlayer == null) {
            return 0;
        }
        return mMediaPlayer.getDuration();
    }

    public int getMusicCurrentPosition() {
        if (mMediaPlayer == null)
            return -1;
        return mMediaPlayer.getCurrentPosition();
    }

    public void stopMusic() {
        if (mMediaPlayer == null)
            return;
        mMediaPlayer.stop();
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        /* Empty body. */
    }

    public void recycle() {
        if (mScannerConnection != null) {
            mScannerConnection.disconnect();
            mScannerConnection = null;
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
        }
    }

    private static class MediaPlayerWrapper extends MediaPlayer {

        static final int STATE_NOT_INITIALIZED = 0;
        static final int STATE_PREPARING = 1;
        static final int STATE_PREPARED = 2;
        static final int STATE_START = 3;
        static final int STATE_PAUSED = 4;
        static final int STATE_STOPPED = 5;
        static final int STATE_RELEASED = 6;

        private int mState = STATE_NOT_INITIALIZED;

        public int getState() {
            return mState;
        }

        @Override
        public void prepare() throws IOException, IllegalStateException {
            mState = STATE_PREPARING;
            super.prepare();
            mState = STATE_PREPARED;
        }

        @Override
        public void prepareAsync() throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void start() throws IllegalStateException {
            super.start();
            mState = STATE_START;
        }

        @Override
        public void stop() throws IllegalStateException {
            super.stop();
            mState = STATE_STOPPED;
        }

        @Override
        public void pause() throws IllegalStateException {
            super.pause();
            mState = STATE_PAUSED;
        }

        @Override
        public void release() {
            super.release();
            mState = STATE_RELEASED;
        }

        @Override
        public void reset() {
            super.reset();
            mState = STATE_NOT_INITIALIZED;
        }

        public void stopAndReset() {
            try {
                if (mState == STATE_START || mState == STATE_PAUSED) {
                    stop();
                }
                if (mState != STATE_NOT_INITIALIZED) {
                    reset();
                }
            } catch (IllegalStateException ignored) {
                /* Ignored. */
            }
        }
    }
}
