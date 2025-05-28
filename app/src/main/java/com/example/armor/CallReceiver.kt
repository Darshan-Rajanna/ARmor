package com.example.armor

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class CallReceiver : BroadcastReceiver() {

    private var activityRef: WeakReference<MainActivity>? = null

    fun setActivity(activity: MainActivity) {
        activityRef = WeakReference(activity)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val mainActivity = activityRef?.get()

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                var incomingNumber = "Unknown"

                // Read incoming number only if permission is granted
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALL_LOG
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        ?: "Unknown"
                }

                Log.d("CallReceiver", "Incoming call from $incomingNumber")
                mainActivity?.sendData("CALL: Incoming from $incomingNumber")
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d("CallReceiver", "Call answered")  
                mainActivity?.sendData("CALL: Answered")
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d("CallReceiver", "Call ended")
                mainActivity?.sendData("CALL: Ended")
            }
        }
    }
}
