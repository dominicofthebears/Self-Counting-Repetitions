package com.example.android.cameraapplication

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
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
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import io.netty.util.internal.ObjectUtil.intValue
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.acos
import kotlin.math.sqrt


class CameraActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    var numReps: Int = 0
    var numSeries: Int = 0
    var restTimeMinutes: Int = 0
    var restTimeSeconds: Int = 0
    var end: Boolean = false
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1000
        private const val TAG = "Pose Landmarker"
    }


    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    //private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT
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

        ExerciseManager.isExerciseInProgress = false
        ExerciseManager.timerGoing = false
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

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
        println("Entro nella start timer")
        ExerciseManager.timerGoing = true
        ExerciseManager.isExerciseInProgress = false
        findViewById<PreviewView>(R.id.previewView).visibility = View.INVISIBLE
        var count = 0
        val timeView = findViewById<TextView>(R.id.timerView)

        if (flag)
            timeView.text= num.toString()
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
                        println("cambia timerGoing a false")
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
        startTimer(3, true) //initial timer, 3 seconds
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
                            var newSeries = findViewById<TextView>(R.id.seriesTV).text.split("/")[0].toInt()
                            newSeries++
                            if(newSeries <= numSeries) { //launching of the timer for the rest time
                                ExerciseManager.repCount = 0
                                findViewById<TextView>(R.id.seriesTV).text=newSeries.toString() + "/$numSeries"
                                findViewById<TextView>(R.id.repTV).text="0/$numReps"
                                //ExerciseManager.timerGoing = true
                                println("cambia timerGoing a true")
                                //ExerciseManager.isExerciseInProgress = false
                                runOnUiThread {
                                    findViewById<TextView>(R.id.timerView).visibility = View.VISIBLE
                                }
                                startTimer((restTimeMinutes * 60) + restTimeSeconds, false)
                            }
                            else{

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
                                val bundle = Bundle()
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
        if (end) {
            return false
        }
        if (!ExerciseManager.timerGoing && !ExerciseManager.isExerciseInProgress) { //timer is ended
            ExerciseManager.isExerciseInProgress = true
            runOnUiThread { findViewById<TextView>(R.id.timerView).visibility=View.INVISIBLE }
            println("Cambiato lo stato")
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
        var timerGoing: Boolean = false
        var isExerciseInProgress: Boolean = false
        var repCount: Int = 0

        private lateinit var currentLandmark: List<NormalizedLandmark>
        private var proximityThreshold: Float = 0.0f
        private var exerciseState = 0

        private const val G30 = 0.523
        private const val G45 = 0.785f
        private const val G60 = 1.047f
        private const val G90 = 1.570f
        private const val G120 = 2.094f
        private const val G150 = 2.618f
        private const val G180 = 3.141f

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
            val angle = angleBetweenPoints(currentLandmark.get(24), currentLandmark.get(26), currentLandmark.get(28))
            var phase = 0
            when {
                angle > G150 -> phase = 0
                (angle > G90) and (angle <= G150) -> phase = 1
                (angle > G60) and (angle <= G90) -> phase = 2
                (angle <= G60) -> phase = 3
            }
            return phase
        }
        fun updateRepCount(phase: Int): Boolean{
            when {
                (exerciseState == 0) and (phase == 1) -> exerciseState++
                (exerciseState == 1) and (phase == 2) -> exerciseState++
                (exerciseState == 2) and (phase == 3) -> exerciseState++
                (exerciseState == 3) and (phase == 2) -> exerciseState++
                (exerciseState == 4) and (phase == 1) -> exerciseState++
                (exerciseState == 5) and (phase == 0) -> {
                    exerciseState = 0
                    repCount++
                    return true
                }
            }
            return false
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