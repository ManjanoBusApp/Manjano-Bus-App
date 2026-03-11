package com.manjano.bus.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
private fun sanitizeKey(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

class ParentSignupViewModel : ViewModel() {

    private val rootRef = FirebaseDatabase.getInstance().reference
    private val firestore = FirebaseFirestore.getInstance()

    init {
        try {
            val firebaseApp = FirebaseApp.getInstance()
            Log.d("🔥", "ParentSignupViewModel Firebase initialized: ${firebaseApp.name}")
        } catch (e: Exception) {
            Log.e("🔥", "Firebase initialization failed", e)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            ContextCompat.getSystemService(context, ConnectivityManager::class.java)

        if (connectivityManager == null) {
            Log.e("🔥", "ConnectivityManager null")
            return false
        }

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun saveParentAndChildren(
        parentName: String,
        childrenNames: String,
        parentPhone: String,
        context: Context
    ) {

        Log.d("🔥", "ParentSignupViewModel save called")

        // --- Firestore Children Collection ---
        val childrenList =
            childrenNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val parentDocRef = firestore.collection("parents").document(parentPhone)

        parentDocRef.get().addOnSuccessListener { parentDoc ->

            if (!parentDoc.exists()) {
                Log.e("🔥", "Parent document not found in Firestore: $parentPhone")
                return@addOnSuccessListener
            }

            val schoolName = parentDoc.getString("school")
                ?: ""  // <-- important: use the exact field name "school"

            val childrenData = mutableMapOf<String, Any>(
                "parentName" to parentName,
                "schoolName" to schoolName
            )

            if (childrenList.size == 1) {
                childrenData["childName"] = childrenList.first()
            } else {
                childrenList.forEachIndexed { index, name ->
                    childrenData["childName${index + 1}"] = name
                }
            }

            firestore.collection("children")
                .document(parentPhone)
                .set(childrenData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("🔥", "Children collection updated in Firestore")
                }
                .addOnFailureListener {
                    Log.e("🔥", "Failed writing children collection", it)
                }
        }

        if (!isNetworkAvailable(context)) {
            Log.e("🔥", "No network connection")
            return
        }

        fun normalizeName(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9]"), "")

        try {

            val parentKey = sanitizeKey(parentName)

            val parentChildrenRef =
                rootRef.child("parents").child(parentKey).child("children")

            val childrenList =
                childrenNames.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val newChildKeys =
                childrenList.map { it.lowercase().replace(Regex("[^a-z0-9]"), "_") }

            parentChildrenRef.get().addOnSuccessListener { snapshot ->

                val existingKeys =
                    snapshot.children.mapNotNull { it.key }.toSet()

                val newKeysSet = newChildKeys.toSet()

                val keysToRemove = existingKeys - newKeysSet

                if (keysToRemove.isNotEmpty()) {

                    val deletionMap = mutableMapOf<String, Any?>()

                    keysToRemove.forEach { removedKey ->

                        deletionMap["parents/$parentKey/children/$removedKey"] = null
                        deletionMap["students/$removedKey"] = null
                    }

                    rootRef.updateChildren(deletionMap)
                }

                if (childrenList.isEmpty()) return@addOnSuccessListener

                val storage =
                    FirebaseStorage.getInstance().reference.child("Children Images")

                val imageBaseNames = mutableMapOf<String, String>()

                storage.listAll().addOnSuccessListener { listResult ->

                    listResult.items.forEach { item ->
                        val fullName = item.name
                        val baseName = fullName.substringBeforeLast('.')
                        imageBaseNames[normalizeName(baseName)] = fullName
                    }

                    childrenList.forEach { childName ->

                        val childKey =
                            childName.lowercase().replace(Regex("[^a-z0-9]"), "_")

                        val sanitizedChildName = normalizeName(childName)

                        val chosenBase = imageBaseNames.keys.find { key ->
                            val firebaseTokens = key.split(Regex("\\W+")).filter { it.isNotBlank() }
                            val childTokens =
                                sanitizedChildName.split(Regex("\\W+")).filter { it.isNotBlank() }
                            // Match if all firebaseTokens exist in childTokens OR vice versa
                            firebaseTokens.all { token -> childTokens.contains(token) } ||
                                    childTokens.all { token -> firebaseTokens.contains(token) }
                        }

                        val fileRef = if (chosenBase != null) {
                            storage.child(imageBaseNames[chosenBase]!!)
                        } else {
                            FirebaseStorage.getInstance().reference
                                .child("Default Image")
                                .child("defaultchild.png")
                        }

                        fileRef.downloadUrl.addOnCompleteListener { task ->

                            val finalPhotoUrl =
                                if (task.isSuccessful) task.result.toString()
                                else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"

                            val childData = hashMapOf(
                                "active" to true,
                                "childId" to childKey,
                                "displayName" to childName,
                                "eta" to "Arriving in 5 minutes",
                                "parentName" to parentName,
                                "photoUrl" to finalPhotoUrl,
                                "status" to "On Route"
                            )

                            val updates = hashMapOf<String, Any>(
                                "parents/$parentKey/children/$childKey" to childData,
                                "students/$childKey" to childData
                            )

                            rootRef.updateChildren(updates)
                                .addOnSuccessListener {

                                    Log.d(
                                        "🔥",
                                        "Schema Sync Success: $childKey for Parent: $parentName"
                                    )
                                }
                        }
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                "🔥",
                "ParentSignupViewModel CRITICAL FAILURE",
                e
            )
        }
    }
}