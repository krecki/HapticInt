
package com.kreckapps.hapticinterface.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

class PendantBluetoothManager(context: Context) {

    companion object {
        const val DEVICE_NAME = "CNC_Mill_Pendant"

        // Standard Bluetooth Classic Serial Port Profile UUID.
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    interface Listener {
        fun onConnectionStateChanged(state: ConnectionState, message: String)
        fun onMessageReceived(message: String)
    }

    var listener: Listener? = null

    private val appContext = context.applicationContext

    private val bluetoothAdapter =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter

    private val mainHandler = Handler(Looper.getMainLooper())

    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var connecting = false

    private val writeLock = Any()

    private fun reportState(
        state: ConnectionState,
        message: String
    ) {
        mainHandler.post {
            listener?.onConnectionStateChanged(state, message)
        }
    }

    private fun reportMessage(message: String) {
        mainHandler.post {
            listener?.onMessageReceived(message)
        }
    }

    /**
     * Connects to the already-paired pendant ESP32.
     */
    @SuppressLint("MissingPermission")
    fun connect() {

        if (socket != null || connecting) return

        if (bluetoothAdapter == null) {
            reportState(
                ConnectionState.ERROR,
                "This device does not support Bluetooth."
            )
            return
        }

        connecting = true

        reportState(
            ConnectionState.CONNECTING,
            "Connecting to $DEVICE_NAME..."
        )

        executor.execute {

            var candidate: BluetoothSocket? = null

            try {

                if (!bluetoothAdapter.isEnabled) {
                    throw IOException("Bluetooth is turned off.")
                }

                // Look for the pendant among previously paired devices.
                val device = bluetoothAdapter.bondedDevices
                    .firstOrNull { it.name == DEVICE_NAME }
                    ?: throw IOException(
                        "$DEVICE_NAME is not paired with this phone."
                    )

                // Create the Bluetooth Classic SPP connection.
                candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)

                socket = candidate

                candidate.connect()

                connecting = false

                reportState(
                    ConnectionState.CONNECTED,
                    "Connected to $DEVICE_NAME"
                )

                // Keep listening for messages until disconnected.
                readMessages(candidate)

            } catch (e: Exception) {

                reportState(
                    ConnectionState.ERROR,
                    e.message ?: "Bluetooth connection failed."
                )

            } finally {

                connecting = false

                try {
                    candidate?.close()
                } catch (_: IOException) {
                }

                if (socket === candidate) {
                    socket = null

                    reportState(
                        ConnectionState.DISCONNECTED,
                        "Disconnected"
                    )
                }
            }
        }
    }

    /**
     * Reads incoming messages from the pendant.
     *
     * Status reports end with '>'.
     * Ordinary grblHAL messages end with a newline.
     */
    private fun readMessages(activeSocket: BluetoothSocket) {

        val input = activeSocket.inputStream
        val buffer = StringBuilder()

        var receivingStatus = false

        val byteArray = ByteArray(256)

        while (socket === activeSocket) {

            val count = input.read(byteArray)

            if (count < 0) break

            for (i in 0 until count) {

                val character = byteArray[i].toInt().toChar()

                if (character == '<') {

                    buffer.clear()
                    buffer.append(character)
                    receivingStatus = true

                } else if (receivingStatus) {

                    buffer.append(character)

                    if (character == '>') {
                        reportMessage(buffer.toString())
                        buffer.clear()
                        receivingStatus = false
                    }

                } else if (character == '\n') {

                    val message = buffer.toString().trim()

                    if (message.isNotEmpty()) {
                        reportMessage(message)
                    }

                    buffer.clear()

                } else if (character != '\r') {

                    buffer.append(character)
                }
            }
        }
    }

    /**
     * Sends a normal grblHAL command followed by a newline.
     *
     * Example: "$G"
     */
    fun sendCommand(command: String): Boolean {

        return try {

            synchronized(writeLock) {

                val output = socket?.outputStream
                    ?: return false

                output.write((command + "\n").toByteArray())
                output.flush()
            }

            true

        } catch (_: IOException) {
            false
        }
    }

    /**
     * Sends a single real-time character without a newline.
     *
     * Example: '?' requests a status report.
     */
    fun sendRealtime(character: Char): Boolean {

        return try {

            synchronized(writeLock) {

                val output = socket?.outputStream
                    ?: return false

                output.write(character.code)
                output.flush()
            }

            true

        } catch (_: IOException) {
            false
        }
    }

    /**
     * Sends an exact sequence of raw bytes without adding a newline.
     * Used for the keypad plugin's binary runtime-setting packets.
     */
    fun sendBytes(bytes: ByteArray): Boolean {
        return try {
            synchronized(writeLock) {
                val output = socket?.outputStream ?: return false
                output.write(bytes)
                output.flush()
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Closes the Bluetooth connection.
     */
    fun disconnect() {

        connecting = false

        val oldSocket = socket
        socket = null

        try {
            oldSocket?.close()
        } catch (_: IOException) {
        }

        reportState(
            ConnectionState.DISCONNECTED,
            "Disconnected"
        )
    }

    /**
     * Releases resources when the manager is no longer needed.
     */
    fun release() {
        disconnect()
        executor.shutdownNow()
    }
}
