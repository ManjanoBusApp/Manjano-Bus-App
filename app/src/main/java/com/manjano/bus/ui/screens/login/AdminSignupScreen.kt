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
                val filtered = newValue.text.filter { ch -> ch.isLetter() || ch.isWhitespace() }
                adminName = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
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
                    if (focusState.isFocused) {
                        adminError = false
                    }
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
                val filtered = newValue.text.filter { ch -> ch.isDigit() }
                idNumber = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
            },
            placeholder = { Text("ID Number") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(idFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        idError = false
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = idError
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
                    if (focusState.isFocused) {
                        schoolError = false
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = schoolError
        )
        if (schoolError) Text("Please Enter the School Name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Current Position
        OutlinedTextField(
            value = currentPosition,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { ch -> ch.isLetter() || ch.isWhitespace() }
                currentPosition = TextFieldValue(
                    text = filtered,
                    selection = TextRange(
                        start = newValue.selection.start.coerceIn(0, filtered.length),
                        end = newValue.selection.end.coerceIn(0, filtered.length)
                    )
                )
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
                    if (focusState.isFocused) {
                        positionError = false
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = positionError
        )
        if (positionError) Text("Please fill this section", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        val dummyFocusRequester = remember { FocusRequester() }
        Box(
            modifier = Modifier
                .size(0.dp)
                .focusRequester(dummyFocusRequester)
        )

        // Phone input
        // 1. Ensure these are defined at the TOP of your Screen Composable
        val keyboardController =
            androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

        // 2. Pass them into the function below
        PhoneInputSection(
            selectedCountry = selectedCountry,
            phoneNumber = phoneNumber,
            onCountrySelected = { selectedCountry = it },
            onPhoneNumberChange = {
                phoneNumber = it
                // Disable real-time error reporting while typing
                phoneError = false

                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(it, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }

                // Keyboard will ONLY hide when the logic confirms the number is fully correct
                if (isValidPhone) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            },
            showError = phoneError,
            phoneFocusRequester = phoneFocusRequester,
            keyboardController = keyboardController,
            focusManager = focusManager
        )

        if (phoneError) {
            Text(
                "Please enter a valid phone number",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
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
                keyboardController?.hide()

                // Validate all fields ONCE when Send Code is clicked
                adminError = adminName.text.isEmpty()
                idError = idNumber.text.isEmpty()
                schoolError = schoolName.text.isEmpty()
                positionError = currentPosition.text.isEmpty()
                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }
                phoneError = phoneNumber.isEmpty() || !isValidPhone

                if (!adminError && !idError && !schoolError && !positionError && !phoneError) {
                    signupViewModel.requestOtp()
                    showOtpMessage = true
                    scope.launch {
                        delay(100)
                        if (scrollState.maxValue > 0) {
                            otpFocusRequester.requestFocus()
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                } else {
                    // Focus the first field with an error
                    when {
                        adminError -> adminFocusRequester.requestFocus()
                        idError -> idFocusRequester.requestFocus()
                        schoolError -> schoolFocusRequester.requestFocus()
                        positionError -> positionFocusRequester.requestFocus()
                        phoneError -> phoneFocusRequester.requestFocus()
                    }
                }
            },
        )
        ResendTimerSection(
            timer = uiState.resendTimerSeconds,
            canResend = uiState.canResendOtp,
            onResendClick = { signupViewModel.resendOtp() }
        )
        if (showOtpMessage) {
            Text(
                text = "Please enter the 4-digits sent to your SMS",
                color = Color.Black,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (showOtpErrorMessage) {
            Text(
                text = "Incorrect code. Please press resend code",
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
                showOtpMessage = false
                digits.forEachIndexed { index, digit ->
                    signupViewModel.onOtpDigitChange(index, digit)
                }
                if (digits.all { it.isNotEmpty() }) {
                    val enteredOtp = digits.joinToString("")
                    if (enteredOtp == Constants.TEST_OTP) {
                        keyboardController?.hide()
                        signupViewModel.setOtpValid(true)
                    } else {
                        signupViewModel.setOtpValid(false)
                        showOtpErrorMessage = true
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

                adminError = adminName.text.isEmpty()
                idError = idNumber.text.isEmpty()
                schoolError = schoolName.text.isEmpty()
                positionError = currentPosition.text.isEmpty()
                phoneError = phoneNumber.isEmpty()

                if (!adminError && !idError && !schoolError && !positionError && !phoneError && uiState.otpDigits.joinToString(
                        ""
                    ) == Constants.TEST_OTP
                ) {
                    navController.navigate("admin_dashboard") {
                        popUpTo("admin_signup") { inclusive = true }
                    }
                }
            },
            enabled = adminName.text.isNotEmpty() &&
                    idNumber.text.isNotEmpty() &&
                    schoolName.text.isNotEmpty() &&
                    currentPosition.text.isNotEmpty() &&
                    phoneNumber.isNotEmpty() &&
                    uiState.otpDigits.joinToString("") == Constants.TEST_OTP,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = continueShakeOffset.floatValue.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appPurple,
                disabledContainerColor = appPurple.copy(alpha = 0.5f)
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
                    navController.navigate("signin/admin")
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