package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.privilege.PrivilegeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "appforge_prefs")

enum class ThemeSetting { SYSTEM, DARK, LIGHT }

data class UserPrefs(
    val preferredMode: PrivilegeMode = PrivilegeMode.SHIZUKU,
    val autoBackupUserApks: Boolean = true,
    val disableCompetingOnForceDefault: Boolean = false,
    val themeSetting: ThemeSetting = ThemeSetting.SYSTEM
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val PREFERRED_MODE = stringPreferencesKey("preferred_mode")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup_user_apks")
        val DISABLE_COMPETING = booleanPreferencesKey("disable_competing_on_default")
        val THEME = stringPreferencesKey("theme_setting")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { data ->
        val mode = data[Keys.PREFERRED_MODE]?.let {
            runCatching { PrivilegeMode.valueOf(it) }.getOrNull()
        } ?: PrivilegeMode.SHIZUKU
        val theme = data[Keys.THEME]?.let {
            runCatching { ThemeSetting.valueOf(it) }.getOrNull()
        } ?: ThemeSetting.SYSTEM
        UserPrefs(
            preferredMode = mode,
            autoBackupUserApks = data[Keys.AUTO_BACKUP] ?: true,
            disableCompetingOnForceDefault = data[Keys.DISABLE_COMPETING] ?: false,
            themeSetting = theme
        )
    }

    suspend fun setPreferredMode(mode: PrivilegeMode) {
        context.dataStore.edit { it[Keys.PREFERRED_MODE] = mode.name }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setDisableCompeting(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISABLE_COMPETING] = enabled }
    }

    suspend fun setTheme(theme: ThemeSetting) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }
}
