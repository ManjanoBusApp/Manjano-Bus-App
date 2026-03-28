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

    private val firestore = FirebaseFirestore.getInstance()  // ← ADD THIS LINE

    private var userRole: String? = null
    private var isTimerCancelled = false

    fun setUserRole(role: String) {
        userRole = role.lowercase()
    }

    // ============ Phone number existence check for sign-in ============
    private val _isPhoneAllowed = MutableStateFlow<Boolean?>(null) // null = not checked yet
    val isPhoneAllowed: StateFlow<Boolean?> = _isPhoneAllowed

    // NEW: Distinguish "saved by admin" vs "actually signed up"
    private val _isPreRegistered = MutableStateFlow<Boolean?>(null)  // exists in collection
    val isPreRegistered: StateFlow<Boolean?> = _isPreRegistered

    private val _isSignedUp = MutableStateFlow<Boolean?>(null)       // has name / children
    val isSignedUp: StateFlow<Boolean?> = _isSignedUp

    private var shouldStopTimer = false

    fun resetOtpSendingState() {
        Log.d("OTP_DEBUG", "resetOtpSendingState called - stopping timer")
        shouldStopTimer = true
        isTimerCancelled = true  // ← ADD THIS - mark timer as cancelled

        // Cancel any running resend timer
        resendTimerJob?.cancel()

        _uiState.value = _uiState.value.copy(
            isSendingOtp = false,
            canResendOtp = true,
            resendTimerSeconds = 0
        )
    }

    fun getAdminByMobile(mobileNumber: String, onResult: (Map<String, Any?>?) -> Unit) {
        firestore.collection("admins")
            .document(mobileNumber)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    onResult(document.data)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
    fun checkPhoneNumberInFirestore(phone: String, countryIso: String) {
        val db = FirebaseFirestore.getInstance()

        val normalizedInput = normalizePhoneNumber(phone, countryIso)


        val collectionName = when (userRole?.lowercase()) {
            "driver" -> "drivers"
            else -> "parents"  // parent or null fallback
        }

        db.collection(collectionName)
            .whereEqualTo("mobileNumber", normalizedInput)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                val matchingDoc = documents.documents.firstOrNull()
                if (matchingDoc != null) {
                    _isPreRegistered.value = true

                    // 🔥 NEW: Check if account is active
                    val isActive = matchingDoc.getBoolean("active") ?: true  // Default to true if field doesn't exist

                    if (!isActive) {
                        Log.d("SignInCheck", "Account is disabled for: $normalizedInput")
                        _isPhoneAllowed.value = false
                        _isPreRegistered.value = false
                        _isSignedUp.value = false
                        // Show disabled account message
                        _uiState.value = _uiState.value.copy(
                            showError = true,
                            phoneValidationMessage = "Your account has been deactivated. Please contact the school.",
                            isPhoneValidationVisible = true
                        )
                        return@addOnSuccessListener
                    }

                    // Check if profile is completed (signed up)
                    val nameField = if (collectionName == "drivers") "name" else "parentName"
                    val hasName = !matchingDoc.getString(nameField).isNullOrBlank()

                    val hasChild = if (collectionName == "parents") {
                        matchingDoc.data?.keys?.any { it.startsWith("childName") } == true
                    } else false

                    _isSignedUp.value = hasName || hasChild

                    Log.d(
                        "SignInCheck",
                        "$collectionName match found - pre-registered: true, signed-up: ${_isSignedUp.value}, active: $isActive"
                    )
                } else {
                    _isPreRegistered.value = false
                    _isSignedUp.value = false
                    Log.d("SignInCheck", "No match in $collectionName")
                }

                _isPhoneAllowed.value = when {
                    _isPreRegistered.value == false -> false // Can't proceed, contact admin
                    _isSignedUp.value == false -> false       // Signed up not completed
                    _isPreRegistered.value == true && _isSignedUp.value == true -> true
                    else -> false
                }
            }
            .addOnFailureListener { e ->
                Log.e("SignInCheck", "Firestore query failed: ${e.message}", e)
                _isPhoneAllowed.value = false
                _isPreRegistered.value = false
                _isSignedUp.value = false
            }
    }
    fun resetPhoneAllowed() {
        _isPhoneAllowed.value = null
    }

    fun resetPreRegistered() {
        _isPreRegistered.value = null
    }

    fun resetSignedUp() {
        _isSignedUp.value = null
    }

    fun setOtpValid(isValid: Boolean) {
        if (isValid) {
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = null,
                shouldShakeOtp = false,
                isOtpIncorrect = false,
                showOtpError = false
            )
        } else {
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = "Incorrect code. Send code again.",
                shouldShakeOtp = true,
                isOtpIncorrect = true,
                showOtpError = true
            )
        }
    }
    // Reuse or copy the normalizePhoneNumber function from SignUpViewModel
    fun normalizePhoneNumber(rawNumber: String, countryIso: String): String {
        val digitsOnly = rawNumber.filter { it.isDigit() }
        val result = if (countryIso.equals("KE", ignoreCase = true)) {
            when {
                digitsOnly.length == 9 -> "0$digitsOnly"
                digitsOnly.length == 10 -> digitsOnly
                digitsOnly.startsWith("254") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(
                    3
                )

                digitsOnly.startsWith("+254") && digitsOnly.length == 13 -> "0" + digitsOnly.substring(
                    4
                )

                else -> digitsOnly  // fallback to digits only
            }
        } else {
            val countryCode = CountryRepository.countries
                .find { it.isoCode.equals(countryIso, ignoreCase = true) }?.callingCode ?: ""
            when {
                rawNumber.startsWith("+") -> rawNumber
                digitsOnly.startsWith("0") -> "+$countryCode" + digitsOnly.drop(1)
                else -> "+$countryCode$digitsOnly"
            }
        }

        Log.d("PhoneNormalize", "Input: $rawNumber ($countryIso) → Normalized: $result")
        return result
    }
    private var resendTimerJob: Job? = null
    private var autoSubmitJob: Job? = null

    // --- Country selection ---
    fun onCountrySelected(country: Country) {
        _uiState.value = _uiState.value.copy(selectedCountry = country)
        validateAndFormatPhoneNumber(_uiState.value.rawPhoneInput)
    }

    // --- Phone number input handling ---
    // --- Unified phone validation + Firestore check ---
    fun onPhoneNumberChange(rawInput: String) {
        _uiState.value = _uiState.value.copy(
            rawPhoneInput = rawInput,
            showError = false,
            phoneValidationMessage = null,
            isPhoneValidationVisible = false
        )

        val countryCode = _uiState.value.selectedCountry.isoCode
        val normalized = normalizePhoneNumber(rawInput, countryCode)

        // Validate length / general validity
        val isValidNumber = PhoneNumberUtils.isPossibleNumber(normalized, countryCode)
        _uiState.value = _uiState.value.copy(
            formattedPhone = PhoneNumberUtils.formatForDisplay(normalized, countryCode),
            isPhoneValid = isValidNumber,
            phoneValidationMessage = if (isValidNumber) null else "Invalid phone number",
            isPhoneValidationVisible = true,
            showError = !isValidNumber
        )

        // Reset Firestore checks if number changes
        resetPreRegistered()
        resetSignedUp()
        resetPhoneAllowed()
    }

    fun onPhoneFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            // Revalidate to show/hide invalid number message
            validateAndFormatPhoneNumber(_uiState.value.rawPhoneInput)
            // Clear Firestore-specific error messages on refocus
            _uiState.value = _uiState.value.copy(
                phoneValidationMessage = null,
                showError = false
            )
        }
    }

    private fun validateAndFormatPhoneNumber(rawInput: String) {
        val countryCode = _uiState.value.selectedCountry.isoCode
        val normalized = normalizePhoneNumber(rawInput, countryCode)
        val isValidNumber = PhoneNumberUtils.isPossibleNumber(normalized, countryCode)
        val formatted = PhoneNumberUtils.formatForDisplay(normalized, countryCode)
        _uiState.value = _uiState.value.copy(
            formattedPhone = formatted,
            isPhoneValid = isValidNumber,
            phoneValidationMessage = if (isValidNumber) null else "Invalid phone number",
            isPhoneValidationVisible = true,
            showError = !isValidNumber
        )
    }

    // --- Request OTP (updated for single tap + proper messages) ---
    fun requestOtp() {
        // Reset the stop flag when requesting new OTP
        shouldStopTimer = false
        isTimerCancelled = false
        if (_uiState.value.isSendingOtp) return

        _uiState.value = _uiState.value.copy(
            showError = false,
            phoneValidationMessage = null,
            generalErrorMessage = null,
            generalValidationError = false
        )

        val rawInput = _uiState.value.rawPhoneInput
        val countryIso = _uiState.value.selectedCountry.isoCode

        // Strict local validation first (this is the key change)
        val isValid = PhoneNumberUtils.isValidNumber(rawInput, countryIso)

        if (!isValid) {
            _uiState.value = _uiState.value.copy(
                showError = true,
                phoneValidationMessage = "Invalid phone number",
                isPhoneValidationVisible = true,
                generalValidationError = true
            )

            resetPreRegistered()
            resetSignedUp()
            resetPhoneAllowed()

            return
        }

        // Only if strictly valid → proceed to Firestore check
        viewModelScope.launch {
            checkPhoneNumberInFirestore(
                phone = rawInput,
                countryIso = countryIso
            )

            // Wait for result
            while (_isPhoneAllowed.value == null) {
                delay(50)
            }

            // Decide based on Firestore
            when {
                _isPreRegistered.value == false -> {
                    _uiState.value = _uiState.value.copy(
                        showError = true,
                        phoneValidationMessage = "Can't proceed, contact the school",
                        isPhoneValidationVisible = true
                    )
                }

                _isSignedUp.value == false -> {
                    _uiState.value = _uiState.value.copy(
                        showError = true,
                        phoneValidationMessage = "You have no account, please sign-up first",
                        isPhoneValidationVisible = true
                    )
                }

                else -> {
                    // Valid + exists + signed up → send OTP
                    _uiState.value = _uiState.value.copy(
                        showError = false,
                        phoneValidationMessage = null,
                        isPhoneValidationVisible = false,
                        isSendingOtp = true
                    )

                    try {
                        delay(1500) // simulate send

                        // Only update if not stopped by user typing
                        if (!shouldStopTimer) {
                            _uiState.value = _uiState.value.copy(
                                isSendingOtp = false,
                                otpRequestSuccess = true,
                                showSmsMessage = true,
                                resendTimerSeconds = 30,
                                canResendOtp = false,
                                sentOtp = Constants.TEST_OTP // dev only
                            )

                            startResendTimer()
                        } else {
                            // If stopped, just reset the sending state
                            _uiState.value = _uiState.value.copy(
                                isSendingOtp = false
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isSendingOtp = false,
                            generalErrorMessage = "Failed to send OTP. Please try again."
                        )
                    }
                }
            }
        }
    }
    fun hideSmsMessage() {
        _uiState.value = _uiState.value.copy(showSmsMessage = false)
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
        isTimerCancelled = false  // ← ADD THIS - reset when starting new timer
        resendTimerJob = viewModelScope.launch {
            var seconds = _uiState.value.resendTimerSeconds
            while (seconds > 0 && !shouldStopTimer && !isTimerCancelled) {
                delay(1000)
                seconds--
                _uiState.value = _uiState.value.copy(resendTimerSeconds = seconds)
            }
            // Only update if not cancelled by user typing
            if (!shouldStopTimer && !isTimerCancelled) {
                _uiState.value = _uiState.value.copy(canResendOtp = true)
            }
            // Reset flag for next OTP request - but only if not cancelled
            if (!isTimerCancelled) {
                shouldStopTimer = false
            }
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