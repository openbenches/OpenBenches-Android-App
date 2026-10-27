package org.openbenches.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale

object AuthJwt {
    fun decodePayloadClaims(jwt: String?): JSONObject? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return try {
            val bytes = Base64.getUrlDecoder().decode(parts[1])
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun displayName(claims: JSONObject?, fallbackUsername: String?): String? {
        return claims?.optString("nickname")?.takeIf { it.isNotBlank() }
            ?: claims?.optString("name")?.takeIf { it.isNotBlank() }
            ?: claims?.optString("email")?.takeIf { it.isNotBlank() }
            ?: fallbackUsername?.takeIf { it.isNotBlank() }
    }

    fun pictureUrl(claims: JSONObject?, fallbackPictureUrl: String?): String? {
        return claims?.optString("picture")?.takeIf { it.isNotBlank() }
            ?: fallbackPictureUrl?.takeIf { it.isNotBlank() }
    }

    /** Provider segment of `sub` before `|`, e.g. `github` from `github|123`. */
    fun verifiedVia(claims: JSONObject?): String? {
        return claims?.optString("sub")
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore('|')
            ?.takeIf { it.isNotBlank() }
    }

    fun openBenchesUserIdFromSub(claims: JSONObject?): String? {
        return claims?.optString("sub")
            ?.substringAfter('|', "")
            ?.takeIf { it.isNotBlank() }
    }

    fun issuedAtEpochSeconds(claims: JSONObject?): Long? {
        val iat = claims?.optLong("iat", 0L) ?: 0L
        return iat.takeIf { it > 0L }
    }

    fun expiresAtEpochSeconds(claims: JSONObject?, fallbackExpiresAtMs: Long?): Long? {
        val exp = claims?.optLong("exp", 0L) ?: 0L
        if (exp > 0L) return exp
        return fallbackExpiresAtMs?.let { it / 1000L }?.takeIf { it > 0L }
    }

    /**
     * True when [jwt] is non-blank and its expiry is still after [nowMs] (+ optional skew).
     * Prefers JWT `exp`; falls back to [fallbackExpiresAtMs]. Missing expiry ⇒ not valid.
     */
    fun isTokenStillValid(
        jwt: String?,
        fallbackExpiresAtMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        skewMs: Long = 60_000L,
    ): Boolean {
        if (jwt.isNullOrBlank()) return false
        val expiresAtSec = expiresAtEpochSeconds(decodePayloadClaims(jwt), fallbackExpiresAtMs)
            ?: return false
        return expiresAtSec * 1000L > nowMs + skewMs
    }

    fun formatEpochSeconds(epochSeconds: Long?): String {
        if (epochSeconds == null || epochSeconds <= 0L) return "—"
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy MMMM dd HH:mm", Locale.getDefault())
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        } catch (_: Exception) {
            "—"
        }
    }

    fun formatIsoDateTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            val instant = Instant.parse(iso)
            val formatter = DateTimeFormatter.ofPattern("yyyy MMMM dd", Locale.getDefault())
            instant.atZone(ZoneId.systemDefault()).format(formatter)
        } catch (_: Exception) {
            iso
        }
    }
}
