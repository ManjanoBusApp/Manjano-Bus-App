package com.manjano.bus.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Signup : Screen("signup")
    object Home : Screen("home")
    object PhoneAuth : Screen("phone_auth")
    object OTPVerification : Screen("otp_verification/{phoneNumber}") {
        fun createRoute(phoneNumber: String): String = "otp_verification/$phoneNumber"
    }

    // NEW: role-based dashboards
    object ParentDashboard : Screen("parent_dashboard")
    object DriverDashboard : Screen("driver_dashboard")

    object ProfileSetup : Screen("profile_setup")
}
