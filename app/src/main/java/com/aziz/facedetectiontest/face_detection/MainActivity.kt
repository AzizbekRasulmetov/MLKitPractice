package com.aziz.facedetectiontest.face_detection

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aziz.facedetectiontest.text_reader.TextReaderActivity
import com.aziz.facedetectiontest.compose.ComposeActivity
import com.aziz.facedetectiontest.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private val targetResolution = Size(480, 640)
    private lateinit var cameraExecutor: ExecutorService

    // High-accuracy landmark detection and face classification
    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setMinFaceSize((targetResolution.width * targetResolution.height).toFloat())
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        //Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        viewBinding.entityButton.setOnClickListener {
            openTextReaderActivity()
        }
        viewBinding.composeBtn.setContent {
            MaterialTheme {
                Button(onClick = { startActivity(Intent(this, ComposeActivity::class.java)) }) {
                    Text("Compose Screen", color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun openTextReaderActivity() {
        startActivity(Intent(this, TextReaderActivity::class.java))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .setTargetResolution(targetResolution)
                .build().also { preview ->
                    preview.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetResolution(targetResolution)
                .setJpegQuality(100)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(targetResolution)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(cameraExecutor, FaceAnalyzer())
                }

            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(
            baseContext, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val EYE_CLOSED = 0.0f
        private const val FACE_SMILING = 1.0f
        private const val TEN_DEGREES = 10f
        private const val FORTY_PERCENT = 0.4f
        private const val SEVENTY_PERCENT = 0.7f
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    private inner class FaceAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                val detector = FaceDetection.getClient(highAccuracyOpts)
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        // Task completed successfully
                        processFaceDetectionResult(
                            faces = faces,
                            onFaceDetectionFailed = {
                                viewBinding.statusTxt.apply {
                                    text = "Unable to recognize face. Please do not smile and open your eyes"
                                    setTextColor(Color.YELLOW)
                                }
                                viewBinding.imageCaptureButton.isEnabled = false
                            },
                            onFaceNotFound = {
                                viewBinding.statusTxt.apply {
                                    text = "Face Not Found"
                                    setTextColor(Color.RED)
                                }
                                viewBinding.imageCaptureButton.isEnabled = false
                            },
                            onFaceDetected = {
                                viewBinding.statusTxt.apply {
                                    text = "Face Detection Success"
                                    setTextColor(Color.GREEN)
                                }
                                viewBinding.imageCaptureButton.isEnabled = true
                            }
                        )
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        viewBinding.imageCaptureButton.isEnabled = false
                        Log.e(TAG, "Failed to detect image", e)
                    }.addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    private fun processFaceDetectionResult(
        faces: List<Face>,
        onFaceNotFound: () -> Unit,
        onFaceDetectionFailed: () -> Unit,
        onFaceDetected: () -> Unit
    ) {
        // Task completed successfully
        if (faces.isEmpty()) {
            onFaceNotFound()
            return
        } else {
            for (face in faces) {
                val bounds = face.boundingBox

                val headRotYDegree = face.headEulerAngleY // Head is rotated to the right rotY degrees
                val headRotXDegree = face.headEulerAngleX // A face with a positive Euler X angle is facing upward.
                val headRotZDegree = face.headEulerAngleZ // Head is tilted sideways rotZ degrees


                /** If classification was enabled **/
                val smileProbability = if (face.smilingProbability == null) FACE_SMILING else face.smilingProbability ?: FACE_SMILING
                val leftEyeOpenProbability = if (face.leftEyeOpenProbability == null) EYE_CLOSED else face.leftEyeOpenProbability ?: EYE_CLOSED
                val rightEyeOpenProbability = if (face.rightEyeOpenProbability == null) EYE_CLOSED else face.rightEyeOpenProbability ?: EYE_CLOSED

                if (smileProbability < SEVENTY_PERCENT && leftEyeOpenProbability > FORTY_PERCENT && rightEyeOpenProbability > FORTY_PERCENT &&
                    headRotYDegree <= TEN_DEGREES && headRotXDegree <= TEN_DEGREES && headRotZDegree <= TEN_DEGREES
                ) {
                    onFaceDetected()
                } else {
                    onFaceDetectionFailed()
                }
            }
        }
    }

}