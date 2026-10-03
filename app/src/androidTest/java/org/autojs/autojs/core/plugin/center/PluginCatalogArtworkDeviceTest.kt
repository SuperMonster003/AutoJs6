package org.autojs.autojs.core.plugin.center

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class PluginCatalogArtworkDeviceTest {
    @Test fun catalogAndInstalledArtworkHaveIdenticalPixelsForEveryTheme() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("autojs.catalog.icons") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = JSONObject(File(context.cacheDir, "catalog-icons-verification.json").readText()).getJSONObject("icons")
        for (packageName in manifest.keys()) {
            val packageContext = context.createPackageContext(packageName, 0)
            for ((mode, night) in listOf("day" to false, "night" to true)) {
                val config = Configuration(packageContext.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
                }
                val themed = packageContext.createConfigurationContext(config)
                @Suppress("DiscouragedApi")
                val id = themed.resources.getIdentifier("ic_plugin_center", "mipmap", packageName)
                assertNotEquals(packageName, 0, id)
                val local = themed.resources.openRawResource(id).use { BitmapFactory.decodeStream(it) }
                val path = manifest.getJSONObject(packageName).getJSONObject(mode).getString("path")
                val connection = URL("https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Official-Plugins-Index/main/$path")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                try {
                    val remote = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                    assertNotNull("Catalog image: $packageName/$mode", remote)
                    assertEquals(local.width, remote.width)
                    assertEquals(local.height, remote.height)
                    val localPixels = IntArray(local.width * local.height)
                    val remotePixels = IntArray(remote.width * remote.height)
                    local.getPixels(localPixels, 0, local.width, 0, 0, local.width, local.height)
                    remote.getPixels(remotePixels, 0, remote.width, 0, 0, remote.width, remote.height)
                    assertArrayEquals("Installed/catalog artwork differs: $packageName/$mode", localPixels, remotePixels)
                    remote.recycle()
                } finally {
                    local.recycle()
                    connection.disconnect()
                }
            }
        }
    }
}
