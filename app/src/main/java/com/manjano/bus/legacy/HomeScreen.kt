@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.manjano.bus.legacy

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manjano Home") }
            )
        }
    ) { padding ->
        Text(
            text = "Welcome to Manjano!",
            modifier = Modifier.padding(padding)
        )
    }
}
