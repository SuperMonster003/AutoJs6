package org.autojs.autojs.runtime.api;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import org.autojs.autojs.core.media.ScriptMusicPlayer;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage;

/**
 * Created by Stardust on Feb 12, 2018.
 */
public class Media implements MediaScannerConnection.MediaScannerConnectionClient {

    private MediaScannerConnection mScannerConnection;
    private final ScriptMusicPlayer mMusicPlayer;
    private final ScriptRuntime mRuntime;

    public Media(Context context, ScriptRuntime runtime) {
        mScannerConnection = new MediaScannerConnection(context, this);
        mRuntime = runtime;
        mMusicPlayer = new ScriptMusicPlayer(context);
        mScannerConnection.connect();
        runtime.resourceScope.own(Stage.MEDIA, "media.recycle", this::recycle);
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
        mMusicPlayer.play(mRuntime.files.nonNullPath(path), volume, looping);
    }

    public void musicSeekTo(int m) {
        mMusicPlayer.seekTo(m);
    }

    public boolean isMusicPlaying() {
        return mMusicPlayer.isPlaying();
    }

    public void pauseMusic() {
        mMusicPlayer.pause();
    }

    public void resumeMusic() {
        mMusicPlayer.resume();
    }

    public int getMusicDuration() {
        return mMusicPlayer.duration();
    }

    public int getMusicCurrentPosition() {
        return mMusicPlayer.position();
    }

    public void stopMusic() {
        mMusicPlayer.stop();
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        /* Empty body. */
    }

    public void recycle() {
        try {
            if (mScannerConnection != null) {
                mScannerConnection.disconnect();
                mScannerConnection = null;
            }
        } finally {
            mMusicPlayer.close();
        }
    }
}
