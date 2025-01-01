package org.autojs.autojs.apkbuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import pxb.android.StringItem;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;

/**
 * Created by Stardust on Oct 23, 2017.
 */
public class ManifestEditor {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private final InputStream mManifestInputStream;
    private int mVersionCode = -1;
    private String mVersionName;
    private CharSequence mAppName;
    private String mPackageName;
    private byte[] mManifestData;

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

    private class MutableAxmlWriter extends AxmlWriter {
        private class MutableNodeImpl extends AxmlWriter.NodeImpl {

            MutableNodeImpl(String ns, String name) {
                super(ns, name);
            }

            @Override
            protected void onAttr(AxmlWriter.Attr a) {
                if ("permission".equals(this.name.data) && "name".equals(a.name.data) && a.value instanceof StringItem) {
                    if ("org.autojs.autojs6.inrt.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION".equals(((StringItem) a.value).data)) {
                        ((StringItem) a.value).data = mPackageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
                        super.onAttr(a);
                        return;
                    }
                }
                if ("uses-permission".equals(this.name.data) && "name".equals(a.name.data) && a.value instanceof StringItem) {
                    this.ignore = !ManifestEditor.this.isPermissionRequired(((StringItem) a.value).data);
                }
                ManifestEditor.this.onAttr(a);
                super.onAttr(a);
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
