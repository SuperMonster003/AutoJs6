package org.autojs.autojs.ui.main.scripts

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle
import java.util.regex.Pattern

@PrismBundle(includeAll = true, grammarLocatorClassName = ".MediaInfoGrammarLocator")
class MediaInfoGrammarLocator : GrammarLocator {

    override fun grammar(prism4j: Prism4j, language: String) = when (language) {
        GRAMMAR_NAME -> create()
        else -> null
    }

    override fun languages() = mutableSetOf(GRAMMAR_NAME)

    companion object {

        const val GRAMMAR_NAME = "mediainfo"

        private fun create() = Prism4j.grammar(
            GRAMMAR_NAME,
            Prism4j.token("boolean", Prism4j.pattern(Pattern.compile("""# [A-Z][a-z]+\b"""))),
            // Prism4j.token("undefined", Prism4j.pattern(Pattern.compile("""[A-Za-z]+\d+(\.\d+)+"""))),
            // Prism4j.token("number", Prism4j.pattern(Pattern.compile("""\b\d+([:/]\d+)+(;\d+)?\b|\b0x[\dA-Fa-f]+\b|(?:\b\d+\.?\d*|\B\.\d+)(?:[Ee][+-]?\d+)?%?(?!(st|nd|rd|th|[\\/][A-Za-z]+)\b)"""))),
            // Prism4j.token("punctuation", Prism4j.pattern(Pattern.compile("""[{}\[\]);,]"""))),
            Prism4j.token("number", Prism4j.pattern(Pattern.compile("""(?<=:\s).+"""))),
        )
    }

}
