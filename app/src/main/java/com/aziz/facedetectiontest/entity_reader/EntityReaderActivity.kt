package com.aziz.facedetectiontest.entity_reader

import android.content.ContentValues
import android.content.pm.PackageManager
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
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aziz.facedetectiontest.databinding.ActivityEntityBinding
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.PaymentCardEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class EntityReaderActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityEntityBinding
    private var imageCapture: ImageCapture? = null
    private val targetResolution = Size(480, 640)
    private var isModelDownloaded: Boolean = false
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityEntityBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        //Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        val entityExtractor = EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build())
        entityExtractor
            .downloadModelIfNeeded()
            .addOnSuccessListener {
                isModelDownloaded = true
                Log.d(TAG, "Model downloaded")
            }
            .addOnFailureListener {
                isModelDownloaded = false
                Log.d(TAG, "Model not downloaded ${it.message}")
            }

        val params = EntityExtractionParams.Builder("My flight is LX373, please pick me up at 8am tomorrow. 8600-2909-0190-0290")
            .setEntityTypesFilter(setOf(Entity.TYPE_PAYMENT_CARD, Entity.TYPE_DATE_TIME))
            .setPreferredLocale(Locale.getDefault())
            .build()


        viewBinding.readButton.setOnClickListener {
            if(isModelDownloaded) {
                entityExtractor
                    .annotate(params)
                    .addOnSuccessListener { entityAnnotations ->
                        Log.d(TAG, "Success to extract")
                        for (entityAnnotation in entityAnnotations) {
                            val entities: List<Entity> = entityAnnotation.entities

                            Log.d(TAG, "Range: ${entityAnnotation.start} - ${entityAnnotation.end}")
                            for (entity in entities) {
                                when (entity) {
                                    is DateTimeEntity -> {
                                        Log.d(TAG, "Granularity: ${entity.dateTimeGranularity}")
                                        Log.d(TAG, "TimeStamp: ${entity.timestampMillis}")
                                    }
                                    is PaymentCardEntity -> {
                                        Log.d(TAG, "Payment Card Number: ${entity.paymentCardNumber}")
                                        Log.d(TAG, "Payment Card Type: ${entity.type}")
                                        Log.d(TAG, "Payment Card Type: ${entity.paymentCardNetwork}")
                                    }
                                    else -> {
                                        Log.d(TAG, "  $entity")
                                    }
                                }
                            }
                        }
                        entityExtractor.close()
                    }
                    .addOnFailureListener {
                        Log.d(TAG, "Failed to extract")
                    }
            }
        }
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
        private const val TAG = "EntityReader"
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


}