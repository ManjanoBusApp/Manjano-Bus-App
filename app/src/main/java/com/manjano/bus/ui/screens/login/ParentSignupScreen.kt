package com.manjano.bus.ui.screens.login

import android.content.Context
import android.util.Log
import android.util.Patterns
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun SignupOtpInputRow(
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
                                // Small delay to ensure the digit is processed before hiding keyboard
                                scope.launch {
                                    delay(50) // Brief delay
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
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
    var parentName by remember { mutableStateOf(TextFieldValue("")) }
    var studentName by remember { mutableStateOf(TextFieldValue("")) }
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var parentError by remember { mutableStateOf(false) }
    var studentError by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf(CountryRepository.countries.first()) }
    var phoneNumber by remember { mutableStateOf("") }
    var showOtpMessage by remember { mutableStateOf(false) }
    var showOtpErrorMessage by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val otpFocusRequester = remember { FocusRequester() }
    val parentFocusRequester = remember { FocusRequester() }
    val studentFocusRequester = remember { FocusRequester() }
    val emailFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

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
            text = "Parent Sign Up",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(top = 16.dp, bottom = 24.dp)
        )

        // Parent Name
        OutlinedTextField(
            value = parentName,
            onValueChange = { newValue ->
                parentName = newValue.copy(
                    text = newValue.text.filter { it.isLetter() || it.isWhitespace() }
                )
            },
            placeholder = { Text("Parent's Full Name") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(parentFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) parentError = false
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = parentError
        )

        if (parentError) Text("Please fill your name", color = Color.Red, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))


// ==================== DYNAMIC MULTI-CHILD SECTION (FINAL COMPACT VERSION) ====================
        var childrenNames by remember { mutableStateOf(listOf(TextFieldValue(""))) }
        var childErrors by remember { mutableStateOf(listOf(false)) }

// Add new child
        val addChild = {
            val newChildren = childrenNames.toMutableList().apply { add(TextFieldValue("")) }
            val newErrors = childErrors.toMutableList().apply { add(false) }
            childrenNames = newChildren
            childErrors = newErrors
        }

// Remove child
        val removeChild = { index: Int ->
            if (childrenNames.size > 1) {
                childrenNames = childrenNames.filterIndexed { i, _ -> i != index }
                childErrors = childErrors.filterIndexed { i, _ -> i != index }
            }
        }

// Column for all child fields
        Column {
            childrenNames.forEachIndexed { index, childName ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    val childFocusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { newValue ->
                            val filtered = newValue.text.filter { it.isLetter() || it.isWhitespace() }
                            val updatedChildren = childrenNames.toMutableList()
                            updatedChildren[index] = newValue.copy(text = filtered)
                            childrenNames = updatedChildren
                        },
                        placeholder = { Text("Child 'First.Middle.Last' Name") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Words
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(childFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    val updatedErrors = childErrors.toMutableList()
                                    updatedErrors[index] = false
                                    childErrors = updatedErrors
                                }
                            },
                        textStyle = TextStyle(fontSize = 16.sp),
                        shape = RoundedCornerShape(12.dp),
                        isError = childErrors[index]
                    )

                    // Error message
                    if (childErrors[index]) {
                        Text(
                            "Please fill child's name",
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }

                    // "+ Add" button below, aligned to end
                    if (index == childrenNames.lastIndex) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 0.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = addChild,
                                modifier = Modifier.height(40.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "+ Add Another Child",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    // Remove button below (indented)
                    if (childrenNames.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            TextButton(
                                onClick = { removeChild(index) },
                                modifier = Modifier.height(44.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "- Remove Child",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Red
                                )
                            }
                        }
                    }

                    // Spacer between child sections
                    if (index < childrenNames.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
            },
            placeholder = { Text("name@email.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        emailError = false
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = emailError
        )

        if (emailError) Text(
            "Please enter a valid email address",
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
        val phoneFocusRequester = remember { FocusRequester() }

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
                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(it, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }
                val requiredLength = PhoneNumberUtils.getExpectedLength(selectedCountry.isoCode)
                phoneError = it.isNotEmpty() && it.length == requiredLength && !isValidPhone
                if (it.length == requiredLength) {
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
                containerColor = Color.Red,   // Red background for error
                contentColor = Color.White     // White text for contrast
            )
        }

        ActionRow(
            rememberMe = uiState.rememberMe,
            isSendingOtp = uiState.isSendingOtp,
            onRememberMeChange = signupViewModel::onRememberMeChange,
            onGetCodeClick = {
                keyboardController?.hide()

                // Validate all fields ONCE when Send Code is clicked
                parentError = parentName.text.isEmpty()

                // Validate ALL children names - FORCE UPDATE childErrors!
                childErrors =
                    childrenNames.indices.map { index -> childrenNames[index].text.isEmpty() }
                val hasEmptyChild = childErrors.any { it }
                studentError = hasEmptyChild
                emailError =
                    email.text.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.text)
                        .matches()

                val isValidPhone = try {
                    PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                } catch (e: Exception) {
                    false
                }
                phoneError = phoneNumber.isEmpty() || !isValidPhone

                if (!parentError && !studentError && !emailError && !phoneError) {
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
                        parentError -> parentFocusRequester.requestFocus()
                        childErrors.any { it } -> {
                            // Focus FIRST EMPTY child
                            val firstEmptyChildIndex = childErrors.indexOfFirst { it }
                            if (firstEmptyChildIndex >= 0) {
                                // Focus the first empty child field
                                if (firstEmptyChildIndex == 0) {
                                    studentFocusRequester.requestFocus()
                                }
                            }
                        }

                        emailError -> emailFocusRequester.requestFocus()
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
        if (showOtpMessage) { // → New: Show message only after Send Code
            Text(
                text = "Please enter the 4-digits sent to your SMS",
                color = Color.Black,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (showOtpErrorMessage) { // → New: Show invalid OTP message
            Text(
                text = "Incorrect code. Please press resend code",
                color = Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        SignupOtpInputRow(
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
                        showOtpErrorMessage = true // → New: Show invalid OTP message
                    }
                } else {
                    showOtpErrorMessage =
                        false // → New: Hide invalid OTP message if not all digits entered
                }
            },


            keyboardController = keyboardController,
            focusManager = focusManager, // NEW ARGUMENT
            onClearError = {},
            onAutoVerify = {},
            isSending = uiState.isOtpSubmitting,
            focusRequester = otpFocusRequester
        )

        Spacer(modifier = Modifier.height(12.dp))

        val isOtpValid by signupViewModel.isOtpValid.collectAsState()

        Button(
            onClick = {
                parentError = parentName.text.isEmpty()
                val hasEmptyChild = childrenNames.any { it.text.isEmpty() }
                childErrors = childrenNames.indices.map { index -> childrenNames[index].text.isEmpty() }
                studentError = hasEmptyChild
                emailError = email.text.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.text).matches()
                phoneError = phoneNumber.isEmpty()

                val enteredOtp = uiState.otpDigits.joinToString("")
                val isTestOtp = enteredOtp == Constants.TEST_OTP

                if (!parentError && !studentError && !emailError && !phoneError && isTestOtp) {
                    // CRITICAL FIX: The logic must now call saveUserNames() to create the
                    // parent-specific Firebase nodes before navigation.

                    Log.d("🔥", "TEST OTP used – initiating saveUserNames for Firebase setup.")

                    // 1. Initiate Firebase Save (asynchronously creates /parents/{key}/children)
                    signupViewModel.saveUserNames(
                        parentName.text,
                        childrenNames.joinToString(",") { it.text },
                        context
                    )

                    // 2. Save locally for session persistence
                    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("parent_name", parentName.text)
                        putString("children_names", childrenNames.joinToString(",") { it.text })
                    }.apply()

                    // 3. Navigate to dashboard (relying on asynchronous save starting immediately)
                    val encodedParent = URLEncoder.encode(parentName.text, StandardCharsets.UTF_8.toString())
                    val encodedChildren = URLEncoder.encode(
                        childrenNames.joinToString(",") { it.text },
                        StandardCharsets.UTF_8.toString()
                    )
                    val encodedStatus = URLEncoder.encode("On Route", StandardCharsets.UTF_8.toString())
                    navController.navigate("parent_dashboard/$encodedParent/$encodedChildren/$encodedStatus") {
                        popUpTo("signup") { inclusive = true }
                    }
                }
                // If it's a real OTP (when you go live) and not the test one, this block would handle it.
                // We leave the block here but ensure the TEST OTP block is self-contained.
                else if (!parentError && !studentError && !emailError && !phoneError && !isTestOtp && enteredOtp.isNotEmpty()) {
                    // This block is typically for real production OTP validation flow.
                    // For now, it remains empty or follows a real OTP verification path.
                }
            },
            enabled = parentName.text.isNotEmpty() &&
                    childrenNames.all { it.text.isNotEmpty() } &&
                    email.text.isNotEmpty() &&
                    Patterns.EMAIL_ADDRESS.matcher(email.text).matches() &&
                    phoneNumber.isNotEmpty() &&
                    uiState.otpDigits.joinToString("") == Constants.TEST_OTP,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = appPurple)
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
                    // include role argument because AppNavGraph expects "signin/{role}"
                    navController.navigate("signin/parent")
                }
            )
        }
    }
}





