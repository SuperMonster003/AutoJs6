// Type definitions for AutoJs6 internal module object-observe
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.3.5
//
// Last modified: Oct 21, 2021
//
// See: https://www.cdnpkg.com/object-observe/file/object-observe-lite.js

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/object-observe-lite.min.js
 */

declare namespace Object {
    namespace Observe {
        /**
         * Function definition of a handler.
         */
        interface Handler {
            (changes: ChangeRecord[]): void;
        }

        /**
         * Type definition for a change.
         * Any other property can be added using the notify() or performChange() methods of the notifier.
         */
        interface ChangeRecord {
            type: string;
            object: object;
            name?: string;
            oldValue?: any;
            index?: number;
        }

        /**
         * Type definition for a notifier (what Object.getNotifier returns).
         */
        interface Notifier {
            notify();

            performChange();
        }
    }
}

declare interface ObjectConstructor {
    /**
     * @see http://arv.github.io/ecmascript-object-observe/#Object.observe
     * @throws {TypeError}
     */
    observe(object: object, handler: Object.Observe.Handler, acceptList: string[]): object;

    /**
     * @see http://arv.github.io/ecmascript-object-observe/#Object.unobserve
     * @throws {TypeError}
     */
    unobserve(object: object, handler: Object.Observe.Handler): object;

    /**
     * @see http://arv.github.io/ecmascript-object-observe/#GetNotifier
     * @throws {TypeError}
     */
    getNotifier(object: object): Object.Observe.Notifier;

    /**
     * @see http://arv.github.io/ecmascript-object-observe/#Object.deliverChangeRecords
     * @see http://arv.github.io/ecmascript-object-observe/#DeliverChangeRecords
     * @throws {TypeError}
     */
    deliverChangeRecords(handler: Object.Observe.Handler);
}