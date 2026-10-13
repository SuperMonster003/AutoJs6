package org.autojs.autojs.external.fileprovider;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;

import org.autojs.autojs6.BuildConfig;

import java.io.File;

public class AppFileProvider extends FileProvider {

    /**
     * Authority of the FileProvider this build declares. Gradle writes the same value into the manifest
     * placeholder "authorities" and into BuildConfig: org.autojs.autojs6.fileprovider for the app,
     * org.autojs.autojs6.inrt.fileprovider for inrt and org.autojs.autojs6.compose.spike.fileprovider for the
     * isolated Compose spike host, so a Uri always names a provider owned by the running application.
     * <p>
     * Read through a method on purpose: a compile-time constant would be inlined into every Java and Kotlin
     * caller, and the app and the spike host share the appDebug variant, so an incremental build that switches
     * between them would keep the previous authority in callers that did not change.
     */
    public static final String AUTHORITY = authority();

    private static String authority() {
        return BuildConfig.FILE_PROVIDER_AUTHORITY;
    }

    public static Uri getUriForFile(Context context, File file){
        return FileProvider.getUriForFile(context, AUTHORITY, file);
    }
}
