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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.manjano.bus.R
import com.manjano.bus.models.CountryRepository
import com.manjano.bus.utils.Constants
import com.manjano.bus.utils.PhoneNumberUtils
import com.manjano.bus.viewmodel.ParentSignupViewModel
import com.manjano.bus.viewmodel.SignUpViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.draw.alpha
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import com.manjano.bus.MainActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState

data class MailboxLayerResult(
    val isValidSyntax: Boolean,
    val isDeliverable: Boolean,
    val error: String? = null
)

private val okHttpClient = OkHttpClient()
private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

@JsonClass(generateAdapter = true)
data class MailboxLayerResponse(
    @Json(name = "format_valid") val formatValid: Boolean,
    @Json(name = "smtp_check") val smtpCheck: Boolean,
    val success: Boolean? = null,
    val error: MailboxLayerError? = null
)

@JsonClass(generateAdapter = true)
data class MailboxLayerError(
    val code: Int?,
    val info: String?
)

suspend fun verifyEmailWithMailboxLayer(email: String): MailboxLayerResult = withContext(Dispatchers.IO) {
    val encodedEmail = URLEncoder.encode(email.trim(), "UTF-8")
    val url = "https://apilayer.net/api/check?access_key=565d95ee183d52173e20a4b8cfc5dadb&email=$encodedEmail&smtp=1&format=1"
    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    try {
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext MailboxLayerResult(
                isValidSyntax = false,
                isDeliverable = false,
                error = "API error: ${response.code}"
            )
        }

        val body = response.body?.string() ?: return@withContext MailboxLayerResult(
            isValidSyntax = false,
            isDeliverable = false,
            error = "Empty response"
        )

        val jsonAdapter = moshi.adapter(MailboxLayerResponse::class.java)
        val apiResponse = jsonAdapter.fromJson(body)

        if (apiResponse == null) {
            return@withContext MailboxLayerResult(
                isValidSyntax = false,
                isDeliverable = false,
                error = "Invalid response format"
            )
        }

        if (apiResponse.success == false) {
            val errorMsg = apiResponse.error?.info ?: "API returned failure"
            return@withContext MailboxLayerResult(
                isValidSyntax = false,
                isDeliverable = false,
                error = errorMsg
            )
        }

        return@withContext MailboxLayerResult(
            isValidSyntax = apiResponse.formatValid,
            isDeliverable = apiResponse.smtpCheck,
            error = null
        )

    } catch (e: IOException) {
        return@withContext MailboxLayerResult(
            isValidSyntax = false,
            isDeliverable = false,
            error = "Network error: ${e.message ?: "Unknown"}"
        )
    } catch (e: Exception) {
        return@withContext MailboxLayerResult(
            isValidSyntax = false,
            isDeliverable = false,
            error = "Unexpected error"
        )
    }
}

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
    focusRequester: FocusRequester,
    emailVerified: Boolean
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
                Box(
                    modifier = Modifier.onFocusChanged { focusState ->
                        // Clear error immediately when any OTP box is focused
                        if (focusState.isFocused) {
                            onClearError()
                        }
                    }
                ) {
                    OutlinedTextField(
                        value = digit,
                        onValueChange = { newValue ->
                            // Only allow change if verified
                            if (emailVerified && newValue.length <= 1 && newValue.all { ch -> ch.isDigit() }) {
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
                        ),
                        isError = otpErrorMessage != null,
                        enabled = emailVerified     // ← this line prevents focus + cursor
                    )
                }
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
    val parentSignupViewModel: ParentSignupViewModel = viewModel()
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
    var hasTouchedEmail by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    var hasTouchedPhone by remember { mutableStateOf(false) }
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
    val isPhoneAllowed by signupViewModel.isPhoneAllowed.collectAsState()
    val alreadyRegisteredError by signupViewModel.alreadyRegisteredError.collectAsState()
    var phoneErrorText by remember { mutableStateOf("") }
    var phoneErrorInvalid by remember { mutableStateOf(false) }
    var hasShownRegisteredError by remember { mutableStateOf(false) }
    var isSendingVerification by remember { mutableStateOf(false) }
    var verificationSent by remember { mutableStateOf(false) }
    var emailCooldown by remember { mutableStateOf(false) }
    var mailboxError by remember { mutableStateOf<String?>(null) }
    var emailTimer by remember { mutableStateOf(0) }
    var showRedMessage by remember { mutableStateOf(false) }
    var canResendEmail by remember { mutableStateOf(false) }
    var hasClickedSendEmail by remember { mutableStateOf(false) }
    var emailVerified by remember { mutableStateOf(false) }
    val emailShakeOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val functions = remember { FirebaseFunctions.getInstance("us-central1") }

    // 🔥 NEW: Get SharedPreferences for loading saved data
    val prefs = context.getSharedPreferences("pending_signup", Context.MODE_PRIVATE)


    LaunchedEffect(Unit) {
        parentFocusRequester.requestFocus()
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
                parentError = false
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
                    else parentError = parentName.text.isBlank()
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
        var hasTouchedChild by remember { mutableStateOf(listOf(false)) }

        // 🔥 LOAD SAVED DATA WHEN SCREEN OPENS
        LaunchedEffect(Unit) {
            val savedParentName = prefs.getString("pending_parent_name", "") ?: ""
            val savedChildren = prefs.getString("pending_children", "") ?: ""
            val savedEmail = prefs.getString("pending_email", "") ?: ""

            Log.d("🔥", "Loading saved data - Parent: $savedParentName")
            Log.d("🔥", "Loading saved data - Email: $savedEmail")
            Log.d("🔥", "Loading saved data - Children: $savedChildren")

            // Restore parent name
            if (savedParentName.isNotEmpty()) {
                parentName = TextFieldValue(savedParentName)
            }

            // Restore email
            if (savedEmail.isNotEmpty()) {
                email = TextFieldValue(savedEmail)
            }

            // Restore children names
            if (savedChildren.isNotEmpty()) {
                val childrenList = savedChildren.split("|||")
                childrenNames = childrenList.map { TextFieldValue(it) }
                childErrors = List(childrenList.size) { false }
                hasTouchedChild = List(childrenList.size) { true }
            }
        }
// Add new child
        val addChild = {
            val newChildren = childrenNames.toMutableList().apply { add(TextFieldValue("")) }
            val newErrors = childErrors.toMutableList().apply { add(false) }
            val newTouched = hasTouchedChild.toMutableList().apply { add(false) }
            childrenNames = newChildren
            childErrors = newErrors
            hasTouchedChild = newTouched
        }


// Remove child
        val removeChild = { index: Int ->
            if (childrenNames.size > 1) {
                childrenNames = childrenNames.filterIndexed { i, _ -> i != index }
                childErrors = childErrors.filterIndexed { i, _ -> i != index }
                hasTouchedChild = hasTouchedChild.filterIndexed { i, _ -> i != index }
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
                            val filtered =
                                newValue.text.filter { it.isLetter() || it.isWhitespace() }
                            val updatedChildren = childrenNames.toMutableList()
                            val updatedTouched = hasTouchedChild.toMutableList()
                            updatedChildren[index] = newValue.copy(text = filtered)
                            updatedTouched[index] = true
                            childrenNames = updatedChildren
                            hasTouchedChild = updatedTouched
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
                                    val updatedTouched = hasTouchedChild.toMutableList()
                                    updatedTouched[index] = true
                                    hasTouchedChild = updatedTouched

                                    val updatedErrors = childErrors.toMutableList()
                                    updatedErrors[index] = false
                                    childErrors = updatedErrors
                                }

                                if (!focusState.isFocused && hasTouchedChild[index]) {
                                    val updatedErrors = childErrors.toMutableList()
                                    updatedErrors[index] = childrenNames[index].text.isBlank()
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
                hasTouchedEmail = true
                email = it
                emailError = false

                prefs.edit().clear().apply()
                MainActivity.clearVerification()

                // 🔥 FULL RESET when user edits email
                verificationSent = false
                hasClickedSendEmail = false
                canResendEmail = false
                emailTimer = 0
                showRedMessage = false
                mailboxError = null
            },
            placeholder = { Text("name@email.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasTouchedEmail = true
                        emailError = false
                    } else if (hasTouchedEmail) {
                        emailError =
                            email.text.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.text)
                                .matches()
                    }
                },
            textStyle = TextStyle(fontSize = 16.sp),
            shape = RoundedCornerShape(12.dp),
            isError = emailError,
            enabled = true
        )

        // Only show one error at a time
        val emailErrorMessage = mailboxError
            ?: if (emailError) "Please enter a valid, working email address" else null

        if (emailErrorMessage != null) {
            Text(
                text = emailErrorMessage,
                color = Color.Red,
                fontSize = 12.sp
            )
        }

        if (hasClickedSendEmail && showRedMessage) {
            Text(
                text = "Check email & tap on link sent",
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.height(12.dp))

// --- Send Email Button (single) ---
        Button(
            onClick = {
                // Shake animation when tapped
                scope.launch {
                    repeat(2) {
                        emailShakeOffset.floatValue = 4f
                        delay(40)
                        emailShakeOffset.floatValue = -4f
                        delay(40)
                    }
                    emailShakeOffset.floatValue = 0f
                }

                parentError = parentName.text.isBlank()
                hasTouchedChild = List(childrenNames.size) { true }
                childErrors = childrenNames.map { it.text.isBlank() }
                val hasEmptyChild = childErrors.any { it }

                hasTouchedEmail = true
                emailError =
                    email.text.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email.text).matches()

                Log.d("EMAIL_DEBUG", "Email text: '${email.text}'")
                Log.d("EMAIL_DEBUG", "Is blank: ${email.text.isBlank()}")
                Log.d(
                    "EMAIL_DEBUG",
                    "Matches pattern: ${Patterns.EMAIL_ADDRESS.matcher(email.text).matches()}"
                )
                Log.d("EMAIL_DEBUG", "emailError: $emailError")

                val isFormValid = !parentError && !hasEmptyChild && !emailError

                if (isFormValid) {
                    val prefs = context.getSharedPreferences("pending_signup", Context.MODE_PRIVATE)
                    prefs.edit().apply {
                        putString("pending_parent_name", parentName.text)
                        putString("pending_email", email.text)
                        // Save children names
                        val childrenList = childrenNames.map { it.text }.joinToString("|||")
                        putString("pending_children", childrenList)
                        putBoolean("pending_verification", true)
                        apply()
                    }
                    Log.d("🔥", "Form data saved: ${parentName.text}, ${email.text}")
                }

                if (isFormValid) {
                    mailboxError = null

                    // Quick typo catch for very common mistakes (sync, instant)
                    val emailLower = email.text.trim().lowercase()
                    val commonTypos = listOf(
                        "gmai.com" to "gmail.com",
                        "gmial.com" to "gmail.com",
                        "gmal.com" to "gmail.com",
                        "gmil.com" to "gmail.com",
                        "gemail.com" to "gmail.com",
                        "yaho.com" to "yahoo.com",
                        "yahhoo.com" to "yahoo.com",
                        "yahooo.com" to "yahoo.com",
                        "hotmai.com" to "hotmail.com",
                        "hotmal.com" to "hotmail.com",
                        "outlok.com" to "outlook.com"
                    )

                    var fixedEmail = emailLower
                    var typoDetected = false

                    for ((wrong, correct) in commonTypos) {
                        if (fixedEmail.contains("@$wrong")) {
                            fixedEmail = fixedEmail.replace("@$wrong", "@$correct")
                            typoDetected = true
                            break
                        }
                    }

                    if (typoDetected) {
                        mailboxError = "Did you mean $fixedEmail? Please correct the typo."
                        emailError = true
                        emailFocusRequester.requestFocus()
                        return@Button
                    }

                    // Bypass MailboxLayer for testing (remove this when API key is renewed)
                    scope.launch {
                        try {
                            isSendingVerification = true

                            // Skip MailboxLayer check - allow any email for testing
                            // Just do a simple format check
                            val emailText = email.text.trim()
                            val isValidFormat = Patterns.EMAIL_ADDRESS.matcher(emailText).matches()

                            withContext(Dispatchers.Main) {
                                if (!isValidFormat) {
                                    // Invalid email format - show error, don't send email
                                    mailboxError = "⚠️ Please enter a valid email address."
                                    emailError = true
                                    emailFocusRequester.requestFocus()
                                    isSendingVerification = false
                                    return@withContext
                                }

                                // ✅ Email format is valid - now send email (skip deliverability check)
                                try {
                                    val emailTrimmed = email.text.trim()
                                    val safeEmail =
                                        if (emailTrimmed.isNotEmpty()) emailTrimmed else "reneegithinji@yahoo.com"
                                    val data = hashMapOf("email" to safeEmail)

                                    Log.d(
                                        "EMAIL_FUNCTION",
                                        "➡️ Sending email to: $safeEmail (MailboxLayer bypassed)"
                                    )

                                    val result = functions.getHttpsCallable("sendVerificationEmail")
                                        .call(data).await()

                                    Log.d(
                                        "EMAIL_FUNCTION",
                                        "✅ Email sent successfully, result: $result"
                                    )

                                    // Only show success message when email is valid AND deliverable
                                    verificationSent = true
                                    hasClickedSendEmail = true
                                    showRedMessage = true
                                    canResendEmail = false
                                    emailTimer = 30
                                    mailboxError = null
                                    emailError = false
                                    isSendingVerification = false

                                } catch (e: Exception) {
                                    Log.e("EMAIL_FUNCTION", "❌ Error sending verification email", e)
                                    mailboxError = "Unable to send email. Please try again."
                                    verificationSent = false
                                    hasClickedSendEmail = false
                                    showRedMessage = false
                                    isSendingVerification = false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EMAIL_FUNCTION", "Verification failed", e)
                            withContext(Dispatchers.Main) {
                                mailboxError = "Unable to verify email. Please try again."
                                isSendingVerification = false
                            }
                        }
                    }
                } else {
                    hasClickedSendEmail = false
                    showRedMessage = false
                    mailboxError = null
                }
            },
            enabled = emailTimer == 0 && !isSendingVerification,
            modifier = Modifier
                .height(44.dp)
                .width(150.dp)
                .align(Alignment.End)
                .offset(x = emailShakeOffset.floatValue.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (emailTimer == 0 && !isSendingVerification) Color.Black else Color.LightGray,
                contentColor = Color.White,
                disabledContainerColor = Color.LightGray
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = when {
                    isSendingVerification -> "Verifying..."
                    emailTimer > 0 -> "Sending..."
                    else -> "Send Email Link"
                },
                color = Color.White,
                fontSize = 12.sp
            )
        }

// --- Resend Email / Timer (Clean Swap Version) ---
        if (hasClickedSendEmail) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (hasClickedSendEmail) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (!canResendEmail && emailTimer > 0) {
                            // ⏰ TIMER STATE
                            Text(
                                text = "⏰ Resend code in ${
                                    String.format(
                                        Locale.getDefault(),
                                        "%02d",
                                        emailTimer
                                    )
                                }",
                                color = Color.Black,
                                fontSize = 14.sp
                            )
                        } else {
                            // ⏰ RESEND STATE
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏰ ",
                                    fontSize = 14.sp,
                                    color = Color(0xFF800080)
                                )

                                Text(
                                    text = "Resend Email",
                                    color = Color(0xFF800080),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable {
                                        verificationSent = true
                                        showRedMessage = true
                                        canResendEmail = false
                                        emailTimer = 30
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
// --- Countdown & red text handler ---
        LaunchedEffect(showRedMessage) {
            if (showRedMessage) {
                delay(5000)
                showRedMessage = false
            }
        }

        LaunchedEffect(emailTimer) {
            if (emailTimer > 0) {
                delay(1000)
                emailTimer--
            } else if (hasClickedSendEmail) {
                canResendEmail = true
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val phoneFocusRequester = remember { FocusRequester() }

        // 🔥 Listen for verification status changes (when app opens from deep link)
        // This runs whenever pendingVerification changes
        LaunchedEffect(MainActivity.pendingVerification.collectAsState().value) {
            val hasPending = MainActivity.hasPendingVerification()
            val pendingEmail = MainActivity.verificationEmail.value

            Log.d(
                "🔥",
                "Verification status changed - hasPending: $hasPending, pendingEmail: $pendingEmail"
            )
            Log.d("🔥", "Current email text: ${email.text}")

            if (hasPending && pendingEmail != null && email.text == pendingEmail) {
                Log.d("🔥", "✅ Setting emailVerified = true - ungrays sections")
                emailVerified = true

                // Clear verification flag
                MainActivity.clearVerification()

                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "✅ Email verified! You can now enter your phone number",
                        withDismissAction = true
                    )
                }

                delay(500)
                phoneFocusRequester.requestFocus()
            }
        }

        // 🔥 Initial check when screen loads (handles case where verification was already pending)
        LaunchedEffect(Unit) {
            delay(1000) // Give time for data to load

            val hasPending = MainActivity.hasPendingVerification()
            val pendingEmail = MainActivity.verificationEmail.value

            Log.d(
                "🔥",
                "Initial verification check - hasPending: $hasPending, pendingEmail: $pendingEmail"
            )
            Log.d("🔥", "Current email text: ${email.text}")

            if (hasPending && pendingEmail != null && email.text == pendingEmail) {
                Log.d("🔥", "✅ Initial check - setting emailVerified = true")
                emailVerified = true
                MainActivity.clearVerification()

                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "✅ Email verified! You can now enter your phone number",
                        withDismissAction = true
                    )
                }

                delay(500)
                phoneFocusRequester.requestFocus()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (emailVerified) 1f else 0.6f)
                .focusable(enabled = emailVerified)
                .clickable(
                    enabled = emailVerified,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            PhoneInputSection(
                selectedCountry = selectedCountry,
                phoneNumber = phoneNumber,
                onCountrySelected = {
                    if (emailVerified) selectedCountry = it
                },
                onPhoneNumberChange = { newNumber ->
                    if (emailVerified) {
                        phoneNumber = newNumber
                        phoneErrorText = ""
                        phoneError = false
                        phoneErrorInvalid = false
                        hasTouchedPhone = true
                    }
                },
                showError = phoneError || phoneErrorInvalid || phoneErrorText.isNotEmpty(),
                onShowErrorChange = { /* ignore */ },
                phoneFocusRequester = phoneFocusRequester,
                keyboardController = keyboardController,
                focusManager = focusManager,
                enabled = emailVerified
            )
        }
        if (hasTouchedPhone) {
            val showErrorText = when {
                phoneErrorInvalid || phoneNumber.isBlank() -> "Please enter a valid phone number"
                phoneErrorText.isNotEmpty() -> phoneErrorText
                alreadyRegisteredError != null -> alreadyRegisteredError!!
                else -> null
            }
            if (showErrorText != null) {
                Text(
                    text = showErrorText,
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
        LaunchedEffect(Unit) {
            // Only request focus for phone when parent and child fields are valid and OTP is not yet requested
            if (parentName.text.isNotEmpty() && childrenNames.all { it.text.isNotEmpty() }) {
                // Optional: uncomment if you want initial phone focus after parent/child
                // phoneFocusRequester.requestFocus()
            }
        }

        // NEW: Call Firestore check when phoneNumber changes
        LaunchedEffect(phoneNumber) {
            if (phoneNumber.isNotBlank()) {
                signupViewModel.checkPhoneNumberInFirestore(
                    phone = phoneNumber,
                    countryIso = selectedCountry.isoCode
                )
            } else {
                signupViewModel.resetPhoneAllowed()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))


        val isOtpCoolingDown by remember(uiState.resendTimerSeconds) {
            derivedStateOf { uiState.resendTimerSeconds > 0 }
        }


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (emailVerified) 1f else 0.6f)
                .focusable(enabled = emailVerified)
                .clickable(
                    enabled = emailVerified,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            ActionRow(
                rememberMe = uiState.rememberMe,
                isSendingOtp = uiState.isSendingOtp || isOtpCoolingDown,
                onRememberMeChange = { if (emailVerified) signupViewModel.onRememberMeChange(it) },
                onGetCodeClick = {
                    if (emailVerified) {
                        keyboardController?.hide()

                        scope.launch {
                            // --- Step 1: Reset previous OTP error message ---
                            showOtpErrorMessage = false

                            // --- Step 2: Ensure Firestore phone check is done ---
                            if (phoneNumber.isNotBlank() && isPhoneAllowed == null) {
                                signupViewModel.checkPhoneNumberInFirestore(
                                    phone = phoneNumber,
                                    countryIso = selectedCountry.isoCode
                                )
                                // Small delay to allow result
                                delay(500)
                            }

                            // --- Step 3: Validate all fields ---
                            parentError = parentName.text.isEmpty()

                            hasTouchedChild = List(childrenNames.size) { true }
                            childErrors =
                                childrenNames.indices.map { index -> childrenNames[index].text.isEmpty() }
                            val hasEmptyChild = childErrors.any { it }
                            studentError = hasEmptyChild

                            hasTouchedEmail = true
                            emailError =
                                email.text.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.text)
                                    .matches()

                            // --- Step 4: Reset phone error UI + mark as touched ---
                            phoneError = false
                            phoneErrorText = ""
                            hasTouchedPhone = true

                            // Local format validation first
                            val isValidFormat = try {
                                PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                            } catch (e: Exception) {
                                false
                            }

                            phoneErrorInvalid = !isValidFormat && phoneNumber.isNotBlank()

                            // Then Firestore / school allowance check
                            val phoneNotAllowed = isPhoneAllowed != true
                            if (phoneNotAllowed && isValidFormat) {
                                phoneErrorText = "Can't proceed, contact the school"
                                phoneError = true
                            } else if (!isValidFormat) {
                                phoneErrorText =
                                    ""   // let the invalid message show via phoneErrorInvalid
                            }
                            // --- Step 6: Overall validity check ---
                            val canProceed =
                                !parentError && !hasEmptyChild && !emailError && !phoneNotAllowed

                            if (canProceed) {
                                // --- Step 7: Request OTP (only once) ---
                                signupViewModel.requestOtp()
                                showOtpMessage = true

                                if (alreadyRegisteredError != null) {
                                    phoneFocusRequester.requestFocus()
                                    return@launch
                                }
                                delay(100)
                                if (scrollState.maxValue > 0) {
                                    otpFocusRequester.requestFocus()
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            } else {
                                // --- Step 8: Focus first invalid field ---
                                when {
                                    parentError -> parentFocusRequester.requestFocus()
                                    hasEmptyChild -> {
                                        val firstEmpty = childErrors.indexOfFirst { it }
                                        if (firstEmpty >= 0) studentFocusRequester.requestFocus()
                                    }

                                    emailError -> emailFocusRequester.requestFocus()
                                    phoneNotAllowed -> phoneFocusRequester.requestFocus()
                                }
                            }
                        }
                    }
                }
            )
        }
        if (showOtpMessage) {
            ResendTimerSection(
                timer = uiState.resendTimerSeconds,
                canResend = uiState.canResendOtp,
                onResendClick = { signupViewModel.resendOtp() }
            )
        }
        if (showOtpMessage) { // → New: Show message only after Send Code
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

        val otpBoxErrors: SnapshotStateList<Boolean> = remember {
            mutableStateListOf(*Array(Constants.OTP_LENGTH) { false })
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (emailVerified) 1f else 0.6f)
                .focusable(enabled = emailVerified)
                .clickable(
                    enabled = emailVerified,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {}
        ) {
            SignupOtpInputRow(
                otp = uiState.otpDigits,
                otpErrorMessage = if (otpBoxErrors.any { error -> error }) "Please fill all OTP digits" else null,
                shouldShakeOtp = uiState.shouldShakeOtp,
                onOtpChange = { digits: List<String> ->
                    if (emailVerified) {
                        showOtpMessage = false

                        // 🔥 RESET OTP SENDING STATE when user starts typing
                        if (digits.any { it.isNotEmpty() }) {
                            signupViewModel.resetOtpSendingState()
                        }

                        digits.forEachIndexed { index, digit ->
                            signupViewModel.onOtpDigitChange(index, digit)
                            otpBoxErrors[index] = digit.isEmpty()
                        }

                        if (digits.all { it.isNotEmpty() }) {
                            val enteredOtp = digits.joinToString("")
                            if (enteredOtp == Constants.TEST_OTP) {
                                keyboardController?.hide()
                                signupViewModel.setOtpValid(true)
                            } else {
                                signupViewModel.setOtpValid(false)

                                val clearedOtp = List(Constants.OTP_LENGTH) { "" }
                                clearedOtp.forEachIndexed { index, _ ->
                                    signupViewModel.onOtpDigitChange(index, "")
                                    otpBoxErrors[index] = true
                                }

                                otpFocusRequester.requestFocus()

                                showOtpErrorMessage = true
                            }
                        }
                    }
                },
                keyboardController = keyboardController,
                focusManager = focusManager,
                onClearError = {
                    if (emailVerified) {
                        otpBoxErrors.fill(false)
                        showOtpErrorMessage = false
                    }
                },
                onAutoVerify = {},
                isSending = uiState.isOtpSubmitting,
                focusRequester = otpFocusRequester,
                emailVerified = emailVerified
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val isOtpValid by signupViewModel.isOtpValid.collectAsState()
        val continueShakeOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

        val isFormValid = parentName.text.isNotEmpty() &&
                childrenNames.all { it.text.isNotEmpty() } &&
                email.text.isNotEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email.text).matches() &&
                phoneErrorText.isEmpty() &&        // <-- use local phone validation
                uiState.otpDigits.size == Constants.OTP_LENGTH &&
                uiState.otpDigits.all { it.isNotBlank() } &&
                isOtpValid                               // OTP is correct

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

                    // Reset phone states
                    phoneError = false
                    phoneErrorText = ""

                    // Format validation first
                    val isFormatValid = PhoneNumberUtils.isValidNumber(
                        phoneNumber,
                        selectedCountry.isoCode
                    )

                    val isCompleteEnough = phoneNumber.isNotBlank() && phoneNumber.length >= 8

                    // Wait for Firestore if needed
                    if (isPhoneAllowed == null && phoneNumber.isNotBlank()) {
                        delay(800)
                    }

                    // Clear previous phone error
                    phoneErrorText = ""

                    // Local phone validation only
                    if (!PhoneNumberUtils.isValidNumber(phoneNumber, selectedCountry.isoCode)
                        || phoneNumber.isBlank()
                        || phoneNumber.length < 8
                    ) {
                        phoneErrorText = "Invalid phone number"
                        phoneFocusRequester.requestFocus()
                        return@launch
                    }

// Continue with parent/children/email validation as usual
                    parentError = parentName.text.isEmpty()
                    hasTouchedChild = List(childrenNames.size) { true }
                    childErrors =
                        childrenNames.indices.map { index -> childrenNames[index].text.isEmpty() }
                    val hasEmptyChild = childErrors.any { it }
                    studentError = hasEmptyChild
                    hasTouchedEmail = true
                    emailError = email.text.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email.text)
                        .matches()

                    val canProceed = !parentError && !hasEmptyChild && !emailError

                    if (canProceed) {
                        Log.d("🔥", "Continue clicked - saving parent & children in Firebase")

                        val firestore = FirebaseFirestore.getInstance()
                        val normalizedPhone = signupViewModel.normalizePhoneNumber(
                            phoneNumber,
                            selectedCountry.isoCode
                        )
                        val now = Calendar.getInstance().time
                        val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                        val timeFormatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val createdOn = dateFormatter.format(now)
                        val createdTime = timeFormatter.format(now)

                        val updateData = hashMapOf<String, Any>(
                            "parentName" to parentName.text.trim(),
                            "email" to email.text.trim(),
                            "createdOn" to createdOn,
                            "createdTime" to createdTime
                        )

                        firestore.collection("parents")
                            .document(normalizedPhone)
                            .set(updateData, SetOptions.merge())
                            .addOnSuccessListener {
                                Log.d("🔥", "Parent extra fields merged in Firestore")


                                // 🔥 SAVE TO CHILDREN COLLECTION IN FIRESTORE
                                firestore.collection("parents")
                                    .document(normalizedPhone)
                                    .get()
                                    .addOnSuccessListener { parentDoc ->
                                        val schoolName = parentDoc.getString("school") ?: ""

                                        val childData = mutableMapOf<String, Any>(
                                            "parentName" to parentName.text.trim(),
                                            "schoolName" to schoolName,
                                            "createdOn" to createdOn,
                                            "createdAt" to createdTime
                                        )

                                        if (childrenNames.size == 1) {
                                            // Only one child - use childName (no number)
                                            childData["childName"] = childrenNames[0].text.trim()
                                        } else {
                                            // Multiple children - use childName1, childName2, etc.
                                            childrenNames.forEachIndexed { index, childField ->
                                                childData["childName${index + 1}"] = childField.text.trim()
                                            }
                                        }

                                        // Format child name(s) for document ID
                                        fun formatChildName(fullName: String): String {
                                            val parts = fullName.trim().split(" ")
                                            return when (parts.size) {
                                                1 -> parts[0] // Only first name
                                                2 -> "${parts[0]} ${parts[1].first()}" // First name + last initial
                                                else -> {
                                                    val firstName = parts[0]
                                                    val middleInitial = parts[1].first()
                                                    val lastInitial = parts.last().first()
                                                    "$firstName $middleInitial.$lastInitial"
                                                }
                                            }
                                        }

                                        val childId = if (childrenNames.size == 1) {
                                            formatChildName(childrenNames[0].text.trim())
                                        } else {
                                            childrenNames.joinToString(" - ") { formatChildName(it.text.trim()) }
                                        }

                                        // Add parent phone number to ensure uniqueness
                                        // Get last 3 digits of phone number
                                        val lastThreeDigits = normalizedPhone.takeLast(3)
                                        val childDocId = "$childId-$lastThreeDigits"

                                        firestore.collection("children")
                                            .document(childDocId)
                                            .set(childData)
                                            .addOnSuccessListener {
                                                Log.d("🔥", "Child document created successfully in Firestore: $childDocId")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("🔥", "Failed to create child document in Firestore", e)
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("🔥", "Failed to get parent document for school name", e)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("🔥", "Failed to merge parent fields", e)
                            }
                        val childrenCsv = childrenNames.joinToString(",") { it.text }

                        parentSignupViewModel.saveParentAndChildren(
                            parentName = parentName.text,
                            childrenNames = childrenCsv,
                            context = context
                        )

                        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            putString("parent_name", parentName.text)
                            putString("children_names", childrenNames.joinToString(",") { it.text })
                        }.apply()

                        val encodedParent =
                            URLEncoder.encode(parentName.text, StandardCharsets.UTF_8.toString())
                        val encodedChildren = URLEncoder.encode(
                            childrenNames.joinToString(",") { it.text },
                            StandardCharsets.UTF_8.toString()
                        )
                        val encodedStatus =
                            URLEncoder.encode("On Route", StandardCharsets.UTF_8.toString())

                        // 🔥 Clear verification data so signup form is fresh next time
                        MainActivity.clearVerification()

                        navController.navigate("parent_dashboard/$encodedParent/$encodedChildren/$encodedStatus") {
                            popUpTo("signup") { inclusive = true }
                        }
                    } else {
                        when {
                            parentError -> parentFocusRequester.requestFocus()
                            hasEmptyChild -> {
                                val firstEmpty = childErrors.indexOfFirst { it }
                                if (firstEmpty >= 0) studentFocusRequester.requestFocus()
                            }

                            emailError -> emailFocusRequester.requestFocus()
                        }
                    }
                }
            },
            enabled = isFormValid && phoneErrorText.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = continueShakeOffset.floatValue.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFormValid) appPurple else Color.LightGray,
                disabledContainerColor = Color.LightGray
            )
        ) {
            Text(
                text = if (isSendingVerification) "Sending..." else "Continue",
                color = Color.White,
                fontSize = 16.sp
            )
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
                    navController.navigate("signin/parent")
                }
            )

        }
    }
}
