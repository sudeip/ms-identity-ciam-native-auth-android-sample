package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.app.Application
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.Logger
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.nativeauth.INativeAuthPublicClientApplication
import com.microsoft.identity.nativeauth.NativeAuthPublicClientApplicationParameters
import org.json.JSONObject

object AuthClient : Application() {
    private lateinit var authClient: INativeAuthPublicClientApplication

    const val EXTRA_CLIENT_ID = "native_auth_client_id"
    const val EXTRA_AUTHORITY_URL = "native_auth_authority_url"

    /** The authority the client was initialized with - either the caller-supplied one, or read
     *  straight out of auth_config_native_auth.json when falling back to the default config.
     *  Exposed so callers can build authority-relative requests (e.g. the v2.0 logout endpoint,
     *  see HomeFragment.clearBrowserSession) without hardcoding the tenant URL a second time. */
    var authorityUrl: String? = null
        private set

    @JvmStatic
    fun getAuthClient(): INativeAuthPublicClientApplication {
        return authClient
    }

    // Initialize the auth client with the provided clientId and authorityUrl, or with the default config file if they are not provided.
    @JvmStatic
    fun initialize(context: Context, clientId: String? = null, authorityUrl: String? = null) {
        Logger.getInstance().setExternalLogger { tag, logLevel, message, containsPII ->
            Log.e(
                "MSAL",
                "$tag $logLevel $message"
            )
        }

        // If clientId and authorityUrl are provided, create the auth client with the provided values. Otherwise, create the auth client with the default config file.
        if (clientId != null && authorityUrl != null) {
            this.authorityUrl = authorityUrl

            val parameters = NativeAuthPublicClientApplicationParameters(
                clientId = clientId,
                authorityUrl = authorityUrl,
                challengeTypes = listOf("oob", "password")
            )
            parameters.capabilities = listOf("mfa_required", "registration_required")

            authClient = PublicClientApplication.createNativeAuthPublicClientApplication(
                context,
                parameters
            )
        } else {
            this.authorityUrl = readAuthorityFromConfig(context)

            authClient = PublicClientApplication.createNativeAuthPublicClientApplication(
                context,
                R.raw.auth_config_native_auth
            )
        }
    }

    private fun readAuthorityFromConfig(context: Context): String? {
        return try {
            val json = context.resources.openRawResource(R.raw.auth_config_native_auth)
                .bufferedReader().use { it.readText() }
            JSONObject(json).getJSONArray("authorities").getJSONObject(0).getString("authority_url")
        } catch (e: Exception) {
            Log.e("MSAL", "Could not read authority_url from auth_config_native_auth.json", e)
            null
        }
    }
}