package org.autojs.autojs.core.accessibility

import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import com.google.gson.GsonBuilder
import org.autojs.autojs.util.WorkingDirectoryUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads and writes layout files: a captured node tree saved as `<working directory>/layouts/<time>.layout.json`
 * so it can be inspected later without the app in front.
 * zh-CN: 读写布局文件: 把捕获的节点树保存为 `<工作目录>/layouts/<时间>.layout.json`, 之后无需目标应用在前台也能分析.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
object LayoutSnapshotFiles {

    const val SUFFIX = ".layout.json"
    const val DIRECTORY_NAME = "layouts"
    private const val FORMAT_VERSION = 1

    // Compact JSON: a launcher tree is a few thousand nodes, pretty printing would triple the file.
    // zh-CN: 紧凑 JSON: 桌面树有数千节点, 美化输出会使文件增大两倍.
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    /** The envelope written to disk. zh-CN: 写入磁盘的外层结构. */
    @Keep
    class LayoutSnapshot(
        var version: Int = FORMAT_VERSION,
        var generator: String = "AutoJs6",
        var capturedAt: String = "",
        var root: NodeInfoSnapshot? = null,
    )

    fun isLayoutFile(name: String?) = name?.endsWith(SUFFIX, ignoreCase = true) == true

    fun directory() = File(WorkingDirectoryUtils.path, DIRECTORY_NAME)

    /**
     * Writes [root] into a new timestamped file and returns it.
     * zh-CN: 把 [root] 写入新的时间戳命名文件并返回该文件.
     */
    @WorkerThread
    fun save(root: NodeInfo): File {
        val dir = directory().also { it.mkdirs() }
        val stamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
        var file = File(dir, stamp + SUFFIX)
        var serial = 1
        while (file.exists()) {
            file = File(dir, "${stamp}_${++serial}$SUFFIX")
        }
        val snapshot = LayoutSnapshot(
            capturedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date()),
            root = NodeInfoSnapshot.of(root),
        )
        file.writeText(gson.toJson(snapshot), Charsets.UTF_8)
        return file
    }

    /**
     * Loads the node tree of a layout file.
     * zh-CN: 读取布局文件中的节点树.
     */
    @WorkerThread
    fun load(file: File): NodeInfo {
        val snapshot = gson.fromJson(file.readText(Charsets.UTF_8), LayoutSnapshot::class.java)
        val root = snapshot?.root ?: throw IllegalArgumentException("No root node in ${file.path}")
        return root.toNodeInfo()
    }

}
