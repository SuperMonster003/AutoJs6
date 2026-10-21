package org.autojs.autojs.runtime.api;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.autojs.autojs.core.pref.store.SettingsStores;
import org.autojs.autojs.core.pref.store.SharedPreferencesStore;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.runtime.ScriptRuntime;
import org.autojs.autojs.runtime.api.shell.PipeShell;
import org.autojs.autojs.runtime.api.shell.ShellLauncher;
import org.autojs.autojs.runtime.api.shell.ShellPipes;
import org.autojs.autojs.runtime.exception.ScriptInterruptedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;

import jackpal.androidterm.ShellTermSession;
import jackpal.androidterm.emulatorview.TermSession;
import jackpal.androidterm.util.TermSettings;

/**
 * Created by Stardust on Apr 24, 2017.
 */
public class Shell extends AbstractShell {

    public interface Callback {

        void onOutput(String str);

        void onNewLine(String line);

        void onInitialized();

        void onInterrupted(InterruptedException e);
    }

    public static class SimpleCallback implements Callback {

        @Override
        public void onOutput(String str) {
            /* Empty body. */
        }

        @Override
        public void onNewLine(String str) {
            /* Empty body. */
        }

        @Override
        public void onInitialized() {
            /* Empty body. */
        }

        @Override
        public void onInterrupted(InterruptedException e) {
            /* Empty body. */
        }
    }

    private static final boolean DEBUG = true;
    private static final String TAG = "Shell";

    private volatile TermSession mTermSession;
    private final Object mInitLock = new Object();
    private final Object mExitLock = new Object();
    private final Object mCommandOutputLock = new Object();
    private volatile RuntimeException mInitException;
    private volatile boolean mInitialized = false;
    private volatile boolean mWaitingExit = false;
    private volatile String mCommandOutput = null;
    private Callback mCallback;

    /**
     * Non-null for a pipe-based Shell (options or command constructor); a terminal-session Shell keeps it null.
     * zh-CN: 基于管道的 Shell (选项或命令构造器) 时非空; 终端会话 Shell 保持为 null.
     */
    private final PipeShell mPipe;

    public Shell(Context context, boolean root) {
        super(context, root);
        mPipe = null;
    }

    /**
     * A pipe-based shell, compatible with Auto.js Pro 9.3 (see {@link PipeShell}): options {@code cmd}, {@code root},
     * {@code adb} (runs in the Shizuku user service), {@code pty} (output on a pseudo-terminal), {@code env} and
     * {@code cwd}. Unlike the terminal-session constructors it offers the stream methods,
     * {@link #execForResult(String)} and a reliable {@link #execAndWaitFor(String)}.
     * zh-CN: 基于管道的 shell, 兼容 Auto.js Pro 9.3 (见 {@link PipeShell}): 选项 {@code cmd}, {@code root}, {@code adb} (在 Shizuku 用户服务中运行),
     * {@code pty} (输出接到伪终端), {@code env} 与 {@code cwd}. 与终端会话构造器不同, 它提供流方法, {@link #execForResult(String)} 以及可靠的
     * {@link #execAndWaitFor(String)}.
     */
    public Shell(Map<?, ?> options) {
        this(ShellPipes.optionsOf(options));
    }

    /**
     * Auto.js Pro 9.3 compatible: a pipe-based shell running {@code cmd}, such as "sh" or "su".
     * zh-CN: 兼容 Auto.js Pro 9.3: 运行 {@code cmd} (如 "sh" 或 "su") 的基于管道的 shell.
     */
    public Shell(String cmd) {
        this(ShellPipes.optionsOfCommand(cmd));
    }

    private Shell(ShellLauncher.Options options) {
        super(ScriptRuntime.getApplicationContext(), options.getRoot(), false);
        mPipe = ShellPipes.open(options);
    }

    /**
     * The Auto.js 4 signature of {@code com.stardust.autojs.core.util.Shell}, which resolves to this class through
     * {@link org.autojs.autojs.rhino.StardustClassAliases} (roadmap P7.3). {@code shouldReadOutput} was stored but
     * never read by Auto.js 4, so it has no effect here either.
     * zh-CN: Auto.js 4 的 {@code com.stardust.autojs.core.util.Shell} 构造器签名, 该类名经
     * {@link org.autojs.autojs.rhino.StardustClassAliases} 解析到本类 (roadmap P7.3); Auto.js 4 只保存而从未读取
     * {@code shouldReadOutput}, 这里同样不产生作用.
     */
    @SuppressWarnings("unused")
    public Shell(Context context, boolean root, boolean shouldReadOutput) {
        this(context, root);
    }

    public Shell(Context context) {
        this(context, false);
    }

    public Shell(boolean root) {
        this(ScriptRuntime.getApplicationContext(), root);
    }

    public Shell() {
        this(false);
    }

    @Override
    protected void init(final String initialCommand) {
        Handler uiHandler = new Handler(mContext.getMainLooper());
        uiHandler.post(() -> {
            TermSettings settings = new TermSettings(mContext.getResources(), ((SharedPreferencesStore) SettingsStores.getDefault()).getPreferences());
            try {
                mTermSession = new MyShellTermSession(settings, initialCommand);
                mTermSession.initializeEmulator(1024, 40);
            } catch (IOException e) {
                mInitException = new UncheckedIOException(e);
                synchronized (mInitLock) {
                    mInitLock.notifyAll();
                }
            }
        });
    }

    public void exec(String command) {
        if (mPipe != null) {
            mPipe.exec(command);
            return;
        }
        ensureInitialized();
        mTermSession.write(command + "\n");
    }

    public String execAndWaitFor(String command) {
        if (mPipe != null) {
            return mPipe.execAndWaitFor(command);
        }
        exec(command);
        synchronized (mCommandOutputLock) {
            try {
                mCommandOutputLock.wait();
                return mCommandOutput;
            } catch (InterruptedException e) {
                throw new ScriptInterruptedException();
            }
        }
    }

    /**
     * Runs {@code command}, waits for it and returns its stdout, stderr and exit code, split by a marker after the
     * command. Pipe-based Shell only.
     * zh-CN: 运行 {@code command}, 等待其结束并返回 stdout, stderr 与退出码, 由命令后的标记分割. 仅限基于管道的 Shell.
     */
    public Result execForResult(String command) {
        return requirePipe("execForResult").execForResult(command);
    }

    public void setCallback(Callback callback) {
        if (mPipe != null) {
            mPipe.setCallback(callback);
            return;
        }
        mCallback = callback;
    }

    public boolean isInitialized() {
        return mPipe != null || mInitialized;
    }

    /**
     * Whether this Shell runs in the Shizuku user service ({@code adb: true}).
     * zh-CN: 此 Shell 是否运行在 Shizuku 用户服务中 ({@code adb: true}).
     */
    public boolean isAdb() {
        return mPipe != null && mPipe.getOptions().getAdb();
    }

    public InputStream getInputStream() {
        return requirePipe("getInputStream").getInputStream();
    }

    public InputStream getErrorStream() {
        return requirePipe("getErrorStream").getErrorStream();
    }

    public OutputStream getOutputStream() {
        return requirePipe("getOutputStream").getOutputStream();
    }

    public int waitFor() {
        return requirePipe("waitFor").waitFor();
    }

    /**
     * The exit code, or null while the shell runs. Pipe-based Shell only.
     * zh-CN: 退出码, shell 运行期间为 null. 仅限基于管道的 Shell.
     */
    public Integer exitCode() {
        return requirePipe("exitCode").exitCode();
    }

    /**
     * Closes stdin and returns the remaining stdout once the shell exited. Pipe-based Shell only.
     * zh-CN: 关闭 stdin, shell 退出后返回剩余的 stdout. 仅限基于管道的 Shell.
     */
    public String exitAndReadOutput() {
        return requirePipe("exitAndReadOutput").exitAndReadOutput();
    }

    /**
     * The underlying {@link Process} of a pipe-based Shell, or the {@link TermSession} of a terminal-session Shell.
     * zh-CN: 基于管道的 Shell 的底层 {@link Process}, 或终端会话 Shell 的 {@link TermSession}.
     */
    public Object raw() {
        return mPipe != null ? mPipe.raw() : mTermSession;
    }

    private PipeShell requirePipe(String method) {
        if (mPipe == null) {
            throw new IllegalStateException(method + "() is not available on a Shell running in a terminal session (new Shell() / new Shell(root)). "
                    + "Create a pipe-based Shell instead, for example new Shell({ root: true }) or new Shell({ adb: true })");
        }
        return mPipe;
    }

    private void ensureInitialized() {
        checkInitException();
        if (mTermSession == null) {
            logDebug("ensureInitialized: not init");
            checkInitException();
            waitInitialization();
            if (mTermSession == null) {
                checkInitException();
                throw new IllegalStateException();
            }
        } else {
            logDebug("ensureInitialized: init");
        }
    }

    private void logDebug(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }

    private boolean isShellPrompt(String output) {
        return output.endsWith(" # ") || output.endsWith(" $ ");
    }

    private void checkInitException() {
        if (mInitException != null) {
            throw mInitException;
        }
    }

    private void waitInitialization() {
        synchronized (mInitLock) {
            if (mInitialized) {
                return;
            }
            logDebug("waitInitialization: enter");
            try {
                mInitLock.wait();
                logDebug("waitInitialization: exit");
            } catch (InterruptedException e) {
                onInterrupted(e);
            }
        }
    }

    private void onInterrupted(InterruptedException e) {
        if (mCallback == null) {
            exit();
            throw new ScriptInterruptedException();
        } else {
            mCallback.onInterrupted(e);
        }
    }

    @Override
    public void exit() {
        if (mPipe != null) {
            mPipe.exit();
            return;
        }
        mTermSession.finish();
    }

    @Override
    public void exitAndWaitFor() {
        if (mPipe != null) {
            mPipe.exitAndWaitFor();
            return;
        }
        execExitAndWait();
        if (!isExecWithRoot()) {
            return;
        }
        execExitAndWait();
    }

    private void execExitAndWait() {
        synchronized (mExitLock) {
            mWaitingExit = true;
            exec("exit");
            try {
                mExitLock.wait();
            } catch (InterruptedException e) {
                onInterrupted(e);
            }
        }
    }

    /**
     * The terminal session; null for a pipe-based Shell.
     * zh-CN: 终端会话; 基于管道的 Shell 为 null.
     */
    public TermSession getTermSession() {
        return mTermSession;
    }

    private class MyShellTermSession extends ShellTermSession {

        private final StringBuilder mStringBuffer = new StringBuilder();
        private final ArrayList<String> mCommandOutputs = new ArrayList<>();

        public MyShellTermSession(TermSettings settings, String initialCommand) throws IOException {
            super(settings, initialCommand);
        }

        private void onNewLine(String line) {
            if (!line.isEmpty() && !line.contains("/dev/input/event")) {
                logDebug("onNewLine: " + line);
            }
            if (!mInitialized) {
                if (!isExecWithRoot() && line.endsWith(" $ sh")) {
                    notifyInitialized();
                }
            } else {
                mCommandOutputs.add(line);
            }
            if (mCallback != null) {
                mCallback.onNewLine(line);
            }
            if (mWaitingExit && line.endsWith(" exit")) {
                notifyExit();
            }
        }

        private void onCommandOutput(ArrayList<String> output) {
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < output.size(); i++) {
                result.append(output.get(i));
                if (i < output.size() - 1) {
                    result.append("\n");
                }
            }
            logDebug("onCommandOutput: lines = " + output + ", output = " + result);
            synchronized (mCommandOutputLock) {
                mCommandOutput = result.toString();
                mCommandOutputLock.notifyAll();
            }
        }

        private void onOutput(String str) {
            if (!str.isEmpty() && !str.contains("/dev/input/event")) {
                logDebug("onOutput: " + str);
            }
            if (!mInitialized && isShellPrompt(str)) {
                notifyInitialized();
            }
            int start = 0;
            int i;
            while (true) {
                i = str.indexOf("\n", start);
                if (i > 0) {
                    mStringBuffer.append(str.substring(start, i));
                    onNewLine(mStringBuffer.toString().trim());
                    mStringBuffer.setLength(0);
                    // onNewLine((mStringBuffer + str.substring(0, i - 1)).trim());
                    // mStringBuffer.delete(0, mStringBuffer.length());
                } else {
                    if (start <= str.length() - 1) {
                        int len = mStringBuffer.length();
                        if (len > 1 << 24) {
                            Log.e(TAG, " String buffer was cleared as its huge length of " + len);

                            mStringBuffer.setLength(0);
                            // mStringBuffer.delete(0, len);
                        } else if (len > 0) {
                            logDebug("Length of string buffer: " + len);
                        }
                        mStringBuffer.append(str.substring(start));
                    }
                    break;
                }
                start = i + 1;
            }
            if (str.endsWith(" # ") || str.endsWith(" $ ")) {
                onCommandOutput(mCommandOutputs);
                mCommandOutputs.clear();
            }
            if (mCallback != null) {
                mCallback.onOutput(str.replace("\r", ""));
            }
        }

        @Override
        protected void processInput(byte[] data, int offset, int count) {
            onOutput(new String(data, offset, count));
        }

        private void notifyExit() {
            synchronized (mExitLock) {
                mWaitingExit = false;
                mExitLock.notify();
            }
        }

        private void notifyInitialized() {
            logDebug("notifyInitialized");
            mInitialized = true;
            synchronized (mInitLock) {
                mInitLock.notifyAll();
            }
            if (mCallback != null) {
                mCallback.onInitialized();
            }
        }

        @Override
        protected void onProcessExit() {
            super.onProcessExit();
            if (!mInitialized && mInitException == null) {
                mInitException = new IllegalStateException("Shell process exited before initialization");
            }
            if (!mInitialized) {
                synchronized (mInitLock) {
                    mInitLock.notifyAll();
                }
            }
            synchronized (mExitLock) {
                mWaitingExit = false;
                mExitLock.notify();
            }
        }

    }

}
