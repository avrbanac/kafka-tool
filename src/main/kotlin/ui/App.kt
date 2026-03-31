package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import persistence.ProfileRepository
import state.AppState
import state.ClusterViewModel
import ui.sidebar.Sidebar
import ui.tabs.TabArea

@Composable
fun App() {
    val scope: CoroutineScope = rememberCoroutineScope()
    val profileRepository: ProfileRepository = remember { ProfileRepository() }
    val appState: AppState = remember { AppState() }
    val clusterViewModels: MutableMap<String, ClusterViewModel> = remember { mutableStateMapOf() }

    LaunchedEffect(Unit) {
        appState.setProfiles(profileRepository.loadProfiles())
    }

    val profiles by appState.profiles.collectAsState()

    LaunchedEffect(profiles) {
        profileRepository.saveProfiles(profiles)
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                appState = appState,
                clusterViewModels = clusterViewModels,
                scope = scope,
                modifier = Modifier.width(260.dp).fillMaxHeight()
            )
            VerticalDivider()
            TabArea(
                appState = appState,
                clusterViewModels = clusterViewModels,
                scope = scope,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
