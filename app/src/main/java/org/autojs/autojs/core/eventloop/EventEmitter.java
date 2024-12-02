package org.autojs.autojs.core.eventloop;

import org.autojs.autojs.core.looper.Timer;
import org.autojs.autojs.runtime.ScriptBridges;
import org.autojs.autojs.runtime.exception.ScriptException;
import org.autojs.autojs6.R;
import org.mozilla.javascript.BaseFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.autojs.autojs.util.StringUtils.str;

public class EventEmitter {

    public static class ListenerWrapper {
        public boolean isOnce;
        public BaseFunction listener;

        public ListenerWrapper(BaseFunction listener, boolean isOnce) {
            this.listener = listener;
            this.isOnce = isOnce;
        }
    }

    public class Listeners {
        private final CopyOnWriteArrayList<ListenerWrapper> listeners = new CopyOnWriteArrayList<>();

        private void ensureListenersNotAtLimit() {
            if (mMaxListeners != 0 && listeners.size() >= mMaxListeners) {
                throw new ScriptException(new TooManyListenersException(str(R.string.error_max_listeners_exceeded, mMaxListeners)));
            }
        }

        public void add(BaseFunction listener, boolean isOnce, Object[] stickyArgs) {
            if (stickyArgs != null) {
                if (mTimer != null) {
                    mTimer.setImmediate(listener, stickyArgs);
                } else {
                    mBridges.call(listener, EventEmitter.this, stickyArgs);
                }
                if (isOnce) {
                    return;
                }
            }
            ensureListenersNotAtLimit();
            listeners.add(new ListenerWrapper(listener, isOnce));
        }

        public int count() {
            return listeners.size();
        }

        public void emit(Object[] args) {
            Iterator<ListenerWrapper> iterator = listeners.iterator();
            int index = 0;
            while (iterator.hasNext()) {
                ListenerWrapper listenerWrapper = iterator.next();
                BaseFunction listener = listenerWrapper.listener;
                if (listener instanceof IListener) {
                    ((IListener) listener).onEvent(args);
                } else {
                    if (mTimer != null) {
                        mTimer.setImmediate(listenerWrapper.listener, args);
                    } else {
                        mBridges.call(listenerWrapper.listener, EventEmitter.this, args);
                    }
                }
                if (listenerWrapper.isOnce) {
                    listeners.remove(index);
                }
                ++index;
            }
        }

        public boolean empty() {
            return listeners.isEmpty();
        }

        public void prepend(BaseFunction listener, boolean isOnce) {
            ensureListenersNotAtLimit();
            listeners.add(0, new ListenerWrapper(listener, isOnce));
        }

        public boolean remove(BaseFunction listener) {
            for (ListenerWrapper wrapper : listeners) {
                if (wrapper.listener == listener) {
                    listeners.remove(wrapper);
                    return true;
                }
            }
            return false;
        }

        public Object[] toArray() {
            ArrayList<Object> listenerList = new ArrayList<>(listeners.size());
            for (ListenerWrapper wrapper : listeners) {
                listenerList.add(wrapper.listener);
            }
            return listenerList.toArray(new Object[0]);
        }
    }

    private final Map<String, Listeners> mListenersMap = new HashMap<>();
    private final Map<String, Object[]> mStickyEvents = new HashMap<>();
    public static int defaultMaxListeners = 10;
    private int mMaxListeners = defaultMaxListeners;
    protected ScriptBridges mBridges;
    private final Timer mTimer;

    public EventEmitter(ScriptBridges bridges) {
        this(bridges, null);
    }

    public EventEmitter(ScriptBridges bridges, Timer timer) {
        mBridges = bridges;
        mTimer = timer;
    }

    private Listeners getListeners(String eventName) {
        Listeners listeners = mListenersMap.get(eventName);
        if (listeners == null) {
            listeners = new Listeners();
            mListenersMap.put(eventName, listeners);
        }
        return listeners;
    }

    public EventEmitter addListener(String eventName, BaseFunction listener) {
        return on(eventName, listener);
    }

    public boolean emit(String eventName, Object... args) {
        Listeners listeners = mListenersMap.get(eventName);
        if (listeners != null && !listeners.empty()) {
            listeners.emit(args);
            return true;
        }
        return false;
    }

    public boolean emitSticky(String eventName, Object... args) {
        boolean result = emit(eventName, args);
        mStickyEvents.put(eventName, args);
        return result;
    }

    public String[] eventNames() {
        return mListenersMap.keySet().toArray(new String[0]);
    }

    public int getMaxListeners() {
        return mMaxListeners;
    }

    public Timer getTimer() {
        return mTimer;
    }

    public int listenerCount(String eventName) {
        Listeners listeners = mListenersMap.get(eventName);
        return listeners == null ? 0 : listeners.count();
    }

    public Object[] listeners(String eventName) {
        return getListeners(eventName).toArray();
    }

    public EventEmitter on(String eventName, BaseFunction listener) {
        getListeners(eventName).add(listener, false, mStickyEvents.get(eventName));
        emit("newListener", eventName, listener);
        return this;
    }

    public EventEmitter once(String eventName, BaseFunction listener) {
        getListeners(eventName).add(listener, true, mStickyEvents.get(eventName));
        emit("newListener", eventName, listener);
        return this;
    }

    public EventEmitter prependListener(String eventName, BaseFunction listener) {
        getListeners(eventName).prepend(listener, false);
        emit("newListener", eventName, listener);
        return this;
    }

    public EventEmitter prependOnceListener(String eventName, BaseFunction listener) {
        getListeners(eventName).prepend(listener, true);
        emit("newListener", eventName, listener);
        return this;
    }

    public EventEmitter removeAllListeners() {
        for (Map.Entry<String, Listeners> entry : mListenersMap.entrySet()) {
            String eventName = entry.getKey();
            Listeners listeners = entry.getValue();
            for (ListenerWrapper wrapper : listeners.listeners) {
                emit("removeListener", eventName, wrapper.listener);
            }
        }
        mListenersMap.clear();
        return this;
    }

    public EventEmitter removeAllListeners(String eventName) {
        Listeners listeners = mListenersMap.remove(eventName);
        if (listeners != null) {
            for (ListenerWrapper wrapper : listeners.listeners) {
                emit("removeListener", eventName, wrapper.listener);
            }
        }
        return this;
    }

    public EventEmitter removeListener(String eventName, BaseFunction listener) {
        Listeners listeners = mListenersMap.get(eventName);
        if (listeners != null && listeners.remove(listener)) {
            emit("removeListener", eventName, listener);
        }
        return this;
    }

    public EventEmitter setMaxListeners(int maxListeners) {
        mMaxListeners = maxListeners;
        return this;
    }

    public static int defaultMaxListeners() {
        return defaultMaxListeners;
    }

    public interface IListener {
        void onEvent(Object[] args);
    }

}