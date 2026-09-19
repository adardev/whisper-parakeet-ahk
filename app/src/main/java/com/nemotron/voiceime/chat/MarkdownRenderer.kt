package com.nemotron.voiceime.chat

import androidx.core.text.HtmlCompat

/** Markdown ligero con bloques matemáticos legibles para TextView. */
object MarkdownRenderer {
    fun render(source: String): android.text.Spanned {
        var body = source.replace("\r\n", "\n")
        val math = mutableListOf<String>()

        fun saveMath(value: String, block: Boolean): String {
            val normalized = normalizeMath(value.trim())
            val escaped = escape(normalized).replace("\n", "<br>")
            val html = if (block) {
                "<br><br><font color='#A9CBFF'><big><tt>$escaped</tt></big></font><br><br>"
            } else {
                "<font color='#A9CBFF'><tt>$escaped</tt></font>"
            }
            math.add(html)
            return "@@MATH${math.lastIndex}@@"
        }

        body = body.replace(Regex("""(?s)\$\$(.+?)\$\$""")) { saveMath(it.groupValues[1], true) }
        body = body.replace(Regex("""(?s)\\\((.+?)\\\)""")) { saveMath(it.groupValues[1], false) }
        body = body.replace(Regex("""\$([^$\n]+)\$""")) { saveMath(it.groupValues[1], false) }

        var html = escape(body)
        html = html.replace(Regex("""(?m)^#{1,6}\s+(.+)$""")) { "<b>${it.groupValues[1]}</b>" }
        html = html.replace(Regex("""(?m)^[-*]\s+"""), "• ")
        html = html.replace(Regex("```(?:[a-zA-Z0-9_+-]+)?\n?([\\s\\S]*?)```")) {
            "<br><tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt><br>"
        }
        html = html.replace(Regex("`([^`]+)`")) { "<tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt>" }
        html = html.replace(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) {
            "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>"
        }
        html = html.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")) {
            "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>"
        }
        html = html.replace("\n", "<br>")
        math.forEachIndexed { index, value -> html = html.replace("@@MATH$index@@", value) }
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun normalizeMath(source: String): String {
        var value = source
        repeat(3) {
            value = value.replace(Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}"""), "($1 / $2)")
            value = value.replace(Regex("""\\sqrt\{([^{}]*)\}"""), "√($1)")
        }
        val commands = mapOf(
            "\\partial" to "∂", "\\nabla" to "∇", "\\int" to "∫", "\\sum" to "Σ",
            "\\prod" to "Π", "\\infty" to "∞", "\\times" to "×", "\\cdot" to "·",
            "\\approx" to "≈", "\\pm" to "±", "\\leq" to "≤", "\\geq" to "≥",
            "\\neq" to "≠", "\\equiv" to "≡", "\\in" to "∈", "\\notin" to "∉",
            "\\pi" to "π", "\\theta" to "θ", "\\alpha" to "α", "\\beta" to "β",
            "\\gamma" to "γ", "\\Delta" to "Δ", "\\lambda" to "λ", "\\mu" to "μ",
            "\\quad" to "    ", "\\qquad" to "        ", "\\," to " ", "\\;" to " ",
            "\\!" to "", "\\left" to "", "\\right" to ""
        )
        commands.forEach { (key, replacement) -> value = value.replace(key, replacement) }
        value = value.replace(Regex("""\\text\{([^{}]*)\}"""), "$1")
        value = value.replace(Regex("""\\mathrm\{([^{}]*)\}"""), "$1")
        value = value.replace(Regex("""\^([A-Za-z0-9]+)""")) { toSuperscript(it.groupValues[1]) }
        value = value.replace(Regex("""_\{([^{}]*)\}"""), "_$1")
        value = value.replace("{", "").replace("}", "")
        value = value.replace(Regex("""\\[A-Za-z]+"""), "")
        return value.trim()
    }

    private fun toSuperscript(value: String): String = value.map {
        when (it) {
            '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
            '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
            '+' -> '⁺'; '-' -> '⁻'; '=' -> '⁼'; '(' -> '⁽'; ')' -> '⁾'
            else -> it
        }
    }.joinToString("")
}
