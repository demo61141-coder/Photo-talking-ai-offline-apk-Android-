package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.model.BackgroundFillMode
import com.example.model.StudioSettings
import com.example.model.VideoAspectRatio
import com.example.model.VideoResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val MOUTH_SENSITIVITY = floatPreferencesKey("mouth_sensitivity")
        val SILENCE_THRESHOLD = floatPreferencesKey("silence_threshold")
        val SMOOTHING = floatPreferencesKey("smoothing")
        val BLINK_RATE = floatPreferencesKey("blink_rate")
        val HEAD_MOTION = floatPreferencesKey("head_motion")
        val BODY_BREATHING = floatPreferencesKey("body_breathing")
        val EMPHASIS_ZOOM = booleanPreferencesKey("emphasis_zoom")
        val DEFAULT_RES = stringPreferencesKey("default_resolution")
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val DEFAULT_FILL = stringPreferencesKey("default_fill")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val lang = prefs[Keys.LANGUAGE]?.let { code ->
            AppLanguage.entries.firstOrNull { it.code == code }
        } ?: AppLanguage.EN

        val res = prefs[Keys.DEFAULT_RES]?.let { name ->
            runCatching { VideoResolution.valueOf(name) }.getOrNull()
        } ?: VideoResolution.P720

        val aspect = prefs[Keys.DEFAULT_ASPECT]?.let { name ->
            runCatching { VideoAspectRatio.valueOf(name) }.getOrNull()
        } ?: VideoAspectRatio.RATIO_9_16

        val fill = prefs[Keys.DEFAULT_FILL]?.let { name ->
            runCatching { BackgroundFillMode.valueOf(name) }.getOrNull()
        } ?: BackgroundFillMode.BLURRED_PHOTO

        StudioSettings(
            language = lang,
            mouthSensitivity = prefs[Keys.MOUTH_SENSITIVITY] ?: 1.25f,
            silenceThreshold = prefs[Keys.SILENCE_THRESHOLD] ?: 0.08f,
            smoothingAmount = prefs[Keys.SMOOTHING] ?: 0.45f,
            blinkRateSeconds = prefs[Keys.BLINK_RATE] ?: 3.4f,
            headMotionStrength = prefs[Keys.HEAD_MOTION] ?: 0.70f,
            bodyBreathingStrength = prefs[Keys.BODY_BREATHING] ?: 0.65f,
            emphasisZoomEnabled = prefs[Keys.EMPHASIS_ZOOM] ?: true,
            defaultResolution = res,
            defaultAspectRatio = aspect,
            defaultFillMode = fill,
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30
        )
    }

    suspend fun update(transform: (StudioSettings) -> StudioSettings) {
        context.studioDataStore.edit { prefs ->
            val cur = StudioSettings(
                language = prefs[Keys.LANGUAGE]?.let { c -> AppLanguage.entries.firstOrNull { it.code == c } } ?: AppLanguage.EN,
                mouthSensitivity = prefs[Keys.MOUTH_SENSITIVITY] ?: 1.25f,
                silenceThreshold = prefs[Keys.SILENCE_THRESHOLD] ?: 0.08f,
                smoothingAmount = prefs[Keys.SMOOTHING] ?: 0.45f,
                blinkRateSeconds = prefs[Keys.BLINK_RATE] ?: 3.4f,
                headMotionStrength = prefs[Keys.HEAD_MOTION] ?: 0.70f,
                bodyBreathingStrength = prefs[Keys.BODY_BREATHING] ?: 0.65f,
                emphasisZoomEnabled = prefs[Keys.EMPHASIS_ZOOM] ?: true,
                defaultResolution = prefs[Keys.DEFAULT_RES]?.let { n -> runCatching { VideoResolution.valueOf(n) }.getOrNull() } ?: VideoResolution.P720,
                defaultAspectRatio = prefs[Keys.DEFAULT_ASPECT]?.let { n -> runCatching { VideoAspectRatio.valueOf(n) }.getOrNull() } ?: VideoAspectRatio.RATIO_9_16,
                defaultFillMode = prefs[Keys.DEFAULT_FILL]?.let { n -> runCatching { BackgroundFillMode.valueOf(n) }.getOrNull() } ?: BackgroundFillMode.BLURRED_PHOTO,
                defaultFps = prefs[Keys.DEFAULT_FPS] ?: 30
            )
            val next = transform(cur)
            prefs[Keys.LANGUAGE] = next.language.code
            prefs[Keys.MOUTH_SENSITIVITY] = next.mouthSensitivity
            prefs[Keys.SILENCE_THRESHOLD] = next.silenceThreshold
            prefs[Keys.SMOOTHING] = next.smoothingAmount
            prefs[Keys.BLINK_RATE] = next.blinkRateSeconds
            prefs[Keys.HEAD_MOTION] = next.headMotionStrength
            prefs[Keys.BODY_BREATHING] = next.bodyBreathingStrength
            prefs[Keys.EMPHASIS_ZOOM] = next.emphasisZoomEnabled
            prefs[Keys.DEFAULT_RES] = next.defaultResolution.name
            prefs[Keys.DEFAULT_ASPECT] = next.defaultAspectRatio.name
            prefs[Keys.DEFAULT_FILL] = next.defaultFillMode.name
            prefs[Keys.DEFAULT_FPS] = next.defaultFps
        }
    }

    suspend fun toggleLanguage() {
        update { it.copy(language = it.language.toggled()) }
    }

    suspend fun resetDefaults() {
        context.studioDataStore.edit { it.clear() }
    }
}
