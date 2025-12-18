package com.manjano.bus.ui.screens.home

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class Student(
    val id: String = "",
    val name: String = "",
    val stop: String = "",
    val status: String = "Waiting",
    val busAssigned: String = ""
)

@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {

    private val database = FirebaseDatabase.getInstance().reference
    private var currentBusId: String = "bus_001"

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _studentList = MutableStateFlow<List<Student>>(emptyList())
    val studentList = _studentList.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val updates = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to System.currentTimeMillis()
                )
                database.child("busLocations").child(currentBusId).setValue(updates)
            }
        }
    }

    fun markStudentAsBoarded(studentId: String) {
        database.child("students").child(studentId).child("status").setValue("Boarded")
    }

    fun setBusId(newBusId: String) {
        currentBusId = newBusId
        fetchAssignedStudents()
    }

    init {
        seedDatabaseWithStudents()
        fetchAssignedStudents()
    }

    private fun fetchAssignedStudents() {
        database.child("students")
            .orderByChild("busAssigned")
            .equalTo(currentBusId)
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val list = mutableListOf<Student>()
                    snapshot.children.forEach { child ->
                        child.getValue(Student::class.java)?.let { list.add(it) }
                    }
                    _studentList.value = list
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private fun seedDatabaseWithStudents() {
        val busIds = (1..10).map { "bus_${it.toString().padStart(3, '0')}" }
        val stops = listOf("Beijing Road", "Mombasa Road", "Westlands", "Kilimani")

        for (i in 1..100) {
            val studentId = "stu_${i.toString().padStart(4, '0')}"
            val assignedBus = if (i <= 10) "bus_001" else busIds.random()

            val studentData = mapOf(
                "id" to studentId,
                "name" to "Student $i",
                "stop" to stops.random(),
                "status" to "Waiting",
                "busAssigned" to assignedBus
            )
            database.child("students").child(studentId).setValue(studentData)
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