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
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration

/**
 * Uploads Health Connect snapshots to Firebase Realtime Database.
 *
 * The phone is the only component that reads Health Connect. Firebase is the
 * internet-accessible relay consumed by the NAS/server, so no LAN/VPN is
 * required. Anonymous Firebase Auth keeps the database scoped to this app
 * installation without putting a server credential in the APK.
 */
class FirebaseHealthUploader(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseHealthUploader"
        private const val AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp"
        private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(30))
        .writeTimeout(Duration.ofSeconds(30))
        .build()

    suspend fun upload(date: String, snapshot: JSONObject) = withContext(Dispatchers.IO) {
        val apiKey = SecureStore.getFirebaseApiKey(context)
        val databaseUrl = SecureStore.getFirebaseDatabaseUrl(context).trimEnd('/')
        require(apiKey.isNotBlank()) { "Firebase API key no configurada" }
        require(databaseUrl.startsWith("https://")) { "Firebase Database URL inválida" }

        val auth = authenticate(apiKey)
        val pathDate = URLEncoder.encode(date, "UTF-8")
        val url = "$databaseUrl/health/${auth.localId}/$pathDate.json?auth=${auth.idToken}"
        val response = request(
            Request.Builder()
                .url(url)
                .put(snapshot.toString().toRequestBody("application/json".toMediaType()))
                .build())
        if (!response.first) throw IllegalStateException("Firebase upload HTTP ${response.second}")
        Log.d(TAG, "Health snapshot uploaded: $date")
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
        val r = request(
            Request.Builder().url("$AUTH_URL?key=$apiKey").post(body).build()
        )
        if (!r.first) throw IllegalStateException("Firebase Auth HTTP ${r.second}: ${r.third}")
        val json = JSONObject(r.third)
        SecureStore.setFirebaseRefreshToken(context, json.getString("refreshToken"))
        SecureStore.setFirebaseLocalId(context, json.getString("localId"))
        Auth(json.getString("idToken"), json.getString("localId"))
    }

    private fun request(request: Request): Triple<Boolean, Int, String> {
        client.newCall(request).execute().use { response ->
            return Triple(response.isSuccessful, response.code, response.body?.string().orEmpty())
        }
    }

    private data class Auth(val idToken: String, val localId: String)
}
