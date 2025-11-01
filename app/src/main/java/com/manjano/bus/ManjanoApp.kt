package com.manjano.bus

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ManjanoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
            Log.d("🔥", "🔧 FirebaseApp initialized in ManjanoApp: [DEFAULT]")
            FirebaseDatabase.getInstance().setPersistenceEnabled(false)
            Log.d("🔥", "🔧 FirebaseDatabase persistence disabled in ManjanoApp")
        } catch (e: Exception) {
            Log.e("🔥", "❌ Firebase initialization failed in ManjanoApp: ${e.message}", e)
        }
    }
}