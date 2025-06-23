/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dongliu.apk.parser.cert.pkcs7;

import net.dongliu.apk.parser.cert.asn1.Asn1Class;
import net.dongliu.apk.parser.cert.asn1.Asn1Field;
import net.dongliu.apk.parser.cert.asn1.Asn1OpaqueObject;
import net.dongliu.apk.parser.cert.asn1.Asn1Tagging;
import net.dongliu.apk.parser.cert.asn1.Asn1Type;

/**
 * PKCS #7 {@code ContentInfo} as specified in RFC 5652.
 */
@Asn1Class(type = Asn1Type.Sequence)
public class ContentInfo {

    @Asn1Field(index = 1, type = Asn1Type.ObjectIdentifier)
    public String contentType;

    @Asn1Field(index = 2, type = Asn1Type.Any, tagging = Asn1Tagging.Explicit, tagNumber = 0)
    public Asn1OpaqueObject content;
}
