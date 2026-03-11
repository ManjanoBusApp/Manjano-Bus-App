package com.manjano.bus.ui.screens.home

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.*
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.StateFlow

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

    // ------------------- MOBILE NUMBER ERROR STATE -------------------
    private val _mobileNumberError = MutableStateFlow<String?>(null)
    val mobileNumberError: StateFlow<String?> = _mobileNumberError.asStateFlow()

    // ------------------- DRIVER FIRST NAME STATE -------------------
    private val firestore = FirebaseFirestore.getInstance()

    private val _driverFirstName = MutableStateFlow("")
    val driverFirstName: StateFlow<String> = _driverFirstName

    // ------------------- CURRENT LOGGED-IN DRIVER PHONE -------------------
    var loggedInDriverPhoneNumber: String? = null
        private set

    fun setLoggedInDriverPhoneNumber(phone: String) {
        loggedInDriverPhoneNumber = if (phone.startsWith("7")) "0$phone" else phone
    }

    // ------------------- FETCH DRIVER NAME IN REALTIME -------------------
    fun fetchDriverNameRealtime(phoneNumber: String) {
        val normalizedPhone = if (phoneNumber.startsWith("7")) "0$phoneNumber" else phoneNumber

        firestore.collection("drivers")
            .document(normalizedPhone)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("DriverDashboardVM", "Error fetching driver name", error)
                    return@addSnapshotListener
                }

                snapshot?.getString("name")?.let { fullName ->
                    // Take first name for greeting
                    _driverFirstName.value = fullName.split(" ").firstOrNull() ?: fullName
                }
            }
    }

    // ------------------- UPDATE LAST LOGIN -------------------
    fun updateLastLogin(phoneNumber: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("drivers")
            .document(phoneNumber)
            .update("lastLogin", System.currentTimeMillis())
            .addOnFailureListener { e ->
                android.util.Log.e("DriverDashboardVM", "Error updating lastLogin", e)
            }
    }

    // ------------------- TRACKING STATE -------------------
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

    // ------------------- MARK STUDENT BOARDING -------------------
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

    // ------------------- FETCH STUDENTS -------------------
    private fun fetchAssignedStudents() {
        database.child("students")
            .addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val list = snapshot.children.mapNotNull { child ->
                        val student = child.getValue(Student::class.java) ?: return@mapNotNull null
                        student.copy(parentName = student.parentName.replace("+", " "))
                    }
                    _studentList.value = list
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    android.util.Log.e("Firebase", "Error fetching students: ${error.message}")
                }
            })
    }

    // ------------------- TRACKING LOCATION -------------------
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

    // ------------------- SAVE DRIVER PROFILE (SIGNUP) -------------------
    fun saveDriverProfileIfNeeded(
        phoneNumber: String,
        fullName: String,
        nationalId: String,
        schoolName: String,
        onAlreadyRegistered: () -> Unit = {}
    ) {
        val db = FirebaseFirestore.getInstance()

        // Mobile number = Firestore document ID
        val normalizedPhone = if (phoneNumber.startsWith("7")) "0$phoneNumber" else phoneNumber
        val docRef = db.collection("drivers").document(normalizedPhone)

        docRef.get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    // Mobile number not recognized by admin → prevent signup
                    _mobileNumberError.value = "Mobile number not recognized"
                    return@addOnSuccessListener
                }

                val existingName = snapshot.getString("name")
                if (!existingName.isNullOrBlank()) {
                    // Already signed up → reject
                    _driverFirstName.value = ""
                    onAlreadyRegistered()
                    _mobileNumberError.value = "Already registered, go to signin instead"
                    return@addOnSuccessListener
                }

                // Prepare timestamps
                val createdAt = System.currentTimeMillis()
                val createdAtDateTime = java.text.SimpleDateFormat(
                    "hh:mm a, dd MMM yyyy",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(createdAt))

                // Write fields for first-time signup
                val updates = mapOf(
                    "name" to fullName,
                    "nationalId" to nationalId,
                    "schoolName" to schoolName,
                    "createdAt" to createdAt,
                    "createdAtDateTime" to createdAtDateTime
                )

                docRef.update(updates)
                    .addOnSuccessListener {
                        val firstName = fullName.split(" ").firstOrNull() ?: fullName
                        _driverFirstName.value = firstName
                        _mobileNumberError.value = null
                        android.util.Log.d("DriverDashboardVM", "Driver profile saved successfully")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("DriverDashboardVM", "Failed to save driver profile", e)
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DriverDashboardVM", "Firestore query failed", e)
            }
    }
}