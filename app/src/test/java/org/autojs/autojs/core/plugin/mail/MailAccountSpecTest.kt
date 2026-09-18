package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailAccountSpecTest {

    @Test
    fun inlineAccountsNeedAJsonObjectAndAtMostOneSecret() {
        assertNull(MailAccountSpec.inline("""{"address":"a@b.c"}""", password = "pw").validate())
        assertNull(MailAccountSpec.inline("""{"address":"a@b.c"}""", accessToken = "tok").validate())
        assertNull(MailAccountSpec.inline("""{"address":"a@b.c"}""").validate())
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, MailAccountSpec.inline("").validate()!!.code)
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, MailAccountSpec.inline("[]").validate()!!.code)
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, MailAccountSpec.inline("{").validate()!!.code)
        val both = MailAccountSpec.inline("{}", password = "pw", accessToken = "tok").validate()!!
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, both.code)
        assertFalse(both.retryable)
    }

    @Test
    fun aliasAccountsCarryNothingElse() {
        val alias = MailAccountSpec.alias("work")
        assertTrue(alias.usesAlias)
        assertNull(alias.validate())
        assertNull(alias.accountJson)
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, MailAccountSpec.alias(" ").validate()!!.code)
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, alias.withAccessToken("tok").validate()!!.code)
    }

    @Test
    fun tokenReplacementKeepsTheRest() {
        val spec = MailAccountSpec.inline("""{"x":1}""", accessToken = "old")
        val refreshed = spec.withAccessToken("new")
        assertEquals("new", refreshed.accessToken)
        assertEquals("""{"x":1}""", refreshed.accountJson)
        assertNull(refreshed.password)
        assertEquals("old", spec.accessToken)
    }

    @Test
    fun toStringNeverPrintsSecrets() {
        val text = MailAccountSpec.inline("""{"address":"a@b.c"}""", password = "hunter2", accessToken = null).toString()
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("password=***"))
        assertTrue(text.contains("accessToken=null"))
        val token = MailAccountSpec.inline("{}", accessToken = "ya29.secret").toString()
        assertFalse(token.contains("ya29"))
        assertTrue(token.contains("accessToken=***"))
        assertFalse(MailPluginException(MailError(MailErrorCodes.AUTH_FAILED, "bad credentials", false)).message!!.contains("hunter2"))
    }
}
