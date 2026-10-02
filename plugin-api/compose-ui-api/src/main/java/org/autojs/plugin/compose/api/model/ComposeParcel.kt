package org.autojs.plugin.compose.api.model

import android.os.Parcel
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiLimits as L

/** Framed V1 transport, independent of Parcelable class-name loading for nested values. */
internal object ComposeParcel {
    private const val MAGIC = 0x43554931
    private const val VERSION = 1

    fun write(parcel: Parcel, value: ComposeParcelable, flags: Int) {
        parcel.writeInt(MAGIC)
        parcel.writeInt(VERSION)
        val lengthPosition = parcel.dataPosition()
        parcel.writeInt(0)
        val start = parcel.dataPosition()
        Writer(parcel, start).value(value)
        val end = parcel.dataPosition()
        parcel.setDataPosition(lengthPosition)
        parcel.writeInt(end - start)
        parcel.setDataPosition(end)
    }

    fun read(parcel: Parcel): ComposeParcelable = try {
        if (parcel.dataAvail() < 12 || parcel.readInt() != MAGIC) invalid("Invalid Compose parcel header")
        if (parcel.readInt() != VERSION) throw ComposeUiContractException(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE, "Unsupported Compose parcel version")
        val size = parcel.readInt()
        limit(size <= L.MAX_PARCEL_BYTES, "Compose parcel exceeds byte limit")
        if (size < 4 || size > parcel.dataAvail()) invalid("Truncated Compose parcel")
        val end = parcel.dataPosition() + size
        Reader(parcel, end).value(0).also {
            if (parcel.dataPosition() != end) invalid("Unexpected trailing Compose parcel data")
        }
    } catch (failure: ComposeUiContractException) {
        throw failure
    } catch (failure: RuntimeException) {
        throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "Malformed Compose parcel", cause = failure)
    }

    private class Writer(val out: Parcel, val start: Int) {
        private fun room(bytes: Int) = limit(out.dataPosition() - start <= L.MAX_PARCEL_BYTES - bytes, "Compose parcel exceeds byte limit")
        fun int(value: Int) { room(4); out.writeInt(value) }
        fun long(value: Long) { room(8); out.writeLong(value) }
        fun number(value: Double) { room(8); out.writeDouble(value) }
        fun bool(value: Boolean) = int(if (value) 1 else 0)
        fun string(value: String?) {
            value?.let(::text)
            room(if (value == null) 4 else 4 + ((value.length + 1) * 2 + 3) / 4 * 4)
            out.writeString(value)
        }
        fun <T> optional(value: T?, write: (T) -> Unit) { bool(value != null); if (value != null) write(value) }
        fun <T> list(values: List<T>, write: (T) -> Unit) { int(values.size); values.forEach(write) }
        fun <T> map(values: Map<String, T>, write: (T) -> Unit) {
            int(values.size)
            values.toSortedMap().forEach { (key, value) -> string(key); write(value) }
        }
        fun payload(value: Any?) {
            when (value) {
                null -> int(0)
                is String -> { int(1); string(value) }
                is Boolean -> { int(2); bool(value) }
                is Int -> { int(3); int(value) }
                is Long -> { int(4); long(value) }
                is Double -> { int(5); number(value) }
                else -> invalid("Unsupported event payload value")
            }
        }
        fun value(v: ComposeParcelable) {
            when (v) {
                is UiValue.Str -> { int(1); string(v.value) }
                is UiValue.Num -> { int(2); number(v.value) }
                is UiValue.Bool -> { int(3); bool(v.value) }
                is UiValue.Color -> { int(4); int(v.argb) }
                is UiValue.Dp -> { int(5); number(v.value) }
                is UiValue.Sp -> { int(6); number(v.value) }
                is UiValue.Enum -> { int(7); string(v.value) }
                is UiValue.TextStyle -> {
                    int(8); string(v.fontFamily); optional(v.fontSize, ::number); optional(v.fontWeight, ::int)
                    optional(v.italic, ::bool); optional(v.lineHeight, ::number); optional(v.letterSpacing, ::number)
                }
                is UiValue.BitmapRef -> {
                    if (v.get() == null) invalid("BitmapRef owner is no longer available")
                    int(9); string(BitmapHandles.processToken); long(v.handle)
                }
                is UiValue.IconName -> { int(10); string(v.value) }
                is UiValue.Shape -> { int(11); string(v.shape.name); number(v.radius) }
                is UiValue.ListOf -> { int(12); list(v.values, ::value) }
                UiValue.Null -> int(13)
                is ModifierOp -> { int(20); string(v.name); list(v.args, ::value); string(v.scope.name) }
                is UiNode -> {
                    int(21); int(v.nodeId); string(v.type); string(v.key); map(v.props, ::value)
                    list(v.modifier, ::value); list(v.children, ::int); map(v.slots, ::int); map(v.callbacks, ::int)
                }
                is UiTree -> { int(22); int(v.rootId); list(v.nodes, ::value) }
                is ThemeSpec -> {
                    int(23); optional(v.seedArgb, ::int); map(v.colorOverrides, ::int); optional(v.dark, ::bool)
                    bool(v.dynamicColor); string(v.fontFamily); optional(v.fontScale, ::number)
                }
                is UiPatch.SetProps -> {
                    int(30); int(v.nodeId); map(v.props, ::value)
                    optional(v.modifier) { list(it, ::value) }; optional(v.callbacks) { map(it, ::int) }
                }
                is UiPatch.Insert -> { int(31); int(v.parentId); int(v.index); value(v.subtree) }
                is UiPatch.Remove -> { int(32); int(v.parentId); int(v.nodeId) }
                is UiPatch.Move -> { int(33); int(v.parentId); int(v.nodeId); int(v.toIndex) }
                is UiPatch.ReplaceSlot -> { int(34); int(v.parentId); string(v.slot); optional(v.subtree, ::value) }
                is UiPatch.SetRoot -> { int(35); value(v.tree) }
                is UiPatchBatch -> { int(36); int(v.sessionId); long(v.generation); list(v.patches, ::value) }
                is UiEvent -> { int(40); int(v.sessionId); long(v.generation); int(v.nodeId); string(v.type); int(v.callbackId); map(v.payloadValues, ::payload) }
                is TextSelection -> { int(41); int(v.start); int(v.end) }
                is UiCommand.Focus -> { int(50); int(v.nodeId) }
                is UiCommand.Blur -> { int(51); int(v.nodeId) }
                is UiCommand.ScrollTo -> { int(52); int(v.nodeId); optional(v.index, ::int); optional(v.offset, ::int) }
                is UiCommand.Edit -> { int(53); int(v.nodeId); string(v.text); optional(v.selection, ::value); long(v.editSeq) }
                is UiCommand.ShowSnackbar -> { int(54); string(v.message); string(v.actionLabel); string(v.duration.name); optional(v.callbackId, ::int) }
            }
        }
    }

    private class Reader(val input: Parcel, val end: Int) {
        fun room(bytes: Int) { if (bytes < 0 || input.dataPosition() > end - bytes) invalid("Truncated Compose parcel field") }
        fun int(): Int { room(4); return input.readInt() }
        fun long(): Long { room(8); return input.readLong() }
        fun number(): Double { room(8); return finite(input.readDouble()) }
        fun bool(): Boolean = when (int()) { 0 -> false; 1 -> true; else -> invalid("Invalid Boolean encoding") }
        fun nullableString(): String? {
            val start = input.dataPosition()
            val length = int()
            if (length < -1) invalid("Invalid string length")
            limit(length <= L.MAX_STRING_CHARS, "String exceeds contract limit")
            if (length >= 0) room(((length + 1) * 2 + 3) / 4 * 4)
            input.setDataPosition(start)
            val result = input.readString()
            if (length >= 0 && (result == null || result.length != length)) invalid("Malformed string field")
            return result
        }
        fun string(): String = nullableString() ?: invalid("Unexpected null string")
        inline fun <reified T : kotlin.Enum<T>> enumValue(): T {
            val name = string()
            return enumValues<T>().firstOrNull { it.name == name } ?: invalid("Unknown enum value")
        }
        fun <T> optional(read: () -> T): T? = if (bool()) read() else null
        fun count(maximum: Int): Int = int().also {
            if (it < 0) invalid("Negative collection count")
            limit(it <= maximum, "Collection exceeds contract limit")
        }
        fun <T> list(maximum: Int, read: () -> T): List<T> = List(count(maximum)) { read() }
        fun <T> map(maximum: Int, read: () -> T): Map<String, T> {
            val count = count(maximum)
            val result = LinkedHashMap<String, T>()
            repeat(count) {
                val key = string()
                if (result.containsKey(key)) invalid("Duplicate map key")
                result[key] = read()
            }
            return result
        }
        inline fun <reified T : ComposeParcelable> typed(depth: Int): T = value(depth) as? T ?: invalid("Wrong nested parcel type")
        fun payload(): Any? = when (int()) {
            0 -> null
            1 -> string()
            2 -> bool()
            3 -> int()
            4 -> long()
            5 -> number()
            else -> invalid("Unknown event scalar type")
        }
        fun value(depth: Int): ComposeParcelable {
            limit(depth <= L.MAX_DEPTH + L.MAX_VALUE_DEPTH, "Parcel nesting exceeds contract limit")
            val d = depth + 1
            return when (int()) {
                1 -> UiValue.Str(string())
                2 -> UiValue.Num(number())
                3 -> UiValue.Bool(bool())
                4 -> UiValue.Color(int())
                5 -> UiValue.Dp(number())
                6 -> UiValue.Sp(number())
                7 -> UiValue.Enum(string())
                8 -> UiValue.TextStyle(nullableString(), optional(::number), optional(::int), optional(::bool), optional(::number), optional(::number))
                9 -> BitmapHandles.restore(string(), long())
                10 -> UiValue.IconName(string())
                11 -> UiValue.Shape(enumValue(), number())
                12 -> UiValue.ListOf(list(L.MAX_VALUE_ITEMS) { typed<UiValue>(d) })
                13 -> UiValue.Null
                20 -> ModifierOp(string(), list(L.MAX_MODIFIER_OPS) { typed<UiValue>(d) }, enumValue())
                21 -> UiNode(int(), string(), nullableString(), map(L.MAX_PROPERTIES_PER_NODE) { typed<UiValue>(d) },
                    list(L.MAX_MODIFIER_OPS) { typed<ModifierOp>(d) }, list(L.MAX_LAZY_CHILDREN_PER_NODE, ::int),
                    map(L.MAX_SLOTS_PER_NODE, ::int), map(L.MAX_PROPERTIES_PER_NODE, ::int))
                22 -> UiTree(int(), list(L.MAX_NODES_PER_SESSION) { typed<UiNode>(d) })
                23 -> ThemeSpec(optional(::int), map(L.MAX_THEME_COLORS, ::int), optional(::bool), bool(), nullableString(), optional(::number))
                30 -> UiPatch.SetProps(int(), map(L.MAX_PROPERTIES_PER_NODE) { typed<UiValue>(d) },
                    optional { list(L.MAX_MODIFIER_OPS) { typed<ModifierOp>(d) } }, optional { map(L.MAX_PROPERTIES_PER_NODE, ::int) })
                31 -> UiPatch.Insert(int(), int(), typed(d))
                32 -> UiPatch.Remove(int(), int())
                33 -> UiPatch.Move(int(), int(), int())
                34 -> UiPatch.ReplaceSlot(int(), string(), optional { typed<UiTree>(d) })
                35 -> UiPatch.SetRoot(typed(d))
                36 -> UiPatchBatch(int(), long(), list(L.MAX_PATCHES_PER_BATCH) { typed<UiPatch>(d) })
                40 -> UiEvent(int(), long(), int(), string(), int(), payloadBundle(map(L.MAX_EVENT_FIELDS, ::payload)))
                41 -> TextSelection(int(), int())
                50 -> UiCommand.Focus(int())
                51 -> UiCommand.Blur(int())
                52 -> UiCommand.ScrollTo(int(), optional(::int), optional(::int))
                53 -> UiCommand.Edit(int(), nullableString(), optional { typed<TextSelection>(d) }, long())
                54 -> UiCommand.ShowSnackbar(string(), nullableString(), enumValue(), optional(::int))
                else -> invalid("Unknown Compose parcel type")
            }
        }
    }
}
