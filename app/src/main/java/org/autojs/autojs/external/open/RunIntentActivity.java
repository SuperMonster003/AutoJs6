package org.autojs.autojs.external.open;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.autojs.autojs.core.plugin.jvm.JvmSourceExplicitRunner;
import org.autojs.autojs.execution.ExecutionConfig;
import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.script.JvmSourceKind;
import org.autojs.autojs.script.StringScriptSource;
import org.autojs.autojs.storage.file.TmpScriptFiles;
import org.autojs.autojs.util.IntentUtils;
import org.autojs.autojs.util.ScriptOriginResolver;
import org.autojs.autojs.util.ViewUtils;
import org.autojs.autojs6.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by Stardust on Feb 22, 2017.
 */
public class RunIntentActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            handleIntent(getIntent());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true);
        }
        finish();
    }

    private void handleIntent(Intent intent) throws IOException {
        Uri uri = intent.getData();
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            ScriptIntents.handleIntent(this, intent);
            return;
        }

        String originPath = ScriptOriginResolver.resolveFilePath(this, uri);
        String displayName = IntentUtils.getFileName(this, uri);
        if (JvmSourceKind.fromFileName(displayName) == JvmSourceKind.JAVA
                || JvmSourceKind.fromFileName(originPath) == JvmSourceKind.JAVA) {
            // Content-provided .java sources are staged into a private tmp file and executed
            // through the explicit JVM source runner (Roadmap v2 M1).
            // zh-CN: content 提供的 .java 源码先落入私有临时文件, 再经显式 JVM runner 执行.
            runJavaSourceFromContent(uri, originPath);
            return;
        }
        JvmSourceKind.requireNoJavaScriptFallback(displayName);
        JvmSourceKind.requireNoJavaScriptFallback(originPath);
        String sourceName = PFiles.getNameWithoutExtension(displayName);

        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new FileNotFoundException(uri.toString());
        }

        StringScriptSource source = new StringScriptSource(sourceName, PFiles.read(stream));
        source.setOverriddenFullPath(originPath);
        source.setOriginUri(uri.toString());

        String workingDirectory = originPath == null ? null : new File(originPath).getParent();
        Scripts.run(this, source, workingDirectory);
    }

    private void runJavaSourceFromContent(Uri uri, @Nullable String originPath) throws IOException {
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new FileNotFoundException(uri.toString());
        }
        File tmpFile = TmpScriptFiles.createWithExactName(this, "Main.java");
        PFiles.write(tmpFile, PFiles.read(stream));
        String workingDirectory = originPath == null ? tmpFile.getParent() : new File(originPath).getParent();
        ExecutionConfig config = new ExecutionConfig();
        config.setWorkingDirectory(workingDirectory == null ? "" : workingDirectory);
        JvmSourceExplicitRunner.runJvmSourceExplicit(
                tmpFile,
                null,
                config,
                originPath,
                uri.toString()
        );
    }

}
