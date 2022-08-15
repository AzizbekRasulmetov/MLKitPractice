package com.aziz.facedetectiontest.text_reader

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aziz.facedetectiontest.databinding.ActivityTextRecognitionBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class TextReaderActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityTextRecognitionBinding
    private var imageCapture: ImageCapture? = null
    private val targetResolution = Size(720, 1280)
    private lateinit var cameraExecutor: ExecutorService
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityTextRecognitionBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        //Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        var isCardDataRead = false
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
                .apply {
                    setAnalyzer(cameraExecutor, CreditCardImageAnalyzer(
                        onExtracted = { cardNumber, expiryDate ->
                            if (!isCardDataRead) {
                                isCardDataRead = true
                                recognizer.close()
                                startActivity(Intent(this@TextReaderActivity, MyCardActivity::class.java).apply {
                                    putExtra("cardNumber", cardNumber)
                                    putExtra("expiryDate", expiryDate)
                                }
                                )
                            }
                        }
                    ))
                }

            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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

    private inner class CreditCardImageAnalyzer(private val onExtracted: (String, String) -> Unit) : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            var cardNumber = ""
            var expiryDate = ""
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image).addOnSuccessListener { visionText ->
                    for (block in visionText.textBlocks) {
                        val blockText = block.text.replace(" ", "")
                        if (blockText.length == 16 && blockText.matches(Regex("^\\d{16}"))) {
                            cardNumber = blockText
                        } else if (blockText.contains(Regex("(0[1-9]|1[0-2])/?([0-9]{4}|[0-9]{2})"))) {
                            val date = blockText.replace(Regex("[a-zA-Z]"), "").replace(" ", "")
                            val slashIndex = date.indexOf("/")
                            if (slashIndex >= 2 && (slashIndex + 2) <= date.lastIndex) {
                                expiryDate = date.substring(slashIndex - 2, slashIndex + 3)
                            }
                        }
                    }
                    if (cardNumber.isNotEmpty()) {
                        onExtracted(cardNumber, expiryDate)
                    }
                }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "Failed to read credit card data")
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }


}