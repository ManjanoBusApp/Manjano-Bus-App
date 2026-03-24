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
import kotlinx.coroutines.flow.asStateFlow

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

    private val _driverFirstName = MutableStateFlow("")
    val driverFirstName: StateFlow<String> = _driverFirstName

    private val _alreadyRegisteredError = MutableStateFlow<String?>(null)
    val alreadyRegisteredError: StateFlow<String?> = _alreadyRegisteredError.asStateFlow()

    private var shouldStopTimer = false
    fun setAlreadyRegisteredError(message: String?) {
        _alreadyRegisteredError.value = message
    }
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

    fun resetOtpSendingState() {
        Log.d("OTP_DEBUG", "resetOtpSendingState called - stopping timer")
        shouldStopTimer = true

        _uiState.value = _uiState.value.copy(
            isSendingOtp = false,
            canResendOtp = true,
            resendTimerSeconds = 0
        )
    }
    fun requestOtp() {
        // Reset the stop flag when requesting new OTP
        shouldStopTimer = false

        _uiState.value = _uiState.value.copy(
            isSendingOtp = true,
            resendTimerSeconds = resendDuration,
            canResendOtp = false
        )
        viewModelScope.launch {
            delay(2000)
            // Only continue if not stopped by user typing
            if (!shouldStopTimer) {
                _uiState.value = _uiState.value.copy(isSendingOtp = false)
                // Only start the timer if still not stopped
                if (!shouldStopTimer) {
                    startResendTimer()
                }
            }
        }
    }

    private fun startResendTimer() {
        viewModelScope.launch {
            var seconds = resendDuration
            while (seconds > 0 && !shouldStopTimer) {
                _uiState.value = _uiState.value.copy(resendTimerSeconds = seconds)
                delay(1000)
                seconds--
            }
            if (!shouldStopTimer) {
                _uiState.value = _uiState.value.copy(
                    canResendOtp = true,
                    resendTimerSeconds = 0
                )
            }
            // Reset flag for next OTP request
            shouldStopTimer = false
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

        // Step 1: Normalize user input to our standard format
        val normalizedInput = normalizeForMatching(phone, countryIso)

        if (normalizedInput.isBlank()) {
            _isPhoneAllowed.value = false
            setAlreadyRegisteredError(null)
            return
        }

        db.collection("parents")
            .get()
            .addOnSuccessListener { documents ->
                var found = false

                for (doc in documents.documents) {
                    val storedNumber = doc.getString("mobileNumber") ?: continue
                    val normalizedStored = normalizeForMatching(storedNumber, countryIso)

                    if (normalizedStored == normalizedInput) {
                        found = true

                        // Optional: still show "already signed up" if profile looks complete
                        val parentName = doc.getString("parentName") ?: ""
                        val hasChild = doc.data?.keys?.any { it.startsWith("childName") } == true

                        if (parentName.isNotBlank() || hasChild) {
                            setAlreadyRegisteredError("You’re registered, please Sign-in")
                        } else {
                            setAlreadyRegisteredError(null)
                        }
                        break
                    }
                }

                _isPhoneAllowed.value = found
                if (!found) {
                    setAlreadyRegisteredError(null)
                }
            }
            .addOnFailureListener {
                _isPhoneAllowed.value = false
                setAlreadyRegisteredError(null)
            }
    }

    private fun normalizeForMatching(raw: String, countryIso: String): String {
        val digits = raw.filter { it.isDigit() }

        return if (countryIso.equals("KE", ignoreCase = true)) {
            when {
                digits.length == 9 -> "0$digits"                  // 712345678 → 0712345678
                digits.length == 10 -> {
                    if (digits.startsWith("0")) digits           // 0712345678 → 0712345678
                    else "0$digits"                               // 7123456789 → 07123456789 (rare)
                }

                digits.startsWith("254") -> {
                    val rest = digits.drop(3)
                    if (rest.startsWith("0")) rest else "0$rest"  // 254712345678 → 0712345678
                }

                else -> digits                                    // fallback – unlikely
            }
        } else {
            // Non-Kenyan: full E.164 with +
            val countryCode = CountryRepository.countries
                .find { it.isoCode.equals(countryIso, ignoreCase = true) }?.callingCode ?: ""

            when {
                raw.startsWith("+") -> raw
                digits.startsWith("00") -> "+" + digits.drop(2)
                digits.startsWith("0") -> "+$countryCode${digits.drop(1)}"
                else -> "+$countryCode$digits"
            }
        }
    }
    fun saveDriverProfileIfNeeded(
        phoneNumber: String,
        fullName: String,
        nationalId: String,
        schoolName: String,
        countryIso: String
    ) {
        val normalizedPhone = normalizePhoneNumber(phoneNumber, countryIso)

        firestore.collection("drivers")
            .get()
            .addOnSuccessListener { snapshot ->

                val matchingDoc = snapshot.documents.firstOrNull { doc ->
                    val dbNumber = doc.getString("mobileNumber")?.filter { it.isDigit() } ?: ""
                    val inputNumber = normalizedPhone.filter { it.isDigit() }
                    dbNumber == inputNumber
                }

                if (matchingDoc != null) {

                    val existingName = matchingDoc.getString("name")

                    // Only write if the profile was not filled before
                    if (existingName.isNullOrBlank()) {

                        val now = java.util.Calendar.getInstance().time
                        val dateFormatter = java.text.SimpleDateFormat(
                            "dd MMMM yyyy",
                            java.util.Locale.getDefault()
                        )
                        val timeFormatter =
                            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                        val createdAtDate = dateFormatter.format(now)
                        val createdAtTime = timeFormatter.format(now)

                        firestore.collection("drivers")
                            .document(matchingDoc.id)
                            .update(
                                mapOf(
                                    "name" to fullName,
                                    "idNumber" to nationalId,
                                    "schoolName" to schoolName,
                                    "createdOn" to createdAtDate,
                                    "createdTime" to createdAtTime
                                )
                            )
                            .addOnSuccessListener {
                                Log.d("🔥", "Driver profile saved successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("🔥", "Failed to save driver profile", e)
                            }

                    } else {
                        Log.d("🔥", "Driver profile already exists, skipping write")
                    }

                } else {
                    Log.e("🔥", "No matching driver document found for phone")
                }

            }
            .addOnFailureListener { e ->
                Log.e("🔥", "Firestore query failed", e)
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
                val matchingDoc = snapshot.documents.firstOrNull { doc ->
                    val dbNumber = doc.getString("mobileNumber")?.filter { it.isDigit() } ?: ""
                    val inputNumber = normalizedInput.filter { it.isDigit() }
                    dbNumber == inputNumber
                }

                if (matchingDoc != null) {
                    _isPhoneAllowed.value = true  // phone exists in Firestore

                    // Only show "You're registered..." if driver has signed up before
                    val hasSignedUp = matchingDoc.getBoolean("hasSignedUp") ?: false
                    val hasName = !matchingDoc.getString("name").isNullOrBlank()

                    if (hasSignedUp || hasName) {
                        setAlreadyRegisteredError("You’re registered, please Sign-in")
                    } else {
                        // First-time signup → no error
                        setAlreadyRegisteredError(null)
                    }

                } else {
                    // Phone not found → block Send Code
                    _isPhoneAllowed.value = false
                    setAlreadyRegisteredError(null)
                }
            }
            .addOnFailureListener {
                _isPhoneAllowed.value = false
                setAlreadyRegisteredError(null)
            }
    }
}