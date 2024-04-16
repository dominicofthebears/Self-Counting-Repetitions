package com.example.android.cameraapplication

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.button).setOnClickListener{
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("numReps", findViewById<TextView>(R.id.editTextNumber5).text.toString().toInt())
            intent.putExtra("numSets", findViewById<TextView>(R.id.editTextNumber4).text.toString().toInt())
            intent.putExtra("restSeconds", findViewById<TextView>(R.id.editTextNumber3).text.toString().toInt())
            intent.putExtra("restMinutes", findViewById<TextView>(R.id.editTextNumber).text.toString().toInt())
            startActivity(intent)
        }

    }
}
