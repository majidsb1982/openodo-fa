// SPDX-License-Identifier: GPL-3.0-only
package ca.cyberengma.openodo.ui.util

import java.time.LocalDate
import java.time.YearMonth

/**
 * Dependency-free Gregorian ↔ Jalali (Persian) conversion and formatting.
 */
object Jalali {

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    data class Date(val year: Int, val month: Int, val day: Int) {
        override fun toString(): String = "%04d/%02d/%02d".format(year, month, day)
        fun toDisplay(): String = "$day ${monthNames.getOrElse(month - 1) { "?" }} $year"
        fun toDisplayShort(): String = "$day ${monthNames.getOrElse(month - 1) { "?" }}"
        fun toYearMonth(): String = "${monthNames.getOrElse(month - 1) { "?" }} $year"
        fun toLocalDate(): LocalDate = toGregorian(year, month, day)
    }

    fun fromGregorian(gy: Int, gm: Int, gd: Int): Date {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Date(jy, jm, jd)
    }

    fun fromGregorian(date: LocalDate): Date =
        fromGregorian(date.year, date.monthValue, date.dayOfMonth)

    /** Convert Jalali y/m/d to Gregorian LocalDate (correct for common range 1200–1500). */
    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val jy2 = jy - 979
        val days = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 +
            (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186) + jd - 1
        var gDayNo = days + 79
        val gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        var gyAdj = gy
        if (gDayNo >= 36525) {
            gDayNo--
            gyAdj += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        gyAdj += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gyAdj += gDayNo / 365
            gDayNo %= 365
        }
        val sal_a = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gDayNo >= sal_a[gm]) {
            gDayNo -= sal_a[gm]
            gm++
        }
        return LocalDate.of(gyAdj, gm, gDayNo + 1)
    }

    /** Leap year check — accurate for practical modern range (approx. 1200–1500). */
    fun isLeap(jy: Int): Boolean {
        val r = jy % 33
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
    }

    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        jm == 12 -> if (isLeap(jy)) 30 else 29
        else -> 30
    }

    fun format(date: LocalDate): String = fromGregorian(date).toDisplay()
    fun formatShort(date: LocalDate): String = fromGregorian(date).toDisplayShort()
    fun formatYearMonth(ym: YearMonth): String = fromGregorian(ym.atDay(1)).toYearMonth()
    fun formatIso(date: LocalDate): String = fromGregorian(date).toString()
    fun today(): Date = fromGregorian(LocalDate.now())
}
