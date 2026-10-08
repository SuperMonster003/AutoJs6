package org.autojs.plugin.compose.api.model

import android.os.Parcel
import android.os.Parcelable
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits
import java.util.Collections

/** Value equality is independent of collection implementation and never exposes mutable backing collections. */
sealed class ComposeParcelable : Parcelable {
    internal abstract fun fields(): List<Any?>
    final override fun describeContents() = 0
    final override fun writeToParcel(destination: Parcel, flags: Int) = ComposeParcel.write(destination, this, flags)
    final override fun equals(other: Any?): Boolean = this === other ||
        (other is ComposeParcelable && javaClass == other.javaClass && fields() == other.fields())
    final override fun hashCode() = 31 * javaClass.name.hashCode() + fields().hashCode()
    // Do not accidentally expose script UI text through diagnostic object rendering.
    final override fun toString(): String = javaClass.simpleName
}

internal fun invalid(message: String): Nothing = throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, message)
internal fun limit(condition: Boolean, message: String) {
    if (!condition) throw ComposeUiContractException(ComposeUiErrorCodes.LIMIT_EXCEEDED, message)
}
internal fun text(value: String): String = value.also {
    limit(it.length <= ComposeUiLimits.MAX_STRING_CHARS, "String exceeds contract limit")
}
internal fun name(value: String): String = text(value).also { if (it.isBlank()) invalid("Empty contract name") }
internal fun positive(value: Int): Int = value.also { if (it <= 0) invalid("Identifier must be positive") }
internal fun nonnegative(value: Long): Long = value.also { if (it < 0) invalid("Value must be nonnegative") }
internal fun finite(value: Double): Double = value.also { if (!it.isFinite()) invalid("Number must be finite") }
internal fun <T> frozenList(values: Collection<T>, maximum: Int): List<T> {
    limit(values.size <= maximum, "Collection exceeds contract limit")
    return Collections.unmodifiableList(ArrayList(values))
}
internal fun <T> frozenMap(values: Map<String, T>, maximum: Int): Map<String, T> {
    limit(values.size <= maximum, "Map exceeds contract limit")
    values.keys.forEach(::name)
    return Collections.unmodifiableMap(LinkedHashMap(values))
}
internal fun <T> frozenSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
internal inline fun <reified T : ComposeParcelable> creator(): Parcelable.Creator<T> = object : Parcelable.Creator<T> {
    override fun createFromParcel(source: Parcel): T = ComposeParcel.read(source) as? T ?: invalid("Unexpected parcel type")
    override fun newArray(size: Int): Array<T?> = arrayOfNulls(size)
}
