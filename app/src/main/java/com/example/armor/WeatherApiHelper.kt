package com.example.armor

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject

class WeatherApiHelper private constructor(context: Context) {
    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val appContext: Context = context.applicationContext

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
        private const val TAG = "WeatherApiHelper"

        @Volatile
        private var instance: WeatherApiHelper? = null

        fun getInstance(context: Context): WeatherApiHelper {
            return instance ?: synchronized(this) {
                instance ?: WeatherApiHelper(context).also { instance = it }
            }
        }
    }

    interface WeatherCallback {
        fun onSuccess(weatherData: WeatherData)
        fun onFailure(errorMessage: String)
    }

    data class WeatherData(
        val temp: Double,
        val description: String,
        val humidity: Int,
        val windSpeed: Double,
        val iconCode: String?
    )

    fun getWeather(lat: Double, lon: Double, callback: WeatherCallback) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            callback.onFailure("API key not configured")
            return
        }

        val url = "$BASE_URL?lat=$lat&lon=$lon&appid=$apiKey&units=metric"

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.GET, url, null,
            { response -> parseWeatherResponse(response, callback) },
            { error -> handleWeatherError(error, callback) }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Accept"] = "application/json"
                return headers
            }
        }

        jsonObjectRequest.tag = TAG
        requestQueue.add(jsonObjectRequest)
    }

    private fun parseWeatherResponse(response: JSONObject, callback: WeatherCallback) {
        try {
            val main = response.getJSONObject("main")
            val weatherArray = response.getJSONArray("weather")
            val weather = weatherArray.getJSONObject(0)
            val wind = response.optJSONObject("wind") ?: JSONObject()

            val weatherData = WeatherData(
                temp = main.getDouble("temp"),
                description = weather.getString("description"),
                humidity = main.getInt("humidity"),
                windSpeed = wind.optDouble("speed", 0.0),
                iconCode = weather.optString("icon", null)
            )

            callback.onSuccess(weatherData)
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parsing error", e)
            callback.onFailure("Failed to parse weather data")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            callback.onFailure("Unexpected error occurred")
        }
    }

    private fun handleWeatherError(error: VolleyError, callback: WeatherCallback) {
        val errorMessage = when {
            error.networkResponse == null -> "No network connection"
            error.networkResponse.statusCode == 401 -> "Invalid API key"
            error.networkResponse.statusCode == 404 -> "Location not found"
            error.networkResponse.statusCode in 500..599 -> "Server error"
            else -> "Failed to fetch weather data"
        }

        Log.e(TAG, errorMessage, error)
        callback.onFailure(errorMessage)
    }

    private fun getApiKey(): String {
        return try {
            // Define this in your build.gradle:
            // buildConfigField("String", "OPENWEATHER_API_KEY", "\"your_api_key\"")
            BuildConfig.OPENWEATHER_API_KEY
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't find API key in BuildConfig", e)
            "" // Fallback empty string
        }
    }

    fun cancelAllRequests() {
        requestQueue.cancelAll(TAG)
    }
}