package com.nemotron.voiceime.dhizuku

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One bounded BLE session per action. No service, scans, reconnect loop or periodic polling. */
object Fit3AodController {
    private const val TAG = "Fit3Aod"
    data class Result(val confirmed: Boolean, val message: String)

    @SuppressLint("MissingPermission")
    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Result {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return Result(false, "Falta el permiso de dispositivos cercanos de Nemotron.")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return Result(false, "Bluetooth no disponible.")
        if (!adapter.isEnabled) return Result(false, "Enciende Bluetooth para contactar la Fit3.")
        val devices = adapter.bondedDevices.filter {
            it.name.orEmpty().contains("Fit3", true) || it.name.orEmpty().contains("R390", true)
        }
        if (devices.size != 1) return Result(false,
            if (devices.isEmpty()) "No hay una Fit3 emparejada." else "Hay varias Fit3 emparejadas; no se eligió una al azar.")

        return try {
            Session(context.applicationContext, devices.single()).use { session ->
                session.setAod(enabled)
                Result(true, "Fit3 confirmó AOD y Tocar para activar ${if (enabled) "activados" else "desactivados"}; Levantar para activar apagado.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AOD $enabled no confirmado: ${e.message}")
            Result(false, "Fit3: ${e.message ?: "no respondió"}")
        }
    }

    @SuppressLint("MissingPermission")
    private class Session(private val context: Context, private val device: BluetoothDevice) : AutoCloseable {
        private data class Event(val kind: String, val status: Int, val value: Int = 0)
        private val events = LinkedBlockingQueue<Event>()
        private val notifications = LinkedBlockingQueue<ByteArray>()
        private val reassembler = Fit3AodProtocol.Reassembler()
        private var gatt: BluetoothGatt? = null
        private lateinit var writer: BluetoothGattCharacteristic
        private var sapMtu = 0
        private val deadline = SystemClock.elapsedRealtime() + 30_000L

        private val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(link: BluetoothGatt, status: Int, newState: Int) {
                events.offer(Event("connection", status, newState))
                if (newState == BluetoothProfile.STATE_DISCONNECTED) notifications.offer(byteArrayOf())
            }
            override fun onMtuChanged(link: BluetoothGatt, mtu: Int, status: Int) {
                events.offer(Event("mtu", status, mtu))
            }
            override fun onServicesDiscovered(link: BluetoothGatt, status: Int) {
                events.offer(Event("services", status))
            }
            override fun onDescriptorWrite(link: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Log.i(TAG, "Suscripción SAP terminada, status=$status")
                events.offer(Event("subscribe", status))
            }
            override fun onCharacteristicWrite(link: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                events.offer(Event("write", status))
            }
            @Deprecated("Legacy Android callback")
            override fun onCharacteristicChanged(link: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                if (characteristic.uuid == NOTIFY) {
                    val value = characteristic.value.clone()
                    Log.d(TAG, "SAP RX ${value.size} bytes")
                    notifications.offer(value)
                }
            }
            override fun onCharacteristicChanged(link: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid == NOTIFY) {
                    Log.d(TAG, "SAP RX ${value.size} bytes")
                    notifications.offer(value.clone())
                }
            }
        }

        fun setAod(enabled: Boolean) {
            Log.i(TAG, "Conectando directamente a Fit3 para AOD=$enabled")
            val link = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("no se pudo abrir GATT")
            gatt = link
            if (event("connection", 10_000).value != BluetoothProfile.STATE_CONNECTED) throw IOException("Fit3 desconectada")
            if (!link.requestMtu(517)) throw IOException("no se pudo negociar MTU")
            if (event("mtu", 5_000).value < 108) throw IOException("MTU insuficiente")
            if (!link.discoverServices()) throw IOException("no se pudieron buscar los servicios")
            event("services", 5_000)
            val service = link.getService(SERVICE) ?: throw IOException("servicio SAP ausente")
            writer = service.getCharacteristic(WRITE) ?: throw IOException("canal de escritura ausente")
            val notify = service.getCharacteristic(NOTIFY) ?: throw IOException("canal de respuestas ausente")
            val descriptor = notify.getDescriptor(CCCD) ?: throw IOException("descriptor de respuestas ausente")
            if (!link.setCharacteristicNotification(notify, true)) throw IOException("no se pudieron habilitar respuestas")
            val subscribed = if (Build.VERSION.SDK_INT >= 33) {
                link.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                link.writeDescriptor(descriptor)
            }
            if (!subscribed) throw IOException("no se pudo solicitar la suscripción")
            event("subscribe", 5_000)

            // The reference client allows 15 seconds for this first SAP packet.
            val handshakeDeadline = SystemClock.elapsedRealtime() + 15_000
            while (sapMtu == 0) {
                val packet = notification(handshakeDeadline)
                val response = Fit3AodProtocol.capabilityReply(packet) ?: continue
                sapMtu = Fit3AodProtocol.transportMtu(packet)
                write(response)
                Log.i(TAG, "Canal SAP directo negociado, MTU=$sapMtu")
            }

            write(Fit3AodProtocol.frame(Fit3AodProtocol.setAod(enabled), sapMtu))
            write(Fit3AodProtocol.frame(Fit3AodProtocol.setRaiseToWake(false), sapMtu))
            write(Fit3AodProtocol.frame(Fit3AodProtocol.setTouchToWake(enabled), sapMtu))
            // A successful GATT write alone does not mean the setting changed.
            // Read the state twice, including a delayed read to catch an immediate undo.
            Thread.sleep(250)
            queryAndConfirm(enabled)
            Thread.sleep(1_000)
            queryAndConfirm(enabled)
            Log.i(TAG, "VERIFIED AOD=$enabled RAISE_TO_WAKE=false TOUCH_TO_WAKE=$enabled, dos lecturas de la pulsera")
        }

        private fun queryAndConfirm(enabled: Boolean) {
            notifications.clear()
            write(Fit3AodProtocol.frame(Fit3AodProtocol.querySettings(), sapMtu))
            val readDeadline = SystemClock.elapsedRealtime() + 3_000
            while (true) {
                val packet = notification(readDeadline)
                val message = reassembler.push(packet) ?: continue
                val state = Fit3AodProtocol.displayState(message) ?: continue
                Log.i(TAG, "READ AOD=${state.aod} RAISE_TO_WAKE=${state.raiseToWake} TOUCH_TO_WAKE=${state.touchToWake}")
                if (state.aod != enabled) throw IOException("la pulsera devolvió AOD=${state.aod}")
                if (state.raiseToWake != false) throw IOException("la pulsera devolvió Levantar para activar=${state.raiseToWake}")
                if (state.touchToWake != enabled) throw IOException("la pulsera devolvió Tocar para activar=${state.touchToWake}")
                return
            }
        }

        private fun write(bytes: ByteArray) {
            val link = gatt ?: throw IOException("GATT cerrado")
            val acknowledged = writer.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val type = if (acknowledged) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                       else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val accepted = if (Build.VERSION.SDK_INT >= 33) {
                link.writeCharacteristic(writer, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                writer.value = bytes
                writer.writeType = type
                @Suppress("DEPRECATION")
                link.writeCharacteristic(writer)
            }
            if (!accepted) throw IOException("Bluetooth rechazó la escritura")
            if (acknowledged) event("write", 3_000) else Thread.sleep(100)
        }

        private fun event(kind: String, timeoutMs: Long): Event {
            val until = minOf(deadline, SystemClock.elapsedRealtime() + timeoutMs)
            while (true) {
                val remaining = until - SystemClock.elapsedRealtime()
                if (remaining <= 0) throw IOException("tiempo agotado: $kind")
                val result = events.poll(remaining, TimeUnit.MILLISECONDS) ?: throw IOException("sin respuesta: $kind")
                if (result.kind == "connection" && result.value == BluetoothProfile.STATE_DISCONNECTED) throw IOException("enlace cerrado, GATT ${result.status}")
                if (result.kind != kind) continue
                if (result.status != BluetoothGatt.GATT_SUCCESS) throw IOException("$kind falló, GATT ${result.status}")
                return result
            }
        }

        private fun notification(until: Long): ByteArray {
            val remaining = minOf(until, deadline) - SystemClock.elapsedRealtime()
            if (remaining <= 0) throw IOException("sin respuesta SAP de la Fit3")
            val packet = notifications.poll(remaining, TimeUnit.MILLISECONDS) ?: throw IOException("sin respuesta SAP de la Fit3")
            if (packet.isEmpty()) throw IOException("Fit3 se desconectó")
            return packet
        }

        override fun close() {
            gatt?.let { runCatching { it.disconnect() }; runCatching { it.close() } }
            gatt = null
            notifications.clear()
            events.clear()
            Log.i(TAG, "Sesión BLE cerrada")
        }
    }

    private val SERVICE = UUID.fromString("00001a1a-0000-1000-8000-00805f9b34fb")
    private val WRITE = UUID.fromString("63e30bad-4206-4596-839f-e47cbf7a4b5d")
    private val NOTIFY = UUID.fromString("797ae4e9-2e58-4fe8-b48d-b5c79599fb9b")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
