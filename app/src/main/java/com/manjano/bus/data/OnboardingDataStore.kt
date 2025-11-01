package com.manjano.bus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "onboarding_prefs"

// ✅ Correct extension property for DataStore
val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)

object OnboardingPrefs {
    private val FIRST_LAUNCH_KEY = booleanPreferencesKey("first_launch_done")

    fun isFirstLaunch(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            !(prefs[FIRST_LAUNCH_KEY] ?: false)
        }
    }

    suspend fun setFirstLaunchDone(context: Context) {
        context.dataStore.edit { prefs ->
            prefs[FIRST_LAUNCH_KEY] = true
        }
    }
}
