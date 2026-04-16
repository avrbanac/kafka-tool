package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.AppSettings
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class SettingsRepository {
    private val logger: Logger = LoggerFactory.getLogger(SettingsRepository::class.java)

    private val configDir: File = File(System.getProperty("user.home"), ".config/kafka-tool")
    private val settingsFile: File = File(configDir, "settings.json")
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        configDir.mkdirs()
    }

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) {
            logger.info("No settings file found, using defaults")
            return AppSettings()
        }
        return try {
            val settings: AppSettings = json.decodeFromString<AppSettings>(settingsFile.readText())
            logger.info("Loaded settings from {}", settingsFile.absolutePath)
            settings
        } catch (e: Exception) {
            logger.error("Failed to load settings from {}, using defaults", settingsFile.absolutePath, e)
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
            logger.debug("Saved settings to {}", settingsFile.absolutePath)
        } catch (e: Exception) {
            logger.error("Failed to save settings to {}", settingsFile.absolutePath, e)
        }
    }
}
