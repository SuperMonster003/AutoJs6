package org.autojs.autojs.theme.intelligent.calendar

import org.autojs.autojs.theme.intelligent.GeoLocation
import java.time.Instant
import java.time.ZoneId

/**
 * Infers a location from the time zone, so sunrise / sunset and seasons work without location permission (D20).
 * Known zones map to the coordinates of their representative city; other zones get an estimate from the
 * standard UTC offset, which the user can replace with a manual location.
 * zh-CN: 由时区推断地点, 使日出日落与季节无需定位权限即可工作 (D20). 已知时区对应其代表城市的坐标;
 * 其他时区按标准 UTC 偏移估算, 用户可改为手动输入的地点.
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
object TimeZoneLocations {

    /** [isApproximate] is true for an estimate from the UTC offset. zh-CN: 由 UTC 偏移估算时 [isApproximate] 为 true. */
    data class Inferred(val location: GeoLocation, val isApproximate: Boolean)

    private const val ESTIMATED_LATITUDE = 35.0

    /** Zone regions south of the equator, used when estimating. zh-CN: 位于南半球的时区区域, 用于估算. */
    private val SOUTHERN_PREFIXES = listOf("Australia/", "Antarctica/", "America/Argentina/", "Pacific/Auckland", "Pacific/Chatham", "Indian/Reunion")

    private val TABLE: Map<String, GeoLocation> by lazy {
        mapOf(
            // Asia
            "Asia/Shanghai" to geo(31.23, 121.47), "Asia/Chongqing" to geo(29.56, 106.55), "Asia/Harbin" to geo(45.80, 126.53),
            "Asia/Urumqi" to geo(43.80, 87.58), "Asia/Hong_Kong" to geo(22.28, 114.15), "Asia/Macau" to geo(22.20, 113.55),
            "Asia/Taipei" to geo(25.05, 121.50), "Asia/Tokyo" to geo(35.69, 139.69), "Asia/Seoul" to geo(37.57, 126.98),
            "Asia/Pyongyang" to geo(39.02, 125.75), "Asia/Singapore" to geo(1.29, 103.85), "Asia/Kuala_Lumpur" to geo(3.14, 101.69),
            "Asia/Bangkok" to geo(13.75, 100.50), "Asia/Ho_Chi_Minh" to geo(10.82, 106.63), "Asia/Saigon" to geo(10.82, 106.63),
            "Asia/Jakarta" to geo(-6.21, 106.85), "Asia/Makassar" to geo(-5.15, 119.43), "Asia/Jayapura" to geo(-2.53, 140.72),
            "Asia/Manila" to geo(14.60, 120.98), "Asia/Kolkata" to geo(22.57, 88.36), "Asia/Calcutta" to geo(22.57, 88.36),
            "Asia/Karachi" to geo(24.86, 67.01), "Asia/Dhaka" to geo(23.81, 90.41), "Asia/Kathmandu" to geo(27.72, 85.32),
            "Asia/Colombo" to geo(6.93, 79.85), "Asia/Yangon" to geo(16.80, 96.15), "Asia/Dubai" to geo(25.27, 55.30),
            "Asia/Riyadh" to geo(24.71, 46.68), "Asia/Tehran" to geo(35.69, 51.39), "Asia/Baghdad" to geo(33.31, 44.36),
            "Asia/Jerusalem" to geo(31.78, 35.22), "Asia/Tel_Aviv" to geo(32.08, 34.78), "Asia/Beirut" to geo(33.89, 35.50),
            "Asia/Amman" to geo(31.95, 35.93), "Asia/Damascus" to geo(33.51, 36.29), "Asia/Kabul" to geo(34.56, 69.21),
            "Asia/Tashkent" to geo(41.30, 69.24), "Asia/Almaty" to geo(43.24, 76.89), "Asia/Novosibirsk" to geo(55.03, 82.92),
            "Asia/Yekaterinburg" to geo(56.84, 60.61), "Asia/Krasnoyarsk" to geo(56.01, 92.85), "Asia/Irkutsk" to geo(52.29, 104.30),
            "Asia/Vladivostok" to geo(43.12, 131.89), "Asia/Yakutsk" to geo(62.03, 129.73), "Asia/Magadan" to geo(59.56, 150.80),
            "Asia/Kamchatka" to geo(53.02, 158.65), "Asia/Omsk" to geo(54.99, 73.37), "Asia/Ulaanbaatar" to geo(47.89, 106.91),
            "Asia/Baku" to geo(40.41, 49.87), "Asia/Tbilisi" to geo(41.72, 44.79), "Asia/Yerevan" to geo(40.18, 44.51),
            "Asia/Qatar" to geo(25.29, 51.53), "Asia/Kuwait" to geo(29.38, 47.99), "Asia/Muscat" to geo(23.59, 58.41),
            "Asia/Bahrain" to geo(26.23, 50.59), "Asia/Brunei" to geo(4.90, 114.94), "Asia/Phnom_Penh" to geo(11.56, 104.92),
            "Asia/Vientiane" to geo(17.98, 102.63), "Asia/Thimphu" to geo(27.47, 89.64), "Asia/Bishkek" to geo(42.87, 74.59),
            "Asia/Dushanbe" to geo(38.56, 68.77), "Asia/Ashgabat" to geo(37.95, 58.38), "Asia/Nicosia" to geo(35.17, 33.36),
            "Asia/Dili" to geo(-8.56, 125.57), "Asia/Kuching" to geo(1.55, 110.33), "Asia/Pontianak" to geo(-0.03, 109.33),
            // Europe
            "Europe/London" to geo(51.51, -0.13), "Europe/Dublin" to geo(53.35, -6.26), "Europe/Lisbon" to geo(38.72, -9.14),
            "Europe/Madrid" to geo(40.42, -3.70), "Europe/Paris" to geo(48.86, 2.35), "Europe/Brussels" to geo(50.85, 4.35),
            "Europe/Amsterdam" to geo(52.37, 4.90), "Europe/Luxembourg" to geo(49.61, 6.13), "Europe/Berlin" to geo(52.52, 13.40),
            "Europe/Zurich" to geo(47.38, 8.54), "Europe/Rome" to geo(41.90, 12.50), "Europe/Vienna" to geo(48.21, 16.37),
            "Europe/Prague" to geo(50.08, 14.44), "Europe/Warsaw" to geo(52.23, 21.01), "Europe/Budapest" to geo(47.50, 19.04),
            "Europe/Copenhagen" to geo(55.68, 12.57), "Europe/Stockholm" to geo(59.33, 18.07), "Europe/Oslo" to geo(59.91, 10.75),
            "Europe/Helsinki" to geo(60.17, 24.94), "Europe/Tallinn" to geo(59.44, 24.75), "Europe/Riga" to geo(56.95, 24.11),
            "Europe/Vilnius" to geo(54.69, 25.28), "Europe/Athens" to geo(37.98, 23.73), "Europe/Istanbul" to geo(41.01, 28.98),
            "Europe/Bucharest" to geo(44.43, 26.10), "Europe/Sofia" to geo(42.70, 23.32), "Europe/Belgrade" to geo(44.79, 20.45),
            "Europe/Zagreb" to geo(45.81, 15.98), "Europe/Ljubljana" to geo(46.06, 14.51), "Europe/Bratislava" to geo(48.15, 17.11),
            "Europe/Kyiv" to geo(50.45, 30.52), "Europe/Kiev" to geo(50.45, 30.52), "Europe/Minsk" to geo(53.90, 27.57),
            "Europe/Moscow" to geo(55.76, 37.62), "Europe/Samara" to geo(53.20, 50.15), "Europe/Kaliningrad" to geo(54.71, 20.51),
            "Europe/Volgograd" to geo(48.71, 44.51), "Europe/Chisinau" to geo(47.01, 28.86), "Europe/Monaco" to geo(43.73, 7.42),
            "Europe/Malta" to geo(35.90, 14.51), "Europe/Sarajevo" to geo(43.86, 18.41), "Europe/Skopje" to geo(42.00, 21.43),
            "Europe/Tirane" to geo(41.33, 19.82), "Atlantic/Reykjavik" to geo(64.15, -21.94), "Atlantic/Canary" to geo(28.12, -15.43),
            "Atlantic/Azores" to geo(37.74, -25.67),
            // Africa
            "Africa/Cairo" to geo(30.04, 31.24), "Africa/Johannesburg" to geo(-26.20, 28.05), "Africa/Lagos" to geo(6.52, 3.38),
            "Africa/Nairobi" to geo(-1.29, 36.82), "Africa/Casablanca" to geo(33.57, -7.59), "Africa/Algiers" to geo(36.75, 3.06),
            "Africa/Tunis" to geo(36.81, 10.18), "Africa/Accra" to geo(5.60, -0.19), "Africa/Addis_Ababa" to geo(9.03, 38.74),
            "Africa/Khartoum" to geo(15.50, 32.56), "Africa/Kinshasa" to geo(-4.44, 15.27), "Africa/Abidjan" to geo(5.36, -4.01),
            "Africa/Dar_es_Salaam" to geo(-6.79, 39.21), "Africa/Kampala" to geo(0.35, 32.58), "Africa/Luanda" to geo(-8.84, 13.23),
            "Africa/Maputo" to geo(-25.97, 32.57), "Africa/Harare" to geo(-17.83, 31.05), "Africa/Lusaka" to geo(-15.39, 28.32),
            "Africa/Tripoli" to geo(32.89, 13.19), "Africa/Dakar" to geo(14.72, -17.47), "Africa/Windhoek" to geo(-22.56, 17.08),
            // Americas
            "America/New_York" to geo(40.71, -74.01), "America/Detroit" to geo(42.33, -83.05), "America/Chicago" to geo(41.88, -87.63),
            "America/Denver" to geo(39.74, -104.99), "America/Phoenix" to geo(33.45, -112.07), "America/Los_Angeles" to geo(34.05, -118.24),
            "America/Anchorage" to geo(61.22, -149.90), "Pacific/Honolulu" to geo(21.31, -157.86), "America/Toronto" to geo(43.65, -79.38),
            "America/Vancouver" to geo(49.28, -123.12), "America/Edmonton" to geo(53.55, -113.49), "America/Winnipeg" to geo(49.90, -97.14),
            "America/Halifax" to geo(44.65, -63.58), "America/St_Johns" to geo(47.56, -52.71), "America/Regina" to geo(50.45, -104.61),
            "America/Mexico_City" to geo(19.43, -99.13), "America/Tijuana" to geo(32.51, -117.04), "America/Monterrey" to geo(25.69, -100.32),
            "America/Cancun" to geo(21.16, -86.85), "America/Guatemala" to geo(14.63, -90.51), "America/El_Salvador" to geo(13.69, -89.22),
            "America/Tegucigalpa" to geo(14.07, -87.19), "America/Managua" to geo(12.11, -86.24), "America/Costa_Rica" to geo(9.93, -84.08),
            "America/Panama" to geo(8.98, -79.52), "America/Havana" to geo(23.11, -82.37), "America/Jamaica" to geo(18.02, -76.80),
            "America/Santo_Domingo" to geo(18.49, -69.93), "America/Puerto_Rico" to geo(18.47, -66.11), "America/Bogota" to geo(4.71, -74.07),
            "America/Caracas" to geo(10.48, -66.90), "America/Lima" to geo(-12.05, -77.04), "America/Guayaquil" to geo(-2.19, -79.89),
            "America/La_Paz" to geo(-16.50, -68.15), "America/Santiago" to geo(-33.45, -70.67),
            "America/Argentina/Buenos_Aires" to geo(-34.60, -58.38), "America/Buenos_Aires" to geo(-34.60, -58.38),
            "America/Montevideo" to geo(-34.90, -56.16), "America/Asuncion" to geo(-25.26, -57.58), "America/Sao_Paulo" to geo(-23.55, -46.63),
            "America/Manaus" to geo(-3.12, -60.02), "America/Fortaleza" to geo(-3.73, -38.52), "America/Recife" to geo(-8.05, -34.88),
            "America/Bahia" to geo(-12.97, -38.50), "America/Belem" to geo(-1.46, -48.49),
            // Oceania and the Indian Ocean
            "Australia/Sydney" to geo(-33.87, 151.21), "Australia/Melbourne" to geo(-37.81, 144.96), "Australia/Brisbane" to geo(-27.47, 153.03),
            "Australia/Adelaide" to geo(-34.93, 138.60), "Australia/Perth" to geo(-31.95, 115.86), "Australia/Darwin" to geo(-12.46, 130.84),
            "Australia/Hobart" to geo(-42.88, 147.33), "Pacific/Auckland" to geo(-36.85, 174.76), "Pacific/Fiji" to geo(-18.14, 178.44),
            "Pacific/Guam" to geo(13.44, 144.79), "Pacific/Port_Moresby" to geo(-9.44, 147.18), "Pacific/Tongatapu" to geo(-21.14, -175.20),
            "Pacific/Apia" to geo(-13.83, -171.77), "Indian/Maldives" to geo(4.18, 73.51), "Indian/Mauritius" to geo(-20.16, 57.50),
            "Indian/Reunion" to geo(-20.88, 55.45),
        )
    }

    private fun geo(latitude: Double, longitude: Double) = GeoLocation(latitude, longitude)

    fun locationOf(zoneId: String): GeoLocation? = TABLE[zoneId]

    /**
     * The location for [zone]: the table entry when known, otherwise an estimate from its standard offset at [at].
     * zh-CN: [zone] 对应的地点: 表中已知时取表中坐标, 否则按其在 [at] 时刻的标准偏移估算.
     */
    fun infer(zone: ZoneId, at: Instant = Instant.now()): Inferred {
        TABLE[zone.id]?.let { return Inferred(it, isApproximate = false) }
        val standardOffsetSeconds = runCatching { zone.rules.getStandardOffset(at).totalSeconds }.getOrDefault(0)
        val longitude = (standardOffsetSeconds / 240.0).coerceIn(-180.0, 180.0)
        val southern = SOUTHERN_PREFIXES.any { zone.id.startsWith(it) }
        return Inferred(GeoLocation(if (southern) -ESTIMATED_LATITUDE else ESTIMATED_LATITUDE, longitude), isApproximate = true)
    }

}
