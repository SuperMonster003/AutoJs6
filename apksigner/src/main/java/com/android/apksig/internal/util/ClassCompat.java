/*
 * Copyright (C) 2020 Muntashir Al-Islam
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

package com.android.apksig.internal.util;

import java.lang.annotation.Annotation;
import java.util.Objects;

public class ClassCompat {
    public static <A extends Annotation> A getDeclaredAnnotation(Class<?> containerClass,
                                                                 Class<A> annotationClass) {
        Objects.requireNonNull(annotationClass);
        Objects.requireNonNull(containerClass);
        // Loop over all directly-present annotations looking for a matching one
        for (Annotation annotation : containerClass.getDeclaredAnnotations()) {
            if (annotationClass.equals(annotation.annotationType())) {
                // More robust to do a dynamic cast at runtime instead
                // of compile-time only.
                return annotationClass.cast(annotation);
            }
        }
        return null;
    }
}
