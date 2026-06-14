// Type definitions for AutoJs6 internal module result-adapter
//
// Definitions by: SuperMonster003 <https://github.com/SuperMonster003>
// TypeScript Version: 4.5.4
//
// Last modified: May 2, 2022

/// <reference path="../index.d.ts" />

/**
 * @Source %AutoJs6Assets%/modules/result-adapter.js
 */

import Impl = ResultAdapter.Impl;
import VolatileDispose = org.autojs.autojs.concurrent.VolatileDispose;
import ScriptPromiseAdapter = org.autojs.autojs.runtime.api.ScriptPromiseAdapter;

declare namespace Internal {
    class ResultAdapter implements Impl {
        constructor();

        static promise(promiseAdapter: ScriptPromiseAdapter): Promise<unknown>;
        static wait(promise: Promise<unknown> | ScriptPromiseAdapter): any;

        cont?: Internal.Continuation.Creator;
        disposable?: VolatileDispose<any>;
        impl: Impl;

        callback: (result, error) => any;
        setResult(result): void;
        setError(error): void;
        get(): any;
    }
}

declare namespace ResultAdapter {
    interface Impl {
        setResult(result): void;
        setError(error): void;
        get(): any;
    }
}
