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
import android.content.Context



@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private val _verificationEmail = MutableStateFlow<String?>(null)
        val verificationEmail = _verificationEmail.asStateFlow()

        private val _pendingVerification = MutableStateFlow(false)
        val pendingVerification = _pendingVerification.asStateFlow()

        // Track if we should go to signup screen
        private val _navigateToSignup = MutableStateFlow(false)
        val navigateToSignup = _navigateToSignup.asStateFlow()

        // 🔥 NEW: Track if we should go to signin screen (for existing active accounts)
        private val _navigateToSignin = MutableStateFlow(false)
        val navigateToSignin = _navigateToSignin.asStateFlow()

        fun setVerificationEmail(email: String) {
            _verificationEmail.value = email
            _pendingVerification.value = true
            _navigateToSignup.value = true
            _navigateToSignin.value = false
            Log.d("🔥", "✅ Verification email set: $email")
        }

        // 🔥 NEW: Set navigation to signin screen for existing active accounts
        fun setSigninNavigation(email: String? = null) {
            _verificationEmail.value = email
            _pendingVerification.value = false
            _navigateToSignup.value = false
            _navigateToSignin.value = true
            Log.d("🔥", "✅ Signin navigation set for existing account: $email")
        }

        fun clearVerification() {
            _verificationEmail.value = null
            _pendingVerification.value = false
            _navigateToSignup.value = false
            _navigateToSignin.value = false
        }

        fun hasPendingVerification(): Boolean = _pendingVerification.value
        fun shouldNavigateToSignup(): Boolean = _navigateToSignup.value
        fun shouldNavigateToSignin(): Boolean = _navigateToSignin.value
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

        // 🔥 NEW: Check for manjanoapp://signin (for existing active accounts)
        if (uri.scheme == "manjanoapp" && uri.host == "signin") {
            Log.d("🔥", "✅ SIGNIN deep link received - navigating to signin screen")
            // Get email if present
            val email = uri.getQueryParameter("email")
            // Set navigation to signin screen (not signup)
            setSigninNavigation(email)
            return
        }

        // Check for manjanoapp://verification-success (SUCCESS - has email)
        if (uri.scheme == "manjanoapp" && uri.host == "verification-success") {
            Log.d("🔥", "✅ Verification SUCCESS deep link received")

            // Get email from the deep link
            val email = uri.getQueryParameter("email")

            if (!email.isNullOrEmpty()) {
                Log.d("🔥", "✅ Setting verification email: $email")
                setVerificationEmail(email)
            } else {
                Log.d("🔥", "No email found in success deep link")
            }
            return
        }

        // Check for manjanoapp://verification-failed (ERROR - no verification)
        if (uri.scheme == "manjanoapp" && uri.host == "verification-failed") {
            Log.d("🔥", "❌ Verification FAILED deep link received - keeping sections grayed")
            // Do NOT call setVerificationEmail()
            // BUT we still need to go to signup screen so user can request a new link
            _navigateToSignup.value = true
            Log.d("🔥", "✅ Setting navigate to signup screen (failed verification)")
            return
        }

        // Check for manjanoapp:// scheme (old)
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