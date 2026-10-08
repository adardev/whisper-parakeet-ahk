package com.nemotron.voiceime.health

import android.content.Context
import android.util.Log
import com.nemotron.voiceime.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

/** Uploads Samsung Health Data SDK snapshots to Firebase Firestore. */
class FirebaseHealthUploader(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseHealthUploader"
        private const val AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
        private const val FIRESTORE = "https://firestore.googleapis.com/v1/projects"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    suspend fun upload(date: String, snapshot: JSONObject) = withContext(Dispatchers.IO) {
        val apiKey = SecureStore.getFirebaseApiKey(context)
        val auth = authenticate(apiKey)
        val project = SecureStore.DEFAULT_FIREBASE_PROJECT_ID
        val url = "$FIRESTORE/$project/databases/(default)/documents/" +
            "health/${auth.localId}/snapshots/$date"
        val response = request(
            Request.Builder()
                .url(url)
                .put(toFirestoreDocument(snapshot).toString()
                    .toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer ${auth.idToken}")
                .build())
        if (!response.first) throw IllegalStateException(
            "Firebase Firestore HTTP ${response.second}: ${response.third.take(300)}"
        )
        Log.d(TAG, "Health snapshot uploaded to Firestore: $date")
    }

    private fun authenticate(apiKey: String): Auth = synchronized(this) {
        val refresh = SecureStore.getFirebaseRefreshToken(context)
        if (refresh.isNotBlank()) {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .build()
            val r = request(Request.Builder().url("$TOKEN_URL?key=$apiKey").post(body).build())
            if (r.first) {
                val json = JSONObject(r.third)
                return@synchronized Auth(
                    json.getString("id_token"),
                    json.optString("user_id", SecureStore.getFirebaseLocalId(context))
                )
            }
            Log.w(TAG, "Firebase refresh token rechazado; se crea sesión anónima nueva")
        }
        val body = JSONObject().put("returnSecureToken", true)
            .toString().toRequestBody("application/json".toMediaType())
        val r = request(Request.Builder().url("$AUTH_URL?key=$apiKey").post(body).build())
        if (!r.first) throw IllegalStateException("Firebase Auth HTTP ${r.second}: ${r.third}")
        val json = JSONObject(r.third)
        SecureStore.setFirebaseRefreshToken(context, json.getString("refreshToken"))
        SecureStore.setFirebaseLocalId(context, json.getString("localId"))
        Auth(json.getString("idToken"), json.getString("localId"))
    }

    private fun toFirestoreDocument(json: JSONObject): JSONObject =
        JSONObject().put("fields", firestoreFields(json))

    private fun firestoreFields(json: JSONObject): JSONObject {
        val fields = JSONObject()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            fields.put(key, firestoreValue(json.opt(key)))
        }
        return fields
    }

    private fun firestoreValue(value: Any?): JSONObject = when (value) {
        null, JSONObject.NULL -> JSONObject().put("nullValue", JSONObject.NULL)
        is JSONObject -> JSONObject().put("mapValue", JSONObject().put("fields", firestoreFields(value)))
        is JSONArray -> {
            val values = JSONArray()
            for (i in 0 until value.length()) values.put(firestoreValue(value.opt(i)))
            JSONObject().put("arrayValue", JSONObject().put("values", values))
        }
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Number -> JSONObject().put("doubleValue", value.toDouble())
        else -> JSONObject().put("stringValue", value.toString())
    }

    private fun request(request: Request): Triple<Boolean, Int, String> {
        client.newCall(request).execute().use { response ->
            return Triple(response.isSuccessful, response.code, response.body?.string().orEmpty())
        }
    }

    private data class Auth(val idToken: String, val localId: String)
}
