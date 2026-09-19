package com.nemotron.voiceime.chat

import android.text.Spanned
import androidx.core.text.HtmlCompat

/** Markdown ligero, sin dependencias externas, optimizado para respuestas del agente. */
object MarkdownRenderer {
    fun render(source: String): Spanned {
        var text = normalizeMath(source)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        text = text.replace(Regex("(?m)^#{1,6}\\s+(.+)$")) { "<b>${it.groupValues[1]}</b>" }
        text = text.replace(Regex("(?m)^[-*]\\s+"), "• ")
        text = text.replace(Regex("```(?:[a-zA-Z0-9_+-]+)?\\n?([\\s\\S]*?)```")) {
            "<tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt>"
        }
        text = text.replace(Regex("`([^`]+)`")) { "<tt><font color='#B8D5FF'>${it.groupValues[1]}</font></tt>" }
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) {
            "<b>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</b>"
        }
        text = text.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")) {
            "<i>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</i>"
        }
        // Fórmulas inline y bloques simples: se distinguen visualmente sin
        // alterar su contenido ni depender de un motor pesado de TeX.
        text = text.replace(Regex("\\$([^$]+)\\$|\\\\\\(([^)]+)\\\\\\)")) {
            "<tt><font color='#9BC4FF'>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</font></tt>"
        }
        text = text.replace("\n", "<br>")
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun normalizeMath(source: String): String {
        var value = source
        value = value.replace(Regex("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}"), "($1 / $2)")
        value = value.replace(Regex("\\\\sqrt\\{([^{}]+)\\}"), "√($1)")
        value = value
            .replace("\\\\times", "×")
            .replace("\\\\cdot", "·")
            .replace("\\\\approx", "≈")
            .replace("\\\\pm", "±")
            .replace("\\\\leq", "≤")
            .replace("\\\\geq", "≥")
            .replace("\\\\neq", "≠")
            .replace("\\\\infty", "∞")
            .replace("\\\\pi", "π")
            .replace("\\\\theta", "θ")
            .replace("\\\\alpha", "α")
            .replace("\\\\beta", "β")
            .replace("\\\\Delta", "Δ")
        value = value.replace(Regex("\\^([0-9]+)")) { toSuperscript(it.groupValues[1]) }
        return value
    }

    private fun toSuperscript(value: String): String = value.map {
        when (it) {
            '0' -> '⁰'; '1' -> '¹'; '2' -> '²'; '3' -> '³'; '4' -> '⁴'
            '5' -> '⁵'; '6' -> '⁶'; '7' -> '⁷'; '8' -> '⁸'; '9' -> '⁹'
            else -> it
        }
    }.joinToString("")
}
