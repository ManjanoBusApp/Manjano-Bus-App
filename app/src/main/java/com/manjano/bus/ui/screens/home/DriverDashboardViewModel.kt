package com.manjano.bus.ui.screens.home

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {

    private val database = FirebaseDatabase.getInstance().getReference("busLocations/bus_001")

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val updates = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to System.currentTimeMillis()
                )
                database.setValue(updates)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleTracking() {
        _isTracking.value = !_isTracking.value
        if (_isTracking.value) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
        } else {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}