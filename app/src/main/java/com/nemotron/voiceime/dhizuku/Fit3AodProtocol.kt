package com.nemotron.voiceime.dhizuku

import java.io.ByteArrayOutputStream

/** Minimal Fit3 SAP codec for the AOD setting and its full-settings readback. */
internal object Fit3AodProtocol {
    private const val SETTINGS_SERVICE = 11
    private const val DISPLAY_MESSAGE = 6
    private const val FULL_SETTINGS_MESSAGE = 32
    private const val AOD_PARAMETER = 9
    private const val RAISE_TO_WAKE_PARAMETER = 10

    fun setAod(enabled: Boolean) = byteArrayOf(
        DISPLAY_MESSAGE.toByte(), AOD_PARAMETER.toByte(), if (enabled) 1 else 0
    )

    fun setRaiseToWake(enabled: Boolean) = byteArrayOf(
        DISPLAY_MESSAGE.toByte(), RAISE_TO_WAKE_PARAMETER.toByte(), if (enabled) 1 else 0
    )

    fun querySettings() = byteArrayOf(FULL_SETTINGS_MESSAGE.toByte())

    fun capabilityReply(packet: ByteArray): ByteArray? {
        if (packet.size != 105 || packet[0] != 104.toByte() || packet[1] != 20.toByte()) return null
        return packet.clone().apply { this[1] = 21 }
    }

    fun transportMtu(capability: ByteArray): Int =
        ((capability[44].toInt() and 255) * 256 + (capability[45].toInt() and 255))
            .also { require(it in 15..500) { "MTU SAP inválido: $it" } }

    fun frame(message: ByteArray, mtu: Int): ByteArray {
        val body = byteArrayOf(0, 0x40, SETTINGS_SERVICE.toByte(), SETTINGS_SERVICE.toByte()) + message
        val length = if (mtu < 256) byteArrayOf(body.size.toByte(), body.size.toByte())
        else byteArrayOf((body.size / 256).toByte(), body.size.toByte()).let { it + checksum(it) }
        return (length + body + checksum(body)).also { require(it.size <= mtu) }
    }

    /** Returns the decoded Fit3 settings message only for complete SAP packets with valid CRCs. */
    fun settingsMessage(packet: ByteArray): ByteArray? {
        if (packet.size < 8) return null
        val a = packet[0].toInt() and 255
        val b = packet[1].toInt() and 255
        val prefix = when {
            a == b && packet.size == a + 4 -> 2
            packet.size == a * 256 + b + 6 && packet.size >= 10 &&
                packet.copyOfRange(2, 4).contentEquals(checksum(packet.copyOfRange(0, 2))) -> 4
            else -> return null
        }
        val body = packet.copyOfRange(prefix, packet.size - 2)
        if (!packet.copyOfRange(packet.size - 2, packet.size).contentEquals(checksum(body))) return null
        if (body.size < 5 || body[0] != 0.toByte() || body[2] != SETTINGS_SERVICE.toByte() || body[3] != body[2]) return null
        return body.copyOfRange(4, body.size)
    }

    /** Decode one SAP transport frame and reassemble multi-frame settings replies. */
    class Reassembler {
        private var serviceId: Int? = null
        private var stream = -1
        private var nextSequence = 0
        private val data = ByteArrayOutputStream()

        fun push(frame: ByteArray): ByteArray? {
            val fragment = decodeFrame(frame) ?: return null
            if (fragment.kind == 0) {
                reset()
                return fragment.serviceId?.takeIf { it == SETTINGS_SERVICE }?.let { fragment.payload }
            }
            if (fragment.kind == 1) {
                reset()
                if (fragment.sequence != 0 || fragment.serviceId != SETTINGS_SERVICE) return null
                serviceId = fragment.serviceId
                stream = fragment.stream
                nextSequence = 1
                data.write(fragment.payload)
                return null
            }
            if (serviceId != SETTINGS_SERVICE || fragment.stream != stream ||
                fragment.sequence != nextSequence || fragment.kind !in 2..3 || data.size() + fragment.payload.size > 4096) {
                reset()
                return null
            }
            data.write(fragment.payload)
            nextSequence++
            if (fragment.kind != 3) return null
            return data.toByteArray().also { reset() }
        }

        private data class Fragment(val kind: Int, val sequence: Int, val stream: Int, val serviceId: Int?, val payload: ByteArray)

        private fun decodeFrame(frame: ByteArray): Fragment? {
            return try {
            if (frame.size < 8) return null
            val a = frame[0].toInt() and 255
            val b = frame[1].toInt() and 255
            val prefix = when {
                a == b && a + 4 == frame.size -> 2
                frame.size >= 10 && a * 256 + b + 6 == frame.size &&
                    frame.copyOfRange(2, 4).contentEquals(checksum(frame.copyOfRange(0, 2))) -> 4
                else -> return null
            }
            val body = frame.copyOfRange(prefix, frame.size - 2)
            if (!frame.copyOfRange(frame.size - 2, frame.size).contentEquals(checksum(body)) || body.size < 2) return null
            val flags = body[0].toInt() and 255
            if ((flags ushr 6) != 0) return null
            val kind = (flags ushr 1) and 3
            val seq = body[1].toInt() and 255
            val first = kind == 0 || kind == 1
            if (first && (body.size < 4 || body[2] != body[3])) return null
            Fragment(kind, seq and 15, seq ushr 5,
                if (first) body[2].toInt() and 255 else null,
                body.copyOfRange(if (first) 4 else 2, body.size))
            } catch (_: Exception) { null }
        }

        private fun reset() { serviceId = null; stream = -1; nextSequence = 0; data.reset() }
    }

    /** Full settings response: header 0x60, count, then parameter ID/value pairs. */
    data class DisplayState(val aod: Boolean, val raiseToWake: Boolean?)

    fun displayState(message: ByteArray): DisplayState? {
        if (message.size < 3 || message[0] != 0x60.toByte()) return null
        val count = message[1].toInt() and 255
        if (count !in 1..64) return null
        var i = 2
        var aod: Boolean? = null
        var raiseToWake: Boolean? = null
        repeat(count) {
            if (i + 1 >= message.size) return null
            val id = message[i++].toInt() and 255
            if (id in setOf(5, 6, 14, 15, 16, 17, 18)) {
                if (i + 1 >= message.size) return null
                val value = (message[i].toInt() and 255) or ((message[i + 1].toInt() and 255) shl 8)
                i += 2
                if (id == AOD_PARAMETER) aod = value != 0
                if (id == RAISE_TO_WAKE_PARAMETER) raiseToWake = value != 0
            } else {
                if (i >= message.size) return null
                val value = message[i++].toInt() and 255
                if (id == AOD_PARAMETER) aod = value != 0
                if (id == RAISE_TO_WAKE_PARAMETER) raiseToWake = value != 0
            }
        }
        return aod?.let { DisplayState(it, raiseToWake) }
    }

    private fun checksum(bytes: ByteArray): ByteArray {
        var crc = 0
        for (value in bytes) {
            crc = crc xor (value.toInt() and 255)
            for (bit in 0 until 8) crc = if (crc and 1 == 0) crc ushr 1 else (crc ushr 1) xor 0xa001
        }
        return byteArrayOf((crc ushr 8).toByte(), crc.toByte())
    }
}
