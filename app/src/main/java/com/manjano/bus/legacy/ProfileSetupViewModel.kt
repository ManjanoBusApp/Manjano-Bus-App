package com.manjano.bus.legacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class UiEvent {
    data class UpdateLoadingState(val isLoading: Boolean) : UiEvent()
    data class ShowToast(val message: String, val duration: Int = 0) : UiEvent()
    data class SetEditTextError(val errorMessage: String?) : UiEvent()
    object ClearEditTextError : UiEvent()
    object NavigateToMain : UiEvent()
    object FinishActivity : UiEvent()
}

class ProfileSetupViewModel(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiEvents = Channel<UiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    // Private variable to store the full name
    private var fullName: String = ""

    // Update full name from Activity input
    fun setFullName(name: String) {
        fullName = name
        viewModelScope.launch {
            _uiEvents.send(UiEvent.ClearEditTextError)
        }
    }

    // Save profile using the internal fullName
    fun saveUserProfile() {
        val validationError = validateInput(fullName)
        if (validationError != null) {
            viewModelScope.launch {
                _uiEvents.send(UiEvent.ShowToast(validationError.toastMessage))
                _uiEvents.send(UiEvent.SetEditTextError(validationError.fieldError))
            }
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            viewModelScope.launch {
                _uiEvents.send(UiEvent.ShowToast("User not signed in. Returning to login...", 1))
                kotlinx.coroutines.delay(1500)
                _uiEvents.send(UiEvent.FinishActivity)
            }
            return
        }

        viewModelScope.launch {
            _uiEvents.send(UiEvent.UpdateLoadingState(true))

            val userData = hashMapOf(
                "fullName" to fullName,
                "profileCompleted" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )

            try {
                firestore.collection("users").document(userId).set(userData).await()
                _uiEvents.send(UiEvent.ShowToast("Profile saved successfully!"))
                _uiEvents.send(UiEvent.NavigateToMain)
            } catch (e: Exception) {
                val errorMessage = when {
                    e is FirebaseFirestoreException -> {
                        when (e.code) {
                            FirebaseFirestoreException.Code.UNAVAILABLE -> "Network unavailable. Please check your connection."
                            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Permission denied."
                            else -> "Failed to save profile. Please try again."
                        }
                    }
                    e.message?.contains("network", ignoreCase = true) == true -> "Network error. Check your connection."
                    else -> "Failed to save profile. Please try again."
                }
                _uiEvents.send(UiEvent.ShowToast(errorMessage, 1))
            } finally {
                _uiEvents.send(UiEvent.UpdateLoadingState(false))
            }
        }
    }

    private data class ValidationError(val fieldError: String, val toastMessage: String)

    private fun validateInput(fullName: String): ValidationError? {
        return when {
            fullName.isEmpty() -> ValidationError("Full name is required", "Please enter your full name")
            fullName.isBlank() -> ValidationError("Invalid name", "Please enter a valid name")
            fullName.length < 2 -> ValidationError("Name too short", "Name must be at least 2 characters")
            fullName.length > 50 -> ValidationError("Name too long", "Name cannot exceed 50 characters")
            !fullName.contains(" ") -> ValidationError("Full name is required", "Please include both first and last name")
            else -> null
        }
    }
}
