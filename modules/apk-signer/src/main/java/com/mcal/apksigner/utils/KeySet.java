package com.mcal.apksigner.utils;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class KeySet {
    String name;
    X509Certificate publicKey = null;
    PrivateKey privateKey = null;
    byte[] sigBlockTemplate = null;

    String signatureAlgorithm = "SHA1withRSA";

    public KeySet() {
    }

    public KeySet(String name, X509Certificate publicKey, PrivateKey privateKey, byte[] sigBlockTemplate) {
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public KeySet(String name, X509Certificate publicKey, PrivateKey privateKey, String signatureAlgorithm, byte[] sigBlockTemplate) {
        this.name = name;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        if (signatureAlgorithm != null) {
            this.signatureAlgorithm = signatureAlgorithm;
        }
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public X509Certificate getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(X509Certificate publicKey) {
        this.publicKey = publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public byte[] getSigBlockTemplate() {
        return sigBlockTemplate;
    }

    public void setSigBlockTemplate(byte[] sigBlockTemplate) {
        this.sigBlockTemplate = sigBlockTemplate;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        if (signatureAlgorithm == null) {
            this.signatureAlgorithm = "SHA1withRSA";
        } else {
            this.signatureAlgorithm = signatureAlgorithm;
        }
    }
}
