package com.besklar.relatives

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.besklar.relatives.ui.RelativesTheme
import com.besklar.relatives.ui.RelativesNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RelativesApplication).container
        setContent {
            RelativesTheme {
                RelativesNavigation(container.repository, container.portraitLoader)
            }
        }
    }
}
