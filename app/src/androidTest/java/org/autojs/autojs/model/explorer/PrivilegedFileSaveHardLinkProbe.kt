package org.autojs.autojs.model.explorer

import android.system.Os
import android.system.OsConstants
import androidx.annotation.Keep
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.system.exitProcess

/** Test-APK-only entry point; shell SELinux permits hard links on its own /data/local/tmp files. */
@Keep
object PrivilegedFileSaveHardLinkProbe {
    @Keep
    @JvmStatic
    fun main(arguments: Array<String>) {
        try {
            check(arguments.size == 2)
            PrivilegedFileNative.configure(arguments[0])
            check(arguments[1] in listOf("existing", "concurrent"))
            val root = File("/data/local/tmp/autojs6-save-hardlink-${UUID.randomUUID()}")
            check(root.mkdir())
            Os.chmod(root.path, 0x1c0)
            try {
                val source = File(root, "source").apply { writeText("original") }
                val alias = File(root, "alias")
                if (arguments[1] == "existing") Os.link(source.path, alias.path)
                val snapshot = JSONObject(PrivilegedFileSaveEngine.statSnapshot(JSONObject().put("source", source.path).toString()))
                check(snapshot.getBoolean("ok")) { snapshot.toString() }
                val bytes = "edit".toByteArray(Charsets.UTF_8)
                val request = JSONObject().put("source", source.path).put("expected", snapshot.getJSONObject("identity"))
                    .put("expectedDigest", snapshot.getString("digest")).put("contentSize", bytes.size)
                    .put("contentDigest", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) })
                    .put("transactionId", UUID.randomUUID().toString()).put("deadlineMillis", System.currentTimeMillis() + 30_000)
                val result = if (arguments[1] == "existing") {
                    JSONObject(PrivilegedFileSaveEngine.save(request.toString(), ByteArrayInputStream(bytes)))
                } else {
                    JSONObject(PrivilegedFileSaveEngine.saveWithCommitObserver(request.toString(), ByteArrayInputStream(bytes)) { backup ->
                        Os.link(backup, alias.path)
                    })
                }
                val report = JSONObject().put("ok", true).put("result", result)
                    .put("fileInode", Os.lstat(source.path).st_ino).put("aliasInode", Os.lstat(alias.path).st_ino)
                    .put("links", Os.lstat(source.path).st_nlink).put("fileContents", source.readText())
                    .put("aliasContents", alias.readText())
                System.out.println(report.toString())
            } finally {
                check(root.path.matches(Regex("/data/local/tmp/autojs6-save-hardlink-[a-f0-9-]{36}")))
                removeFixture(root)
            }
        } catch (error: Throwable) {
            error.printStackTrace(System.err)
            exitProcess(1)
        }
    }

    private fun removeFixture(file: File) {
        if (OsConstants.S_ISDIR(Os.lstat(file.path).st_mode)) file.listFiles()!!.forEach(::removeFixture)
        Os.remove(file.path)
    }
}
