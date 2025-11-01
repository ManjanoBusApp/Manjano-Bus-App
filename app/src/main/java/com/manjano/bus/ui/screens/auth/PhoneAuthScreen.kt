package com.manjano.bus.ui.screens.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.manjano.bus.viewmodel.PhoneAuthViewModel
import com.manjano.bus.ui.navigation.Screen

@Composable
fun PhoneAuthScreen(
    navController: NavController,
    viewModel: PhoneAuthViewModel = hiltViewModel()
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val activity = context as? Activity

    var phoneNumber by remember { mutableStateOf("+254") }

    val isPhoneValid = remember(phoneNumber) {
        phoneNumber.length >= 10 && phoneNumber.startsWith("+")
    }

    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.verificationFailed.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Verify your phone number", fontSize = 20.sp, color = Color.Black)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        activity?.let {
                            viewModel.startPhoneNumberVerification(phoneNumber, it)
                            navController.navigate(Screen.OTPVerification.route + "/$phoneNumber")
                        }
                    },
                    enabled = isPhoneValid && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send OTP")
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator()
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
