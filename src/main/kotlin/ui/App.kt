package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import model.AppSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import persistence.ProfileRepository
import persistence.SettingsRepository
import state.AppState
import state.ClusterViewModel
import ui.sidebar.Sidebar
import ui.tabs.TabArea

private val logger: Logger = LoggerFactory.getLogger("ui.App")

@Composable
fun App() {
    val scope: CoroutineScope = rememberCoroutineScope()
    val profileRepository: ProfileRepository = remember { ProfileRepository() }
    val settingsRepository: SettingsRepository = remember { SettingsRepository() }
    val appState: AppState = remember { AppState() }
    val clusterViewModels: MutableMap<String, ClusterViewModel> = remember { mutableStateMapOf() }
    var isDarkTheme: Boolean by remember { mutableStateOf(settingsRepository.loadSettings().darkTheme) }

    LaunchedEffect(Unit) {
        logger.info("App initialized, darkTheme={}", isDarkTheme)
        appState.setProfiles(profileRepository.loadProfiles())
    }

    val profiles by appState.profiles.collectAsState()

    LaunchedEffect(profiles) {
        profileRepository.saveProfiles(profiles)
    }

    LaunchedEffect(isDarkTheme) {
        settingsRepository.saveSettings(AppSettings(darkTheme = isDarkTheme))
    }

    val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalScrollbarStyle provides ScrollbarStyle(
                minimalHeight = 16.dp,
                thickness = 8.dp,
                shape = MaterialTheme.shapes.small,
                hoverDurationMillis = 300,
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f),
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Sidebar(
                        appState = appState,
                        clusterViewModels = clusterViewModels,
                        scope = scope,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme },
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
    }
}
