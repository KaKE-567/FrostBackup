package io.github.project.vault.core.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

object OAuthManager {
    // Default open-source Client ID for installed Android apps (PKCE enabled)
    // Users can also supply their own client ID in settings if preferred
    const val DEFAULT_CLIENT_ID = "1053749725515-m49i9b4u9v8rsv6e5gq09o8e24k30b6s.apps.googleusercontent.com"
    const val REDIRECT_URI = "io.github.project.vault://oauth2redirect"
    private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"

    private const val PREF_KEY_VERIFIER = "pkce_code_verifier"

    private val httpClient = OkHttpClient()

    fun startBrowserAuth(context: Context, customClientId: String? = null) {
        val clientId = customClientId?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID

        // 1. Generate PKCE code verifier and challenge
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        // Save code verifier in SharedPreferences
        val prefs = context.getSharedPreferences("vault_oauth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_VERIFIER, codeVerifier).apply()

        // 2. Build Authorization URL
        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "https://www.googleapis.com/auth/drive.appdata email profile")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()

        // 3. Launch via Chrome Custom Tabs or fallback browser
        try {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, authUri)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }

    suspend fun handleAuthCode(
        context: Context,
        code: String,
        customClientId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val clientId = customClientId?.takeIf { it.isNotBlank() } ?: DEFAULT_CLIENT_ID
        val prefs = context.getSharedPreferences("vault_oauth_prefs", Context.MODE_PRIVATE)
        val codeVerifier = prefs.getString(PREF_KEY_VERIFIER, null) ?: return@withContext false

        try {
            // Exchange code for token with PKCE verifier
            val formBody = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val tokenRequest = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build()

            val response = httpClient.newCall(tokenRequest).execute()
            if (!response.isSuccessful) return@withContext false

            val jsonResponse = JSONObject(response.body?.string() ?: "")
            val accessToken = jsonResponse.getString("access_token")

            // Fetch user profile (email and name)
            val profileRequest = Request.Builder()
                .url(USER_INFO_URL)
                .header("Authorization", "Bearer $accessToken")
                .build()

            val profileResponse = httpClient.newCall(profileRequest).execute()
            var email = "Google User"
            var displayName = "Google User"
            if (profileResponse.isSuccessful) {
                val profileJson = JSONObject(profileResponse.body?.string() ?: "")
                email = profileJson.optString("email", email)
                displayName = profileJson.optString("name", displayName)
            }

            // Save credentials
            val driveManager = GoogleDriveManager(context)
            driveManager.saveAccount(email, displayName, accessToken)

            // Clean up PKCE verifier
            prefs.edit().remove(PREF_KEY_VERIFIER).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes)
        val digest = messageDigest.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
