package io.github.project.vault.core.cloud

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GoogleDriveManager(private val context: Context) : CloudProvider {
    override val providerName: String = "Google Drive"

    private val prefs: SharedPreferences = context.getSharedPreferences("vault_cloud_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var userEmail: String?
        get() = prefs.getString("gdrive_user_email", null)
        private set(value) = prefs.edit().putString("gdrive_user_email", value).apply()

    var displayName: String?
        get() = prefs.getString("gdrive_display_name", null)
        private set(value) = prefs.edit().putString("gdrive_display_name", value).apply()

    private var accessToken: String?
        get() = prefs.getString("gdrive_access_token", null)
        set(value) = prefs.edit().putString("gdrive_access_token", value).apply()

    fun saveAccount(email: String, name: String?, token: String) {
        userEmail = email
        displayName = name
        accessToken = token
    }

    suspend fun fetchTokenFromGoogleAccount(account: Account): String? = withContext(Dispatchers.IO) {
        try {
            val scope = "oauth2:https://www.googleapis.com/auth/drive.appdata"
            val token = GoogleAuthUtil.getToken(context, account, scope)
            accessToken = token
            userEmail = account.name
            token
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun isConnected(): Boolean {
        return !accessToken.isNullOrBlank()
    }

    override suspend fun disconnect() {
        prefs.edit()
            .remove("gdrive_access_token")
            .remove("gdrive_user_email")
            .remove("gdrive_display_name")
            .apply()
    }

    override suspend fun uploadBackup(file: File, metadataJson: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext false
        try {
            val initJson = JSONObject().apply {
                put("name", file.name)
                put("parents", org.json.JSONArray().put("appDataFolder"))
                put("description", metadataJson)
            }

            val initRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("X-Upload-Content-Type", "application/octet-stream")
                .header("X-Upload-Content-Length", file.length().toString())
                .post(initJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val initResponse = httpClient.newCall(initRequest).execute()
            if (!initResponse.isSuccessful) return@withContext false

            val uploadLocation = initResponse.header("Location") ?: return@withContext false

            val fileBody = file.asRequestBody("application/octet-stream".toMediaType())
            val uploadRequest = Request.Builder()
                .url(uploadLocation)
                .header("Authorization", "Bearer $token")
                .put(fileBody)
                .build()

            val uploadResponse = httpClient.newCall(uploadRequest).execute()
            onProgress(1.0f)
            uploadResponse.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun downloadBackup(fileId: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext false

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            var bytesReadTotal = 0L

            body.byteStream().use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesReadTotal += read
                        if (contentLength > 0) {
                            onProgress(bytesReadTotal.toFloat() / contentLength)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun listBackups(): List<CloudBackupItem> = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext emptyList()
        try {
            val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size,modifiedTime,description)"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val responseString = response.body?.string() ?: return@withContext emptyList()
            val jsonObject = JSONObject(responseString)
            val filesArray = jsonObject.optJSONArray("files") ?: return@withContext emptyList()

            val items = mutableListOf<CloudBackupItem>()
            for (i in 0 until filesArray.length()) {
                val f = filesArray.getJSONObject(i)
                val id = f.getString("id")
                val name = f.getString("name")
                val size = f.optLong("size", 0L)
                val modified = System.currentTimeMillis()
                val pkg = name.substringBeforeLast("_")
                items.add(CloudBackupItem(id, name, size, modified, pkg))
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun deleteBackup(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
