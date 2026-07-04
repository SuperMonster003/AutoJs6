package org.autojs.plugin.mlkit.barcode.api

object BarcodeValueTypes {
    const val TYPE_UNKNOWN = 0
    const val TYPE_CONTACT_INFO = 1
    const val TYPE_EMAIL = 2
    const val TYPE_ISBN = 3
    const val TYPE_PHONE = 4
    const val TYPE_PRODUCT = 5
    const val TYPE_SMS = 6
    const val TYPE_TEXT = 7
    const val TYPE_URL = 8
    const val TYPE_WIFI = 9
    const val TYPE_GEO = 10
    const val TYPE_CALENDAR_EVENT = 11
    const val TYPE_DRIVER_LICENSE = 12

    const val TYPE_NAME_UNKNOWN = "TYPE_NAME_UNKNOWN"

    val NAME_BY_VALUE: Map<Int, String> = mapOf(
        TYPE_UNKNOWN to "UNKNOWN",
        TYPE_CONTACT_INFO to "CONTACT_INFO",
        TYPE_EMAIL to "EMAIL",
        TYPE_ISBN to "ISBN",
        TYPE_PHONE to "PHONE",
        TYPE_PRODUCT to "PRODUCT",
        TYPE_SMS to "SMS",
        TYPE_TEXT to "TEXT",
        TYPE_URL to "URL",
        TYPE_WIFI to "WIFI",
        TYPE_GEO to "GEO",
        TYPE_CALENDAR_EVENT to "CALENDAR_EVENT",
        TYPE_DRIVER_LICENSE to "DRIVER_LICENSE",
    )
}
