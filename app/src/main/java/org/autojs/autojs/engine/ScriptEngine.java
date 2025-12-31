package org.autojs.autojs.engine;

import androidx.annotation.CallSuper;
import org.autojs.autojs.execution.ScriptExecution;
import org.autojs.autojs.execution.ScriptExecutionGlobalListener;
import org.autojs.autojs.script.ScriptSource;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Stardust on Apr 2, 2017.
 * <p>
 * A ScriptEngine is created by {@link ScriptEngineManager#createEngine(String, int)} ()}, and then can be
 * used to execute script with {@link ScriptEngine#execute(ScriptSource)} in the <strong>same</strong> thread.
 * When the execution finish successfully, the engine should be destroy in the thread that created it.
 * <br>
 * If you want to stop the engine in other threads, you should call {@link ScriptEngine#forceStop()}.
 * </p>
 */
public interface ScriptEngine<S extends ScriptSource> {

    String TAG_ENV_PATH = "env_path";
    String TAG_SOURCE = "source";
    String TAG_WORKING_DIRECTORY = "execute_path";

    void put(String name, Object value);

    Object execute(S scriptSource);

    void forceStop();

    void destroy();

    boolean isDestroyed();

    void setTag(String key, Object value);

    Object getTag(String key);

    String cwd();

    void uncaughtException(Throwable throwable);

    Throwable getUncaughtException();

    int getId();

    void setId(int id);

    /**
     * @hide
     */
    void setOnDestroyListener(OnDestroyListener listener);

    /**
     * @hide
     */
    void init();

    default long getStartTime() {
        try {
            Field field = ScriptExecutionGlobalListener.class.getDeclaredField("ENGINE_TAG_START_TIME");
            field.setAccessible(true);
            String tag = String.valueOf(field.get(null));
            Object o = getTag(tag);
            if (o instanceof Long) {
                return (long) o;
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            /* Ignored. */
        }
        return 0;
    }

    interface OnDestroyListener {
        void onDestroy(ScriptEngine<? extends ScriptSource> engine);
    }

    abstract class AbstractScriptEngine<S extends ScriptSource> implements ScriptEngine<S> {

        private final Map<String, Object> mTags = new ConcurrentHashMap<>();
        private final AtomicInteger mId = new AtomicInteger(ScriptExecution.NO_ID);
        private OnDestroyListener mOnDestroyListener;
        private volatile boolean mDestroyed = false;
        private Throwable mUncaughtException;

        @Override
        public void setTag(String key, Object value) {
            if (value == null) {
                mTags.remove(key);
            } else {
                mTags.put(key, value);
            }
        }

        @Override
        public Object getTag(String key) {
            return mTags.get(key);
        }

        @Override
        public boolean isDestroyed() {
            return mDestroyed;
        }

        @CallSuper
        @Override
        public void destroy() {
            if (mOnDestroyListener != null) {
                mOnDestroyListener.onDestroy(this);
            }
            mDestroyed = true;
        }

        public String cwd() {
            return (String) getTag(TAG_WORKING_DIRECTORY);
        }

        public void setOnDestroyListener(OnDestroyListener onDestroyListener) {
            if (mOnDestroyListener != null) {
                throw new DestroyListenerSetMoreThanOneException(AbstractScriptEngine.class.getSimpleName() + ".setOnDestroyListener");
            }
            mOnDestroyListener = onDestroyListener;
        }

        @Override
        public void uncaughtException(Throwable throwable) {
            mUncaughtException = throwable;
            forceStop();
        }

        @Override
        public Throwable getUncaughtException() {
            return mUncaughtException;
        }

        @Override
        public int getId() {
            return mId.get();
        }

        @Override
        public void setId(int id) {
            mId.compareAndSet(ScriptExecution.NO_ID, id);
        }

        private static class DestroyListenerSetMoreThanOneException extends SecurityException {
            public DestroyListenerSetMoreThanOneException(String s) {
                super(s);
            }

        }

    }

}
