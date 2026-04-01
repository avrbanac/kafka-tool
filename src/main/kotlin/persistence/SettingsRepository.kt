package persistence

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.AppSettings
import java.io.File

class SettingsRepository {
    private val configDir: File = File(System.getProperty("user.home"), ".config/kafka-tool")
    private val settingsFile: File = File(configDir, "settings.json")
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    init {
        configDir.mkdirs()
    }

    fun loadSettings(): AppSettings {
        if (!settingsFile.exists()) return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(settingsFile.readText())
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            settingsFile.writeText(json.encodeToString(settings))
        } catch (e: Exception) {
            // best-effort persistence
        }
    }
}
