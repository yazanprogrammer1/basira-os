package com.basira.os.core.render.calibration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.basira.os.core.common.viewer.ScreenOrientation
import com.basira.os.core.common.viewer.ViewerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Persisted lens calibration. Emits [ViewerProfile.CARDBOARD_V2] until the user saves something. */
interface CalibrationStore {
    val profile: Flow<ViewerProfile>

    suspend fun save(profile: ViewerProfile)

    suspend fun reset()
}

private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(name = "viewer_calibration")

class DataStoreCalibrationStore @Inject constructor(
    @ApplicationContext context: Context,
) : CalibrationStore {

    private val store = context.calibrationDataStore

    override val profile: Flow<ViewerProfile> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map(::toProfile)
        .distinctUntilChanged()

    override suspend fun save(profile: ViewerProfile) {
        store.edit { prefs ->
            prefs[IPD] = profile.interLensDistanceMm
            prefs[SCREEN_TO_LENS] = profile.screenToLensMm
            prefs[TRAY_TO_LENS] = profile.trayToLensCenterMm
            prefs[K1] = profile.k1
            prefs[K2] = profile.k2
            prefs[FOV] = profile.maxFovDeg
            prefs[SHIFT] = profile.passthroughShiftPx
            prefs[ORIENTATION] = profile.orientation.name
        }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private fun toProfile(prefs: Preferences): ViewerProfile {
        val d = ViewerProfile.CARDBOARD_V2
        return ViewerProfile(
            interLensDistanceMm = prefs[IPD] ?: d.interLensDistanceMm,
            screenToLensMm = prefs[SCREEN_TO_LENS] ?: d.screenToLensMm,
            trayToLensCenterMm = prefs[TRAY_TO_LENS] ?: d.trayToLensCenterMm,
            k1 = prefs[K1] ?: d.k1,
            k2 = prefs[K2] ?: d.k2,
            maxFovDeg = prefs[FOV] ?: d.maxFovDeg,
            passthroughShiftPx = prefs[SHIFT] ?: d.passthroughShiftPx,
            orientation = prefs[ORIENTATION]
                ?.let { name -> ScreenOrientation.entries.firstOrNull { it.name == name } }
                ?: d.orientation,
        ).sanitized()
    }

    private companion object {
        val IPD = floatPreferencesKey("inter_lens_mm")
        val SCREEN_TO_LENS = floatPreferencesKey("screen_to_lens_mm")
        val TRAY_TO_LENS = floatPreferencesKey("tray_to_lens_mm")
        val K1 = floatPreferencesKey("k1")
        val K2 = floatPreferencesKey("k2")
        val FOV = floatPreferencesKey("max_fov_deg")
        val SHIFT = floatPreferencesKey("passthrough_shift_px")
        val ORIENTATION = stringPreferencesKey("orientation")
    }
}
