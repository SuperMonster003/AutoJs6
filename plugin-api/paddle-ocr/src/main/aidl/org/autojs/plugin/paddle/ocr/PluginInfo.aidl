package org.autojs.plugin.paddle.ocr;

parcelable PluginInfo {

    String name;
    @nullable String description;

    String author;
    @nullable String[] collaborators;

    String versionName;
    long versionCode;
    @nullable String versionDate;

    /** @example "paddle-ocr-v5" */
    @nullable String id;
    /** @sample "paddle-ocr" */
    @nullable String engine;
    /** @sample "v5" */
    @nullable String variant;

    @nullable android.os.Bundle capabilities;

}
