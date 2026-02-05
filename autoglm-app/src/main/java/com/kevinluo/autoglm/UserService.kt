package com.kevinluo.autoglm

import com.kevinluo.autoglm.util.Logger
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * User service for executing shell commands with elevated privileges.
 * This service runs in a separate process with Shizuku permissions.
 */
class UserService : IUserService.Stub() {
    /**
     * Destroys the service and exits the process.
     */
    override fun destroy() {
        Logger.i(TAG, "destroy")
        System.exit(0)
    }

    /**
     * Executes a shell command and returns the output.
     *
     * @param command The shell command to execute
     * @return The command output including stdout, stderr, and exit code
     */
    override fun executeCommand(command: String): String = try {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))

        val output = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        val errorOutput = StringBuilder()
        while (errorReader.readLine().also { line = it } != null) {
            errorOutput.append(line).append("\n")
        }

        val exitCode = process.waitFor()
        reader.close()
        errorReader.close()

        if (errorOutput.isNotEmpty()) {
            output.append("\n[stderr]\n").append(errorOutput)
        }
        output.append("\n[exit code: $exitCode]")

        output.toString()
    } catch (e: Exception) {
        "Error: ${e.message}\n${e.stackTraceToString()}"
    }

    companion object {
        private const val TAG = "UserService"
    }
}
