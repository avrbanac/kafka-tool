package state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import model.ClusterProfile
import model.TabType
import model.displayName
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AppState {
    private val logger: Logger = LoggerFactory.getLogger(AppState::class.java)

    private val _profiles: MutableStateFlow<List<ClusterProfile>> = MutableStateFlow(emptyList())
    val profiles: StateFlow<List<ClusterProfile>> = _profiles.asStateFlow()

    private val _tabs: MutableStateFlow<List<TabType>> = MutableStateFlow(emptyList())
    val tabs: StateFlow<List<TabType>> = _tabs.asStateFlow()

    private val _activeTabId: MutableStateFlow<String?> = MutableStateFlow(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    fun setProfiles(profiles: List<ClusterProfile>) {
        _profiles.value = profiles
        logger.debug("Profiles set, count={}", profiles.size)
    }

    fun addProfile(profile: ClusterProfile) {
        _profiles.value = _profiles.value + profile
        logger.info("Profile added: '{}'", profile.name)
    }

    fun updateProfile(profile: ClusterProfile) {
        _profiles.value = _profiles.value.map { existing ->
            if (existing.id == profile.id) profile else existing
        }
        logger.info("Profile updated: '{}'", profile.name)
    }

    fun removeProfile(id: String) {
        _profiles.value = _profiles.value.filter { it.id != id }
        logger.info("Profile removed: id={}", id)
    }

    fun openTab(tab: TabType) {
        // Deduplicate TopicManagement tabs per cluster
        if (tab is TabType.TopicManagement) {
            val existing: TabType.TopicManagement? = _tabs.value
                .filterIsInstance<TabType.TopicManagement>()
                .find { it.profileId == tab.profileId }
            if (existing != null) {
                _activeTabId.value = existing.id
                logger.debug("Reused existing TopicManagement tab: id={}", existing.id)
                return
            }
        }
        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
        logger.info("Tab opened: {} (id={})", tab.displayName(), tab.id)
    }

    fun closeTab(id: String) {
        _tabs.value = _tabs.value.filter { it.id != id }
        if (_activeTabId.value == id) {
            _activeTabId.value = _tabs.value.lastOrNull()?.id
        }
        logger.info("Tab closed: id={}", id)
    }

    fun setActiveTab(id: String) {
        _activeTabId.value = id
    }
}
