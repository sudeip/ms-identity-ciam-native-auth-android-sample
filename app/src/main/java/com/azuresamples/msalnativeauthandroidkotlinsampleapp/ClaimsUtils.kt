package com.azuresamples.msalnativeauthandroidkotlinsampleapp

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Decodes JWT (ID/access token) payloads into a claim -> (value, description) table for display.
 *
 * Ported from the companion web app's utils/claimUtils.js (createClaimsTable / decodeJwtClaims)
 * so the Home screen's claims tables read the same way as the web app's.
 */
object ClaimsUtils {

    data class ClaimRow(val claim: String, val value: String, val description: String)

    /**
     * Base64url-decodes the payload segment of a JWT and returns its claims as a display-ready,
     * ordered list of (claim, value, description) rows. Unlike the ID token (which the native-auth
     * SDK already decodes for you as idTokenClaims), the access token is opaque to the client per
     * spec - Microsoft's access tokens happen to be JWTs too, so this decodes both the same way.
     */
    fun decodeJwtClaims(jwt: String?): List<ClaimRow> {
        if (jwt.isNullOrEmpty()) return emptyList()

        val parts = jwt.split(".")
        if (parts.size < 2) return emptyList()

        return try {
            val decodedBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = JSONObject(String(decodedBytes, Charsets.UTF_8))
            buildClaimRows(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildClaimRows(claims: JSONObject): List<ClaimRow> {
        val rows = mutableListOf<ClaimRow>()
        val keys = claims.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (key) {
                "uti", "rh" -> Unit // Skipped, same as the web app's table.
                "iat", "nbf", "exp" -> rows.add(ClaimRow(key, formatDate(claims.opt(key)), claimDescription(key)))
                "acrs" -> rows.add(ClaimRow(key, stringifyValue(claims.opt(key)), claimDescription(key)))
                else -> rows.add(ClaimRow(key, stringifyValue(claims.opt(key)), claimDescription(key)))
            }
        }
        return rows
    }

    private fun stringifyValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONArray -> (0 until value.length()).joinToString(", ") { value.optString(it) }
        else -> value.toString()
    }

    private fun formatDate(value: Any?): String {
        val seconds = value?.toString()?.toLongOrNull() ?: return stringifyValue(value)
        return "$seconds - [${Date(seconds * 1000)}]"
    }

    /** Claim -> human-readable description, ported from claimUtils.js's createClaimsTable. */
    fun claimDescription(claim: String): String = when (claim) {
        "aud" -> "Identifies the intended recipient of the token. In ID tokens, the audience is your app's Application ID, assigned to your app in the Microsoft Entra admin center."
        "iss" -> "Identifies the issuer, or authorization server that constructs and returns the token. It also identifies the external tenant for which the user was authenticated."
        "iat" -> "Issued At indicates when the authentication for this token occurred."
        "nbf" -> "The nbf (not before) claim identifies the time before which the JWT must not be accepted for processing."
        "exp" -> "The exp (expiration time) claim identifies the expiration time on or after which the JWT must not be accepted for processing."
        "name" -> "The name claim provides a human-readable value that identifies the subject of the token. Not guaranteed to be unique, and designed for display purposes only."
        "preferred_username" -> "The primary username that represents the user. Its value is mutable and must not be used to make authorization decisions."
        "nonce" -> "The nonce matches the parameter included in the original /authorize request to the IDP."
        "oid" -> "The oid (user's object id) is the only claim that should be used to uniquely identify a user in an external tenant."
        "tid" -> "The tenant ID. Used to ensure that only users from the current external tenant can access this app."
        "upn" -> "(user principal name) - might be unique amongst the active set of users in a tenant but can get reassigned over time."
        "email" -> "Email might be unique amongst the active set of users in a tenant but can get reassigned over time."
        "acct" -> "Lets you know what the type of user (homed, guest) is."
        "sid" -> "Session ID, used for per-session user sign-out."
        "acrs" -> "Authentication Context Class Reference(s) satisfied for this token. Set by Conditional Access when a policy requiring, e.g., MFA has been met."
        "sub" -> "The sub claim is a pairwise identifier - unique to a particular application ID."
        "ver" -> "Version of the token issued by the Microsoft identity platform."
        "scp" -> "The set of scopes exposed by your API that the client was granted consent to call."
        "azp", "appid" -> "The Application ID of the client that requested this token."
        else -> ""
    }
}
