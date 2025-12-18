package com.manjano.bus.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.google.accompanist.permissions.* //
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.HorizontalDivider


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DriverDashboardScreen(
    navController: NavHostController,
    viewModel: DriverDashboardViewModel
) {
    // 1. Permission State
    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // 2. Request permission on entry
    LaunchedEffect(Unit) {
        locationPermissionState.launchMultiplePermissionRequest()
    }

    if (locationPermissionState.allPermissionsGranted) {
        // If granted, show the actual dashboard
        DashboardContent(navController, viewModel)
    } else {
        // If not granted, show a button to request it
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Location Permission is required to track the bus.")
                Button(onClick = { locationPermissionState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    navController: NavHostController,
    viewModel: DriverDashboardViewModel
) {
    val isTracking by viewModel.isTracking.collectAsState()
    val students by viewModel.studentList.collectAsState()
    val beijingRoad = LatLng(-1.3815977, 36.9395961)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(beijingRoad, 15f)
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 150.dp,
        sheetContainerColor = Color.White,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Student Attendance List",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                students.forEach { student ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Black
                            )
                            Text(
                                text = student.stop,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val activity = context as androidx.fragment.app.FragmentActivity

                        Button(
                            onClick = {
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                val biometricPrompt = androidx.biometric.BiometricPrompt(
                                    activity,
                                    executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            viewModel.markStudentAsBoarded(student.id)
                                        }
                                    }
                                )

                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Student Verification")
                                    .setSubtitle("Scan finger to confirm boarding")
                                    .setNegativeButtonText("Cancel")
                                    .build()

                                biometricPrompt.authenticate(promptInfo)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (student.status == "Boarded") Color.Gray else Color(0xFFE91E63)
                            ),
                            enabled = student.status != "Boarded"
                        ) {
                            Text(if (student.status == "Boarded") "Boarded" else "Scan")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    isTrafficEnabled = true,
                    isMyLocationEnabled = true
                )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.toggleTracking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) Color.Red else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = if (isTracking) "END TRIP" else "START TRIP",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}