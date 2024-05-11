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
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.acos
import kotlin.math.sqrt


var numReps: Int = 0
var numSeries: Int = 0
var restTimeMinutes: Int = 0
var restTimeSeconds: Int = 0

class CameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {
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

   /* private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartwatchConnector.ActivityBinder
            binder.getService().setActivityContext(this@CameraActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("", "Disconnected from smatwatch")
        }
    }*/

    inner class MessageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            println("Entro nella ricezione lato applicazione")
            val message = intent.getIntExtra("com.example.android.cameraapplication.BPM", 0)
            println("Dati ricevuti lato applicazione, valore $message")
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

        requestCameraPermissions()

        /*
        messageReceiver = MessageReceiver()
        val serviceIntent = Intent(this, SmartwatchConnector::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.RECEIVER_NOT_EXPORTED)
        registerReceiver(messageReceiver, IntentFilter("android.intent.action.BPM_UPDATE"),
            RECEIVER_NOT_EXPORTED)
                    */

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
                    //user detected
                    ExerciseManager.setCurrentLandmark(landmark)
                    if(ExerciseManager.isExerciseInProgress){
                        val phase = ExerciseManager.checkSquatPhase()
                        ExerciseManager.updateRepCount(phase)
                        println("repCount = " + ExerciseManager.repCount)

                        //ExerciseManager.checkForm()
                    }else {
                        println("Exercise NOT in Progress")
                        if (ExerciseManager.checkStart()) ExerciseManager.isExerciseInProgress = true
                    }


                /*for (normalizedLandmark in landmark) {
                        println(normalizedLandmark.x().toString() + " " + normalizedLandmark.y().toString() + "\n")
                }
                println("End of landmarks")
                */
            }
        }



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


class ExerciseManager {
    companion object {
        var isExerciseInProgress: Boolean = true
        var repCount: Int = 0
        private val FORM_TAG = "Form Assessment"
        private val hipComment = "hip not correct in phase "
        private val kneeShoulderComment = "knees too far apart from shoulders in phase "

        private lateinit var currentLandmark: List<NormalizedLandmark>
        private var proximityThreshold: Float = 0.0f
        private var exerciseState = 0
        private val repsPerformedWrong = mutableListOf<Triple<Int, Int, String>>() // (serie,repNumber,explanation)
        private val repsDictionary = mutableMapOf<Triple<Int, Int, String>, Int>()

        private const val G25 = 0.500f
        private const val G30 = 0.523f
        private const val G35 = 0.610f
        private const val G45 = 0.785f
        private const val G60 = 1.047f
        private const val G70 = 1.221f
        private const val G80 = 1.396f
        private const val G90 = 1.570f
        private const val G120 = 2.094f
        private const val G130 = 2.268f
        private const val G150 = 2.618f
        private const val G160 = 2.792f
        private const val G170 = 2.967f

        fun setCurrentLandmark(landmark: List<NormalizedLandmark>) {
            currentLandmark = landmark
            val noseMouthL = relativeDistance(currentLandmark.get(0), currentLandmark.get(9))
            val noseMouthR = relativeDistance(currentLandmark.get(0), currentLandmark.get(10))
            val noseMouth = (noseMouthL + noseMouthR) / 2
            proximityThreshold = noseMouth
        }

        fun checkStart(): Boolean {
            val dist = relativeDistance(currentLandmark.get(19), currentLandmark.get(0))
            if(dist < proximityThreshold){
                return true
            }else return false
        }

        fun checkSquatPhase(): Int {
            //angle between hip, knee and foot
            val angleKnee = angleBetweenPoints(currentLandmark.get(24), currentLandmark.get(26), currentLandmark.get(28))

            // ROBA MIA
            val angleHip = angleBetweenPoints(currentLandmark.get(26), currentLandmark.get(24), currentLandmark.get(12))
            val ankleDistance = relativeDistance(currentLandmark.get(26), currentLandmark.get(28)) // distance between shoulders
            val kneeDistance = relativeDistance(currentLandmark.get(25), currentLandmark.get(26)) // distance between feet
            var phase = 0
            when {
                angleKnee > G120 -> {
                    phase = 0
                    if (angleHip < G130) {
                        repsPerformedWrong.add(Triple(100, repCount, hipComment + phase))
                        insertTriplet(Triple(100, repCount, hipComment + phase))
                        Log.d(FORM_TAG, "HIP " + phase)
                    }
                    if (kneeDistance < (ankleDistance*1.5))// or kneeDistance > ankleDistance*5)
                    {
                        repsPerformedWrong.add(Triple(69, repCount, kneeShoulderComment + phase))
                        insertTriplet(Triple(69, repCount, kneeShoulderComment + phase))
                        Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                    }
                }
                (angleKnee > G60) and (angleKnee <= G130) -> {
                    phase = 1
                    if (angleHip <= G30) {
                        repsPerformedWrong.add(Triple(100, repCount, hipComment + phase))
                        insertTriplet(Triple(100, repCount, hipComment + phase))
                        Log.d(FORM_TAG, "HIP " + phase)
                    }
                    if (kneeDistance > (ankleDistance*2.5))
                    {
                        repsPerformedWrong.add(Triple(69, repCount, kneeShoulderComment + phase))
                        insertTriplet(Triple(69, repCount, kneeShoulderComment + phase))
                        Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                    }
                }
                (angleKnee <= G60) -> {
                    phase = 2
                    if (kneeDistance > (ankleDistance*3.5))
                    {
                        repsPerformedWrong.add(Triple(69, repCount, kneeShoulderComment + phase))
                        insertTriplet(Triple(69, repCount, kneeShoulderComment + phase))
                        Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                    }
                }
            }

            //println("grado dell' HIP = " + (angleHip*57.958).toInt())
            //println("grado dell' KNEE = " + (angleKnee*57.958).toInt())
            println("phase = " + phase)
            println("KNEE = " + kneeDistance)
            println("ANKLE = " + ankleDistance)
            //println(repsDictionary)
            return phase
        }
        fun updateRepCount(phase: Int): Boolean{
            //println("exerciseState = " + exerciseState)
            when {
                (exerciseState == 0) and (phase == 0) -> exerciseState++
                (exerciseState == 1) and (phase == 1) -> exerciseState++
                (exerciseState == 2) and (phase == 2) -> exerciseState++
                (exerciseState == 3) and (phase == 1) -> exerciseState++
                (exerciseState == 4) and (phase == 0) -> {
                    exerciseState = 0
                    repCount++
                    return true
                }
            }
            return false
        }

        fun insertTriplet(key: Triple<Int, Int, String>) {
            if (repsDictionary.containsKey(key))
                repsDictionary[key] = repsDictionary.getValue(key) + 1
            else
                repsDictionary[key] = 1
        }

        fun relativeDistance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            val dx = a.x() - b.x()
            val dy = a.y() - b.y()
            val dz = a.z() - b.z()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        fun dotProduct(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val abx = b.x() - a.x()
            val aby = b.y() - a.y()
            val abz = b.z() - a.z()
            val bcx = b.x() - c.x()
            val bcy = b.y() - c.y()
            val bcz = b.z() - c.z()
            return abx * bcx + aby * bcy + abz * bcz
        }
        fun angleBetweenPoints(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val dot = dotProduct(a, b, c)
            val magAB = relativeDistance(a, b)
            val magBC = relativeDistance(b, c)
            return acos(dot / (magAB * magBC))
        }
    }
}