package org.autojs.autojs.core.storage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import org.autojs.autojs.app.GlobalAppContext;
import org.autojs.autojs.runtime.api.augment.storages.StorageNativeObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Stardust on Dec 3, 2017.
 * Modified by SuperMonster003 as of Oct 15, 2024.
 */
@SuppressLint("ApplySharedPref")
public class LocalStorage {

    public static final String NAME_PREFIX = "autojs.localstorage.";

    private final SharedPreferences mSharedPreferences;

    public LocalStorage(Context context, String name) {
        mSharedPreferences = context.getSharedPreferences(NAME_PREFIX + name, Context.MODE_PRIVATE);
    }

    public LocalStorage(String name) {
        this(GlobalAppContext.get(), name);
    }

    public int size() {
        return mSharedPreferences.getAll().size();
    }

    public LocalStorage put(String key, String value) {
        mSharedPreferences.edit()
                .putString(key, value)
                .apply();
        return this;
    }

    public LocalStorage put(String key, long value) {
        mSharedPreferences.edit()
                .putLong(key, value)
                .apply();
        return this;
    }

    public LocalStorage put(String key, boolean value) {
        mSharedPreferences.edit()
                .putBoolean(key, value)
                .apply();
        return this;
    }

    public LocalStorage putSync(String key, String value) {
        mSharedPreferences.edit()
                .putString(key, value)
                .commit();
        return this;
    }

    public LocalStorage putSync(String key, long value) {
        mSharedPreferences.edit()
                .putLong(key, value)
                .commit();
        return this;
    }

    public LocalStorage putSync(String key, boolean value) {
        mSharedPreferences.edit()
                .putBoolean(key, value)
                .commit();
        return this;
    }

    public long getNumber(String key, long defaultValue) {
        return mSharedPreferences.getLong(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return mSharedPreferences.getBoolean(key, defaultValue);
    }

    public String getString(String key, String defaultValue) {
        return mSharedPreferences.getString(key, defaultValue);
    }

    public long getNumber(String key) {
        return getNumber(key, 0);
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public void remove(String key) {
        mSharedPreferences.edit().remove(key).apply();
    }

    public void removeSync(String key) {
        mSharedPreferences.edit().remove(key).commit();
    }

    public boolean contains(String key) {
        return mSharedPreferences.contains(key);
    }

    public void clear() {
        mSharedPreferences.edit().clear().apply();
    }

    public void clearSync() {
        mSharedPreferences.edit().clear().commit();
    }

    public static List<String> getAllStorageNames() {
        List<String> storageNames = new ArrayList<>();
        File prefsDir = new File(GlobalAppContext.get().getApplicationInfo().dataDir, "shared_prefs");
        if (prefsDir.exists() && prefsDir.isDirectory()) {
            File[] listedFiles = prefsDir.listFiles();
            if (listedFiles != null) {
                for (File file : listedFiles) {
                    String fileName = file.getName();
                    if (fileName.startsWith(NAME_PREFIX) && fileName.endsWith(".xml") && !isSharedPreferencesFileAsEmpty(file.getPath())) {
                        String storageName = fileName.substring(NAME_PREFIX.length(), fileName.length() - 4);
                        storageNames.add(storageName);
                    }
                }
            }
        }
        return storageNames;
    }

    public static List<StorageNativeObject> getAllStorages() {
        List<StorageNativeObject> storages = new ArrayList<>();
        for (String name : getAllStorageNames()) {
            storages.add(new StorageNativeObject(name));
        }
        return storages;
    }

    private static boolean isSharedPreferencesFileAsEmpty(String filePath) {
        File sharedPrefsFile = new File(filePath);
        if (!sharedPrefsFile.exists()) {
            return true;
        }
        try {
            FileInputStream fis = new FileInputStream(sharedPrefsFile);
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            parser.setInput(fis, "UTF-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("map")) {
                    eventType = parser.next();
                    return eventType == XmlPullParser.END_TAG;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
        return true;
    }

}
