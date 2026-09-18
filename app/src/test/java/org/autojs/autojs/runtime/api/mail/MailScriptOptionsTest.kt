package org.autojs.autojs.runtime.api.mail

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.mail.MailPluginException
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MailScriptOptionsTest {

    @Test
    fun aliasFormKeepsOnlyTheAlias() {
        val options = MailScriptOptions.alias("  work ")
        assertEquals("work", options.alias)
        assertEquals("work", options.spec.alias)
        assertNull(options.spec.accountJson)
        assertFalse(options.debug)
        assertEquals("""{"alias":"work"}""", options.snapshot.toString())
        assertEquals("alias=work", options.describe())
        assertNull(options.spec.validate())
        assertTrue(invalidMessage { MailScriptOptions.alias(" ") }.contains("blank"))
    }

    @Test
    fun inlinePasswordAccountSplitsSecretsFromTheSnapshot() {
        val options = inline("""{"provider":"qq","address":"alice@qq.com","password":"s3cret","debug":true,"name":"Alice"}""")
        assertNull(options.alias)
        assertTrue(options.debug)
        assertEquals("s3cret", options.spec.password)
        assertNull(options.spec.accessToken)
        assertFalse(options.snapshot.has("password"))
        assertFalse(options.spec.accountJson!!.contains("s3cret"))
        assertEquals("alice@qq.com", options.snapshot.get("address").asString)
        assertEquals("Alice", options.snapshot.get("name").asString)
        assertTrue(options.snapshot.get("debug").asBoolean)
        assertFalse("no auth is implied for a password account", options.snapshot.has("auth"))
        assertEquals(options.snapshot, JsonParser.parseString(options.spec.accountJson!!))
        assertEquals("provider=qq, address=alice@qq.com", options.describe())
        assertNull(options.spec.validate())
    }

    @Test
    fun accessTokenAccountsImplyXoauth2() {
        val token = inline("""{"address":"a@gmail.com","accessToken":"ya29.x"}""")
        assertEquals("ya29.x", token.spec.accessToken)
        assertNull(token.spec.password)
        assertEquals(MailContract.AUTH_XOAUTH2, token.snapshot.get("auth").asString)
        assertFalse(token.snapshot.has("accessToken"))

        val provider = inline("""{"address":"a@gmail.com","tokenProvider":"ignored-by-shape"}""", hasTokenProvider = true)
        assertNull(provider.spec.accessToken)
        assertEquals(MailContract.AUTH_XOAUTH2, provider.snapshot.get("auth").asString)
        assertFalse(provider.snapshot.has("tokenProvider"))

        val explicit = inline("""{"address":"a@gmail.com","auth":"xoauth2","accessToken":"t"}""")
        assertEquals(MailContract.AUTH_XOAUTH2, explicit.snapshot.get("auth").asString)
    }

    @Test
    fun secretsAndAuthMustBeConsistent() {
        assertTrue(invalidMessage { inline("""{"address":"a@b.c"}""") }.contains("required"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","accessToken":"t"}""") }.contains("exclusive"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":""}""") }.contains("empty"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","accessToken":""}""") }.contains("empty"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p"}""", hasTokenProvider = true) }.contains("tokenProvider"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","auth":"password","accessToken":"t"}""") }.contains("auth: password"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","auth":"xoauth2","password":"p"}""") }.contains("auth: xoauth2"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","auth":"plain","password":"p"}""") }.contains("password, xoauth2"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":42}""") }.contains("'password' must be a string"))
    }

    @Test
    fun addressAndUnknownKeysAreRefusedByName() {
        assertTrue(invalidMessage { inline("""{"password":"p"}""") }.contains("'address' is required"))
        assertTrue(invalidMessage { inline("""{"address":"  ","password":"p"}""") }.contains("blank"))
        assertTrue(invalidMessage { inline("""{"address":"alice","password":"p"}""") }.contains("mail address"))
        assertTrue(invalidMessage { inline("""{"address":"@b.c","password":"p"}""") }.contains("mail address"))
        assertTrue(invalidMessage { inline("""{"address":"a@","password":"p"}""") }.contains("mail address"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","bogus":1}""") }.contains("'bogus'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","imap":{"host":"h","proxy":1}}""") }.contains("'imap.proxy'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","timeout":{"idle":1}}""") }.contains("'timeout.idle'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","tls":{"verify":true}}""") }.contains("'tls.verify'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","clientId":{"vendor":"x"}}""") }.contains("'clientId.vendor'"))
    }

    @Test
    fun endpointsTimeoutsAndFlagsAreShapeChecked() {
        val ok = inline(
            """{"address":"a@b.c","password":"p","receive":"pop3",""" +
                """"imap":{"host":"imap.b.c","port":993,"tls":"ssl"},"pop3":{"host":"pop.b.c","port":995},"smtp":{"tls":"starttls"},""" +
                """"timeout":{"connect":5000,"read":0,"write":null},"tls":{"trustAll":false},"clientId":{"name":"AutoJs6","version":"6.8.0"}}""",
        )
        assertEquals("pop3", ok.snapshot.get("receive").asString)
        assertEquals(993, ok.snapshot.getAsJsonObject("imap").get("port").asInt)
        assertEquals("address=a@b.c, receive=pop3", ok.describe())

        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","receive":"smtp"}""") }.contains("imap, pop3"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","imap":"imap.b.c"}""") }.contains("'imap' must be an object"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","imap":{"host":" "}}""") }.contains("'imap.host'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","imap":{"host":7}}""") }.contains("'imap.host' must be a string"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","imap":{"port":0}}""") }.contains("1..65535"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","smtp":{"port":70000}}""") }.contains("1..65535"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","pop3":{"port":"995"}}""") }.contains("'pop3.port' must be a number"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","smtp":{"tls":"tls"}}""") }.contains("'smtp.tls' must be one of"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","timeout":{"connect":-1}}""") }.contains("'timeout.connect'"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","timeout":{"read":1.5}}""") }.contains("integer"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","timeout":[]}""") }.contains("'timeout' must be an object"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","tls":{"trustAll":"yes"}}""") }.contains("'tls.trustAll' must be a boolean"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","clientId":{"name":1}}""") }.contains("'clientId.name' must be a string"))
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":"p","debug":"true"}""") }.contains("'debug' must be a boolean"))
    }

    @Test
    fun nullValuesCountAsAbsent() {
        val options = inline("""{"address":"a@b.c","password":"p","provider":null,"imap":null,"timeout":{"connect":null},"debug":null}""")
        assertFalse(options.debug)
        assertNull(options.spec.validate())
        assertTrue(invalidMessage { inline("""{"address":"a@b.c","password":null}""") }.contains("required"))
    }

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    private fun inline(text: String, hasTokenProvider: Boolean = false): MailScriptOptions =
        MailScriptOptions.inline(json(text), hasTokenProvider)

    private fun invalidMessage(block: () -> Any?): String {
        try {
            block()
        } catch (e: MailPluginException) {
            assertEquals(e.message, MailErrorCodes.INVALID_ARGUMENT, e.code)
            assertFalse(e.retryable)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
