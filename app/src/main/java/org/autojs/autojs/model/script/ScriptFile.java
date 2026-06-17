package org.autojs.autojs.model.script;

import org.autojs.autojs.pio.PFile;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.ScriptLaunchSourceFactory;
import org.autojs.autojs.script.ScriptSource;

import java.io.File;

import static org.autojs.autojs.util.FileUtils.TYPE.AUTO;

/**
 * Created by Stardust on Jan 23, 2017.
 */
public class ScriptFile extends PFile {

    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_AUTO = 1;
    public static final int TYPE_JAVASCRIPT = 2;

    private int mType = -1;

    public ScriptFile(String path) {
        super(path);
    }

    public ScriptFile(String parent, String name) {
        super(parent, name);
    }

    public ScriptFile(File parent, String child) {
        super(parent, child);
    }

    public ScriptFile(File file) {
        super(file.getPath());
    }

    public int getType() {
        if (mType == -1) {
            mType = JavaScriptSource.isJavaScriptFileName(getName()) ? TYPE_JAVASCRIPT :
                    getName().endsWith(AUTO.extensionWithDot) ? TYPE_AUTO : TYPE_UNKNOWN;
        }
        return mType;
    }

    @Override
    public ScriptFile getParentFile() {
        String p = this.getParent();
        if (p == null)
            return null;
        return new ScriptFile(p);
    }

    public ScriptSource toSource() {
        return ScriptLaunchSourceFactory.createFileSource(this);
    }
}
