package io.github.project.vault.core.cloud

import android.accounts.Account
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleDriveManager(private val context: Context) : CloudProvider {
    override val providerName: String = "Google Drive"

    private val prefs: SharedPreferences = context.getSharedPreferences("vault_cloud_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    var userEmail: String?
        get() = prefs.getString("gdrive_user_email", null)
        private set(value) = prefs.edit().putString("gdrive_user_email", value).apply()

    var displayName: String?
        get() = prefs.getString("gdrive_display_name", null)
        private set(value) = prefs.edit().putString("gdrive_display_name", value).apply()

    var accessToken: String?
        get() = prefs.getString("gdrive_access_token", null)
        private set(value) = prefs.edit().putString("gdrive_access_token", value).apply()

    var refreshToken: String?
        get() = prefs.getString("gdrive_refresh_token", null)
        private set(value) = prefs.edit().putString("gdrive_refresh_token", value).apply()

    private var cachedFolderId: String? = null

    fun saveAccount(email: String, name: String?, token: String, refreshTok: String? = null) {
        userEmail = email
        displayName = name
        accessToken = token
        if (!refreshTok.isNullOrBlank()) {
            refreshToken = refreshTok
        }
    }

    suspend fun fetchTokenFromGoogleAccount(account: Account): String? = withContext(Dispatchers.IO) {
        try {
            val scope = "oauth2:https://www.googleapis.com/auth/drive.file"
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
        return !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()
    }

    override suspend fun disconnect() {
        cachedFolderId = null
        prefs.edit()
            .remove("gdrive_access_token")
            .remove("gdrive_refresh_token")
            .remove("gdrive_user_email")
            .remove("gdrive_display_name")
            .apply()
    }

    /**
     * Refreshes the OAuth 2.0 access token using the stored refresh token.
     */
    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val rToken = refreshToken ?: return@withContext null
        try {
            val formBody = FormBody.Builder()
                .add("client_id", OAuthManager.CLIENT_ID)
                .add("client_secret", OAuthManager.CLIENT_SECRET)
                .add("refresh_token", rToken)
                .add("grant_type", "refresh_token")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("GoogleDriveManager", "Token refresh failed with code ${response.code}: ${response.body?.string()}")
                return@withContext null
            }

            val json = JSONObject(response.body?.string() ?: "")
            val newAccessToken = json.getString("access_token")
            accessToken = newAccessToken
            Log.d("GoogleDriveManager", "Successfully refreshed access token")
            newAccessToken
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Exception refreshing access token", e)
            null
        }
    }

    private suspend fun getValidToken(): String? {
        val current = accessToken
        if (!current.isNullOrBlank()) {
            return current
        }
        return refreshAccessToken()
    }

    private suspend fun executeWithAuth(requestBuilder: (String) -> Request): Response? = withContext(Dispatchers.IO) {
        var token = getValidToken() ?: return@withContext null
        var response = try {
            httpClient.newCall(requestBuilder(token)).execute()
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Network error executing request", e)
            return@withContext null
        }

        if (response.code == 401) {
            response.close()
            Log.w("GoogleDriveManager", "Received 401 Unauthorized. Attempting to refresh token...")
            token = refreshAccessToken() ?: return@withContext null
            response = try {
                httpClient.newCall(requestBuilder(token)).execute()
            } catch (e: Exception) {
                Log.e("GoogleDriveManager", "Network error on retried request", e)
                return@withContext null
            }
        }
        response
    }

    /**
     * Finds or creates a visible 'FrostBackup' folder in the user's main Google Drive (My Drive).
     */
    suspend fun getOrCreateBackupFolder(): String? = withContext(Dispatchers.IO) {
        cachedFolderId?.let { return@withContext it }
        try {
            val query = "name = 'FrostBackup' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&spaces=drive&fields=files(id,name)"
            val response = executeWithAuth { token ->
                Request.Builder()
                    .url(searchUrl)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            }

            if (response != null && response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val files = JSONObject(body).optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val id = files.getJSONObject(0).getString("id")
                    cachedFolderId = id
                    Log.d("GoogleDriveManager", "Found existing FrostBackup folder in Drive: $id")
                    return@withContext id
                }
            }

            // Create visible folder in root of Google Drive
            val createJson = JSONObject().apply {
                put("name", "FrostBackup")
                put("mimeType", "application/vnd.google-apps.folder")
            }
            val createResponse = executeWithAuth { token ->
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files")
                    .header("Authorization", "Bearer $token")
                    .post(createJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()
            }

            if (createResponse != null && createResponse.isSuccessful) {
                val body = createResponse.body?.string() ?: ""
                val id = JSONObject(body).getString("id")
                cachedFolderId = id
                Log.d("GoogleDriveManager", "Created visible FrostBackup folder in Drive: $id")
                id
            } else {
                Log.e("GoogleDriveManager", "Failed to create FrostBackup folder: ${createResponse?.code}")
                null
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Error resolving FrostBackup folder", e)
            null
        }
    }

    override suspend fun uploadBackup(file: File, metadataJson: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                Log.e("GoogleDriveManager", "Upload failed: File does not exist or cannot be read: ${file.absolutePath}")
                return@withContext false
            }

            // Put backup into visible FrostBackup folder
            val folderId = getOrCreateBackupFolder() ?: "root"

            val initJson = JSONObject().apply {
                put("name", file.name)
                put("parents", org.json.JSONArray().put(folderId))
                put("description", metadataJson)
            }

            val initResponse = executeWithAuth { token ->
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                    .header("Authorization", "Bearer $token")
                    .header("X-Upload-Content-Type", "application/octet-stream")
                    .header("X-Upload-Content-Length", file.length().toString())
                    .post(initJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()
            } ?: return@withContext false

            if (!initResponse.isSuccessful) {
                Log.e("GoogleDriveManager", "Resumable init failed: HTTP ${initResponse.code} - ${initResponse.body?.string()}")
                return@withContext false
            }

            val uploadLocation = initResponse.header("Location") ?: return@withContext false

            val fileBody = file.asRequestBody("application/octet-stream".toMediaType())
            val uploadRequest = Request.Builder()
                .url(uploadLocation)
                .put(fileBody)
                .build()

            val uploadResponse = httpClient.newCall(uploadRequest).execute()
            val isOk = uploadResponse.isSuccessful
            if (isOk) {
                onProgress(1.0f)
                Log.d("GoogleDriveManager", "Successfully uploaded ${file.name} to visible FrostBackup folder in Drive")
            } else {
                Log.e("GoogleDriveManager", "Upload payload failed: HTTP ${uploadResponse.code} - ${uploadResponse.body?.string()}")
            }
            isOk
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Exception during upload", e)
            false
        }
    }

    override suspend fun downloadBackup(fileId: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = executeWithAuth { token ->
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            } ?: return@withContext false

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
            Log.e("GoogleDriveManager", "Exception downloading backup", e)
            false
        }
    }

    override suspend fun listBackups(): List<CloudBackupItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<CloudBackupItem>()
        try {
            val folderId = getOrCreateBackupFolder()

            // 1. Query visible FrostBackup folder
            if (folderId != null) {
                val query = "'$folderId' in parents and trashed = false"
                val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(query, "UTF-8")}&spaces=drive&fields=files(id,name,size,modifiedTime,description)"
                val response = executeWithAuth { token ->
                    Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                }

                if (response != null && response.isSuccessful) {
                    val responseString = response.body?.string() ?: ""
                    val jsonObject = JSONObject(responseString)
                    val filesArray = jsonObject.optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val f = filesArray.getJSONObject(i)
                            val id = f.getString("id")
                            val name = f.getString("name")
                            val size = f.optLong("size", 0L)
                            val modified = System.currentTimeMillis()
                            val pkg = name.substringBeforeLast("_")
                            items.add(CloudBackupItem(id, name, size, modified, pkg))
                        }
                    }
                }
            }

            // 2. Also check appDataFolder for any earlier backups
            try {
                val appDataUrl = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,size,modifiedTime,description)"
                val appDataResponse = executeWithAuth { token ->
                    Request.Builder()
                        .url(appDataUrl)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                }
                if (appDataResponse != null && appDataResponse.isSuccessful) {
                    val responseString = appDataResponse.body?.string() ?: ""
                    val jsonObject = JSONObject(responseString)
                    val filesArray = jsonObject.optJSONArray("files")
                    if (filesArray != null) {
                        for (i in 0 until filesArray.length()) {
                            val f = filesArray.getJSONObject(i)
                            val id = f.getString("id")
                            if (items.none { it.fileId == id }) {
                                val name = f.getString("name")
                                val size = f.optLong("size", 0L)
                                val modified = System.currentTimeMillis()
                                val pkg = name.substringBeforeLast("_")
                                items.add(CloudBackupItem(id, name, size, modified, pkg))
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {}

            items
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Exception listing backups", e)
            items
        }
    }

    override suspend fun deleteBackup(fileId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = executeWithAuth { token ->
                Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
            } ?: return@withContext false
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("GoogleDriveManager", "Exception deleting backup", e)
            false
        }
    }
}
