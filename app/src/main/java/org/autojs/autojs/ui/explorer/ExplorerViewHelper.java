package org.autojs.autojs.ui.explorer;

import static org.autojs.autojs.util.FileUtils.TYPE.AUTO;
import static org.autojs.autojs.util.FileUtils.TYPE.JAVASCRIPT;
import static org.autojs.autojs.util.FileUtils.TYPE.PROJECT;
import static org.autojs.autojs.util.FileUtils.TYPE.UNKNOWN;

import android.content.Context;

import androidx.annotation.NonNull;

import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerProjectPage;
import org.autojs.autojs.model.explorer.ExplorerSamplePage;
import org.autojs.autojs.pio.PFiles;
import org.autojs.autojs.util.FileUtils.TYPE;
import org.autojs.autojs6.R;

public class ExplorerViewHelper {

    public static String getDisplayName(Context context, ExplorerItem item) {
        if (item instanceof ExplorerSamplePage && ((ExplorerSamplePage) item).isRoot()) {
            return context.getString(R.string.text_sample);
        }
        if (item instanceof ExplorerPage) {
            return item.getName();
        }
        TYPE type = item.getType();
        if (type == JAVASCRIPT || type == AUTO) {
            if (item instanceof ExplorerFileItem) {
                return ((ExplorerFileItem) item).getFile().getSimplifiedName();
            }
            return PFiles.getNameWithoutExtension(item.getName());
        }
        return item.getName();
    }

    public static String getIconText(@NonNull ExplorerItem item) {
        TYPE type = item.getType();
        switch (type) {
            case UNKNOWN:
                return UNKNOWN.getIconText();
            case AUTO:
                return AUTO.getIconText();
        }
        if (item.getName().equalsIgnoreCase(PROJECT.getTypeName())) {
            return PROJECT.getIconText();
        }
        return type.getTypeName().substring(0, 1).toUpperCase();
    }

    public static int getIcon(ExplorerPage page) {
        if (page instanceof ExplorerSamplePage) {
            return R.drawable.ic_sample_dir;
        }
        if (page instanceof ExplorerProjectPage) {
            return R.drawable.ic_project;
        }
        return R.drawable.ic_folder_yellow_100px;
    }

}
