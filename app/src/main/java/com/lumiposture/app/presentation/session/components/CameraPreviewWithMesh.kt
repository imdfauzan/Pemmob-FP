package com.lumiposture.app.presentation.session.components

import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumiposture.app.data.sensor.FaceLandmarkerHelper
import com.lumiposture.app.domain.model.NormalizedPoint
import com.lumiposture.app.presentation.theme.DarkPrimary
import java.util.concurrent.Executors
import kotlin.math.max

@Composable
fun CameraPreviewWithMesh(
    faceLandmarkerHelper: FaceLandmarkerHelper,
    landmarks: List<NormalizedPoint>,
    leftIris: NormalizedPoint?,
    rightIris: NormalizedPoint?,
    frameWidth: Int,
    frameHeight: Int,
    showMeshOverlay: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, isActive) {
        if (!isActive) {
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(480, 640),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply {
                        setAnalyzer(analysisExecutor) { imageProxy ->
                            faceLandmarkerHelper.analyzeImageProxy(imageProxy)
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis,
                )
            }
        }, mainExecutor)

        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (showMeshOverlay && landmarks.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val srcW = frameWidth.coerceAtLeast(1).toFloat()
                val srcH = frameHeight.coerceAtLeast(1).toFloat()
                val scale = max(size.width / srcW, size.height / srcH)
                val scaledW = srcW * scale
                val scaledH = srcH * scale
                val offsetX = (size.width - scaledW) * 0.5f
                val offsetY = (size.height - scaledH) * 0.5f

                fun mapPoint(pt: NormalizedPoint): Offset {
                    // Front camera preview is mirrored horizontally relative to raw sensor frame
                    val mirroredX = 1f - pt.x
                    return Offset(
                        x = offsetX + (mirroredX * scaledW),
                        y = offsetY + (pt.y * scaledH),
                    )
                }

                landmarks.forEach { pt ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.65f),
                        radius = 2.dp.toPx(),
                        center = mapPoint(pt),
                    )
                }

                listOfNotNull(leftIris, rightIris).forEach { iris ->
                    val center = mapPoint(iris)
                    drawCircle(
                        color = DarkPrimary,
                        radius = 5.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }

                if (leftIris != null && rightIris != null) {
                    drawLine(
                        color = DarkPrimary.copy(alpha = 0.6f),
                        start = mapPoint(leftIris),
                        end = mapPoint(rightIris),
                        strokeWidth = 1.2.dp.toPx(),
                    )
                }
            }
        }
    }
}
