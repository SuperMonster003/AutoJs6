package org.autojs.autojs.script

/** An in-memory TypeScript entry that must be compiled before execution. */
internal class TypeScriptStringSource private constructor(
    private val canonicalSource: CanonicalSource,
    script: String,
) : StringScriptSource(canonicalSource.runtimeName, script) {

    constructor(sourceName: String, script: String) : this(
        CanonicalSource.from(sourceName),
        script,
    )

    val compilationSourceName: String
        get() = canonicalSource.sourceName

    val compilationSourceIdentity: String
        get() = "autojs://string/$compilationSourceName"

    override val engineName: String
        get() = if (JavaScriptSource.isNodeJsFileName(compilationSourceName)) {
            ENGINE_NODEJS
        } else {
            ENGINE
        }

    override fun toString(): String {
        originPath?.let { return it }
        originUri?.let { return it }
        return prefix.orEmpty() + compilationSourceName
    }

    private data class CanonicalSource(
        val sourceName: String,
        val runtimeName: String,
    ) {

        companion object {

            private val EXECUTABLE_EXTENSIONS = listOf(".ts", ".tsx", ".mts", ".cts")

            fun from(sourceName: String): CanonicalSource {
                require(
                    sourceName.isNotBlank() &&
                        sourceName != "." &&
                        sourceName != ".." &&
                        sourceName.none { character ->
                            character == '/' ||
                                character == '\\' ||
                                character.code in 0x00..0x1f ||
                                character.code in 0x7f..0x9f
                        },
                ) { "The TypeScript source name is not a canonical single-file name." }
                require(EXECUTABLE_EXTENSIONS.any(sourceName::endsWith)) {
                    "The TypeScript source name must end in .ts, .tsx, .mts, or .cts."
                }
                require(!JavaScriptSource.isTypeScriptDeclarationFileName(sourceName)) {
                    "TypeScript declaration sources are not executable."
                }
                return CanonicalSource(
                    sourceName = sourceName,
                    runtimeName = sourceName.substringBeforeLast('.'),
                )
            }

        }

    }

}
