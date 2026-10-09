package com.besklar.relatives

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.besklar.relatives.ui.list.PeopleListScreen
import com.besklar.relatives.ui.list.PeopleListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RelativesApplication).container
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(PeopleListViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return PeopleListViewModel(container.repository) as T
            }
        }
        setContent {
            val list: PeopleListViewModel = viewModel(factory = factory)
            MaterialTheme {
                PeopleListScreen(list.state.collectAsStateWithLifecycle().value, list::refresh, container.portraitLoader)
            }
        }
    }
}
