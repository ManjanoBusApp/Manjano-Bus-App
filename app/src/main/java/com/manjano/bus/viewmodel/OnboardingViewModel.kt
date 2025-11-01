package com.manjano.bus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    fun saveUserProfile(name: String, email: String?, selectedRole: String, onSuccess: () -> Unit) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            _errorMessage.value = "Not logged in"
            return
        }
        _isSaving.value = true
        _errorMessage.value = null

        val user = hashMapOf(
            "name" to name,
            "email" to (email ?: ""),
            "role" to selectedRole,
            "timestamp" to System.currentTimeMillis()
        )

        viewModelScope.launch {
            db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener {
                    _saveSuccess.value = true
                    _isSaving.value = false
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    _errorMessage.value = "Failed to save: ${e.message}"
                    _isSaving.value = false
                }
        }
    }

    fun checkUserRole(roleViewModel: RoleViewModel, onComplete: () -> Unit) {
        roleViewModel.fetchUserRole(onComplete)
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

}
