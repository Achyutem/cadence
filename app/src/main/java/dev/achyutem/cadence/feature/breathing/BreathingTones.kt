package dev.achyutem.cadence.feature.breathing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import dev.achyutem.cadence.domain.breathing.BreathCue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** One note: a frequency, how long it lasts, and how loud relative to the others. */
private data class Note(val hertz: Double, val millis: Int, val gain: Double = 1.0)

/**
 * The sound of a session.
 *
 * Synthesised rather than bundled as audio files, and synthesised rather than driven through
 * `ToneGenerator`. `ToneGenerator`'s catalogue is call-progress and DTMF tones; it can make a
 * noise at a phase change but it cannot make *inhale* sound like the opposite of *exhale*, which
 * is the entire requirement. A rising pair means breathe in, a falling pair means breathe out, a
 * low sustained note means hold. That is learnable in one session and works with the screen off.
 *
 * Sixty lines of sine wave is also cheaper than an audio dependency or a folder of WAVs, which is
 * the same trade the Markdown renderer and the heatmap already make.
 */
class BreathingTones {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Playback is serialised onto one coroutine.
     *
     * Two overlapping writes to a single [AudioTrack] would interleave their samples into noise,
     * and cues can land back to back when a one-second phase ends on a countdown tick.
     */
    @Volatile
    private var track: AudioTrack? = null

    fun play(cue: BreathCue) {
        val notes = notesFor(cue)
        scope.launch {
            runCatching {
                val samples = render(notes)
                val output = track ?: createTrack().also { track = it }
                output.write(samples, 0, samples.size)
            }
            // A device with no audio output, or one that refuses an AudioTrack, must not take the
            // session down with it. Silence is a degraded session; a crash mid-breath-hold is not.
        }
    }

    fun release() {
        scope.cancel()
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE / 4)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Sonification on the media stream, so the hardware volume keys adjust it and
                    // it respects silent mode. An alarm-stream beep that ignores a silenced phone
                    // would be the wrong call for something people do to calm down.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    /**
     * Notes to PCM.
     *
     * Each note gets a short attack and an exponential decay. Without the attack a sine starting
     * at full amplitude clicks; without the decay it ends in one, and a click is exactly the kind
     * of sharp noise this screen should not be making.
     */
    private fun render(notes: List<Note>): ShortArray {
        val total = notes.sumOf { it.millis * SAMPLE_RATE / 1000 }
        val out = ShortArray(total)
        var offset = 0
        for (note in notes) {
            val count = note.millis * SAMPLE_RATE / 1000
            val attack = min(ATTACK_SAMPLES, count / 4)
            for (i in 0 until count) {
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    else -> exp(-DECAY * (i - attack).toDouble() / (count - attack))
                }
                val value = sin(2.0 * PI * note.hertz * i / SAMPLE_RATE) *
                    envelope * note.gain * AMPLITUDE
                out[offset + i] = value.toInt().toShort()
            }
            offset += count
        }
        return out
    }

    private fun notesFor(cue: BreathCue): List<Note> = when (cue) {
        // Rising: breathe in.
        BreathCue.INHALE -> listOf(Note(440.0, 110), Note(660.0, 150))
        // Falling: breathe out.
        BreathCue.EXHALE -> listOf(Note(660.0, 110), Note(440.0, 150))
        // Low and sustained: stop moving air.
        BreathCue.HOLD -> listOf(Note(300.0, 320))
        BreathCue.HOLD_EMPTY -> listOf(Note(220.0, 320))
        // Soft and open: breathe freely.
        BreathCue.RECOVER -> listOf(Note(523.0, 90, gain = 0.7), Note(392.0, 180, gain = 0.7))
        BreathCue.PREPARE -> listOf(Note(392.0, 160, gain = 0.6))
        // Quiet enough not to startle someone deep into a hold.
        BreathCue.COUNTDOWN -> listOf(Note(880.0, 55, gain = 0.32))
        BreathCue.FINISH -> listOf(Note(523.0, 130), Note(659.0, 130), Note(784.0, 260))
    }

    private companion object {
        /**
         * 22.05 kHz is plenty for tones under 1 kHz and halves the samples rendered on the main
         * thread's behalf. Nothing here is music.
         */
        const val SAMPLE_RATE = 22_050
        const val AMPLITUDE = 9_000.0
        const val ATTACK_SAMPLES = 120
        const val DECAY = 3.2
    }
}

/** A [BreathingTones] tied to the composition, released when the session leaves the screen. */
@Composable
fun rememberBreathingTones(): BreathingTones {
    val tones = remember { BreathingTones() }
    DisposableEffect(tones) { onDispose { tones.release() } }
    return tones
}
