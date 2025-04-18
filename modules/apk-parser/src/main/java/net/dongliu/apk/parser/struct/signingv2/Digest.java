package net.dongliu.apk.parser.struct.signingv2;

import androidx.annotation.NonNull;

public class Digest {
    public final int algorithmID;
    public final byte[] value;

    public Digest(final int algorithmID, final @NonNull byte[] value) {
        this.algorithmID = algorithmID;
        this.value = value;
    }
}
