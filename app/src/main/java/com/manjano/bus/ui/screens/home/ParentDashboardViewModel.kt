package com.manjano.bus.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) { /* ignore */ }

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

    /** Create child node ONLY if it doesn't exist; do NOT overwrite existing ETA */
    private fun createChildIfMissing(childKey: String, displayName: String) {
        val ref = database.child("children").child(childKey)
        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Log.d("🔥", "🆕 Creating child node '$childKey' with displayName: $displayName")
                val defaultData = mapOf(
                    "displayName" to displayName,
                    "eta" to "Arriving in 5 minutes", // only used for new nodes
                    "status" to "On Route",
                    "messages" to emptyMap<String, Any>()
                )
                ref.updateChildren(defaultData)
                    .addOnSuccessListener {
                        Log.d("🔥", "✅ Child '$childKey' created successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e("🔥", "❌ Failed to create child '$childKey': ${e.message}")
                    }
            } else {
                Log.d("🔥", "✅ Child '$childKey' already exists — no overwrite")
                // Only fill missing fields if necessary, without touching 'eta'
                val updates = mutableMapOf<String, Any>()
                if (!snapshot.hasChild("displayName")) updates["displayName"] = displayName
                if (!snapshot.hasChild("status")) updates["status"] = "On Route"
                if (!snapshot.hasChild("messages")) updates["messages"] = emptyMap<String, Any>()

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
    fun getPhotoUrlFlow(childName: String): StateFlow<String> {
        val key = childName.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
        Log.d("🔥", "🔍 Observing photoUrl for '$key' (original: $childName)")

        val photoFlow = MutableStateFlow("https://firebasestorage.googleapis.com/v0/b/manjano-bus.appspot.com/o/default_child.jpg?alt=media")
        val ref = database.child("children").child(key).child("photoUrl")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val url = snapshot.getValue(String::class.java) ?: photoFlow.value
                if (photoFlow.value != url) {
                    Log.d("🔥", "✅ Photo URL update from Firebase: '$url'")
                    photoFlow.value = url
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (photoFlow.value != "Error loading photo") {
                    Log.e("🔥", "❌ Failed to fetch photoUrl: ${error.message}")
                    photoFlow.value = "Error loading photo"
                }
            }
        }

        ref.addValueEventListener(listener)
        addCloseableListener(ref, listener)

        return photoFlow
    }
}

