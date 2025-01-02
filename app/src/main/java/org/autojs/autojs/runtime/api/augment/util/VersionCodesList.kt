package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.util.RhinoUtils.newNativeObject

object VersionCodesInfo {

    // @UpTo Jan 2, 2025.
    private val raw = mutableMapOf(
        "BAKLAVA" to listOf("Android 16", "Baklava", "16", "36", "Q2, 2025"),
        "VANILLA_ICE_CREAM" to listOf("Android 15", "Vanilla Ice Cream", "15", "35", "September 3, 2024"),
        "UPSIDE_DOWN_CAKE" to listOf("Android 14", "Upside Down Cake", "14", "34", "October 4, 2023"),
        "TIRAMISU" to listOf("Android 13", "Tiramisu", "13", "33", "August 15, 2022"),
        "S_V2" to listOf("Android 12L", "Snow Cone v2", "12.1", "32", "March 7, 2022"),
        "S" to listOf("Android 12", "Snow Cone", "12", "31", "October 4, 2021"),
        "R" to listOf("Android 11", "Red Velvet Cake", "11", "30", "September 8, 2020"),
        "Q" to listOf("Android 10", "Quince Tart", "10", "29", "September 3, 2019"),
        "P" to listOf("Android Pie", "Pistachio Ice Cream", "9", "28", "August 6, 2018"),
        "O_MR1" to listOf("Android Oreo", "Oatmeal Cookie", "8.1", "27", "December 5, 2017"),
        "O" to listOf("Android Oreo", "Oatmeal Cookie", "8.0", "26", "August 21, 2017"),
        "N_MR1" to listOf("Android Nougat", "New York Cheesecake", "7.1-7.1.2", "25", "October 4, 2016"),
        "N" to listOf("Android Nougat", "New York Cheesecake", "7.0", "24", "August 22, 2016"),
        "M" to listOf("Android Marshmallow", "Macadamia Nut Cookie", "6.0-6.0.1", "23", "October 2, 2015"),
        "LOLLIPOP_MR1" to listOf("Android Lollipop", "Lemon Meringue Pie", "5.1-5.1.1", "22", "March 2, 2015"),
        "LOLLIPOP" to listOf("Android Lollipop", "Lemon Meringue Pie", "5.0-5.0.2", "21", "November 4, 2014"),
        "KITKAT_WATCH" to listOf("Android KitKat", "Key Lime Pie", "4.4W-4.4W.2", "20", "June 25, 2014"),
        "KITKAT" to listOf("Android KitKat", "Key Lime Pie", "4.4-4.4.4", "19", "October 31, 2013"),
        "JELLY_BEAN_MR2" to listOf("Android Jelly Bean", "Jelly Bean", "4.3-4.3.1", "18", "July 24, 2013"),
        "JELLY_BEAN_MR1" to listOf("Android Jelly Bean", "Jelly Bean", "4.2-4.2.2", "17", "November 13, 2012"),
        "JELLY_BEAN" to listOf("Android Jelly Bean", "Jelly Bean", "4.1-4.1.2", "16", "July 9, 2012"),
        "ICE_CREAM_SANDWICH_MR1" to listOf("Android Ice Cream Sandwich", "Ice Cream Sandwich", "4.0.3-4.0.4", "15", "December 16, 2011"),
        "ICE_CREAM_SANDWICH" to listOf("Android Ice Cream Sandwich", "Ice Cream Sandwich", "4.0-4.0.2", "14", "October 18, 2011"),
        "HONEYCOMB_MR2" to listOf("Android Honeycomb", "Honeycomb", "3.2-3.2.6", "13", "July 15, 2011"),
        "HONEYCOMB_MR1" to listOf("Android Honeycomb", "Honeycomb", "3.1", "12", "May 10, 2011"),
        "HONEYCOMB" to listOf("Android Honeycomb", "Honeycomb", "3.0", "11", "February 22, 2011"),
        "GINGERBREAD_MR1" to listOf("Android Gingerbread", "Gingerbread", "2.3.3-2.3.7", "10", "February 9, 2011"),
        "GINGERBREAD" to listOf("Android Gingerbread", "Gingerbread", "2.3-2.3.2", "9", "December 6, 2010"),
        "FROYO" to listOf("Android Froyo", "Froyo", "2.2-2.2.3", "8", "May 20, 2010"),
        "ECLAIR_MR1" to listOf("Android Eclair", "Eclair", "2.1", "7", "January 11, 2010"),
        "ECLAIR_0_1" to listOf("Android Eclair", "Eclair", "2.0.1", "6", "December 3, 2009"),
        "ECLAIR" to listOf("Android Eclair", "Eclair", "2.0", "5", "October 27, 2009"),
        "DONUT" to listOf("Android Donut", "Donut", "1.6", "4", "September 15, 2009"),
        "CUPCAKE" to listOf("Android Cupcake", "Cupcake", "1.5", "3", "April 27, 2009"),
        "BASE_1_1" to listOf("Android 1.1", "Petit Four", "1.1", "2", "February 9, 2009"),
        "BASE" to listOf("Android 1.0", "", "1.0", "1", "September 23, 2008"),
    )

    val list = mutableListOf<VersionCodes.Info>()

    val obj = newNativeObject()

    init {
        for ((versionCode, infoList) in raw) {
            val (releaseName, internalCodename, platformVersion, apiLevel, releaseDate) = infoList
            val info = VersionCodes.Info(versionCode, releaseName, internalCodename, platformVersion, apiLevel, releaseDate)
            obj.put(versionCode, obj, info.toNativeObject())
            list += info
        }
    }

}
