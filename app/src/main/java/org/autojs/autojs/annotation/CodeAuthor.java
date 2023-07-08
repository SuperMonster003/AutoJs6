package org.autojs.autojs.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by SuperMonster003 on Jun 3, 2023.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.FIELD})
public @interface CodeAuthor {

    String name();

    String homepage();

}
