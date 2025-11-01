package com.manjano.bus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manjano.bus.ui.ManjanoAppUI
import com.manjano.bus.ui.theme.ManjanoTheme
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("🔥", "🔧 MainActivity onCreate called")
        enableEdgeToEdge()
        setContent {
            ManjanoTheme {
                ManjanoAppUI()
            }
        }
    }
}