package com.manjano.bus.ui.navigation

import androidx.compose.runtime.Composable
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


@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") { _: NavBackStackEntry ->
            WelcomeScreen(
                onParentClick = { navController.navigate("signin/parent") },
                onDriverClick = { navController.navigate("signin/driver") },
                onAdminClick = { navController.navigate("signin/admin") }
            )
        }

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
                signUpViewModel = signUpViewModel, // Added
                role = role
            )
        }

        composable("signup") { _: NavBackStackEntry ->
            SignupScreen(navController = navController)
        }

        composable("driver_signup") { _: NavBackStackEntry ->
            DriverSignupScreen(navController = navController)
        }

        composable("admin_signup") { _: NavBackStackEntry ->
            AdminSignupScreen(navController = navController)
        }

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
            // Decode values passed from SignupScreen (kept for logging/safety)
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

            ParentDashboardScreen(
                navController = navController,
                navBackStackEntry = backStackEntry // <-- CRITICAL: Pass the backStackEntry object
            )
        }

        composable("driver_dashboard") { _: NavBackStackEntry ->
            val driverViewModel: DriverDashboardViewModel = hiltViewModel()
            DriverDashboardScreen(
                navController = navController,
                viewModel = driverViewModel
            )
        }

        composable("admin_dashboard") { _: NavBackStackEntry ->
            AdminDashboardScreen(navController = navController)
        }
    }
}