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
        var errorsAngleHipDuringExercise: Int = 0
        var errorsKneeDistanceDuringExercise: Int = 0
        private val exerciseThresholdOnErrors: Int = 10
        private val FORM_TAG = "Form Assessment"
        private var hipComment = "hip not correct "
        private var kneeShoulderComment = "knees too close , "

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
        private const val G110 = 1.919f
        private const val G120 = 2.094f
        private const val G140 = 2.443f
        private const val G145 = 2.530f
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
            val angleKnee = angleBetweenPoints2D(currentLandmark.get(24), currentLandmark.get(26), currentLandmark.get(28))
            val angleHip = angleBetweenPoints2D(currentLandmark.get(26), currentLandmark.get(24), currentLandmark.get(12))
            val ankleDistance = relativeDistance2D(currentLandmark.get(26), currentLandmark.get(28)) // distance between shoulders
            val kneeDistance = relativeDistance3D(currentLandmark.get(25), currentLandmark.get(26)) // distance between feet
            var phase = 0
            // Check the squat phase looking at the angle between hip, knee and foot
            // For each phase, check the form correctness looking at:
            // - the angle between shoulder, hip and knee
            // - the distance between the knees
            when {
                (angleKnee > G145) and (angleKnee < G160) -> {
                    phase = 0
                    if ((exerciseState == 1) or (exerciseState == 0))
                    {
                        if (angleHip < G140) {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, hipComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, hipComment + phase))
                            errorsAngleHipDuringExercise++
                            Log.d(FORM_TAG, "HIP " + phase)

                        }
                        if ((kneeDistance < (ankleDistance*4)) or (kneeDistance > ankleDistance*8))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsKneeDistanceDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
                (angleKnee > G90) and (angleKnee <= G145) -> {
                    phase = 1
                    if ((exerciseState == 2) or (exerciseState == 4))
                    {
                        if (angleHip <= G90) {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, hipComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, hipComment + phase))
                            errorsAngleHipDuringExercise++
                            Log.d(FORM_TAG, "HIP " + phase)

                        }
                        if ((kneeDistance < (ankleDistance*3)) or (kneeDistance > (ankleDistance*8)))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsKneeDistanceDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
                (angleKnee <= G90) -> {
                    phase = 2
                    if (exerciseState == 3)
                    {
                        if ((kneeDistance < (ankleDistance*3)) or (kneeDistance > (ankleDistance*8)))
                        {
                            repsPerformedWrong.add(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            //insertTriplet(Triple(seriesCount, repCount+1, kneeShoulderComment + phase))
                            errorsKneeDistanceDuringExercise++
                            Log.d(FORM_TAG, "KNEE-SHOULDER " + phase)
                        }
                    }
                }
            }

            //println("phase = " + phase)
            //println("KNEE = " + angleKnee*57.2958)
            //println("HIP = " + angleHip*57.2958)
            //println("knee distance = " + kneeDistance)
            //println("ankle = " + ankleDistance)
            //println("state = " + exerciseState)
            return phase
        }
        // Update the rep count according to the phase and the exercise state
        fun updateRepCount(phase: Int): Boolean{
            when {
                (exerciseState == 0) and (phase == 0) -> exerciseState++
                (exerciseState == 1) and (phase == 1) -> exerciseState++
                (exerciseState == 2) and (phase == 2) -> exerciseState++
                (exerciseState == 3) and (phase == 1) -> exerciseState++
                (exerciseState == 4) and (phase == 0) -> {
                    exerciseState = 0
                    repCount++
                    errorsDoneDuringExercise = errorsAngleHipDuringExercise + errorsKneeDistanceDuringExercise
                    //println("errori sull'hip = " + errorsAngleHipDuringExercise)
                    //println("errori knee distance = " + errorsKneeDistanceDuringExercise)
                    if (errorsDoneDuringExercise < exerciseThresholdOnErrors) {
                        errorsDoneDuringExercise = 0
                        errorsAngleHipDuringExercise = 0
                        errorsKneeDistanceDuringExercise = 0
                        return true
                    }
                    else {
                        errorsDoneDuringExercise = 0
                        if (errorsKneeDistanceDuringExercise == 0) kneeShoulderComment = ""
                        if (errorsAngleHipDuringExercise == 0) hipComment = ""
                        insertTriplet(Triple(seriesCount, repCount, kneeShoulderComment + hipComment))
                        errorsAngleHipDuringExercise = 0
                        errorsKneeDistanceDuringExercise = 0
                        println(repsDictionary)
                        return false
                    }
                }
            }
            return true
        }

        // Insert a triplet in the dictionary
        fun insertTriplet(key: Triple<Int, Int, String>) {
            if (repsDictionary.containsKey(key))
                repsDictionary[key] = repsDictionary.getValue(key) + 1
            else
                repsDictionary[key] = 1
        }

        // Euclidean distance between 2 points
        fun relativeDistance2D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            val dx = a.x() - b.x()
            val dy = a.y() - b.y()
            return sqrt(dx * dx + dy * dy)
        }

        fun relativeDistance3D(a: NormalizedLandmark, b: NormalizedLandmark): Float {
            val dx = a.x() - b.x()
            val dy = a.y() - b.y()
            val dz = a.z() - b.z()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        // Dot product between 2 vectors (a,b) and (b,c)
        fun dotProduct2D(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val abx = b.x() - a.x()
            val aby = b.y() - a.y()
            val bcx = b.x() - c.x()
            val bcy = b.y() - c.y()
            return abx * bcx + aby * bcy
        }

        fun dotProduct3D(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val abx = b.x() - a.x()
            val aby = b.y() - a.y()
            val abz = b.z() - a.z()
            val bcx = b.x() - c.x()
            val bcy = b.y() - c.y()
            val bcz = b.z() - c.z()
            return abx * bcx + aby * bcy + abz * bcz
        }

        // Angle in b between a and c (in radians)
        fun angleBetweenPoints2D(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val dot = dotProduct2D(a, b, c)
            val magAB = relativeDistance2D(a, b)
            val magBC = relativeDistance2D(b, c)
            return acos(dot / (magAB * magBC))
        }

        fun angleBetweenPoints3D(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Float {
            val dot = dotProduct3D(a, b, c)
            val magAB = relativeDistance3D(a, b)
            val magBC = relativeDistance3D(b, c)
            return acos(dot / (magAB * magBC))
        }
    }
}