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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.firebase.storage.StorageException

/** Sanitizes a string to be used as a Firebase Realtime Database key.
 * Converts to lowercase and replaces non-alphanumeric characters with underscores.
 */
private fun sanitizeKey(input: String): String {
    return input.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
}

class ParentDashboardViewModel(
) : ViewModel() {

    // XXXXXXXXXX PRIVATE CONTEXT XXXXXXXXXX
    private val _parentKey = MutableStateFlow("") // Initialize as empty
    private val liveParentKey: StateFlow<String> = _parentKey // Use a flow to react to changes

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
    // CRITICAL FIX: Define childrenRef as a custom getter that reads the *current value* of the key flow
    private val parentRef: DatabaseReference
        get() = database.child("parents").child(_parentKey.value)

    private val childrenRef: DatabaseReference
        get() = parentRef.child("children")

    fun updateParentDisplayName(newName: String) {
        val sanitizedKey = sanitizeKey(newName)
        val oldKey = _parentKey.value
        val oldParentRef = database.child("parents").child(oldKey)

        // 1. Update displayName under the old node
        oldParentRef.child("_displayName").setValue(newName)
            .addOnSuccessListener { Log.d("🔥", "Parent displayName updated to '$newName'") }
            .addOnFailureListener { Log.e("🔥", "Failed to update displayName: ${it.message}") }

        // 2. Update UI immediately
        _parentDisplayName.value = newName

        // 3. If the key has changed, move the node and update the parentKey flow before deleting old node
        if (oldKey != sanitizedKey) {
            oldParentRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) return@addOnSuccessListener
                val parentData = snapshot.value

                val newParentRef = database.child("parents").child(sanitizedKey)
                newParentRef.setValue(parentData)
                    .addOnSuccessListener {
                        // Update the ViewModel's parent key BEFORE removing the old node
                        _parentKey.value = sanitizedKey
                        oldParentRef.removeValue()
                            .addOnSuccessListener { Log.d("🔥", "Parent node key renamed: $oldKey → $sanitizedKey") }
                            .addOnFailureListener { Log.e("🔥", "Failed to delete old parent node: ${it.message}") }
                    }
                    .addOnFailureListener { Log.e("🔥", "Failed to create new parent node: ${it.message}") }
            }.addOnFailureListener { Log.e("🔥", "Failed to fetch old parent node: ${it.message}") }
        }
    }

    fun initializeParent(rawParentName: String) {
        val initialKey = sanitizeKey(rawParentName)
        if (_parentKey.value.isEmpty()) {
            _parentKey.value = initialKey
            database.child("parents").child(initialKey).child("_displayName").get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    database.child("parents").child(initialKey).child("_displayName").setValue(rawParentName)
                }
            }
        }
    }

    private val _parentDisplayName = MutableStateFlow("")
    val parentDisplayName: StateFlow<String> get() = _parentDisplayName

    private val childrenEventListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            val key = snapshot.key ?: return

            // 1. Check if the node is empty or missing structure
            if (!snapshot.hasChild("displayName")) {
                val rawName = key.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                val defaultData = mapOf(
                    "active" to true,
                    "displayName" to rawName,
                    "eta" to "Arriving in 5 minutes",
                    "photoUrl" to DEFAULT_CHILD_PHOTO_URL,
                    "status" to "On Route"
                )
                // Automatically inject the structure into Firebase
                childrenRef.child(key).updateChildren(defaultData)
            }

            // 2. Mirror addition to global /students node if missing
            database.child("students").child(key).get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val displayName =
                        snapshot.child("displayName").getValue(String::class.java) ?: key
                    val parentName = _parentDisplayName.value
                    val studentData = mapOf(
                        "childId" to key,
                        "displayName" to displayName,
                        "parentName" to parentName,
                        "status" to "On Route",
                        "active" to true,
                        "eta" to snapshot.child("eta").getValue(String::class.java).orEmpty(),
                        "photoUrl" to snapshot.child("photoUrl").getValue(String::class.java)
                            .orEmpty()
                    )
                    database.child("students").child(key).setValue(studentData)
                        .addOnSuccessListener { Log.d("🔥", "Global student created: $key") }
                }
            }

            // 3. Update the UI list
            if (!_childrenKeys.value.contains(key)) {
                _childrenKeys.value = _childrenKeys.value + key
                Log.d("🔥", "Detected and Auto-Formatted manual entry: $key")
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
            val childKey = snapshot.key ?: return

            // 1. Remove from parent's UI immediately
            _childrenKeys.value = _childrenKeys.value.filter { it != childKey }
            Log.d("🔥", "Child removed from parent: $childKey")

            // 2. 🔥 CRITICAL: Mirror deletion to global students node
            database.child("students").child(childKey)
                .removeValue()
                .addOnSuccessListener {
                    Log.d("🔥", "Global student removed: $childKey")
                }
                .addOnFailureListener { e ->
                    Log.e("🔥", "Failed to remove student $childKey: ${e.message}")
                }
        }


        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* ignore */
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("🔥", "childrenEventListener cancelled: ${error.message}")
        }
    }

    init {
        // Bus location does not depend on parentKey, run immediately
        observeBusLocation()

        // CRITICAL FIX: Use the parentKey flow to set up all dependent listeners and jobs.
        // This runs only when _parentKey is first set to a non-empty value.
        viewModelScope.launch {
            liveParentKey.collectLatest { key ->
                if (key.isNotBlank()) {
                    val currentParentRef = database.child("parents").child(key)

                    currentParentRef.child("_displayName")
                        .addValueEventListener(object : ValueEventListener {
                            private var isMigrating = false
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val remoteName = snapshot.getValue(String::class.java) ?: return
                                _parentDisplayName.value = remoteName
                                val targetKey = sanitizeKey(remoteName)
                                if (key != targetKey && !isMigrating && targetKey.isNotEmpty()) {
                                    isMigrating = true
                                    currentParentRef.get().addOnSuccessListener { dataSnap ->
                                        if (dataSnap.exists()) {
                                            database.child("parents").child(targetKey)
                                                .setValue(dataSnap.value).addOnSuccessListener {
                                                    _parentKey.value = targetKey
                                                    currentParentRef.removeValue()
                                                        .addOnCompleteListener { isMigrating = false }
                                                }
                                        }
                                    }
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })

                    childrenRef.addChildEventListener(childrenEventListener)

                    childrenRef.addValueEventListener(object : ValueEventListener {
                        private val renameInProgress = mutableSetOf<String>()
                        override fun onDataChange(snapshot: DataSnapshot) {
                            for (childSnap in snapshot.children) {
                                val childKey = childSnap.key ?: continue
                                val displayName =
                                    childSnap.child("displayName").getValue(String::class.java)
                                        ?: continue
                                val normalizedNewKey = sanitizeKey(displayName)
                                if (childKey != normalizedNewKey && !renameInProgress.contains(
                                        childKey
                                    )
                                ) {
                                    renameInProgress.add(childKey)
                                    renameChildNode(
                                        oldKey = childKey,
                                        newKey = normalizedNewKey
                                    ) { renameInProgress.remove(childKey) }
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {}
                    })

                    viewModelScope.launch {
                        delay(5000)
                        autoCleanupDuplicates()
                    }

                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        while (true) {
                            try {
                                val storage = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("Children Images")
                                val listResult = com.google.android.gms.tasks.Tasks.await(storage.listAll())
                                val rawFileNames = listResult.items.map { it.name }
                                repairAllChildImages(rawFileNames)
                            } catch (e: Exception) {
                                android.util.Log.e("🔥", "Background Storage Monitor Error: ${e.message}")
                            }
                            kotlinx.coroutines.delay(10000)
                        }
                    }
                }
            }
        }
    }

    fun monitorStorageForChildImage(childKey: String) {
        if (activeStorageMonitors.contains(childKey)) return
        activeStorageMonitors.add(childKey)

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("Children Images/$childKey.png")

            liveParentKey.collectLatest { pKey ->
                if (pKey.isBlank()) return@collectLatest

                var imageFound = false
                while (!imageFound) {
                    try {
                        val url = storageRef.downloadUrl.await().toString()

                        database.child("parents")
                            .child(pKey)
                            .child("children")
                            .child(childKey)
                            .child("photoUrl")
                            .setValue(url)
                            .await()

                        imageFound = true
                        activeStorageMonitors.remove(childKey)
                    } catch (e: Exception) {
                        kotlinx.coroutines.delay(5000L)
                    }
                }
            }
        }
    }

    // Inside your ViewModel:
    private val activeStorageMonitors = mutableSetOf<String>()

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


    /** Initialize all children from a list of names */
    fun initializeChildrenFromList(childNames: List<String>) {
        // CRITICAL FIX: Since SignUpViewModel now creates all nodes under /parents,
        // we only need to ensure the listeners and UI are active.
        // We stop all data writes here to prevent race conditions and preserve the
        // single source of truth (SignUpViewModel).
        Log.d("🔥", "Initialization skipped writes. Listening to live data now.")
    }

    private fun renameChildNode(oldKey: String, newKey: String, onRenamed: (String) -> Unit = {}) {
        childrenRef.child(oldKey).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            val oldData = snapshot.value as? Map<*, *> ?: return@addOnSuccessListener
            val updatedData = oldData.toMutableMap()

            childrenRef.child(newKey).setValue(updatedData).addOnSuccessListener {
                viewModelScope.launch {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance().reference.child("Children Images")
                    storage.listAll().addOnSuccessListener { listResult ->
                        val storageFiles = listResult.items.map { it.name }
                        val normalizedFiles = storageFiles.associateBy {
                            it.substringBeforeLast(".").substringAfterLast("/").lowercase().replace(Regex("[^a-z0-9]"), "_")
                        }

                        val matchedFile = findBestImageMatch(newKey, normalizedFiles)
                        val encodedFileName = if (matchedFile != null) android.net.Uri.encode(matchedFile) else null
                        val verifiedUrl = if (encodedFileName == null) DEFAULT_CHILD_PHOTO_URL
                        else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$encodedFileName?alt=media"

                        childrenRef.child(newKey).child("photoUrl").setValue(verifiedUrl).addOnCompleteListener {
                            childrenRef.child(oldKey).removeValue().addOnSuccessListener {
                                database.child("decommissionedKeys").child(oldKey).setValue(true)
                                _childrenKeys.value = _childrenKeys.value.toMutableList().apply {
                                    remove(oldKey)
                                    if (!contains(newKey)) add(newKey)
                                }
                                onRenamed(newKey)
                                Log.d("🔥", "Transfer Complete: $oldKey -> $newKey with verified image.")
                            }
                        }
                    }
                }
            }.addOnFailureListener { error ->
                Log.e("🔥", "Rename failed: ${error.message}")
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

        val ref = childrenRef.child(key).child("eta")
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

        val ref = childrenRef.child(key).child("displayName")
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

        val ref = childrenRef.child(key).child("status")
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
        childrenRef.child(key).child("status").setValue(newStatus)
    }

    /** Send quick action message, accepts only the normalized KEY */
    fun sendQuickActionMessage(key: String, action: String, message: String) {
        // NOTE: No key calculation here, only use the provided key
        val messageRef = childrenRef.child(key).child("messages").push()
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

    fun repairAllChildImages(storageFiles: List<String>) {
        val normalizedFiles = storageFiles.associateBy { fileName ->
            fileName.substringBeforeLast(".").lowercase().trim().replace(Regex("[^a-z0-9]"), "_")
        }

        childrenRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) return@addOnSuccessListener

            snapshot.children.forEach { childSnap ->
                val key = childSnap.key ?: return@forEach
                val currentUrl = childSnap.child("photoUrl").getValue(String::class.java).orEmpty()
                val matchedFileName = findBestImageMatch(key, normalizedFiles)

                if (matchedFileName != null) {
                    val encodedName = android.net.Uri.encode(matchedFileName)
                    val newUrl = "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$encodedName?alt=media"

                    if (currentUrl != newUrl) {
                        childrenRef.child(key).child("photoUrl").setValue(newUrl).addOnSuccessListener {
                            Log.d("🔥", "AUTO-DETECT: Image updated for $key")
                        }
                    }
                } else {
                    if (!currentUrl.contains("defaultchild.png") && currentUrl.isNotBlank() && currentUrl != "null") {
                        childrenRef.child(key).child("photoUrl").setValue(DEFAULT_CHILD_PHOTO_URL).addOnSuccessListener {
                            Log.d("🔥", "AUTO-CLEAN: Image missing from storage for $key. Reverted to default.")
                        }
                    }
                }
            }
        }
    }

    /** Smart family matching - works with separated (ati_una_kuja) AND concatenated (atiunakuja) filenames */
    private fun findBestImageMatch(childKey: String, normalizedFiles: Map<String, String>): String? {
        val cleanKey = childKey.lowercase().trim()

        if (normalizedFiles.containsKey(cleanKey)) return normalizedFiles[cleanKey]

        val fuzzyMatch = normalizedFiles.entries.find { (imgKey, _) ->
            imgKey == cleanKey || imgKey.replace("_", "").contains(cleanKey.replace("_", "")) || cleanKey.replace("_", "").contains(imgKey.replace("_", ""))
        }
        if (fuzzyMatch != null) return fuzzyMatch.value

        val parts = cleanKey.split("_").filter { it.length >= 2 }
        if (parts.isEmpty()) return null

        return normalizedFiles.entries.find { (imgKey, _) ->
            parts.all { part -> imgKey.contains(part) }
        }?.value
    }

    /** Observe photoUrl flow - purely observational, no creation, no initial await() */
    fun getPhotoUrlFlow(key: String): StateFlow<String> {
        val photoFlow = MutableStateFlow(DEFAULT_CHILD_PHOTO_URL)

        viewModelScope.launch {
            // Whenever the parent key changes, restart the listener on the new path
            liveParentKey.collectLatest { pKey ->
                if (pKey.isBlank()) return@collectLatest

                val dbRef = database.child("parents").child(pKey).child("children").child(key).child("photoUrl")

                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val dbUrl = snapshot.getValue(String::class.java).orEmpty()
                        photoFlow.value = if (dbUrl.isNotBlank()) dbUrl else DEFAULT_CHILD_PHOTO_URL
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }

                dbRef.addValueEventListener(listener)

                // Keep listener alive until the parent key changes again or ViewModel cleared
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    dbRef.removeEventListener(listener)
                }
            }
        }

        return photoFlow
    }

    /** Fetch all storage files and repair */
    fun fetchAndRepairChildImages(storageFiles: List<String>) {
        viewModelScope.launch { repairAllChildImages(storageFiles) }
    }


    companion object {
        private const val DEFAULT_CHILD_PHOTO_URL =
            "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"
    }
}