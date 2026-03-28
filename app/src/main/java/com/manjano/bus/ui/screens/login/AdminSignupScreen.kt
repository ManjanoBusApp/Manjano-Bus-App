package com.manjano.bus.ui.screens.login

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import kotlin.ranges.coerceIn
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.focus.FocusManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.content.Context
import android.util.Log
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSignupScreen(
    navController: NavController,
    signupViewModel: SignUpViewModel = viewModel()
) {
    val appPurple = Color(0xFF800080)
    val uiState by signupViewModel.uiState.collectAsState()

    // Snackbar setup for OTP errors
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
    var adminName by remember { mutableStateOf(TextFieldValue("")) }
    var idNumber by remember { mutableStateOf(TextFieldValue("")) }
    var schoolName by remember { mutableStateOf(TextFieldValue("")) }
    var currentPosition by remember { mutableStateOf(TextFieldValue("")) }
    var adminError by remember { mutableStateOf(false) }
    var idError by remember { mutableStateOf(false) }
    var schoolError by remember { mutableStateOf(false) }
    var positionError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryRepository.countries.first()) }
    var phoneNumber by remember { mutableStateOf("") }
    var showOtpMessage by remember { mutableStateOf(false) }
    var showOtpErrorMessage by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val otpFocusRequester = remember { FocusRequester() }
    val adminFocusRequester = remember { FocusRequester() }
    val idFocusRequester = remember { FocusRequester() }
    val schoolFocusRequester = remember { FocusRequester() }
    val positionFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showUnauthorizedError by remember { mutableStateOf(false) }
    var unauthorizedErrorMessage by remember { mutableStateOf("Not authorized. Admin access only.") }
    var phoneChangeTrigger by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .imePadding()
            .navigationBarsPadding()
            .systemBarsPadding()
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
            text = "Admin Sign Up",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 16.dp, bottom = 24.dp)
        )

        // Admin Name
        OutlinedTextField(
            value = adminName,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isLetter() || it.isWhitespace() }
                adminName = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )

                // Only hide error if the user types at least one character
                if (filtered.isNotEmpty()) adminError = false
            },
            placeholder = { Text("Your Full Name") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(adminFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && adminName.text.isNotEmpty()) adminError = false
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = adminError
        )
        if (adminError) Text("Please fill your name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

// ID Number
        OutlinedTextField(
            value = idNumber,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isDigit() }
                idNumber = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                idError = false // hide error as soon as user types
            },
            placeholder = { Text("ID Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(idFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) idError = false
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = idError
        )
        if (idError) Text("Please fill ID number", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

// School Name
        OutlinedTextField(
            value = schoolName,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isLetter() || it.isWhitespace() }
                schoolName = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                schoolError = false // hide error as soon as user types
            },
            placeholder = { Text("School Name") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(schoolFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) schoolError = false
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = schoolError
        )
        if (schoolError) Text("Please fill your school name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

// Current Position
        OutlinedTextField(
            value = currentPosition,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isLetter() || it.isWhitespace() }
                currentPosition = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
                positionError = false // hide error as soon as user types
            },
            placeholder = { Text("Current Position") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(positionFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) positionError = false
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = positionError
        )
        if (positionError) Text(
            "Please fill your current position",
            color = Color.Red,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        val dummyFocusRequester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .size(0.dp)
                .focusRequester(dummyFocusRequester)
        )

        // Phone input with admin verification
        Box(modifier = Modifier.fillMaxWidth()) {
            PhoneInputSection(
                selectedCountry = selectedCountry,
                phoneNumber = phoneNumber,
                onCountrySelected = { selectedCountry = it },
                onPhoneNumberChange = {
                    phoneNumber = it
                    phoneError = false
                    showUnauthorizedError = false
                    unauthorizedErrorMessage = "Not authorized. Admin access only."
                    phoneChangeTrigger += 1   // forces recomposition
                },
                showError = phoneError,
                onShowErrorChange = { phoneError = it },
                phoneFocusRequester = phoneFocusRequester,
                keyboardController = keyboardController,
                focusManager = focusManager
            )
        }

// Move "Not authorized..." message below the Box
        if (showUnauthorizedError) {
            Text(
                text = unauthorizedErrorMessage,
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(top = 4.dp, start = 4.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SnackbarHost(
            hostState = snackbarHostState
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color.Red,
                contentColor = Color.White
            )
        }

        ActionRow(
            rememberMe = uiState.rememberMe,
            isSendingOtp = uiState.isSendingOtp,
            onRememberMeChange = signupViewModel::onRememberMeChange,
            onGetCodeClick = {
                run {
                    keyboardController?.hide()
                    focusManager.clearFocus()

                    // Reset all errors first
                    adminError = adminName.text.isEmpty()
                    idError = idNumber.text.isEmpty()
                    schoolError = schoolName.text.isEmpty()
                    positionError = currentPosition.text.isEmpty()
                    phoneError = phoneNumber.isEmpty()
                    showOtpErrorMessage = false

                    // Focus the first field with an error (if any)
                    when {
                        adminError -> adminFocusRequester.requestFocus()
                        idError -> idFocusRequester.requestFocus()
                        schoolError -> schoolFocusRequester.requestFocus()
                        positionError -> positionFocusRequester.requestFocus()
                        phoneError -> phoneFocusRequester.requestFocus()
                    }

                    // Stop here if any errors
                    if (adminError || idError || schoolError || positionError || phoneError) return@run

                    // Normalize phone number
                    var normalizedPhone = phoneNumber.filter { it.isDigit() }

                    // Handle common Kenyan formats: 07xxxxxxxx, 7xxxxxxxx, 254xxxxxxxxx, 011xxxxxxx
                    normalizedPhone = when {
                        normalizedPhone.startsWith("254") -> "0" + normalizedPhone.substring(3)
                        normalizedPhone.startsWith("7") && normalizedPhone.length == 9 -> "0$normalizedPhone"
                        normalizedPhone.startsWith("07") -> normalizedPhone
                        normalizedPhone.startsWith("011") -> normalizedPhone
                        else -> normalizedPhone
                    }

                    // Check Firestore for admin role and signup status
                    signupViewModel.getAdminByMobile(normalizedPhone) { adminData ->
                        if (adminData == null) {
                            // Number not in Firestore - not authorized to sign up
                            unauthorizedErrorMessage = "Not authorized. Admin access only."
                            showUnauthorizedError = true
                            phoneFocusRequester.requestFocus()
                        } else {
                            // Number exists in Firestore - check if they have completed signup
                            val name = adminData["name"] as? String
                            val idNumber = adminData["idNumber"] as? String
                            val schoolName = adminData["schoolName"] as? String
                            val position = adminData["position"] as? String

                            val hasCompletedSignup = !name.isNullOrBlank() &&
                                    !idNumber.isNullOrBlank() &&
                                    !schoolName.isNullOrBlank() &&
                                    !position.isNullOrBlank()

                            if (hasCompletedSignup) {
                                // Already signed up - should sign in instead
                                unauthorizedErrorMessage = "You already have an account. Please sign in."
                                showUnauthorizedError = true
                                phoneFocusRequester.requestFocus()
                            } else {
                                // Number exists but not completed signup - allow sign up
                                showUnauthorizedError = false
                                signupViewModel.hideSmsMessage() // hide previous SMS prompt
                                signupViewModel.requestOtp()      // request OTP

                                scope.launch {
                                    delay(300)
                                    otpFocusRequester.requestFocus()
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                    signupViewModel.showSmsMessage() // trigger SMS visibility
                                }
                            }
                        }
                    }
                }
            },
        )
        if (showOtpMessage) {
            ResendTimerSection(
                timer = uiState.resendTimerSeconds,
                canResend = uiState.canResendOtp,
                onResendClick = { signupViewModel.resendOtp() }
            )
        }
        // Collect uiState in composable
        val uiState by signupViewModel.uiState.collectAsState()

// Show SMS message only if uiState.showSmsMessage is true
        AnimatedVisibility(visible = uiState.showSmsMessage) {
            Text(
                text = "Check SMS for ${Constants.OTP_LENGTH}-digit code",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (showOtpErrorMessage) {
            Text(
                text = "Incorrect code. Send code again.",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        AdminSignupOtpInputRow(
            otp = uiState.otpDigits,
            otpErrorMessage = uiState.otpErrorMessage,
            shouldShakeOtp = uiState.shouldShakeOtp,
            onOtpChange = { digits: List<String> ->
                signupViewModel.hideSmsMessage() // hide SMS prompt immediately when typing

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
                            repeat(Constants.OTP_LENGTH) { idx ->
                                signupViewModel.onOtpDigitChange(idx, "")
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
            onClearError = {},
            onAutoVerify = {},
            isSending = uiState.isOtpSubmitting,
            focusRequester = otpFocusRequester
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isOtpValid by signupViewModel.isOtpValid.collectAsState()
        val continueShakeOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

        var isPhoneAuthorized by remember { mutableStateOf(false) }

        val normalizedPhone by remember(phoneNumber) {
            derivedStateOf {
                val digits = phoneNumber.filter { it.isDigit() }
                when {
                    digits.startsWith("254") -> "0" + digits.substring(3)
                    digits.startsWith("7") && digits.length == 9 -> "0$digits"
                    digits.startsWith("07") -> digits
                    digits.startsWith("011") -> digits
                    else -> digits
                }
            }
        }

        LaunchedEffect(normalizedPhone) {
            if (normalizedPhone.length == 10) {
                signupViewModel.getAdminByMobile(normalizedPhone) { adminData ->
                    if (adminData == null) {
                        isPhoneAuthorized = false
                        unauthorizedErrorMessage = "Not authorized. Admin access only."
                        showUnauthorizedError = true
                    } else {
                        val name = adminData["name"] as? String
                        val idNumber = adminData["idNumber"] as? String
                        val schoolName = adminData["schoolName"] as? String
                        val position = adminData["position"] as? String

                        val hasCompletedSignup = !name.isNullOrBlank() &&
                                !idNumber.isNullOrBlank() &&
                                !schoolName.isNullOrBlank() &&
                                !position.isNullOrBlank()

                        if (hasCompletedSignup) {
                            isPhoneAuthorized = false
                            unauthorizedErrorMessage = "You already have an account. Please sign in."
                            showUnauthorizedError = true
                        } else {
                            isPhoneAuthorized = true
                            showUnauthorizedError = false
                        }
                    }
                }
            } else {
                isPhoneAuthorized = false
                showUnauthorizedError = false
            }
        }
        val isContinueEnabled by remember(
            adminName.text,
            idNumber.text,
            schoolName.text,
            currentPosition.text,
            normalizedPhone,
            isPhoneAuthorized,
            showOtpErrorMessage,
            uiState.otpDigits
        ) {
            derivedStateOf {
                adminName.text.isNotBlank() &&
                        idNumber.text.isNotBlank() &&
                        schoolName.text.isNotBlank() &&
                        currentPosition.text.isNotBlank() &&
                        normalizedPhone.length == 10 &&
                        isPhoneAuthorized &&
                        !showOtpErrorMessage &&
                        uiState.otpDigits.size == Constants.OTP_LENGTH &&
                        uiState.otpDigits.all { it.isNotBlank() } &&
                        uiState.otpDigits.joinToString("") == Constants.TEST_OTP
            }
        }
        Button(
            onClick = {
                scope.launch {
                    repeat(2) {
                        continueShakeOffset.floatValue = 4f
                        delay(40)
                        continueShakeOffset.floatValue = -4f
                        delay(40)
                    }
                    continueShakeOffset.floatValue = 0f
                }
                // All navigation safety checks are already handled reactively by isContinueEnabled
                if (isContinueEnabled) {
                    // 🔥 SAVE ADMIN PROFILE TO FIRESTORE
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val now = java.util.Calendar.getInstance().time
                    val dateFormatter = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
                    val timeFormatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                    val createdOn = dateFormatter.format(now)
                    val createdTime = timeFormatter.format(now)

                    val adminData = mapOf(
                        "name" to adminName.text.trim(),
                        "idNumber" to idNumber.text.trim(),
                        "schoolName" to schoolName.text.trim(),
                        "position" to currentPosition.text.trim(),
                        "mobileNumber" to normalizedPhone,
                        "role" to "admin",
                        "createdOn" to createdOn,
                        "createdTime" to createdTime,
                        "active" to true  // 🔥 All new admins are active by default
                    )

                    // Use phone number as document ID
                    db.collection("admins")
                        .document(normalizedPhone)
                        .set(adminData)
                        .addOnSuccessListener {
                            Log.d("🔥", "Admin profile saved successfully")
                        }
                        .addOnFailureListener { e ->
                            Log.e("🔥", "Failed to save admin profile", e)
                        }

                    // Save session for foreground check
                    val sessionPrefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                    sessionPrefs.edit().apply {
                        putString("user_role", "admin")
                        putString("user_phone", normalizedPhone)
                        putBoolean("is_signed_in", true)
                        apply()
                    }

                    val encodedMobile = URLEncoder.encode(normalizedPhone, StandardCharsets.UTF_8.toString())
                    navController.navigate("admin_dashboard/$encodedMobile") {
                        popUpTo("admin_signup") { inclusive = true }
                    }
                }
            },
            enabled = isContinueEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = continueShakeOffset.floatValue.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appPurple,
                disabledContainerColor = Color.LightGray
            )
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp)
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
                    navController.navigate("admin_signin") {
                        // Optional: clear back stack so user can't go back to signup after signin
                        popUpTo("admin_signup") { inclusive = false }
                    }
                }
            )
        }
    }
}

@Composable
fun AdminSignupOtpInputRow(
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
                                }
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .size(50.dp)
                        .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == Constants.OTP_LENGTH - 1) ImeAction.Done else ImeAction.Next
                    )
                )
            }
        }
    }
}