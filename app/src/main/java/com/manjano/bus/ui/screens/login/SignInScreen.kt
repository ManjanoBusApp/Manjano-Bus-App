package com.manjano.bus.ui.screens.login

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.manjano.bus.R
import com.manjano.bus.models.Country
import com.manjano.bus.models.CountryRepository
import com.manjano.bus.utils.Constants
import com.manjano.bus.utils.PhoneNumberUtils
import com.manjano.bus.viewmodel.SignInViewModel
import com.manjano.bus.viewmodel.SignUpViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import androidx.compose.ui.focus.onFocusChanged



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    navController: NavController,
    viewModel: SignInViewModel,
    signUpViewModel: SignUpViewModel,
    role: String
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(role) {
        viewModel.setUserRole(role)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val otpFocusRequester = remember { FocusRequester() }
    var showValidationError by rememberSaveable { mutableStateOf(false) }
    val phoneFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val signUpUiState by signUpViewModel.uiState.collectAsStateWithLifecycle()

    // NEW: Firestore phone existence states (mirroring signup)
    val isPhoneAllowed by viewModel.isPhoneAllowed.collectAsStateWithLifecycle()
    var phoneErrorText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.navigateToDashboard) {
        if (uiState.navigateToDashboard) {

            runCatching {

                if (role == "driver") {
                    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    val driverName = prefs.getString("driver_name", "") ?: "Driver"
                    val driverPhone = prefs.getString("driver_phone", "") ?: ""

                    val encodedDriverName =
                        URLEncoder.encode(driverName, StandardCharsets.UTF_8.toString())
                    navController.navigate("driver_dashboard/$driverPhone/$encodedDriverName") {
                        popUpTo("signin/driver") { inclusive = true }
                    }

                } else if (role == "parent") {
                    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    val parentName = prefs.getString("parent_name", "") ?: ""
                    val childrenNames = prefs.getString("children_names", "") ?: ""
                    val parentFirstName = parentName.split(" ").firstOrNull() ?: parentName

                    val encodedParent =
                        URLEncoder.encode(parentFirstName, StandardCharsets.UTF_8.toString())
                    val encodedChildren =
                        URLEncoder.encode(childrenNames, StandardCharsets.UTF_8.toString())
                    val encodedStatus =
                        URLEncoder.encode("On Route", StandardCharsets.UTF_8.toString())

                    navController.navigate("parent_dashboard/$encodedParent/$encodedChildren/$encodedStatus") {
                        popUpTo("signin/parent") { inclusive = true }
                    }
                }
            }.onFailure { throwable ->
                android.util.Log.e("NavigationError", "Failed to navigate to dashboard", throwable)
            }

            viewModel.onNavigationConsumed()
        }
    }
    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            phoneErrorText = ""
                            // Optional: reset allowed state if you want fresh check on re-focus
                            // viewModel.resetPhoneAllowed()
                        }
                    }
            ) {
                PhoneInputSection(
                    selectedCountry = uiState.selectedCountry,
                    phoneNumber = uiState.rawPhoneInput,
                    onCountrySelected = viewModel::onCountrySelected,
                    onPhoneNumberChange = {
                        viewModel.onPhoneNumberChange(it)
                        phoneErrorText = ""  // Clear error on typing
                    },
                    showError = false,
                    onShowErrorChange = { /* ignore */ },
                    phoneFocusRequester = phoneFocusRequester,
                    keyboardController = keyboardController,
                    focusManager = focusManager
                )
            }

            // Show Firestore-based error (identical to signup style)
            if (phoneErrorText.isNotEmpty()) {
                Text(
                    text = phoneErrorText,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // NEW: Trigger Firestore check when phone number or country changes
            LaunchedEffect(uiState.rawPhoneInput, uiState.selectedCountry) {
                if (uiState.rawPhoneInput.isNotBlank()) {
                    viewModel.checkPhoneNumberInFirestore(
                        phone = uiState.rawPhoneInput,
                        countryIso = uiState.selectedCountry.isoCode
                    )
                } else {
                    viewModel.resetPhoneAllowed()
                    phoneErrorText = ""
                }
            }

            val snackbarHostState = remember { SnackbarHostState() }

            SnackbarHost(snackbarHostState)

            ActionRow(
                rememberMe = uiState.rememberMe,
                isSendingOtp = uiState.isSendingOtp,
                onRememberMeChange = viewModel::onRememberMeChange,
                onGetCodeClick = {
                    val isFormatValid = PhoneNumberUtils.isValidNumber(
                        uiState.rawPhoneInput,
                        uiState.selectedCountry.isoCode
                    )

                    val isCompleteEnough = uiState.rawPhoneInput.isNotBlank() &&
                            uiState.rawPhoneInput.length >= 8  // basic length check to avoid false "incomplete"

                    scope.launch {
                        // Wait briefly if Firestore check is still pending
                        if (isPhoneAllowed == null && uiState.rawPhoneInput.isNotBlank()) {
                            delay(800)  // Allow time for async Firestore response
                        }

                        // Clear previous errors first
                        phoneErrorText = ""

                        if (!isFormatValid || !isCompleteEnough) {
                            // Format or incomplete → show "Invalid phone number" after click
                            phoneErrorText = "Invalid phone number"
                            phoneFocusRequester.requestFocus()
                            return@launch
                        }

                        // Now check Firestore existence (only if format is OK)
                        if (isPhoneAllowed != true) {
                            phoneErrorText = "Can't proceed, contact the school"
                            phoneFocusRequester.requestFocus()
                            return@launch
                        }

                        // All good → proceed
                        phoneErrorText = ""
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

                    viewModel.hideSmsMessage()

                    digits.forEachIndexed { index, digit ->
                        viewModel.onOtpDigitChange(index, digit) {
                            keyboardController?.hide()
                        }
                    }

                    // When all digits are filled check if OTP matches the test code
                    if (digits.all { it.isNotEmpty() }) {

                        val enteredOtp = digits.joinToString("")

                        if (enteredOtp == Constants.TEST_OTP) {

                            keyboardController?.hide()
                            viewModel.setOtpValid(true)

                        } else {

                            viewModel.setOtpValid(false)

                            scope.launch {

                                delay(1000) // allow user to see wrong code briefly

                                repeat(Constants.OTP_LENGTH) { index ->
                                    viewModel.onOtpDigitChange(index, "") {
                                        keyboardController?.hide()
                                    }
                                }

                                focusManager.clearFocus()

                                delay(50)

                                otpFocusRequester.requestFocus()
                            }
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
                    text = uiState.otpErrorMessage ?: "Incorrect code. Send code again.",
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


//     Auto-refocus first OTP box + show keyboard after failed verification

            LaunchedEffect(uiState.showOtpError, uiState.otpDigits) {
                if (uiState.showOtpError == true &&
                    uiState.otpDigits.all { it.isEmpty() } &&
                    uiState.otpErrorMessage?.isNotBlank() == true
                ) {

                    // Give shake animation + error visibility time to settle
                    delay(180)           // ← tune between 120–300 ms if needed

                    // Important sequence from the working file
                    focusManager.clearFocus()           // reset any stale focus
                    delay(60)                           // tiny breath

                    otpFocusRequester.requestFocus()    // request on first box
                    keyboardController?.show()          // explicitly show keyboard
                }
            }
            val continueShakeOffset = remember { mutableFloatStateOf(0f) }
            val haptic = LocalHapticFeedback.current

            val isPhoneValid = try {
                val proto = PhoneNumberUtil.getInstance()
                    .parse(uiState.rawPhoneInput, uiState.selectedCountry.isoCode)
                PhoneNumberUtil.getInstance()
                    .isValidNumberForRegion(proto, uiState.selectedCountry.isoCode)
            } catch (e: Exception) {
                false
            }


// 1. Are all 4 digits filled?
            val isOtpComplete by remember(uiState.otpDigits) {
                derivedStateOf { uiState.otpDigits.all { it.isNotEmpty() } }
            }

// 2. Phone allowed (you already have this – keep it unchanged)
            val phoneAllowed by remember(isPhoneAllowed) {
                derivedStateOf { isPhoneAllowed == true }
            }

// 3. Final button enabled state – NO isOtpCorrect condition
            val isContinueEnabled by remember(
                isOtpComplete,
                phoneAllowed,
                uiState.isOtpSubmitting
            ) {
                derivedStateOf {
                    isOtpComplete &&
                            phoneAllowed &&
                            !uiState.isOtpSubmitting
                }
            }

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
                enabled = isContinueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isContinueEnabled) Color(0xFF800080) else Color.LightGray,
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

            SignUpFooter(onSignUpClick = {
                when (role) {
                    "driver" -> navController.navigate("driver_signup")
                    "admin" -> navController.navigate("admin_signup")
                    else -> navController.navigate("signup")
                }
            })

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ___________________Phone Input Section _____________________
@Composable
fun PhoneInputSection(
    selectedCountry: Country,
    phoneNumber: String,
    onCountrySelected: (Country) -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    showError: Boolean,
    onShowErrorChange: (Boolean) -> Unit,
    phoneFocusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    focusManager: androidx.compose.ui.focus.FocusManager
) {

    var expanded by remember { mutableStateOf(false) }
    val appPurple = Color(0xFF800080)
    var localPhone by remember { mutableStateOf(TextFieldValue(phoneNumber)) }
    var isPhoneFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isPhoneFieldFocused) {
        if (isPhoneFieldFocused) {
            delay(80)
            keyboardController?.show()
        }
    }
    val phoneUtil = PhoneNumberUtil.getInstance()
    var lastCountry by remember { mutableStateOf(selectedCountry) }

    LaunchedEffect(selectedCountry) {
        if (selectedCountry != lastCountry) {
            localPhone = TextFieldValue(
                text = "",
                selection = TextRange(0) // ensure cursor is at start
            )
            onPhoneNumberChange("")
            phoneFocusRequester.requestFocus()
            lastCountry = selectedCountry
        }
    }


    val displayError by remember(localPhone.text, showError, selectedCountry) {
        derivedStateOf {
            showError && !PhoneNumberUtils.isValidNumber(localPhone.text, selectedCountry.isoCode)
        }
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 0.dp) // Keeps it flush with the top of the phone input
                .width(96.dp)
                .height(56.dp)
        ) {

            OutlinedTextField(
                value = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                onValueChange = {},
                readOnly = true,
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(selectedCountry.flag, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(selectedCountry.dialCode, fontSize = 16.sp)
                    }
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select country",
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { expanded = !expanded }
                        )
                    )
                },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(fontSize = 16.sp, lineHeight = 20.sp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = appPurple,
                    unfocusedBorderColor = Color.Gray
                )
            )

            var searchQuery by remember { mutableStateOf("") }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    searchQuery = ""
                },
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .width(280.dp),
                properties = PopupProperties(focusable = true)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp),
                    shape = RoundedCornerShape(8.dp)
                )

                val filteredCountries by remember(searchQuery) {
                    mutableStateOf(
                        CountryRepository.countries.filter { country ->
                            country.name.contains(searchQuery, ignoreCase = true) ||
                                    country.dialCode.contains(searchQuery) ||
                                    country.isoCode.contains(searchQuery, ignoreCase = true)
                        }
                    )
                }


                if (filteredCountries.isEmpty()) {
                    Text(
                        "No results",
                        modifier = Modifier.padding(12.dp),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                } else {
                    filteredCountries.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${country.flag} ${country.dialCode} ${country.name}",
                                    fontSize = 16.sp
                                )
                            },
                            onClick = {
                                onCountrySelected(country)
                                expanded = false
                                searchQuery = ""
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            OutlinedTextField(
                value = localPhone,
                onValueChange = { newValue ->
                    val digits = newValue.text.filter { it.isDigit() }
                    val limitedDigits = digits.take(15)
                    val formatter = phoneUtil.getAsYouTypeFormatter(selectedCountry.isoCode)
                    var formatted = ""
                    limitedDigits.forEach { ch -> formatted = formatter.inputDigit(ch) }

                    // Calculate approximate new cursor position after formatting
                    val oldSelectionStart =
                        newValue.selection.start.coerceIn(0, newValue.text.length)
// Count how many digits were before the old cursor
                    var digitCountBeforeCursor = 0
                    for (i in 0 until oldSelectionStart) {
                        if (newValue.text[i].isDigit()) digitCountBeforeCursor++
                    }
// Now find the position in the new formatted string after that many digits
                    var newCursorPos = 0
                    var digitsSeen = 0
                    while (newCursorPos < formatted.length && digitsSeen < digitCountBeforeCursor) {
                        if (formatted[newCursorPos].isDigit()) digitsSeen++
                        newCursorPos++
                    }
// If cursor was at end or after last digit, place at new end
                    if (oldSelectionStart >= newValue.text.length || digitsSeen < digitCountBeforeCursor) {
                        newCursorPos = formatted.length
                    }

                    localPhone = TextFieldValue(
                        text = formatted,
                        selection = TextRange(newCursorPos.coerceIn(0, formatted.length))
                    )

                    onPhoneNumberChange(limitedDigits)

                    // Auto-hide keyboard when valid length/format is reached for the specific country
                    val isNumberValid = try {
                        val proto = phoneUtil.parse(limitedDigits, selectedCountry.isoCode)
                        phoneUtil.isValidNumberForRegion(proto, selectedCountry.isoCode)
                    } catch (e: Exception) {
                        false
                    }

                    if (isNumberValid) {
                        keyboardController?.hide()
                    }

                },
                placeholder = {
                    Text(
                        text = "Mobile Number",
                        fontSize = 17.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                    )
                },
                trailingIcon = {
                    val isNumberValid = try {
                        val proto = phoneUtil.parse(localPhone.text, selectedCountry.isoCode)
                        phoneUtil.isValidNumberForRegion(proto, selectedCountry.isoCode)
                    } catch (e: Exception) {
                        false
                    }

                    if (isNumberValid) {
                        Box(
                            modifier = Modifier.offset(x = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                isError = displayError,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (-4).dp)
                    .focusRequester(phoneFocusRequester)
                    .onFocusChanged { state: androidx.compose.ui.focus.FocusState ->
                        isPhoneFieldFocused = state.isFocused   // ← just update the trigger state

                        if (!state.isFocused && localPhone.text.isNotEmpty()) {
                            val isValid = try {
                                val proto =
                                    phoneUtil.parse(localPhone.text, selectedCountry.isoCode)
                                phoneUtil.isValidNumberForRegion(proto, selectedCountry.isoCode)
                            } catch (e: Exception) {
                                false
                            }
                            if (!isValid) {
                                onShowErrorChange(true)
                            }
                        }
                    },
                textStyle = TextStyle(fontSize = 18.sp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (displayError) Color.Red else appPurple,
                    unfocusedBorderColor = if (displayError) Color.Red else Color.Gray
                )
            )
        }
    }
}

// ---------------- ActionRow ----------------
@Composable
fun ActionRow(
    rememberMe: Boolean,
    isSendingOtp: Boolean,
    onRememberMeChange: (Boolean) -> Unit,
    onGetCodeClick: () -> Unit,
    scope: CoroutineScope = rememberCoroutineScope(),
    phoneFocusRequester: FocusRequester? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(0.dp)
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = onRememberMeChange,
                modifier = Modifier.size(24.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Black,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.White
                )
            )
            Text(
                "Stay signed in",
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val shakeOffset = remember { mutableFloatStateOf(0f) }
        val haptic = LocalHapticFeedback.current

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    repeat(2) {
                        shakeOffset.floatValue = 3f
                        delay(30)
                        shakeOffset.floatValue = -3f
                        delay(30)
                    }
                    shakeOffset.floatValue = 0f
                }
                onGetCodeClick()
            },

            enabled = !isSendingOtp,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            modifier = Modifier.offset(x = shakeOffset.floatValue.dp)
        ) {
            Text(
                text = if (isSendingOtp) "Sending..." else "Send Code",
                color = Color.White
            )
        }
    }
}

// ---------------- ResendTimerSection ----------------
@Composable
fun ResendTimerSection(
    timer: Int,
    canResend: Boolean,
    onResendClick: () -> Unit
) {
    val appPurple = Color(0xFF800080)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (timer > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⏰",
                    color = Color.Black,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Resend code in ${String.format(Locale.getDefault(), "%02d", timer)}",
                    color = Color.Black,
                    fontSize = 14.sp
                )
            }
        } else if (canResend) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onResendClick
                )
            ) {
                Text(
                    text = "⏰",
                    color = appPurple,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Resend code",
                    color = appPurple,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ---------------- OtpInputRow ----------------
@Composable
fun OtpInputRow(
    otp: List<String>,
    otpErrorMessage: String? = null,
    shouldShakeOtp: Boolean = false,
    onOtpChange: (List<String>) -> Unit,
    keyboardController: SoftwareKeyboardController?,
    onClearError: () -> Unit,
    onAutoVerify: () -> Unit,
    isSending: Boolean = false,
    focusRequester: FocusRequester        // this is the one for index 0
) {
    val safeOtp = if (otp.size == Constants.OTP_LENGTH) otp else List(Constants.OTP_LENGTH) { "" }
    val focusRequesters = remember { List(Constants.OTP_LENGTH) { FocusRequester() } }
    val scope = rememberCoroutineScope()

    val offsetX by animateDpAsState(
        targetValue = if (shouldShakeOtp) 8.dp else 0.dp
    )

    // ────────────────────────────────────────────────
    //     NEW: Auto-refocus + show keyboard on error + cleared
    // ────────────────────────────────────────────────
    val hasErrorAndIsCleared by remember(otp, otpErrorMessage) {
        derivedStateOf {
            otpErrorMessage?.isNotBlank() == true &&
                    safeOtp.all { it.isEmpty() }
        }
    }

    LaunchedEffect(hasErrorAndIsCleared) {
        if (hasErrorAndIsCleared) {
            // Small delay helps animation/previous clear settle
            delay(120)   // 80–150 ms usually feels natural
            focusRequester.requestFocus()           // ← first box (the one passed from parent)
            keyboardController?.show()
        }
    }
    // ────────────────────────────────────────────────

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
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
                                focusRequesters[index + 1].requestFocus()
                            }
                            if (newValue.isNotEmpty() && index == Constants.OTP_LENGTH - 1) {
                                keyboardController?.hide()
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 20.sp, textAlign = TextAlign.Center),
                    modifier = Modifier
                        .size(50.dp)
                        // Important: first field uses the parent's focusRequester
                        .focusRequester(if (index == 0) focusRequester else focusRequesters[index]),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (index == Constants.OTP_LENGTH - 1) ImeAction.Done else ImeAction.Next
                    )
                )
            }
        }
    }
}


// ---------------- SignUpFooter ----------------
@Composable
fun SignUpFooter(onSignUpClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text("Don't have an account? ")
        Text(
            text = "Sign up",
            color = Color(0xFF800080),
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onSignUpClick() }
        )
    }
}
