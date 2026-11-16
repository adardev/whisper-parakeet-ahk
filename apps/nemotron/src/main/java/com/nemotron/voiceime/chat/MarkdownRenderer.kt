package com.nemotron.voiceime.chat

import androidx.core.text.HtmlCompat
import java.util.concurrent.ConcurrentHashMap

/** Markdown ligero con bloques matemáticos legibles para TextView. */
object MarkdownRenderer {
    // All patterns are private constant expressions. Reuse their compiled
    // forms across rows and nested math/table rendering.
    private val patterns = ConcurrentHashMap<String, Regex>()
    private val mathCommands = mapOf(
        "\\partial" to "∂", "\\nabla" to "∇", "\\int" to "∫", "\\sum" to "Σ",
        "\\prod" to "Π", "\\infty" to "∞", "\\times" to "×", "\\cdot" to "·",
        "\\approx" to "≈", "\\pm" to "±", "\\leq" to "≤", "\\geq" to "≥",
        "\\neq" to "≠", "\\equiv" to "≡", "\\in" to "∈", "\\notin" to "∉",
        "\\pi" to "π", "\\theta" to "θ", "\\alpha" to "α", "\\beta" to "β",
        "\\gamma" to "γ", "\\Delta" to "Δ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\to" to "→", "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\iff" to "⇔", "\\land" to "∧", "\\lor" to "∨", "\\therefore" to "∴",
        "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ",
        "\\!" to "", "\\left" to "", "\\right" to ""
    )
    private fun pattern(expression: String): Regex = patterns.computeIfAbsent(expression) { Regex(it) }

    fun render(source: String): android.text.Spanned =
        HtmlCompat.fromHtml(toHtml(source), HtmlCompat.FROM_HTML_MODE_LEGACY)

    internal fun toHtml(source: String): String {
        var body = source.replace("\r\n", "\n")
        val math = mutableListOf<String>()

        fun saveMath(value: String, block: Boolean): String {
            val normalized = normalizeMath(value.trim())
            val escaped = escape(normalized).replace("\n", "<br>")
            val html = if (block) {
                // TextView understands the alignment on <p>; using a serif face here
                // makes equations read like typeset math without changing prose.
                "<br><p align=\"center\"><font face=\"serif\" color='#F1F3F7'><big>$escaped</big></font></p><br>"
            } else {
                "<font face=\"serif\" color='#E7ECF5'>$escaped</font>"
            }
            math.add(html)
            return "@@MATH${math.lastIndex}@@"
        }

        body = body.replace(pattern("""(?s)\$\$(.+?)\$\$""")) { saveMath(it.groupValues[1], true) }
        body = body.replace(pattern("""(?s)\\\[(.+?)\\\]""")) { saveMath(it.groupValues[1], true) }
        body = body.replace(pattern("""(?m)^\s*\\\((.+?)\\\)\s*$""")) { saveMath(it.groupValues[1], true) }
        body = body.replace(pattern("""(?s)\\\((.+?)\\\)""")) { saveMath(it.groupValues[1], false) }
        body = body.replace(pattern("""(?m)^\s*\$([^$\n]+)\$\s*$""")) { saveMath(it.groupValues[1], true) }
        body = body.replace(pattern("""\$([^$\n]+)\$""")) { saveMath(it.groupValues[1], false) }

        val tables = mutableListOf<String>()
        body = extractTables(body, tables)

        var html = escape(body)
        html = html.replace(pattern("""(?m)^#{1}\s+(.+)$""")) { "<br><big><b>${it.groupValues[1]}</b></big>" }
        html = html.replace(pattern("""(?m)^#{2}\s+(.+)$""")) { "<br><b>${it.groupValues[1]}</b>" }
        html = html.replace(pattern("""(?m)^#{3,6}\s+(.+)$""")) { "<b>${it.groupValues[1]}</b>" }
        html = html.replace(pattern("""(?m)^\s*[-*+]\s+\[ \]\s+"""), "☐ ")
        html = html.replace(pattern("""(?m)^\s*[-*+]\s+\[[xX]\]\s+"""), "☑ ")
        html = html.replace(pattern("""(?m)^\s*[-*+]\s+"""), "• ")
        html = html.replace(pattern("""(?m)^\s*\d+[.)]\s+"""), "• ")
        html = html.replace(pattern("""(?m)^\s*>\s?"""), "│ ")
        html = html.replace(pattern("""(?m)^\s*(?:---+|___+|\*\*\*+)\s*$"""), "<br>────────────<br>")
        html = html.replace(pattern("```(?:[a-zA-Z0-9_+-]+)?\n?([\\s\\S]*?)```")) {
            "<br><tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt><br>"
        }
        html = html.replace(pattern("`([^`]+)`")) { "<tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt>" }
        html = html.replace(pattern("\\[([^]]+)]\\(([^)]+)\\)")) { "<a href='${it.groupValues[2]}'>${it.groupValues[1]}</a>" }
        html = html.replace(pattern("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) {
            "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>"
        }
        html = html.replace(pattern("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")) {
            "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>"
        }
        html = html.replace(pattern("~~([^~]+)~~")) { "<strike>${it.groupValues[1]}</strike>" }
        html = html.replace("\n", "<br>")
        // Tables must be expanded before math placeholders are restored so
        // equations inside table cells are rendered too.
        tables.forEachIndexed { index, value -> html = html.replace("@@TABLE$index@@", value) }
        math.forEachIndexed { index, value -> html = html.replace("@@MATH$index@@", value) }
        return html
    }

    /** Turns GitHub-style pipe tables into readable, monospaced table blocks. */
    private fun extractTables(source: String, tables: MutableList<String>): String {
        val lines = source.split("\n")
        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            if (i + 1 < lines.size && isTableRow(lines[i]) && isSeparatorRow(lines[i + 1])) {
                val rows = mutableListOf(parseCells(lines[i]))
                i += 2 // header + separator
                while (i < lines.size && isTableRow(lines[i])) {
                    rows += parseCells(lines[i])
                    i++
                }
                if (rows.first().isNotEmpty()) {
                    tables += tableHtml(rows)
                    out += "@@TABLE${tables.lastIndex}@@"
                    continue
                }
            }
            out += lines[i]
            i++
        }
        return out.joinToString("\n")
    }

    private fun isTableRow(line: String): Boolean {
        val value = line.trim()
        return value.count { it == '|' } >= 1 && value.replace("|", "").trim().isNotEmpty()
    }

    private fun isSeparatorRow(line: String): Boolean =
        isTableRow(line) && parseCells(line).isNotEmpty() &&
            parseCells(line).all { it.replace(":", "").trim().matches(pattern("-{3,}")) }

    private fun parseCells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    private fun tableHtml(rows: List<List<String>>): String {
        val columns = rows.maxOf { it.size }
        val normalized = rows.map { row -> (row + List(columns - row.size) { "" }).take(columns) }

        fun cell(value: String): String {
            // Keep Markdown inside cells (for example **Batería**) instead of
            // displaying the asterisks literally.
            var result = escape(value)
            result = result.replace(pattern("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) {
                "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>"
            }
            result = result.replace(pattern("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) {
                "<i>${it.groupValues[1]}</i>"
            }
            return result
        }

        val header = normalized.first().joinToString("&nbsp;&nbsp;│&nbsp;&nbsp;") { cell(it) }
        val body = normalized.drop(1).joinToString("<br>") { row ->
            val values = row.map { cell(it) }.filter { it.isNotBlank() }
            if (values.isEmpty()) "" else
                "<font color='#8FC1FF'><b>${values.first()}</b></font>" +
                    if (values.size > 1) {
                        "&nbsp;&nbsp;│&nbsp;&nbsp;${values.drop(1).joinToString("&nbsp;&nbsp;│&nbsp;&nbsp;")}"
                    } else ""
        }
        return "<br><font color='#8FC1FF'><b>$header</b></font><br>" +
            "<font color='#526B92'>────────────────────────</font><br>" +
            body + "<br>"
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun normalizeMath(source: String): String {
        var value = source
        repeat(3) {
            value = value.replace(pattern("""\\frac\{([^{}]*)\}\{([^{}]*)\}"""), "$1⁄$2")
            value = value.replace(pattern("""\\sqrt\{([^{}]*)\}"""), "√($1)")
        }
        mathCommands.forEach { (key, replacement) -> value = value.replace(key, replacement) }
        value = value.replace("\\\\", "\n")
        value = value.replace(pattern("""\\(begin|end)\s*\{[^{}]*\}"""), "")
        value = value.replace(pattern("&+"), " ")
        value = value.replace(pattern("""\\text\{([^{}]*)\}"""), "$1")
        value = value.replace(pattern("""\\mathrm\{([^{}]*)\}"""), "$1")
        value = value.replace(pattern("""\\(mathbf|mathbb|mathcal|operatorname)\{([^{}]*)\}"""), "$2")
        value = value.replace(pattern("""\\(overline|underline|hat|bar)\{([^{}]*)\}"""), "$2")
        value = value.replace(pattern("""\^\{([^{}]*)\}""")) { toSuperscript(it.groupValues[1]) }
        value = value.replace(pattern("""_\{([^{}]*)\}""")) { toSubscript(it.groupValues[1]) }
        value = value.replace(pattern("""\^([A-Za-z0-9]+)""")) { toSuperscript(it.groupValues[1]) }
        value = value.replace(pattern("""_([A-Za-z0-9]+)""")) { toSubscript(it.groupValues[1]) }
        value = value.replace("{", "").replace("}", "")
        value = value.replace(pattern("""\\[A-Za-z]+"""), "")
        return value.trim()
    }

    private fun toSuperscript(value: String): String = value.map {
        when (it) {
            '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
            '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
            '+' -> '⁺'; '-' -> '⁻'; '=' -> '⁼'; '(' -> '⁽'; ')' -> '⁾'
            'a' -> 'ᵃ'; 'b' -> 'ᵇ'; 'c' -> 'ᶜ'; 'd' -> 'ᵈ'; 'e' -> 'ᵉ'; 'f' -> 'ᶠ'
            'g' -> 'ᵍ'; 'h' -> 'ʰ'; 'i' -> 'ⁱ'; 'j' -> 'ʲ'; 'k' -> 'ᵏ'; 'l' -> 'ˡ'
            'm' -> 'ᵐ'; 'n' -> 'ⁿ'; 'o' -> 'ᵒ'; 'p' -> 'ᵖ'; 'r' -> 'ʳ'; 's' -> 'ˢ'
            't' -> 'ᵗ'; 'u' -> 'ᵘ'; 'v' -> 'ᵛ'; 'w' -> 'ʷ'; 'x' -> 'ˣ'; 'y' -> 'ʸ'; 'z' -> 'ᶻ'
            else -> it
        }
    }.joinToString("")

    private fun toSubscript(value: String): String = value.map {
        when (it) {
            '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
            '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; '9' -> '₉'
            '+' -> '₊'; '-' -> '₋'; '=' -> '₌'; '(' -> '₍'; ')' -> '₎'
            'a' -> 'ₐ'; 'e' -> 'ₑ'; 'h' -> 'ₕ'; 'i' -> 'ᵢ'; 'j' -> 'ⱼ'; 'k' -> 'ₖ'
            'l' -> 'ₗ'; 'm' -> 'ₘ'; 'n' -> 'ₙ'; 'o' -> 'ₒ'; 'p' -> 'ₚ'; 'r' -> 'ᵣ'
            's' -> 'ₛ'; 't' -> 'ₜ'; 'u' -> 'ᵤ'; 'v' -> 'ᵥ'; 'x' -> 'ₓ'
            else -> it
        }
    }.joinToString("")
}
