package com.manjano.bus.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.manjano.bus.MainActivity
import com.manjano.bus.ui.theme.ManjanoTheme
import com.manjano.bus.ui.navigation.AppNavGraph


@Composable
fun ManjanoAppUI(
    startAtSignup: Boolean = false  // 🔥 Add this parameter
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 🔥 Check if there's pending signup data
    val prefs = context.getSharedPreferences("pending_signup", Context.MODE_PRIVATE)
    val hasPendingSignup = prefs.getBoolean("pending_verification", false)

    // Determine if we should start at signup screen
    val shouldStartAtSignup = startAtSignup || hasPendingSignup

    ManjanoTheme {
        AppNavGraph(
            navController = navController,
            startAtSignup = shouldStartAtSignup  // 🔥 Pass to AppNavGraph
        )
    }
}