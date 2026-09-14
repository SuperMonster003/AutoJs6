package org.autojs.autojs.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuotingTest {

    @Test
    fun safeWordsStayUnquoted() {
        assertEquals("is-odd", ShellQuoting.quote("is-odd"))
        assertEquals("@scope/pkg@1.2.3", ShellQuoting.quote("@scope/pkg@1.2.3"))
        assertEquals("a=b,c:d/e.f", ShellQuoting.quote("a=b,c:d/e.f"))
    }

    @Test
    fun unsafeWordsAreSingleQuoted() {
        assertEquals("''", ShellQuoting.quote(""))
        assertEquals("'a b'", ShellQuoting.quote("a b"))
        assertEquals("'\$HOME'", ShellQuoting.quote("\$HOME"))
        assertEquals("'a;rm -rf'", ShellQuoting.quote("a;rm -rf"))
        assertEquals("'it'\\''s'", ShellQuoting.quote("it's"))
        assertEquals("'中文 目录'", ShellQuoting.quote("中文 目录"))
    }

    @Test
    fun joinQuotesEachArgument() {
        assertEquals("npm install is-odd 'left pad'", ShellQuoting.join("npm", "install", "is-odd", "left pad"))
        assertEquals("", ShellQuoting.join(emptyList()))
    }

}
