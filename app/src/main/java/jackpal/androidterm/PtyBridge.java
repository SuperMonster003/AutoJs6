package jackpal.androidterm;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Same-package bridge to the package-private pty helpers of libtermexec.
 * <p>
 * {@link TermExec#start(ParcelFileDescriptor)} drops argv[0] before it reaches execve, and the
 * pty ioctls in {@link Exec} are package-private, so the host terminal talks to both through this
 * tiny bridge instead of subclassing {@code ShellTermSession}.
 * <p>
 * zh-CN: 与 libtermexec 包私有 pty 辅助方法同包的桥接类.
 * {@link TermExec#start(ParcelFileDescriptor)} 会在 execve 前丢弃 argv[0], 而 {@link Exec} 中的 pty ioctl
 * 是包私有的, 因此宿主终端通过这个小桥接类调用它们, 而不是继承 {@code ShellTermSession}.
 */
public final class PtyBridge {

    public static final int SIGHUP = 1;
    public static final int SIGINT = 2;
    public static final int SIGKILL = 9;
    public static final int SIGTERM = 15;

    private PtyBridge() {
    }

    /**
     * Forks a child on the slave side of {@code ptmx} and execs {@code cmd} with the full argv
     * (argv[0] included) and the given {@code KEY=VALUE} environment.
     * <p>
     * zh-CN: 在 {@code ptmx} 的从端 fork 子进程并以完整 argv (含 argv[0]) 与给定的 {@code KEY=VALUE} 环境执行 {@code cmd}.
     *
     * @return the child pid
     */
    public static int createSubprocess(
            @NonNull ParcelFileDescriptor ptmx,
            @NonNull String cmd,
            @NonNull String[] argv,
            @NonNull String[] envp
    ) throws IOException {
        return TermExec.createSubprocess(ptmx, cmd, argv, envp);
    }

    public static int waitFor(int pid) {
        return TermExec.waitFor(pid);
    }

    public static void sendSignal(int pid, int signal) {
        TermExec.sendSignal(pid, signal);
    }

    public static void setWindowSize(int fd, int rows, int cols, int xPixels, int yPixels) throws IOException {
        Exec.setPtyWindowSizeInternal(fd, rows, cols, xPixels, yPixels);
    }

    public static void setUtf8Mode(int fd, boolean utf8) throws IOException {
        Exec.setPtyUTF8ModeInternal(fd, utf8);
    }

}
