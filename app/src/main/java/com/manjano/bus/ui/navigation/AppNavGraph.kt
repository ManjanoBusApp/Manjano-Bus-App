package com.manjano.bus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.navArgument
import com.manjano.bus.ui.screens.WelcomeScreen
import com.manjano.bus.ui.screens.login.SignInScreen
import com.manjano.bus.ui.screens.login.SignupScreen
import com.manjano.bus.ui.screens.login.DriverSignupScreen
import com.manjano.bus.ui.screens.login.AdminSignupScreen
import com.manjano.bus.ui.screens.home.ParentDashboardScreen
import com.manjano.bus.viewmodel.SignInViewModel
import com.manjano.bus.ui.screens.home.AdminDashboardScreen
import com.manjano.bus.ui.screens.home.DriverDashboardScreen
import android.util.Log
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.navigation.NavType
import com.manjano.bus.viewmodel.SignUpViewModel
import com.manjano.bus.ui.screens.home.DriverDashboardViewModel
import com.manjano.bus.ui.screens.adminpanel.BusTrackingScreen
import com.manjano.bus.ui.screens.adminpanel.SendNotificationsScreen
import com.manjano.bus.ui.screens.adminpanel.ProfileEditScreen
import com.manjano.bus.ui.screens.adminpanel.AttendanceVerificationScreen
import com.manjano.bus.ui.screens.adminpanel.DriverCommunicationScreen
import com.manjano.bus.ui.screens.login.AdminSignInScreen
import com.manjano.bus.MainActivity

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startAtSignup: Boolean = false,
    startAtSignin: Boolean = false,
    deactivatedRole: String? = null
) {
    // Determine start destination
    val startDestination = when {
        startAtSignup -> "signup"
        startAtSignin -> {
            // 🔥 Parents go to welcome screen, drivers go to driver sign-in
            when (deactivatedRole) {
                "driver" -> "signin/driver"
                "admin" -> "admin_signin"
                else -> "welcome"  // Parents go to welcome screen
            }
        }
        else -> "welcome"
    }


    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome
        composable("welcome") { _: NavBackStackEntry ->
            WelcomeScreen(
                onParentClick = { navController.navigate("signin/parent") },
                onDriverClick = { navController.navigate("signin/driver") },
                onAdminClick = { navController.navigate("admin_signin") }
            )
        }

        // Signin with role
        composable(
            route = "signin/{role}",
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "parent"
            val signInViewModel: SignInViewModel = hiltViewModel()
            val signUpViewModel: SignUpViewModel = hiltViewModel()
            SignInScreen(
                navController = navController,
                viewModel = signInViewModel,
                signUpViewModel = signUpViewModel,
                role = role
            )
        }

        // Signup
        composable("signup") { _: NavBackStackEntry ->
            SignupScreen(navController = navController)
        }

        // Driver Signup
        composable("driver_signup") { _: NavBackStackEntry ->
            DriverSignupScreen(navController = navController)
        }

        // Admin Signup
        composable("admin_signup") { _: NavBackStackEntry ->
            AdminSignupScreen(navController = navController)
        }

        // Parent Dashboard
        composable(
            route = "parent_dashboard/{parentName}/{childrenNames}/{status}",
            arguments = listOf(
                navArgument("parentName") { type = NavType.StringType },
                navArgument("childrenNames") { type = NavType.StringType },
                navArgument("status") {
                    type = NavType.StringType
                    defaultValue = "On Route"
                }
            )
        ) { backStackEntry ->
            val parentName = URLDecoder.decode(
                backStackEntry.arguments?.getString("parentName") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            val childrenNames = URLDecoder.decode(
                backStackEntry.arguments?.getString("childrenNames") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            val status = URLDecoder.decode(
                backStackEntry.arguments?.getString("status") ?: "On Route",
                StandardCharsets.UTF_8.toString()
            )

            Log.d("ParentDashboard", "Route hit! parent=$parentName children=$childrenNames status=$status")

            val parentDashboardViewModel: com.manjano.bus.ui.screens.home.ParentDashboardViewModel =
                hiltViewModel()

            // The Handshake: Use the Full Name to start the DB listeners
            androidx.compose.runtime.LaunchedEffect(parentName) {
                if (parentName.isNotEmpty()) {
                    parentDashboardViewModel.initializeParent(parentName)
                }
            }

            ParentDashboardScreen(
                navController = navController,
                navBackStackEntry = backStackEntry,
                viewModel = parentDashboardViewModel // Ensure your Screen accepts this instance
            )
        }

        // Driver Dashboard
        composable(
            route = "driver_dashboard/{driverPhoneNumber}/{driverName}",
            arguments = listOf(
                navArgument("driverPhoneNumber") { type = NavType.StringType },
                navArgument("driverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverPhoneNumber = backStackEntry.arguments?.getString("driverPhoneNumber") ?: ""
            val driverNameEncoded = backStackEntry.arguments?.getString("driverName") ?: ""
            val driverName = URLDecoder.decode(driverNameEncoded, StandardCharsets.UTF_8.toString())
            val driverViewModel: DriverDashboardViewModel = hiltViewModel()
            DriverDashboardScreen(
                navController = navController,
                driverPhoneNumber = driverPhoneNumber,
            )
        }

        // Admin Dashboard
        composable("admin_dashboard") { _: NavBackStackEntry ->
            AdminDashboardScreen(navController = navController)
        }

        // Admin Sign-in (separate screen)
        composable("admin_signin") { _: NavBackStackEntry ->
            val signInViewModel: SignInViewModel = hiltViewModel()
            val signUpViewModel: SignUpViewModel = hiltViewModel()
            AdminSignInScreen(
                navController = navController,
                viewModel = signInViewModel,
                signUpViewModel = signUpViewModel
            )
        }

        // --- Mobile Admin Module Screens ---
        composable("bus_tracking") { _: NavBackStackEntry ->
            BusTrackingScreen()
        }
        composable("send_notifications") { _: NavBackStackEntry ->
            SendNotificationsScreen()
        }
        composable("profile_edit") { _: NavBackStackEntry ->
            ProfileEditScreen()
        }
        composable("attendance_verification") { _: NavBackStackEntry ->
            AttendanceVerificationScreen()
        }
        composable("driver_communication") { _: NavBackStackEntry ->
            DriverCommunicationScreen()
        }

    }
}