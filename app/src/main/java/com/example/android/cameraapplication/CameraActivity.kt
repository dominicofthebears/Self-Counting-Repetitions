package com.example.android.cameraapplication

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.fragment.app.commit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.android.cameraapplication.R.id.redCrossImageView
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.Serializable
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {
    // User input variables
    var numReps: Int = 0
    var numSeries: Int = 0
    var restTimeMinutes: Int = 0
    var restTimeSeconds: Int = 0

    // Flags
    var end: Boolean = false
    var starting: Boolean = true

    var seriesCounter: Int = 0


    companion object {
        // Request code for camera permissions
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        // Logging tag
        private const val TAG = "Pose Landmarker"
    }

    // Pose LandmarkerHelper instance, it wrap the PoseLandmarker and provides some useful methods
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    // CameraX variables
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var previewView : PreviewView

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    // Message receiver to receive messages from the smartwatch
    private var messageReceiver : MessageReceiver? = null

    // Service connection to the smartwatch
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SmartwatchConnector.ActivityBinder
            binder.getService().setActivityContext(this@CameraActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i("", "Disconnected from smatwatch service")
        }
    }

    // Inner class to receive messages from the smartwatch
    inner class MessageReceiver: BroadcastReceiver() {
        var accumulator: Int = 0
            get() = field
        var numElements: Int = 0
            get() = field
        override fun onReceive(context: Context, intent: Intent) {
            val message = intent.getIntExtra("com.example.android.cameraapplication.BPM", 0)
            findViewById<TextView>(R.id.bpmTV).text = message.toString()
            accumulator += message
            numElements += 1
        }

    }

    // OnCreate method to initialize the activity and start the camera and the smartwatch connection
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        requestCameraPermissions()
        startBluetooth()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        // Set the red cross image, that will be shown when a wrong rep is detected, to invisible
        findViewById<ImageView>(redCrossImageView).alpha = 0f

        // Initialize the variables with the user
        ExerciseManager.timerGoing=false
        ExerciseManager.isExerciseInProgress=false
        end = false
        starting = true

        // Get the user input from the previous activity
        numReps = intent.getIntExtra("numReps", 0)
        numSeries = intent.getIntExtra("numSets", 0)
        restTimeMinutes = intent.getIntExtra("restSeconds", 0)
        restTimeSeconds = intent.getIntExtra("restMinutes", 0)

        // Set number of reps and series in the UI
        findViewById<TextView>(R.id.repTV).text = java.lang.String("0/$numReps")
        findViewById<TextView>(R.id.seriesTV).text = java.lang.String("1/$numSeries")

        // Register the message receiver to receive messages from the smartwatch
        messageReceiver = MessageReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver!!, IntentFilter("android.intent.action.BPM_UPDATE"))
        val serviceIntent = Intent(this, SmartwatchConnector::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.RECEIVER_NOT_EXPORTED)

        // Set up the camera
        previewView = findViewById<PreviewView>(R.id.previewView)
        setUpCamera()
        // Initialize the PoseLandmarkerHelper instance in a background thread to avoid blocking the UI thread
        backgroundExecutor = Executors.newSingleThreadExecutor()

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
                minPoseTrackingConfidence = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
                minPosePresenceConfidence = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
                poseLandmarkerHelperListener = this
            )
        }
    }


    @SuppressLint("SetTextI18n")
    // Function to start the timer for the rest time between series and the timer for the exercise
    private fun startTimer(num: Int, flag: Boolean){
        ExerciseManager.timerGoing = true
        ExerciseManager.isExerciseInProgress = false
        findViewById<PreviewView>(R.id.previewView).visibility = View.INVISIBLE
        var count = 0
        val timeView = findViewById<TextView>(R.id.timerView)
        if (flag)
            timeView.text= num.toString()
        else
            if(num%60<10)
                timeView.text=((num/60).toString()+":0"+(num%60).toString())
            else
                timeView.text=((num/60).toString()+":"+(num%60).toString())
        val timer = Timer()
        var newTime = num
        try{
            val timerTask = object : TimerTask(){
                override fun run() {
                    newTime--
                    if (flag)
                        timeView.text = newTime.toString()
                    else
                        if(newTime%60<10)
                            timeView.text=((newTime/60).toString()+":0"+(newTime%60).toString())
                        else
                            timeView.text=((newTime/60).toString()+":"+(newTime%60).toString())
                    count++
                    if(count==num){
                        timer.cancel()
                        runOnUiThread{findViewById<PreviewView>(R.id.previewView).visibility = View.VISIBLE}
                        ExerciseManager.timerGoing=false
                        starting = false
                    }
                }
            }

            timer.schedule(timerTask, 1000, 1000)
        }catch (e: Exception){
            e.printStackTrace()
        }
    }
    // Function to set up the camera and bind the camera use cases to the camera provider to start the camera preview and the image analysis
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(this)
        )
    }
    @SuppressLint("UnsafeOptInUsageError")
    // Function to bind the camera use cases to the camera provider to start the camera preview and the image analysis
    private fun bindCameraUseCases() {
        // Get the camera provider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")
        // Set up the camera selector to select the front/back camera
        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to the input ratio of the PoseLandmarker
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
        // start the timer to give time to the user to get ready
        startTimer(5, true)
        // ImageAnalysis to analyze the camera frames and call the detect pose method
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
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
        // Bind use cases to camera
        try {
            // A variable number of use-cases can be passed here -
            // Camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }
    // Function to detect the pose in the camera frames using the PoseLandmarkerHelper
    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }
    @SuppressLint("CutPasteId")
    // Function to handle the results of the PoseLandmarkerHelper
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        // Get the landmarks from the PoseLandmarker result
        resultBundle.results[0]?.let { poseLandmarkerResult ->
            val landmarks = poseLandmarkerResult.landmarks()
            for(landmark in poseLandmarkerResult.landmarks()) {

                //An user is detected in the camera frame
                    // Set the current landmark in the ExerciseManager
                    ExerciseManager.setCurrentLandmark(landmark)
                    // Check if an exercise is in progress
                    if(ExerciseManager.isExerciseInProgress){
                        // Check in which phase of the squat the user is
                        val phase = ExerciseManager.checkSquatPhase()
                        // Update the rep count according to the phase
                        if (!ExerciseManager.updateRepCount(phase)){
                            runOnUiThread{
                                wrongRepSignal()
                            }

                        }
                        else{
                            // Update the UI with the rep count
                            findViewById<TextView>(R.id.repTV).text=ExerciseManager.repCount.toString() + "/$numReps"
                            // Check if the current series is completed
                            if(ExerciseManager.repCount == numReps){
                                // Reset the rep count
                                ExerciseManager.repCount = 0
                                // Increment the series count
                                seriesCounter = findViewById<TextView>(R.id.seriesTV).text.split("/")[0].toInt()
                                seriesCounter++
                                ExerciseManager.seriesCount++
                                // Update the UI with the series count
                                if(seriesCounter <= numSeries) {
                                    findViewById<TextView>(R.id.seriesTV).text=seriesCounter.toString() + "/$numSeries"
                                    findViewById<TextView>(R.id.repTV).text="0/$numReps"
                                    runOnUiThread {
                                        findViewById<TextView>(R.id.timerView).visibility = View.VISIBLE
                                    }
                                    // Start the timer for the rest time between series
                                    startTimer((restTimeMinutes * 60) + restTimeSeconds, false)
                                }
                                else{
                                    // All the series are completed and the exercise is ended
                                    end = true
                                    ExerciseManager.isExerciseInProgress=false
                                    runOnUiThread{
                                        cameraProvider?.unbindAll()
                                        backgroundExecutor.shutdownNow()
                                    }
                                    unbindService(serviceConnection)
                                    messageReceiver?.let {
                                        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                                            it
                                        )
                                    }
                                    ExerciseManager.isExerciseInProgress = false

                                    // If the user has not performed any wrong rep
                                    if(ExerciseManager.repsDictionary.isEmpty()){
                                        ExerciseManager.repsDictionary.put(Triple(Int.MAX_VALUE, 0, ""), 0)
                                    }

                                    val bundle = Bundle().apply {
                                        putSerializable("errors", ExerciseManager.repsDictionary as Serializable)
                                    }

                                    if((messageReceiver!!.accumulator > 0) and (messageReceiver!!.numElements > 0)){
                                        messageReceiver?.let { bundle.putInt("averageBPM",
                                            (it.accumulator/it.numElements)
                                        ) }
                                    }
                                    else{
                                        messageReceiver?.let { bundle.putInt("averageBPM", 0) }
                                    }

                                    val fragment = ResultFragment()
                                    fragment.setArguments(bundle)
                                    supportFragmentManager.commit {
                                        replace(R.id.fragment_container_view, fragment)
                                        setReorderingAllowed(true)
                                    }

                                }
                            }
                        }
                    }else {
                        changeFlags()
                    }
            }
        }

    }

    // Function to handle the error messages from the PoseLandmarkerHelper
    override fun onError(error: String, errorCode: Int) {
        Log.d(TAG, "onError")
    }

    // Function to change the flags according to the exercise status
    private fun changeFlags(): Boolean {
        if (end || starting) {
            return false
        }
        if (!ExerciseManager.timerGoing && !ExerciseManager.isExerciseInProgress) { //timer is ended
            ExerciseManager.isExerciseInProgress = true
            runOnUiThread { findViewById<TextView>(R.id.timerView).visibility=View.INVISIBLE }
        }
        return ExerciseManager.isExerciseInProgress
    }

    // Function to request the camera permissions
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
    // Function to handle the camera permissions request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                finish()
            }
        }
    }

    // Function to start the bluetooth connection
    private fun startBluetooth(){
        if(!BluetoothAdapter.getDefaultAdapter().isEnabled){
            val bluetoothSettings = Intent(ACTION_REQUEST_ENABLE)
            if ((ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED)) {
                startActivity(bluetoothSettings)
            }
            else{
                val requestConnectPermission: ActivityResultLauncher<String> =
                    registerForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted ->
                        if (permissionGranted) {
                            startActivity(bluetoothSettings)
                        }
                    }
                requestConnectPermission.launch(BLUETOOTH_CONNECT)
            }
        }
    }

        fun wrongRepSignal(){
            val redCrossImageView = findViewById<ImageView>(redCrossImageView)
            val animator = ObjectAnimator.ofFloat(redCrossImageView, "alpha", 0f, 1f)
            animator.duration = 2500
            animator.start()
            Handler(mainLooper).post {
                val dissolveAnimation = AnimationUtils.loadAnimation(this, R.anim.dissolve)
                redCrossImageView.startAnimation(dissolveAnimation)
            }
        }
}



