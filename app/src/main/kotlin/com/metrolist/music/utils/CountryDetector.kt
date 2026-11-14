/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.telephony.TelephonyManager
import com.metrolist.music.constants.CountryCodeToName
import timber.log.Timber
import java.util.Locale
import java.util.TimeZone

object CountryDetector {
    const val GLOBAL_CODE = "GLOBAL"

    /**
     * Determines user's country code (ISO 3166-1 alpha-2) without requiring location permissions.
     * Checks:
     * 1. Telephony Network Country ISO (zero permission, matches current cellular network)
     * 2. Telephony SIM Country ISO (zero permission, works even in flight mode with Wi-Fi)
     * 3. TimeZone heuristic (zero permission, works on Wi-Fi tablets without SIM)
     * 4. System Locale country (e.g., en_IN, de_DE)
     * 5. Defaults to "US" if all else fails.
     */
    fun detectCountry(context: Context): String {
        val telephony = runCatching {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }.getOrNull()

        // 1. Cellular Network ISO
        val networkIso = runCatching {
            telephony?.networkCountryIso?.takeIf { it.isNotBlank() }?.uppercase()
        }.getOrNull()
        if (networkIso != null && networkIso in CountryCodeToName) {
            Timber.tag("CountryDetector").d("Detected country from network: $networkIso")
            return networkIso
        }

        // 2. SIM Card ISO
        val simIso = runCatching {
            telephony?.simCountryIso?.takeIf { it.isNotBlank() }?.uppercase()
        }.getOrNull()
        if (simIso != null && simIso in CountryCodeToName) {
            Timber.tag("CountryDetector").d("Detected country from SIM: $simIso")
            return simIso
        }

        // 3. TimeZone heuristic
        val tzCountry = detectCountryFromTimeZone()
        if (tzCountry != null && tzCountry in CountryCodeToName) {
            Timber.tag("CountryDetector").d("Detected country from TimeZone: $tzCountry")
            return tzCountry
        }

        // 4. System Locale country
        val localeCountry = runCatching {
            Locale.getDefault().country?.takeIf { it.isNotBlank() }?.uppercase()
        }.getOrNull()
        if (localeCountry != null && localeCountry in CountryCodeToName) {
            Timber.tag("CountryDetector").d("Detected country from Locale: $localeCountry")
            return localeCountry
        }

        return "US"
    }

    private fun detectCountryFromTimeZone(): String? {
        val id = TimeZone.getDefault().id ?: return null
        return when {
            id.startsWith("Asia/Kolkata") || id.startsWith("Asia/Calcutta") -> "IN"
            id.startsWith("Europe/London") -> "GB"
            id.startsWith("Asia/Tokyo") -> "JP"
            id.startsWith("Asia/Seoul") -> "KR"
            id.startsWith("Europe/Berlin") -> "DE"
            id.startsWith("Europe/Paris") -> "FR"
            id.startsWith("America/Sao_Paulo") -> "BR"
            id.startsWith("Asia/Dubai") -> "AE"
            id.startsWith("Asia/Dhaka") -> "BD"
            id.startsWith("Asia/Karachi") -> "PK"
            id.startsWith("Asia/Jakarta") -> "ID"
            id.startsWith("Europe/Rome") -> "IT"
            id.startsWith("Europe/Madrid") -> "ES"
            id.startsWith("Asia/Singapore") -> "SG"
            id.startsWith("Asia/Bangkok") -> "TH"
            id.startsWith("Asia/Kuala_Lumpur") -> "MY"
            id.startsWith("Asia/Manila") -> "PH"
            id.startsWith("America/Toronto") || id.startsWith("America/Vancouver") -> "CA"
            id.startsWith("Australia/") -> "AU"
            id.startsWith("America/") -> "US"
            else -> null
        }
    }
}

