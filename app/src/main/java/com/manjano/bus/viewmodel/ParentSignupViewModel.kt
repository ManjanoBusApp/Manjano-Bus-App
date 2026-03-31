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

private fun sanitizeKey(name: String): String =
    name.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")

class ParentSignupViewModel : ViewModel() {

    private val rootRef = FirebaseDatabase.getInstance().reference

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
        context: Context,
        pickUpAddress: String = "",
        pickUpPlaceId: String = "",
        pickUpLat: Double = 0.0,
        pickUpLng: Double = 0.0,
        dropOffAddress: String = "",
        dropOffPlaceId: String = "",
        dropOffLat: Double = 0.0,
        dropOffLng: Double = 0.0
    ) {

        Log.d("🔥", "========== SAVE PARENT AND CHILDREN ==========")
        Log.d("🔥", "Parent Name: $parentName")
        Log.d("🔥", "Child Name: $childrenNames")
        Log.d("🔥", "pickUpAddress: $pickUpAddress")
        Log.d("🔥", "pickUpPlaceId: $pickUpPlaceId")
        Log.d("🔥", "pickUpLat: $pickUpLat")
        Log.d("🔥", "pickUpLng: $pickUpLng")
        Log.d("🔥", "dropOffAddress: $dropOffAddress")
        Log.d("🔥", "dropOffPlaceId: $dropOffPlaceId")
        Log.d("🔥", "dropOffLat: $dropOffLat")
        Log.d("🔥", "dropOffLng: $dropOffLng")
        Log.d("🔥", "=============================================")

        Log.d("🔥", "ParentSignupViewModel save called")

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

            rootRef.child("parents").get().addOnSuccessListener { allParentsSnapshot ->
                // Case-insensitive and trim-aware check for existing parent key
                val parentExists = allParentsSnapshot.children.any {
                    it.key?.equals(parentKey, ignoreCase = true) == true
                }

                if (parentExists) {
                    Log.d(
                        "🔥",
                        "Blockage: Parent $parentKey already exists in RDB. Preventing duplicate write."
                    )
                    return@addOnSuccessListener
                }

                parentChildrenRef.get().addOnSuccessListener { snapshot ->
                    val existingKeys = snapshot.children.mapNotNull { it.key }.toSet()
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
                    val storage = FirebaseStorage.getInstance().reference.child("Children Images")
                    val imageBaseNames = mutableMapOf<String, String>()

                    storage.listAll().addOnSuccessListener { listResult ->
                        listResult.items.forEach { item ->
                            val fullName = item.name
                            val baseName = fullName.substringBeforeLast('.')
                            imageBaseNames[normalizeName(baseName)] = fullName
                        }

                        childrenList.forEach { childName ->
                            val childKey = childName.lowercase().replace(Regex("[^a-z0-9]"), "_")
                            val sanitizedChildName = normalizeName(childName)
                            val chosenBase = imageBaseNames.keys.find { key ->
                                val firebaseTokens =
                                    key.split(Regex("\\W+")).filter { it.isNotBlank() }
                                val childTokens = sanitizedChildName.split(Regex("\\W+"))
                                    .filter { it.isNotBlank() }
                                firebaseTokens.all { token -> childTokens.contains(token) } ||
                                        childTokens.all { token -> firebaseTokens.contains(token) }
                            }

                            val fileRef = if (chosenBase != null) {
                                storage.child(imageBaseNames[chosenBase]!!)
                            } else {
                                FirebaseStorage.getInstance().reference.child("Default Image")
                                    .child("defaultchild.png")
                            }

                            fileRef.downloadUrl.addOnCompleteListener { task ->
                                val finalPhotoUrl = if (task.isSuccessful) task.result.toString()
                                else "https://firebasestorage.googleapis.com/v0/b/manjano-bus.firebasestorage.app/o/Default%20Image%2Fdefaultchild.png?alt=media"

                                val childData = hashMapOf(
                                    "active" to true,
                                    "childId" to childKey,
                                    "displayName" to childName,
                                    "eta" to "Arriving in 5 minutes",
                                    "parentName" to parentName,
                                    "photoUrl" to finalPhotoUrl,
                                    "status" to "On Route",
                                    "pickUpAddress" to pickUpAddress,
                                    "pickUpPlaceId" to pickUpPlaceId,
                                    "pickUpLat" to pickUpLat,
                                    "pickUpLng" to pickUpLng,
                                    "dropOffAddress" to dropOffAddress,
                                    "dropOffPlaceId" to dropOffPlaceId,
                                    "dropOffLat" to dropOffLat,
                                    "dropOffLng" to dropOffLng
                                )

                                Log.d("🔥", "childData contains pickUpLat: ${childData["pickUpLat"]}")
                                Log.d("🔥", "childData contains pickUpLng: ${childData["pickUpLng"]}")

                                Log.d("🔥", "FINAL childData to write: $childData")

                                val updates = hashMapOf<String, Any>(
                                    "parents/$parentKey/children/$childKey" to childData,
                                    "students/$childKey" to childData
                                )

                                Log.d("🔥", "FULL updates map: $updates")

                                rootRef.updateChildren(updates).addOnSuccessListener {
                                    Log.d(
                                        "🔥",
                                        "Schema Sync Success: $childKey for Parent: $parentName"
                                    )
                                }.addOnFailureListener { error ->
                                    Log.e("🔥", "Schema Sync FAILED: $childKey", error)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("🔥", "ParentSignupViewModel CRITICAL FAILURE", e)
        }
    }
}