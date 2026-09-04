package com.nemotron.voiceime.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * HealthConnectManager: lee todos los tipos de datos posibles de Health Connect
 * y los estructura como JSON para transferir al NAS via webhook.
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectManager"

        // Provider de Health Connect: Samsung usa com.google.android.healthconnect.controller
        // (en Pixel es com.google.android.apps.healthdata). Usar el paquete real del dispositivo.
        val PROVIDER_PACKAGE = "com.google.android.healthconnect.controller"

        val READ_PERMISSIONS: Set<String> = setOf(
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_WEIGHT",
            "android.permission.health.READ_HEIGHT",
            "android.permission.health.READ_BLOOD_PRESSURE",
            "android.permission.health.READ_BLOOD_GLUCOSE",
            "android.permission.health.READ_OXYGEN_SATURATION",
            "android.permission.health.READ_BODY_TEMPERATURE",
            "android.permission.health.READ_HYDRATION",
            "android.permission.health.READ_MENSTRUATION",
            "android.permission.health.READ_NUTRITION",
            "android.permission.health.READ_RESPIRATORY_RATE",
            "android.permission.health.READ_VO2_MAX",
            "android.permission.health.READ_BODY_FAT",
            "android.permission.health.READ_BASAL_METABOLIC_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_FLOORS_CLIMBED"
        )
    }

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context, PROVIDER_PACKAGE) }

    suspend fun hasPermissions(): Boolean {
        return runCatching {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            READ_PERMISSIONS.any { it in granted }
        }.getOrDefault(false)
    }

    /**
     * Lee TODOS los tipos de datos del rango indicado y devuelve JSON.
     */
    suspend fun readAllData(start: Instant = Instant.now().minus(7, ChronoUnit.DAYS),
                            end: Instant = Instant.now()): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject()
        result.put("start", start.toString())
        result.put("end", end.toString())
        result.put("timestamp", Instant.now().toString())
        val filter = TimeRangeFilter.between(start, end)

        putSafe(result, "steps") { readSteps(filter) }
        putSafe(result, "distance") { readDistance(filter) }
        putSafe(result, "heart_rate") { readHeartRate(filter) }
        putSafe(result, "sleep") { readSleep(filter) }
        putSafe(result, "exercise") { readExercise(filter) }
        putSafe(result, "weight") { readWeight(filter) }
        putSafe(result, "calories_active") { readCaloriesActive(filter) }
        putSafe(result, "calories_total") { readCaloriesTotal(filter) }
        putSafe(result, "body_fat") { readBodyFat(filter) }
        putSafe(result, "blood_pressure") { readBloodPressure(filter) }
        putSafe(result, "blood_glucose") { readBloodGlucose(filter) }
        putSafe(result, "oxygen_saturation") { readSpO2(filter) }
        putSafe(result, "body_temperature") { readBodyTemperature(filter) }
        putSafe(result, "hydration") { readHydration(filter) }
        putSafe(result, "respiration_rate") { readRespirationRate(filter) }
        putSafe(result, "height") { readHeight(filter) }
        putSafe(result, "floors_climbed") { readFloorsClimbed(filter) }
        putSafe(result, "vo2_max") { readVo2Max(filter) }
        putSafe(result, "basal_metabolic_rate") { readBmr(filter) }
        putSafe(result, "resting_heart_rate") { readRestingHeartRate(filter) }
        putSafe(result, "nutrition") { readNutrition(filter) }
        putSafe(result, "menstruation") { readMenstruation(filter) }

        Log.d(TAG, "readAllData completado")
        result
    }

    private suspend fun putSafe(json: JSONObject, key: String, block: suspend () -> JSONArray) {
        try {
            json.put(key, block())
        } catch (e: Exception) {
            json.put("${key}_error", e.message)
        }
    }

    private suspend fun readSteps(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(StepsRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("count", r.count)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readDistance(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("distance_meters", r.distance.inMeters)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readHeartRate(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("samples", JSONArray().also { samples ->
                    r.samples.forEach { s -> samples.put(JSONObject().apply {
                        put("time", s.time.toString())
                        put("bpm", s.beatsPerMinute)
                    }) }
                })
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readSleep(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
                put("title", r.title)
                put("notes", r.notes)
                put("stages", JSONArray().also { stages ->
                    r.stages.forEach { s -> stages.put(JSONObject().apply {
                        put("stage", s.stage)
                        put("start", s.startTime.toString())
                        put("end", s.endTime.toString())
                    }) }
                })
            })
        }
    }

    private suspend fun readExercise(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
                put("title", r.title)
                put("exerciseType", r.exerciseType)
                put("segments", JSONArray().also { segs ->
                    r.segments.forEach { s -> segs.put(JSONObject().apply {
                        put("segmentType", s.segmentType)
                        put("start", s.startTime.toString())
                        put("end", s.endTime.toString())
                    }) }
                })
                put("laps", JSONArray().also { laps ->
                    r.laps.forEach { l -> laps.put(JSONObject().apply {
                        put("start", l.startTime.toString())
                        put("end", l.endTime.toString())
                    }) }
                })
            })
        }
    }

    private suspend fun readWeight(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(WeightRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("weight_kg", r.weight.inKilograms)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readCaloriesActive(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("calories_kcal", r.energy.inKilocalories)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readCaloriesTotal(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("calories_kcal", r.energy.inKilocalories)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readBodyFat(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(BodyFatRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("percentage", r.percentage.value)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readBloodPressure(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(BloodPressureRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("systolic", r.systolic.inMillimetersOfMercury)
                put("diastolic", r.diastolic.inMillimetersOfMercury)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readBloodGlucose(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("level_mgdl", r.level.inMilligramsPerDeciliter)
                put("specimenSource", r.specimenSource)
                put("mealType", r.mealType)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readSpO2(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("percentage", r.percentage.value)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readBodyTemperature(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(BodyTemperatureRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("temperature_c", r.temperature.inCelsius)
                put("measurementLocation", r.measurementLocation)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readHydration(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(HydrationRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("volume_liters", r.volume.inLiters)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readRespirationRate(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(RespiratoryRateRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("rate", r.rate)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readHeight(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(HeightRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("height_m", r.height.inMeters)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readFloorsClimbed(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(FloorsClimbedRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("floors", r.floors)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readVo2Max(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(Vo2MaxRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("vo2max_ml_kg_min", r.vo2MillilitersPerMinuteKilogram)
                put("measurementMethod", r.measurementMethod)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readBmr(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(BasalMetabolicRateRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("bmr_kcal", r.basalMetabolicRate.inKilocaloriesPerDay)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readRestingHeartRate(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("bpm", r.beatsPerMinute)
                put("time", r.time.toString())
            })
        }
    }

    private suspend fun readNutrition(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(NutritionRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("energy_kcal", r.energy?.inKilocalories)
                put("protein_g", r.protein?.inGrams)
                put("calcium_mg", r.calcium?.inMilligrams)
                put("iron_mg", r.iron?.inMilligrams)
                put("sodium_mg", r.sodium?.inMilligrams)
                put("cholesterol_mg", r.cholesterol?.inMilligrams)
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }

    private suspend fun readMenstruation(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        for (r in healthConnectClient.readRecords(
            ReadRecordsRequest(MenstruationPeriodRecord::class, timeRangeFilter = filter)).records) {
            arr.put(JSONObject().apply {
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
            })
        }
    }
}