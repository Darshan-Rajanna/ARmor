package com.example.armor

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BluetoothHelper(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var connectedThread: ConnectedThread? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "BluetoothHelper"
    }

    interface BluetoothConnectionListener {
        fun onDeviceConnected(device: BluetoothDevice)
        fun onDeviceDisconnected()
        fun onConnectionFailed(error: String)
        fun onDataReceived(data: String)
    }

    private var connectionListener: BluetoothConnectionListener? = null
    private var isConnecting = false

    fun setConnectionListener(listener: BluetoothConnectionListener) {
        this.connectionListener = listener
    }

    @SuppressLint("MissingPermission")
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices ?: emptySet()
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (isConnecting || isConnected()) {
            return
        }

        isConnecting = true
        executor.execute {
            try {
                bluetoothAdapter?.cancelDiscovery()

                // Create insecure connection (more stable for some devices)
                val socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)

                try {
                    socket.connect()

                    synchronized(this) {
                        bluetoothSocket = socket
                        outputStream = socket.outputStream
                        inputStream = socket.inputStream
                        connectedThread = ConnectedThread().apply { start() }
                    }

                    handler.post {
                        connectionListener?.onDeviceConnected(device)
                    }
                } catch (e: IOException) {
                    socket.close()
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                cleanupConnection()
                handler.post {
                    connectionListener?.onConnectionFailed("Connection failed: ${e.message}")
                }
            } finally {
                isConnecting = false
            }
        }
    }

    private fun cleanupConnection() {
        synchronized(this) {
            try {
                connectedThread?.interrupt()
                connectedThread = null

                bluetoothSocket?.close()
                outputStream?.close()
                inputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error during cleanup", e)
            } finally {
                bluetoothSocket = null
                outputStream = null
                inputStream = null
            }
        }
    }

    fun disconnect() {
        executor.execute {
            cleanupConnection()
            handler.post {
                connectionListener?.onDeviceDisconnected()
            }
        }
    }

    fun isConnected(): Boolean {
        return try {
            bluetoothSocket?.isConnected ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun sendData(data: String) {
        executor.execute {
            if (!isConnected()) {
                handler.post {
                    connectionListener?.onConnectionFailed("Not connected")
                }
                return@execute
            }

            try {
                outputStream?.let { stream ->
                    stream.write(data.toByteArray())
                    stream.flush()
                    Log.d(TAG, "Data sent: $data")
                } ?: run {
                    handler.post {
                        connectionListener?.onConnectionFailed("No output stream")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending data", e)
                disconnect()
            }
        }
    }

    private inner class ConnectedThread : Thread() {
        private val buffer = ByteArray(1024)

        override fun run() {
            while (!isInterrupted()) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes <= 0) {
                        throw IOException("Connection lost")
                    }

                    val message = String(buffer, 0, bytes).trim()
                    Log.d(TAG, "Received: $message")
                    handler.post {
                        connectionListener?.onDataReceived(message)
                    }
                } catch (e: IOException) {
                    if (!isInterrupted()) {
                        Log.e(TAG, "Connection error", e)
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    fun cleanup() {
        disconnect()
        executor.shutdown()
    }
}