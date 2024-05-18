package com.example.android.cameraapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, // Gradient orientation
            intArrayOf(0xFF7D12DC.toInt(), 0xFFFFFFFF.toInt()) // Gradient colors
        )
        gradientDrawable.cornerRadius = 20f // Set corner radius
        val viewWithGradient = findViewById<View>(R.id.MainLayout)
        viewWithGradient.background = gradientDrawable




        findViewById<Button>(R.id.button1).setOnClickListener{
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("numReps", findViewById<TextView>(R.id.editTextNumber1).text.toString().toInt())
            intent.putExtra("numSets", findViewById<TextView>(R.id.editTextNumber2).text.toString().toInt())
            intent.putExtra("restSeconds", findViewById<TextView>(R.id.editTextNumber3).text.toString().toInt())
            intent.putExtra("restMinutes", findViewById<TextView>(R.id.editTextNumber4).text.toString().toInt())
            startActivity(intent)
        }
    }
}
