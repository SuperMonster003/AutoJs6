package org.autojs.plugin.apkbuilder.template.impl;

import pxb.android.StringItem;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class RemoteApkManifestEditor {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private final InputStream mManifestInputStream;
    private int mVersionCode = -1;
    private String mVersionName;
    private String mAppName;
    private String mPackageName;
    private byte[] mManifestData;

    RemoteApkManifestEditor(InputStream manifestInputStream) {
        mManifestInputStream = manifestInputStream;
    }

    RemoteApkManifestEditor setVersionCode(int versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    RemoteApkManifestEditor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    RemoteApkManifestEditor setAppName(String appName) {
        mAppName = appName;
        return this;
    }

    RemoteApkManifestEditor setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    RemoteApkManifestEditor commit() throws IOException {
        AxmlWriter writer = new MutableAxmlWriter();
        AxmlReader reader = new AxmlReader(readFully(mManifestInputStream));
        reader.accept(writer);
        mManifestData = writer.toByteArray();
        return this;
    }

    void writeTo(OutputStream manifestOutputStream) throws IOException {
        try (OutputStream output = manifestOutputStream) {
            output.write(mManifestData);
        }
    }

    private static byte[] readFully(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256 * 1024];
            int read;
            while ((read = source.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private void onAttr(AxmlWriter.Attr attr) {
        if ("package".equals(attr.name.data) && mPackageName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mPackageName;
            return;
        }
        if (attr.ns == null || !NS_ANDROID.equals(attr.ns.data)) {
            rewriteAuthorities(attr);
            return;
        }
        if ("versionCode".equals(attr.name.data) && mVersionCode != -1) {
            attr.value = mVersionCode;
            return;
        }
        if ("versionName".equals(attr.name.data) && mVersionName != null) {
            attr.value = new StringItem(mVersionName);
            return;
        }
        if ("label".equals(attr.name.data) && mAppName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mAppName;
            return;
        }
        rewriteAuthorities(attr);
    }

    private void rewriteAuthorities(AxmlWriter.Attr attr) {
        if (!"authorities".equals(attr.name.data) || mPackageName == null || !(attr.value instanceof StringItem)) {
            return;
        }
        ((StringItem) attr.value).data = ((StringItem) attr.value).data.replace(BuildConfig.TEMPLATE_PACKAGE_NAME, mPackageName);
    }

    private boolean shouldIgnoreComponentNode(String nodeName, String componentClassName) {
        if ("meta-data".equals(nodeName) && "android.app.shortcuts".equals(componentClassName)) {
            return true;
        }
        return "org.autojs.autojs.external.open.EditIntentActivity".equals(componentClassName)
                || "org.autojs.autojs.external.open.RunIntentActivity".equals(componentClassName)
                || "org.autojs.autojs.external.open.ImportIntentActivity".equals(componentClassName)
                || "org.autojs.autojs.external.tile.LayoutBoundsTile".equals(componentClassName)
                || "org.autojs.autojs.external.tile.LayoutHierarchyTile".equals(componentClassName);
    }

    private class MutableAxmlWriter extends AxmlWriter {
        private class MutableNodeImpl extends AxmlWriter.NodeImpl {

            MutableNodeImpl(String ns, String name) {
                super(ns, name);
            }

            @Override
            protected void onAttr(AxmlWriter.Attr attr) {
                if (attr.ns != null
                        && NS_ANDROID.equals(attr.ns.data)
                        && "name".equals(attr.name.data)
                        && attr.value instanceof StringItem
                        && shouldIgnoreComponentNode(this.name.data, ((StringItem) attr.value).data)
                ) {
                    this.ignore = true;
                    return;
                }
                if ("permission".equals(this.name.data)
                        && "name".equals(attr.name.data)
                        && attr.value instanceof StringItem
                ) {
                    String permissionName = ((StringItem) attr.value).data;
                    if ((BuildConfig.TEMPLATE_PACKAGE_NAME + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION").equals(permissionName)) {
                        ((StringItem) attr.value).data = mPackageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
                        super.onAttr(attr);
                        return;
                    }
                    if ("org.autojs.permission.PLUGIN".equals(permissionName)) {
                        this.ignore = true;
                        return;
                    }
                }
                RemoteApkManifestEditor.this.onAttr(attr);
                super.onAttr(attr);
            }

            @Override
            public NodeVisitor child(String ns, String name) {
                NodeImpl child = new MutableNodeImpl(ns, name);
                this.children.add(child);
                return child;
            }
        }

        @Override
        public NodeVisitor child(String ns, String name) {
            NodeImpl first = new MutableNodeImpl(ns, name);
            this.firsts.add(first);
            return first;
        }
    }
}
