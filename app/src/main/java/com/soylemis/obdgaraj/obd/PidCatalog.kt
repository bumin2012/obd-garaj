package com.soylemis.obdgaraj.obd

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kullanıcı/dosya tabanlı PID tanımlarını yükler (AndrOBD'nin sensors.json kalıbı).
 *
 * Biçim (pids.json):
 * ```json
 * [ { "pid": "0x5E", "name": "Yakıt tüketimi", "unit": "L/h", "bytes": 2, "formula": "(A*256+B)/20" } ]
 * ```
 * `formula` içinde A..D baytları, + - * / ( ) ve sayılar serbest; başka bir şeye izin
 * verilmez (değerlendirici kendi küçük ayrıştırıcımızdır, script çalıştırılmaz).
 */
object PidCatalog {

    fun parse(jsonText: String): List<PidDef> {
        val arr = JSONArray(jsonText)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pid = parsePid(o.getString("pid"))
            PidDef(
                pid = pid,
                nameTr = o.getString("name"),
                unit = o.optString("unit", ""),
                byteCount = o.optInt("bytes", formulaVars(o.getString("formula"))),
                decode = compileFormula(o.getString("formula")),
            )
        }
    }

    /**
     * [defs] üzerine [extra] tanımları ekler; çakışmada [extra] kazanır (kullanıcı
     * düzeltmesi yerleşik tablodan önce gelir). Bozuk JSON'da [IllegalArgumentException]
     * fırlatılır — çağıran ([PidTable.install]) yakalar ve mevcut tabloyu korur.
     */
    fun merge(defs: Map<Int, PidDef>, extraJson: String?): Map<Int, PidDef> {
        if (extraJson.isNullOrBlank()) return defs
        return (defs.values + parse(extraJson)).associateBy { it.pid }
    }

    private fun parsePid(s: String): Int {
        val t = s.trim().removePrefix("0x").removePrefix("0X").uppercase()
        require(t.length in 1..2 && t.all { it in "0123456789ABCDEF" }) { "Geçersiz pid: $s" }
        return t.toInt(16)
    }

    /** Formülde hangi baytlar kullanılıyor — bytes alanı yoksa bundan çıkar. */
    private fun formulaVars(f: String): Int =
        ('A'..'D').count { c -> f.any { it.equals(c, ignoreCase = true) } }.coerceAtLeast(1)

    // ---- güvenli formül ayrıştırıcısı (rekrsif iniş; yalnız aritmetik) ----

    fun compileFormula(expr: String): (IntArray) -> Double {
        val tokens = tokenize(expr)
        val parser = Parser(tokens)
        val node = parser.expr()
        require(parser.peek() == null) { "Formül sonda fazla karakter: $expr" }
        return { d -> node(d) }
    }

    private sealed interface Node { operator fun invoke(d: IntArray): Double }

    private class Const(val v: Double) : Node { override fun invoke(d: IntArray) = v }
    private class Var(val idx: Int) : Node {
        override fun invoke(d: IntArray) = if (idx < d.size) d[idx].toDouble() else 0.0
    }
    private class Neg(val a: Node) : Node { override fun invoke(d: IntArray) = -a(d) }
    private class Op(val a: Node, val b: Node, val kind: Char) : Node {
        override fun invoke(d: IntArray): Double = when (kind) {
            '+' -> a(d) + b(d)
            '-' -> a(d) - b(d)
            '*' -> a(d) * b(d)
            '/' -> { val x = b(d); if (x == 0.0) 0.0 else a(d) / x }
            else -> error("?")
        }
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c in "+-*/()" -> { out += c.toString(); i++ }
                c.isDigit() || c == '.' -> {
                    var j = i
                    while (j < s.length && (s[j].isDigit() || s[j] == '.')) j++
                    out += s.substring(i, j); i = j
                }
                c.lowercaseChar() in 'a'..'d' -> { out += c.uppercaseChar().toString(); i++ }
                else -> throw IllegalArgumentException("Formülde izin verilmeyen karakter: '$c'")
            }
        }
        return out
    }

    private class Parser(private val t: List<String>) {
        private var i = 0
        fun peek(): String? = t.getOrNull(i)
        private fun eat(w: String): Boolean = peek() == w && run { i++; true }

        fun expr(): Node {
            var n = term()
            while (true) n = when {
                eat("+") -> Op(n, term(), '+')
                eat("-") -> Op(n, term(), '-')
                else -> return n
            }
        }

        private fun term(): Node {
            var n = unary()
            while (true) n = when {
                eat("*") -> Op(n, unary(), '*')
                eat("/") -> Op(n, unary(), '/')
                else -> return n
            }
        }

        private fun unary(): Node = when {
            eat("-") -> Neg(unary())
            eat("+") -> unary()
            else -> atom()
        }

        private fun atom(): Node {
            val tok = peek() ?: throw IllegalArgumentException("Formül beklenenden kısa")
            return when {
                eat("(") -> {
                    val n = expr()
                    require(eat(")")) { "Kapanış parantezi yok" }
                    n
                }
                tok == "A" || tok == "B" || tok == "C" || tok == "D" -> { i++; Var(tok[0] - 'A') }
                tok.all { it.isDigit() || it == '.' } -> { i++; Const(tok.toDouble()) }
                else -> throw IllegalArgumentException("Formülde beklenmeyen '$tok'")
            }
        }
    }
}
