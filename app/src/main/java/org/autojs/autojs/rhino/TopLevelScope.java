package org.autojs.autojs.rhino;

import org.mozilla.javascript.ImporterTopLevel;

import java.lang.ref.WeakReference;

public class TopLevelScope extends ImporterTopLevel {

    // Weak: script objects can outlive the script in a queue or a listener, and must not keep its runtime reachable.
    // zh-CN: 弱引用: 脚本对象可能在队列或监听器中比脚本活得久, 不能让其运行时保持可达.
    private volatile WeakReference<ScriptEventLoop> mEventLoop;

    public TopLevelScope() {
        getGlobalThis().setParentScope(this);
    }

    public ScriptEventLoop getEventLoop() {
        WeakReference<ScriptEventLoop> reference = mEventLoop;
        return reference == null ? null : reference.get();
    }

    public void setEventLoop(ScriptEventLoop eventLoop) {
        mEventLoop = new WeakReference<>(eventLoop);
    }
}
