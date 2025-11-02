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
import com.manjano.bus.utils.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


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
    val childrenNames: String = ""
)

class SignUpViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> get() = _uiState
    private val _isOtpValid = MutableStateFlow(false)
    val isOtpValid: StateFlow<Boolean> get() = _isOtpValid

    private val resendDuration = 30 // seconds
    private val database = FirebaseDatabase.getInstance().reference

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
        Log.d("🔥", "🔍 Starting Firebase connection test...")
        val testRef = database.child("test").child("connection_check")
        val testData =
            mapOf("test" to "connection_check", "timestamp" to System.currentTimeMillis())

        Log.d("🔥", "🔍 Attempting test write to 'test/connection_check'...")
        testRef.setValue(testData)
            .addOnSuccessListener {
                Log.d("🔥", "✅ Test write to 'test/connection_check' succeeded")
                _uiState.value = _uiState.value.copy(
                    otpErrorMessage = "Firebase connection test succeeded!",
                    showOtpError = true
                )
            }
            .addOnFailureListener { exception ->
                Log.e(
                    "🔥",
                    "❌ Test write to 'test/connection_check' failed: ${exception.message}",
                    exception
                )
                _uiState.value = _uiState.value.copy(
                    otpErrorMessage = "Firebase test failed: ${exception.message}",
                    showOtpError = true
                )
            }
            .addOnCanceledListener {
                Log.e("🔥", "❌ Test write to 'test/connection_check' was canceled")
                _uiState.value = _uiState.value.copy(
                    otpErrorMessage = "Firebase test was canceled",
                    showOtpError = true
                )
            }
    }

    fun setOtpValid(valid: Boolean) {
        _isOtpValid.value = valid
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

    fun onRememberMeChange(checked: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = checked)
    }

    fun saveUserNames(parentName: String, childrenNames: String, context: Context) {
        Log.d("🔥", "saveUserNames() called with parent: $parentName, children: $childrenNames")

        _uiState.value = _uiState.value.copy(
            parentName = parentName,
            childrenNames = childrenNames
        )

        if (!isNetworkAvailable(context)) {
            Log.e("🔥", "❌ No network connection available")
            _uiState.value = _uiState.value.copy(
                otpErrorMessage = "No internet connection. Please check your network.",
                showOtpError = true
            )
            return
        }

        Log.d("🔥", "🔍 Running Firebase connection test before saving...")
        testFirebaseConnection()

        // Helper to normalize names for matching
        fun normalizeName(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9]"), "")

        // ✅ New: List all image files from the "Children Images" folder in Firebase Storage
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
            .reference
            .child("Children Images") // 👈 point to correct folder where images are stored

        val imageBaseNames = mutableMapOf<String, String>()  // normalized name -> full file name

        storage.listAll()
            .addOnSuccessListener { listResult ->
                Log.d("🔥", "✅ Listed ${listResult.items.size} files from Storage")

                listResult.items.forEach { item ->
                    val fullName = item.name
                    val baseName = fullName.substringBeforeLast('.')
                    val normalizedBase = normalizeName(baseName)
                    imageBaseNames[normalizedBase] = fullName
                    Log.d("🔥", "📸 Found: $fullName → normalized=$normalizedBase")
                }

                val childrenList = childrenNames.split(",").map { it.trim() }

                childrenList.forEach { childName ->
                    if (childName.isNotEmpty()) {
                        val childKey = childName.lowercase().replace(Regex("[^a-z0-9]"), "_")
                        val sanitizedChildName = normalizeName(childName)

                        // Simple Levenshtein fuzzy matching
                        fun levenshtein(lhs: String, rhs: String): Int {
                            val dp = Array(lhs.length + 1) { IntArray(rhs.length + 1) }
                            for (i in 0..lhs.length) dp[i][0] = i
                            for (j in 0..rhs.length) dp[0][j] = j
                            for (i in 1..lhs.length) {
                                for (j in 1..rhs.length) {
                                    val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                                    dp[i][j] = minOf(
                                        dp[i - 1][j] + 1,
                                        dp[i][j - 1] + 1,
                                        dp[i - 1][j - 1] + cost
                                    )
                                }
                            }
                            return dp[lhs.length][rhs.length]
                        }

                        val bestMatch = imageBaseNames.keys.minByOrNull { levenshtein(it, sanitizedChildName) }

                        val chosenBase = imageBaseNames.keys.find { key ->
                            key.contains(sanitizedChildName, ignoreCase = true) ||
                                    sanitizedChildName.contains(key, ignoreCase = true)
                        } ?: bestMatch

                        val finalFileName = if (chosenBase != null) imageBaseNames[chosenBase] else "default_child.jpg"

                        val imageRef = storage.child(finalFileName!!)

                        imageRef.downloadUrl.addOnSuccessListener { uri ->
                            val photoUrl = uri.toString()
                            val childRef = com.google.firebase.database.FirebaseDatabase
                                .getInstance()
                                .getReference("children")
                                .child(childKey)

                            val childData = mapOf(
                                "eta" to "Arriving in 5 minutes",
                                "active" to true,
                                "displayName" to childName,
                                "photoUrl" to photoUrl
                            )

                            childRef.setValue(childData)
                                .addOnSuccessListener {
                                    Log.d("🔥", "✅ Saved '$childKey' with image ${imageRef.name}")
                                }
                                .addOnFailureListener { e ->
                                    Log.e("🔥", "❌ Failed to save '$childKey': ${e.message}", e)
                                }
                        }.addOnFailureListener {
                            Log.e("🔥", "❌ Failed to get image URL for $childName: ${it.message}")
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("🔥", "❌ Failed to list Storage files: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    otpErrorMessage = "Failed to list images: ${e.message}",
                    showOtpError = true
                )
            }
    }
}
