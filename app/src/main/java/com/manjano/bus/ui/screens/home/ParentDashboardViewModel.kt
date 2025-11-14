package com.manjano.bus.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ParentDashboardViewModel : ViewModel() {

    private val database =
        FirebaseDatabase.getInstance("https://manjano-bus-default-rtdb.firebaseio.com/")
            .reference

    // real-time children list (paste inside ParentDashboardViewModel, after `database`)
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

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
            // no-op here (we only care about add/remove for the dropdown keys)
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

    // attach listener (you may already have an init block — if so, put this line there; otherwise add an init{})
    init {
        childrenRef.addChildEventListener(childrenEventListener)
    }

    init {
        fixMismatchedDisplayNames()
    }

     /** Create child node ONLY if it doesn't exist; do NOT overwrite existing ETA.
     *  Important: at creation time we explicitly set photoUrl → DEFAULT_CHILD_PHOTO_URL
     *  to avoid any incorrect inference. Matching against Storage should happen
     *  separately when a verified list of storage filenames/URLs is available.
     */
    private fun createChildIfMissing(childKey: String, displayName: String) {
        val ref = database.child("children").child(childKey)
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Log.d("🔥", "🆕 Creating child node '$childKey' with displayName: $displayName")
                val defaultData = mapOf(
                    "displayName" to displayName,
                    "eta" to "Arriving in 5 minutes", // only used for new nodes
                    "status" to "On Route",
                    "messages" to emptyMap<String, Any>(),
                    // Ensure new nodes always get the canonical default photo url.
                    "photoUrl" to DEFAULT_CHILD_PHOTO_URL
                )
                ref.updateChildren(defaultData)
                    .addOnSuccessListener {
                        Log.d("🔥", "✅ Child '$childKey' created successfully (photoUrl set to default)")
                    }
                    .addOnFailureListener { e ->
                        Log.e("🔥", "❌ Failed to create child '$childKey': ${e.message}")
                    }
            } else {
                Log.d("🔥", "✅ Child '$childKey' already exists — no overwrite")
                // Only fill missing fields if necessary, without touching 'eta' or existing photoUrl.
                val updates = mutableMapOf<String, Any>()
                if (!snapshot.hasChild("displayName")) updates["displayName"] = displayName
                if (!snapshot.hasChild("status")) updates["status"] = "On Route"
                if (!snapshot.hasChild("messages")) updates["messages"] = emptyMap<String, Any>()
                // If photoUrl is missing, set to default (but do NOT overwrite an existing photoUrl)
                if (!snapshot.hasChild("photoUrl")) updates["photoUrl"] = DEFAULT_CHILD_PHOTO_URL

                if (updates.isNotEmpty()) {
                    Log.d("🔥", "🩹 Filling missing fields for '$childKey': $updates")
                    ref.updateChildren(updates)
                }
            }
        }.addOnFailureListener { error ->
            Log.e("🔥", "❌ Failed to check '$childKey': ${error.message}")
        }
    }

    /** Detect and fix mismatched child keys when displayName changes manually in Firebase */
    private fun fixMismatchedDisplayNames() {
        val childrenRef = database.child("children")

        childrenRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (childSnapshot in snapshot.children) {
                    val oldKey = childSnapshot.key ?: continue
                    val displayName =
                        childSnapshot.child("displayName").getValue(String::class.java) ?: continue

                    val newKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

                    // ✅ Rename only if necessary
                    if (oldKey != newKey) {
                        Log.d("🔥", "⚙️ Renaming '$oldKey' → '$newKey'")

                        renameChildNode(oldKey, newKey) { renamedKey ->
                            Log.d("🔥", "🔁 App now tracks '$renamedKey'")

                            // ⚡ Immediately refresh live listeners so UI switches seamlessly
                            viewModelScope.launch {
                                getEtaFlowByName(renamedKey)
                                getDisplayNameFlow(renamedKey)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("🔥", "❌ Error observing children: ${error.message}")
            }
        })
    }


    /** Rename a child node safely, update active listeners, and delete old node */
    private fun renameChildNode(oldKey: String, newKey: String, onRenamed: (String) -> Unit = {}) {
        val childrenRef = database.child("children")

        childrenRef.child(oldKey).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Log.w("🔥", "⚠️ No data found for oldKey='$oldKey', skipping rename")
                return@addOnSuccessListener
            }

            val oldData = snapshot.value

            // 1️⃣ Copy all data to the new node
            childrenRef.child(newKey).setValue(oldData)
                .addOnSuccessListener {
                    Log.d("🔥", "✅ Data copied from '$oldKey' → '$newKey'")

                    // 2️⃣ Immediately reattach app listeners to the new node
                    viewModelScope.launch {
                        Log.d("🔥", "🔁 Switching app listeners to '$newKey'")
                        getEtaFlowByName(newKey)
                        getDisplayNameFlow(newKey)
                    }

                    // 3️⃣ Wait a moment to ensure flows stabilize
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)

                        // 4️⃣ Delete the old node safely
                        childrenRef.child(oldKey).removeValue()
                            .addOnSuccessListener {
                                Log.d("🔥", "🧹 Deleted old node '$oldKey' after rename")
                                onRenamed(newKey)
                            }
                            .addOnFailureListener { e ->
                                Log.e("🔥", "❌ Failed to delete old node '$oldKey': ${e.message}")
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("🔥", "❌ Failed to copy data to '$newKey': ${e.message}")
                }
        }.addOnFailureListener { e ->
            Log.e("🔥", "❌ Error fetching oldKey='$oldKey': ${e.message}")
        }
    }


    private fun ViewModel.addCloseableListener(
        ref: DatabaseReference,
        listener: ValueEventListener
    ) {
        this.addCloseable {
            ref.removeEventListener(listener)
        }
    }

    private fun ViewModel.addCloseable(onCleared: () -> Unit) {
        val vm = this
        vm.viewModelScope.launch {
            try {
                kotlinx.coroutines.delay(Long.MAX_VALUE)
            } finally {
                onCleared()
            }
        }
    }

    /** Observe ETA updates (persistent real-time listener, no duplicate logs) */
    fun getEtaFlowByName(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "🔍 Observing ETA for '$key' (original: $childName)")

        val etaFlow = MutableStateFlow("Loading...")

        // Ensure child exists only once
        viewModelScope.launch {
            createChildIfMissing(key, childName)
        }

        val ref = database.child("children").child(key).child("eta")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val eta = snapshot.getValue(String::class.java) ?: "Arriving in 5 minutes"
                // Only update if value changed
                if (etaFlow.value != eta) {
                    Log.d("🔥", "✅ ETA update from Firebase: '$eta'")
                    etaFlow.value = eta
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (etaFlow.value != "Error loading ETA") {
                    Log.e("🔥", "❌ Failed to fetch ETA: ${error.message}")
                    etaFlow.value = "Error loading ETA"
                }
            }
        }

        // Attach listener
        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)

        return etaFlow
    }

    /** Observe displayName updates (real-time) */
    fun getDisplayNameFlow(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "🔍 Observing displayName for '$key' (original: $childName)")

        val nameFlow = MutableStateFlow("Loading...")

        // Attach listener
        val ref = database.child("children").child(key).child("displayName")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.getValue(String::class.java) ?: childName
                if (nameFlow.value != name) {
                    Log.d("🔥", "✅ displayName update from Firebase: '$name'")
                    nameFlow.value = name
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (nameFlow.value != "Error loading name") {
                    Log.e("🔥", "❌ Failed to fetch displayName: ${error.message}")
                    nameFlow.value = "Error loading name"
                }
            }
        }

        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)

        return nameFlow
    }

    /** Observe child's status updates (real-time) */
    fun getStatusFlow(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "🔍 Observing status for '$key' (original: $childName)")

        val statusFlow = MutableStateFlow("Loading...")

        val ref = database.child("children").child(key).child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java) ?: "Unknown"
                if (statusFlow.value != status) {
                    Log.d("🔥", "✅ Status update from Firebase: '$status'")
                    statusFlow.value = status
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (statusFlow.value != "Error loading status") {
                    Log.e("🔥", "❌ Failed to fetch status: ${error.message}")
                    statusFlow.value = "Error loading status"
                }
            }
        }

        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)

        return statusFlow
    }

    /** Update child's status (no child creation) */
    fun updateChildStatus(childName: String, newStatus: String) {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "🔍 Updating status for '$key' to '$newStatus'")
        database.child("children").child(key).child("status")
            .setValue(newStatus)
            .addOnSuccessListener {
                Log.d("🔥", "✅ Status updated for '$key'")
            }
            .addOnFailureListener { e ->
                Log.e("🔥", "❌ Failed to update status: ${e.message}")
            }
    }

    /** Send quick action message (no child creation) */
    fun sendQuickActionMessage(childName: String, action: String, message: String) {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "📩 Sending message for '$key' ($action): $message")

        val messageRef = database.child("children").child(key).child("messages").push()
        val msgData = mapOf(
            "action" to action,
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )

        messageRef.setValue(msgData)
            .addOnSuccessListener {
                Log.d("🔥", "✅ Message sent for '$key'")
            }
            .addOnFailureListener { e ->
                Log.e("🔥", "❌ Failed to send message: ${e.message}")
            }
    }

    /** Calculate Levenshtein distance between two strings */
    private fun levenshteinDistance(lhs: String, rhs: String): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        val dp = Array(lhsLength + 1) { IntArray(rhsLength + 1) }

        for (i in 0..lhsLength) dp[i][0] = i
        for (j in 0..rhsLength) dp[0][j] = j

        for (i in 1..lhsLength) {
            for (j in 1..rhsLength) {
                val cost = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[lhsLength][rhsLength]
    }

    companion object {
        private const val DEFAULT_CHILD_PHOTO_URL =
            "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"
    }


    /** Assign each child's photo strictly by exact match; fallback to default if none found */
    private fun saveChildImage(childKey: String, displayName: String, storageFiles: List<String>) {
        val normalizedChild = childKey.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

        // ✅ Create a map of exact normalized file names
        val normalizedFiles = storageFiles.associateBy {
            it.substringBeforeLast(".")
                .substringAfterLast("/")               // remove folder path
                .lowercase()                           // standardize case
                .replace(Regex("[^a-z0-9]"), "_")      // normalize symbols/spaces
        }

        // ✅ Exact lookup only — get the file that exactly matches this child
        val matchedFile = normalizedFiles[normalizedChild]

        // ✅ Only use exact match; no partial or substring matching
        val finalUrl = if (matchedFile == null) {
            // fallback to explicit Default Image file
            DEFAULT_CHILD_PHOTO_URL
        } else {
            // matchedFile is the filename in Children Images — include the folder path in the public URL
            "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"
        }


        Log.d("🔥", "✅ Saved '$childKey' → ${if (matchedFile == null) "a.png" else matchedFile}")

        database.child("children").child(normalizedChild).child("photoUrl")
            .setValue(finalUrl)
            .addOnSuccessListener {
                Log.d("🔥", "✅ Photo URL saved for '$childKey'")
            }
            .addOnFailureListener { e ->
                Log.e("🔥", "❌ Failed to save photo URL for '$childKey': ${e.message}")
            }
    }

    /** Step 2: Scan all children and repair photoUrl links using verified storage files */
    fun repairAllChildImages(storageFiles: List<String>) {
        val normalizedFiles = storageFiles.associateBy {
            it.substringBeforeLast(".")
                .substringAfterLast("/")               // remove folder path
                .lowercase()                           // standardize case
                .replace(Regex("[^a-z0-9]"), "_")      // normalize symbols/spaces
        }

        database.child("children").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    snapshot.children.forEach { childSnap ->
                        val displayName = childSnap.child("displayName").getValue(String::class.java) ?: return@forEach
                        val normalizedKey = displayName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

                        val matchedFile = normalizedFiles[normalizedKey]
                        val currentUrl = childSnap.child("photoUrl").getValue(String::class.java)

                        if (matchedFile != null) {
                            val verifiedUrl =
                                "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Children%20Images%2F$matchedFile?alt=media"
                            if (currentUrl != verifiedUrl) {
                                database.child("children").child(normalizedKey).child("photoUrl").setValue(verifiedUrl)
                                Log.d("🧩 repairAllChildImages", "✅ Updated $displayName → $verifiedUrl")
                            }

                        } else {
                            // no matching image file found — explicitly set the new default image
                            database.child("children").child(normalizedKey).child("photoUrl")
                                .setValue(DEFAULT_CHILD_PHOTO_URL)
                            Log.d("🧩 repairAllChildImages", "🩹 Set $displayName → DEFAULT")
                        }
                    }
                } else {
                    Log.w("🧩 repairAllChildImages", "⚠️ No children found in database.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("🧩 repairAllChildImages", "❌ Failed to read children: ${e.message}")
            }
    }

    // Make getPhotoUrlFlow accept the current storage file list so UI reacts to deletions/uploads immediately
    fun getPhotoUrlFlow(childName: String) = callbackFlow<String> {
        val normalizedKey = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        val dbRef = database.child("children").child(normalizedKey).child("photoUrl")

        // Emit default immediately to avoid grey circle
        trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val dbUrl = snapshot.getValue(String::class.java).orEmpty()

                // If DB has no URL, immediately send default
                if (dbUrl.isBlank()) {
                    trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                    return
                }

                // Try to resolve the Storage reference from the DB URL and verify it exists.
                try {
                    val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
                    val storageRef = try {
                        storage.getReferenceFromUrl(dbUrl)
                    } catch (e: Exception) {
                        null
                    }

                    if (storageRef == null) {
                        // URL wasn't a valid storage URL — switch to default and correct DB
                        trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                        database.child("children").child(normalizedKey).child("photoUrl")
                            .setValue(DEFAULT_CHILD_PHOTO_URL)
                        return
                    }

                    // Check metadata to ensure the file still exists in Storage
                    storageRef.metadata
                        .addOnSuccessListener {
                            // File exists — emit the original DB URL (so Coil can load it)
                            trySend(dbUrl).isSuccess
                        }
                        .addOnFailureListener { _ ->
                            // File missing or inaccessible — set DB to default and emit default
                            trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                            database.child("children").child(normalizedKey).child("photoUrl")
                                .setValue(DEFAULT_CHILD_PHOTO_URL)
                        }
                } catch (e: Exception) {
                    // Any unexpected failure -> fall back to default
                    trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
                }
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(DEFAULT_CHILD_PHOTO_URL).isSuccess
            }
        }

        dbRef.addValueEventListener(listener)
        awaitClose { dbRef.removeEventListener(listener) }
    }.distinctUntilChanged()

    // Fetch all image filenames from Firebase Storage, then repair photoUrl links
    fun fetchAndRepairChildImages(storageFiles: List<String>) {
        Log.d("ParentDashboard", "✅ Listed ${storageFiles.size} files from Storage")
        viewModelScope.launch {
            repairAllChildImages(storageFiles)
        }
    }
}
