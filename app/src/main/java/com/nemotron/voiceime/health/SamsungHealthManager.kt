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
        val startLocal = LocalDateTime.ofInstant(start, ZoneId.systemDefault())
        val endLocal = LocalDateTime.ofInstant(end, ZoneId.systemDefault())
        val filter = LocalTimeFilter.of(startLocal, endLocal)
        val result = JSONObject()

        for (type in readableTypes) {
            try {
                @Suppress("UNCHECKED_CAST")
                val readable = type as DataType.Readable<HealthDataPoint, ReadDataRequest.Builder<HealthDataPoint>>
                @Suppress("UNCHECKED_CAST")
                val builder = readable.readDataRequestBuilder as ReadDataRequest.DualTimeBuilder<HealthDataPoint>
                val response = withTimeoutOrNull(5_000L) {
                    store.readData(
                        builder.setLocalTimeFilter(filter).setOrdering(Ordering.ASC).build()
                    )
                } ?: continue
                val points = JSONArray()
                response.dataList.forEach { points.put(pointToJson(type, it)) }
                result.put(type.name, points)
            } catch (_: Exception) {
                // Unavailable/ungranted types do not prevent the rest of the snapshot.
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
