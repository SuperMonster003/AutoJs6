package org.mozilla.javascript;

/**
 * Calls a script function as the top of a coroutine, like {@link Context#callFunctionWithContinuations}, but with a this
 * object (that method always passes undefined): the function may suspend through {@link Context#captureContinuation},
 * whose {@link ContinuationPending} then comes out of the call, and is resumed later through
 * {@link Context#resumeContinuation}. Used for listeners of an EventEmitter dispatched while no script code runs on the
 * thread (docs/augment/ROADMAP.md Q23); it lives in this package because the flag it sets is package-private.
 * <p>
 * zh-CN: 把脚本函数作为协程的顶层来调用, 与 {@link Context#callFunctionWithContinuations} 相同, 但可以指定 this (该方法总是传入 undefined):
 * 函数可以经 {@link Context#captureContinuation} 挂起, 此时 {@link ContinuationPending} 从本调用抛出, 之后经 {@link Context#resumeContinuation} 恢复.
 * 用于在线程上没有脚本代码运行时派发的 EventEmitter 监听器 (docs/augment/ROADMAP.md Q23); 因为要设置的标志是包私有的, 所以放在本包中.
 */
public final class ContinuationTopCall {

    private ContinuationTopCall() {
    }

    /**
     * Whether {@code function} can be called so: a function of interpreted script code (arrow functions included), while
     * {@code cx} has no pending top call. Continuations cannot be captured across Java frames, so other callables (bound
     * functions, Java methods) are not.
     * <p>
     * zh-CN: {@code function} 能否这样调用: 须为解释执行的脚本函数 (含箭头函数), 且 {@code cx} 上没有进行中的顶层调用.
     * 协程不能跨越 Java 帧捕获, 因此其他可调用对象 (bind 得到的函数, Java 方法) 不行.
     */
    public static boolean canCall(Context cx, Object function) {
        return function instanceof JSFunction
                && ((JSFunction) function).getDescriptor().getCode() instanceof InterpreterData
                && !ScriptRuntime.hasTopCall(cx);
    }

    public static Object call(Context cx, Callable function, VarScope scope, Object thisObj, Object[] args) throws ContinuationPending {
        if (!canCall(cx, function)) {
            throw new IllegalArgumentException("Not an interpreted script function, or a top call is pending: " + function);
        }
        // Consumed by the first interpreter frame, which then may be the outermost frame of a captured continuation.
        // zh-CN: 由第一个解释器帧取走, 该帧随后可以作为被捕获协程的最外层帧.
        cx.isContinuationsTopCall = true;
        return ScriptRuntime.doTopCall(function, cx, scope, thisObj, args, false);
    }

}
