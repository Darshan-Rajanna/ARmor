package com.example.armor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat

class SmsHelper {
    companion object {
        fun sendSms(context: Context, phoneNumber: String, message: String) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.SEND_SMS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}