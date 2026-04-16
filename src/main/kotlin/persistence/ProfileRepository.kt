package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.ClusterProfile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class ProfileRepository {
    private val logger: Logger = LoggerFactory.getLogger(ProfileRepository::class.java)

    private val configDir: File = File(System.getProperty("user.home"), ".config/kafka-tool")
    private val profilesFile: File = File(configDir, "profiles.json")
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        configDir.mkdirs()
        logger.debug("Profile storage directory: {}", configDir.absolutePath)
    }

    fun loadProfiles(): List<ClusterProfile> {
        if (!profilesFile.exists()) {
            logger.info("No profiles file found, starting with empty list")
            return emptyList()
        }
        return try {
            val profiles: List<ClusterProfile> = json.decodeFromString<List<ClusterProfile>>(profilesFile.readText())
            logger.info("Loaded {} profile(s) from {}", profiles.size, profilesFile.absolutePath)
            profiles
        } catch (e: Exception) {
            logger.error("Failed to load profiles from {}", profilesFile.absolutePath, e)
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<ClusterProfile>) {
        try {
            profilesFile.writeText(json.encodeToString(profiles))
            logger.debug("Saved {} profile(s) to {}", profiles.size, profilesFile.absolutePath)
        } catch (e: Exception) {
            logger.error("Failed to save profiles to {}", profilesFile.absolutePath, e)
        }
    }
}
