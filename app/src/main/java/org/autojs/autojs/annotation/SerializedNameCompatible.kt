package org.autojs.autojs.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class SerializedNameCompatible(vararg val with: With) {
    annotation class With(val value: String, val target: Array<String> = ["AutoJs6"], val isReversed: Boolean = false)
}