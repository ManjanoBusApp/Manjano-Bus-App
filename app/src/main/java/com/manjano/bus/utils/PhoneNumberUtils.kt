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

        return try {
            val protoNumber = phoneUtil.parse(digits, regionCode)

            // This single line handles the WHOLE world correctly.
            // It checks both the correct length and the current prefixes
            // for the specific country (KE, UG, US, etc.)
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
            // Universal fallback: just show a generic pattern
            "712 345 678"
        }
    }

    private fun getCountryName(region: String): String {
        // Use Java's Locale to get the country name automatically for any region code
        return try {
            Locale("", region).getDisplayCountry(Locale.ENGLISH)
        } catch (e: Exception) {
            region
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
     * Returns the expected number of digits for ANY country dynamically.
     * This ensures 01xxx works in Kenya and any length works globally.
     */
    fun getExpectedLength(regionCode: String): Int {
        return try {
            val example = phoneUtil.getExampleNumber(regionCode)
            example.nationalNumber.toString().length
        } catch (e: Exception) {
            10 // Standard fallback
        }
    }
}
