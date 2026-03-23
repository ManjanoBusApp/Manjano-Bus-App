package com.manjano.bus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manjano.bus.ui.ManjanoAppUI
import com.manjano.bus.ui.theme.ManjanoTheme
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private val _verificationEmail = MutableStateFlow<String?>(null)
        val verificationEmail = _verificationEmail.asStateFlow()

        private val _pendingVerification = MutableStateFlow(false)
        val pendingVerification = _pendingVerification.asStateFlow()

        // 🔥 NEW: Track if we should go to signup screen
        private val _navigateToSignup = MutableStateFlow(false)
        val navigateToSignup = _navigateToSignup.asStateFlow()

        fun setVerificationEmail(email: String) {
            _verificationEmail.value = email
            _pendingVerification.value = true
            _navigateToSignup.value = true  // 🔥 Trigger navigation to signup screen
            Log.d("🔥", "✅ Verification email set: $email")
        }

        fun clearVerification() {
            _verificationEmail.value = null
            _pendingVerification.value = false
            _navigateToSignup.value = false
        }

        fun hasPendingVerification(): Boolean = _pendingVerification.value
        fun shouldNavigateToSignup(): Boolean = _navigateToSignup.value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("🔥", "🔧 MainActivity onCreate called")

        // Handle deep link when app is opened from email
        intent?.data?.let { uri ->
            handleDeepLink(uri)
        }

        enableEdgeToEdge()
        setContent {
            ManjanoTheme {
                ManjanoAppUI(
                    startAtSignup = shouldNavigateToSignup()  // 🔥 Pass this to your navigation
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("🔥", "🔧 MainActivity onNewIntent called")

        // Handle deep link when app is already running
        intent.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    private fun handleDeepLink(uri: Uri) {
        Log.d("🔥", "🔧 Deep link received: $uri")
        Log.d("🔥", "🔧 Scheme: ${uri.scheme}")
        Log.d("🔥", "🔧 Host: ${uri.host}")

        // Check for manjanoapp:// scheme
        if (uri.scheme == "manjanoapp" && uri.host == "verify") {
            val email = uri.getQueryParameter("email")
            Log.d("🔥", "🔧 Extracted email from deep link: $email")
            if (email != null && email.isNotEmpty()) {
                setVerificationEmail(email)
            }
        }
        // Check for https:// scheme (web link)
        else if (uri.scheme == "https" && uri.host == "manjano-app.web.app" && uri.path == "/verify") {
            val email = uri.getQueryParameter("email")
            Log.d("🔥", "🔧 Extracted email from https link: $email")
            if (email != null && email.isNotEmpty()) {
                setVerificationEmail(email)
            }
        } else {
            Log.d("🔥", "Not a verification deep link, ignoring")
        }
    }
}