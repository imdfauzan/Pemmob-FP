package com.lumiposture.app.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class NoiseLevelSampler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun observeNoiseDb(): Flow<Float> = flow {
        val sampleRate = 16000
        val sampleWindowSamples = (sampleRate * 0.2f).toInt() // 200 ms window
        val minBufferBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )

        if ((minBufferBytes <= 0) || !hasAudioPermission()) {
            while (currentCoroutineContext().isActive) {
                emit(DEFAULT_QUIET_DB)
                delay(2000L)
            }
            return@flow
        }

        val bufferSizeBytes = maxOf(minBufferBytes, sampleWindowSamples * 2)
        @android.annotation.SuppressLint("MissingPermission")
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSizeBytes,
            )
        }.getOrNull()

        if ((audioRecord == null) || (audioRecord.state != AudioRecord.STATE_INITIALIZED)) {
            audioRecord?.release()
            while (currentCoroutineContext().isActive) {
                emit(DEFAULT_QUIET_DB)
                delay(2000L)
            }
            return@flow
        }

        val pcmBuffer = ShortArray(sampleWindowSamples)
        try {
            audioRecord.startRecording()
            while (currentCoroutineContext().isActive) {
                var totalRead = 0
                while ((totalRead < pcmBuffer.size) && currentCoroutineContext().isActive) {
                    val read = audioRecord.read(pcmBuffer, totalRead, pcmBuffer.size - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                if (totalRead > 0) {
                    val db = computeDbFromPcm(pcmBuffer, totalRead)
                    emit(db)
                }
                delay(1800L)
            }
        } catch (_: SecurityException) {
            emit(DEFAULT_QUIET_DB)
        } finally {
            runCatching {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            }
            runCatching { audioRecord.release() }
        }
    }.flowOn(Dispatchers.IO)

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun computeDbFromPcm(samples: ShortArray, count: Int): Float {
        var sumSquares = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble()
            sumSquares += v * v
        }
        val rms = sqrt(sumSquares / count.coerceAtLeast(1)).coerceAtLeast(1.0)
        val db = ((20.0 * log10(rms / P_REF)) + CALIBRATION_OFFSET_DB).toFloat()
        return db.coerceIn(25f, 95f)
    }

    companion object {
        private const val P_REF = 1.0
        private const val CALIBRATION_OFFSET_DB = 2.0f
        private const val DEFAULT_QUIET_DB = 36f
    }
}
