package com.mcal.apksigner.app.filepicker;

import androidx.annotation.NonNull;

import java.io.InputStream;

/**
 * Created by radiationx on 12.01.17.
 */

public class RequestFile {
    private final String fileName;
    private final String mimeType;
    private InputStream fileStream;
    private String requestName;

    public RequestFile(String fileName, String mimeType, InputStream fileStream) {
        this.fileStream = fileStream;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public RequestFile(String requestName, String fileName, String mimeType, InputStream fileStream) {
        this.requestName = requestName;
        this.fileStream = fileStream;
        this.fileName = fileName;
        this.mimeType = mimeType;
    }

    public InputStream getFileStream() {
        return fileStream;
    }

    public void setFileStream(InputStream fileStream) {
        this.fileStream = fileStream;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getRequestName() {
        return requestName;
    }

    public void setRequestName(String requestName) {
        this.requestName = requestName;
    }

    @NonNull
    @Override
    public String toString() {
        return "RequestFile{" + fileName + ", " + mimeType + ", " + requestName + ", " + fileStream + "}";
    }
}
