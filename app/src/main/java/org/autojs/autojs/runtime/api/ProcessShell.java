package org.autojs.autojs.runtime.api;

import static org.autojs.autojs.util.StringUtils.str;

import android.util.Log;

import org.autojs.autojs.pio.UncheckedIOException;
import org.autojs.autojs.util.ProcessUtils;
import org.autojs.autojs6.R;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by Stardust on 2017/1/20.
 * <p>
 * 来自网络~~
 */
public class ProcessShell extends AbstractShell {

    private static final String TAG = "ProcessShell";

    private Process mProcess;
    private DataOutputStream mCommandOutputStream;
    private BufferedReader mSucceedReader;
    private BufferedReader mErrorReader;

    private final StringBuilder mSucceedOutput = new StringBuilder();
    private final StringBuilder mErrorOutput = new StringBuilder();

    public ProcessShell() {

    }

    public ProcessShell(boolean root) {
        super(root);
    }

    @Override
    protected void init(String initialCommand) {
        try {
            mProcess = new ProcessBuilder(initialCommand).redirectErrorStream(true).start();
            mCommandOutputStream = new DataOutputStream(mProcess.getOutputStream());
            mSucceedReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
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
        if (mSucceedReader != null) {
            try {
                mSucceedReader.close();
            } catch (IOException ignored) {

            }
            mSucceedReader = null;
        }
        if (mErrorReader != null) {
            try {
                mErrorReader.close();
            } catch (IOException ignored) {

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
        return readSucceedOutput().readErrorOutput();
    }

    public ProcessShell readSucceedOutput() {
        read(mSucceedReader, mSucceedOutput);
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

    public StringBuilder getSucceedOutput() {
        return mSucceedOutput;
    }

    public StringBuilder getErrorOutput() {
        return mErrorOutput;
    }

    public Process getProcess() {
        return mProcess;
    }

    public BufferedReader getSucceedReader() {
        return mSucceedReader;
    }

    public BufferedReader getErrorReader() {
        return mErrorReader;
    }

    public static Result exec(String command, boolean isRoot) {
        String[] commands = command.split(COMMAND_LINE_END);
        return exec(commands, isRoot);
    }

    public static Result exec(String[] commands, boolean isRoot) {
        ProcessShell shell = null;
        try {
            shell = new ProcessShell(isRoot);
            for (String command : commands) {
                shell.exec(command);
            }
            shell.exec(COMMAND_EXIT);
            Result result = new Result();
            result.code = shell.waitFor();
            shell.readAll();
            result.error = shell.getErrorOutput().toString();
            result.result = shell.getSucceedOutput().toString();
            shell.exit();
            return result;
        } finally {
            if (shell != null) {
                shell.exit();
            }
        }
    }

    public static Result execCommand(String[] commands, boolean isRoot) {
        try {
            Process process = isRoot ? getRootProcess() : getShellProcess();
            return execCommand(commands, process);
        } catch (Exception e) {
            Result result = new Result();
            String message = e.getMessage();
            result.code = 1;
            result.result = "";
            result.error = message != null ? message : "";
            return result;
        }
    }

    public static Result execCommand(String[] commands, Process process) {
        Result commandResult = new Result();
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException(str(R.string.error_empty_shell_command));
        }
        DataOutputStream os = null;
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
            commandResult.code = process.waitFor();
            commandResult.result = readAll(process.getInputStream());
            commandResult.error = readAll(process.getErrorStream());
            Log.d(TAG, commandResult.toString());
        } catch (Exception e) {
            // @Overwrite by SuperMonster003 on Apr 26, 2022.
            //  ! Try write exception into commandResult and make it visible to AutoJs6 console.
            //  ! Example (for non-root devices): log(shell('date', true));

            // throw new ScriptInterruptedException(e);

            String message = e.getMessage();
            String aimErrStr = "error=";

            int index = message != null ? message.indexOf(aimErrStr) : -1;

            commandResult.code = index < 0 ? 1 : Integer.parseInt(message.substring(index + aimErrStr.length()).replaceAll("^(\\d+).+", "$1"));
            commandResult.result = "";
            commandResult.error = message != null ? message : "";
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) {
                    process.getInputStream().close();
                    process.getOutputStream().close();
                }
            } catch (IOException ignored) {

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
        String[] commands = command.split(COMMAND_LINE_END);
        return execCommand(commands, withRoot);
    }

}