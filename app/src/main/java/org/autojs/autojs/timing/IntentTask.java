package org.autojs.autojs.timing;

import android.content.IntentFilter;

import org.autojs.autojs.storage.database.BaseModel;
import org.autojs.autojs.script.PersistedScriptLaunchContext;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;

public class IntentTask extends BaseModel {

    public static final String TABLE = "IntentTask";

    private String mScriptPath;

    private String mSourceKind;

    private String mProjectRoot;

    private String mAction;

    private String mCategory;

    private String mDataType;

    private boolean mLocal;

    public IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        if (mAction != null) {
            filter.addAction(mAction);
        }
        if (mCategory != null) {
            filter.addCategory(mCategory);
        }
        if (mDataType != null) {
            try {
                filter.addDataType(mDataType);
            } catch (IntentFilter.MalformedMimeTypeException e) {
                e.printStackTrace();
            }
        }
        return filter;
    }

    public String getScriptPath() {
        return mScriptPath;
    }

    public void setScriptPath(String scriptPath) {
        mScriptPath = scriptPath;
    }

    public void setScriptPathAndCaptureContext(String scriptPath) {
        mScriptPath = scriptPath;
        PersistedScriptLaunchContext context = ScriptLaunchSourceFactory.capturePersistedLaunchContext(scriptPath);
        mSourceKind = context.getSourceKind();
        mProjectRoot = context.getProjectRoot();
    }

    public String getSourceKind() {
        return mSourceKind;
    }

    public void setSourceKind(String sourceKind) {
        mSourceKind = sourceKind;
    }

    public String getProjectRoot() {
        return mProjectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        mProjectRoot = projectRoot;
    }

    public String getAction() {
        return mAction;
    }

    public void setAction(String action) {
        mAction = action;
    }

    public String getCategory() {
        return mCategory;
    }

    public void setCategory(String category) {
        mCategory = category;
    }

    public String getDataType() {
        return mDataType;
    }

    public void setDataType(String dataType) {
        mDataType = dataType;
    }

    public boolean isLocal() {
        return mLocal;
    }

    public void setLocal(boolean local) {
        mLocal = local;
    }
}
