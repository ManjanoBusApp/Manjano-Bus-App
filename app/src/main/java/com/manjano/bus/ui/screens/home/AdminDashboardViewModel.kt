package com.manjano.bus.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminDashboardViewModel : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    // Placeholder for fetching mobile admin quick data (optional)
    fun fetchQuickData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // TODO: fetch summaries from Firebase (active buses, pending notifications)
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun getAdminIdByMobileNumber(mobileNumber: String): String? {
        return try {
            val querySnapshot = firestore.collection("admins")
                .whereEqualTo("mobileNumber", mobileNumber)
                .limit(1)
                .get()
                .await()
            querySnapshot.documents.firstOrNull()?.id
        } catch (e: Exception) {
            null
        }
    }

    fun listenForDeactivation(adminId: String, onDeactivated: () -> Unit) {
        listenerRegistration = firestore.collection("admins")
            .document(adminId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val isActive = snapshot.getBoolean("active") ?: true
                    if (!isActive) {
                        onDeactivated()
                    }
                }
            }
    }

    fun removeListener() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}