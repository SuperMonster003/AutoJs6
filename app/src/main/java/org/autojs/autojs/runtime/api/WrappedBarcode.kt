package org.autojs.autojs.runtime.api

import android.graphics.Point
import android.graphics.Rect
import org.autojs.autojs.runtime.api.Barcode.Companion.FORMAT_NAME_UNKNOWN
import org.autojs.autojs.runtime.api.Barcode.Companion.TYPE_NAME_UNKNOWN
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import com.google.mlkit.vision.barcode.common.Barcode as MLKitBarcode


class WrappedBarcode(val barcode: MLKitBarcode) : IWrappedBarcode {

    override fun getFormat(): Int = barcode.format

    override fun getFormatName(): String {
        val declaredFields: Array<Field> = MLKitBarcode::class.java.declaredFields
        for (field in declaredFields) {
            if (Modifier.isStatic(field.modifiers)) {
                if (field.type == Int::class.java && field.name.startsWith("FORMAT_")) {
                    if (field.getInt(barcode) == barcode.format) {
                        return field.name
                    }
                }
            }
        }
        return FORMAT_NAME_UNKNOWN
    }

    override fun getValueType(): Int = barcode.valueType

    override fun getType(): Int = valueType

    override fun getValueTypeName(): String = getTypeName()

    override fun getTypeName(): String {
        val declaredFields: Array<Field> = MLKitBarcode::class.java.declaredFields
        for (field in declaredFields) {
            if (Modifier.isStatic(field.modifiers)) {
                if (field.type == Int::class.java && field.name.startsWith("TYPE_")) {
                    if (field.getInt(barcode) == barcode.valueType) {
                        return field.name
                    }
                }
            }
        }
        return TYPE_NAME_UNKNOWN
    }

    override fun getBoundingBox(): Rect? = barcode.boundingBox

    override fun getCalendarEvent(): MLKitBarcode.CalendarEvent? = barcode.calendarEvent

    override fun getContactInfo(): MLKitBarcode.ContactInfo? = barcode.contactInfo

    override fun getDriverLicense(): MLKitBarcode.DriverLicense? = barcode.driverLicense

    override fun getEmail(): MLKitBarcode.Email? = barcode.email

    override fun getGeoPoint(): MLKitBarcode.GeoPoint? = barcode.geoPoint

    override fun getPhone(): MLKitBarcode.Phone? = barcode.phone

    override fun getSms(): MLKitBarcode.Sms? = barcode.sms

    override fun getUrl(): MLKitBarcode.UrlBookmark? = barcode.url

    override fun getWifi(): MLKitBarcode.WiFi? = barcode.wifi

    override fun getDisplayValue(): String? = barcode.displayValue

    override fun getRawValue(): String? = barcode.rawValue

    override fun getRawBytes(): ByteArray? = barcode.rawBytes

    override fun getCornerPoints(): Array<Point>? = barcode.cornerPoints

}
