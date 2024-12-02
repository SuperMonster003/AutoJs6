package org.autojs.autojs.script;

import static org.autojs.autojs.engine.RhinoJavaScriptEngine.JS_BEAUTIFY_PATH;
import static org.autojs.autojs.engine.RhinoJavaScriptEngine.JS_BEAUTIFY_FILE;

import android.content.Context;
import android.view.View;

import org.autojs.autojs.engine.module.AssetAndUrlModuleSourceProvider;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.pio.UncheckedIOException;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Stardust on Apr 12, 2017.
 */
public class JsBeautifier {

    public interface Callback {

        void onSuccess(String beautifiedCode);

        void onException(Exception e);
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Context mContext;
    private Function mJsBeautifyFunction;
    private org.mozilla.javascript.Context mScriptContext;
    private Scriptable mScriptable;

    private View mView;

    public JsBeautifier(View view) {
        mContext = view.getContext();
        mView = view;
    }

    public void beautify(final String code, final Callback callback) {
        mExecutor.execute(() -> {
            try {
                prepareIfNeeded();
                enterContext();
                Object beautifiedCode = mJsBeautifyFunction.call(mScriptContext, mScriptable, mScriptable, new Object[]{code});
                mView.post(() -> callback.onSuccess(beautifiedCode.toString()));
            } catch (Exception e) {
                mView.post(() -> callback.onException(e));
            } finally {
                exitContext();
            }
        });
    }

    private void exitContext() {
        if (mScriptContext != null) {
            org.mozilla.javascript.Context.exit();
            mScriptContext = null;
        }
    }

    private void enterContext() {
        if (mScriptContext != null) {
            return;
        }
        mScriptContext = org.mozilla.javascript.Context.enter();
        mScriptContext.setLanguageVersion(org.mozilla.javascript.Context.VERSION_1_8);
        mScriptContext.setOptimizationLevel(-1);
        if (mScriptable == null) {
            ImporterTopLevel importerTopLevel = new ImporterTopLevel();
            importerTopLevel.initStandardObjects(mScriptContext, false);
            mScriptable = importerTopLevel;
        }
        AssetAndUrlModuleSourceProvider provider = new AssetAndUrlModuleSourceProvider(
                mContext,
                JS_BEAUTIFY_PATH,
                Collections.singletonList(new File(File.separator).toURI())
        );
        new RequireBuilder()
                .setModuleScriptProvider(new SoftCachingModuleScriptProvider(provider))
                .setSandboxed(false)
                .createRequire(mScriptContext, mScriptable)
                .install(mScriptable);
    }

    public void prepare() {
        mExecutor.execute(() -> {
            try {
                prepareIfNeeded();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void prepareIfNeeded() {
        if (mJsBeautifyFunction != null)
            return;
        compile();
    }

    private void compile() {
        try {
            enterContext();
            InputStream is = mContext.getAssets().open(JS_BEAUTIFY_FILE);
            mJsBeautifyFunction = (Function) mScriptContext.evaluateString(mScriptable, PFiles.read(is), "<js_beautify>", 1, null);
        } catch (IOException e) {
            exitContext();
            throw new UncheckedIOException(e);
        }
    }

    public void shutdown() {
        mExecutor.shutdownNow();
        mView = null;
    }

}
