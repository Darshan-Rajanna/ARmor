package com.example.armor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.armor.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.widget.NestedScrollView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ALL_PERMISSIONS = 1001
        private const val REQUEST_BLUETOOTH_CONNECT = 1002
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var weatherApiHelper: WeatherApiHelper
    private var esp32Device: BluetoothDevice? = null
    private val callReceiver = CallReceiver()

    private var contact1 = ""
    private var contact2 = ""

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setupBluetooth()
        } else {
            showToast("Bluetooth must be enabled.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothHelper = BluetoothHelper(this).apply {
            setConnectionListener(bluetoothListener)
        }

        callReceiver.setActivity(this)

        weatherApiHelper = WeatherApiHelper.getInstance(this)

        checkAndRequestPermissions()
        setupUI()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            permissions += listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQUEST_ALL_PERMISSIONS)
        } else {
            setupBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_ALL_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    setupBluetooth()
                } else {
                    showToast("Permissions required to run the app.")
                }
            }
            REQUEST_BLUETOOTH_CONNECT -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    discoverDevices()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupBluetooth() {
        if (!bluetoothHelper.isBluetoothEnabled()) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableIntent)
        } else {
            discoverDevices()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun discoverDevices() {
        if (!hasBluetoothConnectPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_CONNECT)
            return
        }

        binding.deviceName.text = "Searching for ESP32..."

        lifecycleScope.launch(Dispatchers.IO) {
            val pairedDevices = bluetoothHelper.getPairedDevices()
            val espDevice = pairedDevices.find { it.name?.contains("ARmor", ignoreCase = true) == true }

            withContext(Dispatchers.Main) {
                if (espDevice != null) {
                    esp32Device = espDevice
                    binding.deviceName.text = "Found: ${espDevice.name}"
                    bluetoothHelper.connectToDevice(espDevice)
                } else {
                    binding.deviceName.text = "ARmor Helmet not found. Please pair it first."
                }
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.split(":")?.any { it.contains(packageName) } == true
    }

    private fun setupUI() {
        binding.connectButton.setOnClickListener {
            if (bluetoothHelper.isConnected()) {
                disconnect()
            } else {
                esp32Device?.let { bluetoothHelper.connectToDevice(it) } ?: discoverDevices()
            }
        }

        binding.saveContactsButton.setOnClickListener {
            contact1 = binding.contact1.text.toString()
            contact2 = binding.contact2.text.toString()
            showToast("Contacts saved.")
        }
    }

    private fun disconnect() {
        bluetoothHelper.disconnect()
    }

    fun sendData(data: String) {
        try {
            if (bluetoothHelper.isConnected()) {
                bluetoothHelper.sendData(data)
                logMessage("Sent: $data")
            }
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error sending data", e)
            disconnect()
        }
    }

    private val bluetoothListener = object : BluetoothHelper.BluetoothConnectionListener {
        override fun onDeviceConnected(device: BluetoothDevice) {
            runOnUiThread {
                val name = if (hasBluetoothConnectPermission()) device.name else "Unknown Device"
                binding.connectionStatus.text = "Connected to $name"
                binding.connectButton.text = "Disconnect"
                logMessage("Connected to $name")

                if (!isNotificationServiceEnabled()) {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    showToast("Enable notification access")
                } else {
                    startService(Intent(this@MainActivity, NotificationListener::class.java))
                }
            }
        }

        override fun onDeviceDisconnected() {
            runOnUiThread {
                binding.connectionStatus.text = "Disconnected"
                binding.connectButton.text = "Connect"
                logMessage("Disconnected")
            }
        }

        override fun onConnectionFailed(error: String) {
            runOnUiThread {
                binding.connectionStatus.text = "Connection failed"
                showToast("Connection failed: $error")
                logMessage("Connection failed: $error")
            }
        }

        override fun onDataReceived(data: String) {
            runOnUiThread {
                logMessage("Received: $data")
                processEsp32Command(data)
            }
        }
    }

    private fun processEsp32Command(command: String) {
        when (command.lowercase(Locale.ROOT)) {
            "sos" -> handleSosCommand()
            "weather" -> handleWeatherCommand()
            "time" -> handleTimeCommand()
            "navigate" -> handleNavigateCommand()
            "btstatus" -> handleBtStatusCommand()
            else -> logMessage("Unknown command: $command")
        }
    }

    private fun handleSosCommand() {
        if (contact1.isEmpty() && contact2.isEmpty()) {
            sendData("SOS: No contacts saved")
            return
        }

        LocationHelper(this).getCurrentLocation(object : LocationHelper.LocationCallback {
            override fun onLocationResult(location: Location) {
                val mapUrl = "https://www.google.com/maps?q=${location.latitude},${location.longitude}"
                val message = "SOS! I need help! Location: $mapUrl"

                if (contact1.isNotEmpty()) SmsHelper.sendSms(this@MainActivity, contact1, message)
                if (contact2.isNotEmpty()) SmsHelper.sendSms(this@MainActivity, contact2, message)

                sendData("SOS sent with location")
            }

            override fun onLocationUnavailable(reason: String) {
                sendData("SOS failed: $reason")
            }
        })
    }

    private fun handleWeatherCommand() {
        LocationHelper(this).getCurrentLocation(object : LocationHelper.LocationCallback {
            override fun onLocationResult(location: Location) {
                weatherApiHelper.getWeather(location.latitude, location.longitude, object : WeatherApiHelper.WeatherCallback {
                    override fun onSuccess(weatherData: WeatherApiHelper.WeatherData) {
                        sendData("Weather: ${weatherData.temp}°C, ${weatherData.description}")
                    }

                    override fun onFailure(errorMessage: String) {
                        sendData("Weather error: $errorMessage")
                        Log.e("WeatherAPI", errorMessage)
                    }
                })
            }

            override fun onLocationUnavailable(reason: String) {
                sendData("Location unavailable: $reason")
            }
        })
    }

    private fun handleTimeCommand() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        sendData("Time: $time, Date: $date")
    }

    private fun handleNavigateCommand() {
        sendData("Navigation mode activated")
    }


    private fun handleBtStatusCommand() {
        sendData(if (bluetoothHelper.isConnected()) "Connected" else "Disconnected")
    }
    private var autoScrollEnabled = true

    private fun logMessage(msg: String) {
        runOnUiThread {
            // Append the new message
            binding.logTextView.append("$msg\n")

            // Get the scroll view reference (add this as a class field if you use it elsewhere)
            val scrollView = binding.root.findViewById<NestedScrollView>(R.id.logScrollView)

            // Smooth scroll to bottom
            scrollView.post {
                scrollView.smoothScrollTo(0, binding.logTextView.bottom)
            }

            // Optional: Limit log size to prevent memory issues
            if (binding.logTextView.lineCount > 500) {
                val lines = binding.logTextView.text.toString().lines()
                binding.logTextView.text = lines.takeLast(250).joinToString("\n")
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver,
            IntentFilter("com.example.armor.NOTIFICATION_EVENT")
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.getStringExtra("data")?.let { sendData(it) }
        }
    }
}
