package com.nemotron.voiceime.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * HealthConnectManager: lee todos los tipos de datos posibles de Health Connect
 * y los estructura como JSON para transferir al NAS via webhook.
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectManager"

        private val SEGMENT_TYPE_NAMES: Map<Int, String> = run {
            val m = HashMap<Int, String>()
            try {
                for (f in ExerciseSegment::class.java.declaredFields) {
                    if (f.name.startsWith("EXERCISE_SEGMENT_TYPE_")) {
                        f.isAccessible = true
                        val name = f.name.removePrefix("EXERCISE_SEGMENT_TYPE_")
                            .replace("_", " ")
                            .lowercase()
                        val human = name.split(" ").joinToString(" ") { w ->
                            w.replaceFirstChar { it.uppercase() }
                        }
                        m[f.getInt(null)] = human
                    }
                }
            } catch (_: Throwable) {}
            m
        }

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
     * Por defecto: TODA la historia (desde 2000) para el resumen diario/semanal,
     * y los registros crudos de HR limitados a los ultimos 7 dias (son enormes).
     */
    suspend fun readAllData(start: Instant = Instant.parse("2000-01-01T00:00:00Z"),
                            end: Instant = Instant.now()): JSONObject = withContext(Dispatchers.IO) {
        val result = JSONObject()
        result.put("start", start.toString())
        result.put("end", end.toString())
        result.put("timestamp", Instant.now().toString())
        val filter = TimeRangeFilter.between(start, end)

        // Resumen diario/semanal de TODA la historia: primero porque las lecturas
        // crudas son pesadas. Se construye desde records agrupados por dia.
        putSafe(result, "summary") {
            buildDailySummary(
                steps = readSteps(filter, daily = true),
                distanceM = readDistance(filter),
                caloriesActive = readCaloriesActive(filter),
                sleep = readSleep(filter),
                exercise = readExercise(filter),
                weight = readWeight(filter),
                bodyFat = readBodyFat(filter),
                restingHr = readRestingHeartRate(filter)
            )
        }

        // Registros crudos: solo ultimas 4 semanas para no explotar el tamano.
        val recentStart = Instant.now().minus(28, ChronoUnit.DAYS)
        val recentFilter = TimeRangeFilter.between(recentStart, end)
        putSafe(result, "raw_steps") { readSteps(recentFilter, daily = true) }
        putSafe(result, "distance") { readDistance(recentFilter) }
        putSafe(result, "heart_rate") { readHeartRate(recentFilter) }
        putSafe(result, "sleep") { readSleep(recentFilter) }
        putSafe(result, "exercise") { readExercise(recentFilter) }
        putSafe(result, "weight") { readWeight(recentFilter) }
        putSafe(result, "calories_active") { readCaloriesActive(recentFilter) }
        putSafe(result, "calories_total") { readCaloriesTotal(recentFilter) }
        putSafe(result, "body_fat") { readBodyFat(recentFilter) }
        putSafe(result, "blood_pressure") { readBloodPressure(recentFilter) }
        putSafe(result, "blood_glucose") { readBloodGlucose(recentFilter) }
        putSafe(result, "oxygen_saturation") { readSpO2(recentFilter) }
        putSafe(result, "body_temperature") { readBodyTemperature(recentFilter) }
        putSafe(result, "hydration") { readHydration(recentFilter) }
        putSafe(result, "respiration_rate") { readRespirationRate(recentFilter) }
        putSafe(result, "height") { readHeight(recentFilter) }
        putSafe(result, "floors_climbed") { readFloorsClimbed(recentFilter) }
        putSafe(result, "vo2_max") { readVo2Max(recentFilter) }
        putSafe(result, "basal_metabolic_rate") { readBmr(recentFilter) }
        putSafe(result, "resting_heart_rate") { readRestingHeartRate(recentFilter) }
        putSafe(result, "nutrition") { readNutrition(recentFilter) }
        putSafe(result, "menstruation") { readMenstruation(recentFilter) }

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

    /**
     * Construye resumen diario y semanal de TODA la historia a partir de las lecturas
     * de los distintos tipos (cada uno se agrupa por dia y se suma/promedia).
     */
    private suspend fun buildDailySummary(
        steps: JSONArray,
        distanceM: JSONArray,
        caloriesActive: JSONArray,
        sleep: JSONArray,
        exercise: JSONArray,
        weight: JSONArray,
        bodyFat: JSONArray,
        restingHr: JSONArray
    ): JSONArray = withContext(Dispatchers.IO) {
        val daily = LinkedHashMap<String, JSONObject>()

        fun day(k: String): JSONObject {
            val d = dayKey(k)
            return daily.getOrPut(d) { JSONObject().apply { put("date", d) } }
        }

        fun addDouble(obj: JSONObject, key: String, v: Double) {
            obj.put(key, obj.optDouble(key, 0.0) + v)
        }
        fun addLong(obj: JSONObject, key: String, v: Long) {
            obj.put(key, obj.optLong(key, 0L) + v)
        }

        // Pasos por dia (ya vienen por dia desde readStepsDaily)
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            day(s.getString("date")).put("steps", s.optLong("count"))
        }
        // Distancia
        for (i in 0 until distanceM.length()) {
            val s = distanceM.getJSONObject(i)
            addDouble(day(s.getString("start")), "distance_m", s.optDouble("distance_meters"))
        }
        // Calorias activas
        for (i in 0 until caloriesActive.length()) {
            val s = caloriesActive.getJSONObject(i)
            addDouble(day(s.getString("start")), "calories_kcal", s.optDouble("calories_kcal"))
        }
        // Sueno: suma horas, cuenta sesiones
        for (i in 0 until sleep.length()) {
            val s = sleep.getJSONObject(i)
            val o = day(s.getString("start"))
            val hrs = runCatching {
                java.time.Duration.between(Instant.parse(s.getString("start")), Instant.parse(s.getString("end"))).toMinutes() / 60.0
            }.getOrDefault(0.0)
            addDouble(o, "sleep_hours", hrs)
            addLong(o, "sleep_sessions", 1L)
        }
        // Ejercicio: minutos por dia, sesiones, por tipo
        for (i in 0 until exercise.length()) {
            val s = exercise.getJSONObject(i)
            val o = day(s.getString("start"))
            // workout_duration_minutes es el tiempo activo agregado por
            // Health Connect; no confundirlo con end-start, que incluye pausas.
            val workoutMinutes = s.optLong("workout_duration_minutes", -1L)
            val min = if (workoutMinutes >= 0L) workoutMinutes else runCatching {
                java.time.Duration.between(Instant.parse(s.getString("start")), Instant.parse(s.getString("end"))).toMinutes()
            }.getOrDefault(0L)
            addLong(o, "exercise_minutes", min)
            s.optDouble("workout_calories_kcal", Double.NaN).takeUnless { it.isNaN() }?.let {
                addDouble(o, "workout_calories_kcal", it)
            }
            addLong(o, "exercise_sessions", 1L)
            val t = s.optString("exerciseName", "Desconocido")
            val key = "ex_${t.replace(' ', '_')}"
            addLong(o, key, 1L)
        }
        // Peso: ultimo valor del dia
        for (i in 0 until weight.length()) {
            val s = weight.getJSONObject(i)
            day(s.getString("time")).put("weight_kg", s.optDouble("weight_kg"))
        }
        // Grasa corporal: ultimo valor del dia
        for (i in 0 until bodyFat.length()) {
            val s = bodyFat.getJSONObject(i)
            day(s.getString("time")).put("body_fat_pct", s.optDouble("percentage"))
        }
        // FC en reposo: solo el mas bajo del dia (buena senal de recuperacion)
        for (i in 0 until restingHr.length()) {
            val s = restingHr.getJSONObject(i)
            val o = day(s.getString("time"))
            val cur = o.optDouble("resting_hr_bpm", 0.0)
            val bpm = s.optDouble("bpm")
            if (cur == 0.0 || bpm < cur) o.put("resting_hr_bpm", bpm)
        }

        // Semanal: agrupar los dias de daily en semanas (lunes-domingo)
        val weekly = LinkedHashMap<String, JSONObject>()
        for (o in daily.values) {
            val date = LocalDate.parse(o.getString("date"))
            val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
            val w = weekly.getOrPut(weekStart.toString()) { JSONObject().apply { put("week_start", weekStart.toString()) } }
            w.put("days_count", w.optInt("days_count") + 1)
            for (key in listOf("steps", "calories_kcal", "exercise_minutes")) {
                w.put(key, w.optLong(key) + o.optLong(key, 0L))
            }
            addDouble(w, "distance_m", o.optDouble("distance_m", 0.0))
            addDouble(w, "sleep_hours", o.optDouble("sleep_hours", 0.0))
            addLong(w, "exercise_sessions", o.optLong("exercise_sessions", 0L))
            addLong(w, "sleep_sessions", o.optLong("sleep_sessions", 0L))
        }

        JSONArray().apply {
            put(JSONObject().apply { put("granularity", "daily") }.also { it.put("days", JSONArray().apply {
                for (o in daily.values) put(o)
            }) })
            put(JSONObject().apply { put("granularity", "weekly") }.also { it.put("weeks", JSONArray().apply {
                for (o in weekly.values) put(o)
            }) })
        }
    }

    /** '2026-09-12' a partir de un timestamp ISO. */
    private fun dayKey(instant: Instant): String =
        instant.atZone(ZoneOffset.UTC).toLocalDate().toString()

    /** '2026-09-12' a partir de un string ISO. */
    private fun dayKey(iso: String): String = runCatching {
        Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate().toString()
    }.getOrDefault(iso.take(10))

    private suspend fun readSteps(filter: TimeRangeFilter, daily: Boolean = false): JSONArray =
        if (daily) readStepsDaily(filter) else readStepsRaw(filter)

    /** Una entrada por dia con el total de pasos (para el resumen de toda la historia). */
    private suspend fun readStepsDaily(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        try {
            val byDay = LinkedHashMap<String, Long>()
            for (r in healthConnectClient.readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRangeFilter = filter)).records) {
                val d = dayKey(r.startTime)
                byDay[d] = (byDay[d] ?: 0L) + r.count
            }

            // Samsung Health suele publicar los pasos sólo mediante aggregate()
            // (no siempre como StepsRecord legibles). Consultar agregados por
            // día para el periodo reciente garantiza que aparezca también el
            // día actual sin hacer miles de consultas para toda la historia.
            val now = Instant.now()
            val rangeStart = filter.startTime ?: return@also
            val rangeEnd = filter.endTime ?: return@also
            val aggregateStart = maxOf(rangeStart, now.minus(35, ChronoUnit.DAYS))
            var date = aggregateStart.atZone(ZoneOffset.UTC).toLocalDate()
            val lastDate = rangeEnd.atZone(ZoneOffset.UTC).toLocalDate()
            while (!date.isAfter(lastDate)) {
                val dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant()
                val dayEnd = minOf(dayStart.plus(1, ChronoUnit.DAYS), rangeEnd)
                if (dayEnd.isAfter(dayStart)) {
                    val aggregate = healthConnectClient.aggregate(
                        AggregateRequest(
                            metrics = setOf(StepsRecord.COUNT_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                        )
                    )
                    aggregate[StepsRecord.COUNT_TOTAL]?.let { count ->
                        byDay[date.toString()] = count
                    }
                }
                date = date.plusDays(1)
            }

            for ((d, c) in byDay) {
                arr.put(JSONObject().apply {
                    put("date", d)
                    put("count", c)
                })
            }
        } catch (e: Exception) {
            arr.put(JSONObject().apply { put("error", e.message ?: "readStepsDaily") })
        }
    }

    /** Registros crudos originales (por periodo, no por dia). */
    private suspend fun readStepsRaw(filter: TimeRangeFilter): JSONArray = JSONArray().also { arr ->
        try {
            // Usar agregacion: suma el total de pasos del rango (mas confiable que records)
            val agg = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = filter
                )
            )
            val total = agg[StepsRecord.COUNT_TOTAL]
            if (total != null) {
                arr.put(JSONObject().apply {
                    put("count_total", total)
                })
            }
        } catch (_: Exception) {
            // fallback: records individuales
            for (r in healthConnectClient.readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRangeFilter = filter)).records) {
                arr.put(JSONObject().apply {
                    put("count", r.count)
                    put("start", r.startTime.toString())
                    put("end", r.endTime.toString())
                })
            }
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
            val workoutDurationMs = runCatching {
                healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime)
                    )
                )[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMillis()
            }.getOrNull()
            val workoutCaloriesKcal = runCatching {
                healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime)
                    )
                )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
            }.getOrNull()
            arr.put(JSONObject().apply {
                put("start", r.startTime.toString())
                put("end", r.endTime.toString())
                workoutDurationMs?.let {
                    put("workout_duration_ms", it)
                    put("workout_duration_minutes", it / 60_000L)
                }
                workoutCaloriesKcal?.let { put("workout_calories_kcal", it) }
                put("title", r.title)
                put("exerciseType", r.exerciseType)
                put("exerciseName", exerciseName(r.exerciseType))
                put("segments", JSONArray().also { segs ->
                    r.segments.forEach { s -> segs.put(JSONObject().apply {
                        put("segmentType", s.segmentType)
                        put("segmentName", segmentName(s.segmentType))
                        put("start", s.startTime.toString())
                        put("end", s.endTime.toString())
                    }) }
                })
                put("laps", JSONArray().also { laps ->
                    r.laps.forEach { l -> laps.put(JSONObject().apply {
                        put("start", l.startTime.toString())
                        put("end", l.endTime.toString())
                        runCatching { l.length?.let { put("length_m", it.inMeters) } }
                    }) }
                })
            })
        }
    }

    /** Devuelve el nombre legible del tipo de ejercicio (SDK trae el mapa ya invertido). */
    private fun exerciseName(type: Int): String =
        ExerciseSessionRecord.EXERCISE_TYPE_INT_TO_STRING_MAP[type]
            ?.replace("_", " ")
            ?.let { humanize(it) }
            ?: "Desconocido ($type)"

    /** Devuelve el nombre legible del tipo de segmento (mapa construido por reflexion). */
    private fun segmentName(type: Int): String =
        SEGMENT_TYPE_NAMES[type] ?: "Desconocido ($type)"

    private fun humanize(s: String): String =
        s.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase() }
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
