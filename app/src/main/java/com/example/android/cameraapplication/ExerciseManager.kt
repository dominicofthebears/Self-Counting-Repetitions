package com.example.android.cameraapplication

import android.util.Log
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.sqrt

class ExerciseManager {
    companion object {
        var timerGoing: Boolean = false
        var isExerciseInProgress: Boolean = false
        var repCount: Int = 0
        var seriesCount: Int = 1
        var errorsDoneDuringExercise: Int = 0
        private val exerciseThresholdOnErrors: Int = 5
        private val FORM_TAG = "Form Assessment"
        private val hipComment = "hip not correct in phase "
        private val kneeShoulderComment = "knees too far apart from shoulders in phase "

        private lateinit var currentLandmark: List<NormalizedLandmark>
        //private var proximityThreshold: Float = 0.0f
        private var exerciseState = 0
        private val repsPerformedWrong = mutableListOf<Triple<Int, Int, String>>() // (serie,repNumber,explanation)
        val repsDictionary = mutableMapOf<Triple<Int, Int, String>, Int>()
            get() = field

        // Constants to map radians to degrees
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
            /*
            val noseMouthL = relativeDistance(currentLandmark.get(0), currentLandmark.get(9))
            val noseMouthR = relativeDistance(currentLandmark.get(0), currentLandmark.get(10))
            val noseMouth = (noseMouthL + noseMouthR) / 2
            proximityThreshold = noseMouth
             */
        }
        /*
        fun checkStart(): Boolean {
            val dist = relativeDistance(currentLandmark.get(19), currentLandmark.get(0))
            if(dist < proximityThreshold){
                return true
            }else return false
        }
         */

        // Check the squat phase and form correctness
        fun checkSquatPhase(): Int {
            //angle between hip, knee and foot
            val angleKnee = angleBetweenPoints(currentLandmark.get(24), currentLandmark.get(26), currentLandmark.get(28))
            val angleHip = angleBetweenPoints(currentLandmark.get(26), currentLandmark.get(24), currentLandmark.get(12))
            val ankleDistance = relativeDistance(currentLandmark.get(26), currentLandmark.get(28)) // distance between shoulders
            val kneeDistance = relativeDistance(currentLandmark.get(25), currentLandmark.get(26)) // distance between feet
            var phase = 0
            // Check the squat phase looking at the angle between hip, knee and foot
            // For each phase, check the form correctness looking at:
            // - the angle between shoulder, hip and knee
            // - the distance between the knees
            when {
                angleKnee > G120 -> {
                    phase = 0
                    if ((exerciseState == 0) or (exerciseState == 4))
                    {
                        if (angleHip < G130) {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, hipComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, hipComment + phase))
                            errorsDoneDuringExercise++
                            Log.d(FORM_TAG, "HIP " + phase)

                        }
                        if ((kneeDistance < (ankleDistance*1.5)) or (kneeDistance > ankleDistance*4.7))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsDoneDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
                (angleKnee > G60) and (angleKnee <= G130) -> {
                    phase = 1
                    if ((exerciseState == 1) or (exerciseState == 3))
                    {
                        if (angleHip <= G30) {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, hipComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, hipComment + phase))
                            errorsDoneDuringExercise++
                            Log.d(FORM_TAG, "HIP " + phase)

                        }
                        if (kneeDistance > (ankleDistance*2.5))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsDoneDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
                (angleKnee <= G60) -> {
                    phase = 2
                    if (exerciseState == 2)
                    {
                        if ((kneeDistance > (ankleDistance*3.5))) // or (kneeDistance in (ankleDistance*0.8)..(ankleDistance*1.2)))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsDoneDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
            }

            return phase
        }
        // Update the rep count according to the phase and the exercise state
        fun updateRepCount(phase: Int): Boolean{
            //println("exerciseState = " + exerciseState)
            when {
                (exerciseState == 0) and (phase == 0) -> exerciseState++
                (exerciseState == 1) and (phase == 1) -> exerciseState++
                (exerciseState == 2) and (phase == 2) -> exerciseState++
                (exerciseState == 3) and (phase == 1) -> exerciseState++
                (exerciseState == 4) and (phase == 0) -> {
                    exerciseState = 0
                    if (errorsDoneDuringExercise < exerciseThresholdOnErrors) {
                        repCount++
                        errorsDoneDuringExercise = 0
                        return true
                    }
                    else {
                        errorsDoneDuringExercise = 0
                        insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                        return false
                    }
                }
            }
            return false
        }

        // Insert a triplet in the dictionary
        fun insertTriplet(key: Triple<Int, Int, String>) {
            if (repsDictionary.containsKey(key))
                repsDictionary[key] = repsDictionary.getValue(key) + 1
            else
                repsDictionary[key] = 1
        }

        // Euclidean distance between 2 points
        fun relativeDistance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            val dx = a.x() - b.x()
            val dy = a.y() - b.y()
            val dz = a.z() - b.z()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        // Dot product between 2 vectors (a,b) and (b,c)
        fun dotProduct(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val abx = b.x() - a.x()
            val aby = b.y() - a.y()
            val abz = b.z() - a.z()
            val bcx = b.x() - c.x()
            val bcy = b.y() - c.y()
            val bcz = b.z() - c.z()
            return abx * bcx + aby * bcy + abz * bcz
        }
        // Angle in b between a and c (in radians)
        fun angleBetweenPoints(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val dot = dotProduct(a, b, c)
            val magAB = relativeDistance(a, b)
            val magBC = relativeDistance(b, c)
            return acos(dot / (magAB * magBC))
        }
    }
}