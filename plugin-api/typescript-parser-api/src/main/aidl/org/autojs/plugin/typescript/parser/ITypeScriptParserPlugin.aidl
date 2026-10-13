package org.autojs.plugin.typescript.parser;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface ITypeScriptParserPlugin {

    // The transaction order is frozen; new methods are append-only.
    int getContractVersion();

    String getContractHash();

    Bundle getParserInfo();

    // Parses one source file. The UTF-8 JSON syntax tree is written to treeOutput, a writable
    // descriptor owned by the caller (normally a file in its cache directory); the returned Bundle
    // carries the outcome, the tree's byte length and SHA-256, or an error code.
    Bundle parse(in Bundle request, in ParcelFileDescriptor treeOutput);
}
