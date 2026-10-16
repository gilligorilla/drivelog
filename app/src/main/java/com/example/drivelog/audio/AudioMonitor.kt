package com.example.drivelog.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process

/**
 * Owns the microphone and a worker thread that feeds an [ImpactDetector].
 *
 * While [listening] is false the microphone is stopped (not just ignored), so pausing really
 * halts mic processing. Callbacks are invoked on the worker thread.
 */
class AudioMonitor(
    context: Context,
    private val settings: () -> DetectorSettings,
    private val onImpact: () -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext

    @Volatile
    var listening: Boolean = true

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        thread = Thread(::runLoop, "audio-monitor").also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
        thread?.let {
            try {
                it.join(1500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
    }

    @SuppressLint("MissingPermission") // Permission is checked by the UI before the service starts.
    private fun runLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val sampleRate = ImpactDetector.DEFAULT_SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            onError("Microphone unavailable")
            running = false
            return
        }
        val readSize = ImpactDetector.DEFAULT_FRAME_SIZE * 2
        val bufferBytes = maxOf(minBuffer, readSize * 2 * 4)

        val record = try {
            AudioRecord(
                pickAudioSource(),
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
        } catch (e: Exception) {
            onError("Microphone error: ${e.message}")
            running = false
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("Microphone failed to initialise")
            running = false
            return
        }

        val detector = ImpactDetector(sampleRate)
        val buffer = ShortArray(readSize)
        var recording = false
        var frameTick = 0

        try {
            while (running) {
                if (!listening) {
                    if (recording) {
                        record.stop()
                        recording = false
                    }
                    try {
                        Thread.sleep(40)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                if (!recording) {
                    detector.reset()
                    record.startRecording()
                    recording = true
                }

                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) {
                    onError("Microphone read error ($n)")
                    try {
                        Thread.sleep(250)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                if (n == 0) continue

                if (detector.process(buffer, n, settings())) onImpact()
                // Throttle level updates (~20 Hz) to keep UI work light.
                if (++frameTick % 2 == 0) onLevel(detector.lastLevelDb)
            }
        } catch (e: Exception) {
            onError("Audio thread stopped: ${e.message}")
        } finally {
            try {
                if (recording) record.stop()
            } catch (_: IllegalStateException) {
            }
            record.release()
            running = false
        }
    }

    private fun pickAudioSource(): Int {
        // Prefer an unprocessed source (no AGC / noise suppression) so energy levels stay
        // meaningful and repeatable; otherwise VOICE_RECOGNITION, which is usually least processed.
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed =
            am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }
}
