package com.nemotron.voiceime.dhizuku

import android.util.Log
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.StringReader
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Bounded root UI read. Never taps a window belonging to another package. */
internal object UberWalletReader {
    private const val PKG = "com.ubercab.driver"
    private const val FILE = "/data/local/tmp/nemotron_uber_wallet.xml"
    private val money = Regex("^(-)?\\$([0-9]{1,3}(?:,[0-9]{3})*|[0-9]+)\\.([0-9]{2})(?:\\s*MXN)?$")
    private val zone = ZoneId.of("America/Mexico_City")

    @Synchronized fun read(): JSONObject? = try {
        command("cmd statusbar collapse")
        command("am start -n $PKG/com.ubercab.carbon.core.CarbonActivity")
        var result: JSONObject? = null
        var previous: Long? = null
        for (attempt in 0 until 20) {
            Thread.sleep(750)
            val nodes = dump() ?: continue
            val texts = nodes.map { it.getAttribute("text").trim() }
            val wallet = texts.any { it == "Billetera" }
            val balanceIndex = texts.indexOf("Saldo")
            if (wallet && balanceIndex >= 0) {
                val candidates = texts.drop(balanceIndex + 1).filter { it.isNotBlank() }
                val match = candidates.firstOrNull()?.let { money.matchEntire(it) }
                if (match == null) continue // wallet balance can render after its heading
                val amount = BigDecimal((match.groupValues[1]) + match.groupValues[2].replace(",", "") + "." + match.groupValues[3])
                val cents = amount.movePointRight(2).longValueExact()
                if (cents !in -100_000_000L..100_000_000L) break
                if (previous == cents) {
                    val now = Instant.now()
                    val day = now.atZone(zone).toLocalDate()
                    result = JSONObject().put("amountCents", cents).put("currency", "MXN")
                        .put("date", day.toString()).put("capturedAt", now.toString())
                        .put("weekStart", day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString())
                        .put("source", "uber_driver_wallet_ui").put("valid", true)
                    break
                }
                previous = cents
                continue
            }
            val walletNode = nodes.firstOrNull { it.getAttribute("text") == "Billetera" }
            val menuNode = nodes.firstOrNull { it.getAttribute("resource-id") == "$PKG:id/glide_bottom_nav_menu" }
            when {
                walletNode != null -> tap(walletNode)
                menuNode != null -> tap(menuNode)
                else -> break
            }
        }
        if (result == null) Log.w("UberWalletReader", "No confirmed wallet balance; no amount reported")
        result
    } catch (error: Exception) {
        Log.w("UberWalletReader", "Wallet read failed: ${error.javaClass.simpleName}")
        null
    } finally {
        command("rm -f $FILE")
    }

    /** Uber exposes the wallet as a screen inside CarbonActivity, not its own activity. */
    fun returnHome() {
        command("input keyevent KEYCODE_HOME")
    }

    private fun dump(): List<Element>? {
        val foreground = command("dumpsys activity activities") ?: return null
        if (!foreground.lineSequence().any { it.contains("topResumedActivity") && it.contains("$PKG/") }) return null
        command("uiautomator dump --compressed $FILE") ?: return null
        val xml = command("cat $FILE") ?: return null
        val start = xml.indexOf("<?xml")
        if (start < 0) return null
        // This is XML produced locally by Android, never a network document.
        // Android's DocumentBuilderFactory does not implement Xerces features.
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) return null
        val factory = DocumentBuilderFactory.newInstance()
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml.substring(start))))
        val items = document.getElementsByTagName("node")
        return (0 until items.length).map { items.item(it) as Element }
            .filter { it.getAttribute("package") == PKG }
    }

    private fun tap(node: Element) {
        val values = Regex("[0-9]+").findAll(node.getAttribute("bounds")).map { it.value.toInt() }.toList()
        if (values.size != 4) return
        val foreground = command("dumpsys activity activities") ?: return
        if (!foreground.lineSequence().any { it.contains("topResumedActivity") && it.contains("$PKG/") }) return
        command("input tap ${(values[0] + values[2]) / 2} ${(values[1] + values[3]) / 2}")
    }

    private fun command(script: String): String? = RootShellManager.execShellFresh(arrayOf("sh", "-c", script), 15_000L)
}
