package com.camarodiag.gen5.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.camarodiag.gen5.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothObdManager(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    var isDemoMode: Boolean = true
        private set

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<Pair<String, String>> {
        return try {
            bluetoothAdapter?.bondedDevices
                ?.map { (it.name ?: "Unknown OBD Device") to it.address }
                ?.sortedByDescending { it.first.contains("OBD", true) || it.first.contains("ELM", true) || it.first.contains("vLinker", true) }
                ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectAndProbe(macAddress: String): Result<AdapterCapabilities> = withContext(Dispatchers.IO) {
        disconnect()
        try {
            val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(macAddress)
                ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter unavailable"))

            val btSocket = device.createRfcommSocketToServiceRecord(sppUuid)
            bluetoothAdapter?.let { it.cancelDiscovery() }
            btSocket.connect()

            socket = btSocket
            inputStream = btSocket.inputStream
            outputStream = btSocket.outputStream
            isDemoMode = false

            val atz = sendAtCommand("ATZ")
            sendAtCommand("ATE0")
            sendAtCommand("ATL0")
            sendAtCommand("ATH1")
            val sti = sendAtCommand("STI")
            val atal = sendAtCommand("ATAL")
            val stp61 = sendAtCommand("STP 61")
            sendAtCommand("ATSP 6")

            val caps = CamaroUdsProtocol.evaluateAdapterProbe(
                deviceName = device.name ?: "Bluetooth OBD-II",
                macAddress = macAddress,
                atzResp = atz,
                stiResp = sti,
                atalResp = atal,
                stp61Resp = stp61
            )
            Result.success(caps)
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    suspend fun activateSimulatedAdapter(preset: String): AdapterCapabilities = withContext(Dispatchers.Default) {
        disconnect()
        isDemoMode = true
        delay(350)
        when (preset) {
            "MX_PLUS" -> CamaroUdsProtocol.evaluateAdapterProbe(
                deviceName = "OBDLink MX+ (Simulated)",
                macAddress = "00:04:3E:8A:12:9F",
                atzResp = "ELM327 v1.4b",
                stiResp = "STN2255 v5.6.5",
                atalResp = "OK",
                stp61Resp = "OK"
            )
            "ELM_15" -> CamaroUdsProtocol.evaluateAdapterProbe(
                deviceName = "Genuine ELM327 v1.5 (Simulated)",
                macAddress = "00:1D:A5:01:44:2B",
                atzResp = "ELM327 v1.5",
                stiResp = "?",
                atalResp = "OK",
                stp61Resp = "?"
            )
            else -> CamaroUdsProtocol.evaluateAdapterProbe(
                deviceName = "Generic ELM327 v2.1 Clone (Simulated)",
                macAddress = "AA:BB:CC:11:22:33",
                atzResp = "ELM327 v2.1",
                stiResp = "?",
                atalResp = "?",
                stp61Resp = "?"
            )
        }
    }

    suspend fun sendAtCommand(command: String, timeoutMs: Long = 1200L): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext "NO_SOCKET"
        val inp = inputStream ?: return@withContext "NO_SOCKET"

        while (inp.available() > 0) inp.read()
        out.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
        out.flush()

        val buffer = StringBuilder()
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (inp.available() > 0) {
                val c = inp.read().toChar()
                if (c == '>') break
                buffer.append(c)
            } else {
                delay(15)
            }
        }
        buffer.toString()
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(command, "")
            .trim()
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {
        } finally {
            inputStream = null
            outputStream = null
            socket = null
        }
    }
}
