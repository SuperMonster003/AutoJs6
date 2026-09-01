package org.autojs.autojs.external.open;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;

import org.autojs.autojs.external.ScriptIntents;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.runtime.api.Engines;
import org.autojs.autojs.script.JavaScriptSource;
import org.autojs.autojs.script.JvmFileSource;
import org.autojs.autojs.script.JvmSourceKind;
import org.autojs.autojs.storage.file.TmpScriptFiles;
import org.autojs.autojs.ui.jvm.JvmSourcePluginUi;
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
        boolean finishAfterHandling = true;
        try {
            finishAfterHandling = handleIntent(getIntent());
        } catch (Exception e) {
            e.printStackTrace();
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true);
        }
        if (finishAfterHandling) {
            finish();
        }
    }

    /** Returns false only while an unavailable-provider dialog owns this Activity's window. */
    private boolean handleIntent(Intent intent) throws IOException {
        Uri uri = intent.getData();
        if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
            ScriptIntents.PreparedLaunch prepared = ScriptIntents.prepareLaunch(this, intent);
            if (prepared != null) {
                if (prepared.getLaunch().getSource() instanceof JvmFileSource jvmSource) {
                    if (!JvmSourcePluginUi.ensureAvailableForInteractiveLaunch(
                            this,
                            jvmSource.getSourceKind(),
                            this::finish
                    )) {
                        return false;
                    }
                }
                Scripts.executeLaunch(this, prepared.getLaunch(), prepared.getConfig());
            }
            return true;
        }

        ScriptIntents.enforceExternalUriRequestShape(intent);
        String originPath = ScriptOriginResolver.resolveFilePath(this, uri);
        String displayName = IntentUtils.getFileName(this, uri);
        String providerMimeType = getContentResolver().getType(uri);
        ScriptIntents.enforceContentPythonExecutionBoundary(
                displayName,
                uri.getPath(),
                originPath,
                intent.getType(),
                providerMimeType
        );
        JvmSourceKind jvmSourceKind = JvmSourceKind.fromFileName(displayName);
        if (jvmSourceKind == null) {
            jvmSourceKind = JvmSourceKind.fromFileName(originPath);
        }
        if (jvmSourceKind == null) {
            jvmSourceKind = JvmSourceKind.fromFileName(uri.getPath());
        }
        if (jvmSourceKind != null) {
            return runJvmSourceFromContent(
                    uri,
                    originPath,
                    displayName,
                    providerMimeType,
                    jvmSourceKind
            );
        }
        JvmSourceKind.requireNoJavaScriptFallback(displayName);
        JvmSourceKind.requireNoJavaScriptFallback(originPath);
        JvmSourceKind.requireNoJavaScriptFallback(uri.getPath());
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new FileNotFoundException(uri.toString());
        }

        JavaScriptSource source = Engines.createExecScriptSource(displayName, PFiles.read(stream));
        source.setOverriddenFullPath(originPath);
        source.setOriginUri(uri.toString());

        String workingDirectory = originPath == null ? null : new File(originPath).getParent();
        Scripts.run(this, source, workingDirectory);
        return true;
    }

    private boolean runJvmSourceFromContent(
            Uri uri,
            @Nullable String originPath,
            String displayName,
            @Nullable String providerMimeType,
            JvmSourceKind sourceKind
    ) throws IOException {
        if (!JvmSourcePluginUi.ensureAvailableForInteractiveLaunch(this, sourceKind, this::finish)) {
            return false;
        }
        InputStream stream = getContentResolver().openInputStream(uri);
        if (stream == null) {
            throw new FileNotFoundException(uri.toString());
        }
        File tmpFile = TmpScriptFiles.createWithExactName(
                this,
                sourceKind == JvmSourceKind.JAVA ? "Main.java" : "Main.kt"
        );
        PFiles.write(tmpFile, PFiles.read(stream));
        String workingDirectory = originPath == null ? tmpFile.getParent() : new File(originPath).getParent();
        Scripts.runWithBroadcastSender(
                tmpFile,
                workingDirectory == null ? "" : workingDirectory,
                originPath,
                uri.toString(),
                displayName,
                providerMimeType
        );
        return true;
    }

}
