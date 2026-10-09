package com.besklar.relatives.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
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
@Serializable data class ProfileRoute(val id: String, val previewName: String = "",
    val previewLifespan: String = "", val previewPortraitPath: String = "", val transitionKey: String = "")

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelativesNavigation(records: PersonRecords, portraits: PortraitLoader) {
    val nav = rememberNavController()
    val listFactory = remember(records) { viewModelFactory { initializer { PeopleListViewModel(records) } } }
    val profileFactory = remember(records) {
        viewModelFactory { initializer { ProfileViewModel(createSavedStateHandle().toRoute<ProfileRoute>().id, records) } }
    }
    SharedTransitionLayout {
        val shared = this
        val openProfile: (ProfilePreview, String) -> Unit = { preview, key ->
            val current = nav.currentBackStackEntry
            val currentId = current?.takeIf { it.destination.hasRoute<ProfileRoute>() }
                ?.toRoute<ProfileRoute>()?.id
            // Ignore a second tap while the first destination is entering.
            if (currentId != preview.id && current?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                nav.navigate(ProfileRoute(preview.id, preview.name, preview.lifespan, preview.portraitPath, key))
            }
        }
        CompositionLocalProvider(LocalPortraitTransitionActive provides shared.isTransitionActive) {
            NavHost(navController = nav, startDestination = PeopleRoute,
                enterTransition = { fadeIn(tween(300)) }, exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(300)) }, popExitTransition = { fadeOut(tween(300)) }) {
                composable<PeopleRoute> { entry ->
                    val list: PeopleListViewModel = viewModel(factory = listFactory)
                    val visibility = this
                    PeopleListScreen(list.state.collectAsStateWithLifecycle().value, list::refresh, portraits,
                        onOpenPreview = { preview, slot -> openProfile(preview, "${entry.id}:$slot") },
                        portraitModifier = { _, slot -> with(shared) {
                            Modifier.sharedElement(rememberSharedContentState("${entry.id}:$slot"), visibility,
                                boundsTransform = { _, _ -> tween(300) },
                                clipInOverlayDuringTransition = OverlayClip(CircleShape))
                        } })
                }
                composable<ProfileRoute> { entry ->
                    val route = entry.toRoute<ProfileRoute>()
                    val profile: ProfileViewModel = viewModel(factory = profileFactory)
                    val visibility = this
                    ProfileScreen(profile.state.collectAsStateWithLifecycle().value, profile::refresh,
                        { nav.popBackStack() }, {}, portraits,
                        preview = route.previewName.takeIf { it.isNotBlank() }?.let {
                            ProfilePreview(route.id, it, route.previewLifespan, route.previewPortraitPath)
                        },
                        onOpenPreview = { preview, slot -> openProfile(preview, "${entry.id}:$slot") },
                        portraitModifier = { _, slot -> with(shared) {
                            val key = if (slot == "hero") route.transitionKey else "${entry.id}:$slot"
                            if (key.isBlank()) Modifier else Modifier.sharedElement(rememberSharedContentState(key), visibility,
                                boundsTransform = { _, _ -> tween(300) },
                                clipInOverlayDuringTransition = OverlayClip(CircleShape))
                        } })
                }
            }
        }
    }
}
