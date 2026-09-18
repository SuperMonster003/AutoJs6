package org.autojs.autojs.runtime.api.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailProviderCatalogTest {

    private val catalog = MailProviderCatalog(
        """{"version":3,"providers":[""" +
            """{"id":"qq","displayName":"QQ Mail","domains":["qq.com","Foxmail.com"],"imap":{"host":"imap.qq.com","port":993,"tls":"ssl"}},""" +
            """{"id":" 163 ","domains":["163.com"]},""" +
            """{"domains":["orphan.test"]},""" +
            """"not an object",""" +
            """{"id":"","domains":["blank.test"]}""" +
            """]}""",
    )

    @Test
    fun missingBlankOrInvalidCatalogIsEmpty() {
        listOf(null, "", "  ", "[]", "42", "{\"providers\":{}}", "{bad").forEach { json ->
            val empty = MailProviderCatalog(json)
            assertEquals(json.toString(), 0, empty.version)
            assertTrue(json.toString(), empty.ids.isEmpty())
            assertTrue(json.toString(), empty.list().isEmpty())
            assertNull(json.toString(), empty.get("qq"))
            assertNull(json.toString(), empty.resolve("a@qq.com"))
        }
    }

    @Test
    fun entriesWithoutAnIdAreSkippedAndIdsAreNormalized() {
        assertEquals(3, catalog.version)
        assertEquals(listOf("qq", "163"), catalog.ids)
        val listed = catalog.list()
        assertEquals(2, listed.size)
        val qq = listed[0] as Map<*, *>
        assertEquals("qq", qq["id"])
        assertEquals(listOf("qq.com", "Foxmail.com"), qq["domains"])
        assertEquals(993, (qq["imap"] as Map<*, *>)["port"])
    }

    @Test
    fun getIsCaseInsensitiveAndTrimmed() {
        assertEquals("qq", (catalog.get("QQ") as Map<*, *>)["id"])
        assertEquals(" 163 ", (catalog.get(" 163") as Map<*, *>)["id"])
        assertNull(catalog.get(null))
        assertNull(catalog.get(""))
        assertNull(catalog.get("   "))
        assertNull(catalog.get("gmail"))
    }

    @Test
    fun resolveMatchesTheDomainOfAnAddressIgnoringCase() {
        assertEquals("qq", (catalog.resolve("Bob@QQ.COM") as Map<*, *>)["id"])
        assertEquals("qq", (catalog.resolve("x@foxmail.com") as Map<*, *>)["id"])
        assertEquals(" 163 ", (catalog.resolve("a@b@163.com") as Map<*, *>)["id"])
        assertNull(catalog.resolve("someone@example.test"))
        assertNull(catalog.resolve("no-at-sign"))
        assertNull(catalog.resolve("trailing@"))
        assertNull(catalog.resolve(null))
        assertNull(catalog.resolve("x@orphan.test"))
    }
}
