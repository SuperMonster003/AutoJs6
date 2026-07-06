package org.autojs.plugin.apkbuilder.template.impl;

import pxb.android.StringItem;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

class RemoteApkManifestEditor {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String EMBEDDED_NODE_SCRIPT_SERVICE = "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService";
    private static final String EMBEDDED_NODE_PROCESS = ":nodejs_embedded";
    private static final String FOREGROUND_SERVICE_PERMISSION = "android.permission.FOREGROUND_SERVICE";
    private static final String FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE";
    private static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000;
    private final InputStream mManifestInputStream;
    private int mVersionCode = -1;
    private int mSplashThemeId;
    private int mNoSplashThemeId;
    private String mVersionName;
    private String mAppName;
    private String mPackageName;
    private Set<String> mPermissions;
    private byte[] mManifestData;
    private boolean mShouldAddEmbeddedNodeScriptService;
    private boolean mShouldAddEmbeddedNodeForegroundServicePermissions;
    private boolean mHasEmbeddedNodeScriptService;
    private boolean mHasForegroundServicePermission;
    private boolean mHasForegroundServiceSpecialUsePermission;
    private boolean mHasAddedEmbeddedNodeForegroundServicePermissions;

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

    RemoteApkManifestEditor setPermissions(Collection<String> permissions) {
        mPermissions = permissions == null ? null : new HashSet<>(permissions);
        return this;
    }

    RemoteApkManifestEditor setSplashThemeReplacement(int splashThemeId, int noSplashThemeId) {
        mSplashThemeId = splashThemeId;
        mNoSplashThemeId = noSplashThemeId;
        return this;
    }

    RemoteApkManifestEditor addEmbeddedNodeScriptServiceIfMissing() {
        mShouldAddEmbeddedNodeScriptService = true;
        return this;
    }

    RemoteApkManifestEditor addEmbeddedNodeForegroundServicePermissionsIfMissing() {
        mShouldAddEmbeddedNodeForegroundServicePermissions = true;
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
        if ("theme".equals(attr.name.data)
                && mSplashThemeId != 0
                && mNoSplashThemeId != 0
                && attr.value instanceof Integer
                && (Integer) attr.value == mSplashThemeId
        ) {
            attr.value = mNoSplashThemeId;
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

    private boolean isPermissionRequired(String permissionName) {
        if (mShouldAddEmbeddedNodeForegroundServicePermissions && isEmbeddedNodeForegroundServicePermission(permissionName)) {
            return true;
        }
        return mPermissions == null || mPermissions.contains(permissionName);
    }

    private boolean isEmbeddedNodeForegroundServicePermission(String permissionName) {
        return FOREGROUND_SERVICE_PERMISSION.equals(permissionName)
                || FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION.equals(permissionName);
    }

    private class MutableAxmlWriter extends AxmlWriter {
        private class MutableNodeImpl extends AxmlWriter.NodeImpl {
            private String mComponentClassName;
            private boolean mHasExportedAttr;
            private boolean mHasForegroundServiceTypeAttr;
            private boolean mHasProcessAttr;

            MutableNodeImpl(String ns, String name) {
                super(ns, name);
            }

            @Override
            protected void onAttr(AxmlWriter.Attr attr) {
                if ("service".equals(this.name.data)
                        && attr.ns != null
                        && NS_ANDROID.equals(attr.ns.data)
                ) {
                    if ("name".equals(attr.name.data) && attr.value instanceof StringItem) {
                        mComponentClassName = ((StringItem) attr.value).data;
                        if (EMBEDDED_NODE_SCRIPT_SERVICE.equals(mComponentClassName)) {
                            mHasEmbeddedNodeScriptService = true;
                        }
                    } else if ("exported".equals(attr.name.data)) {
                        mHasExportedAttr = true;
                    } else if ("foregroundServiceType".equals(attr.name.data)) {
                        mHasForegroundServiceTypeAttr = true;
                    } else if ("process".equals(attr.name.data)) {
                        mHasProcessAttr = true;
                    }
                }
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
                if ("uses-permission".equals(this.name.data)
                        && "name".equals(attr.name.data)
                        && attr.value instanceof StringItem
                ) {
                    String permissionName = ((StringItem) attr.value).data;
                    if (FOREGROUND_SERVICE_PERMISSION.equals(permissionName)) {
                        mHasForegroundServicePermission = true;
                    } else if (FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION.equals(permissionName)) {
                        mHasForegroundServiceSpecialUsePermission = true;
                    }
                    this.ignore = !isPermissionRequired(permissionName);
                    if (this.ignore) {
                        return;
                    }
                }
                RemoteApkManifestEditor.this.onAttr(attr);
                super.onAttr(attr);
            }

            @Override
            public void end() {
                if (EMBEDDED_NODE_SCRIPT_SERVICE.equals(mComponentClassName)) {
                    ensureEmbeddedNodeScriptServiceAttrs();
                }
                if ("application".equals(this.name.data)
                        && mShouldAddEmbeddedNodeScriptService
                        && !mHasEmbeddedNodeScriptService
                ) {
                    addEmbeddedNodeScriptServiceNode();
                    mHasEmbeddedNodeScriptService = true;
                }
                if ("manifest".equals(this.name.data)) {
                    addMissingEmbeddedNodeForegroundServicePermissionNodes();
                }
                super.end();
            }

            @Override
            public NodeVisitor child(String ns, String name) {
                if ("manifest".equals(this.name.data) && "application".equals(name)) {
                    addMissingEmbeddedNodeForegroundServicePermissionNodes();
                }
                NodeImpl child = new MutableNodeImpl(ns, name);
                this.children.add(child);
                return child;
            }

            private void ensureEmbeddedNodeScriptServiceAttrs() {
                if (!mHasExportedAttr) {
                    attr(NS_ANDROID, "exported", android.R.attr.exported, NodeVisitor.TYPE_INT_BOOLEAN, Boolean.FALSE);
                }
                if (!mHasForegroundServiceTypeAttr) {
                    attr(NS_ANDROID, "foregroundServiceType", android.R.attr.foregroundServiceType, NodeVisitor.TYPE_INT_HEX, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                }
                if (!mHasProcessAttr) {
                    attr(NS_ANDROID, "process", android.R.attr.process, NodeVisitor.TYPE_STRING, EMBEDDED_NODE_PROCESS);
                }
            }

            private void addEmbeddedNodeScriptServiceNode() {
                NodeVisitor service = child(null, "service");
                service.attr(NS_ANDROID, "name", android.R.attr.name, NodeVisitor.TYPE_STRING, EMBEDDED_NODE_SCRIPT_SERVICE);
                service.attr(NS_ANDROID, "exported", android.R.attr.exported, NodeVisitor.TYPE_INT_BOOLEAN, Boolean.FALSE);
                service.attr(NS_ANDROID, "foregroundServiceType", android.R.attr.foregroundServiceType, NodeVisitor.TYPE_INT_HEX, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                service.attr(NS_ANDROID, "process", android.R.attr.process, NodeVisitor.TYPE_STRING, EMBEDDED_NODE_PROCESS);

                NodeVisitor property = service.child(null, "property");
                property.attr(NS_ANDROID, "name", android.R.attr.name, NodeVisitor.TYPE_STRING, "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE");
                property.attr(NS_ANDROID, "value", android.R.attr.value, NodeVisitor.TYPE_STRING, "Run isolated embedded Node scripts without touching the main app process");
            }

            private void addMissingEmbeddedNodeForegroundServicePermissionNodes() {
                if (!mShouldAddEmbeddedNodeForegroundServicePermissions || mHasAddedEmbeddedNodeForegroundServicePermissions) {
                    return;
                }
                if (!mHasForegroundServicePermission) {
                    addUsesPermissionNode(FOREGROUND_SERVICE_PERMISSION);
                    mHasForegroundServicePermission = true;
                }
                if (!mHasForegroundServiceSpecialUsePermission) {
                    addUsesPermissionNode(FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION);
                    mHasForegroundServiceSpecialUsePermission = true;
                }
                mHasAddedEmbeddedNodeForegroundServicePermissions = true;
            }

            private void addUsesPermissionNode(String permissionName) {
                NodeVisitor usesPermission = child(null, "uses-permission");
                usesPermission.attr(NS_ANDROID, "name", android.R.attr.name, NodeVisitor.TYPE_STRING, permissionName);
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
