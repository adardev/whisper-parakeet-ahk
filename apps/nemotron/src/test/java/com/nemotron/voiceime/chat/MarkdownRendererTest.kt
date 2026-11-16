package com.nemotron.voiceime.chat

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MarkdownRendererTest {
    @Test fun keepsHeadingEscapingAndTaskListFormatting() {
        assertEquals("<br><big><b>Title</b></big><br><b>bold</b> &amp; &lt;xml&gt;<br>☐ item<br>☑ done",
            MarkdownRenderer.toHtml("# Title\n**bold** & <xml>\n- [ ] item\n- [x] done"))
    }

    @Test fun keepsMathAndTableFormattingAcrossRepeatedCalls() {
        val source = "| Value | Formula |\n| --- | --- |\n| **energy** | \$E=mc^2\$ |"
        val expected = MarkdownRenderer.toHtml(source)
        assertTrue(expected.contains("<b>energy</b>"))
        assertTrue(expected.contains("E=mc²"))
        assertFalse(expected.contains("@@MATH"))
        assertFalse(expected.contains("@@TABLE"))
        repeat(20) { assertEquals(expected, MarkdownRenderer.toHtml(source)) }
        val fraction = MarkdownRenderer.toHtml("\$\$x^{2}+\\frac{1}{2}\$\$")
        assertTrue(fraction.contains("x²+1⁄2"))
        assertTrue(fraction.contains("<p align=\"center\">"))
    }

    @Test fun compiledPatternsCanBeSharedWithoutSharingMessageState() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 40).map { index -> Callable {
                val source = "**message $index**"
                assertEquals("<b>message $index</b>", MarkdownRenderer.toHtml(source))
            } }
            executor.invokeAll(tasks).forEach { it.get(5, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
    }
}
