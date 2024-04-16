package com.example.android.cameraapplication

import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import android.Manifest
import android.util.Log
import android.widget.Button
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraProvider
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.core.content.ContentProviderCompat.requireContext
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(),PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    companion object{
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    var cam: CameraSelector? = null
    var camProv: ProcessCameraProvider? = null
    var camera: Camera? = null
    var prev: Preview? = null
    var imageAnalyzer: ImageAnalysis? = null
    lateinit var backgroundExecutor: ExecutorService /** Blocking ML operations are performed using this executor */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()
        requestCameraPermissions()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            camProv = cameraProvider //maybe redundant zio pera

            val previewView = findViewById<PreviewView>(R.id.previewView)

            val preview = androidx.camera.core.Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                //.setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            prev = preview

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cam = cameraSelector
            //bindCameraUseCases() //TODO tentativo di usare mediapipe

            imageAnalyzer =
                ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    //.setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    // The analyzer can then be assigned to the instance
                    .also {
                        it.setAnalyzer(backgroundExecutor) { image ->
                            detectPose(image)
                        }
                    }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
                minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
                minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
                currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
                poseLandmarkerHelperListener = this
            )
        }

        findViewById<Button>(R.id.button2).setOnClickListener{
            if(cam == CameraSelector.DEFAULT_FRONT_CAMERA)
                cam = CameraSelector.DEFAULT_BACK_CAMERA
            else
                cam = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                camProv?.unbindAll()
                camProv?.bindToLifecycle(this, cam!!, prev)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /*
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = camProv
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.DEFAULT_FRONT_CAMERA


        // Preview. Only using the 4:3 ratio because this is the closest to our models
        /*preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()
        */
        // ImageAnalysis. Using RGBA 8888 to match how our models work


        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, prev, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            //prev?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            //Log.e(CameraFragment.TAG, "Use case binding failed", exc)
        }
    }*/

    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = true
            )
        }
    }

    private fun requestCameraPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onError(error: String, errorCode: Int) {
        TODO("Not yet implemented")
    }

    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        println(resultBundle.results)
        println(resultBundle.inputImageHeight)
        println(resultBundle.inputImageWidth)

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Permesso della fotocamera non ottenuto, chiudi l'app
                finish()
            }
        }
    }

}
