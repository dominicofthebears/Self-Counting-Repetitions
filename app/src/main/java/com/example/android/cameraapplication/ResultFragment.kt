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
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment

var errors = mutableMapOf<Triple<Int, Int, String>, Int>()
var averageBPM: Int = 0

class ResultFragment : Fragment(){
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        averageBPM = requireArguments().getInt("averageBPM")
        errors = (requireArguments().getSerializable("errors") as? MutableMap<Triple<Int, Int, String>, Int>)!!
        return inflater.inflate(R.layout.results_fragment, container, false)
    }
    @SuppressLint("SetTextI18n", "CutPasteId")
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
        layout.gravity = Gravity.CENTER

        if (averageBPM == 0){
            view.findViewById<TextView>(R.id.averageBPM).text = "BPM data not received"
        }
        else{
            view.findViewById<TextView>(R.id.averageBPM).text = "Average BPM value: " + averageBPM.toString()
        }
        view.findViewById<TextView>(R.id.averageBPM).textSize = 15f
        view.findViewById<TextView>(R.id.averageBPM).setTextColor(Color.WHITE)
        view.findViewById<TextView>(R.id.averageBPM).gravity = Gravity.CENTER

        var textView : TextView
        for(element in errors){

            val parentWidth = resources.displayMetrics.widthPixels
            val desiredWidthPercentage = 0.95


            val cardView = CardView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    (parentWidth * desiredWidthPercentage).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                cardElevation = 20f // Elevation of the card
                radius = 20f // Corner radius of the card
                preventCornerOverlap = true
                useCompatPadding = true
                setCardBackgroundColor(Color.WHITE)
            }


            textView = TextView(requireContext())
            textView.textSize = 15f
            textView.setTextColor(Color.BLACK)
            textView.gravity = Gravity.CENTER_HORIZONTAL
            textView.apply {  setPadding(5, 5, 5, 5) }
            textView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )

            if(element.key.first.equals(Int.MAX_VALUE)){
                textView.text = "You did no mistakes during this workout, good job!"
            }
            else{
                textView.text = "Serie: " + element.key.first.toString() + " - Rep: " + element.key.second.toString() +
                        " - Motivation: "+ element.key.third
            }


            cardView.addView(textView)
            layout.addView(cardView)
        }

        var buttonBack = Button(requireContext())
        buttonBack.setOnClickListener{
            val intent = Intent(context, MainActivity::class.java)
            startActivity(intent)
        }
        buttonBack.setText("Back")
        buttonBack.setTextColor(Color.BLACK)
        buttonBack.gravity = Gravity.CENTER
        val drawable = GradientDrawable()
        drawable.setSize(50, 57)
        buttonBack.background = drawable
        buttonBack.setBackgroundColor(0xFFFFFFFF.toInt())
        layout.addView(buttonBack)

    }

}