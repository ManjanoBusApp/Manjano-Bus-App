package com.manjano.bus.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.delay

@HiltViewModel
class OTPVerificationViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    // --- New state for OTP digits ---
    private val _otpDigits = MutableStateFlow("")
    val otpDigits: StateFlow<String> = _otpDigits

    // --- Function to update OTP digits ---
    fun updateOtpDigits(otp: String) {
        _otpDigits.value = otp
    }

    private val _verificationId = MutableStateFlow<String?>(null)
    val verificationId: StateFlow<String?> = _verificationId

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isVerified = MutableStateFlow(false)
    val isVerified: StateFlow<Boolean> = _isVerified

    // New flags for UI
    private val _isOtpRequested = MutableStateFlow(false)
    val isOtpRequested: StateFlow<Boolean> = _isOtpRequested

    private val _isOtpInvalid = MutableStateFlow(false)
    val isOtpInvalid: StateFlow<Boolean> = _isOtpInvalid

    fun sendOTP(phoneNumber: String, activity: Activity) {
        _isOtpRequested.value = true
        _isVerifying.value = true

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    _isVerifying.value = false
                    _errorMessage.value = "Verification failed: ${e.message}"
                    Log.e("OTP", "Verification failed", e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    _verificationId.value = verificationId
                    _isVerifying.value = false
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp() {
        val otpValue = _otpDigits.value

        _isOtpInvalid.value = false // clear old error
        if (otpValue == "1234") {
            _isVerified.value = true
        } else {
            _isOtpInvalid.value = true
            viewModelScope.launch {
                delay(500)
                _isOtpInvalid.value = false
            }
        }
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        _isVerifying.value = true
        viewModelScope.launch {
            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    _isVerifying.value = false
                    if (task.isSuccessful) {
                        _isVerified.value = true
                    } else {
                        _errorMessage.value = task.exception?.message ?: "OTP verification failed"
                        Log.e("OTP", "Sign in failed", task.exception)
                        _isOtpInvalid.value = true
                        // reset after short delay
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(500)
                            _isOtpInvalid.value = false
                        }
                    }
                }
        }
    }
}
