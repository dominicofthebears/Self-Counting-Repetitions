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

    var numReps: Int = 0
    var numSeries: Int = 0
    var restTimeMinutes: Int = 0
    var restTimeSeconds: Int = 0
    var end: Boolean = false
    var starting: Boolean = true
    var seriesCounter: Int = 0

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val TAG = "Pose Landmarker"
    }

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        requestCameraPermissions()
        startBluetooth()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        findViewById<ImageView>(redCrossImageView).alpha = 0f

        ExerciseManager.timerGoing=false
        ExerciseManager.isExerciseInProgress=false
        end = false
        starting = true

        numReps = intent.getIntExtra("numReps", 0)
        numSeries = intent.getIntExtra("numSets", 0)
        restTimeMinutes = intent.getIntExtra("restSeconds", 0)
        restTimeSeconds = intent.getIntExtra("restMinutes", 0)

        findViewById<TextView>(R.id.repTV).text = java.lang.String("0/$numReps")
        findViewById<TextView>(R.id.seriesTV).text = java.lang.String("1/$numSeries")

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


    @SuppressLint("SetTextI18n")
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
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
        startTimer(3, true) //initial timer, 3 seconds
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

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }
    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }
    @SuppressLint("CutPasteId")
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
                        findViewById<TextView>(R.id.repTV).text=ExerciseManager.repCount.toString() + "/$numReps" //update rep count
                        if(ExerciseManager.repCount == numReps){ //new series
                            ExerciseManager.repCount = 0
                            seriesCounter = findViewById<TextView>(R.id.seriesTV).text.split("/")[0].toInt()
                            seriesCounter++
                            ExerciseManager.seriesCount++
                            if(seriesCounter <= numSeries) { //launching of the timer for the rest time
                                findViewById<TextView>(R.id.seriesTV).text=seriesCounter.toString() + "/$numSeries"
                                findViewById<TextView>(R.id.repTV).text="0/$numReps"
                                runOnUiThread {
                                    findViewById<TextView>(R.id.timerView).visibility = View.VISIBLE
                                }
                                startTimer((restTimeMinutes * 60) + restTimeSeconds, false)
                            }
                            else{
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
                                    println("Entro qui")
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
                    }else {
                        changeFlags()
                    }
            }
        }

    }

    override fun onError(error: String, errorCode: Int) {
        Log.d(TAG, "onError")
    }

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

    fun wrongRepSignal(){
        val redCrossImageView = findViewById<ImageView>(redCrossImageView)

        val animator = ObjectAnimator.ofFloat(redCrossImageView, "alpha", 0f, 1f)
        animator.duration = 1000

        animator.start()
        redCrossImageView.postDelayed({
            val dissolveAnimation = AnimationUtils.loadAnimation(this, R.anim.dissolve)
            redCrossImageView.startAnimation(dissolveAnimation)
        }, 1000)
    }
}



