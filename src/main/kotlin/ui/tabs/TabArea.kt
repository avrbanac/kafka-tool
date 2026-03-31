package ui.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import model.ClusterProfile
import model.TabType
import state.AppState
import state.ClusterViewModel
import state.ConsumerViewModel
import state.ProducerViewModel

@Composable
fun TabArea(
    appState: AppState,
    clusterViewModels: Map<String, ClusterViewModel>,
    scope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val tabs: List<TabType> by appState.tabs.collectAsState()
    val activeTabId: String? by appState.activeTabId.collectAsState()
    val profiles: List<ClusterProfile> by appState.profiles.collectAsState()

    val consumerViewModels: MutableMap<String, ConsumerViewModel> = remember { mutableStateMapOf() }
    val producerViewModels: MutableMap<String, ProducerViewModel> = remember { mutableStateMapOf() }

    // Clean up view models for closed tabs
    LaunchedEffect(tabs) {
        val openIds: Set<String> = tabs.map { it.id }.toSet()
        val staleConsumers: List<String> = consumerViewModels.keys.filter { it !in openIds }
        staleConsumers.forEach { id ->
            consumerViewModels[id]?.stopConsuming()
            consumerViewModels.remove(id)
        }
        val staleProducers: List<String> = producerViewModels.keys.filter { it !in openIds }
        staleProducers.forEach { id ->
            producerViewModels[id]?.close()
            producerViewModels.remove(id)
        }
    }

    Column(modifier = modifier) {
        TabBar(
            tabs = tabs,
            activeTabId = activeTabId,
            onTabSelected = { id -> appState.setActiveTab(id) },
            onTabClosed = { id -> appState.closeTab(id) }
        )

        val activeTab: TabType? = tabs.find { it.id == activeTabId }

        Box(modifier = Modifier.fillMaxSize()) {
            if (activeTab == null) {
                Text(
                    text = "Connect to a cluster and select a topic to get started.",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                when (activeTab) {
                    is TabType.Consumer -> {
                        val profile: ClusterProfile? = profiles.find { it.id == activeTab.profileId }
                        if (profile != null) {
                            val vm: ConsumerViewModel = consumerViewModels.getOrPut(activeTab.id) {
                                ConsumerViewModel(profile.bootstrapServers, profile.id, profile.hostnameMapping, activeTab.topicName, scope)
                            }
                            ConsumerTab(viewModel = vm, modifier = Modifier.fillMaxSize())
                        }
                    }
                    is TabType.Producer -> {
                        val profile: ClusterProfile? = profiles.find { it.id == activeTab.profileId }
                        if (profile != null) {
                            val vm: ProducerViewModel = producerViewModels.getOrPut(activeTab.id) {
                                ProducerViewModel(profile.bootstrapServers, profile.id, profile.hostnameMapping, activeTab.topicName, scope)
                            }
                            ProducerTab(viewModel = vm, modifier = Modifier.fillMaxSize())
                        }
                    }
                    is TabType.TopicManagement -> {
                        val clusterVm: ClusterViewModel? = clusterViewModels[activeTab.profileId]
                        val profile: ClusterProfile? = profiles.find { it.id == activeTab.profileId }
                        if (clusterVm != null && profile != null) {
                            TopicManagementTab(
                                clusterViewModel = clusterVm,
                                profileName = profile.name,
                                onOpenProducer = { topicName ->
                                    appState.openTab(TabType.Producer(topicName = topicName, profileId = activeTab.profileId))
                                },
                                onOpenConsumer = { topicName ->
                                    appState.openTab(TabType.Consumer(topicName = topicName, profileId = activeTab.profileId))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
