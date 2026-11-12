package com.houseaffairs.app.core.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.houseaffairs.app.R
import com.houseaffairs.app.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What just happened, so it can sound and feel right. */
enum class Cue { SAVED, DONE, UNDO, ERROR }

/**
 * Small sounds and taps that confirm an action: a light ding on save, a little rising chime when
 * something's done, a soft step back on undo. Quiet when the phone is on silent or vibrate, and
 * off altogether with Settings → Sounds & vibration.
 */
@Singleton
class Feedback @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
) {
    @Volatile private var on = true
    private val audio = context.getSystemService(AudioManager::class.java)
    private val pool: SoundPool by lazy {
        SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        ).build()
    }
    private val sounds: Map<Cue, Int> by lazy {
        mapOf(Cue.SAVED to R.raw.cue_save, Cue.DONE to R.raw.cue_done, Cue.UNDO to R.raw.cue_undo).mapValues { (_, res) -> pool.load(context, res, 1) }
    }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.feedbackOn.collectLatest { on = it }
        }
        // Loaded ahead, so the first save already has its sound.
        CoroutineScope(Dispatchers.IO).launch { sounds }
    }

    fun play(cue: Cue, view: View? = null) {
        if (!on) return
        view?.performHapticFeedback(haptic(cue))
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        sounds[cue]?.let { pool.play(it, 0.6f, 0.6f, 1, 0, 1f) }
    }

    private fun haptic(cue: Cue): Int = when {
        Build.VERSION.SDK_INT < 30 -> HapticFeedbackConstants.VIRTUAL_KEY
        cue == Cue.ERROR -> HapticFeedbackConstants.REJECT
        cue == Cue.UNDO -> HapticFeedbackConstants.CLOCK_TICK
        else -> HapticFeedbackConstants.CONFIRM
    }
}
