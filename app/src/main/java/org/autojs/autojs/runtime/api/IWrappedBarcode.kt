package org.autojs.autojs.runtime.api

import android.graphics.Point
import android.graphics.Rect

interface IWrappedBarcode {

    fun getFormat(): Int

    fun getFormatName(): String

    fun getValueType(): Int

    fun getValueTypeName(): String

    fun getType(): Int

    fun getTypeName(): String

    fun getBoundingBox(): Rect?

    fun getCalendarEvent(): Any?

    fun getContactInfo(): Any?

    fun getDriverLicense(): Any?

    fun getEmail(): Any?

    fun getGeoPoint(): Any?

    fun getPhone(): Any?

    fun getSms(): Any?

    fun getUrl(): Any?

    fun getWifi(): Any?

    fun getDisplayValue(): String?

    fun getRawValue(): String?

    fun getRawBytes(): ByteArray?

    fun getCornerPoints(): Array<Point>?

}
