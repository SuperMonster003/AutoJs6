// Type definitions for AutoJs6 internal module i18n
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.6.3
//
// Last modified: Jul 3, 2022

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/__i18n__.js
 */

declare namespace Internal {

    namespace Banana {

        /**
         * @default {}
         */
        type ParamOptions = {
            /**
             * Final fallback locale
             * @default 'en'
             */
            finalFallback?: string;
            /**
             * Messages
             * @default undefined
             */
            messages?: object | undefined;
            /**
             * Whether the wiki style link syntax should be parsed or not
             * @default false
             */
            wikilinks?: boolean;
        };

        type Mixed = any;

        class BananaMessageStore {

            constructor();

            sourceMap: Map<string, object>;

            /**
             * @param messageSource
             * @param locale BCP 47 language tag.  In its most common form
             *   it can contain, in order: a language code, a script code, and a country
             *   or region code, all separated by hyphens. A very minimal validation
             *   is done.
             * @throws Error
             */
            load(messageSource: object, locale: string): void;

            getMessage(key: string, locale: string): object | null;

            /**
             * Check if the given locale is present in the message store or not
             */
            hasLocale(locale: string): boolean;

        }

        class Parser {

            constructor(locale: string, options?: {
                /**
                 * Whether the wiki style link syntax should be parsed or not
                 * @default false
                 */
                wikilinks?: boolean;
            });

            locale: string;
            wikilinks: boolean;
            emitter: Emitter;

            parse(message: string, params: any[]): string;

            simpleParse(message: string, params: any[]): string;

        }

        class Emitter {

            constructor(locale: string);

            locale: string;
            language: Language;

            /**
             * (We put this method definition here, and not in prototype, to make
             * sure it's not overwritten by any magic.) Walk entire node structure,
             * applying replacements and template functions when appropriate
             *
             * @param node abstract syntax tree (top node or sub-node)
             * @param replacements for $1, $2, ... $n
             * @returns single-string node or array of nodes suitable for
             *  jQuery appending.
             */
            emit(node: Mixed, replacements: any[]): Mixed;

            /**
             * Parsing has been applied depth-first we can assume that all nodes
             * here are single nodes Must return a single node to parents -- a
             * jQuery with synthetic span However, unwrap any other synthetic spans
             * in our children and pass them upwards
             *
             * @param nodes Mixed, some single nodes, some arrays of nodes.
             */
            concat(nodes: any[]): string;

            /**
             * Return escaped replacement of correct index, or string if
             * unavailable. Note that we expect the parsed parameter to be
             * zero-based. i.e. $1 should have become [ 0 ]. if the specified
             * parameter is not found return the same string (e.g. "$99" ->
             * parameter 98 -> not found -> return "$99" )
             *
             * @param nodes One element, integer, n >= 0
             * @param replacements for $1, $2, ... $n
             * @returns replacement
             */
            replace(nodes: any[], replacements: any[]): string;

            /**
             * Transform parsed structure into pluralization n.b. The first node may
             * be a non-integer (for instance, a string representing an Arabic
             * number). So convert it back with the current language's
             * convertNumber.
             *
             * @param nodes List [ {String|Number}, {String}, {String} ... ]
             * @returns selected pluralized form according to current
             *  language.
             */
            plural(nodes: any[]): string;

            /**
             * Transform parsed structure into gender Usage
             * {{gender:gender|masculine|feminine|neutral}}.
             * The first node(gender) must be one of 'male', 'female' or 'unknown'
             * Mediawiki allows this string as empty to indicate current logged-in user.
             * But this library cannot access such user contexts unless explicitly passed.
             * So we need to fall back to gender-neutral if it is empty.
             *
             * @param nodes List [ {String}, {String}, {String} , {String} ]
             * @returns selected gender form according to current language
             */
            gender(nodes: any[]): string;

            /**
             * Transform parsed structure into grammar conversion. Invoked by
             * putting {{grammar:form|word}} in a message
             *
             * @param nodes List [{Grammar case eg: genitive}, {String word}]
             * @returns selected grammatical form according to current language.
             */
            grammar(nodes: any[]): string;

            /**
             * Transform wiki-link
             */
            wikilink(nodes: string[]): string;

            /**
             * Transform parsed structure into external link.
             */
            extlink(nodes: string[]): string;

            /**
             * Wraps argument with unicode control characters for directionality safety
             *
             * This solves the problem where directionality-neutral characters at the edge of
             * the argument string get interpreted with the wrong directionality from the
             * enclosing context, giving renderings that look corrupted like "(Ben_(WMF".
             *
             * The wrapping is LRE...PDF or RLE...PDF, depending on the detected
             * directionality of the argument string, using the BIDI algorithm's own "First
             * strong directional codepoint" rule. Essentially, this works round the fact that
             * there is no embedding equivalent of U+2068 FSI (isolation with heuristic
             * direction inference). The latter is cleaner but still not widely supported.
             *
             * @param nodes The text nodes from which to take the first item.
             * @returns Wrapped String of content as needed.
             */
            bidi(nodes: string[]): string;

            /**
             * Takes a not formatted number (arab, no group separators and . as decimal separator)
             * and outputs it in the localized digit script and formatted with decimal
             * separator, according to the current language.
             *
             * @param nodes List of nodes
             * @returns Formatted number
             */
            formatnum(nodes: any[]): number | string;

            /**
             * Converts array of HTML element key value pairs to object
             *
             * @param nodes Array of consecutive key value pairs, with index 2 * n being a
             *  name and 2 * n + 1 the associated value
             * @returns Object mapping attribute name to attribute value
             */
            htmlattributes(nodes: any[]): object;

            /**
             * Handles an (already-validated) HTML element.
             *
             * @param nodes Nodes to process when creating element
             */
            htmlelement(nodes: any[]): string;

        }

        class Language {

            constructor(locale: string);

            locale: string;

            /**
             * Plural form transformations, needed for some languages.
             *
             * @param count Non-localized quantifier
             * @param forms List of plural forms
             * @returns Correct form for quantifier in this language
             */
            convertPlural(count: number, forms: any[]): string;

            /**
             * For the number, get the plural for index
             *
             * @param number
             * @param locale
             * @returns plural form index
             */
            getPluralForm(number: number, locale: string): number;

            /**
             * Converts a number using digitTransformTable.
             *
             * @param num Value to be converted
             * @param integer Convert the return value to an integer (default: false)
             * @returns The number converted into a String.
             */
            convertNumber(num: number, integer: boolean): string;

            /**
             * Grammatical transformations, needed for inflected languages.
             * Invoked by putting {{grammar:form|word}} in a message.
             * Override this method for languages that need special grammar rules
             * applied dynamically.
             */
            convertGrammar(word: string, form: string): string;

            /**
             * Provides an alternative text depending on specified gender. Usage
             * {{gender:[gender|user object]|masculine|feminine|neutral}}. If second
             * or third parameter are not specified, masculine is used.
             *
             * These details may be overridden per language.
             *
             * @param gender male, female, or anything else for neutral.
             * @param forms List of gender forms
             */
            gender(gender: string, forms: any[]): string;

            /**
             * Get the digit transform table for the given language
             * See http://cldr.unicode.org/translation/numbering-systems
             *
             * @param language
             * @returns List of digits in the past language or false
             * representation, or boolean false if there is no information.
             */
            digitTransformTable(language: string): any[] | boolean;

        }

    }

    class Banana {

        constructor(locale?: string, options?: Banana.ParamOptions);

        /**
         * Load localized messages for a locale
         * If locale not provided, the keys in messageSource will be used as locales.
         */
        load(messageSource: object, locale?: string);

        i18n: I18n;
        messageStore: Banana.BananaMessageStore;
        locale: string;
        parser: Banana.Parser;
        finalFallback: string;
        wikilinks: boolean;

        setLocale(locale: string): void;

        getFallbackLocales(): string[];

        getMessage(key: string): string;

        /**
         * Register a plugin for the library's message parser
         * Example:
         * <pre>
         *   banana.registerParserPlugin('foobar', nodes => {
         *     return nodes[0] === 'foo' ? nodes[1] : nodes[2]
         *   }
         * </pre>
         * Usage:
         * <pre>
         *   banana.i18n('{{foobar:foo|first message|second message}}') --> 'first message'
         *   banana.i18n('{{foobar:bar|first message|second message}}') --> 'second message'
         * </pre>
         * See emitter.js for built-in parser operations.
         * @param name - the name of the plugin
         * @param plugin - the plugin function. It receives nodes as argument -
         * a mixed array corresponding to the pipe-separated objects in the operation.
         */
        registerParserPlugin(name: string, plugin: Function): void;

    }

    interface I18n {

        (key: string, ...parameters: any[]): string;

        banana: Banana;

        setPath(relativePath: string): void;

        setLocale(locale: string): void;

        getFallbackLocales(): string[];

        getParser(): Banana.Parser;

        getPath(): string;

        getLocale(): string;

        getFinalFallback(): string;

        load(messageSource: object, locale: string): void;

        /**
         * @throws Error
         */
        load(messageSource: string, locale?: string): void;

        loadAll(): void;

    }

}