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

        // Run repair ONCE on startup, not periodically
        viewModelScope.launch {
            try {
                val storage = FirebaseStorage.getInstance().reference.child("Children Images")
                val listResult = suspendCancellableCoroutine { cont ->
                    storage.listAll()
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
                val storageFiles = listResult.items.map { it.name }
                repairAllChildImages(storageFiles)
                Log.d("🔥", "Initial storage repair completed, files: ${storageFiles.size}")
            } catch (e: Exception) {
                Log.e("🔥", "Initial storage repair failed: ${e.message}")
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
            snapshot.children.forEach { childSnap ->
                val displayName = childSnap.child("displayName").getValue(String::class.java) ?: return@forEach
                val normalizedKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
                val currentUrl = childSnap.child("photoUrl").getValue(String::class.java).orEmpty()

                // Skip if current URL is already a custom image (not default) and we don't have a better match
                if (currentUrl != DEFAULT_CHILD_PHOTO_URL && currentUrl.isNotBlank()) {
                    // Extract filename from current URL to see if it still exists in storage
                    val currentFileName = currentUrl.substringAfterLast("%2F").substringBefore("?")
                    if (storageFiles.contains(currentFileName)) {
                        // Current custom image still exists, no need to change anything
                        return@forEach
                    }
                }

                // Try exact match first
                var matchedFile = normalizedFiles[normalizedKey]

                // If no exact match, try matching without underscores
                if (matchedFile == null) {
                    val keyWithoutUnderscores = normalizedKey.replace("_", "")
                    matchedFile = normalizedFiles.entries
                        .find { it.key.replace("_", "") == keyWithoutUnderscores }
                        ?.value
                }

                val verifiedUrl = if (matchedFile == null) DEFAULT_CHILD_PHOTO_URL
                else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"

                // Only update DB if URL has actually changed
                if (currentUrl != verifiedUrl) {
                    database.child("children").child(normalizedKey).child("photoUrl").setValue(verifiedUrl)
                    Log.d("🔥", "✅ Saved '$normalizedKey' with image ${matchedFile ?: "default"}")
                }
            }
        }
    }

    /** Observe photoUrl flow with periodic repair (improved: detects re-uploads) */
    fun getPhotoUrlFlow(childName: String) = callbackFlow<String> {
        val normalizedKey = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val dbRef = database.child("children").child(normalizedKey).child("photoUrl")

        // Keep track of last emitted value to avoid unnecessary updates
        var lastUrl: String? = null

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbUrl = snapshot.getValue(String::class.java).orEmpty().ifBlank { DEFAULT_CHILD_PHOTO_URL }
                if (dbUrl != lastUrl) {
                    lastUrl = dbUrl
                    trySend(dbUrl).isSuccess
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (lastUrl != DEFAULT_CHILD_PHOTO_URL) {
                    lastUrl = DEFAULT_CHILD_PHOTO_URL
                    trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                }
            }
        }

        dbRef.addValueEventListener(listener)

        // Periodic verification job - checks for both deletions AND re-uploads
        val job = viewModelScope.launch {
            while (coroutineContext.isActive) {
                delay(15000) // check every 15s - good balance
                try {
                    // Get current storage files to detect re-uploads
                    val storage = FirebaseStorage.getInstance().reference.child("Children Images")
                    val listResult = storage.listAll().await()
                    val storageFiles = listResult.items.map { it.name }

                    // Run repair to update URLs if images were re-uploaded
                    repairAllChildImages(storageFiles)

                    // Also verify current URL still exists (for deleted images)
                    val snapshot = dbRef.get().await()
                    val currentUrl = snapshot.getValue(String::class.java).orEmpty()
                    if (currentUrl.isNotBlank() && currentUrl != DEFAULT_CHILD_PHOTO_URL) {
                        try {
                            val storageRef = com.google.firebase.storage.FirebaseStorage
                                .getInstance()
                                .getReferenceFromUrl(currentUrl)
                            storageRef.metadata.await()
                        } catch (e: Exception) {
                            // Image was deleted, set to default
                            if (lastUrl != DEFAULT_CHILD_PHOTO_URL) {
                                dbRef.setValue(DEFAULT_CHILD_PHOTO_URL).await()
                                lastUrl = DEFAULT_CHILD_PHOTO_URL
                                trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("🔥", "Photo URL periodic check failed: ${e.message}")
                }
            }
        }
        
        awaitClose {
            dbRef.removeEventListener(listener)
            job.cancel()
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

