package org.autojs.autojs.apkbuilder;

import pxb.android.StringItem;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.autojs.autojs.apkbuilder.ApkBuilder.INRT_APP_ID;

/**
 * Created by Stardust on Oct 23, 2017.
 * Modified by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) as of Mar 9, 2026.
 */
public class ManifestEditor {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String EMBEDDED_NODE_SCRIPT_SERVICE = "org.autojs.autojs.engine.NativeNodeEmbeddedScriptService";
    private static final String EMBEDDED_NODE_PROCESS = ":nodejs_embedded";
    private static final String FOREGROUND_SERVICE_PERMISSION = "android.permission.FOREGROUND_SERVICE";
    private static final String FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION = "android.permission.FOREGROUND_SERVICE_SPECIAL_USE";
    private static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 0x40000000;
    private final InputStream mManifestInputStream;
    private int mVersionCode = -1;
    private String mVersionName;
    private CharSequence mAppName;
    private String mPackageName;
    private byte[] mManifestData;
    private boolean mShouldAddEmbeddedNodeScriptService;
    private boolean mShouldAddEmbeddedNodeForegroundServicePermissions;
    private boolean mHasEmbeddedNodeScriptService;
    private boolean mHasForegroundServicePermission;
    private boolean mHasForegroundServiceSpecialUsePermission;
    private boolean mHasAddedEmbeddedNodeForegroundServicePermissions;

    public ManifestEditor(InputStream manifestInputStream) {
        mManifestInputStream = manifestInputStream;
    }

    public ManifestEditor setVersionCode(int versionCode) {
        mVersionCode = versionCode;
        return this;
    }

    public ManifestEditor setVersionName(String versionName) {
        mVersionName = versionName;
        return this;
    }

    public ManifestEditor setAppName(CharSequence appName) {
        mAppName = appName;
        return this;
    }

    public ManifestEditor setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public ManifestEditor addEmbeddedNodeScriptServiceIfMissing() {
        mShouldAddEmbeddedNodeScriptService = true;
        return this;
    }

    public ManifestEditor addEmbeddedNodeForegroundServicePermissionsIfMissing() {
        mShouldAddEmbeddedNodeForegroundServicePermissions = true;
        return this;
    }

    public ManifestEditor commit() throws IOException {
        AxmlWriter writer = new MutableAxmlWriter();
        AxmlReader reader = new AxmlReader(readFully(mManifestInputStream));
        reader.accept(writer);
        mManifestData = writer.toByteArray();
        return this;
    }

    private static byte[] readFully(InputStream is) throws IOException {
        byte[] bytes = new byte[is.available()];
        is.read(bytes);
        return bytes;
    }

    public void writeTo(OutputStream manifestOutputStream) throws IOException {
        manifestOutputStream.write(mManifestData);
        manifestOutputStream.close();
    }

    public void onAttr(AxmlWriter.Attr attr) {
        if ("package".equals(attr.name.data) && mPackageName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mPackageName;
            return;
        }
        if (attr.ns == null || !NS_ANDROID.equals(attr.ns.data)) {
            return;
        }
        if ("versionCode".equals(attr.name.data) && mVersionCode != -1) {
            attr.value = mVersionCode;
            return;
        }
        if ("versionName".equals(attr.name.data) && mVersionName != null && attr.value instanceof StringItem) {
            attr.value = new StringItem(mVersionName);
            ((StringItem) attr.value).data = mVersionName;
            return;
        }
        if ("label".equals(attr.name.data) && mAppName != null && attr.value instanceof StringItem) {
            ((StringItem) attr.value).data = mAppName.toString();
            return;
        }
    }

    public boolean isPermissionRequired(String permissionName) {
        return true;
    }

    protected boolean isEmbeddedNodeForegroundServicePermission(String permissionName) {
        return FOREGROUND_SERVICE_PERMISSION.equals(permissionName)
            || FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION.equals(permissionName);
    }

    public boolean shouldIgnoreComponentNode(String nodeName, String componentClassName) {
        return false;
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
            protected void onAttr(AxmlWriter.Attr a) {
                if ("service".equals(this.name.data)
                    && a.ns != null
                    && NS_ANDROID.equals(a.ns.data)
                ) {
                    if ("name".equals(a.name.data) && a.value instanceof StringItem) {
                        mComponentClassName = ((StringItem) a.value).data;
                        if (EMBEDDED_NODE_SCRIPT_SERVICE.equals(mComponentClassName)) {
                            mHasEmbeddedNodeScriptService = true;
                        }
                    } else if ("exported".equals(a.name.data)) {
                        mHasExportedAttr = true;
                    } else if ("foregroundServiceType".equals(a.name.data)) {
                        mHasForegroundServiceTypeAttr = true;
                    } else if ("process".equals(a.name.data)) {
                        mHasProcessAttr = true;
                    }
                }
                if (a.ns != null
                    && NS_ANDROID.equals(a.ns.data)
                    && "name".equals(a.name.data)
                    && a.value instanceof StringItem
                    && ManifestEditor.this.shouldIgnoreComponentNode(this.name.data, ((StringItem) a.value).data)
                ) {
                    this.ignore = true;
                    return;
                }
                if ("permission".equals(this.name.data)
                    && "name".equals(a.name.data)
                    && a.value instanceof StringItem
                ) {
                    if ((INRT_APP_ID + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION").equals(((StringItem) a.value).data)) {
                        ((StringItem) a.value).data = mPackageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
                        super.onAttr(a);
                        return;
                    }
                    if ("org.autojs.permission.PLUGIN".equals(((StringItem) a.value).data)) {
                        this.ignore = true;
                        return;
                    }
                }
                if ("uses-permission".equals(this.name.data)
                    && "name".equals(a.name.data)
                    && a.value instanceof StringItem
                ) {
                    String permissionName = ((StringItem) a.value).data;
                    if (FOREGROUND_SERVICE_PERMISSION.equals(permissionName)) {
                        mHasForegroundServicePermission = true;
                    } else if (FOREGROUND_SERVICE_SPECIAL_USE_PERMISSION.equals(permissionName)) {
                        mHasForegroundServiceSpecialUsePermission = true;
                    }
                    this.ignore = !ManifestEditor.this.isPermissionRequired(((StringItem) a.value).data);
                }
                ManifestEditor.this.onAttr(a);
                super.onAttr(a);
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
