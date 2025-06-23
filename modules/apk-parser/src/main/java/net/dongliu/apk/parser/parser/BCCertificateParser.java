package net.dongliu.apk.parser.parser;

import androidx.annotation.NonNull;

import net.dongliu.apk.parser.bean.CertificateMeta;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.Store;

import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parser certificate info using BouncyCastle.
 *
 * @author dongliu
 */
class BCCertificateParser extends CertificateParser {

    private static final Provider provider = new BouncyCastleProvider();

    public BCCertificateParser(@NonNull final byte[] data) {
        super(data);
    }

    /**
     * get certificate info
     */
    @Override
    @SuppressWarnings("unchecked")
    @NonNull
    public List<CertificateMeta> parse() throws CertificateException {
        final CMSSignedData cmsSignedData;
        try {
            cmsSignedData = new CMSSignedData(this.data);
        } catch (final CMSException e) {
            throw new CertificateException(e);
        }
        final Store<X509CertificateHolder> certStore = cmsSignedData.getCertificates();
        final SignerInformationStore signerInfos = cmsSignedData.getSignerInfos();
        final Collection<SignerInformation> signers = signerInfos.getSigners();
        final List<X509Certificate> certificates = new ArrayList<>();
        for (final SignerInformation signer : signers) {
            final SignerId sid = signer.getSID();
            final Collection<X509CertificateHolder> matches = certStore.getMatches(sid);
            for (final X509CertificateHolder holder : matches) {
                certificates.add(new JcaX509CertificateConverter().setProvider(BCCertificateParser.provider)
                        .getCertificate(holder));
            }
        }
        return CertificateMetas.from(certificates);
    }

}
