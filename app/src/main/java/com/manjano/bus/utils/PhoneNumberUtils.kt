package com.manjano.bus.utils

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.AsYouTypeFormatter
import java.util.Locale

/**
 * A utility class for all phone number formatting, validation, and parsing.
 * Wraps the Google libphonenumber library.
 */
object PhoneNumberUtils {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()
    private val formatterMap = mutableMapOf<String, AsYouTypeFormatter>()

    /**
     * Formats a raw digit string as the user types, based on the selected country's pattern.
     */
    fun formatPhoneNumberAsYouType(rawDigits: String?, regionCode: String): String {
        if (rawDigits.isNullOrEmpty()) return ""
        val formatter = formatterMap.getOrPut(regionCode) {
            phoneUtil.getAsYouTypeFormatter(regionCode)
        }

        formatter.clear()
        var formattedNumber = ""
        for (char in rawDigits.trim()) { // trim spaces to avoid paste issues
            if (char.isDigit()) {
                formattedNumber = formatter.inputDigit(char)
            }
        }
        return formattedNumber
    }

    /**
     * Formats the number for display after blur/focus loss.
     * Provides a “final” clean national format for UI.
     */
    fun formatForDisplay(rawDigits: String?, regionCode: String): String {
        if (rawDigits.isNullOrEmpty()) return ""
        return try {
            val protoNumber = phoneUtil.parse(rawDigits.trim(), regionCode)
            phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        } catch (e: Exception) {
            rawDigits
        }
    }

    fun isPossibleNumber(rawDigits: String?, regionCode: String): Boolean {
        if (rawDigits.isNullOrEmpty()) return false
        return try {
            val protoNumber: Phonenumber.PhoneNumber = phoneUtil.parse(rawDigits.trim(), regionCode)
            phoneUtil.isPossibleNumber(protoNumber)
        } catch (e: Exception) {
            false
        }
    }

    fun isValidNumber(rawDigits: String?, regionCode: String): Boolean {
        if (rawDigits.isNullOrEmpty()) return false

        val digits = rawDigits.filter { it.isDigit() }
        if (digits.isEmpty()) return false

        // Kenya-specific validation rules
        if (regionCode == "KE") {
            // Kenya numbers must be exactly 10 digits and start with 07 or 01
            return digits.length == 10 && (digits.startsWith("07") || digits.startsWith("01"))
        }

        return try {
            val protoNumber = phoneUtil.parse(digits, regionCode)
            phoneUtil.isValidNumber(protoNumber)
        } catch (e: Exception) {
            false
        }
    }

    fun isPhoneValidForUi(rawDigits: String?, regionCode: String): Boolean =
        isValidNumber(rawDigits, regionCode)

    fun normalizeToE164(rawDigits: String?, regionCode: String): String {
        if (rawDigits.isNullOrEmpty()) return ""
        return try {
            val protoNumber: Phonenumber.PhoneNumber = phoneUtil.parse(rawDigits.trim(), regionCode)
            phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
        } catch (e: Exception) {
            rawDigits
        }
    }

    fun getExampleNumber(regionCode: String): String {
        return try {
            val exampleNumber = phoneUtil.getExampleNumber(regionCode)
            phoneUtil.format(exampleNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
        } catch (e: Exception) {
            when (regionCode) {
                "KE" -> "+254 712 345 678"
                "UG" -> "+256 712 345 678"
                "TZ" -> "+255 712 345 678"
                "RW" -> "+250 712 345 678"
                "ET" -> "+251 912 345 678"
                else -> "+123 456 7890"
            }
        }
    }

    private fun getCountryName(region: String): String {
        return when (region) {
            "KE" -> "Kenya"
            "UG" -> "Uganda"
            "TZ" -> "Tanzania"
            "RW" -> "Rwanda"
            "ET" -> "Ethiopia"
            else -> region
        }
    }

    fun extractRawDigits(phoneNumber: String?): String {
        if (phoneNumber.isNullOrEmpty()) return ""
        return phoneNumber.filter { it.isDigit() }
    }

    fun getFullFormattedNumber(phoneNumber: String?, region: String): String {
        if (phoneNumber.isNullOrEmpty()) return ""
        return try {
            val protoNumber = phoneUtil.parse(phoneNumber.trim(), region)
            phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
        } catch (e: Exception) {
            phoneNumber
        }
    }

    /**
     * Returns the expected number of digits for each supported country.
     * Used to hide the keyboard once the full number has been typed.
     */
    fun getExpectedLength(regionCode: String): Int {
        return when (regionCode.uppercase(Locale.ROOT)) {
            "KE" -> 10 // Kenya
            "UG" -> 9  // Uganda
            "TZ" -> 9  // Tanzania
            "RW" -> 9  // Rwanda
            "ET" -> 9  // Ethiopia
            else -> 10 // fallback
        }
    }
}
