package net.dongliu.apk.parser.struct.signingv2;

import androidx.annotation.NonNull;

public class Signature {
    public final int algorithmID;
    public final byte[] data;

    public Signature(final int algorithmID, final @NonNull byte[] data) {
        this.algorithmID = algorithmID;
        this.data = data;
    }

}
