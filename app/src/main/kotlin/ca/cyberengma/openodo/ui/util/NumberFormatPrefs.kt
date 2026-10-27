// SPDX-License-Identifier: GPL-3.0-only
package ca.cyberengma.openodo.ui.util

/**
 * Number display preferences (Excel-like separators + optional Persian digits).
 */
data class NumberFormatPrefs(
    val usePersianDigits: Boolean = true,
    /** Thousands grouping: "" | "," | "." | " " | "٬" */
    val thousandsSeparator: String = "٬",
    /** Decimal separator: "." | "," | "٫" */
    val decimalSeparator: String = "٫",
) {
    fun toWesternDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(
                when (ch) {
                    in '۰'..'۹' -> '0' + (ch - '۰')
                    in '٠'..'٩' -> '0' + (ch - '٠')
                    else -> ch
                },
            )
        }
        return sb.toString()
    }

    fun toDisplayDigits(input: String): String {
        if (!usePersianDigits) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(if (ch in '0'..'9') '۰' + (ch - '0') else ch)
        }
        return sb.toString()
    }

    /** Normalize user input to a plain western decimal string (dot decimal, no grouping). */
    fun normalizeInput(text: String): String {
        var s = toWesternDigits(text.trim())
        val dec = decimalSeparator.ifEmpty { "." }
        for (g in listOf(" ", "٬", "'")) {
            if (g != dec) s = s.replace(g, "")
        }
        when (dec) {
            "," -> {
                if (thousandsSeparator == ".") s = s.replace(".", "")
                s = s.replace(",", ".")
            }
            "٫" -> {
                if (thousandsSeparator == ",") s = s.replace(",", "")
                if (thousandsSeparator == ".") s = s.replace(".", "")
                s = s.replace("٫", ".")
                if (s.count { it == ',' } > 0 && s.count { it == '.' } == 1) {
                    s = s.replace(",", "")
                } else if (s.count { it == ',' } == 1 && s.count { it == '.' } == 0) {
                    s = s.replace(",", ".")
                }
            }
            else -> {
                if (thousandsSeparator == ",") s = s.replace(",", "")
                if (thousandsSeparator == " ") s = s.replace(" ", "")
                s = s.replace("٫", ".")
            }
        }
        return s
    }

    companion object {
        val PRESETS_THOUSANDS = listOf("٬", ",", ".", " ", "")
        val PRESETS_DECIMAL = listOf("٫", ".", ",")

        val WESTERN = NumberFormatPrefs(
            usePersianDigits = false,
            thousandsSeparator = ",",
            decimalSeparator = ".",
        )

        fun labelThousands(sep: String): String = when (sep) {
            "" -> "بدون جداکننده"
            "," -> "ویرگول لاتین (1,000)"
            "." -> "نقطه (1.000)"
            " " -> "فاصله (1 000)"
            "٬" -> "ویرگول فارسی (۱٬۰۰۰)"
            else -> sep
        }

        fun labelDecimal(sep: String): String = when (sep) {
            "." -> "نقطه (12.5)"
            "," -> "ویرگول لاتین (12,5)"
            "٫" -> "ممیز فارسی (۱۲٫۵)"
            else -> sep
        }
    }
}

/** Common currencies for picker (code + Persian label). */
object CurrencyCatalog {
    data class Item(val code: String, val label: String)

    val items = listOf(
        Item("IRT", "تومان (IRT)"),
        Item("IRR", "ریال ایران (IRR)"),
        Item("USD", "دلار آمریکا (USD)"),
        Item("EUR", "یورو (EUR)"),
        Item("AED", "درهم امارات (AED)"),
        Item("TRY", "لیر ترکیه (TRY)"),
        Item("GBP", "پوند انگلیس (GBP)"),
        Item("CAD", "دلار کانادا (CAD)"),
        Item("AUD", "دلار استرالیا (AUD)"),
        Item("CHF", "فرانک سوئیس (CHF)"),
        Item("CNY", "یوآن چین (CNY)"),
        Item("JPY", "ین ژاپن (JPY)"),
        Item("INR", "روپیه هند (INR)"),
        Item("SAR", "ریال سعودی (SAR)"),
        Item("QAR", "ریال قطر (QAR)"),
        Item("IQD", "دینار عراق (IQD)"),
        Item("AFN", "افغانی (AFN)"),
        Item("RUB", "روبل روسیه (RUB)"),
    )
}
