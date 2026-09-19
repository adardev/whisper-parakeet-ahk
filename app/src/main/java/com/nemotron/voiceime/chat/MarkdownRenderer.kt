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
                // TextView understands the alignment on <p>; using a serif face here
                // makes equations read like typeset math without changing prose.
                "<br><p align=\"center\"><font face=\"serif\" color='#F1F3F7'><big>$escaped</big></font></p><br>"
            } else {
                "<font face=\"serif\" color='#E7ECF5'>$escaped</font>"
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
            value = value.replace(Regex("""\\frac\{([^{}]*)\}\{([^{}]*)\}"""), "$1⁄$2")
            value = value.replace(Regex("""\\sqrt\{([^{}]*)\}"""), "√($1)")
        }
        val commands = mapOf(
            "\\partial" to "∂", "\\nabla" to "∇", "\\int" to "∫", "\\sum" to "Σ",
            "\\prod" to "Π", "\\infty" to "∞", "\\times" to "×", "\\cdot" to "·",
            "\\approx" to "≈", "\\pm" to "±", "\\leq" to "≤", "\\geq" to "≥",
            "\\neq" to "≠", "\\equiv" to "≡", "\\in" to "∈", "\\notin" to "∉",
            "\\pi" to "π", "\\theta" to "θ", "\\alpha" to "α", "\\beta" to "β",
            "\\gamma" to "γ", "\\Delta" to "Δ", "\\lambda" to "λ", "\\mu" to "μ",
            "\\quad" to " ", "\\qquad" to "  ", "\\," to " ", "\\;" to " ",
            "\\!" to "", "\\left" to "", "\\right" to ""
        )
        commands.forEach { (key, replacement) -> value = value.replace(key, replacement) }
        value = value.replace("\\\\", "\n")
        value = value.replace(Regex("""\\(begin|end)\s*\{[^{}]*\}"""), "")
        value = value.replace(Regex("&+"), " ")
        value = value.replace(Regex("""\\text\{([^{}]*)\}"""), "$1")
        value = value.replace(Regex("""\\mathrm\{([^{}]*)\}"""), "$1")
        value = value.replace(Regex("""\^\{([^{}]*)\}""")) { toSuperscript(it.groupValues[1]) }
        value = value.replace(Regex("""_\{([^{}]*)\}""")) { toSubscript(it.groupValues[1]) }
        value = value.replace(Regex("""\^([A-Za-z0-9]+)""")) { toSuperscript(it.groupValues[1]) }
        value = value.replace(Regex("""_([A-Za-z0-9]+)""")) { toSubscript(it.groupValues[1]) }
        value = value.replace("{", "").replace("}", "")
        value = value.replace(Regex("""\\[A-Za-z]+"""), "")
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
