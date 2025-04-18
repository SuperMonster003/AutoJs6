package net.dongliu.apk.parser;

import static java.lang.System.arraycopy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.dongliu.apk.parser.bean.AdaptiveIcon;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.dongliu.apk.parser.bean.ApkSignStatus;
import net.dongliu.apk.parser.bean.ApkSigner;
import net.dongliu.apk.parser.bean.ApkV2Signer;
import net.dongliu.apk.parser.bean.CertificateMeta;
import net.dongliu.apk.parser.bean.DexClass;
import net.dongliu.apk.parser.bean.Icon;
import net.dongliu.apk.parser.bean.IconFace;
import net.dongliu.apk.parser.bean.IconPath;
import net.dongliu.apk.parser.exception.ParserException;
import net.dongliu.apk.parser.parser.AdaptiveIconParser;
import net.dongliu.apk.parser.parser.ApkMetaTranslator;
import net.dongliu.apk.parser.parser.ApkSignBlockParser;
import net.dongliu.apk.parser.parser.BinaryXmlParser;
import net.dongliu.apk.parser.parser.CertificateMetas;
import net.dongliu.apk.parser.parser.CertificateParser;
import net.dongliu.apk.parser.parser.CompositeXmlStreamer;
import net.dongliu.apk.parser.parser.DexParser;
import net.dongliu.apk.parser.parser.ResourceTableParser;
import net.dongliu.apk.parser.parser.XmlStreamer;
import net.dongliu.apk.parser.parser.XmlTranslator;
import net.dongliu.apk.parser.struct.AndroidConstants;
import net.dongliu.apk.parser.struct.resource.ResourceTable;
import net.dongliu.apk.parser.struct.signingv2.ApkSigningBlock;
import net.dongliu.apk.parser.struct.signingv2.SignerBlock;
import net.dongliu.apk.parser.struct.zip.EOCD;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.Unsigned;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Common Apk Parser methods.
 * This Class is not thread-safe.
 *
 * @author Liu Dong
 */
public abstract class AbstractApkFile implements Closeable {
    private DexClass[] dexClasses;

    private boolean resourceTableParsed;
    private ResourceTable resourceTable;
    private Set<Locale> locales;

    private boolean manifestParsed;
    private String manifestXml;
    private ApkMeta apkMeta;
    private List<IconPath> iconPaths;
    private List<ApkSigner> apkSigners;
    private List<ApkV2Signer> apkV2Signers;

    private static final Locale DEFAULT_LOCALE = Locale.US;

    /**
     * default use empty locale
     */
    private Locale preferredLocale = AbstractApkFile.DEFAULT_LOCALE;

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public String getManifestXml() throws IOException {
        this.parseManifest();
        return this.manifestXml;
    }

    /**
     * return decoded AndroidManifest.xml
     *
     * @return decoded AndroidManifest.xml
     */
    public ApkMeta getApkMeta() throws IOException {
        this.parseManifest();
        return this.apkMeta;
    }

    /**
     * get locales supported from resource file
     *
     * @return decoded AndroidManifest.xml
     */
    public Set<Locale> getLocales() throws IOException {
        this.parseResourceTable();
        return this.locales;
    }

    /**
     * Get the apk's all cert file info, of apk v1 signing.
     * If cert faile not exist, return empty list.
     */
    public List<ApkSigner> getApkSigners() throws IOException, CertificateException {
        if (this.apkSigners == null) {
            this.parseCertificates();
        }
        return this.apkSigners;
    }

    private void parseCertificates() throws IOException, CertificateException {
        this.apkSigners = new ArrayList<>();
        for (final CertificateFile file : this.getAllCertificateData()) {
            final CertificateParser parser = CertificateParser.getInstance(file.data);
            final List<CertificateMeta> certificateMetas = parser.parse();
            this.apkSigners.add(new ApkSigner(file.path, certificateMetas));
        }
    }

    /**
     * Get the apk's all signer in apk sign block, using apk singing v2 scheme.
     * If apk v2 signing block not exists, return empty list.
     */
    public List<ApkV2Signer> getApkV2Singers() throws IOException, CertificateException {
        if (this.apkV2Signers == null) {
            this.parseApkSigningBlock();
        }
        return this.apkV2Signers;
    }

    private void parseApkSigningBlock() throws IOException, CertificateException {
        final List<ApkV2Signer> list = new ArrayList<>();
        final ByteBuffer apkSignBlockBuf = this.findApkSignBlock();
        if (apkSignBlockBuf != null) {
            final ApkSignBlockParser parser = new ApkSignBlockParser(apkSignBlockBuf);
            final ApkSigningBlock apkSigningBlock = parser.parse();
            for (final SignerBlock signerBlock : apkSigningBlock.signerBlocks) {
                final List<X509Certificate> certificates = signerBlock.certificates;
                final List<CertificateMeta> certificateMetas = CertificateMetas.from(certificates);
                final ApkV2Signer apkV2Signer = new ApkV2Signer(certificateMetas);
                list.add(apkV2Signer);
            }
        }
        this.apkV2Signers = list;
    }

    @NonNull
    protected abstract List<CertificateFile> getAllCertificateData() throws IOException;

    protected static class CertificateFile {
        @NonNull
        public final String path;
        @NonNull
        public final byte[] data;

        public CertificateFile(@NonNull final String path, @NonNull final byte[] data) {
            this.path = path;
            this.data = data;
        }
    }

    private void parseManifest() throws IOException {
        if (this.manifestParsed) {
            return;
        }
        this.parseResourceTable();
        final XmlTranslator xmlTranslator = new XmlTranslator();
        final ApkMetaTranslator apkTranslator = new ApkMetaTranslator(this.resourceTable, this.preferredLocale);
        final XmlStreamer xmlStreamer = new CompositeXmlStreamer(xmlTranslator, apkTranslator);
        final byte[] data = this.getFileData(AndroidConstants.MANIFEST_FILE);
        if (data == null) {
            throw new ParserException("Manifest file not found");
        }
        this.transBinaryXml(data, xmlStreamer);
        this.manifestXml = xmlTranslator.getXml();
        this.apkMeta = apkTranslator.getApkMeta();
        this.iconPaths = apkTranslator.getIconPaths();
        this.manifestParsed = true;
    }

    /**
     * read file in apk into bytes
     */
    @Nullable
    public abstract byte[] getFileData(@NonNull String path) throws IOException;

    /**
     * return the whole apk file as ByteBuffer
     */
    @NonNull
    protected abstract ByteBuffer fileData() throws IOException;

    /**
     * trans binary xml file to text xml file.
     *
     * @param path the xml file path in apk file
     * @return the text. null if file not exists
     */
    @Nullable
    public String transBinaryXml(final @NonNull String path) throws IOException {
        final byte[] data = this.getFileData(path);
        if (data == null) {
            return null;
        }
        this.parseResourceTable();
        final XmlTranslator xmlTranslator = new XmlTranslator();
        this.transBinaryXml(data, xmlTranslator);
        return xmlTranslator.getXml();
    }

    private void transBinaryXml(final byte[] data, @NonNull final XmlStreamer xmlStreamer) throws IOException {
        this.parseResourceTable();
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final BinaryXmlParser binaryXmlParser = new BinaryXmlParser(buffer, this.resourceTable, xmlStreamer, this.preferredLocale);
        binaryXmlParser.parse();
    }

    /**
     * This method return icons specified in android manifest file, application.
     * The icons could be file icon, color icon, or adaptive icon, etc.
     *
     * @return icon files.
     */
    @NonNull
    public List<IconFace> getAllIcons() throws IOException {
        this.parseManifest();
        final List<IconPath> iconPaths = this.iconPaths;
        if (iconPaths.isEmpty()) {
            return Collections.emptyList();
        }
        final List<IconFace> iconFaces = new ArrayList<>(iconPaths.size());
        for (final IconPath iconPath : iconPaths) {
            final String filePath = iconPath.path;
            if (filePath != null && filePath.endsWith(".xml")) {
                // adaptive icon?
                final byte[] data = this.getFileData(filePath);
                if (data == null) {
                    continue;
                }
                this.parseResourceTable();
                final AdaptiveIconParser iconParser = new AdaptiveIconParser();
                this.transBinaryXml(data, iconParser);
                Icon backgroundIcon = null;
                final String background = iconParser.getBackground();
                if (background != null) {
                    backgroundIcon = this.newFileIcon(background, iconPath.density);
                }
                Icon foregroundIcon = null;
                final String foreground = iconParser.getForeground();
                if (foreground != null) {
                    foregroundIcon = this.newFileIcon(foreground, iconPath.density);
                }
                final AdaptiveIcon icon = new AdaptiveIcon(foregroundIcon, backgroundIcon);
                iconFaces.add(icon);
            } else {
                final Icon icon = this.newFileIcon(filePath, iconPath.density);
                iconFaces.add(icon);
            }
        }
        return iconFaces;
    }

    @NonNull
    private Icon newFileIcon(@NonNull final String filePath, final int density) throws IOException {
        return new Icon(filePath, density, this.getFileData(filePath));
    }

    /**
     * get class infos form dex file. currently only class name
     */
    public DexClass[] getDexClasses() throws IOException {
        if (this.dexClasses == null) {
            this.parseDexFiles();
        }
        return this.dexClasses;
    }

    private DexClass[] mergeDexClasses(@NonNull final DexClass[] first, @NonNull final DexClass[] second) {
        final DexClass[] result = new DexClass[first.length + second.length];
        arraycopy(first, 0, result, 0, first.length);
        arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @NonNull
    private DexClass[] parseDexFile(@NonNull final String path) throws IOException {
        final byte[] data = this.getFileData(path);
        if (data == null) {
            final String msg = String.format("Dex file %s not found", path);
            throw new ParserException(msg);
        }
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final DexParser dexParser = new DexParser(buffer);
        return dexParser.parse();
    }

    private void parseDexFiles() throws IOException {
        this.dexClasses = this.parseDexFile(AndroidConstants.DEX_FILE);
        for (int i = 2; i < 1000; i++) {
            final String path = String.format(Locale.ROOT, AndroidConstants.DEX_ADDITIONAL, i);
            try {
                final DexClass[] classes = this.parseDexFile(path);
                this.dexClasses = this.mergeDexClasses(this.dexClasses, classes);
            } catch (final ParserException e) {
                break;
            }
        }
    }

    /**
     * parse resource table.
     */
    private void parseResourceTable() throws IOException {
        if (this.resourceTableParsed) {
            return;
        }
        this.resourceTableParsed = true;
        final byte[] data = this.getFileData(AndroidConstants.RESOURCE_FILE);
        if (data == null) {
            // if no resource entry has been found, we assume it is not needed by this APK
            this.resourceTable = new ResourceTable(null);
            this.locales = Collections.emptySet();
            return;
        }
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        final ResourceTableParser resourceTableParser = new ResourceTableParser(buffer);
        resourceTableParser.parse();
        this.resourceTable = resourceTableParser.getResourceTable();
        this.locales = resourceTableParser.locales;
    }

    /**
     * Check apk sign. This method only use apk v1 scheme verifier
     *
     * @deprecated using google official ApkVerifier of apksig lib instead.
     */
    @NonNull
    @Deprecated
    public abstract ApkSignStatus verifyApk() throws IOException;

    @Override
    public void close() throws IOException {
        this.apkSigners = null;
        this.resourceTable = null;
        this.iconPaths = null;
    }

    /**
     * The local used to parse apk
     */
    public Locale getPreferredLocale() {
        return this.preferredLocale;
    }

    /**
     * The locale preferred. Will cause getManifestXml / getApkMeta to return different values.
     * The default value is from os default locale setting.
     */
    public void setPreferredLocale(final Locale preferredLocale) {
        if (!Objects.equals(this.preferredLocale, preferredLocale)) {
            this.preferredLocale = preferredLocale;
            this.manifestXml = null;
            this.apkMeta = null;
            this.manifestParsed = false;
        }
    }

    /**
     * Create ApkSignBlockParser for this apk file.
     *
     * @return null if do not have sign block
     */
    @Nullable
    protected ByteBuffer findApkSignBlock() throws IOException {
        final ByteBuffer buffer = this.fileData().order(ByteOrder.LITTLE_ENDIAN);
        final int len = buffer.limit();
        // first find zip end of central directory entry
        if (len < 22) {
            // should not happen
            throw new RuntimeException("Not zip file");
        }
        final int maxEOCDSize = 1024 * 100;
        EOCD eocd = null;
        for (int i = len - 22; i > Math.max(0, len - maxEOCDSize); i--) {
            final int v = buffer.getInt(i);
            if (v == EOCD.SIGNATURE) {
                Buffers.position(buffer, i + 4);
                eocd = new EOCD();
                eocd.setDiskNum(Buffers.readUShort(buffer));
                eocd.setCdStartDisk(Buffers.readUShort(buffer));
                eocd.setCdRecordNum(Buffers.readUShort(buffer));
                eocd.setTotalCDRecordNum(Buffers.readUShort(buffer));
                eocd.setCdSize(Buffers.readUInt(buffer));
                eocd.setCdStart(Buffers.readUInt(buffer));
                eocd.setCommentLen(Buffers.readUShort(buffer));
            }
        }
        if (eocd == null) {
            return null;
        }
        final int magicStrLen = 16;
        final long cdStart = eocd.getCdStart();
        // find apk sign block
        Buffers.position(buffer, cdStart - magicStrLen);
        final String magic = Buffers.readAsciiString(buffer, magicStrLen);
        if (!magic.equals(ApkSigningBlock.MAGIC)) {
            return null;
        }
        Buffers.position(buffer, cdStart - 24);
        final int blockSize = Unsigned.ensureUInt(buffer.getLong());
        Buffers.position(buffer, cdStart - blockSize - 8);
        final long size2 = Unsigned.ensureULong(buffer.getLong());
        if (blockSize != size2) {
            return null;
        }
        // now at the start of signing block
        return Buffers.sliceAndSkip(buffer, blockSize - magicStrLen);
    }

}
