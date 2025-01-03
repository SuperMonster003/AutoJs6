/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.apksig.kms;

/** Represents an exception thrown by the external KMS. */
public class KmsException extends RuntimeException {
    private final String mKmsType;

    public KmsException(String kmsType, String message) {
        super(message);
        this.mKmsType = kmsType;
    }

    public KmsException(String kmsType, String message, Throwable cause) {
        super(message, cause);
        this.mKmsType = kmsType;
    }

    public KmsException(String kmsType, Throwable cause) {
        super(cause);
        this.mKmsType = kmsType;
    }

    @Override
    public String getMessage() {
        return "KMS " + mKmsType + " threw exception: " + super.getMessage();
    }
}
