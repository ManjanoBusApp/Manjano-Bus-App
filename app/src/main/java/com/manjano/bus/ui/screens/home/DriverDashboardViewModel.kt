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
    val childId: String = "",
    val displayName: String = "",
    val parentName: String = "",
    val status: String = "Waiting",
    val active: Boolean = true,
    val eta: String = "",
    val photoUrl: String = "",
    val fingerprintId: Int? = null
)
@HiltViewModel
class DriverDashboardViewModel @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val database = FirebaseDatabase.getInstance().reference
    private var currentBusId: String = "bus_001"
    private var tts: android.speech.tts.TextToSpeech? = null

    init {
        tts = android.speech.tts.TextToSpeech(context) { status ->
            if (status != android.speech.tts.TextToSpeech.ERROR) {
                tts?.language = java.util.Locale.US
            }
        }
        fetchAssignedStudents()
    }

    private val _isTracking = MutableStateFlow(false)
    val isTracking = _isTracking.asStateFlow()

    private val _studentList = MutableStateFlow<List<Student>>(emptyList())
    val studentList = _studentList.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                val updates = mapOf(
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "timestamp" to System.currentTimeMillis()
                )
                database.child("busLocations").child(currentBusId).setValue(updates)
            }
        }
    }

    fun markStudentAsBoarded(studentId: String) {
        database.child("students").child(studentId).child("status").setValue("Boarded")
    }

    fun onFingerprintScanned(hardwareId: Int?) {
        if (hardwareId == null) return
        val student = _studentList.value.find { it.fingerprintId == hardwareId }
        student?.let {
            markStudentAsBoarded(it.childId)
            tts?.speak(
                "${it.displayName} has boarded",
                android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
            android.util.Log.d("HARDWARE_SCAN", "Successfully Boarded: ${it.displayName}")
        }
    }

    fun setBusId(newBusId: String) {
        currentBusId = newBusId
        fetchAssignedStudents()
    }

    private fun fetchAssignedStudents() {
        database.child("students")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val list = snapshot.children.mapNotNull { child ->
                        val student = child.getValue(Student::class.java) ?: return@mapNotNull null
                        // Fix encoded parent name: replace + with space (from URL encoding)
                        student.copy(parentName = student.parentName.replace("+", " "))
                    }
                    _studentList.value = list
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    android.util.Log.e("Firebase", "Error fetching students: ${error.message}")
                }
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
                "busAssigned" to assignedBus,
                "fingerprintId" to i
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
    fun simulateBoarding(studentId: String, studentName: String) {
        markStudentAsBoarded(studentId)
        tts?.speak(
            "$studentName has boarded",
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tts?.stop()
        tts?.shutdown()
    }
}