package com.manjano.bus.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.manjano.bus.ui.theme.ManjanoTheme
import com.manjano.bus.ui.navigation.AppNavGraph


@Composable
fun ManjanoAppUI() {
    val navController = rememberNavController() // main nav controller

    ManjanoTheme {
        AppNavGraph(navController = navController) // use the main nav graph
    }
}
