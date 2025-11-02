package com.manjano.bus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.manjano.bus.utils.Constants
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import android.util.Log
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

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

        val childrenList = childrenNames.split(",").map { it.trim() }

        childrenList.forEach { childName ->
            if (childName.isNotEmpty()) {
                val childKey = childName.lowercase().replace(Regex("[^a-z0-9]"), "_")
                Log.d("🔥", "🔍 Checking if child '$childKey' exists in Firebase...")
                val childRef = database.child("children").child(childKey)

                childRef.get().addOnSuccessListener { snapshot ->
                    val exists = snapshot.exists()
                    Log.d("🔥", "✅ Successfully checked child '$childKey': exists = $exists")
                    if (!exists) {

                        // Aggressive normalization and improved image mapping
                        val storageBaseUrl = "https://firebasestorage.googleapis.com/v0/b/YOUR_PROJECT_ID.appspot.com/o/"
                        val defaultImageUrl = "$storageBaseUrl/default_child.jpg?alt=media"

                        // Function to aggressively normalize names
                        fun normalizeName(name: String): String =
                            name.lowercase().replace(Regex("[^a-z0-9]"), "")

// ✅ Explicit alias mapping for tricky names (no extensions)
                        val explicitAliases = mapOf(
                            "umtonikyra" to "kayraumutoni",
                            "kayrankongoliu" to "kayraumutoni",
                            "doemari" to "marydoe2",
                            "galinana" to "nanagally",
                            "pikmotofue" to "motopiki"
                        )

// ✅ Base image map (no extensions)
                        val imageMap = mapOf(
                            "naceneza" to "nezanace",
                            "kayraumuton" to "kayraumuton",
                            "doemari" to "marydoe2",
                            "galinana" to "nanagally",
                            "motopiki" to "motopiki"
                        )


// Normalize the child name
                        val normalizedChild = normalizeName(childName)

// Step 1: Check explicit aliases first
                        val aliasMatch = explicitAliases.entries.firstOrNull {
                            normalizeName(it.key) == normalizedChild
                        }?.value

                        // Step 2: Fuzzy match with Levenshtein if no alias found
                        fun levenshtein(lhs: String, rhs: String): Int {
                            val lhsLen = lhs.length
                            val rhsLen = rhs.length
                            val dp = Array(lhsLen + 1) { IntArray(rhsLen + 1) }
                            for (i in 0..lhsLen) dp[i][0] = i
                            for (j in 0..rhsLen) dp[0][j] = j
                            for (i in 1..lhsLen) {
                                for (j in 1..rhsLen) {
                                    dp[i][j] = if (lhs[i - 1] == rhs[j - 1]) dp[i - 1][j - 1]
                                    else minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + 1)
                                }
                            }
                            return dp[lhsLen][rhsLen]
                        }

                        fun closestMatch(input: String, options: List<String>, threshold: Int = 6): String? {
                            var bestMatch: String? = null
                            var bestDistance = threshold + 1
                            for (option in options) {
                                val distance = levenshtein(input, option)
                                if (distance < bestDistance) {
                                    bestDistance = distance
                                    bestMatch = option
                                }
                            }
                            return bestMatch
                        }

// Determine final mapped key with more forgiving matching
                        val mappedKey = aliasMatch ?: run {
                            val closest = closestMatch(normalizedChild, imageMap.keys.toList(), threshold = 8)
                            // Also allow partial contains matches both ways
                            val partial = imageMap.keys.firstOrNull { key ->
                                normalizedChild.contains(key) || key.contains(normalizedChild)
                            }
                            partial ?: closest
                        }

// Build final image URL
                        val baseImageName = mappedKey?.let { imageMap[it] ?: it }
                        val finalImageUrl = baseImageName?.let {
                            "$storageBaseUrl$it.jpg?alt=media"
                        } ?: defaultImageUrl



// Write child data to Firebase
                        val childData = mapOf(
                            "eta" to "Arriving in 5 minutes",
                            "active" to true,
                            "displayName" to childName,
                            "photoUrl" to finalImageUrl
                        )

                        childRef.setValue(childData)
                            .addOnSuccessListener {
                                Log.d("🔥", "✅ Successfully wrote child '$childKey' to Firebase")
                                _uiState.value = _uiState.value.copy(
                                    otpErrorMessage = "Child $childName saved successfully!",
                                    showOtpError = true
                                )
                                // ✅ Auto-detect image file extension (.jpg, .jpeg, .png)
                                val storage = com.google.firebase.storage.FirebaseStorage.getInstance().reference

                                val possibleExtensions = listOf("jpg", "jpeg", "png")
                                var foundUrl: String? = null

                                fun checkNextExtension(index: Int = 0) {
                                    if (index >= possibleExtensions.size) {
                                        // None found, keep default/fallback URL
                                        Log.d("🔥", "⚠️ No image found for '$childKey', keeping finalImageUrl")
                                        childRef.child("photoUrl").setValue(finalImageUrl)
                                        return
                                    }

                                    val ext = possibleExtensions[index]
                                    val imageRef = storage.child("${normalizedChild}.$ext")

                                    imageRef.metadata.addOnSuccessListener {
                                        // ✅ Found valid image file — build and save the correct URL
                                        foundUrl =
                                            "https://firebasestorage.googleapis.com/v0/b/YOUR_PROJECT_ID.appspot.com/o/${normalizedChild}.$ext?alt=media"
                                        Log.d("🔥", "✅ Found image for '$childKey': $foundUrl")
                                        childRef.child("photoUrl").setValue(foundUrl)
                                    }.addOnFailureListener {
                                        // Try the next extension
                                        checkNextExtension(index + 1)
                                    }
                                }

// Start recursive search
                                checkNextExtension()

                            }

                            .addOnFailureListener { e ->
                                Log.e("🔥", "❌ Failed to write child '$childKey': ${e.message}", e)
                                _uiState.value = _uiState.value.copy(
                                    otpErrorMessage = "Failed to save child '$childName': ${e.message}",
                                    showOtpError = true
                                )
                            }

                    } else {
                        Log.d("🔥", "⚠️ Child '$childKey' already exists")
                    }
                }.addOnFailureListener { exception ->
                    Log.e(
                        "🔥",
                        "❌ Failed to check child '$childKey': ${exception.message}",
                        exception
                    )
                    _uiState.value = _uiState.value.copy(
                        otpErrorMessage = "Failed to check child '$childName': ${exception.message}",
                        showOtpError = true
                    )
                }.addOnCanceledListener {
                    Log.e("🔥", "❌ Check for child '$childKey' was canceled")
                    _uiState.value = _uiState.value.copy(
                        otpErrorMessage = "Check for child '$childName' was canceled",
                        showOtpError = true
                    )
                }
            }
        }
    }
}