package com.manjano.bus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.manjano.bus.ui.ManjanoAppUI
import com.manjano.bus.ui.theme.ManjanoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private val _verificationEmail = MutableStateFlow<String?>(null)
        val verificationEmail = _verificationEmail.asStateFlow()

        private val _pendingVerification = MutableStateFlow(false)
        val pendingVerification = _pendingVerification.asStateFlow()

        private val _navigateToSignup = MutableStateFlow(false)
        val navigateToSignup = _navigateToSignup.asStateFlow()

        private val _navigateToSignin = MutableStateFlow(false)
        val navigateToSignin = _navigateToSignin.asStateFlow()

        private val _deactivatedUserRole = MutableStateFlow<String?>(null)
        val deactivatedUserRole = _deactivatedUserRole.asStateFlow()

        fun clearDeactivatedUserRole() {
            _deactivatedUserRole.value = null
        }

        fun setVerificationEmail(email: String) {
            _verificationEmail.value = email
            _pendingVerification.value = true
            _navigateToSignup.value = true
            _navigateToSignin.value = false
            Log.d("🔥", "✅ Verification email set: $email")
        }

        fun setSigninNavigation(email: String? = null) {
            _verificationEmail.value = email
            _pendingVerification.value = false
            _navigateToSignup.value = false  // ← This is important
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

    private var activeStatusListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun checkUserActiveStatus() {
        val prefs = getSharedPreferences("user_session", Context.MODE_PRIVATE)
        val userRole = prefs.getString("user_role", null)
        val userPhone = prefs.getString("user_phone", null)

        Log.d("🔥", "checkUserActiveStatus - role: $userRole, phone: $userPhone")

        // Remove any existing listener
        activeStatusListener?.remove()

        if (userRole != null && userPhone != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val collectionName = if (userRole == "driver") "drivers" else "parents"

            Log.d("🔥", "Setting up real-time listener for $collectionName with mobileNumber: $userPhone")

            // Set up a real-time listener instead of a one-time get
            activeStatusListener = db.collection(collectionName)
                .whereEqualTo("mobileNumber", userPhone)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("🔥", "Error listening to active status", error)
                        return@addSnapshotListener
                    }

                    val doc = snapshot?.documents?.firstOrNull()
                    if (doc != null) {
                        val isActive = doc.getBoolean("active") ?: true
                        Log.d("🔥", "Real-time update - active: $isActive")

                        if (!isActive) {
                            Log.d("🔥", "User deactivated in real-time - signing out")

                            // Save the user's role before clearing session
                            val deactivatedRole = userRole

                            prefs.edit().clear().apply()
                            clearVerification()

                            // Store the role so we know which sign-in screen to show
                            _deactivatedUserRole.value = deactivatedRole

                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        Log.d("🔥", "No document found for phone: $userPhone")
                    }
                }
        } else {
            Log.d("🔥", "No active session found")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        activeStatusListener?.remove()
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
                    startAtSignup = shouldNavigateToSignup()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("🔥", "App resumed - checking user status")
        checkUserActiveStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("🔥", "🔧 MainActivity onNewIntent called")
        intent.data?.let { uri ->
            handleDeepLink(uri)
        }
    }

    private fun handleDeepLink(uri: Uri) {
        Log.d("🔥", "🔧 Deep link received: $uri")
        Log.d("🔥", "🔧 Scheme: ${uri.scheme}")
        Log.d("🔥", "🔧 Host: ${uri.host}")

        if (uri.scheme == "manjanoapp" && uri.host == "signin") {
            Log.d("🔥", "✅ SIGNIN deep link received")
            val email = uri.getQueryParameter("email")

            // 🔥 Clear any pending signup data to prevent old data from showing
            val pendingPrefs = getSharedPreferences("pending_signup", Context.MODE_PRIVATE)
            pendingPrefs.edit().clear().apply()

            // 🔥 Clear verification state and set sign-in navigation
            clearVerification()
            setSigninNavigation(email)
            return
        }

        if (uri.scheme == "manjanoapp" && uri.host == "verification-success") {
            Log.d("🔥", "✅ Verification SUCCESS deep link received")
            val email = uri.getQueryParameter("email")
            if (!email.isNullOrEmpty()) {
                setVerificationEmail(email)
            }
            return
        }

        if (uri.scheme == "manjanoapp" && uri.host == "verification-failed") {
            Log.d("🔥", "❌ Verification FAILED deep link received")
            _navigateToSignup.value = true
            return
        }

        if (uri.scheme == "manjanoapp" && uri.host == "verify") {
            val email = uri.getQueryParameter("email")
            if (email != null && email.isNotEmpty()) {
                setVerificationEmail(email)
            }
        } else if (uri.scheme == "https" && uri.host == "manjano-app.web.app" && uri.path == "/verify") {
            val email = uri.getQueryParameter("email")
            if (email != null && email.isNotEmpty()) {
                setVerificationEmail(email)
            }
        } else {
            Log.d("🔥", "Not a verification deep link, ignoring")
        }
    }
}