package com.besklar.relatives.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import com.besklar.relatives.data.PersonRecords
import com.besklar.relatives.ui.list.PeopleListScreen
import com.besklar.relatives.ui.list.PeopleListViewModel
import com.besklar.relatives.ui.profile.ProfileScreen
import com.besklar.relatives.ui.profile.ProfileViewModel
import kotlinx.serialization.Serializable

@Serializable data object PeopleRoute
@Serializable data class ProfileRoute(val id: String)

@Composable
fun RelativesNavigation(records: PersonRecords, portraits: PortraitLoader) {
    val nav = rememberNavController()
    val listFactory = remember(records) { viewModelFactory { initializer { PeopleListViewModel(records) } } }
    val profileFactory = remember(records) {
        viewModelFactory { initializer { ProfileViewModel(createSavedStateHandle().toRoute<ProfileRoute>().id, records) } }
    }
    val openProfile: (String) -> Unit = { id ->
        val currentId = nav.currentBackStackEntry?.takeIf { it.destination.hasRoute<ProfileRoute>() }
            ?.toRoute<ProfileRoute>()?.id
        if (currentId != id) nav.navigate(ProfileRoute(id))
    }
    NavHost(navController = nav, startDestination = PeopleRoute) {
        composable<PeopleRoute> {
            val list: PeopleListViewModel = viewModel(factory = listFactory)
            PeopleListScreen(list.state.collectAsStateWithLifecycle().value, list::refresh, portraits, openProfile)
        }
        composable<ProfileRoute> {
            val profile: ProfileViewModel = viewModel(factory = profileFactory)
            ProfileScreen(profile.state.collectAsStateWithLifecycle().value, profile::refresh,
                { nav.popBackStack() }, openProfile, portraits)
        }
    }
}
