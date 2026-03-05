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

            PhoneInputSection(
                selectedCountry = uiState.selectedCountry,
                phoneNumber = uiState.rawPhoneInput,
                onCountrySelected = viewModel::onCountrySelected,
                onPhoneNumberChange = {
                    viewModel.onPhoneNumberChange(it)
                    showPhoneError = false
                },
                showError = showPhoneError,
                onShowErrorChange = { showPhoneError = it },
                phoneFocusRequester = phoneFocusRequester,
                keyboardController = keyboardController,
                focusManager = focusManager
            )

            val snackbarHostState = remember { SnackbarHostState() }

            SnackbarHost(snackbarHostState)

            ActionRow(
                rememberMe = uiState.rememberMe,
                isSendingOtp = uiState.isSendingOtp,
                onRememberMeChange = viewModel::onRememberMeChange,
                onGetCodeClick = {

                    val isValid = PhoneNumberUtils.isValidNumber(
                        uiState.rawPhoneInput,
                        uiState.selectedCountry.isoCode
                    )

                    if (!isValid) {
                        showPhoneError = true
                        phoneFocusRequester.requestFocus()
                    } else {

                        showPhoneError = false

                        keyboardController?.hide()
                        focusManager.clearFocus()

                        viewModel.requestOtp()

                        scope.launch {

                            delay(300)

                            focusManager.clearFocus(force = true)
                            otpFocusRequester.requestFocus()

                            scrollState.animateScrollTo(scrollState.maxValue)

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
                    text = "Please check your SMS for the ${Constants.OTP_LENGTH}-digit code",
                    color = Color.Black,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            OtpInputRow(
                otp = uiState.otpDigits,
                otpErrorMessage = uiState.otpErrorMessage,
                shouldShakeOtp = uiState.shouldShakeOtp,
                onOtpChange = { digits ->

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
                        ?: "Incorrect code. Please resend the code?",
                    color = MaterialTheme.colorScheme.error,
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

            val isPhoneValid = try {

                val proto = PhoneNumberUtil
                    .getInstance()
                    .parse(
                        uiState.rawPhoneInput,
                        uiState.selectedCountry.isoCode
                    )

                PhoneNumberUtil
                    .getInstance()
                    .isValidNumberForRegion(
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
                        isOtpComplete &&
                        !uiState.isOtpSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (isPhoneValid && isOtpComplete)
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