package com.manjano.bus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manjano.bus.models.Country
import com.manjano.bus.models.CountryRepository
import com.manjano.bus.utils.Constants
import com.manjano.bus.utils.PhoneNumberUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log


// ---------------- SignInUiState ----------------
// Holds all UI state needed for the SignIn screen
data class SignInUiState(
    // Phone section
    val rawPhoneInput: String = "",
    val formattedPhone: String = "",
    val selectedCountry: Country = CountryRepository.getDefaultCountry(),
    val isPhoneValid: Boolean = false,
    val phoneValidationMessage: String? = null,
    val isPhoneValidationVisible: Boolean = false,

    // OTP section
    val otpDigits: List<String> = List(Constants.OTP_LENGTH) { "" },
    val isOtpComplete: Boolean = false,
    val isOtpSubmitting: Boolean = false,
    val otpErrorMessage: String? = null,
    val shouldShakeOtp: Boolean = false,

    // OTP request & resend
    val isSendingOtp: Boolean = false,
    val otpRequestSuccess: Boolean = false,
    val resendTimerSeconds: Int = 0,
    val canResendOtp: Boolean = true,

    // General state
    val rememberMe: Boolean = false,
    val sentOtp: String? = null, // For development/testing
    val navigateToDashboard: Boolean = false,
    val targetDashboardRoute: String? = null,

    // Legacy fields for backward compatibility
    val showError: Boolean = false,
    val showSmsMessage: Boolean = false,
    val showOtpError: Boolean = false,
    val isOtpIncorrect: Boolean = false,
    val generalValidationError: Boolean = false,
    val generalErrorMessage: String? = null
)

// ---------------- SignInViewModel ----------------
// Handles phone input, OTP request/verification, and navigation flow
class SignInViewModel : ViewModel() {

    private val isDebug = true  // ✅ temporary, for testing admin numbers
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState

    private var resendTimerJob: Job? = null
    private var autoSubmitJob: Job? = null

    // --- Country selection ---
    fun onCountrySelected(country: Country) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
        validateAndFormatPhoneNumber(_uiState.value.rawPhoneInput)
    }

    // --- Phone number input handling ---
    fun onPhoneNumberChange(rawInput: String) {
        _uiState.value = _uiState.value.copy(
            rawPhoneInput = rawInput,
            showError = false,
            isPhoneValidationVisible = false
        )


        // Keep formatted phone for display
        val formatted = PhoneNumberUtils.formatPhoneNumberAsYouType(
            rawDigits = rawInput,
            regionCode = _uiState.value.selectedCountry.isoCode
        )
        _uiState.value = _uiState.value.copy(formattedPhone = formatted)

// Normalize phone for validation & future use
        var normalized = rawInput.filter { it.isDigit() }
        if (normalized.startsWith("254")) {
            normalized = "0" + normalized.substring(3)
        }

// Validate the normalized number
        val isValid =
            PhoneNumberUtils.isPossibleNumber(normalized, _uiState.value.selectedCountry.isoCode)
        _uiState.value = _uiState.value.copy(isPhoneValid = isValid)

        _uiState.value = _uiState.value.copy(isPhoneValid = isValid)
    }

    fun onPhoneFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            validateAndFormatPhoneNumber(_uiState.value.rawPhoneInput)
        }
    }

    private fun validateAndFormatPhoneNumber(rawInput: String) {
        val countryCode = _uiState.value.selectedCountry.isoCode
        val isValid = PhoneNumberUtils.isValidNumber(rawInput, countryCode)
        val formatted = PhoneNumberUtils.formatForDisplay(rawInput, countryCode)

        val validationMessage = if (isValid) null else "Please Enter a Valid Phone Number"

        _uiState.value = _uiState.value.copy(
            formattedPhone = formatted,
            isPhoneValid = isValid,
            phoneValidationMessage = validationMessage,
            isPhoneValidationVisible = true,
            showError = !isValid
        )
    }
    private fun getTestRole(phone: String): String? {
        // Normalize phone: remove spaces and ensure full format
        val normalized = phone.replace(" ", "")
        return when (normalized) {
            "+254700123456" -> "school_admin"   // test admin
            "+254700222222" -> "super_admin"    // test super admin
            else -> null
        }
    }

    // --- OTP digit updates (manual entry or paste) ---
    fun updateOtpDigits(otp: String) {
        val digits = otp.filter { it.isDigit() }.take(Constants.OTP_LENGTH)
        val paddedDigits = List(Constants.OTP_LENGTH) { index ->
            digits.getOrNull(index)?.toString() ?: ""
        }

        _uiState.value = _uiState.value.copy(
            otpDigits = paddedDigits,
            isOtpComplete = digits.length == Constants.OTP_LENGTH
        )
    }

    // --- Request OTP (simulated, sets Constants.TEST_OTP for dev/testing) ---
    fun requestOtp() {
        if (_uiState.value.isSendingOtp) return

        _uiState.value = _uiState.value.copy(
            showError = false,
            isPhoneValidationVisible = false,
            generalValidationError = false,
            generalErrorMessage = null
        )

        validateAndFormatPhoneNumber(_uiState.value.rawPhoneInput)

        if (!_uiState.value.isPhoneValid) {
            _uiState.value = _uiState.value.copy(
                showError = true,
                isPhoneValidationVisible = true,
                generalValidationError = true
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSendingOtp = true)

        viewModelScope.launch {
            try {
                delay(1500)

                _uiState.value = _uiState.value.copy(
                    isSendingOtp = false,
                    otpRequestSuccess = true,
                    showSmsMessage = true,
                    resendTimerSeconds = 30,
                    canResendOtp = false,
                    sentOtp = Constants.TEST_OTP // ✅ dev-only
                )

                startResendTimer()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSendingOtp = false,
                    generalErrorMessage = "Failed to send OTP. Please try again."
                )
            }
        }
    }

    // --- Handle OTP input (digit by digit) ---
    fun onOtpDigitChange(
        index: Int,
        digit: String,
        onHideKeyboard: () -> Unit
    ) {
        val newOtpDigits = _uiState.value.otpDigits.toMutableList().apply {
            this[index] = digit.take(1)
        }

        val isComplete = newOtpDigits.all { it.isNotBlank() }

        _uiState.value = _uiState.value.copy(
            otpDigits = newOtpDigits,
            isOtpComplete = isComplete,
            otpErrorMessage = null,
            shouldShakeOtp = false,
            isOtpIncorrect = false,
            showOtpError = false
        )

        if (isComplete) {
            onHideKeyboard()
        }
    }

    // --- Handle OTP paste ---
    fun onOtpPaste(pastedText: String) {
        val digits = pastedText.filter { it.isDigit() }.take(Constants.OTP_LENGTH)
        val newOtpDigits = List(Constants.OTP_LENGTH) { index ->
            digits.getOrNull(index)?.toString() ?: ""
        }

        _uiState.value = _uiState.value.copy(
            otpDigits = newOtpDigits,
            isOtpComplete = digits.length == Constants.OTP_LENGTH,
            otpErrorMessage = null,
            shouldShakeOtp = false
        )

    }

    // --- Verify OTP (compares against Constants.TEST_OTP or sentOtp) ---
    fun verifyOtp() {
        val enteredOtp = _uiState.value.otpDigits.joinToString("")

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isOtpSubmitting = true,
                otpErrorMessage = null,
                shouldShakeOtp = false,
                showOtpError = false
            )

            if (enteredOtp == Constants.TEST_OTP || enteredOtp == _uiState.value.sentOtp) {
                _uiState.value = _uiState.value.copy(
                    isOtpSubmitting = false,
                    navigateToDashboard = true,
                    otpErrorMessage = null,
                    shouldShakeOtp = false,
                    isOtpIncorrect = false,
                    showOtpError = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isOtpSubmitting = false,
                    otpErrorMessage = null,
                    shouldShakeOtp = true,
                    isOtpIncorrect = true,
                    showOtpError = true
                )
            }
        }
    }

    // --- Resend OTP ---
    fun resendOtp() {
        if (!_uiState.value.canResendOtp) return

        resendTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            canResendOtp = false,
            otpDigits = List(Constants.OTP_LENGTH) { "" },
            isOtpComplete = false
        )

        startResendTimer()
        requestOtp()
    }

    fun setTargetDashboardRoute(route: String) {
        _uiState.value = _uiState.value.copy(targetDashboardRoute = route)
    }

    // --- Countdown timer for resend ---
    fun startResendTimer() {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            var seconds = _uiState.value.resendTimerSeconds
            while (seconds > 0) {
                delay(1000)
                seconds--
                _uiState.value = _uiState.value.copy(resendTimerSeconds = seconds)
            }
            _uiState.value = _uiState.value.copy(canResendOtp = true)
        }
    }

    // --- Utility state updaters ---
    fun onOtpShakeComplete() {
        _uiState.value = _uiState.value.copy(shouldShakeOtp = false)
    }

    fun onNavigationConsumed() {
        _uiState.value = _uiState.value.copy(navigateToDashboard = false)
    }

    override fun onCleared() {
        super.onCleared()
        resendTimerJob?.cancel()
        autoSubmitJob?.cancel()
    }

    fun onRememberMeChange(checked: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = checked)
    }

    fun onOtpVerified() {
        _uiState.value = _uiState.value.copy(
            navigateToDashboard = true,
            otpErrorMessage = null,
            shouldShakeOtp = false,
            showOtpError = false
        )
    }

    fun onOtpError(message: String) {
        _uiState.value = _uiState.value.copy(
            otpErrorMessage = message,
            shouldShakeOtp = true,
            showOtpError = true
        )
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = null,
                shouldShakeOtp = false,
                showOtpError = false
            )
        }
    }

    fun getAdminRoleByMobile(mobile: String, callback: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        // Step 1: Normalize input number
        val normalizedInput = mobile.filter { it.isDigit() }
            .let {
                when {
                    it.startsWith("07") -> it             // Already in 07XXXXXXXX
                    it.startsWith("254") -> "0" + it.substring(3)
                    it.startsWith("+254") -> "0" + it.substring(4)
                    else -> it
                }
            }

        Log.d("AdminFirestore", "Querying admins collection for normalized phone: $normalizedInput")

        // Step 2: Query Firestore
        db.collection("admins")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("AdminFirestore", "Firestore success! Found ${documents.size()} documents")

                // Step 3: Compare normalized DB numbers with normalized input
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
                Log.d("AdminFirestore", "Admin role found: $role")
                callback(role)
            }
            .addOnFailureListener { e ->
                Log.e("AdminFirestore", "Firestore query failed: ${e.message}", e)
                callback(null)
            }
    }
}