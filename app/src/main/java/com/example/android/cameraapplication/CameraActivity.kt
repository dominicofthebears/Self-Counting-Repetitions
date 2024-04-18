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
import android.widget.Button
import androidx.camera.core.Preview

class CameraActivity : AppCompatActivity() {

    var numReps: Int = 0
    var numSeries: Int = 0
    var restTimeMinutes: Int = 0
    var restTimeSeconds: Int = 0


    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    companion object{
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    var cam: CameraSelector? = null
    var camProv: ProcessCameraProvider? = null
    var prev: Preview? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        numReps = intent.getIntExtra("numReps", 0)
        numSeries = intent.getIntExtra("numSets", 0)
        restTimeMinutes = intent.getIntExtra("restSeconds", 0)
        restTimeSeconds = intent.getIntExtra("restMinutes", 0)

        requestCameraPermissions()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            camProv = cameraProvider

            val previewView = findViewById<PreviewView>(R.id.previewView)

            val preview = androidx.camera.core.Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            prev = preview

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cam = cameraSelector

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))

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
