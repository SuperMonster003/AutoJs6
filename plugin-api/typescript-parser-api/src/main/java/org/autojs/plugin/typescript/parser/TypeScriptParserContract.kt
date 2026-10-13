package org.autojs.plugin.typescript.parser

/**
 * TypeScript syntax-tree parsing contract v1.
 *
 * A Binder service separate from the frozen compiler contract (`org.autojs.plugin.typescript.api`):
 * a provider (the TypeScript Engine plugin) parses one source file with the TypeScript compiler's
 * `createSourceFile` and returns its syntax tree with source positions and the syntactic diagnostics.
 * Nothing is type-checked, emitted or executed. The service is bound with [ACTION] and protected by the
 * `org.autojs.permission.PLUGIN` permission; providers advertise it in their PluginInfo capabilities
 * ([CAPABILITY_CONTRACT_VERSION], [CAPABILITY_SERVICE_ACTION], [CAPABILITY_TREE_SCHEMA_REVISION]).
 *
 * The request Bundle holds the source text; the tree is written as UTF-8 JSON to the caller's file
 * descriptor, so its size is not bound by the Binder transaction limit. The JSON layout (schema
 * revision [TREE_SCHEMA_REVISION]) is described in `docs/dev/typescript-parser-plugin-protocol-v1.md`.
 */
object TypeScriptParserContract {

    const val VERSION = 1
    const val ACTION = "org.autojs.plugin.typescript.PARSER"
    const val HASH_INPUT =
        "autojs6.typescript.parser.v1|" +
            "getContractVersion()->int|getContractHash()->String|" +
            "getParserInfo()->Bundle|parse(Bundle,ParcelFileDescriptor)->Bundle|" +
            "bundle:primitive-only|fd:tree-output|tree:json-utf8"
    const val HASH = "6c8a1a8089aab67d118211ae9edf78f0b341fb4f08f57e21bd7df8d532b1d5d1"
    const val TREE_SCHEMA_REVISION = 1
    const val TREE_FORMAT = "json-utf8"

    const val CAPABILITY_CONTRACT_VERSION = "typescriptParserContractVersion"
    const val CAPABILITY_SERVICE_ACTION = "typescriptParserServiceAction"
    const val CAPABILITY_TREE_SCHEMA_REVISION = "typescriptParserTreeSchemaRevision"

    const val KEY_CONTRACT_VERSION = "contractVersion"
    const val KEY_CONTRACT_HASH = "contractHash"
    const val KEY_TREE_SCHEMA_REVISION = "treeSchemaRevision"
    const val KEY_REQUEST_ID = "requestId"
    const val KEY_FILE_NAME = "fileName"
    const val KEY_SOURCE_TEXT = "sourceText"
    const val KEY_TIMEOUT_MS = "timeoutMs"

    const val KEY_OUTCOME = "outcome"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_ERROR_MESSAGE = "errorMessage"
    const val KEY_TREE_BYTE_LENGTH = "treeByteLength"
    const val KEY_TREE_SHA256 = "treeSha256"
    const val KEY_NODE_COUNT = "nodeCount"
    const val KEY_DIAGNOSTIC_COUNT = "diagnosticCount"
    const val KEY_ELAPSED_MS = "elapsedMs"

    const val KEY_STATUS = "status"
    const val KEY_COMPILER_VERSION = "compilerVersion"
    const val KEY_SUPPORTED_EXTENSIONS = "supportedExtensions"
    const val KEY_MAX_SOURCE_CHARS = "maxSourceChars"
    const val KEY_MAX_CONCURRENT_PARSES = "maxConcurrentParses"

    const val OUTCOME_PARSED = "parsed"
    const val OUTCOME_FAILED = "failed"
    const val OUTCOME_TIMED_OUT = "timed_out"

    const val STATUS_READY = "ready"
    const val STATUS_STARTING = "starting"
    const val STATUS_UNAVAILABLE = "unavailable"

    const val ERROR_INVALID_REQUEST = "ERR_AUTOJS6_TYPESCRIPT_PARSER_INVALID_REQUEST"
    const val ERROR_CONTRACT_MISMATCH = "ERR_AUTOJS6_TYPESCRIPT_PARSER_CONTRACT_MISMATCH"
    const val ERROR_BUSY = "ERR_AUTOJS6_TYPESCRIPT_PARSER_BUSY"
    const val ERROR_TIMEOUT = "ERR_AUTOJS6_TYPESCRIPT_PARSER_TIMEOUT"
    const val ERROR_UNAVAILABLE = "ERR_AUTOJS6_TYPESCRIPT_PARSER_UNAVAILABLE"
    const val ERROR_TREE_LIMIT = "ERR_AUTOJS6_TYPESCRIPT_PARSER_TREE_LIMIT"
    const val ERROR_OUTPUT_TRANSPORT = "ERR_AUTOJS6_TYPESCRIPT_PARSER_OUTPUT_TRANSPORT"

    /** Covers a cold start of the provider's runtime; a warm parse of a few hundred lines takes milliseconds. */
    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val MIN_TIMEOUT_MS = 1_000L
    const val MAX_TIMEOUT_MS = 60_000L
    /** UTF-16 chars; with the request keys this keeps a parse request well below the 1 MiB Binder buffer. */
    const val MAX_SOURCE_CHARS = 256 * 1024
    const val MAX_FILE_NAME_CHARS = 255
    const val MAX_REQUEST_ID_CHARS = 128
    const val MAX_ERROR_MESSAGE_CHARS = 4_096
    const val MAX_TREE_NODES = 250_000
    const val MAX_TREE_BYTES = 32L * 1024L * 1024L
    const val MAX_DIAGNOSTICS = 1_000
    /** Parses never take a compilation slot; providers run at most this many at once and answer [ERROR_BUSY] beyond it. */
    const val MAX_CONCURRENT_PARSES = 2

    val SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts", ".tsx", ".mts", ".cts", ".js", ".jsx")

    val TERMINAL_OUTCOMES
        get() = arrayOf(OUTCOME_PARSED, OUTCOME_FAILED, OUTCOME_TIMED_OUT)

    /** Top-level members of the tree JSON (schema revision 1). */
    object Tree {
        const val SCHEMA_REVISION = "schemaRevision"
        const val COMPILER_VERSION = "compilerVersion"
        const val FILE_NAME = "fileName"
        const val LANGUAGE_VARIANT = "languageVariant"
        const val NODE_COUNT = "nodeCount"
        const val DIAGNOSTICS = "diagnostics"
        const val ROOT = "root"

        const val VARIANT_STANDARD = "standard"
        const val VARIANT_JSX = "jsx"
    }

    /**
     * Members of a node object. Besides these, a node holds its child nodes under the property names of
     * the TypeScript compiler API (`initializer`, `tagName`, `attributes`, `properties`, ...): a node or an
     * array of nodes.
     */
    object Node {
        const val KIND = "kind"
        const val START = "start"
        const val END = "end"
        const val TEXT = "text"
        const val DECLARATION_KIND = "declarationKind"
        const val OPERATOR = "operator"
        const val TRIVIA_ONLY = "triviaOnly"
    }

    /** Members of a diagnostic object; positions are UTF-16 offsets like node positions. */
    object Diagnostic {
        const val START = "start"
        const val LENGTH = "length"
        const val CODE = "code"
        const val CATEGORY = "category"
        const val MESSAGE = "message"

        const val CATEGORY_ERROR = "error"
        const val CATEGORY_WARNING = "warning"
        const val CATEGORY_SUGGESTION = "suggestion"
        const val CATEGORY_MESSAGE = "message"
    }
}
