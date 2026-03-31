package state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.ClusterProfile
import model.TabType

class AppState {
    private val _profiles: MutableStateFlow<List<ClusterProfile>> = MutableStateFlow(emptyList())
    val profiles: StateFlow<List<ClusterProfile>> = _profiles.asStateFlow()

    private val _tabs: MutableStateFlow<List<TabType>> = MutableStateFlow(emptyList())
    val tabs: StateFlow<List<TabType>> = _tabs.asStateFlow()

    private val _activeTabId: MutableStateFlow<String?> = MutableStateFlow(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    fun setProfiles(profiles: List<ClusterProfile>) {
        _profiles.value = profiles
    }

    fun addProfile(profile: ClusterProfile) {
        _profiles.value = _profiles.value + profile
    }

    fun updateProfile(profile: ClusterProfile) {
        _profiles.value = _profiles.value.map { existing ->
            if (existing.id == profile.id) profile else existing
        }
    }

    fun removeProfile(id: String) {
        _profiles.value = _profiles.value.filter { it.id != id }
    }

    fun openTab(tab: TabType) {
        // Deduplicate TopicManagement tabs per cluster
        if (tab is TabType.TopicManagement) {
            val existing: TabType.TopicManagement? = _tabs.value
                .filterIsInstance<TabType.TopicManagement>()
                .find { it.profileId == tab.profileId }
            if (existing != null) {
                _activeTabId.value = existing.id
                return
            }
        }
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
    }

    fun closeTab(id: String) {
        _tabs.value = _tabs.value.filter { it.id != id }
        if (_activeTabId.value == id) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
    }

    fun setActiveTab(id: String) {
        _activeTabId.value = id
    }
}
