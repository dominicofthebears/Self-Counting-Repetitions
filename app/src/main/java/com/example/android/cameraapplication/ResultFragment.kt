package com.example.android.cameraapplication

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

val errors = listOf(Triple(1, 1, "wide knees"), Triple(1, 1, "wide knees"),
    Triple(1, 1, "wide knees"), Triple(1, 1, "wide knees"))

class ResultFragment : Fragment(){
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.results_fragment, container, false)
    }
    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create a GradientDrawable
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, // Gradient orientation
            intArrayOf(0xFF7D12DC.toInt(), 0xFFFFFFFF.toInt()) // Gradient colors
        )
        gradientDrawable.cornerRadius = 20f // Set corner radius
        val viewWithGradient = view.findViewById<View>(R.id.scrollView2)
        viewWithGradient.background = gradientDrawable

        val layout = view.findViewById<LinearLayout>(R.id.linearLayout)

        var textView : TextView
        for(element in errors){
            textView = TextView(requireContext())
            textView.textSize = 20f
            textView.setTextColor(Color.WHITE)
            textView.gravity = Gravity.CENTER_HORIZONTAL
            textView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            textView.text = "Rep: " + element.first.toString() + " - Serie: " + element.second.toString() +
                    " - Motivation:"+ element.third
            layout.addView(textView)
        }

        var buttonBack = Button(requireContext())
        buttonBack.setOnClickListener{
            val intent = Intent(context, MainActivity::class.java)
            startActivity(intent)
        }
        buttonBack.setText("Back")
        buttonBack.gravity = Gravity.CENTER_HORIZONTAL
        layout.addView(buttonBack)
    }

}