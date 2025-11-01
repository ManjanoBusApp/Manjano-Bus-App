package com.manjano.bus.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PhoneAuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _verificationFailed = MutableStateFlow<String?>(null)
    val verificationFailed: StateFlow<String?> = _verificationFailed

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    fun startPhoneNumberVerification(phoneNumber: String, activity: Activity) {
        _isLoading.value = true

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOTP(otp: String, onSuccess: () -> Unit) {
        val id = verificationId
        if (id == null) {
            _verificationFailed.value = "Verification ID is missing. Please retry."
            return
        }

        _isLoading.value = true
        val credential = PhoneAuthProvider.getCredential(id, otp)

        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    _verificationFailed.value = task.exception?.message ?: "Verification failed"
                }
            }
    }

    fun resendCode(phoneNumber: String, activity: Activity) {
        _isLoading.value = true

        val token = resendToken
        if (token == null) {
            _verificationFailed.value = "Resend token is missing. Try again later."
            _isLoading.value = false
            return
        }

        val options = PhoneAuthOptions.newBuilder(firebaseAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .setForceResendingToken(token)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto verification (rarely happens)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _isLoading.value = false
            _verificationFailed.value = e.localizedMessage ?: "Verification failed"
        }

        override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
            _isLoading.value = false
            verificationId = vid
            resendToken = token
        }
    }
}
