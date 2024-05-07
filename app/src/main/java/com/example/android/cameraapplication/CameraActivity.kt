package com.example.android.cameraapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors




class CameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    var numReps: Int = 0
    var numSeries: Int = 0
    var restTimeMinutes: Int = 0
    var restTimeSeconds: Int = 0
    companion object {
        //QUESTO è PRESO DAL CODICE DI DENNY
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "Pose Landmarker"
    }
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    //private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    //QUESTO è PRESO DAL CODICE DI DENNY
    private lateinit var previewView : PreviewView

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    private var messageReceiver : MessageReceiver? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartwatchConnector.ActivityBinder
            binder.getService().setActivityContext(this@CameraActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("", "Disconnected from smatwatch service")
        }
    }

    inner class MessageReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getIntExtra("com.example.android.cameraapplication.BPM", 0)
            findViewById<TextView>(R.id.bpmTV).text = message.toString()
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        //Log.d(TAG, "DebugMess: ")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        numReps = intent.getIntExtra("numReps", 0)
        numSeries = intent.getIntExtra("numSets", 0)
        restTimeMinutes = intent.getIntExtra("restSeconds", 0)
        restTimeSeconds = intent.getIntExtra("restMinutes", 0)

        findViewById<TextView>(R.id.repTV).text = java.lang.String("0/$numReps")
        findViewById<TextView>(R.id.seriesTV).text = java.lang.String("1/$numSeries")

        requestCameraPermissions()

        messageReceiver = MessageReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver!!, IntentFilter("android.intent.action.BPM_UPDATE"))
        val serviceIntent = Intent(this, SmartwatchConnector::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.RECEIVER_NOT_EXPORTED)


        previewView = findViewById<PreviewView>(R.id.previewView)
        setUpCamera()
        backgroundExecutor = Executors.newSingleThreadExecutor()

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                //context = requireContext(),
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                //minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                //minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                //minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                //currentDelegate = viewModel.currentDelegate,
                minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
                minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
                minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
                currentDelegate = PoseLandmarkerHelper.DELEGATE_CPU,
                poseLandmarkerHelperListener = this
            )
        }
    }
    private fun setUpCamera() {
        val cameraProviderFuture =
            //ProcessCameraProvider.getInstance(requireContext())
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
                //}, ContextCompat.getMainExecutor(requireContext())
            }, ContextCompat.getMainExecutor(this)
        )
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            //.setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()
        //Log.d(TAG, "DebugMess: ")
        // ImageAnalysis. Using RGBA 8888 to match how our models work
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

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            // Attach the viewfinder's surface provider to preview use case
            //preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            //Log.e(CameraFragment.TAG, "Use case binding failed", exc)
            Log.e(TAG, "Use case binding failed", exc)
        }
    }
    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            //Log.d(TAG, "DebugMess: ")
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        resultBundle.results[0]?.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                        println(normalizedLandmark.x().toString() + " " + normalizedLandmark.y().toString() + "\n")
                }
                println("End of landmarks")
            }}



        /*
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }

         */
    }

    override fun onError(error: String, errorCode: Int) {
        Log.d(TAG, "onError")
        /*
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }

         */
    }
    //QUESTO è PRESO DAL CODICE DI DENNY
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

    //QUESTO è PRESO DAL CODICE DI DENNY
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