package org.autojs.plugin.compose.api

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.autojs.plugin.compose.api.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeUiParcelTest {
    private fun roundTrip(value: ComposeParcelable): ComposeParcelable {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            assertEquals(0x43554931, parcel.readInt())
            assertEquals(1, parcel.readInt())
            assertEquals(parcel.dataSize() - 12, parcel.readInt())
            assertEquals(WIRE_TAGS.getValue(value.javaClass.simpleName), parcel.readInt())
            parcel.setDataSize(0); parcel.setDataPosition(0)
            parcel.writeParcelable(value, 0)
            parcel.writeInt(0x12345678)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            val restored = requireNotNull(parcel.readParcelable<ComposeParcelable>(ComposeParcelable::class.java.classLoader))
            assertEquals(value, restored)
            assertEquals(0x12345678, parcel.readInt())
            assertEquals(0, parcel.dataAvail())
            return restored
        } finally { parcel.recycle() }
    }

    private fun fails(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: ComposeUiContractException) { assertEquals(code, failure.code) }
    }

    @Test fun everyValueVariantPreservesItsTypeAndContents() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val values = listOf(UiValue.Str("hello 你好 \uD83D\uDC4B"), UiValue.Num(0.125), UiValue.Bool(true),
                UiValue.Color(0x80ff3300.toInt()), UiValue.Dp(-3.5), UiValue.Sp(18.0), UiValue.Enum("center"),
                UiValue.TextStyle("sans-serif", 18.0, 700, true, 22.0, -0.5), UiValue.IconName("Home"),
                UiValue.Shape(ShapeKind.ROUNDED, 12.0), UiValue.ListOf(listOf(UiValue.Num(1.0), UiValue.Null)),
                UiValue.Null, UiValue.BitmapRef(bitmap))
            values.forEach(::roundTrip)
        } finally { bitmap.recycle() }
    }

    @Test fun treeAllPatchVariantsCommandsAndThemeRoundTrip() {
        val child = UiNode(2, ComposeUiComponents.TEXT, key = "text", props = mapOf("text" to UiValue.Str("value")),
            modifier = listOf(ModifierOp("padding", listOf(UiValue.Dp(4.0)))), callbacks = mapOf("click" to 7))
        val root = UiNode(1, ComposeUiComponents.SCAFFOLD, children = listOf(2), slots = mapOf("topBar" to 3))
        val tree = UiTree(1, listOf(root, child, UiNode(3, "Text")))
        roundTrip(root); roundTrip(child); roundTrip(tree)
        roundTrip(ModifierOp("padding", listOf(UiValue.Dp(4.0))))
        roundTrip(TextSelection(3, 1))
        val branch = UiTree(4, listOf(UiNode(4, "Text")))
        val patches = listOf(UiPatch.SetProps(2, mapOf("text" to UiValue.Str("next"))),
            UiPatch.SetProps(2, emptyMap(), emptyList(), emptyMap()), UiPatch.Insert(1, 0, branch),
            UiPatch.Remove(1, 2), UiPatch.Move(1, 2, 0), UiPatch.ReplaceSlot(1, "title", branch),
            UiPatch.ReplaceSlot(1, "title", null), UiPatch.SetRoot(tree))
        patches.forEach(::roundTrip)
        roundTrip(UiPatchBatch(3, 0x1ffffffffL, patches))
        listOf(UiCommand.Focus(2), UiCommand.Blur(2), UiCommand.ScrollTo(1, index = 0, offset = -2),
            UiCommand.ScrollTo(1, offset = 40), UiCommand.Edit(2, "abc", TextSelection(3, 1), 9),
            UiCommand.Edit(2, selection = TextSelection(0, 0), editSeq = 10),
            UiCommand.ShowSnackbar("message", "action", SnackbarDuration.INDEFINITE, 8)).forEach(::roundTrip)
        roundTrip(ThemeSpec(0xff00aa88.toInt(), mapOf("primary" to 0xff112233.toInt()), null, true, "serif", 1.25))
        roundTrip(ThemeSpec())
    }

    @Test fun eventPayloadIsCopiedInBothDirectionsAndRejectsMutableValues() {
        val input = Bundle().apply { putString("text", "first"); putInt("selectionStart", 0); putLong("editSeq", 9); putFloat("value", 0.5f); putBoolean("checked", true) }
        val event = UiEvent(1, 2, 3, "valueChange", 4, input)
        input.putString("text", "changed")
        assertEquals("first", event.payload.getString("text"))
        event.payload.putString("text", "changed again")
        assertEquals("first", event.payload.getString("text"))
        assertEquals(0.5, event.payload.getDouble("value"), 0.0)
        val restored = roundTrip(event) as UiEvent
        restored.payload.clear()
        assertEquals("first", restored.payload.getString("text"))
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { UiEvent(1, 0, 1, "click", 1, Bundle().apply { putIntArray("unsafe", intArrayOf(1)) }) }
    }

    @Test fun bitmapReferencesRemainBorrowedAndCannotCrossProcesses() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val ref = UiValue.BitmapRef(bitmap)
        val restored = roundTrip(ref) as UiValue.BitmapRef
        assertSame(bitmap, restored.get())
        assertEquals(ref, UiValue.BitmapRef(bitmap))
        assertFalse(bitmap.isRecycled)
        bitmap.recycle()
        assertNull(ref.get())
        fails(ComposeUiErrorCodes.INVALID_ARGUMENT) { roundTrip(ref) }
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(9); writeString("different-process"); writeLong(1) }
    }

    @Test fun malformedFramesAndOversizedCountsFailBeforeAllocation() {
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(9999) }
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(12); writeInt(-1) }
        malformed(ComposeUiErrorCodes.LIMIT_EXCEEDED) { writeInt(12); writeInt(Int.MAX_VALUE) }
        malformed(ComposeUiErrorCodes.LIMIT_EXCEEDED) { writeInt(1); writeInt(ComposeUiLimits.MAX_STRING_CHARS + 1) }
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(3); writeInt(2) }
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(1); writeString(null) }
        malformed(ComposeUiErrorCodes.INVALID_ARGUMENT) { writeInt(13); writeInt(123) }
        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(0x43554931); parcel.writeInt(2); parcel.writeInt(4); parcel.writeInt(13); parcel.setDataPosition(0)
            fails(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE) { UiValue.CREATOR.createFromParcel(parcel) }
        } finally { parcel.recycle() }
    }

    @Test fun parcelByteBudgetRejectsRepeatedLargeTextWithoutChangingTheModel() {
        val value = UiValue.Str("a".repeat(ComposeUiLimits.MAX_STRING_CHARS))
        val children = (2..80).map { UiNode(it, "Text", props = mapOf("text" to value)) }
        val tree = UiTree(1, listOf(UiNode(1, "Column", children = children.map { it.nodeId })) + children)
        val parcel = Parcel.obtain()
        try { fails(ComposeUiErrorCodes.LIMIT_EXCEEDED) { tree.writeToParcel(parcel, 0) } }
        finally { parcel.recycle() }
        assertEquals(80, tree.nodes.size)
    }

    private fun malformed(code: String, body: Parcel.() -> Unit) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInt(0x43554931); parcel.writeInt(1); parcel.writeInt(0)
            body(parcel)
            val end = parcel.dataPosition()
            parcel.setDataPosition(8); parcel.writeInt(end - 12); parcel.setDataPosition(0)
            fails(code) { UiValue.CREATOR.createFromParcel(parcel) }
        } finally { parcel.recycle() }
    }

    companion object {
        private val WIRE_TAGS = mapOf("Str" to 1, "Num" to 2, "Bool" to 3, "Color" to 4, "Dp" to 5, "Sp" to 6,
            "Enum" to 7, "TextStyle" to 8, "BitmapRef" to 9, "IconName" to 10, "Shape" to 11, "ListOf" to 12,
            "Null" to 13, "ModifierOp" to 20, "UiNode" to 21, "UiTree" to 22, "ThemeSpec" to 23,
            "SetProps" to 30, "Insert" to 31, "Remove" to 32, "Move" to 33, "ReplaceSlot" to 34, "SetRoot" to 35,
            "UiPatchBatch" to 36, "UiEvent" to 40, "TextSelection" to 41, "Focus" to 50, "Blur" to 51,
            "ScrollTo" to 52, "Edit" to 53, "ShowSnackbar" to 54)
    }
}
