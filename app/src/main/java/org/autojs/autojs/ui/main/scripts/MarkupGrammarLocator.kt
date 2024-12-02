package org.autojs.autojs.ui.main.scripts

import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import io.noties.prism4j.Prism4j.Grammar
import io.noties.prism4j.annotations.PrismBundle
import java.util.regex.Pattern

// FIXME by SuperMonster003 on Nov 20, 2024.
//  ! The syntax highlighting is still not perfect.
//  ! 语法高亮尚不够完善.
@PrismBundle(includeAll = true, grammarLocatorClassName = ".MarkupGrammarLocator")
class MarkupGrammarLocator : GrammarLocator {

    override fun grammar(prism4j: Prism4j, language: String) = when (language) {
        GRAMMAR_NAME -> create()
        else -> null
    }

    override fun languages() = mutableSetOf(GRAMMAR_NAME)

    companion object {

        const val GRAMMAR_NAME = "markup"

        private fun create(): Grammar {
            val entity = Prism4j.token("entity", Prism4j.pattern(Pattern.compile("&#?[\\da-z]{1,8};", Pattern.CASE_INSENSITIVE)))

            val punctuationPattern = Prism4j.pattern(Pattern.compile("[<>]"))
            val tagPattern = Prism4j.pattern(Pattern.compile("[a-zA-Z_:][\\w:.-]*"))
            val attrValuePattern = Prism4j.pattern(Pattern.compile("\"[^\"]*\"|'[^']*'"))
            val attrNamePattern = Prism4j.pattern(Pattern.compile("[a-zA-Z_:][\\w:.-]*"))
            val namespacePattern = Prism4j.pattern(Pattern.compile("[a-zA-Z_:][\\w:.-]*:"))

            val tokens: List<Prism4j.Token> = listOf(
                Prism4j.token("comment", Prism4j.pattern(Pattern.compile("<!--[\\s\\S]*?-->"))),
                Prism4j.token("prolog", Prism4j.pattern(Pattern.compile("<\\?[\\s\\S]+?\\?>"))),
                Prism4j.token("doctype", Prism4j.pattern(Pattern.compile("<!DOCTYPE[\\s\\S]+?>", Pattern.CASE_INSENSITIVE))),
                Prism4j.token("cdata", Prism4j.pattern(Pattern.compile("<!\\[CDATA\\[[\\s\\S]*?]]>", Pattern.CASE_INSENSITIVE))),
                Prism4j.token("punctuation", punctuationPattern),
                Prism4j.token("tag", tagPattern),
                Prism4j.token("attr-value", attrValuePattern),
                Prism4j.token("attr-name", attrNamePattern),
                Prism4j.token("namespace", namespacePattern),
                Prism4j.token(
                    "tag",
                    Prism4j.pattern(
                        Pattern.compile("</?[a-zA-Z_:][\\w:.-]*(\\s+[a-zA-Z_:][\\w:.-]*(\\s*=\\s*(?:\"[^\"]*\"|'[^']*'))?)*\\s*/?>"),
                        false,
                        true,
                        null,
                        Prism4j.grammar(
                            "inside",
                            Prism4j.token("punctuation", punctuationPattern),
                            Prism4j.token("tag", tagPattern),
                            Prism4j.token("attr-value", attrValuePattern),
                            Prism4j.token("attr-name", attrNamePattern),
                            Prism4j.token("namespace", namespacePattern),
                            entity,
                        ),
                    ),
                ),
            )

            return Prism4j.grammar(GRAMMAR_NAME, *tokens.toTypedArray(), entity)
        }
    }

}
