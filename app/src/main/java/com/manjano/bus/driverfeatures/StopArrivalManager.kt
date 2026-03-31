package com.manjano.bus.driverfeatures

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.manjano.bus.ui.screens.home.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class StopArrivalManager @Inject constructor(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Current bus location
    private var currentBusLocation: Location? = null

    // List of all students with their pickup coordinates
    private var allStudents: List<Student> = emptyList()

    // Nearby student IDs (within 50m radius)
    private val _nearbyStudentIds = MutableStateFlow<Set<String>>(emptySet())
    val nearbyStudentIds: StateFlow<Set<String>> = _nearbyStudentIds.asStateFlow()

    // Whether bus is near any student
    private val _isNearAnyStudent = MutableStateFlow(false)
    val isNearAnyStudent: StateFlow<Boolean> = _isNearAnyStudent.asStateFlow()

    // Track which students have been notified to avoid duplicate notifications
    private val notifiedStudents = mutableSetOf<String>()

    // Detection radius in meters
    private val DETECTION_RADIUS_METERS = 50.0

    // Location callback for receiving bus location updates
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                currentBusLocation = location
                checkProximityToAllStudents()
            }
        }
    }

    /**
     * Start monitoring bus location
     * Returns true if monitoring started successfully, false if permission denied
     */
    fun startMonitoring(): Boolean {
        return try {
            if (hasLocationPermission()) {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
                fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            android.util.Log.e("StopArrivalManager", "Location permission denied", e)
            false
        }
    }

    /**
     * Stop monitoring bus location
     */
    fun stopMonitoring() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        currentBusLocation = null
        _nearbyStudentIds.value = emptySet()
        _isNearAnyStudent.value = false
        notifiedStudents.clear()
    }

    /**
     * Update the list of students with their pickup coordinates
     * Call this when student list changes
     */
    fun updateStudentList(students: List<Student>) {
        allStudents = students
        // Re-check proximity with new student list
        checkProximityToAllStudents()
    }

    /**
     * Get list of student IDs currently within detection radius
     */
    fun getNearbyStudentIds(): Set<String> {
        return _nearbyStudentIds.value
    }

    /**
     * Check if a specific student is currently nearby
     */
    fun isStudentNearby(studentId: String): Boolean {
        return _nearbyStudentIds.value.contains(studentId)
    }

    /**
     * Reset notification tracking for a student (called when student is boarded/alighted)
     */
    fun resetNotificationForStudent(studentId: String) {
        notifiedStudents.remove(studentId)
    }

    /**
     * Check proximity of bus to all students and update nearby list
     */
    private fun checkProximityToAllStudents() {
        val busLocation = currentBusLocation
        if (busLocation == null || allStudents.isEmpty()) {
            _nearbyStudentIds.value = emptySet()
            _isNearAnyStudent.value = false
            return
        }

        val nearbyIds = mutableSetOf<String>()
        val newlyNearby = mutableListOf<String>()

        allStudents.forEach { student ->
            // Only check students with valid coordinates (not default 0.0)
            if (student.pickUpLat != 0.0 || student.pickUpLng != 0.0) {
                val distance = calculateDistance(
                    busLocation.latitude, busLocation.longitude,
                    student.pickUpLat, student.pickUpLng
                )

                if (distance <= DETECTION_RADIUS_METERS) {
                    nearbyIds.add(student.childId)

                    // Check if this is a new detection (not already notified)
                    if (!notifiedStudents.contains(student.childId)) {
                        newlyNearby.add(student.childId)
                        notifiedStudents.add(student.childId)
                    }
                }
            }
        }

        _nearbyStudentIds.value = nearbyIds
        _isNearAnyStudent.value = nearbyIds.isNotEmpty()

        // Trigger notifications for newly nearby students
        if (newlyNearby.isNotEmpty()) {
            onStudentsNearby(newlyNearby)
        }
    }

    /**
     * Calculate distance between two points using Haversine formula
     * Returns distance in meters
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Check if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Callback when students are detected within 50m radius
     * Override this or set a listener for parent notifications
     */
    private fun onStudentsNearby(studentIds: List<String>) {
        // This will be used by the notification service
        // For now, just log
        android.util.Log.d("StopArrivalManager", "Students nearby: $studentIds")
    }

    /**
     * Get current bus location (for debugging)
     */
    fun getCurrentBusLocation(): Location? = currentBusLocation

    /**
     * Get distance to a specific student's home (for debugging)
     */
    fun getDistanceToStudent(student: Student): Double? {
        val busLocation = currentBusLocation ?: return null
        if (student.pickUpLat == 0.0 && student.pickUpLng == 0.0) return null
        return calculateDistance(
            busLocation.latitude, busLocation.longitude,
            student.pickUpLat, student.pickUpLng
        )
    }
}