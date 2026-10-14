package com.vansh.quicknotes.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vansh.quicknotes.crypto.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: SecretStore,
) {
    private object Keys {
        val baseUrl = stringPreferencesKey("ai_base_url")
        val model = stringPreferencesKey("ai_model")
        val transcriptionModel = stringPreferencesKey("ai_transcription_model")
        val serverTranscription = booleanPreferencesKey("ai_server_transcription")
        val accentArgb = longPreferencesKey("accent_argb")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val dailyRecap = stringPreferencesKey("daily_recap")
        val dailyRecapDay = longPreferencesKey("daily_recap_day")
        val featurePrefix = "ai_feature_"
    }

    data class Settings(
        val baseUrl: String = "",
        val model: String = "cora_chat",
        val hasApiKey: Boolean = false,
        val transcriptionModel: String = "whisper-1",
        val serverTranscription: Boolean = false,
        val accentArgb: Long = 0xFFFFD60AL,
        val reduceMotion: Boolean = false,
        val onboardingDone: Boolean = false,
        val dailyRecap: String? = null,
        val featureEnabled: (String) -> Boolean = { true },
    )

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[Keys.baseUrl].orEmpty(),
            model = prefs[Keys.model] ?: "cora_chat",
            hasApiKey = secretStore.hasApiKey(),
            transcriptionModel = prefs[Keys.transcriptionModel] ?: "whisper-1",
            serverTranscription = prefs[Keys.serverTranscription] == true,
            accentArgb = prefs[Keys.accentArgb] ?: 0xFFFFD60AL,
            reduceMotion = prefs[Keys.reduceMotion] == true,
            onboardingDone = prefs[Keys.onboardingDone] == true,
            dailyRecap = prefs[Keys.dailyRecap],
            featureEnabled = { feature -> prefs[androidx.datastore.preferences.core.booleanPreferencesKey(Keys.featurePrefix + feature)] ?: true },
        )
    }

    fun current(): Settings = kotlinx.coroutines.runBlocking { settings.first() }

    suspend fun setBaseUrl(v: String) = context.dataStore.edit { it[Keys.baseUrl] = v.trim() }
    suspend fun setModel(v: String) = context.dataStore.edit { it[Keys.model] = v.trim() }
    suspend fun setTranscriptionModel(v: String) = context.dataStore.edit { it[Keys.transcriptionModel] = v.trim() }
    suspend fun setServerTranscription(v: Boolean) = context.dataStore.edit { it[Keys.serverTranscription] = v }
    suspend fun setApiKey(v: String) = secretStore.setApiKey(v)
    suspend fun apiKey(): String? = secretStore.getApiKey()
    suspend fun setAccent(argb: Long) = context.dataStore.edit { it[Keys.accentArgb] = argb }
    suspend fun setReduceMotion(v: Boolean) = context.dataStore.edit { it[Keys.reduceMotion] = v }
    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[Keys.onboardingDone] = v }

    suspend fun setDailyRecap(text: String?, dayStartMillis: Long) = context.dataStore.edit {
        if (text == null) {
            it.remove(Keys.dailyRecap); it.remove(Keys.dailyRecapDay)
        } else {
            it[Keys.dailyRecap] = text; it[Keys.dailyRecapDay] = dayStartMillis
        }
    }
    suspend fun setFeature(feature: String, enabled: Boolean) = context.dataStore.edit { it[booleanPreferencesKey(Keys.featurePrefix + feature)] = enabled }
}
