package com.shivam.glasslauncher.search

/** Converts length, weight and temperature queries such as "5 km to mi" or "72f in c". */
object UnitConverter {

    enum class Dimension { LENGTH, WEIGHT, TEMPERATURE }

    data class Unit(val symbol: String, val name: String, val dimension: Dimension, val toBase: Double, val aliases: List<String>)

    data class Conversion(val value: Double, val from: Unit, val result: Double, val to: Unit) {
        val text: String get() = "${Calculator.format(value)} ${from.symbol} = ${formatResult(result)} ${to.symbol}"
    }

    private val units = listOf(
        // Length, base: metre
        Unit("mm", "millimetres", Dimension.LENGTH, 0.001, listOf("mm", "millimeter", "millimeters", "millimetre", "millimetres")),
        Unit("cm", "centimetres", Dimension.LENGTH, 0.01, listOf("cm", "centimeter", "centimeters", "centimetre", "centimetres")),
        Unit("m", "metres", Dimension.LENGTH, 1.0, listOf("m", "meter", "meters", "metre", "metres")),
        Unit("km", "kilometres", Dimension.LENGTH, 1000.0, listOf("km", "kms", "kilometer", "kilometers", "kilometre", "kilometres")),
        Unit("in", "inches", Dimension.LENGTH, 0.0254, listOf("in", "inch", "inches", "\"")),
        Unit("ft", "feet", Dimension.LENGTH, 0.3048, listOf("ft", "foot", "feet", "'")),
        Unit("yd", "yards", Dimension.LENGTH, 0.9144, listOf("yd", "yds", "yard", "yards")),
        Unit("mi", "miles", Dimension.LENGTH, 1609.344, listOf("mi", "mile", "miles")),
        Unit("nmi", "nautical miles", Dimension.LENGTH, 1852.0, listOf("nmi", "nautical mile", "nautical miles")),
        // Weight, base: kilogram
        Unit("mg", "milligrams", Dimension.WEIGHT, 1e-6, listOf("mg", "milligram", "milligrams")),
        Unit("g", "grams", Dimension.WEIGHT, 0.001, listOf("g", "gm", "gms", "gram", "grams")),
        Unit("kg", "kilograms", Dimension.WEIGHT, 1.0, listOf("kg", "kgs", "kilo", "kilos", "kilogram", "kilograms")),
        Unit("t", "tonnes", Dimension.WEIGHT, 1000.0, listOf("t", "tonne", "tonnes", "ton", "tons")),
        Unit("oz", "ounces", Dimension.WEIGHT, 0.028349523125, listOf("oz", "ounce", "ounces")),
        Unit("lb", "pounds", Dimension.WEIGHT, 0.45359237, listOf("lb", "lbs", "pound", "pounds")),
        Unit("st", "stone", Dimension.WEIGHT, 6.35029318, listOf("st", "stone", "stones")),
        // Temperature, handled with formulas
        Unit("°C", "Celsius", Dimension.TEMPERATURE, 1.0, listOf("c", "°c", "celsius", "centigrade", "degc")),
        Unit("°F", "Fahrenheit", Dimension.TEMPERATURE, 1.0, listOf("f", "°f", "fahrenheit", "degf")),
        Unit("K", "Kelvin", Dimension.TEMPERATURE, 1.0, listOf("k", "kelvin", "kelvins")),
    )

    private val bySymbolAlias: Map<String, Unit> = buildMap {
        units.forEach { u -> u.aliases.forEach { put(it, u) } }
    }

    private val defaultTarget = mapOf(
        "km" to "mi", "mi" to "km", "m" to "ft", "ft" to "m", "cm" to "in", "in" to "cm",
        "mm" to "in", "yd" to "m", "nmi" to "km",
        "kg" to "lb", "lb" to "kg", "g" to "oz", "oz" to "g", "mg" to "g", "t" to "lb", "st" to "kg",
        "°C" to "°F", "°F" to "°C", "K" to "°C",
    )

    private val withTarget = Regex(
        "^\\s*(-?\\d*\\.?\\d+)\\s*([a-z°\"']+(?:\\s[a-z]+)?)\\s+(?:to|in|into|as|=|->|in to)\\s+([a-z°\"']+(?:\\s[a-z]+)?)\\s*$",
    )
    private val bare = Regex("^\\s*(-?\\d*\\.?\\d+)\\s*([a-z°\"']+(?:\\s[a-z]+)?)\\s*$")

    fun convert(query: String): Conversion? {
        val q = query.lowercase().replace("degrees ", "").replace("degree ", "")
        withTarget.matchEntire(q)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            val from = lookup(m.groupValues[2]) ?: return null
            val to = lookup(m.groupValues[3]) ?: return null
            if (from.dimension != to.dimension || from == to) return null
            return Conversion(value, from, convert(value, from, to), to)
        }
        bare.matchEntire(q)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return null
            val unitText = m.groupValues[2]
            // A single letter alone ("5 m", "5 c") is ambiguous with app names, require 2+ letters
            // except for well-known metric units.
            if (unitText.length < 2 && unitText !in setOf("m", "g", "c", "f")) return null
            val from = lookup(unitText) ?: return null
            val to = bySymbolAlias[defaultTarget[from.symbol]?.lowercase()] ?: units.firstOrNull { it.symbol == defaultTarget[from.symbol] } ?: return null
            return Conversion(value, from, convert(value, from, to), to)
        }
        return null
    }

    private fun lookup(text: String): Unit? = bySymbolAlias[text.trim()]

    fun convert(value: Double, from: Unit, to: Unit): Double {
        if (from.dimension == Dimension.TEMPERATURE) {
            val celsius = when (from.symbol) {
                "°C" -> value
                "°F" -> (value - 32.0) * 5.0 / 9.0
                else -> value - 273.15
            }
            return when (to.symbol) {
                "°C" -> celsius
                "°F" -> celsius * 9.0 / 5.0 + 32.0
                else -> celsius + 273.15
            }
        }
        return value * from.toBase / to.toBase
    }

    private fun formatResult(v: Double): String {
        val abs = kotlin.math.abs(v)
        if (abs != 0.0 && (abs < 1e-4 || abs >= 1e12)) return "%.4e".format(java.util.Locale.US, v)
        val bd = java.math.BigDecimal(v).setScale(if (abs >= 100) 2 else 4, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }
}
