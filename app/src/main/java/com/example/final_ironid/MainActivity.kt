package com.example.final_ironid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.annotation.RawRes
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import androidx.exifinterface.media.ExifInterface

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var galleryButton: MaterialButton
    private lateinit var videoView: VideoView
    private lateinit var hintText: TextView
    private lateinit var resultSheet: MaterialCardView
    private lateinit var resultTitle: TextView
    private lateinit var confidenceChip: TextView
    private lateinit var videoVariationsScroll: View
    private lateinit var videoVariationsGroup: ChipGroup
    private lateinit var scanAgainButton: MaterialButton

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var interpreter: Interpreter

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val performanceTracker = PerformanceTracker

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                cameraExecutor.execute {
                    val bitmap = loadBitmapFromUri(uri)
                    if (bitmap != null) {
                        runClassification(bitmap)
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                "Unable to load image",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

    // Model classes (order must match the TFLite model output)
    private val modelLabels = listOf(
        "barbell",
        "bench-press",
        "dumbell",
        "kattle-ball",
        "leg-press",
        "punching-bag",
        "roller-abs",
        "statis-bicycle",
        "step",
        "treadmill"
    )

    // Map model labels to user-facing labels and video resources
    private val labelDisplayMap = mapOf(
        "barbell" to "Barbell Deadlifts",
        "bench-press" to "Bench Press",
        "dumbell" to "Dumbbell",
        "kattle-ball" to "Kettlebell",
        "leg-press" to "Leg Press",
        "punching-bag" to "Punching Bag",
        "roller-abs" to "Ab Roller",
        "statis-bicycle" to "Stationary Bicycle",
        "step" to "Step Platform",
        "treadmill" to "Treadmill"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.captureButton)
        galleryButton = findViewById(R.id.galleryButton)
        videoView = findViewById(R.id.videoView)
        hintText = findViewById(R.id.hintText)
        resultSheet = findViewById(R.id.resultSheet)
        resultTitle = findViewById(R.id.resultTitle)
        confidenceChip = findViewById(R.id.confidenceChip)
        videoVariationsScroll = findViewById(R.id.videoVariationsScroll)
        videoVariationsGroup = findViewById(R.id.videoVariationsGroup)
        scanAgainButton = findViewById(R.id.scanAgainButton)

        captureButton.setOnClickListener { takePhoto() }
        galleryButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        scanAgainButton.setOnClickListener { resetForScan() }
        videoView.setOnCompletionListener { }

        performanceTracker.logAppStarted()

        try {
            interpreter = Interpreter(loadModelFile())
            logModelInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            Toast.makeText(this, "Model load failed", Toast.LENGTH_LONG).show()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                showPermissionDeniedUi()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        captureButton.isEnabled = false
        Log.d(TAG, "takePhoto invoked")
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    Log.d(TAG, "onCaptureSuccess -> bitmap=${bitmap?.width}x${bitmap?.height}")
                    image.close()
                    if (bitmap != null) {
                        runClassification(bitmap)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to capture image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    runOnUiThread { captureButton.isEnabled = true }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    runOnUiThread { captureButton.isEnabled = true }
                }
            }
        )
    }

    private fun runClassification(bitmap: Bitmap) {
        Log.d(TAG, "runClassification called on thread ${Thread.currentThread().name}")
        inferenceExecutor.execute {
            try {
                performanceTracker.logStart()
                val processed = bitmap.centerCrop(MODEL_INPUT_SIZE)
                Log.d(TAG, "runClassification bitmap ${bitmap.width}x${bitmap.height} -> processed ${processed.width}x${processed.height}")
                val inputBuffer = processed.toRawBuffer()
                logInputStats(processed)

                val outputBuffer = TensorBuffer.createFixedSize(
                    intArrayOf(1, modelLabels.size),
                    DataType.FLOAT32
                )

                outputBuffer.buffer.rewind()
                val startTimeMs = SystemClock.elapsedRealtime()
                interpreter.run(inputBuffer, outputBuffer.buffer)
                val endTimeMs = SystemClock.elapsedRealtime()
                val inferenceLatencyMs = endTimeMs - startTimeMs

                val scores = outputBuffer.floatArray
                val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: -1
                val rawLabel = modelLabels.getOrNull(bestIndex)
                val confidence = if (bestIndex >= 0) scores[bestIndex] else 0f

                performanceTracker.logInference(inferenceLatencyMs, confidence)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Latency: ${inferenceLatencyMs}ms | Conf: ${"%.1f".format(confidence * 100)}%",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val displayLabel = rawLabel?.let { labelDisplayMap[it] }

                // Log top-3 for debugging
                scores
                    .mapIndexed { i, s -> i to s }
                    .sortedByDescending { it.second }
                    .take(3)
                    .forEachIndexed { rank, (idx, sc) ->
                        Log.d(TAG, "rank$rank: ${modelLabels.getOrNull(idx)} = $sc")
                    }
                Log.d(TAG, "top1 raw=$rawLabel display=$displayLabel conf=$confidence")

                runOnUiThread {
                    // PHASE 4: ERROR HANDLING
                    // Reject low-confidence results to prevent false positives
                    if (confidence < LOW_CONFIDENCE_THRESHOLD) {
                        videoView.stopPlayback()
                        videoView.visibility = View.GONE
                        resultSheet.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Confidence too low, please move closer.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@runOnUiThread
                    }

                    if (rawLabel != null) {
                        showPrediction(rawLabel, confidence)
                    } else {
                        showLowConfidence("Unknown", confidence)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classification failed", e)
                performanceTracker.logFailure(e)
                runOnUiThread { showNotSure() }
            }
        }
    }

    private fun playVideo(@RawRes videoRes: Int, label: String, confidence: Float) {
        Log.d(TAG, "Prediction: $label ($confidence) -> res=$videoRes")
        resultSheet.visibility = View.VISIBLE
        videoView.visibility = View.VISIBLE
        val videoUri = Uri.parse("android.resource://$packageName/$videoRes")
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mp: MediaPlayer ->
            mp.isLooping = false
            videoView.start()
        }
    }

    private fun showNotSure() {
        videoView.stopPlayback()
        videoView.visibility = View.GONE
        resultSheet.visibility = View.VISIBLE
        resultTitle.text = "Not sure"
        confidenceChip.text = "Confidence: --"
        hintText.text = "Try again"
        Toast.makeText(this, "Not sure", Toast.LENGTH_SHORT).show()
    }

    private fun showPrediction(rawLabel: String, confidence: Float) {
        val displayLabel = labelDisplayMap[rawLabel] ?: rawLabel
        resultTitle.text = displayLabel
        confidenceChip.text = "Confidence: ${(confidence * 100).toInt()}%"
        hintText.text = "Machine detected"
        resultSheet.visibility = View.VISIBLE

        val variations = VideoRepository.getVideos(displayLabel)
        if (variations.isNullOrEmpty()) {
            videoVariationsScroll.visibility = View.GONE
            Toast.makeText(this, "No videos found for $displayLabel", Toast.LENGTH_SHORT).show()
            return
        }

        if (variations.size == 1) {
            videoVariationsScroll.visibility = View.GONE
            playVideo(variations.first(), displayLabel, confidence)
        } else {
            videoVariationsScroll.visibility = View.VISIBLE
            bindVariationChips(displayLabel, confidence, variations)
            playVideo(variations.first(), displayLabel, confidence)
            selectFirstChip()
        }
    }

    private fun showLowConfidence(label: String?, confidence: Float) {
        val labelText = label ?: "Unknown"
        videoView.stopPlayback()
        videoView.visibility = View.GONE
        resultSheet.visibility = View.VISIBLE
        resultTitle.text = "Not sure"
        confidenceChip.text = "Confidence: ${(confidence * 100).toInt()}%"
        hintText.text = "Try another angle"
        videoVariationsScroll.visibility = View.GONE
        Toast.makeText(this, "Not sure: $labelText", Toast.LENGTH_SHORT).show()
    }

    private fun resetForScan() {
        videoView.stopPlayback()
        videoView.visibility = View.GONE
        resultSheet.visibility = View.GONE
        hintText.text = "Align machine in frame"
        videoVariationsScroll.visibility = View.GONE
        captureButton.isEnabled = true
    }

    private fun bindVariationChips(label: String, confidence: Float, variations: List<Int>) {
        videoVariationsGroup.removeAllViews()
        variations.forEachIndexed { index, resId ->
            val chip = Chip(this).apply {
                text = "Variation ${index + 1}"
                isCheckable = true
                isCheckedIconVisible = false
                setOnClickListener {
                    isChecked = true
                    playVideo(resId, label, confidence)
                }
            }
            videoVariationsGroup.addView(chip)
        }
    }

    private fun selectFirstChip() {
        if (videoVariationsGroup.childCount > 0) {
            val first = videoVariationsGroup.getChildAt(0)
            if (first is Chip) {
                first.isChecked = true
            }
        }
    }

    // PHASE 4: UX Error Handling for missing permissions.
    private fun showPermissionDeniedUi() {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, "Camera access is needed to identify gym machines.", Snackbar.LENGTH_LONG).show()
        hintText.text = "Camera access is needed to identify gym machines."
        hintText.visibility = View.VISIBLE
        videoView.visibility = View.GONE
        resultSheet.visibility = View.GONE
        captureButton.isEnabled = false
    }

    // Center-crop to a square, preserving aspect ratio (matches Python ImageOps.fit)
    private fun Bitmap.centerCrop(target: Int): Bitmap {
        if (width == 0 || height == 0) return this
        val scale = max(
            target.toFloat() / width.toFloat(),
            target.toFloat() / height.toFloat()
        )
        val scaledW = (width * scale).roundToInt()
        val scaledH = (height * scale).roundToInt()
        val scaled = Bitmap.createScaledBitmap(this, scaledW, scaledH, true)
        val x = ((scaledW - target) / 2f).roundToInt().coerceAtLeast(0)
        val y = ((scaledH - target) / 2f).roundToInt().coerceAtLeast(0)
        return Bitmap.createBitmap(
            scaled,
            x,
            y,
            target.coerceAtMost(scaled.width - x),
            target.coerceAtMost(scaled.height - y)
        )
    }

    // Prepare float32 buffer in 0-255 range (no normalization) to match Python preprocessing
    private fun Bitmap.toRawBuffer(): ByteBuffer {
        val cropped = centerCrop(MODEL_INPUT_SIZE)
        val buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        cropped.getPixels(
            pixels,
            0,
            MODEL_INPUT_SIZE,
            0,
            0,
            MODEL_INPUT_SIZE,
            MODEL_INPUT_SIZE
        )
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            buffer.putFloat(r.toFloat())
            buffer.putFloat(g.toFloat())
            buffer.putFloat(b.toFloat())
        }
        buffer.rewind()
        return buffer
    }

    private fun logModelInfo() {
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)
            val q = inputTensor.quantizationParams()
            Log.i(TAG, "Input tensor: shape=${inputTensor.shape().contentToString()}, type=${inputTensor.dataType()}, quantScale=${q.scale}, zero=${q.zeroPoint}")
            Log.i(TAG, "Output tensor: shape=${outputTensor.shape().contentToString()}, type=${outputTensor.dataType()}")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to log tensor info", e)
        }
    }

    private fun logInputStats(bitmap: Bitmap) {
        try {
            var minR = 255f; var minG = 255f; var minB = 255f
            var maxR = 0f; var maxG = 0f; var maxB = 0f
            var sumR = 0f; var sumG = 0f; var sumB = 0f
            val w = bitmap.width
            val h = bitmap.height
            val total = w * h
            val pixels = IntArray(total)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            for (p in pixels) {
                val r = ((p shr 16) and 0xFF).toFloat()
                val g = ((p shr 8) and 0xFF).toFloat()
                val b = (p and 0xFF).toFloat()
                minR = minR.coerceAtMost(r); minG = minG.coerceAtMost(g); minB = minB.coerceAtMost(b)
                maxR = maxR.coerceAtLeast(r); maxG = maxG.coerceAtLeast(g); maxB = maxB.coerceAtLeast(b)
                sumR += r; sumG += g; sumB += b
            }
            val meanR = sumR / total
            val meanG = sumG / total
            val meanB = sumB / total
            Log.d(TAG, "Input stats (raw 0-255): mean=(${"%.1f".format(meanR)}, ${"%.1f".format(meanG)}, ${"%.1f".format(meanB)}) min=(${"%.1f".format(minR)},${"%.1f".format(minG)},${"%.1f".format(minB)}) max=(${"%.1f".format(maxR)},${"%.1f".format(maxG)},${"%.1f".format(maxB)})")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to log input stats", e)
        }
    }


    private fun loadModelFile(): ByteBuffer {
        val fileDescriptor = assets.openFd("model_pruned_float16.tflite")
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val baseBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null
            val rotation = contentResolver.openInputStream(uri)?.use { stream ->
                readExifRotation(stream)
            } ?: 0
            if (rotation == 0) baseBitmap else rotateBitmap(baseBitmap, rotation)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode image from gallery", e)
            null
        }
    }

    private fun readExifRotation(stream: InputStream): Int {
        return try {
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val matrix = Matrix()
        matrix.postRotate(imageInfo.rotationDegrees.toFloat())
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            interpreter.close()
        } catch (ignored: Exception) {
        }
        cameraExecutor.shutdown()
        inferenceExecutor.shutdown()
    }

    companion object {
        private const val TAG = "IronID"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val MODEL_INPUT_SIZE = 224
        private const val LOW_CONFIDENCE_THRESHOLD = 0.60f
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

private object PerformanceTracker {
    private const val PERF_TAG = "IronID_Performance"

    fun logStart() {
        Log.d(PERF_TAG, "Inference starting")
    }

    fun logAppStarted() {
        Log.d(PERF_TAG, "App started, waiting for capture or gallery selection")
    }

    fun logInference(latencyMs: Long, confidence: Float) {
        val confidencePercent = "%.2f".format(confidence * 100f)
        Log.d(PERF_TAG, "Inference Latency: $latencyMs ms - Confidence: $confidencePercent%")
    }

    fun logFailure(e: Exception) {
        Log.e(PERF_TAG, "Inference failed: ${e.message}", e)
    }
}

private object VideoRepository {
    // Maps display labels to one or more video resources. Replace resource IDs with your actual files.
    private val videos: Map<String, List<Int>> = mapOf(
        "Barbell Deadlifts" to listOf(R.raw.barbell_1, R.raw.barbell_2, R.raw.barbell_3),
        "Bench Press" to listOf(R.raw.bench_press),
        "Dumbbell" to listOf(R.raw.dumbell_1, R.raw.dumbell_2, R.raw.dumbell_3),
        "Kettlebell" to listOf(R.raw.kattle_ball_1, R.raw.kattle_ball_2, R.raw.kattle_ball_3),
        "Leg Press" to listOf(R.raw.leg_press),
        "Punching Bag" to listOf(R.raw.punching_bag),
        "Ab Roller" to listOf(R.raw.roller_abs),
        "Stationary Bicycle" to listOf(R.raw.statis_bicycle),
        "Step Platform" to listOf(R.raw.step),
        "Treadmill" to listOf(R.raw.treadmill)
    )

    fun getVideos(label: String): List<Int>? = videos[label]
}