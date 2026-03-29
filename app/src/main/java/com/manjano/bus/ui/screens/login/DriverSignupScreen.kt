package com.manjano.bus.ui.screens.login

import android.util.Patterns
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.manjano.bus.R
import com.manjano.bus.models.CountryRepository
import com.manjano.bus.utils.Constants
import com.manjano.bus.utils.PhoneNumberUtils
import com.manjano.bus.viewmodel.SignUpViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlin.ranges.coerceIn
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalFocusManager
import android.util.Log


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverSignupScreen(
    navController: NavController,
    signupViewModel: SignUpViewModel = viewModel()
) {
    val appPurple = Color(0xFF800080)
    val uiState by signupViewModel.uiState.collectAsState()

    // Snackbar setup for OTP errors
    // ========================
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()



    LaunchedEffect(uiState.showOtpError) {
        if (uiState.showOtpError) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = uiState.otpErrorMessage ?: "Invalid OTP",
                    withDismissAction = true
                )
            }
        }
    }
    var driverName by remember { mutableStateOf(TextFieldValue("")) }
    var idNumber by remember { mutableStateOf(TextFieldValue("")) }
    var schoolName by remember { mutableStateOf(TextFieldValue("")) }
    var driverError by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf(false) }
    var schoolError by remember { mutableStateOf(false) }

    // New trackers to prevent immediate errors
    var driverTouched by remember { mutableStateOf(false) }
    var idTouched by remember { mutableStateOf(false) }
    var schoolTouched by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var phoneTouched by remember { mutableStateOf(false) }
    var phoneErrorInvalid by remember { mutableStateOf(false) }
    var phoneErrorNotRegistered by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryRepository.countries.first()) }
    var phoneNumber by remember { mutableStateOf("") }
    var showOtpMessage by remember { mutableStateOf(false) }
    var showOtpErrorMessage by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val otpFocusRequester = remember { FocusRequester() }
    val driverFocusRequester = remember { FocusRequester() }
    val idFocusRequester = remember { FocusRequester() }
    val schoolFocusRequester = remember { FocusRequester() }

// Already-registered error – provide initial = null to avoid type inference errors
    // Already-registered error – collect from ViewModel
    val alreadyRegistered by signupViewModel.alreadyRegisteredError.collectAsState(initial = null)

// Block actions if driver is already registered
    val isBlocked = !alreadyRegistered.isNullOrBlank()

// Phone allowed state – provide initial value if needed (Boolean? in your ViewModel)
    val phoneAllowed by signupViewModel.isPhoneAllowed.collectAsState(initial = null)

    LaunchedEffect(phoneAllowed) {
        if (phoneAllowed == null) return@LaunchedEffect

        // Only update this error for numbers not in Firestore
        phoneErrorNotRegistered = phoneAllowed == false
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .imePadding() // Adjusts padding when the keyboard appears
            .navigationBarsPadding() // Adds padding for navigation bar
            .systemBarsPadding() // Adds padding for status bar and navigation bar
    ) {


        Image(
            painter = painterResource(id = R.drawable.ic_logo),
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
            text = "Driver Sign Up",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 16.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = driverName,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { ch -> ch.isLetter() || ch.isWhitespace() }
                driverName = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                if (filtered.isNotEmpty()) driverError = false
            },
            placeholder = { Text("Your Full Name") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),

            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(driverFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        driverTouched = true
                    } else if (!focusState.isFocused && driverTouched && driverName.text.isEmpty()) {
                        driverError = true
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            shape = RoundedCornerShape(12.dp),
            isError = driverError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (driverError) Color.Red else appPurple,
                unfocusedBorderColor = if (driverError) Color.Red else Color.Gray,
                cursorColor = if (driverError) Color.Red else appPurple
            )
        )

        if (driverError) Text("Please fill your name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // ID Number
        OutlinedTextField(
            value = idNumber,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { ch -> ch.isDigit() }
                idNumber = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                if (filtered.isNotEmpty()) idError = false
            },
            placeholder = { Text("ID Number") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(idFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        idTouched = true
                        idError = false
                    } else if (!focusState.isFocused && idTouched && idNumber.text.isEmpty()) {
                        idError = true
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            shape = RoundedCornerShape(12.dp),
            isError = idError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (idError) Color.Red else appPurple,
                unfocusedBorderColor = if (idError) Color.Red else Color.Gray,
                cursorColor = if (idError) Color.Red else appPurple
            )
        )

        if (idError) Text("Please Enter Your ID Number", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // School Name
        OutlinedTextField(
            value = schoolName,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { ch -> ch.isLetter() || ch.isWhitespace() }
                schoolName = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                if (filtered.isNotEmpty()) schoolError = false
            },
            placeholder = { Text("School Name") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(schoolFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        schoolTouched = true
                        schoolError = false
                    } else if (!focusState.isFocused && schoolTouched && schoolName.text.isEmpty()) {
                        schoolError = true
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
            shape = RoundedCornerShape(12.dp),
            isError = schoolError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (schoolError) Color.Red else appPurple,
                unfocusedBorderColor = if (schoolError) Color.Red else Color.Gray,
                cursorColor = if (schoolError) Color.Red else appPurple
            )
        )

        if (schoolError) Text("Please Enter the School Name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // ---------------------------- PHONE INPUT ----------------------------
        val phoneFocusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) {
                        phoneErrorInvalid = false
                    }
                }
        ) {
            PhoneInputSection(
                selectedCountry = selectedCountry,
                phoneNumber = phoneNumber,
                onCountrySelected = { selectedCountry = it },
                onPhoneNumberChange = { input ->
                    phoneNumber = input
                    if (phoneTouched) {
                        phoneErrorInvalid = false
                    }
                },
                showError = phoneErrorInvalid || phoneErrorNotRegistered,
                onShowErrorChange = {},
                phoneFocusRequester = phoneFocusRequester,
                keyboardController = keyboardController,
                focusManager = focusManager
            )
        }

// Only show error texts after Send Code was tapped
        if (phoneTouched) {
            when {
                phoneErrorInvalid -> {
                    Text(
                        text = "Invalid phone number",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                phoneErrorNotRegistered -> {
                    Text(
                        text = "Can't proceed, contact the school",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                phoneAllowed == true && !alreadyRegistered.isNullOrBlank() -> {
                    Text(
                        text = alreadyRegistered!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
// LaunchedEffect to check Firestore when user types
        LaunchedEffect(phoneNumber) {
            if (phoneNumber.isNotBlank()) {
                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }

                if (isValidPhone) {
                    signupViewModel.checkDriverPhoneNumberInFirestore(
                        phone = phoneNumber,
                        countryIso = selectedCountry.isoCode
                    )
                } else {
                    // Reset previously stored Firestore state if phone is invalid
                    signupViewModel.resetPhoneAllowed()
                }
            } else {
                signupViewModel.resetPhoneAllowed()
            }
        }

        var isButtonInProgress by remember { mutableStateOf(false) }

        LaunchedEffect(uiState.resendTimerSeconds) {
            if (uiState.resendTimerSeconds <= 0 && isButtonInProgress) {
                isButtonInProgress = false
            }
        }
        // ---------------------------- SEND CODE VALIDATION ----------------------------
        ActionRow(
            rememberMe = uiState.rememberMe,
            isSendingOtp = isButtonInProgress,           // ← changed
            timer = uiState.resendTimerSeconds,
            onRememberMeChange = signupViewModel::onRememberMeChange,
            onGetCodeClick = {
                if (isButtonInProgress) return@ActionRow   // prevent double clicks

                // ── IMMEDIATELY show sending state ────────────────
                isButtonInProgress = true

                // Hide keyboard
                keyboardController?.hide()

                // Mark fields as touched
                phoneTouched = true
                driverTouched = true
                idTouched = true
                schoolTouched = true

                // Validate fields
                driverError = driverName.text.isBlank()
                idError = idNumber.text.isBlank()
                schoolError = schoolName.text.isBlank()

                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }
                phoneErrorInvalid = !isValidPhone

                if (!isValidPhone ||
                    !alreadyRegistered.isNullOrBlank() ||
                    phoneErrorNotRegistered
                ) {
                    isButtonInProgress = false
                    return@ActionRow
                }

                // Only reach here if we intend to actually send OTP
                showOtpMessage = phoneAllowed == true && alreadyRegistered.isNullOrBlank()

                // Focus OTP field if conditions are good
                if (alreadyRegistered.isNullOrBlank()) {
                    otpFocusRequester.requestFocus()
                }


                signupViewModel.requestOtp()

                // IMPORTANT: we do NOT set isButtonInProgress = false here!
                // It will be cleared by LaunchedEffect when timer ends
            }   //  ←←←←←←←← ADD THIS LINE (closes the onGetCodeClick lambda)
        )

        SnackbarHost(
            hostState = snackbarHostState
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color.Red,   // Red background for error
                contentColor = Color.White     // White text for contrast
            )
        }

        if (showOtpMessage) {
            ResendTimerSection(
                timer = uiState.resendTimerSeconds,
                canResend = uiState.canResendOtp,
                onResendClick = { signupViewModel.resendOtp() }
            )
        }
        if (showOtpMessage && !isBlocked) {
            Text(
                text = "Check SMS for 4-digit code.",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (showOtpErrorMessage) { // → New: Show invalid OTP message
            Text(
                text = "Incorrect code. Send code again.",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        DriverSignupOtpInputRow(
            otp = uiState.otpDigits,
            otpErrorMessage = uiState.otpErrorMessage,
            shouldShakeOtp = uiState.shouldShakeOtp,
            onOtpChange = { digits: List<String> ->
                showOtpMessage = false

                // 🔥 RESET OTP SENDING STATE when user starts typing
                if (digits.any { it.isNotEmpty() }) {
                    signupViewModel.resetOtpSendingState()
                }

                digits.forEachIndexed { index, digit ->
                    signupViewModel.onOtpDigitChange(index, digit)
                }
                if (digits.all { it.isNotEmpty() }) {
                    val enteredOtp = digits.joinToString("")
                    if (enteredOtp == Constants.TEST_OTP) {
                        keyboardController?.hide()
                        signupViewModel.setOtpValid(true)
                        showOtpErrorMessage = false
                    } else {
                        signupViewModel.setOtpValid(false)
                        showOtpErrorMessage = true

                        scope.launch {
                            delay(1000)
                            repeat(Constants.OTP_LENGTH) { index ->
                                signupViewModel.onOtpDigitChange(index, "")
                            }
                            focusManager.clearFocus()
                            delay(50)
                            otpFocusRequester.requestFocus()
                        }
                    }
                } else {
                    showOtpErrorMessage = false
                }
            },
            keyboardController = keyboardController,
            focusManager = focusManager,
            onClearError = {
                showOtpErrorMessage = false
            },
            onAutoVerify = {},
            isSending = uiState.isOtpSubmitting,
            focusRequester = otpFocusRequester
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isOtpValid by signupViewModel.isOtpValid.collectAsState()
        val continueShakeOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        val isPhoneAllowed by signupViewModel.isPhoneAllowed.collectAsState(initial = null)

        Button(
            onClick = {
                scope.launch {
                    // Shake animation
                    repeat(2) {
                        continueShakeOffset.floatValue = 4f
                        delay(40)
                        continueShakeOffset.floatValue = -4f
                        delay(40)
                    }
                    continueShakeOffset.floatValue = 0f
                }

                // Field validations
                driverError = driverName.text.isEmpty()
                idError = idNumber.text.isEmpty()
                schoolError = schoolName.text.isEmpty()

                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }
                phoneError = phoneNumber.isEmpty() || !isValidPhone

                // Only allow navigation if phone is allowed
                val isPhoneValid = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }

// Block sign-up if number already registered
                if (!driverError && !idError && !schoolError && !phoneError && isPhoneValid) {

                    when {
                        alreadyRegistered.isNullOrBlank() && isPhoneAllowed == true -> {
                            // Format driver name and school name for document ID
                            val formattedDriverId = "${driverName.text.trim()} - ${schoolName.text.trim()}"


                            signupViewModel.saveDriverProfileIfNeeded(
                                phoneNumber = phoneNumber,
                                fullName = driverName.text,
                                nationalId = idNumber.text,
                                schoolName = schoolName.text,
                                countryIso = selectedCountry.isoCode,
                                documentId = formattedDriverId  // 🔥 Pass the formatted ID
                            )

                            val encodedDriverName =
                                java.net.URLEncoder.encode(driverName.text, "UTF-8")

                            val safeDriverName =
                                java.net.URLEncoder.encode(driverName.text, "UTF-8")

                            // Use the formatted ID for navigation (or keep using phone number)
                            runCatching {
                                navController.navigate("driver_dashboard/$phoneNumber/$safeDriverName") {
                                    popUpTo("driver_signup") { inclusive = true }
                                }
                            }.onFailure {
                                Log.e(
                                    "NavError",
                                    "Cannot navigate to driver_dashboard: ${it.message}"
                                )
                            }
                        }

                        !alreadyRegistered.isNullOrBlank() -> {
                            // Show already-registered error under phone input
                            signupViewModel.setAlreadyRegisteredError("You’re registered, please Sign-in")
                        }

                        else -> {
                            // Phone not registered or other issues
                            phoneErrorNotRegistered = true
                        }
                    }
                }
            },
            enabled = driverName.text.isNotEmpty() &&
                    idNumber.text.isNotEmpty() &&
                    schoolName.text.isNotEmpty() &&
                    phoneNumber.isNotEmpty() &&
                    uiState.otpDigits.joinToString("") == Constants.TEST_OTP &&
                    isPhoneAllowed == true &&
                    alreadyRegistered.isNullOrBlank(),  // block continue if driver already exists

            modifier = Modifier
                .fillMaxWidth()
                .offset(x = continueShakeOffset.floatValue.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appPurple,
                disabledContainerColor = Color.LightGray,
                contentColor = Color.White,
                disabledContentColor = Color.White
            )
        ) {
            Text("Continue", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Have an account? ",
                color = Color.Black,
                fontSize = 14.sp
            )
            Text(
                text = "Sign in",
                color = appPurple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    // include role argument because AppNavGraph expects "signin/{role}"
                    navController.navigate("signin/driver")
                }
            )
        }
    }
}

@Composable
fun DriverSignupOtpInputRow(
    otp: List<String>,
    otpErrorMessage: String? = null,
    shouldShakeOtp: Boolean = false,
    onOtpChange: (List<String>) -> Unit,
    keyboardController: SoftwareKeyboardController?,
    focusManager: FocusManager,
    onClearError: () -> Unit,
    onAutoVerify: () -> Unit,
    isSending: Boolean = false,
    focusRequester: FocusRequester
) {
    val safeOtp =
        if (otp.size == Constants.OTP_LENGTH) otp else List(Constants.OTP_LENGTH) { "" }
    val scope = rememberCoroutineScope()
    val offsetX by animateDpAsState(
        targetValue = if (shouldShakeOtp) 8.dp else 0.dp
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .offset(x = offsetX)
        ) {
            safeOtp.forEachIndexed { index, digit ->
                OutlinedTextField(
                    value = digit,
                    onValueChange = { newValue ->
                        if (newValue.length <= 1 && newValue.all { ch -> ch.isDigit() }) {
                            val newOtp = safeOtp.toMutableList()
                            newOtp[index] = newValue
                            onOtpChange(newOtp)

                            if (newValue.isNotEmpty() && index < Constants.OTP_LENGTH - 1) {
                                focusManager.moveFocus(FocusDirection.Next)
                            }

                            if (newValue.isNotEmpty() && index == Constants.OTP_LENGTH - 1) {
                                scope.launch {
                                    delay(50)
                                    keyboardController?.hide()
                                    // Removed focusManager.clearFocus() to prevent jumping to Driver Name
                                }
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .size(50.dp)
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onClearError()
                            }
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == Constants.OTP_LENGTH - 1) ImeAction.Done else ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        cursorColor = Color(0xFF800080),
                        focusedBorderColor = Color(0xFF800080),
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }
        }
    }
}