package com.manjano.bus.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.navigation.NavHostController

@Composable
fun DriverDashboardScreen(navController: NavHostController) {
    Column {
        Text("Welcome to the Driver Dashboard", fontWeight = FontWeight.Bold)
    }
}