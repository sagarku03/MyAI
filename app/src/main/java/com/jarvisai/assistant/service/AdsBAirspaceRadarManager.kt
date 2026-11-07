package com.jarvisai.assistant.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvisai.assistant.model.ProceduralHudMode
import com.jarvisai.assistant.ui.components.ProceduralHudModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class OverheadAircraftTelemetry(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeFeet: Int,
    val speedKmh: Int,
    val speedKnots: Int,
    val headingDeg: Float,
    val distanceKm: Double,
    val bearingDeg: Float
)

data class AdsBRadarSnapshot(
    val centerLat: Double = 28.6139,
    val centerLon: Double = 77.2090,
    val locationLabel: String = "Airspace Sector",
    val radiusKm: Int = 120,
    val aircraftList: List<OverheadAircraftTelemetry> = emptyList(),
    val scannedAtMs: Long = 0L,
    val spokenHinglishBriefing: String = "ADS-B Airspace Radar ready. Tap Scan Airspace to detect overhead flights."
)

/**
 * AbuZar-Ansarii/Jarvis (PrivateAgent) Real-Time ADS-B Overhead Aircraft Radar Telemetry:
 * Uses device GPS (or IP geolocation fallback) to query live ADS-B aviation data from OpenSky Network
 * (`https://opensky-network.org/api/states/all`), computing exact Haversine distance, compass bearing,
 * altitude in feet, and ground speed for every aircraft flying overhead right now.
 */
object AdsBAirspaceRadarManager {
    private const val TAG = "AdsBAirspaceRadar"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _radarSnapshot = MutableStateFlow(AdsBRadarSnapshot())
    val radarSnapshot: StateFlow<AdsBRadarSnapshot> = _radarSnapshot.asStateFlow()

    suspend fun scanOverheadAirspace(
        context: Context,
        radiusKm: Int = 150
    ): AdsBRadarSnapshot = withContext(Dispatchers.IO) {
        ProceduralHudModeState.setMode(ProceduralHudMode.ADSB_AIRSPACE_RADAR)

        val (lat, lon, locLabel) = resolveOperatorCoordinates(context)
        val degDelta = (radiusKm.coerceIn(30, 300) / 111.0).coerceIn(0.4, 2.8)
        val lamin = String.format(Locale.US, "%.4f", lat - degDelta)
        val lamax = String.format(Locale.US, "%.4f", lat + degDelta)
        val lomin = String.format(Locale.US, "%.4f", lon - degDelta)
        val lomax = String.format(Locale.US, "%.4f", lon + degDelta)

        val url = "https://opensky-network.org/api/states/all?lamin=$lamin&lomin=$lomin&lamax=$lamax&lomax=$lomax"
        val detectedFlights = mutableListOf<OverheadAircraftTelemetry>()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "JARVIS-PrivateAgent-Radar/4.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val root = JSONObject(bodyStr)
                        val states = root.optJSONArray("states")
                        if (states != null) {
                            for (i in 0 until states.length()) {
                                val arr = states.optJSONArray(i) ?: continue
                                val icao24 = arr.optString(0, "").trim()
                                val rawCallsign = arr.optString(1, "").trim().ifBlank { "FLT-${icao24.take(4).uppercase()}" }
                                val country = arr.optString(2, "Unknown")
                                val acLon = arr.optDouble(5, Double.NaN)
                                val acLat = arr.optDouble(6, Double.NaN)
                                if (acLat.isNaN() || acLon.isNaN()) continue
                                val baroAltMeters = arr.optDouble(7, 10000.0).let { if (it.isNaN()) 9500.0 else it }
                                val velocityMs = arr.optDouble(9, 220.0).let { if (it.isNaN()) 210.0 else it }
                                val trueTrack = arr.optDouble(10, 0.0).let { if (it.isNaN()) 0.0 else it }.toFloat()

                                val distKm = computeHaversineDistanceKm(lat, lon, acLat, acLon)
                                val bearing = computeBearingDegrees(lat, lon, acLat, acLon)
                                val altFeet = (baroAltMeters * 3.28084).roundToInt().coerceAtLeast(500)
                                val speedKmh = (velocityMs * 3.6).roundToInt()
                                val speedKnots = (velocityMs * 1.94384).roundToInt()

                                detectedFlights.add(
                                    OverheadAircraftTelemetry(
                                        icao24 = icao24,
                                        callsign = rawCallsign,
                                        originCountry = country,
                                        latitude = acLat,
                                        longitude = acLon,
                                        altitudeFeet = altFeet,
                                        speedKmh = speedKmh,
                                        speedKnots = speedKnots,
                                        headingDeg = trueTrack,
                                        distanceKm = distKm,
                                        bearingDeg = bearing
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenSky live fetch fallback: ${e.message}")
        }

        val sortedFlights = detectedFlights.sortedBy { it.distanceKm }.take(15)
        val briefing = if (sortedFlights.isNotEmpty()) {
            val closest = sortedFlights.first()
            "Sir, ADS-B Airspace Radar ne $locLabel ke ${radiusKm}km radius mein ${sortedFlights.size} live aircraft track kiye hain. Sabse kareeb flight ${closest.callsign} (${closest.originCountry}) aapse ${String.format(Locale.US, "%.1f", closest.distanceKm)} km door ${closest.altitudeFeet} feet altitude aur ${closest.speedKmh} km/h speed par ud rahi hai."
        } else {
            "Sir, $locLabel (${String.format(Locale.US, "%.2f", lat)}, ${String.format(Locale.US, "%.2f", lon)}) ke ${radiusKm}km ADS-B airspace sector ko scan kiya gaya. Is waqt is sector mein koi active commercial transponder signal nahi hai."
        }

        val snapshot = AdsBRadarSnapshot(
            centerLat = lat,
            centerLon = lon,
            locationLabel = locLabel,
            radiusKm = radiusKm,
            aircraftList = sortedFlights,
            scannedAtMs = System.currentTimeMillis(),
            spokenHinglishBriefing = briefing
        )
        _radarSnapshot.value = snapshot
        snapshot
    }

    fun resolveOperatorCoordinates(context: Context): Triple<Double, Double, String> {
        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                val providers = lm?.getProviders(true).orEmpty()
                for (provider in providers) {
                    val loc = lm?.getLastKnownLocation(provider)
                    if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        return Triple(loc.latitude, loc.longitude, "Live GPS Sector")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "GPS lookup fallback: ${e.message}")
        }

        // Fallback to IP geolocation if GPS permission is not granted yet
        try {
            val req = Request.Builder().url("http://ip-api.com/json/?fields=status,city,lat,lon").build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    if (json.optString("status") == "success") {
                        val lat = json.optDouble("lat", 28.6139)
                        val lon = json.optDouble("lon", 77.2090)
                        val city = json.optString("city", "Local Airspace").ifBlank { "Local Airspace" }
                        return Triple(lat, lon, "$city Airspace")
                    }
                }
            }
        } catch (_: Exception) {
        }

        return Triple(28.6139, 77.2090, "New Delhi Sector (Default)")
    }

    fun computeHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    fun computeBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
        val theta = atan2(y, x)
        return ((theta * 180.0 / PI + 360.0) % 360.0).toFloat()
    }
}
