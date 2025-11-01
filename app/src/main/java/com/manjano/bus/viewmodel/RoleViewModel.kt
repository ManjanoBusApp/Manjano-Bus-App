package com.manjano.bus.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoleViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun fetchUserRole(onComplete: (() -> Unit)? = null) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _errorMessage.value = "User not logged in"
            return
        }

        _isLoading.value = true
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                _role.value = document.getString("role") ?: "rider"
                _isLoading.value = false
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                _errorMessage.value = "Error fetching role: ${e.message}"
                _isLoading.value = false
            }
    }
}
