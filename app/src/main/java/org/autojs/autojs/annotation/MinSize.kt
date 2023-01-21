package org.autojs.autojs.annotation

/**
 * Created by SuperMonster003 on Oct 1, 2022.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class MinSize(val value: Int)
