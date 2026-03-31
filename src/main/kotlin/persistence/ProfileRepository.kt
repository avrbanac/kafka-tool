package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.ClusterProfile
import java.io.File

class ProfileRepository {
    private val configDir: File = File(System.getProperty("user.home"), ".config/kafka-tool")
    private val profilesFile: File = File(configDir, "profiles.json")
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        configDir.mkdirs()
    }

    fun loadProfiles(): List<ClusterProfile> {
        if (!profilesFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<ClusterProfile>>(profilesFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<ClusterProfile>) {
        try {
            profilesFile.writeText(json.encodeToString(profiles))
        } catch (e: Exception) {
            // best-effort persistence
        }
    }
}
