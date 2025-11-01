package com.manjano.bus.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.manjano.bus.viewmodel.OTPVerificationViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun OtpVerificationScreen(
    navController: NavController,
    phoneNumber: String,
    viewModel: OTPVerificationViewModel
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    // OTP state
    val otpFields = List(4) { remember { mutableStateOf("") } }
    val focusRequesters = List(4) { FocusRequester() }

    val isOtpRequested by viewModel.isOtpRequested.collectAsState()
    val isOtpInvalid by viewModel.isOtpInvalid.collectAsState()

    // Shake animation
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isOtpInvalid) {
        if (isOtpInvalid) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(100)
            ) // can repeat if needed
        }
    }

    // Auto-focus first box after OTP requested
    LaunchedEffect(isOtpRequested) {
        if (isOtpRequested) {
            focusRequesters[0].requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Manjano Bus App",
                fontSize = 28.sp,
                color = Color(128, 0, 128)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Check your SMS for OTP sent to $phoneNumber",
                fontSize = 14.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.offset(x = shakeOffset.value.dp)
            ) {
                otpFields.forEachIndexed { index, otpState ->
                    OutlinedTextField(
                        value = otpState.value,
                        onValueChange = { input ->
                            if (input.length <= 1 && input.all { it.isDigit() }) {
                                otpState.value = input

                                if (input.isNotEmpty() && index < 3) {
                                    focusRequesters[index + 1].requestFocus()
                                }
                                if (input.isEmpty() && index > 0) {
                                    focusRequesters[index - 1].requestFocus()
                                }
                                // Hide keyboard after last digit
                                if (input.isNotEmpty() && index == 3) {
                                    keyboardController?.hide()
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                            .focusRequester(focusRequesters[index]),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 18.sp,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        ),
                        placeholder = { Text("○", color = Color.Gray, textAlign = TextAlign.Center) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val isVerified by viewModel.isVerified.collectAsState()

            LaunchedEffect(isVerified) {
                if (isVerified) {
                    navController.navigate("dashboard") {
                        popUpTo("otpverification") { inclusive = true }
                    }
                }
            }

            Button(
                onClick = {
                    val otpValue = otpFields.joinToString("") { it.value }
                    if (otpValue.length == 4) {
                        viewModel.updateOtpDigits(otpValue)
                        viewModel.verifyOtp()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(128, 0, 128))
            ) {
                Text("Continue", color = Color.White)
            }
        }
    }
}
