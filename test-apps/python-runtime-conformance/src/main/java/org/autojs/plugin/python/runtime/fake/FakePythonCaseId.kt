package org.autojs.plugin.python.runtime.fake

/** Stable, test-only behavior selectors. They are not part of the runtime protocol. */
enum class FakePythonCaseId(val stableId: String) {
    NORMAL("normal"),
    STRUCTURED_EXCEPTION("structured-exception"),
    STALL("stall"),
    LATE_CALLBACK("late-callback"),
    OUTPUT_BEFORE_START("output-before-start"),
    OUTPUT_WITHOUT_CREDIT("output-without-credit"),
    OVERSIZED_CHUNK("oversized-chunk"),
    REORDERED_CHUNKS("reordered-chunks"),
    DUPLICATE_TERMINAL("duplicate-terminal"),
    ;

    companion object {
        const val ARGUMENT_PREFIX = "--fake-python-case="

        fun fromArguments(arguments: Collection<String>): FakePythonCaseId {
            val ids = arguments.filter { it.startsWith(ARGUMENT_PREFIX) }
            require(ids.size <= 1) { "Fake Python case ID may be declared at most once" }
            val id = ids.singleOrNull()?.removePrefix(ARGUMENT_PREFIX) ?: return NORMAL
            return entries.firstOrNull { it.stableId == id }
                ?: throw IllegalArgumentException("Unknown fake Python case ID")
        }
    }
}
