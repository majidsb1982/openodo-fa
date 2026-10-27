// SPDX-License-Identifier: GPL-3.0-only
package ca.cyberengma.openodo.ui

import ca.cyberengma.openodo.core.money.CurrencyMinorDigits
import ca.cyberengma.openodo.core.money.Money
import ca.cyberengma.openodo.core.money.UnitPrice
import ca.cyberengma.openodo.core.units.DistanceUnit
import ca.cyberengma.openodo.core.units.EnergyUnit
import ca.cyberengma.openodo.core.units.Metres
import ca.cyberengma.openodo.core.units.Millilitres
import ca.cyberengma.openodo.core.units.VolumeUnit
import ca.cyberengma.openodo.core.units.WattHours
import ca.cyberengma.openodo.ui.util.NumberFormatPrefs
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object VehicleValueFormatter {
    @Volatile
    var prefs: NumberFormatPrefs = NumberFormatPrefs()

    fun parseDecimal(text: String, locale: Locale = Locale.getDefault()): BigDecimal? {
        val normalized = prefs.normalizeInput(text)
        if (normalized.isEmpty()) return null
        return runCatching { BigDecimal(normalized) }.getOrNull()
    }

    fun parseDistance(text: String, unit: DistanceUnit, locale: Locale = Locale.getDefault()): Metres? =
        parseDecimal(text, locale)?.let { amount ->
            when (unit) {
                DistanceUnit.METRES -> amount.roundLong()?.let(::Metres)
                DistanceUnit.KILOMETRES -> amount.safe { Metres.kilometres(it) }
                DistanceUnit.MILES -> amount.safe { Metres.miles(it) }
            }
        }

    fun formatDistance(value: Metres, unit: DistanceUnit, locale: Locale = Locale.getDefault()): String =
        formatDecimal(value.to(unit), 3)

    fun parseVolume(text: String, unit: VolumeUnit, locale: Locale = Locale.getDefault()): Millilitres? =
        parseDecimal(text, locale)?.let { amount ->
            when (unit) {
                VolumeUnit.MILLILITRES -> amount.roundLong()?.let(::Millilitres)
                VolumeUnit.LITRES -> amount.safe { Millilitres.litres(it) }
                VolumeUnit.US_GALLONS -> amount.safe { Millilitres.usGallons(it) }
                VolumeUnit.UK_GALLONS -> amount.safe { Millilitres.ukGallons(it) }
            }
        }

    fun formatVolume(value: Millilitres, unit: VolumeUnit, locale: Locale = Locale.getDefault()): String =
        formatDecimal(value.to(unit), 3)

    fun parseEnergy(text: String, unit: EnergyUnit, locale: Locale = Locale.getDefault()): WattHours? =
        parseDecimal(text, locale)?.let { amount ->
            when (unit) {
                EnergyUnit.WATT_HOURS -> amount.roundLong()?.let(::WattHours)
                EnergyUnit.KILOWATT_HOURS -> amount.safe { WattHours.kilowattHours(it) }
            }
        }

    fun formatEnergy(value: WattHours, unit: EnergyUnit, locale: Locale = Locale.getDefault()): String =
        formatDecimal(value.to(unit), 3)

    fun parseMoney(text: String, currency: String, locale: Locale = Locale.getDefault()): Money? {
        val digits = CurrencyMinorDigits.of(currency)
        val minor = parseDecimal(text, locale)
            ?.movePointRight(digits)
            ?.setScale(0, RoundingMode.HALF_EVEN)
            ?.toLongExactOrNull()
            ?: return null
        return Money(minor, currency.uppercase(Locale.ROOT))
    }

    fun formatMoney(value: Money, locale: Locale = Locale.getDefault()): String {
        val digits = CurrencyMinorDigits.of(value.currency)
        return formatDecimal(BigDecimal(value.minor).movePointLeft(digits), digits, digits)
    }

    fun parseUnitPrice(text: String, currency: String, locale: Locale = Locale.getDefault()): UnitPrice? {
        val milli = parseDecimal(text, locale)
            ?.movePointRight(3)
            ?.setScale(0, RoundingMode.HALF_EVEN)
            ?.toLongExactOrNull()
            ?: return null
        return UnitPrice(milli, currency.uppercase(Locale.ROOT))
    }

    fun formatUnitPrice(value: UnitPrice, locale: Locale = Locale.getDefault()): String =
        formatDecimal(BigDecimal(value.milli).movePointLeft(3), 3)

    fun formatDecimal(
        value: BigDecimal,
        maximumFractionDigits: Int,
        minimumFractionDigits: Int = 0,
    ): String {
        val scale = maxOf(maximumFractionDigits, minimumFractionDigits)
        val scaled = value.setScale(scale, RoundingMode.HALF_EVEN)
        val plain = if (minimumFractionDigits > 0) {
            scaled.setScale(minimumFractionDigits, RoundingMode.HALF_EVEN).toPlainString()
        } else {
            scaled.stripTrailingZeros().toPlainString()
        }
        val neg = plain.startsWith('-')
        val abs = if (neg) plain.substring(1) else plain
        val parts = abs.split('.')
        val intPart = parts[0]
        val grouped = buildString {
            val rev = intPart.reversed()
            for (i in rev.indices) {
                if (i > 0 && i % 3 == 0 && prefs.thousandsSeparator.isNotEmpty()) {
                    append(prefs.thousandsSeparator)
                }
                append(rev[i])
            }
        }.reversed()
        val frac = if (parts.size > 1) parts[1] else ""
        val body = if (frac.isNotEmpty()) {
            "$grouped${prefs.decimalSeparator}$frac"
        } else {
            grouped
        }
        val withSign = if (neg) "-$body" else body
        return prefs.toDisplayDigits(withSign)
    }

    private fun BigDecimal.roundLong(): Long? =
        setScale(0, RoundingMode.HALF_EVEN).toLongExactOrNull()

    private fun BigDecimal.toLongExactOrNull(): Long? =
        runCatching { longValueExact() }.getOrNull()

    private inline fun <T> BigDecimal.safe(block: (BigDecimal) -> T): T? =
        runCatching { block(this) }.getOrNull()
}
