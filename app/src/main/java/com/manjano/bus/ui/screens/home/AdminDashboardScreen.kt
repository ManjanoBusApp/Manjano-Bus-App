package com.manjano.bus.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AdminDashboardScreen(
    navController: NavHostController,
    viewModel: AdminDashboardViewModel = hiltViewModel()
) {
    val loading by viewModel.loading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchQuickData()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // <-- Set white background
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (errorMessage != null) {
            Text(
                text = "Error: $errorMessage",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Admin Dashboard Banner ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .height(70.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF800080))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Admin Dashboard",
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // --- Sign Out Button (below banner) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Sign Out",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            navController.navigate("welcome") {
                                popUpTo("admin_dashboard") { inclusive = true }
                            }
                        }
                    )
                }

                // --- Dashboard Buttons ---
                DashboardButton("Real-Time Bus Tracking", Icons.Filled.DirectionsBus) {
                    navController.navigate("bus_tracking")
                }

                DashboardButton("Send Alerts / Notifications", Icons.Filled.Notifications) {
                    navController.navigate("send_notifications")
                }

                DashboardButton("Quick Profile Edits", Icons.Filled.Person) {
                    navController.navigate("profile_edit")
                }

                DashboardButton("Verify Attendance", Icons.Filled.CheckCircle) {
                    navController.navigate("attendance_verification")
                }

                DashboardButton("Contact Drivers", Icons.Filled.Phone) {
                    navController.navigate("driver_communication")
                }
            }
        }
    }
}

// --- Reusable Composable for dashboard buttons ---
@Composable
fun DashboardButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}