package net.dongliu.apk.parser.utils

import java.io.*

/**
 * methods for load resources.
 *
 * @author dongliu
 */
object ResourceLoader {
    /**
     * load system attr ids for parse binary xml.
     */
    fun loadSystemAttrIds(): Map<Int, String> {
        try {
            toReader("/r_values.ini").use { reader ->
                val map: MutableMap<Int, String> = HashMap()
                while (true) {
                    val line: String = reader.readLine() ?: break
                    val items = line.trim().split("=")
                    if (items.size != 2) {
                        continue
                    }
                    val name = items[0].trim()
                    val id = Integer.valueOf(items[1].trim())
                    map[id] = name
                }
                return map
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    fun loadSystemStyles(): Map<Int, String> {
        val map: MutableMap<Int, String> = HashMap()
        try {
            toReader("/r_styles.ini").use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val items = line.trim().split("=")
                    if (items.size != 2) {
                        continue
                    }
                    val id = Integer.valueOf(items[1].trim())
                    val name = items[0].trim()
                    map[id] = name
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return map
    }

    private fun toReader(path: String): BufferedReader {
        return BufferedReader(
            InputStreamReader(
                ResourceLoader::class.java.getResourceAsStream(path)
            )
        )
    }
}
