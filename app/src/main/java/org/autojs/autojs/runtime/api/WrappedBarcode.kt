package org.autojs.autojs.runtime.api

import android.graphics.Point
import android.graphics.Rect
import org.autojs.autojs.runtime.api.Barcode.Companion.FORMAT_NAME_UNKNOWN
import org.autojs.autojs.runtime.api.Barcode.Companion.TYPE_NAME_UNKNOWN
import org.autojs.plugin.mlkit.barcode.api.BarcodeFormats
import org.autojs.plugin.mlkit.barcode.api.BarcodeResult
import org.autojs.plugin.mlkit.barcode.api.BarcodeResultExtraKeys
import org.autojs.plugin.mlkit.barcode.api.BarcodeValueTypes
import org.json.JSONObject

class WrappedBarcode(val barcode: BarcodeResult) : IWrappedBarcode {

    val value
        get() = getDisplayValue()

    override fun getFormat(): Int = barcode.format

    override fun getFormatName(): String = normalizeFormatName(barcode.formatName, barcode.format)

    override fun getValueType(): Int = barcode.valueType

    override fun getType(): Int = getValueType()

    override fun getValueTypeName(): String = getTypeName()

    override fun getTypeName(): String = normalizeTypeName(barcode.valueTypeName, barcode.valueType)

    override fun getBoundingBox(): Rect? = barcode.boundingBox

    override fun getCalendarEvent(): Any? = structuredPayload("calendarEvent")

    override fun getContactInfo(): Any? = structuredPayload("contactInfo")

    override fun getDriverLicense(): Any? = structuredPayload("driverLicense")

    override fun getEmail(): Any? = structuredPayload("email")

    override fun getGeoPoint(): Any? = structuredPayload("geoPoint")

    override fun getPhone(): Any? = structuredPayload("phone")

    override fun getSms(): Any? = structuredPayload("sms")

    override fun getUrl(): Any? = structuredPayload("url")

    override fun getWifi(): Any? = structuredPayload("wifi")

    override fun getDisplayValue(): String? = barcode.displayValue

    override fun getRawValue(): String? = barcode.rawValue

    override fun getRawBytes(): ByteArray? = barcode.rawBytes

    override fun getCornerPoints(): Array<Point>? {
        val points = barcode.cornerPoints ?: return null
        if (points.isEmpty()) return emptyArray()
        return points
            .asSequence()
            .chunked(2)
            .filter { it.size == 2 }
            .map { (x, y) -> Point(x, y) }
            .toList()
            .toTypedArray()
    }

    override fun toString(): String = listOf(
        "[" + getFormatName().replaceFirst(Regex("^FORMAT_"), "") + "]",
        "<" + getValueTypeName().replaceFirst(Regex("^TYPE_"), "") + ">",
        getDisplayValue(),
    ).joinToString("\u0020")

    private fun structuredPayload(name: String): Any? {
        val json = barcode.extras?.getString(BarcodeResultExtraKeys.STRUCTURED_JSON) ?: return null
        return runCatching {
            JSONObject(json).optJSONObject(name)
        }.getOrNull()
    }

    private fun normalizeFormatName(name: String?, value: Int): String {
        val normalized = name?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("FORMAT_")) it else "FORMAT_$it"
        }
        return normalized
            ?: BarcodeFormats.NAME_BY_VALUE[value]?.let { "FORMAT_$it" }
            ?: FORMAT_NAME_UNKNOWN
    }

    private fun normalizeTypeName(name: String?, value: Int): String {
        val normalized = name?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("TYPE_")) it else "TYPE_$it"
        }
        return normalized
            ?: BarcodeValueTypes.NAME_BY_VALUE[value]?.let { "TYPE_$it" }
            ?: TYPE_NAME_UNKNOWN
    }
}
