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
            viewModelScope.launch {
                _childrenKeys.value = _childrenKeys.value.toMutableList().apply {
                    if (!contains(key)) {
                        add(key)
                        Log.d("🔥", "childrenEventListener: onChildAdded -> $key")
                    }
                }
            }
        }

        override fun onChildChanged(
            snapshot: DataSnapshot,
            previousChildName: String?
        ) {
            val key = snapshot.key ?: return
            viewModelScope.launch {
                // Force update the children keys to trigger UI refresh when display names change
                _childrenKeys.value = _childrenKeys.value.toMutableList().apply {
                    Log.d("🔥", "childrenEventListener: onChildChanged -> $key")
                }
            }
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {
            val key = snapshot.key ?: return
            viewModelScope.launch {
                _childrenKeys.value = _childrenKeys.value.toMutableList().apply {
                    if (remove(key)) {
                        Log.d("🔥", "childrenEventListener: onChildRemoved -> $key")
                    }
                }
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

// --- NEW: Load all keys immediately to prevent UI issues and provide live data fast ---
        loadAllChildrenKeys()

        // Run auto-cleanup once on startup (with delay to avoid race conditions)
        viewModelScope.launch {
            delay(5000) // Wait for initial data to load
            autoCleanupDuplicates()
        }

//  Single, clean, bulletproof display name monitor
        childrenRef.addValueEventListener(object : ValueEventListener {
            private val renameInProgress = mutableSetOf<String>() // Track renames to prevent loops

            override fun onDataChange(snapshot: DataSnapshot) {
                for (childSnap in snapshot.children) {
                    val key = childSnap.key ?: continue
                    val displayName = childSnap.child("displayName").getValue(String::class.java) ?: continue
                    val normalizedNewKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

                    if (key != normalizedNewKey && !renameInProgress.contains(key)) {
                        Log.d("🔥", "Auto-renaming child node: $key → $normalizedNewKey (displayName = '$displayName')")
                        renameInProgress.add(key)

                        renameChildNode(oldKey = key, newKey = normalizedNewKey) { newKey ->
                            renameInProgress.remove(key)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("🔥", "Global displayName monitor cancelled: ${error.message}")
            }
        })

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

    fun getValidChildNames(): StateFlow<List<String>> {
        val result = MutableStateFlow<List<String>>(emptyList())

        childrenRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val names = mutableListOf<String>()
                snapshot.children.forEach { childSnap ->
                    val displayName = childSnap.child("displayName").getValue(String::class.java)
                    val key = childSnap.key

                    if (displayName != null && key != null) {
                        val correctKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
                        // Only include if key is correct
                        if (key == correctKey) {
                            names.add(displayName)
                        }
                    }
                }
                result.value = names.sorted()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("🔥", "Failed to get valid child names: ${error.message}")
            }
        })

        return result
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
    /** Automatically clean up duplicate nodes (call once on app start) */
    fun autoCleanupDuplicates() {
        childrenRef.get().addOnSuccessListener { snapshot ->
            Log.d("🔥", "Starting auto-cleanup for ${snapshot.childrenCount} children")

            val displayNameToKeyMap = mutableMapOf<String, String>() // displayName → correctKey
            val duplicatesToDelete = mutableListOf<String>()
            val nodesToRename = mutableListOf<Pair<String, String>>() // (oldKey, newKey)

            // First pass: identify duplicates and incorrect keys
            snapshot.children.forEach { childSnap ->
                val key = childSnap.key ?: return@forEach
                val displayName = childSnap.child("displayName").getValue(String::class.java) ?: return@forEach
                val correctKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

                // Case 1: Key is wrong but not a duplicate yet
                if (key != correctKey && !displayNameToKeyMap.containsValue(correctKey)) {
                    nodesToRename.add(Pair(key, correctKey))
                    displayNameToKeyMap[displayName] = correctKey
                }
                // Case 2: Duplicate displayName (same child, different keys)
                else if (displayNameToKeyMap.containsKey(displayName)) {
                    val existingKey = displayNameToKeyMap[displayName]!!
                    Log.d("🔥", "Found duplicate: $key has same displayName as $existingKey ('$displayName')")

                    // Keep the one with correct key, delete others
                    if (correctKey == existingKey) {
                        duplicatesToDelete.add(key) // Delete the wrong key
                    } else {
                        duplicatesToDelete.add(existingKey) // Delete the old wrong key
                        displayNameToKeyMap[displayName] = correctKey
                    }
                }
                // Case 3: First time seeing this displayName
                else {
                    displayNameToKeyMap[displayName] = key
                }
            }

            Log.d("🔥", "Found ${nodesToRename.size} to rename, ${duplicatesToDelete.size} to delete")

            // Execute renames (with delay to avoid Firebase limits)
            nodesToRename.forEachIndexed { index, (oldKey, newKey) ->
                viewModelScope.launch {
                    delay(index * 100L) // Stagger requests
                    renameChildNode(oldKey, newKey)
                }
            }

            // Execute deletions
            duplicatesToDelete.forEachIndexed { index, key ->
                viewModelScope.launch {
                    delay(nodesToRename.size * 100L + index * 100L) // After renames
                    childrenRef.child(key).removeValue().addOnSuccessListener {
                        Log.d("🔥", "Auto-deleted duplicate: $key")
                    }
                }
            }
        }
    }
    private fun createChildIfMissing(childKey: String, displayName: String) {
        // BLOCK RESURRECTION: Never recreate a key that has been officially decommissioned
        val decommissionedRef = database.child("decommissionedKeys").child(childKey)
        decommissionedRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                Log.d("🔥", "BLOCKED resurrection of decommissioned key: $childKey")
                return@addOnSuccessListener
            }

            // Safe to proceed — key has never been renamed away
            val ref = database.child("children").child(childKey)
            ref.get().addOnSuccessListener { childSnapshot ->
                if (!childSnapshot.exists()) {
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
                    if (!childSnapshot.hasChild("displayName")) updates["displayName"] = displayName
                    if (!childSnapshot.hasChild("status")) updates["status"] = "On Route"
                    if (!childSnapshot.hasChild("messages")) updates["messages"] = emptyMap<String, Any>()
                    if (!childSnapshot.hasChild("photoUrl")) updates["photoUrl"] = DEFAULT_CHILD_PHOTO_URL
                    if (updates.isNotEmpty()) ref.updateChildren(updates)
                }
            }
        }
    }

    /** Initialize all children from a list of names */
    fun initializeChildrenFromList(childNames: List<String>) {
        childNames.forEach { childName ->
            val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
            createChildIfMissing(key, childName)
        }
        Log.d("🔥", "Initialized ${childNames.size} children in Firebase")
    }

    private fun renameChildNode(oldKey: String, newKey: String, onRenamed: (String) -> Unit = {}) {
        childrenRef.child(oldKey).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener
            val oldData = snapshot.value

            // Copy all data to new node
            childrenRef.child(newKey).setValue(oldData).addOnSuccessListener {
                // Remove old node immediately — with error handling and retry
                childrenRef.child(oldKey).removeValue()
                    .addOnSuccessListener {
                        Log.d("🔥", "Successfully deleted old node: $oldKey")
                        onRenamed(newKey)

                        // PERMANENTLY MARK OLD KEY AS DECOMMISSIONED
                        database.child("decommissionedKeys").child(oldKey).setValue(true)

                        // Update children keys list
                        viewModelScope.launch {
                            _childrenKeys.value = _childrenKeys.value.toMutableList().apply {
                                remove(oldKey)
                                if (!contains(newKey)) add(newKey)
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("🔥", "CRITICAL: Failed to delete old node $oldKey – GHOST NODE WARNING", exception)
                        // FORCE delete with direct reference (bypass possible listener interference)
                        database.child("children").child(oldKey).removeValue()
                            .addOnSuccessListener {
                                Log.w("🔥", "Force-deleted old node $oldKey after initial failure")
                            }
                            .addOnFailureListener { e2 ->
                                Log.e("🔥", "FINAL FAILURE: Could not delete $oldKey even with force", e2)
                            }
                    }
            }.addOnFailureListener { error ->
                Log.e("🔥", "Failed to rename node $oldKey -> $newKey: ${error.message}")
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

    /** Observe ETA updates – no creation, accepts only the normalized KEY */
    fun getEtaFlowByName(key: String): StateFlow<String> {
        // NOTE: The key is expected to be the correct, normalized key (e.g., 'dez_gatesh')
        val etaFlow = MutableStateFlow("Loading...")

        val ref = database.child("children").child(key).child("eta")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val eta = snapshot.getValue(String::class.java) ?: "Arriving in 5 minutes"
                if (etaFlow.value != eta) etaFlow.value = eta
            }

            override fun onCancelled(error: DatabaseError) {
                etaFlow.value = "Error loading ETA"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return etaFlow
    }

    /** Observe displayName updates – no creation, accepts only the normalized KEY */
    fun getDisplayNameFlow(key: String): StateFlow<String> {
        // NOTE: The key is expected to be the correct, normalized key (e.g., 'dez_gatesh')
        val nameFlow = MutableStateFlow("Loading...")

        val ref = database.child("children").child(key).child("displayName")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // If displayName is missing, use the key as a fallback, NOT the static name
                val name = snapshot.getValue(String::class.java) ?: key
                if (nameFlow.value != name) nameFlow.value = name
            }

            override fun onCancelled(error: DatabaseError) {
                nameFlow.value = "Error loading name"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return nameFlow
    }

    /** Observe child's status – no creation, accepts only the normalized KEY */
    fun getStatusFlow(key: String): StateFlow<String> {
        // NOTE: No key calculation here, only use the provided key
        val statusFlow = MutableStateFlow("Loading...")

        val ref = database.child("children").child(key).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "Unknown"
                if (statusFlow.value != status) statusFlow.value = status
            }

            override fun onCancelled(error: DatabaseError) {
                statusFlow.value = "Error loading status"
            }
        }
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)
        return statusFlow
    }


    /** Update child's status, accepts only the normalized KEY */
    fun updateChildStatus(key: String, newStatus: String) {
        // NOTE: No key calculation here, only use the provided key
        database.child("children").child(key).child("status").setValue(newStatus)
    }

    /** Send quick action message, accepts only the normalized KEY */
    fun sendQuickActionMessage(key: String, action: String, message: String) {
        // NOTE: No key calculation here, only use the provided key
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

    /** Repair all child images - WITHOUT changing display names! */
    fun repairAllChildImages(storageFiles: List<String>) {
        val normalizedFiles = storageFiles.associateBy {
            it.substringBeforeLast(".").substringAfterLast("/").lowercase().replace(Regex("[^a-z0-9]"), "_")
        }

        // Fetch decommissioned keys once for quick lookups
        database.child("decommissionedKeys").get().addOnSuccessListener { decommissionedSnap ->
            val decommissionedKeys = decommissionedSnap.children.mapNotNull { it.key }.toSet()

            database.child("children").get().addOnSuccessListener { snapshot ->
                // Process each child WITHOUT changing displayName
                snapshot.children.forEach { childSnap ->
                    val key = childSnap.key ?: return@forEach
                    // CRITICAL GUARDRAIL: Skip if this key has been decommissioned
                    if (decommissionedKeys.contains(key)) {
                        Log.w("🔥", "Skipping photo repair for decommissioned key: $key")
                        return@forEach
                    }

                    val displayName = childSnap.child("displayName").getValue(String::class.java)
                        ?: return@forEach
                    val currentUrl = childSnap.child("photoUrl").getValue(String::class.java).orEmpty()

                    // Use the ACTUAL key, not normalized from displayName
                    val matchedFile = findBestImageMatch(key, normalizedFiles)
                    val verifiedUrl = if (matchedFile == null) DEFAULT_CHILD_PHOTO_URL
                    else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"

                    // Only update photoUrl, NEVER displayName
                    val shouldUpdate = currentUrl != verifiedUrl

                    if (shouldUpdate) {
                        database.child("children").child(key).child("photoUrl").setValue(verifiedUrl)
                        Log.d("🔥", "✅ Updated photo for '$key' (displayName: '$displayName') → ${matchedFile ?: "default"}")
                    }
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

    /** Observe photoUrl flow - purely observational, no creation, no initial await() */
    fun getPhotoUrlFlow(key: String): StateFlow<String> {
        // NOTE: The key is already expected to be normalized and correct from the UI/childrenKeys
        val photoFlow = MutableStateFlow(DEFAULT_CHILD_PHOTO_URL)
        val dbRef = database.child("children").child(key).child("photoUrl")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbUrl = snapshot.getValue(String::class.java) ?: DEFAULT_CHILD_PHOTO_URL
                if (photoFlow.value != dbUrl) {
                    photoFlow.value = dbUrl
                }
            }

            override fun onCancelled(error: DatabaseError) {
                photoFlow.value = DEFAULT_CHILD_PHOTO_URL
            }
        }

        dbRef.addValueEventListener(listener)
        addCloseableListener(dbRef, listener)
        return photoFlow
    }

    /** Fetch all storage files and repair */
    fun fetchAndRepairChildImages(storageFiles: List<String>) {
        viewModelScope.launch { repairAllChildImages(storageFiles) }
    }
    /** Call this once from the screen to force-load all existing children keys */
    fun loadAllChildrenKeys() {
        childrenRef.get().addOnSuccessListener { snapshot ->
            val keys = snapshot.children.mapNotNull { it.key }
            viewModelScope.launch {
                _childrenKeys.value = keys.toList()
                Log.d("🔥", "Force-loaded ${keys.size} children keys: $keys")
            }
        }.addOnFailureListener {
            Log.e("🔥", "Failed to force-load children keys", it)
        }
    }

    // Add this public function — safe, no private access
    fun refreshChildrenKeys() {
        childrenRef.get().addOnSuccessListener { snapshot ->
            val keys = snapshot.children.mapNotNull { it.key }.toList()
            viewModelScope.launch {
                _childrenKeys.value = keys
                Log.d("🔥", "Refreshed ${keys.size} children keys → rename now works perfectly")
            }
        }
    }
    companion object {
        private const val DEFAULT_CHILD_PHOTO_URL =
            "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"
    }
}


