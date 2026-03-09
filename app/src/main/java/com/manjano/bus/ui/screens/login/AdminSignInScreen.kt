package com.manjano.bus.ui.screens.login

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.manjano.bus.R
import com.manjano.bus.utils.Constants
import com.manjano.bus.utils.PhoneNumberUtils
import com.manjano.bus.viewmodel.SignInViewModel
import com.manjano.bus.viewmodel.SignUpViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.unit.dp
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import android.util.Log
@Composable
fun AdminSignInScreen(
    navController: NavController,
    viewModel: SignInViewModel,
    signUpViewModel: SignUpViewModel
) {

    val role = "admin"

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val otpFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }

    var showValidationError by rememberSaveable { mutableStateOf(false) }
    var showPhoneError by rememberSaveable { mutableStateOf(false) }
    var showUnauthorizedError by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val signUpUiState by signUpViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigateToDashboard) {
        if (uiState.navigateToDashboard) {

            navController.navigate("admin_dashboard") {
                popUpTo("signin/admin") { inclusive = true }
            }

            viewModel.onNavigationConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
        ) {

            Image(
                painter = painterResource(id = R.drawable.ic_bus),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Manjano Bus App",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Text(
                text = "Sign in",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 16.dp, bottom = 24.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PhoneInputSection(
                        selectedCountry = uiState.selectedCountry,
                        phoneNumber = uiState.rawPhoneInput,
                        onCountrySelected = viewModel::onCountrySelected,
                        onPhoneNumberChange = {
                            viewModel.onPhoneNumberChange(it)
                            showPhoneError = false
                            showUnauthorizedError = false
                        },
                        showError = showPhoneError,
                        onShowErrorChange = { showPhoneError = it },
                        phoneFocusRequester = phoneFocusRequester,
                        keyboardController = keyboardController,
                        focusManager = focusManager
                    )

                    if (showUnauthorizedError) {
                        Text(
                            text = "Not authorized. Admin access only.",
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .padding(start = 0.dp, top = 2.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            val snackbarHostState = remember { SnackbarHostState() }

            SnackbarHost(snackbarHostState)

            ActionRow(
                rememberMe = uiState.rememberMe,
                isSendingOtp = uiState.isSendingOtp,
                onRememberMeChange = viewModel::onRememberMeChange,

                onGetCodeClick = {

                    Log.d("AdminSignInDebug", "Get Code button tapped - starting admin check")

                    val isValid = PhoneNumberUtils.isPossibleNumber(
                        uiState.rawPhoneInput,
                        uiState.selectedCountry.isoCode
                    )

                    Log.d(
                        "AdminSignInDebug",
                        "Phone validation result: $isValid | raw input: ${uiState.rawPhoneInput} | country code: ${uiState.selectedCountry.isoCode}"
                    )

                    if (!isValid) {
                        Log.d("AdminSignInDebug", "Validation FAILED - showing phone error")
                        showPhoneError = true
                        phoneFocusRequester.requestFocus()
                    } else {

                        Log.d("AdminSignInDebug", "Validation PASSED - checking admin in Firestore")

                        showPhoneError = false

                        keyboardController?.hide()
                        focusManager.clearFocus()

                        showUnauthorizedError = false

                        var normalizedInput = uiState.rawPhoneInput.filter { it.isDigit() }

                        // Handle common Kenyan formats: 07xxxxxxxx, 7xxxxxxxx, 254xxxxxxxxx
                        normalizedInput = when {
                            normalizedInput.startsWith("254") -> "0" + normalizedInput.substring(3)
                            normalizedInput.startsWith("7") && normalizedInput.length == 9 -> "0$normalizedInput"
                            normalizedInput.startsWith("07") -> normalizedInput
                            normalizedInput.startsWith("011") -> normalizedInput
                            else -> normalizedInput
                        }

                        Log.d(
                            "AdminSignInDebug",
                            "Normalized phone for Firestore query: $normalizedInput"
                        )

                        viewModel.getAdminRoleByMobile(normalizedInput) { role ->
                            if (role == null) {
                                // Number not authorized
                                showUnauthorizedError = true
                                phoneFocusRequester.requestFocus()
                            } else {
                                // Number authorized
                                showUnauthorizedError = false
                                viewModel.requestOtp()

                                scope.launch {
                                    delay(300)
                                    focusManager.clearFocus(force = true)
                                    otpFocusRequester.requestFocus()
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }

                                viewModel.setTargetDashboardRoute(
                                    if (role == "super_admin") "super_admin_dashboard" else "admin_dashboard"
                                )
                            }
                        }
                    }
                },
                scope = scope
            )
            Spacer(modifier = Modifier.height(8.dp))

            ResendTimerSection(
                timer = uiState.resendTimerSeconds,
                canResend = uiState.canResendOtp,
                onResendClick = viewModel::resendOtp
            )

            AnimatedVisibility(visible = uiState.showSmsMessage) {

                Text(
                    text = "Check SMS for ${Constants.OTP_LENGTH}-digit code",
                    color = Color.Red,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }


            OtpInputRow(
                otp = uiState.otpDigits,
                otpErrorMessage = uiState.otpErrorMessage,
                shouldShakeOtp = uiState.shouldShakeOtp,
                onOtpChange = { digits ->
                    viewModel.hideSmsMessage()  // hide SMS prompt immediately

                    digits.forEachIndexed { index, digit ->
                        viewModel.onOtpDigitChange(index, digit) {
                            keyboardController?.hide()
                        }
                    }
                },
                keyboardController = keyboardController,
                onClearError = { showValidationError = false },
                onAutoVerify = { },
                isSending = uiState.isOtpSubmitting,
                focusRequester = otpFocusRequester
            )

            AnimatedVisibility(visible = uiState.showOtpError) {

                Text(
                    text = uiState.otpErrorMessage
                        ?: "Incorrect code. Send code again",
                    color = Color.Red,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .heightIn(min = 18.dp),
                    textAlign = TextAlign.Center
                )
            }

            val continueShakeOffset = remember { mutableFloatStateOf(0f) }
            val haptic = LocalHapticFeedback.current

            var isPhoneAuthorized by remember { mutableStateOf(false) }

            LaunchedEffect(uiState.rawPhoneInput) {
                val normalizedInput = uiState.rawPhoneInput.filter { it.isDigit() }.let {
                    when {
                        it.startsWith("254") -> "0" + it.substring(3)
                        it.startsWith("7") && it.length == 9 -> "0$it"
                        it.startsWith("07") -> it
                        it.startsWith("011") -> it
                        else -> it
                    }
                }

                if (normalizedInput.length == 10) {
                    viewModel.getAdminRoleByMobile(normalizedInput) { role ->
                        isPhoneAuthorized = role != null
                        showUnauthorizedError = role == null
                    }
                } else {
                    isPhoneAuthorized = false
                    showUnauthorizedError = false
                }
            }
            val isPhoneValid = try {
                val proto = PhoneNumberUtil.getInstance().parse(
                    uiState.rawPhoneInput,
                    uiState.selectedCountry.isoCode
                )
                PhoneNumberUtil.getInstance().isValidNumberForRegion(
                    proto,
                    uiState.selectedCountry.isoCode
                )
            } catch (e: Exception) {
                false
            }

            val isOtpComplete = uiState.otpDigits.all { it.isNotEmpty() }

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showValidationError = false

                    val enteredOtp = uiState.otpDigits.joinToString("")
                    viewModel.updateOtpDigits(enteredOtp)
                    viewModel.verifyOtp()

                    scope.launch {
                        repeat(2) {
                            continueShakeOffset.floatValue = 4f
                            delay(40)
                            continueShakeOffset.floatValue = -4f
                            delay(40)
                        }
                        continueShakeOffset.floatValue = 0f
                    }
                },
                enabled = isPhoneValid &&
                        isPhoneAuthorized &&
                        isOtpComplete &&
                        !uiState.isOtpSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (isPhoneValid && isPhoneAuthorized && isOtpComplete)
                            Color(0xFF800080)
                        else
                            Color.LightGray,
                    disabledContainerColor = Color.LightGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .offset(x = continueShakeOffset.floatValue.dp)
            ) {
                Text(
                    text = "Continue",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }

            SignUpFooter(
                onSignUpClick = {

                    navController.navigate("admin_signup")

                }
            )

            Spacer(modifier = Modifier.height(80.dp))

        }
    }
}