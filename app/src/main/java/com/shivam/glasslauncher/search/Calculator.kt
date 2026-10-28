package com.shivam.glasslauncher.search

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small hand-written recursive-descent evaluator for Spotlight's inline calculator.
 *
 * Grammar (lowest to highest precedence):
 *   expr    := term (('+' | '-') term)*
 *   term    := unary (('*' | '/' | '%' | implicit) unary)*
 *   unary   := ('-' | '+') unary | power      so -3^2 == -9
 *   power   := postfix ('^' unary)?            right associative
 *   postfix := primary ('!' | '%')*          '%' here means percent when no operand follows
 *   primary := number | '(' expr ')' | name | name '(' expr (',' expr)* ')'
 *
 * Trig functions use radians.
 */
object Calculator {

    class ParseException(message: String) : Exception(message)

    /**
     * Returns the value of [input] when it is a complete arithmetic expression that contains at
     * least one operator or function, otherwise null (so "42" alone is not shown as a result).
     */
    fun evaluateOrNull(input: String): Double? {
        val text = normalize(input)
        if (text.isBlank()) return null
        if (!looksLikeMath(text)) return null
        return try {
            val v = Parser(text).parseAll()
            if (v.isNaN() || v.isInfinite()) null else v
        } catch (e: ParseException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun evaluate(input: String): Double = Parser(normalize(input)).parseAll()

    fun format(value: Double): String {
        if (value == floor(value) && abs(value) < 1e15) {
            return value.toLong().toString()
        }
        val abs = abs(value)
        if (abs != 0.0 && (abs < 1e-6 || abs >= 1e15)) {
            return "%.6e".format(java.util.Locale.US, value)
        }
        val s = java.math.BigDecimal(value).round(java.math.MathContext(12)).stripTrailingZeros()
        return s.toPlainString()
    }

    private fun normalize(input: String): String {
        var s = input.trim()
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace("**", "^")
            .replace("π", "pi")
        // "3 x 4" or "3x4" -> multiplication, but keep names like "exp" intact.
        s = Regex("(?<=[0-9).])\\s*[xX]\\s*(?=[0-9(.])").replace(s, "*")
        if (s.startsWith("=")) s = s.substring(1)
        return s
    }

    private fun looksLikeMath(s: String): Boolean {
        val hasDigit = s.any { it.isDigit() } || s.contains("pi") || Regex("\\be\\b").containsMatchIn(s)
        val hasOp = s.any { it in "+-*/^%!(" } || FUNCTIONS.keys.any { s.contains(it) }
        val onlyMathChars = s.all { it.isDigit() || it.isLetter() || it in "+-*/^%!(). ," }
        // Plain words ("maps") must not be treated as math.
        val letters = Regex("[a-zA-Z]+").findAll(s).map { it.value.lowercase() }.toList()
        val lettersOk = letters.all { it in FUNCTIONS || it in CONSTANTS }
        return hasDigit && (hasOp || letters.isNotEmpty()) && onlyMathChars && lettersOk
    }

    private val FUNCTIONS: Map<String, (DoubleArray) -> Double> = mapOf(
        "sqrt" to { a -> sqrt(a[0]) },
        "cbrt" to { a -> cbrt(a[0]) },
        "sin" to { a -> sin(a[0]) },
        "cos" to { a -> cos(a[0]) },
        "tan" to { a -> tan(a[0]) },
        "asin" to { a -> asin(a[0]) },
        "acos" to { a -> acos(a[0]) },
        "atan" to { a -> atan(a[0]) },
        "ln" to { a -> ln(a[0]) },
        "log" to { a -> if (a.size == 2) ln(a[0]) / ln(a[1]) else log10(a[0]) },
        "log2" to { a -> log2(a[0]) },
        "exp" to { a -> exp(a[0]) },
        "abs" to { a -> abs(a[0]) },
        "round" to { a -> round(a[0]) },
        "floor" to { a -> floor(a[0]) },
        "ceil" to { a -> ceil(a[0]) },
        "min" to { a -> a.min() },
        "max" to { a -> a.max() },
    )

    private val CONSTANTS = mapOf("pi" to Math.PI, "e" to Math.E, "tau" to 2 * Math.PI)

    private class Parser(private val s: String) {
        private var pos = 0

        fun parseAll(): Double {
            val v = expr()
            skipWs()
            if (pos != s.length) throw ParseException("Unexpected '${s[pos]}' at $pos")
            return v
        }

        private fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipWs()
            return if (pos < s.length) s[pos] else null
        }

        private fun expr(): Double {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += term() }
                    '-' -> { pos++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                val c = peek() ?: return v
                when {
                    c == '*' -> { pos++; v *= unary() }
                    c == '/' -> {
                        pos++
                        val d = unary()
                        if (d == 0.0) throw ArithmeticException("Division by zero")
                        v /= d
                    }
                    c == '%' && operandFollows(pos + 1) -> {
                        pos++
                        val d = unary()
                        if (d == 0.0) throw ArithmeticException("Modulo by zero")
                        v %= d
                    }
                    // implicit multiplication: 2(3), 2pi, (1+2)(3+4)
                    c == '(' || c.isLetter() -> v *= unary()
                    else -> return v
                }
            }
        }

        private fun operandFollows(from: Int): Boolean {
            var i = from
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) return false
            val c = s[i]
            return c.isDigit() || c == '.' || c == '(' || c.isLetter()
        }

        private fun unary(): Double = when (peek()) {
            '-' -> { pos++; -unary() }
            '+' -> { pos++; unary() }
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            if (peek() == '^') {
                pos++
                return base.pow(unary())
            }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                when {
                    peek() == '!' -> { pos++; v = factorial(v) }
                    peek() == '%' && !operandFollows(pos + 1) -> { pos++; v /= 100.0 }
                    else -> return v
                }
            }
        }

        private fun primary(): Double {
            val c = peek() ?: throw ParseException("Unexpected end")
            if (c == '(') {
                pos++
                val v = expr()
                if (peek() != ')') throw ParseException("Missing )")
                pos++
                return v
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && (s[pos].isLetterOrDigit())) pos++
                // Allow names immediately followed by digits only for log2.
                var name = s.substring(start, pos).lowercase()
                if (name !in FUNCTIONS && name !in CONSTANTS) {
                    // Try the longest known prefix (e.g. "pie" is not valid, "pi2" -> pi * 2).
                    val known = (FUNCTIONS.keys + CONSTANTS.keys).filter { name.startsWith(it) }.maxByOrNull { it.length }
                        ?: throw ParseException("Unknown name $name")
                    pos = start + known.length
                    name = known
                }
                CONSTANTS[name]?.let { return it }
                val fn = FUNCTIONS.getValue(name)
                val args = ArrayList<Double>()
                if (peek() == '(') {
                    pos++
                    args += expr()
                    while (peek() == ',') { pos++; args += expr() }
                    if (peek() != ')') throw ParseException("Missing )")
                    pos++
                } else {
                    args += power()
                }
                return fn(args.toDoubleArray())
            }
            throw ParseException("Unexpected '$c'")
        }

        private fun number(): Double {
            val start = pos
            var seenDot = false
            while (pos < s.length && (s[pos].isDigit() || (s[pos] == '.' && !seenDot) || s[pos] == '_')) {
                if (s[pos] == '.') seenDot = true
                pos++
            }
            // scientific notation 1e5 / 2.5E-3
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                val save = pos
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                if (pos < s.length && s[pos].isDigit()) {
                    while (pos < s.length && s[pos].isDigit()) pos++
                } else {
                    pos = save
                }
            }
            val text = s.substring(start, pos).replace("_", "")
            return text.toDoubleOrNull() ?: throw ParseException("Bad number $text")
        }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v) || v > 170) throw ArithmeticException("Bad factorial")
            var r = 1.0
            for (i in 2..v.toInt()) r *= i
            return r
        }
    }
}
