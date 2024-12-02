package org.autojs.autojs.runtime.api;

import android.util.Log;
import kotlin.text.Regex;
import kotlin.text.RegexOption;
import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.util.ProcessUtils;
import org.autojs.autojs6.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.autojs.autojs.util.StringUtils.str;

/**
 * Created by Stardust on Jan 20, 2017.
 * <p>
 * 来自网络~~
 */
public class ProcessShell extends AbstractShell {

    private static final String TAG = "ProcessShell";

    private Process mProcess;
    private DataOutputStream mCommandOutputStream;
    private BufferedReader mSuccessReader;
    private BufferedReader mErrorReader;

    private final StringBuilder mSuccessOutput = new StringBuilder();
    private final StringBuilder mErrorOutput = new StringBuilder();

    public ProcessShell() {
        /* Empty body. */
    }

    public ProcessShell(boolean root) {
        super(root);
    }

    @Override
    protected void init(String initialCommand) {
        try {
            mProcess = new ProcessBuilder(initialCommand).redirectErrorStream(true).start();
            mCommandOutputStream = new DataOutputStream(mProcess.getOutputStream());
            mSuccessReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
            mErrorReader = new BufferedReader(new InputStreamReader(mProcess.getErrorStream()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void exec(String command) {
        try {
            mCommandOutputStream.writeBytes(command);
            if (!command.endsWith(COMMAND_LINE_END)) {
                mCommandOutputStream.writeBytes(COMMAND_LINE_END);
            }
            mCommandOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void exit() {
        if (mProcess != null) {
            Log.d(TAG, "exit: pid = " + ProcessUtils.getProcessPid(mProcess));
            mProcess.destroy();
            mProcess = null;
        }
        if (mSuccessReader != null) {
            try {
                mSuccessReader.close();
            } catch (IOException ignored) {
                /* Empty body. */
            }
            mSuccessReader = null;
        }
        if (mErrorReader != null) {
            try {
                mErrorReader.close();
            } catch (IOException ignored) {
                /* Empty body. */
            }
            mErrorReader = null;
        }

    }

    @Override
    public void exitAndWaitFor() {
        exec(COMMAND_EXIT);
        waitFor();
        exit();
    }

    public int waitFor() {
        try {
            return mProcess.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ProcessShell readAll() {
        return readSuccessOutput().readErrorOutput();
    }

    public ProcessShell readSuccessOutput() {
        read(mSuccessReader, mSuccessOutput);
        return this;
    }

    private void read(BufferedReader reader, StringBuilder sb) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append(COMMAND_LINE_END);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ProcessShell readErrorOutput() {
        read(mErrorReader, mErrorOutput);
        return this;
    }

    public StringBuilder getSuccessOutput() {
        return mSuccessOutput;
    }

    public StringBuilder getErrorOutput() {
        return mErrorOutput;
    }

    public Process getProcess() {
        return mProcess;
    }

    public BufferedReader getSuccessReader() {
        return mSuccessReader;
    }

    public BufferedReader getErrorReader() {
        return mErrorReader;
    }

    public static Result exec(String command, boolean withRoot) {
        String[] commands = command.split(COMMAND_LINE_END);
        return exec(commands, withRoot);
    }

    public static Result exec(String[] commands, boolean withRoot) {
        ProcessShell shell = null;
        try {
            shell = new ProcessShell(withRoot);
            for (String command : commands) {
                shell.exec(command);
            }
            shell.exec(COMMAND_EXIT);
            int code = shell.waitFor();
            shell.readAll();
            Result result = new Result(code, shell);
            shell.exit();
            return result;
        } finally {
            if (shell != null) {
                shell.exit();
            }
        }
    }

    public static Result execCommand(String[] commands, boolean withRoot) {
        try {
            Process process = withRoot ? getRootProcess() : getShellProcess();
            return execCommand(commands, process);
        } catch (Exception e) {
            return new Result(1, e);
        }
    }

    public static Result execCommand(String[] commands, Process process) {
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException(str(R.string.error_empty_shell_command));
        }
        DataOutputStream os = null;
        Result commandResult;
        try {
            os = new DataOutputStream(process.getOutputStream());
            for (String command : commands) {
                if (command != null) {
                    os.write(command.getBytes());
                    os.writeBytes(COMMAND_LINE_END);
                    os.flush();
                }
            }
            os.writeBytes(COMMAND_EXIT);
            os.flush();
            Log.d(TAG, "pid = " + ProcessUtils.getProcessPid(process));
            commandResult = new Result(process.waitFor(),
                    readAll(process.getInputStream()),
                    readAll(process.getErrorStream()));
            Log.d(TAG, commandResult.toString());
        } catch (Exception e) {
            // @Overwrite by SuperMonster003 on Apr 26, 2022.
            //  ! Try writing exception into commandResult and make it visible to AutoJs6 console.
            //  ! Example (for non-root devices): log(shell('date', true));
            //  ! zh-CN:
            //  ! 尝试将异常信息写入 commandResult 并于 AutoJs6 控制台可见.
            //  ! 例如 (对于非 root 权限设备): log(shell('date', true));
            //  !
            //  # throw new ScriptInterruptedException(e);

            String message = e.getMessage();
            String aimErrStr = "error=";

            int index = message != null ? message.indexOf(aimErrStr) : -1;

            int code = index < 0 ? 1 : Integer.parseInt(message.substring(index + aimErrStr.length()).replaceAll("^(\\d+).+", "$1"));
            commandResult = new Result(code, e);
            e.printStackTrace();
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) {
                    process.getInputStream().close();
                    process.getOutputStream().close();
                }
            } catch (IOException ignored) {
                /* Ignored. */
            }
            if (process != null) {
                process.destroy();
            }
        }
        return commandResult;
    }

    public static Process getShellProcess() throws IOException {
        return Runtime.getRuntime().exec(COMMAND_SH);
    }

    public static Process getRootProcess() throws IOException {
        return Runtime.getRuntime().exec(COMMAND_SU);
    }

    private static String readAll(InputStream inputStream) throws IOException {
        String line;
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        while ((line = reader.readLine()) != null) {
            builder.append(line).append(COMMAND_LINE_END);
        }
        return builder.toString();
    }

    public static Result execCommand(String command, boolean withRoot) {
        Regex regex = new Regex("^\\s*adb\\s+shell\\s+", RegexOption.IGNORE_CASE);
        command = regex.replace(command, "");
        String[] commands = command.split(COMMAND_LINE_END);
        return execCommand(commands, withRoot);
    }

}