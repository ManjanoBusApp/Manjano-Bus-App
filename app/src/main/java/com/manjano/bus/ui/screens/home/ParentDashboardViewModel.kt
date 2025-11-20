package com.manjano.bus.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.isActive

class ParentDashboardViewModel : ViewModel() {

    // --- BUS LOCATION STATEFLOWS ---
    private val _busLocations = mutableMapOf<String, MutableStateFlow<LatLng>>()
    val busLocations: Map<String, StateFlow<LatLng>> get() = _busLocations

    fun getBusFlow(busId: String): StateFlow<LatLng> {
        return _busLocations.getOrPut(busId) {
            MutableStateFlow(LatLng(-1.2921, 36.8219))
        }
    }

    private val database =
        FirebaseDatabase.getInstance("https://manjano-bus-default-rtdb.firebaseio.com/").reference

    // --- Real-time children list ---
    private val _childrenKeys = MutableStateFlow<List<String>>(emptyList())
    val childrenKeys: StateFlow<List<String>> = _childrenKeys
    private val childrenRef = database.child("children")

    private val childrenEventListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            val key = snapshot.key ?: return
            val current = _childrenKeys.value.toMutableList()
            if (!current.contains(key)) {
                current.add(key)
                _childrenKeys.value = current
                Log.d("🔥", "childrenEventListener: onChildAdded -> $key")
            }
        }

        override fun onChildChanged(
            snapshot: DataSnapshot,
            previousChildName: String?
        ) { /* no-op */
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            val key = snapshot.key ?: return
            val current = _childrenKeys.value.toMutableList()
            if (current.remove(key)) {
                _childrenKeys.value = current
                Log.d("🔥", "childrenEventListener: onChildRemoved -> $key")
            }
        }

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* ignore */
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("🔥", "childrenEventListener cancelled: ${error.message}")
        }
    }

    init {
        observeBusLocation()
        childrenRef.addChildEventListener(childrenEventListener)
        fixMismatchedDisplayNamesWithStateFlow()

        // Run repair on startup AND every 30 seconds to catch deletions
        viewModelScope.launch {
            while (isActive) {
                try {
                    val storage = FirebaseStorage.getInstance().reference.child("Children Images")
                    val listResult = suspendCancellableCoroutine { cont ->
                        storage.listAll()
                            .addOnSuccessListener { cont.resume(it) }
                            .addOnFailureListener { cont.resumeWithException(it) }
                    }
                    val storageFiles = listResult.items.map { it.name }
                    repairAllChildImages(storageFiles)
                    Log.d("🔥", "Storage repair completed, files: ${storageFiles.size}")

                    delay(30000) // Wait 30 seconds before next repair
                } catch (e: Exception) {
                    Log.e("🔥", "Storage repair failed: ${e.message}")
                    delay(10000) // Wait 10 seconds before retry on error
                }
            }
        }
    }

    private fun observeBusLocation() {
        val busRef = database.child("busLocation")
        busRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val busId = snapshot.key ?: "unknown_bus"
                val lat = snapshot.child("lat").getValue(Double::class.java)
                val lng = snapshot.child("lng").getValue(Double::class.java)
                if (lat != null && lng != null) {
                    _busLocations.getOrPut(busId) {
                        MutableStateFlow(
                            LatLng(
                                -1.2921,
                                36.8219
                            )
                        )
                    }.value =
                        LatLng(lat, lng)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ParentDashboard", "Bus location listener cancelled: ${error.message}")
            }
        })
    }

    private fun createChildIfMissing(childKey: String, displayName: String) {
        val ref = database.child("children").child(childKey)
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                val defaultData = mapOf(
                    "displayName" to displayName,
                    "eta" to "Arriving in 5 minutes",
                    "status" to "On Route",
                    "messages" to emptyMap<String, Any>(),
                    "photoUrl" to DEFAULT_CHILD_PHOTO_URL
                )
                ref.updateChildren(defaultData)
            } else {
                val updates = mutableMapOf<String, Any>()
                if (!snapshot.hasChild("displayName")) updates["displayName"] = displayName
                if (!snapshot.hasChild("status")) updates["status"] = "On Route"
                if (!snapshot.hasChild("messages")) updates["messages"] = emptyMap<String, Any>()
                if (!snapshot.hasChild("photoUrl")) updates["photoUrl"] = DEFAULT_CHILD_PHOTO_URL
                if (updates.isNotEmpty()) ref.updateChildren(updates)
            }
        }
    }

    /** Keep _childrenKeys updated without renaming nodes */
    private fun fixMismatchedDisplayNamesWithStateFlow() {
        childrenRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { childSnapshot ->
                    val key = childSnapshot.key ?: return@forEach
                    val current = _childrenKeys.value.toMutableList()
                    if (!current.contains(key)) {
                        current.add(key)
                        _childrenKeys.value = current
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("🔥", "fixMismatchedDisplayNamesWithStateFlow cancelled: ${error.message}")
            }
        })
    }

    private fun renameChildNode(oldKey: String, newKey: String, onRenamed: (String) -> Unit = {}) {
        childrenRef.child(oldKey).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener
            val oldData = snapshot.value
            childrenRef.child(newKey).setValue(oldData).addOnSuccessListener {
                viewModelScope.launch {
                    getEtaFlowByName(newKey)
                    getDisplayNameFlow(newKey)
                }
                viewModelScope.launch {
                    delay(2000)
                    childrenRef.child(oldKey).removeValue().addOnSuccessListener {
                        onRenamed(newKey)
                    }
                }
            }
        }
    }

    private fun ViewModel.addCloseableListener(
        ref: DatabaseReference,
        listener: ValueEventListener
    ) {
        this.addCloseable { ref.removeEventListener(listener) }
    }

    private fun ViewModel.addCloseable(onCleared: () -> Unit) {
        viewModelScope.launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                onCleared()
            }
        }
    }

    /** Observe ETA updates */
    fun getEtaFlowByName(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val etaFlow = MutableStateFlow("Loading...")
        viewModelScope.launch { createChildIfMissing(key, childName) }

        val ref = database.child("children").child(key).child("eta")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val eta = snapshot.getValue(String::class.java) ?: "Arriving in 5 minutes"
                if (etaFlow.value != eta) etaFlow.value = eta
            }

            override fun onCancelled(error: DatabaseError) {
                if (etaFlow.value != "Error loading ETA") etaFlow.value = "Error loading ETA"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return etaFlow
    }

    /** Observe displayName updates */
    fun getDisplayNameFlow(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val nameFlow = MutableStateFlow("Loading...")
        val ref = database.child("children").child(key).child("displayName")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java) ?: childName
                if (nameFlow.value != name) nameFlow.value = name
            }

            override fun onCancelled(error: DatabaseError) {
                if (nameFlow.value != "Error loading name") nameFlow.value = "Error loading name"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return nameFlow
    }

    /** Observe child's status */
    fun getStatusFlow(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val statusFlow = MutableStateFlow("Loading...")
        val ref = database.child("children").child(key).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "Unknown"
                if (statusFlow.value != status) statusFlow.value = status
            }

            override fun onCancelled(error: DatabaseError) {
                if (statusFlow.value != "Error loading status") statusFlow.value =
                    "Error loading status"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return statusFlow
    }

    /** Update child's status */
    fun updateChildStatus(childName: String, newStatus: String) {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        database.child("children").child(key).child("status").setValue(newStatus)
    }

    /** Send quick action message */
    fun sendQuickActionMessage(childName: String, action: String, message: String) {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val messageRef = database.child("children").child(key).child("messages").push()
        val msgData = mapOf(
            "action" to action,
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )
        messageRef.setValue(msgData)
    }

    /** Save child image */
    private fun saveChildImage(childKey: String, displayName: String, storageFiles: List<String>) {
        val normalizedChild = childKey.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val normalizedFiles = storageFiles.associateBy {
            it.substringBeforeLast(".").substringAfterLast("/").lowercase()
                .replace(Regex("[^a-z0-9]"), "_")
        }
        val matchedFile = normalizedFiles[normalizedChild]
        val finalUrl = if (matchedFile == null) DEFAULT_CHILD_PHOTO_URL
        else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"
        database.child("children").child(normalizedChild).child("photoUrl").setValue(finalUrl)
    }


    /** Repair all child images - optimized to avoid unnecessary updates */
    fun repairAllChildImages(storageFiles: List<String>) {
        val normalizedFiles = storageFiles.associateBy {
            it.substringBeforeLast(".").substringAfterLast("/").lowercase().replace(Regex("[^a-z0-9]"), "_")
        }

        database.child("children").get().addOnSuccessListener { snapshot ->
            // First, collect ALL children and their data
            val childrenToProcess = mutableListOf<Triple<String, String, String>>()

            snapshot.children.forEach { childSnap ->
                val displayName = childSnap.child("displayName").getValue(String::class.java) ?: return@forEach
                val normalizedKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
                val currentUrl = childSnap.child("photoUrl").getValue(String::class.java).orEmpty()

                childrenToProcess.add(Triple(normalizedKey, displayName, currentUrl))
            }

            // Now process ALL children with the complete file list
            childrenToProcess.forEach { (normalizedKey, displayName, currentUrl) ->
                // Check if current custom image still exists in storage
                val currentFileName = if (currentUrl != DEFAULT_CHILD_PHOTO_URL && currentUrl.isNotBlank()) {
                    currentUrl.substringAfterLast("%2F").substringBefore("?")
                } else {
                    null
                }

                val matchedFile = if (currentFileName != null && !storageFiles.contains(currentFileName)) {
                    null // Set to default image
                } else {
                    findBestImageMatch(normalizedKey, normalizedFiles)
                }
                val verifiedUrl = if (matchedFile == null) DEFAULT_CHILD_PHOTO_URL
                else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"

                // Only update DB if URL has actually changed
                val shouldUpdate = currentUrl != verifiedUrl

                if (shouldUpdate) {
                    database.child("children").child(normalizedKey).child("photoUrl").setValue(verifiedUrl)
                    Log.d("🔥", "✅ Saved '$normalizedKey' with image ${matchedFile ?: "default"}")
                }
            }
        }
    }

    /** Smart family matching - works with separated (ati_una_kuja) AND concatenated (atiunakuja) filenames */
    private fun findBestImageMatch(childName: String, normalizedFiles: Map<String, String>): String? {
        val childParts = childName.split("_")
            .filter { it.isNotBlank() }
            .map { it.lowercase() }

        if (childParts.isEmpty()) return null

        val sortedFiles = normalizedFiles.entries.sortedBy { it.key }

        for ((imageKey, originalFileName) in sortedFiles) {
            val imageLower = imageKey.lowercase()
            val imageParts = imageLower.split("_").filter { it.isNotBlank() }

            val isConcatenated = imageParts.size == 1 && imageLower.length > 5
            val concatenatedName = if (isConcatenated) imageLower else null

            // Extract full names and initials, handling both separated and concatenated names
            val imageFullNames = imageParts.filter { it.length > 1 }.toSet()
            val imageInitials = if (isConcatenated) {
                // For concatenated names, extract initials from actual names found in child parts
                val concatenated = imageParts[0]
                val foundInitials = mutableSetOf<String>()

                // Check for each child part that's a full name in the concatenated string
                for (childPart in childParts) {
                    if (childPart.length > 1 && concatenated.contains(childPart)) {
                        foundInitials.add(childPart.first().toString())
                    }
                }
                foundInitials
            } else {
                // For separated names, use normal initial extraction
                imageFullNames.map { it.first().toString() }.toSet()
            }
            var fullNameMatches = 0
            var initialMatches = 0

            for (part in childParts) {
                if (part.length > 1) {
                    val normalMatch = imageFullNames.contains(part)
                    val concatMatch = concatenatedName?.contains(part) == true
                    if (normalMatch || concatMatch) {
                        fullNameMatches++
                    }
                } else if (part.length == 1) {
                    if (imageInitials.contains(part)) {
                        initialMatches++
                    }
                }
            }

            val rulePassed = fullNameMatches >= 2 ||
                    (fullNameMatches == 2 && initialMatches >= 1) ||
                    (fullNameMatches == 1 && initialMatches >= 2)

// DEBUG LOG — prints ONLY when matching fails (so log stays clean even with 1000+ images)
            if (!rulePassed) {
                Log.d("🔥", "MATCH FAILED | child: $childName | image: $imageKey | full: $fullNameMatches | init: $initialMatches | initials: $imageInitials | concatenated: $isConcatenated")
            }

            if (rulePassed) {
                return originalFileName
            }
        }
        return null
    }

    /** Observe photoUrl flow - real-time updates only without periodic repair */
    fun getPhotoUrlFlow(childName: String) = callbackFlow<String> {
        val normalizedKey = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val dbRef = database.child("children").child(normalizedKey).child("photoUrl")

        var previousWasCustom = false  // Tracks if the last photo was a custom image

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbUrl = snapshot.getValue(String::class.java).orEmpty().ifBlank { DEFAULT_CHILD_PHOTO_URL }
                val isCustom = dbUrl != DEFAULT_CHILD_PHOTO_URL

                val finalUrl = if (isCustom) {
                    // We just switched TO a custom image
                    previousWasCustom = true
                    dbUrl
                } else if (previousWasCustom) {
                    // We just switched FROM custom → default → send cache-buster ONCE
                    previousWasCustom = false
                    "${dbUrl}?reset=${System.currentTimeMillis()}"
                } else {
                    // Already on default, stay clean
                    dbUrl
                }

                trySend(finalUrl).isSuccess
                Log.d("🔥", "Photo URL updated for $normalizedKey → $finalUrl")
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                Log.e("🔥", "Photo URL listener cancelled for $normalizedKey")
            }
        }

        dbRef.addValueEventListener(listener)

        awaitClose {
            dbRef.removeEventListener(listener)
            Log.d("🔥", "Photo URL flow closed for $normalizedKey")
        }
    }.distinctUntilChanged()

    /** Fetch all storage files and repair */
    fun fetchAndRepairChildImages(storageFiles: List<String>) {
        viewModelScope.launch { repairAllChildImages(storageFiles) }
    }

    companion object {
        private const val DEFAULT_CHILD_PHOTO_URL =
            "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"
    }
}

