package org.autojs.autojs.theme.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.theme.app.ColorEntities.ColorHistory
import org.autojs.autojs.theme.app.ColorEntities.ColorInfo
import org.autojs.autojs.theme.app.ColorEntities.HistorySource
import org.autojs.autojs.theme.app.ColorEntities.PaletteHistory
import org.autojs.autojs.theme.library.ColorLibraryIds
import org.autojs.autojs.theme.library.ColorLibraryRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Usage counting of the history tables (roadmap P5.2) and favorites of the repository (roadmap P5.1) on a real
 * database built by the production builder.
 * zh-CN: 历史表的使用计数 (Roadmap P5.2) 与仓库中的收藏 (Roadmap P5.1), 使用生产 builder 建立的真实数据库.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
@RunWith(AndroidJUnit4::class)
class ColorUsageDatabaseTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val dbName = "color-usage-test.db"

    @Before
    fun deleteBefore() {
        context.deleteDatabase(dbName)
    }

    @After
    fun deleteAfter() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun upsertsCountUsesAndKeepTheLatestSource() = withDatabase { db ->
        val dao = db.colorHistoryDao()
        repeat(2) { dao.upsert(ColorHistory(libraryId = 2, itemId = 5, lastUsedTime = 1L)) }
        dao.getByLibraryIdAndItemId(2, 5)!!.let {
            assertEquals(2, it.useCount)
            assertEquals(HistorySource.USER, it.source)
            assertTrue(it.lastUsedTime > 1L)
        }
        dao.upsert(ColorHistory(libraryId = 2, itemId = 5, source = HistorySource.SCRIPT, sourceDetail = "demo.js"))
        dao.getByLibraryIdAndItemId(2, 5)!!.let {
            assertEquals(3, it.useCount)
            assertEquals(HistorySource.SCRIPT, it.source)
            assertEquals("demo.js", it.sourceDetail)
        }
        val palette = db.paletteHistoryDao()
        repeat(3) { palette.upsert(PaletteHistory(colorInfo = ColorInfo("#123456"))) }
        assertEquals(3, palette.getByHex("#123456")!!.useCount)
    }

    @Test
    fun favoritesAreUniqueOpaqueAndOrdered() = withDatabase { db ->
        var time = 100L
        val repository = ColorLibraryRepository(db) { time++ }
        val first = repository.addFavorite(0x00123456)
        assertEquals(first, repository.addFavorite(0x80123456.toInt()))
        assertEquals(0xFF123456.toInt(), first.color)
        val second = repository.addFavorite(0xFF654321.toInt())
        val third = repository.addFavorite(0xFFABCDEF.toInt())
        assertEquals(listOf(0, 1, 2), repository.listFavorites().map { it.position })

        repository.setFavoritePinned(0xFFABCDEF.toInt(), true)
        // Pinned favorites list first. zh-CN: 置顶的收藏排在最前.
        assertEquals(listOf(third.id, first.id, second.id), repository.listFavorites().map { it.id })

        repository.reorderFavorites(listOf(second.id, first.id))
        assertEquals(listOf(0, 1, 2), listOf(second.id, first.id, third.id).map { id -> repository.findFavoriteById(id)!!.position })

        // Removing a favorite also removes its history. zh-CN: 取消收藏同时删除其历史记录.
        db.colorHistoryDao().upsert(ColorHistory(libraryId = ColorLibraryIds.FAVORITES, itemId = first.id.toInt()))
        repository.removeFavorite(0x123456)
        assertNull(repository.findFavorite(0xFF123456.toInt()))
        assertNull(db.colorHistoryDao().getByLibraryIdAndItemId(ColorLibraryIds.FAVORITES.toLong(), first.id))
    }

    private fun withDatabase(block: suspend (ColorHistoryDatabase) -> Unit) {
        val db = ColorHistoryDatabase.createBuilder(context, dbName).build()
        try {
            runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

}
