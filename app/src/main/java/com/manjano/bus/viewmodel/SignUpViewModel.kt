package com.manjano.bus.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.manjano.bus.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.manjano.bus.models.CountryRepository
import com.manjano.bus.models.Country


private fun sanitizeKey(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

data class SignUpUiState(
    val otpDigits: List<String> = List(Constants.OTP_LENGTH) { "" },
    val otpErrorMessage: String? = null,
    val shouldShakeOtp: Boolean = false,
    val isSendingOtp: Boolean = false,
    val isOtpSubmitting: Boolean = false,
    val resendTimerSeconds: Int = 0,
    val canResendOtp: Boolean = true,
    val showOtpError: Boolean = false,
    val navigateToDashboard: Boolean = false,
    val rememberMe: Boolean = false,
    val parentName: String = "",
    val childrenNames: String = "",
    val showSmsMessage: Boolean = false
)

class SignUpViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> get() = _uiState

    private val _isOtpValid = MutableStateFlow(false)
    val isOtpValid: StateFlow<Boolean> get() = _isOtpValid


    private val database = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()

    // ============ NEW: Phone number check for parent signup ============
    private val _isPhoneAllowed = MutableStateFlow<Boolean?>(null) // null = not checked yet
    val isPhoneAllowed: StateFlow<Boolean?> = _isPhoneAllowed

    // NEW: show phone error only after Send Code is tapped
    private val _showPhoneError = MutableStateFlow(false)
    val showPhoneError: StateFlow<Boolean> = _showPhoneError

    fun onSendCodeTapped() {
        _showPhoneError.value = true
    }

    fun checkPhoneNumber(phoneNumber: String, schoolName: String) {
        viewModelScope.launch {
            if (phoneNumber.isBlank()) {
                _isPhoneAllowed.value = false
                return@launch
            }

            firestore.collection("parents")
                .whereEqualTo("mobileNumber", phoneNumber)
                .whereEqualTo("school", schoolName)
                .get()
                .addOnSuccessListener { documents ->
                    _isPhoneAllowed.value = !documents.isEmpty
                }
                .addOnFailureListener {
                    _isPhoneAllowed.value = false
                }
        }
    }
    // ====================================================================

    private val resendDuration = 30 // seconds

    init {
        try {
            val firebaseApp = FirebaseApp.getInstance()
            Log.d("🔥", "🔧 FirebaseApp initialized: ${firebaseApp.name}")
            Log.d("🔥", "🔧 FirebaseDatabase initialized with URL: ${database.toString()}")
        } catch (e: Exception) {
            Log.e("🔥", "❌ Firebase initialization error: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = "Firebase initialization failed: ${e.message}",
                showOtpError = true
            )
        }
    }

    // =================== ALL EXISTING FUNCTIONS BELOW REMAIN UNCHANGED ===================
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        if (connectivityManager == null) {
            Log.e("🔥", "❌ ConnectivityManager is null")
            return false
        }
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun testFirebaseConnection() {
        Log.d("🔥", "🔍 Firebase connection test skipped.")
    }

    fun setOtpValid(valid: Boolean) {
        _isOtpValid.value = valid
    }

    fun showSmsMessage() {
        _uiState.value = _uiState.value.copy(showSmsMessage = true)
    }

    fun hideSmsMessage() {
        _uiState.value = _uiState.value.copy(showSmsMessage = false)
    }

    fun requestOtp() {
        _uiState.value = _uiState.value.copy(
            isSendingOtp = true,
            resendTimerSeconds = resendDuration,
            canResendOtp = false
        )
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(isSendingOtp = false)
            startResendTimer()
        }
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            var seconds = resendDuration
            while (seconds > 0) {
                _uiState.value = _uiState.value.copy(resendTimerSeconds = seconds)
                delay(1000)
                seconds--
            }
            _uiState.value = _uiState.value.copy(
                canResendOtp = true,
                resendTimerSeconds = 0
            )
        }
    }

    fun resendOtp() {
        requestOtp()
    }

    fun getAdminRoleByMobile(mobile: String, callback: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        val normalizedInput = mobile.filter { it.isDigit() }
            .let {
                when {
                    it.startsWith("07") -> it
                    it.startsWith("254") -> "0" + it.substring(3)
                    it.startsWith("+254") -> "0" + it.substring(4)
                    else -> it
                }
            }

        db.collection("admins")
            .get()
            .addOnSuccessListener { documents ->

                val adminDoc = documents.documents.firstOrNull { doc ->
                    val dbPhoneRaw = doc.getString("mobileNumber") ?: ""
                    val dbNormalized = dbPhoneRaw.filter { it.isDigit() }
                        .let {
                            when {
                                it.startsWith("07") -> it
                                it.startsWith("254") -> "0" + it.substring(3)
                                it.startsWith("+254") -> "0" + it.substring(4)
                                else -> it
                            }
                        }
                    dbNormalized == normalizedInput
                }

                val role = adminDoc?.getString("role")
                callback(role)
            }
            .addOnFailureListener { callback(null) }
    }

    fun onOtpDigitChange(index: Int, digit: String) {
        val digits = _uiState.value.otpDigits.toMutableList().apply {
            this[index] = digit.take(1)
        }
        _uiState.value = _uiState.value.copy(otpDigits = digits)
        val enteredOtp = digits.joinToString("")
        _isOtpValid.value =
            enteredOtp.length == Constants.OTP_LENGTH && enteredOtp == Constants.TEST_OTP
    }

    fun updateOtpDigits(otp: String) {
        val digits = otp.filter { it.isDigit() }.take(Constants.OTP_LENGTH)
        val paddedDigits = List(Constants.OTP_LENGTH) { index ->
            digits.getOrNull(index)?.toString() ?: ""
        }
        _uiState.value = _uiState.value.copy(otpDigits = paddedDigits)
    }

    fun verifyOtp() {
        val enteredOtp = _uiState.value.otpDigits.joinToString("")
        if (enteredOtp == Constants.TEST_OTP) {
            _uiState.value = _uiState.value.copy(
                navigateToDashboard = true,
                showOtpError = false
            )
            Log.d("🔥", "✅ OTP verified. Signaling UI to save names and navigate.")
        } else {
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = "Incorrect OTP. Please try again.",
                showOtpError = true,
                shouldShakeOtp = true
            )
        }
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToDashboard = false)
    }

    fun normalizePhoneNumber(rawNumber: String, countryIso: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }

        return if (countryIso.equals("KE", ignoreCase = true)) {
            // Keep Kenyan numbers exactly as typed, but ensure leading 0
            when {
                digitsOnly.length == 9 -> "0$digitsOnly"      // 701234567 → 0701234567
                digitsOnly.length == 10 -> digitsOnly        // 0701234567 → 0701234567
                digitsOnly.startsWith("254") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(
                    3
                ) // 254701234567 → 0701234567
                digitsOnly.startsWith("+254") && digitsOnly.length == 13 -> "0" + digitsOnly.substring(
                    4
                ) // +254701234567 → 0701234567
                else -> rawNumber
            }
        } else {
            // International numbers: always store in full E.164
            val countryCode = CountryRepository.countries
                .find { it.isoCode.equals(countryIso, ignoreCase = true) }?.callingCode ?: ""
            when {
                rawNumber.startsWith("+") -> rawNumber               // +491735612777 → +491735612777
                digitsOnly.startsWith("0") -> "+$countryCode" + digitsOnly.drop(1) // 01735612777 → +491735612777
                else -> "+$countryCode$digitsOnly"                  // 1735612777 → +491735612777
            }
        }
    }

    fun checkPhoneNumberInFirestore(phone: String, countryIso: String) {
        val db = FirebaseFirestore.getInstance()

        // Normalize entered number (your existing function – unchanged)
        val normalizedInput = normalizePhoneNumber(phone, countryIso)

        db.collection("parents")
            .get()
            .addOnSuccessListener { documents ->
                val matched = documents.documents.any { doc ->
                    val dbNumber = doc.getString("mobileNumber") ?: ""

                    if (countryIso.equals("KE", ignoreCase = true)) {
                        // Kenyan numbers: exact match – DO NOT CHANGE
                        dbNumber == normalizedInput
                    } else {
                        // ───────────────────────────────────────────────────────────────
                        // INTERNATIONAL – fixed & more reliable version
                        // ───────────────────────────────────────────────────────────────

                        // Clean both to pure E.164 digits (keep + for comparison)
                        val dbClean = dbNumber.replace("[^+0-9]".toRegex(), "")
                        val inputClean = phone.replace("[^+0-9]".toRegex(), "")

                        // Get country calling code (e.g. "61" for AU, "44" for UK)
                        val callingCode = CountryRepository.countries
                            .find { it.isoCode.equals(countryIso, ignoreCase = true) }
                            ?.callingCode
                            ?.replace("[^+0-9]".toRegex(), "") // ensure digits only
                            ?: ""

                        // Build expected E.164 from user input
                        val expectedE164 = when {
                            inputClean.startsWith("+") -> inputClean
                            inputClean.startsWith("00") -> "+" + inputClean.drop(2)
                            inputClean.startsWith("0") -> "+$callingCode${inputClean.drop(1)}"
                            else -> "+$callingCode$inputClean"
                        }

                        // Compare cleaned versions (both should be like +61432756777)
                        dbClean == expectedE164
                    }
                }
                _isPhoneAllowed.value = matched
            }
            .addOnFailureListener {
                _isPhoneAllowed.value = false
            }
    }

    fun saveParentAndChildren(
        parentName: String,
        childrenNames: List<String>,
        context: Context
    ) {
        // Example: Save parent node
        val parentRef = FirebaseFirestore.getInstance().collection("parents").document(parentName)
        val data = hashMapOf(
            "parentName" to parentName,
            "children" to childrenNames
        )
        parentRef.set(data)
            .addOnSuccessListener { Log.d("🔥", "Parent saved") }
            .addOnFailureListener { e -> Log.e("🔥", "Failed to save parent", e) }
    }
    fun onRememberMeChange(checked: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = checked)
    }

    fun resetPhoneAllowed() {
        _isPhoneAllowed.value =
            null // make _isPhoneAllowed: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    }

    fun saveUserNames(parentName: String, childrenNames: String, context: Context) {
        Log.d("🔥", "Signup complete. No Firestore write required.")
        _uiState.value = _uiState.value.copy(navigateToDashboard = true)
    }

    // New function, outside of saveUserNames
    fun checkDriverPhoneNumberInFirestore(phone: String, countryIso: String) {
        val db = FirebaseFirestore.getInstance()
        val normalizedInput = normalizePhoneNumber(phone, countryIso)

        db.collection("drivers")
            .get()
            .addOnSuccessListener { snapshot ->
                val matched = snapshot.documents.any { doc ->
                    val dbNumber = doc.getString("mobileNumber")?.filter { it.isDigit() } ?: ""
                    val inputNumber = normalizedInput.filter { it.isDigit() }
                    dbNumber == inputNumber
                }
                _isPhoneAllowed.value = matched
            }
            .addOnFailureListener {
                _isPhoneAllowed.value = false
            }
    }
}