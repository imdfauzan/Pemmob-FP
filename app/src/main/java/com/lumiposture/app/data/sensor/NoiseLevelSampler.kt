package com.lumiposture.app.data.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.lumiposture.app.domain.model.DataStatus
import com.lumiposture.app.domain.model.NoiseTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class NoiseLevelSampler @Inject constructor(@param:ApplicationContext private val context: Context) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNoiseDb(enabledFlow: Flow<Boolean>): Flow<NoiseTelemetry> = enabledFlow.distinctUntilChanged().flatMapLatest {
        if (it) observeNoiseDb() else flowOf(NoiseTelemetry(status = DataStatus.DISABLED))
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun observeNoiseDb(): Flow<NoiseTelemetry> = flow {
        var recorder: AudioRecord? = null
        try {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
                emit(NoiseTelemetry(status = DataStatus.UNSUPPORTED)); return@flow
            }
            if (!hasPermission()) {
                emit(NoiseTelemetry(status = DataStatus.PERMISSION_DENIED)); return@flow
            }
            emit(NoiseTelemetry())
            val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(size > 0)
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(size, 6400)).also { recorder = it }
            check(audio.state == AudioRecord.STATE_INITIALIZED)
            val pcm = ShortArray(3200)
            while (currentCoroutineContext().isActive) {
                if (!hasPermission()) { emit(NoiseTelemetry(status = DataStatus.PERMISSION_DENIED)); break }
                if (microphoneBlocked()) {
                    emit(NoiseTelemetry(status = DataStatus.DISABLED)); delay(1000); continue
                }
                audio.startRecording()
                check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                var count = 0
                val deadline = android.os.SystemClock.elapsedRealtime() + 1200
                while (count < pcm.size && currentCoroutineContext().isActive && android.os.SystemClock.elapsedRealtime() < deadline) {
                    val read = audio.read(pcm, count, pcm.size - count, AudioRecord.READ_NON_BLOCKING)
                    check(read >= 0)
                    if (read == 0) delay(10) else count += read
                }
                val silenced = Build.VERSION.SDK_INT >= 29 && context.getSystemService(AudioManager::class.java)
                    ?.activeRecordingConfigurations?.any { it.clientAudioSessionId == audio.audioSessionId && it.isClientSilenced } == true
                audio.stop()
                if (silenced) emit(NoiseTelemetry(status = DataStatus.DISABLED))
                else if (count > 0) {
                    val rms = sqrt((0 until count).sumOf { pcm[it].toDouble() * pcm[it] } / count)
                    val dbfs = (20 * log10((rms / 32768.0).coerceAtLeast(1.0 / 32768))).toFloat()
                    emit(NoiseTelemetry(dbfs, DataStatus.AVAILABLE, System.currentTimeMillis()))
                } else emit(NoiseTelemetry(status = DataStatus.FAILED))
                delay(1800)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            emit(NoiseTelemetry(status = DataStatus.PERMISSION_DENIED))
        } catch (_: Exception) {
            emit(NoiseTelemetry(status = DataStatus.FAILED))
        } finally {
            runCatching { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder?.stop() }
            recorder?.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun microphoneBlocked() = context.getSystemService(AudioManager::class.java)?.isMicrophoneMute == true
}
