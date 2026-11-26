package com.lumiposture.app.data.sensor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.lumiposture.app.domain.model.NormalizedPoint
import com.lumiposture.app.domain.model.DataStatus
import com.lumiposture.app.domain.model.VisionTelemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.hypot

class FaceLandmarkerHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _telemetryFlow = MutableStateFlow(VisionTelemetry())
    val telemetryFlow: StateFlow<VisionTelemetry> = _telemetryFlow.asStateFlow()

    private var faceLandmarker: FaceLandmarker? = null

    @Volatile
    private var lastAnalyzedTimestampMs: Long = 0L

    @Volatile
    private var lastSentTimestampMs: Long = 0L

    @Volatile
    private var currentRotatedWidth: Int = 480

    @Volatile
    private var currentRotatedHeight: Int = 640

    private var sessionStartMs: Long = 0L
    private var wasEyeClosed: Boolean = false
    private var closedStartMs: Long = 0L
    private var totalBlinks: Int = 0
    private val blinkTimestamps = ArrayDeque<Long>()
    private val frameTimestamps = ArrayDeque<Long>()

    private var reusableBitmap: Bitmap? = null

    @Synchronized
    fun initialize() {
        if (faceLandmarker != null) return
        runCatching {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener(::onFaceLandmarkerResult)
                .setErrorListener { error ->
                    Log.w(TAG, "MediaPipe error: ${error.message}")
                    reportStatus(DataStatus.FAILED)
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to initialize FaceLandmarker", throwable)
            reportStatus(DataStatus.FAILED)
        }
    }

    @Synchronized
    fun resetSessionCounters() {
        sessionStartMs = 0L
        wasEyeClosed = false
        closedStartMs = 0L
        totalBlinks = 0
        blinkTimestamps.clear()
        frameTimestamps.clear()
        _telemetryFlow.value = VisionTelemetry()
    }

    @Synchronized
    fun analyzeImageProxy(imageProxy: ImageProxy) {
        val landmarker = faceLandmarker
        val nowMs = SystemClock.uptimeMillis()

        if ((landmarker == null) || ((nowMs - lastAnalyzedTimestampMs) < FRAME_THROTTLE_MS)) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestampMs = nowMs

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val srcWidth = imageProxy.width
        val srcHeight = imageProxy.height
        val rotated90or270 = (rotationDegrees == 90) || (rotationDegrees == 270)
        currentRotatedWidth = if (rotated90or270) srcHeight else srcWidth
        currentRotatedHeight = if (rotated90or270) srcWidth else srcHeight

        val bitmap = try {
            extractBitmapFromRgbaProxy(imageProxy)
        } catch (t: Throwable) {
            Log.w(TAG, "Frame conversion error", t)
            null
        } finally {
            imageProxy.close()
        } ?: return

        // Infer upright pixels so preview landmarks and IPD use the same coordinate system.
        val upright = if (rotationDegrees == 0) bitmap else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true,
        ).also { if (it !== bitmap) bitmap.recycle() }
        val mpImage = BitmapImageBuilder(upright).build()

        val monotonicTs = maxOf(nowMs, lastSentTimestampMs + 1L)
        lastSentTimestampMs = monotonicTs

        runCatching {
            landmarker.detectAsync(mpImage, monotonicTs)
        }.onFailure {
            mpImage.close()
            Log.w(TAG, "detectAsync error: ${it.message}")
            reportStatus(DataStatus.FAILED)
        }
    }

    private fun extractBitmapFromRgbaProxy(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height
        val plane = imageProxy.planes[0]
        if ((imageProxy.planes.size == 1) && (plane.pixelStride == 4) && (plane.rowStride == width * 4)) {
            // Each async request owns its bitmap until MediaPipe finishes processing it.
            val bmp = androidx.core.graphics.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            bmp.copyPixelsFromBuffer(plane.buffer)
            return bmp
        }
        return imageProxy.toBitmap()
    }

    private fun onFaceLandmarkerResult(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        input.close()
        val ts = result.timestampMs()
        if (sessionStartMs == 0L) sessionStartMs = ts

        frameTimestamps.addLast(ts)
        while (frameTimestamps.size > 10) {
            frameTimestamps.removeFirst()
        }
        val fps = if (frameTimestamps.size >= 2) {
            val spanMs = (frameTimestamps.last() - frameTimestamps.first()).coerceAtLeast(1L)
            ((frameTimestamps.size - 1) * 1000f) / spanMs
        } else {
            0f
        }

        val landmarksList = result.faceLandmarks()
        if (landmarksList.isEmpty()) {
            sessionStartMs = 0L
            blinkTimestamps.clear()
            wasEyeClosed = false
            _telemetryFlow.value = _telemetryFlow.value.copy(
                status = DataStatus.NO_FACE,
                blinkWindowReady = false,
                faceDetected = false,
                landmarkPoints = emptyList(),
                leftIrisPoint = null,
                rightIrisPoint = null,
                inferenceFps = fps,
                timestampMs = System.currentTimeMillis(),
            )
            return
        }

        val faceLandmarks = landmarksList[0]
        val rotW = currentRotatedWidth.toFloat()
        val rotH = currentRotatedHeight.toFloat()

        // Iris landmarks 468 (left iris) & 473 (right iris)
        var ipdPx = 0f
        var leftIris: NormalizedPoint? = null
        var rightIris: NormalizedPoint? = null
        if (faceLandmarks.size > 473) {
            val l = faceLandmarks[468]
            val r = faceLandmarks[473]
            leftIris = NormalizedPoint(l.x(), l.y())
            rightIris = NormalizedPoint(r.x(), r.y())
            ipdPx = hypot(
                ((r.x() - l.x()) * rotW).toDouble(),
                ((r.y() - l.y()) * rotH).toDouble(),
            ).toFloat()
        }

        val overlayPoints = MINIMAL_MESH_INDICES
            .asSequence()
            .filter { it < faceLandmarks.size }
            .map { idx -> NormalizedPoint(faceLandmarks[idx].x(), faceLandmarks[idx].y()) }
            .toList()

        // Blendshapes: Blink & Squint
        var blinkLeft = 0f
        var blinkRight = 0f
        var squintLeft = 0f
        var squintRight = 0f
        val blendshapesOpt = result.faceBlendshapes()
        if (blendshapesOpt.isPresent && blendshapesOpt.get().isNotEmpty()) {
            for (category in blendshapesOpt.get()[0]) {
                when (category.categoryName()) {
                    "eyeBlinkLeft" -> blinkLeft = category.score()
                    "eyeBlinkRight" -> blinkRight = category.score()
                    "eyeSquintLeft" -> squintLeft = category.score()
                    "eyeSquintRight" -> squintRight = category.score()
                }
            }
        }
        val blinkAvg = (blinkLeft + blinkRight) * 0.5f
        val squintAvg = (squintLeft + squintRight) * 0.5f

        var drowsy = false
        if (!wasEyeClosed && blinkAvg > 0.50f) {
            wasEyeClosed = true
            closedStartMs = ts
        } else if (wasEyeClosed) {
            val closedElapsed = ts - closedStartMs
            if (blinkAvg < 0.30f) {
                if (closedElapsed in 80L..500L) {
                    totalBlinks += 1
                    blinkTimestamps.addLast(ts)
                }
                wasEyeClosed = false
            } else if (closedElapsed > 1500L) {
                drowsy = true
            }
        }

        val windowCutoff = ts - 60_000L
        while (blinkTimestamps.isNotEmpty() && blinkTimestamps.first() < windowCutoff) {
            blinkTimestamps.removeFirst()
        }

        val elapsedSessionMs = (ts - sessionStartMs).coerceAtLeast(1L)
        val bpm = when {
            elapsedSessionMs < 12_000L -> 0f
            elapsedSessionMs < 60_000L -> {
                val windowSec = (elapsedSessionMs / 1000f).coerceAtLeast(12f)
                (blinkTimestamps.size * (60f / windowSec)).coerceIn(0f, 45f)
            }
            else -> blinkTimestamps.size.toFloat()
        }

        // Head Pitch from 4x4 column-major facial transformation matrix
        var headPitchDeg = 0f
        val matrixOpt = result.facialTransformationMatrixes()
        if (matrixOpt.isPresent && matrixOpt.get().isNotEmpty()) {
            val m = matrixOpt.get()[0]
            if (m.size >= 11) {
                val pitchRad = atan2(
                    -m[9].toDouble(),
                    hypot(m[8].toDouble(), m[10].toDouble()),
                )
                headPitchDeg = Math.toDegrees(pitchRad).toFloat().coerceIn(-60f, 60f)
            }
        }

        _telemetryFlow.value = VisionTelemetry(
            faceDetected = true,
            status = if (blendshapesOpt.isPresent && matrixOpt.isPresent) DataStatus.AVAILABLE else DataStatus.FAILED,
            blinkWindowReady = elapsedSessionMs >= 60_000L,
            blinkRateBpm = bpm,
            totalBlinks = totalBlinks,
            eyeBlinkAvg = blinkAvg,
            eyeSquintAvg = squintAvg,
            isDrowsyEyesClosed = drowsy,
            ipdPixels = ipdPx,
            headPitchDeg = headPitchDeg,
            landmarkPoints = overlayPoints,
            leftIrisPoint = leftIris,
            rightIrisPoint = rightIris,
            frameWidth = currentRotatedWidth,
            frameHeight = currentRotatedHeight,
            inferenceFps = fps,
            timestampMs = System.currentTimeMillis(),
        )
    }

    fun reportStatus(status: DataStatus) {
        _telemetryFlow.value = VisionTelemetry(status = status, timestampMs = System.currentTimeMillis())
    }

    @Synchronized
    fun close() {
        runCatching { faceLandmarker?.close() }
        faceLandmarker = null
        reusableBitmap = null
    }

    companion object {
        private const val TAG = "FaceLandmarkerHelper"
        private const val MODEL_ASSET_PATH = "face_landmarker.task"
        private const val FRAME_THROTTLE_MS = 150L

        // Minimal biometric landmarks: face contour, eyebrows, eyes, nose bridge
        private val MINIMAL_MESH_INDICES = listOf(
            // Face oval
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288,
            397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136,
            172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
            // Left eye
            33, 160, 158, 133, 153, 144,
            // Right eye
            362, 385, 387, 263, 373, 380,
            // Eyebrows & nose bridge
            70, 63, 105, 66, 107,
            336, 296, 334, 293, 300,
            168, 6, 197, 195, 5, 4,
        )
    }
}
