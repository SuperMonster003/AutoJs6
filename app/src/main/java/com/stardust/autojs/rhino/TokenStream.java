/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.stardust.autojs.rhino;

import java.io.Reader;

public class TokenStream extends org.autojs.autojs.rhino.TokenStream {
    public TokenStream(Reader sourceReader, String sourceString, int lineno) {
        super(sourceReader, sourceString, lineno);
    }
}
