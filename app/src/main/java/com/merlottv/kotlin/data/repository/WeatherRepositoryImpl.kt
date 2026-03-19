package com.merlottv.kotlin.data.repository

import android.util.Log
import com.merlottv.kotlin.BuildConfig
import com.merlottv.kotlin.domain.model.AirQuality
import com.merlottv.kotlin.domain.model.CurrentWeather
import com.merlottv.kotlin.domain.model.DayForecast
import com.merlottv.kotlin.domain.model.HourForecast
import com.merlottv.kotlin.domain.model.MarineData
import com.merlottv.kotlin.domain.model.RadarFrame
import com.merlottv.kotlin.domain.model.WeatherAlert
import com.merlottv.kotlin.domain.repository.WeatherRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : WeatherRepository {

    private val boundedIo = Dispatchers.IO.limitedParallelism(2)

    private val weatherClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // In-memory cache
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val WEATHER_CACHE_TTL = 15 * 60 * 1000L
    private val RADAR_CACHE_TTL = 2 * 60 * 1000L
    private val ALERTS_CACHE_TTL = 5 * 60 * 1000L
    private val MARINE_CACHE_TTL = 30 * 60 * 1000L  // 30 minutes

    private data class CacheEntry(val data: Any, val timestamp: Long)

    @Suppress("UNCHECKED_CAST")
    private fun <T> getCached(key: String, ttl: Long): T? {
        val entry = cache[key] ?: return null
        return if (System.currentTimeMillis() - entry.timestamp < ttl) {
            entry.data as? T
        } else {
            cache.remove(key)
            null
        }
    }

    private fun putCache(key: String, data: Any) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    private val mapAdapter by lazy {
        moshi.adapter(Map::class.java)
    }

    private val listAdapter by lazy {
        moshi.adapter(List::class.java)
    }

    // NWS alerts client
    private val nwsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(15, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MerlotTV/2.69 (merlottv.app)")
                    .header("Accept", "application/geo+json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    companion object {
        private const val TAG = "WeatherRepo"
        private const val WEATHER_BASE = "https://api.weatherapi.com/v1"
        private const val RADAR_API = "https://api.rainviewer.com/public/weather-maps.json"
        private const val NWS_ALERTS_BASE = "https://api.weather.gov/alerts/active"
        private const val OPEN_METEO_MARINE = "https://marine-api.open-meteo.com/v1/marine"

        private val SEVERITY_ORDER = mapOf(
            "Extreme" to 0, "Severe" to 1, "Moderate" to 2, "Minor" to 3, "Unknown" to 4
        )
    }

    // ─── Weather + Forecast (single API call, AQI enabled) ─────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun getCurrentAndForecast(zipCode: String): Pair<CurrentWeather, List<DayForecast>>? {
        val cacheKey = "weather_$zipCode"
        getCached<Pair<CurrentWeather, List<DayForecast>>>(cacheKey, WEATHER_CACHE_TTL)?.let { return it }

        return withContext(boundedIo) {
            try {
                // AQI enabled now
                val url = "$WEATHER_BASE/forecast.json?key=${BuildConfig.WEATHER_API_KEY}&q=$zipCode&days=7&aqi=yes&alerts=no"
                val json = fetchJson(url) ?: return@withContext null

                val current = json["current"] as? Map<String, Any?> ?: return@withContext null
                val location = json["location"] as? Map<String, Any?> ?: return@withContext null
                val conditionMap = current["condition"] as? Map<String, Any?>

                // Parse air quality
                val aqiMap = current["air_quality"] as? Map<String, Any?>
                val airQuality = if (aqiMap != null) {
                    AirQuality(
                        usEpaIndex = (aqiMap["us-epa-index"] as? Number)?.toInt() ?: 0,
                        pm25 = (aqiMap["pm2_5"] as? Number)?.toDouble() ?: 0.0,
                        pm10 = (aqiMap["pm10"] as? Number)?.toDouble() ?: 0.0,
                        co = (aqiMap["co"] as? Number)?.toDouble() ?: 0.0,
                        no2 = (aqiMap["no2"] as? Number)?.toDouble() ?: 0.0,
                        o3 = (aqiMap["o3"] as? Number)?.toDouble() ?: 0.0,
                        so2 = (aqiMap["so2"] as? Number)?.toDouble() ?: 0.0
                    )
                } else null

                val currentWeather = CurrentWeather(
                    tempF = (current["temp_f"] as? Number)?.toDouble() ?: 0.0,
                    tempC = (current["temp_c"] as? Number)?.toDouble() ?: 0.0,
                    feelsLikeF = (current["feelslike_f"] as? Number)?.toDouble() ?: 0.0,
                    feelsLikeC = (current["feelslike_c"] as? Number)?.toDouble() ?: 0.0,
                    condition = conditionMap?.get("text")?.toString() ?: "Unknown",
                    conditionIconUrl = "https:" + (conditionMap?.get("icon")?.toString() ?: ""),
                    windMph = (current["wind_mph"] as? Number)?.toDouble() ?: 0.0,
                    windGustMph = (current["gust_mph"] as? Number)?.toDouble() ?: 0.0,
                    windDir = current["wind_dir"]?.toString() ?: "",
                    windDegree = (current["wind_degree"] as? Number)?.toInt() ?: 0,
                    humidity = (current["humidity"] as? Number)?.toInt() ?: 0,
                    dewPointF = (current["dewpoint_f"] as? Number)?.toDouble() ?: 0.0,
                    pressureIn = (current["pressure_in"] as? Number)?.toDouble() ?: 0.0,
                    uvIndex = (current["uv"] as? Number)?.toDouble() ?: 0.0,
                    visibilityMiles = (current["vis_miles"] as? Number)?.toDouble() ?: 0.0,
                    cloudCover = (current["cloud"] as? Number)?.toInt() ?: 0,
                    locationName = location["name"]?.toString() ?: "",
                    region = location["region"]?.toString() ?: "",
                    localTime = location["localtime"]?.toString() ?: "",
                    isDay = (current["is_day"] as? Number)?.toInt() == 1,
                    lat = (location["lat"] as? Number)?.toDouble() ?: 0.0,
                    lon = (location["lon"] as? Number)?.toDouble() ?: 0.0,
                    airQuality = airQuality
                )

                // Parse 7-day forecast
                val forecast = json["forecast"] as? Map<String, Any?>
                val forecastDays = (forecast?.get("forecastday") as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

                val dayForecasts = forecastDays.mapNotNull { dayMap ->
                    val dateStr = dayMap["date"]?.toString() ?: return@mapNotNull null
                    val day = dayMap["day"] as? Map<String, Any?> ?: return@mapNotNull null
                    val dayCond = day["condition"] as? Map<String, Any?>
                    val astro = dayMap["astro"] as? Map<String, Any?>

                    val dayOfWeek = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        val date = sdf.parse(dateStr)
                        val d = date ?: throw Exception("parse failed")
                        val cal = Calendar.getInstance().apply { time = d }
                        val dayNames = arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        dayNames[cal.get(Calendar.DAY_OF_WEEK)]
                    } catch (_: Exception) { "?" }

                    // Parse hourly forecast with extended data
                    val hours = (dayMap["hour"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
                    val hourlyForecasts = hours.mapNotNull { h ->
                        val timeStr = h["time"]?.toString() ?: return@mapNotNull null
                        val hCond = h["condition"] as? Map<String, Any?>

                        val displayTime = try {
                            val timePart = timeStr.split(" ").getOrNull(1) ?: ""
                            val hour = timePart.split(":")[0].toInt()
                            val amPm = if (hour >= 12) "PM" else "AM"
                            val h12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                            "$h12 $amPm"
                        } catch (_: Exception) { timeStr }

                        HourForecast(
                            time = timeStr,
                            displayTime = displayTime,
                            tempF = (h["temp_f"] as? Number)?.toDouble() ?: 0.0,
                            feelsLikeF = (h["feelslike_f"] as? Number)?.toDouble() ?: 0.0,
                            condition = hCond?.get("text")?.toString() ?: "Unknown",
                            conditionIconUrl = "https:" + (hCond?.get("icon")?.toString() ?: ""),
                            windMph = (h["wind_mph"] as? Number)?.toDouble() ?: 0.0,
                            windGustMph = (h["gust_mph"] as? Number)?.toDouble() ?: 0.0,
                            windDir = h["wind_dir"]?.toString() ?: "",
                            windDegree = (h["wind_degree"] as? Number)?.toInt() ?: 0,
                            humidity = (h["humidity"] as? Number)?.toInt() ?: 0,
                            chanceOfRain = (h["chance_of_rain"] as? Number)?.toInt()
                                ?: (h["chance_of_rain"]?.toString()?.toIntOrNull() ?: 0),
                            chanceOfSnow = (h["chance_of_snow"] as? Number)?.toInt()
                                ?: (h["chance_of_snow"]?.toString()?.toIntOrNull() ?: 0),
                            isDay = (h["is_day"] as? Number)?.toInt() == 1,
                            dewPointF = (h["dewpoint_f"] as? Number)?.toDouble() ?: 0.0,
                            pressureIn = (h["pressure_in"] as? Number)?.toDouble() ?: 0.0,
                            cloudCover = (h["cloud"] as? Number)?.toInt() ?: 0,
                            uvIndex = (h["uv"] as? Number)?.toDouble() ?: 0.0
                        )
                    }

                    DayForecast(
                        date = dateStr,
                        dayOfWeek = dayOfWeek,
                        maxTempF = (day["maxtemp_f"] as? Number)?.toDouble() ?: 0.0,
                        minTempF = (day["mintemp_f"] as? Number)?.toDouble() ?: 0.0,
                        condition = dayCond?.get("text")?.toString() ?: "Unknown",
                        conditionIconUrl = "https:" + (dayCond?.get("icon")?.toString() ?: ""),
                        chanceOfRain = (day["daily_chance_of_rain"] as? Number)?.toInt()
                            ?: (day["daily_chance_of_rain"]?.toString()?.toIntOrNull() ?: 0),
                        chanceOfSnow = (day["daily_chance_of_snow"] as? Number)?.toInt()
                            ?: (day["daily_chance_of_snow"]?.toString()?.toIntOrNull() ?: 0),
                        avgHumidity = (day["avghumidity"] as? Number)?.toInt() ?: 0,
                        maxWindMph = (day["maxwind_mph"] as? Number)?.toDouble() ?: 0.0,
                        sunrise = astro?.get("sunrise")?.toString() ?: "",
                        sunset = astro?.get("sunset")?.toString() ?: "",
                        moonrise = astro?.get("moonrise")?.toString() ?: "",
                        moonset = astro?.get("moonset")?.toString() ?: "",
                        moonPhase = astro?.get("moon_phase")?.toString() ?: "",
                        moonIllumination = (astro?.get("moon_illumination") as? Number)?.toInt()
                            ?: (astro?.get("moon_illumination")?.toString()?.toIntOrNull() ?: 0),
                        uvIndex = (day["uv"] as? Number)?.toDouble() ?: 0.0,
                        avgVisibilityMiles = (day["avgvis_miles"] as? Number)?.toDouble() ?: 0.0,
                        totalPrecipIn = (day["totalprecip_in"] as? Number)?.toDouble() ?: 0.0,
                        avgTempF = (day["avgtemp_f"] as? Number)?.toDouble() ?: 0.0,
                        hourly = hourlyForecasts
                    )
                }

                val result = Pair(currentWeather, dayForecasts)
                putCache(cacheKey, result)
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch weather for $zipCode", e)
                null
            }
        }
    }

    // ─── Marine / Wave Data (Open-Meteo free API) ─────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun getMarineData(lat: Double, lon: Double): MarineData? {
        val cacheKey = "marine_${String.format("%.2f", lat)}_${String.format("%.2f", lon)}"
        getCached<MarineData>(cacheKey, MARINE_CACHE_TTL)?.let { return it }

        return withContext(boundedIo) {
            try {
                val url = "$OPEN_METEO_MARINE?latitude=${String.format("%.4f", lat)}&longitude=${String.format("%.4f", lon)}" +
                    "&current=wave_height,wave_period,wave_direction,swell_wave_height,swell_wave_period,swell_wave_direction,wind_wave_height" +
                    "&temperature_unit=fahrenheit&wind_speed_unit=mph&length_unit=imperial"
                val json = fetchJson(url) ?: return@withContext null

                val currentData = json["current"] as? Map<String, Any?> ?: return@withContext null

                val marine = MarineData(
                    waveHeightFt = (currentData["wave_height"] as? Number)?.toDouble() ?: 0.0,
                    wavePeriodSec = (currentData["wave_period"] as? Number)?.toDouble() ?: 0.0,
                    waveDirectionDeg = (currentData["wave_direction"] as? Number)?.toInt() ?: 0,
                    swellHeightFt = (currentData["swell_wave_height"] as? Number)?.toDouble() ?: 0.0,
                    swellPeriodSec = (currentData["swell_wave_period"] as? Number)?.toDouble() ?: 0.0,
                    swellDirectionDeg = (currentData["swell_wave_direction"] as? Number)?.toInt() ?: 0,
                    windWaveHeightFt = (currentData["wind_wave_height"] as? Number)?.toDouble() ?: 0.0
                )

                putCache(cacheKey, marine)
                marine
            } catch (e: Exception) {
                Log.d(TAG, "Marine data not available for ($lat, $lon): ${e.message}")
                null
            }
        }
    }

    // ─── Radar Frames (RainViewer) ───────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun getRadarFrames(): List<RadarFrame> {
        getCached<List<RadarFrame>>("radar", RADAR_CACHE_TTL)?.let { return it }

        return withContext(boundedIo) {
            try {
                val json = fetchJson(RADAR_API) ?: return@withContext emptyList()

                val host = json["host"]?.toString() ?: "https://tilecache.rainviewer.com"
                val radar = json["radar"] as? Map<String, Any?> ?: return@withContext emptyList()
                val past = (radar["past"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

                val frames = past.mapNotNull { frame ->
                    val path = frame["path"]?.toString() ?: return@mapNotNull null
                    val time = (frame["time"] as? Number)?.toLong() ?: return@mapNotNull null
                    RadarFrame(path = path, time = time, host = host)
                }

                putCache("radar", frames)
                frames
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch radar frames", e)
                emptyList()
            }
        }
    }

    // ─── NWS Weather Alerts ──────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun getActiveAlerts(lat: Double, lon: Double): List<WeatherAlert> {
        val cacheKey = "alerts_${String.format("%.4f", lat)}_${String.format("%.4f", lon)}"
        getCached<List<WeatherAlert>>(cacheKey, ALERTS_CACHE_TTL)?.let { return it }

        return withContext(boundedIo) {
            try {
                val url = "$NWS_ALERTS_BASE?point=${String.format("%.4f", lat)},${String.format("%.4f", lon)}&status=actual"
                val request = Request.Builder().url(url).build()
                val response = nwsClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "NWS alerts HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val json = mapAdapter.fromJson(body) as? Map<String, Any?> ?: return@withContext emptyList()
                val features = (json["features"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()

                val alerts = features.mapNotNull { feature ->
                    val props = feature["properties"] as? Map<String, Any?> ?: return@mapNotNull null
                    val event = props["event"]?.toString() ?: return@mapNotNull null

                    WeatherAlert(
                        id = props["id"]?.toString() ?: feature["id"]?.toString() ?: "",
                        event = event,
                        headline = props["headline"]?.toString() ?: event,
                        description = props["description"]?.toString() ?: "",
                        severity = props["severity"]?.toString() ?: "Unknown",
                        urgency = props["urgency"]?.toString() ?: "Unknown",
                        senderName = props["senderName"]?.toString() ?: "",
                        areaDesc = props["areaDesc"]?.toString() ?: "",
                        onset = props["onset"]?.toString() ?: props["effective"]?.toString() ?: "",
                        expires = props["expires"]?.toString() ?: "",
                        instruction = props["instruction"]?.toString()
                    )
                }.sortedBy { SEVERITY_ORDER[it.severity] ?: 4 }

                Log.d(TAG, "Fetched ${alerts.size} NWS alerts for ($lat, $lon)")
                putCache(cacheKey, alerts)
                alerts
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch NWS alerts", e)
                emptyList()
            }
        }
    }

    // ─── ZIP → lat/lon geocoding ──────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override suspend fun getCoordinatesForZip(zipCode: String): Pair<Double, Double>? {
        val cacheKey = "coords_$zipCode"
        getCached<Pair<Double, Double>>(cacheKey, WEATHER_CACHE_TTL)?.let { return it }

        return withContext(boundedIo) {
            try {
                val url = "$WEATHER_BASE/search.json?key=${BuildConfig.WEATHER_API_KEY}&q=$zipCode"
                val request = Request.Builder().url(url)
                    .header("Accept", "application/json")
                    .build()
                val response = weatherClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val list = moshi.adapter(List::class.java).fromJson(body) as? List<*> ?: return@withContext null
                val first = (list.firstOrNull() as? Map<String, Any?>) ?: return@withContext null

                val lat = (first["lat"] as? Number)?.toDouble() ?: return@withContext null
                val lon = (first["lon"] as? Number)?.toDouble() ?: return@withContext null

                val result = Pair(lat, lon)
                putCache(cacheKey, result)
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to geocode ZIP $zipCode", e)
                null
            }
        }
    }

    // ─── Network ─────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun fetchJson(url: String): Map<String, Any?>? {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json")
            .build()
        val response = weatherClient.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.w(TAG, "HTTP ${response.code}: $url")
            return null
        }
        val body = response.body?.string() ?: return null
        return mapAdapter.fromJson(body) as? Map<String, Any?>
    }
}
