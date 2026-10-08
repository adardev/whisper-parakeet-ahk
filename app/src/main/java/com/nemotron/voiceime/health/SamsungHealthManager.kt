package com.nemotron.voiceime.health

import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.Field
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Lee Samsung Health Data SDK directamente y devuelve un snapshot JSON. */
class SamsungHealthManager(context: Context) {
    private val store = HealthDataService.getStore(context.applicationContext)
    // Samsung Health may keep the last weight measurement for years, while
    // the other streams are intentionally limited to recent data.
    private val bodyCompositionHistorySeconds = 10L * 365L * 24L * 60L * 60L

    // Son tipos que Samsung Health Data SDK expone como registros legibles.
    private val readableTypes: List<DataType> = listOf(
        DataTypes.EXERCISE,
        DataTypes.EXERCISE_LOCATION,
        DataTypes.HEART_RATE,
        DataTypes.STEPS,
        DataTypes.ACTIVITY_SUMMARY,
        DataTypes.FLOORS_CLIMBED,
        DataTypes.SLEEP,
        DataTypes.SLEEP_GOAL,
        DataTypes.STEPS_GOAL,
        DataTypes.ACTIVE_CALORIES_BURNED_GOAL,
        DataTypes.ACTIVE_TIME_GOAL,
        DataTypes.ENERGY_SCORE,
        DataTypes.USER_PROFILE,
        DataTypes.BLOOD_OXYGEN,
        DataTypes.BLOOD_GLUCOSE,
        DataTypes.BLOOD_PRESSURE,
        DataTypes.BODY_COMPOSITION,
        DataTypes.WATER_INTAKE,
        DataTypes.WATER_INTAKE_GOAL,
        DataTypes.NUTRITION,
        DataTypes.NUTRITION_GOAL,
        DataTypes.BODY_TEMPERATURE,
        DataTypes.SKIN_TEMPERATURE,
        DataTypes.SLEEP_APNEA,
        DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION
    )

    private fun permissions(): Set<Permission> = readableTypes
        .map { Permission.of(it, AccessType.READ) }
        .toSet()

    fun permissionSetForSetup(): Set<Permission> = permissions()

    suspend fun grantedPermissions(): Set<Permission> = withContext(Dispatchers.IO) {
        store.getGrantedPermissions(permissions())
    }

    suspend fun readAllData(start: Instant, end: Instant): JSONObject = withContext(Dispatchers.IO) {
        val endLocal = LocalDateTime.ofInstant(end, ZoneId.systemDefault())
        val result = JSONObject()

        for (type in readableTypes) {
            try {
                val typeStart = if (type == DataTypes.BODY_COMPOSITION) {
                    start.minusSeconds(bodyCompositionHistorySeconds)
                } else start
                val typeFilter = LocalTimeFilter.of(
                    LocalDateTime.ofInstant(typeStart, ZoneId.systemDefault()), endLocal
                )
                @Suppress("UNCHECKED_CAST")
                val readable = type as DataType.Readable<HealthDataPoint, ReadDataRequest.Builder<HealthDataPoint>>
                @Suppress("UNCHECKED_CAST")
                val builder = readable.readDataRequestBuilder as ReadDataRequest.DualTimeBuilder<HealthDataPoint>
                val response = withTimeoutOrNull(
                    if (type == DataTypes.BODY_COMPOSITION) 15_000L else 5_000L
                ) {
                    store.readData(
                        builder.setLocalTimeFilter(typeFilter).setOrdering(Ordering.ASC).build()
                    )
                } ?: continue
                val points = JSONArray()
                // Puede haber años de mediciones de peso. El agente necesita
                // el valor más reciente y Firestore limita cada documento a
                // 1 MiB; conservar todo el histórico rompería la subida.
                val dataPoints = if (type == DataTypes.BODY_COMPOSITION) {
                    response.dataList.takeLast(1)
                } else response.dataList
                dataPoints.forEach { points.put(pointToJson(type, it)) }
                result.put(type.name, points)
            } catch (e: Exception) {
                // Unavailable/ungranted types do not prevent the rest of the snapshot,
                // pero dejamos el motivo en log para no ocultar BODY_COMPOSITION.
                android.util.Log.w("SamsungHealthManager",
                    "No se pudo leer ${type.name}: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        result
    }

    private fun pointToJson(type: DataType, point: HealthDataPoint): JSONObject {
        val out = JSONObject()
        out.put("uid", point.uid)
        out.put("startTime", point.startTime.toString())
        out.put("endTime", point.endTime.toString())
        out.put("dataType", type.name)
        // Los campos públicos del DataType son la forma oficial de extraer valores.
        type.javaClass.fields
            .filter { it.type == Field::class.java }
            // Samsung exposes SERIES_DATA as a Java object toString with
            // thousands of internal references. It is not useful remotely
            // and can push the Firestore document above 1 MiB.
            .filter { it.name != "SERIES_DATA" }
            .forEach { field ->
                runCatching {
                    val sdkField = field.get(null) as Field<Any>
                    point.getValue(sdkField)?.let { value ->
                        if (field.name == "SESSIONS" && value is List<*>) {
                            val details = JSONArray()
                            value.forEach { session ->
                                if (session != null) {
                                    val call: (String) -> Any? = { name ->
                                        session.javaClass.getMethod(name).invoke(session)
                                    }
                                    details.put(JSONObject()
                                        .put("startTime", call("getStartTime").toString())
                                        .put("endTime", call("getEndTime").toString())
                                        .put("durationSeconds", call("getDuration").toString())
                                        .put("distanceMeters", call("getDistance"))
                                        .put("calories", call("getCalories"))
                                        .put("meanHeartRate", call("getMeanHeartRate"))
                                        .put("maxHeartRate", call("getMaxHeartRate"))
                                        .put("meanSpeed", call("getMeanSpeed"))
                                        .put("maxSpeed", call("getMaxSpeed"))
                                        .put("meanCadence", call("getMeanCadence"))
                                        .put("meanPower", call("getMeanPower"))
                                        .put("autoDetected", call("getAutoDetected")))
                                }
                            }
                            out.put("sessionDetails", details)
                        } else out.put(field.name, value.toString())
                    }
                }
            }
        return out
    }
}
