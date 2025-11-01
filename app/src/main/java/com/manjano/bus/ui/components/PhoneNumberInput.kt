package com.manjano.bus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.manjano.bus.utils.PhoneNumberUtils

data class Country(
    val code: String,
    val name: String,
    val dialCode: String,
    val flag: String
)

val SUPPORTED_COUNTRIES = listOf(
    Country("KE", "Kenya", "+254", "🇰🇪"),
    Country("UG", "Uganda", "+256", "🇺🇬"),
    Country("TZ", "Tanzania", "+255", "🇹🇿"),
    Country("RW", "Rwanda", "+250", "🇷🇼"),
    Country("US", "United States", "+1", "🇺🇸"),
    Country("GB", "United Kingdom", "+44", "🇬🇧")
)

@Composable
fun PhoneNumberInput(
    rawPhoneNumber: String?,
    onPhoneNumberChange: (String) -> Unit,
    isValid: Boolean,
    validationMessage: String?,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = validationMessage != null,
    selectedCountry: Country = SUPPORTED_COUNTRIES[0],
    onCountrySelected: (Country) -> Unit = {},
    autoAdvanceEnabled: Boolean = false,
    onAutoAdvance: () -> Unit = {},
    autoFocus: Boolean = false, // remains but ignored
    showLeadingDialCode: Boolean = true,
    realTimeFormatting: Boolean = true
) {
    var isCountryDropdownExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- Remove focusRequester entirely ---
    // val focusRequester = remember { FocusRequester() }

    // Real-time formatting
    val formattedDisplayNumber = remember(rawPhoneNumber, selectedCountry.code) {
        if (rawPhoneNumber.isNullOrEmpty()) {
            ""
        } else if (realTimeFormatting) {
            PhoneNumberUtils.formatPhoneNumberAsYouType(
                rawDigits = rawPhoneNumber,
                regionCode = selectedCountry.code
            )
        } else {
            rawPhoneNumber
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Country selector
            if (showLeadingDialCode) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { isCountryDropdownExpanded = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(selectedCountry.flag, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(selectedCountry.dialCode, style = MaterialTheme.typography.bodyMedium)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select country",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isCountryDropdownExpanded,
                    onDismissRequest = { isCountryDropdownExpanded = false }
                ) {
                    SUPPORTED_COUNTRIES.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        country.flag,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        "${country.name} (${country.dialCode})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            },
                            onClick = {
                                onCountrySelected(country)
                                isCountryDropdownExpanded = false
                                // ❌ Remove this focus request
                                // focusRequester.requestFocus()
                            }
                        )
                    }
                }
            }

            // Phone input
            OutlinedTextField(
                value = formattedDisplayNumber,
                onValueChange = { newValue ->
                    val rawDigits = newValue.filter { it.isDigit() }
                    onPhoneNumberChange(rawDigits)
                },
                label = { Text("Phone Number") },
                placeholder = {
                    val exampleNumber = PhoneNumberUtils.getExampleNumber(selectedCountry.code)
                        ?.replace(selectedCountry.dialCode, "")
                        ?.trim() ?: ""
                    Text(exampleNumber)
                },
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = {
                    when {
                        isValid && !rawPhoneNumber.isNullOrEmpty() -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Valid phone number",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        isError && !rawPhoneNumber.isNullOrEmpty() -> {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Invalid phone number",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f) // ❌ Removed .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        onFocusChanged(focusState.isFocused)
                        if (autoAdvanceEnabled && isValid && !rawPhoneNumber.isNullOrEmpty() && !focusState.isFocused) {
                            keyboardController?.hide()
                            onAutoAdvance()
                        }
                    }
            )
        }

        if (!validationMessage.isNullOrEmpty()) {
            Text(
                text = validationMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    top = 4.dp,
                    start = if (showLeadingDialCode) 60.dp else 0.dp
                )
            )
        }

        // ❌ Remove auto-focus logic entirely
        /*
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    */

        // Re-validate when country changes
        LaunchedEffect(selectedCountry) {
            if (!rawPhoneNumber.isNullOrEmpty()) onFocusChanged(false)
        }
    }
}
