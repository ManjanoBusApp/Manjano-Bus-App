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

        val formatted = PhoneNumberUtils.formatPhoneNumberAsYouType(
            rawDigits = rawInput,
            regionCode = _uiState.value.selectedCountry.isoCode
        )

        _uiState.value = _uiState.value.copy(formattedPhone = formatted)

        val isValid = PhoneNumberUtils.isPossibleNumber(
            rawInput,
            _uiState.value.selectedCountry.isoCode
        )

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
}
