package com.example.cadenceplayer.fft

import android.util.Log
import com.example.cadenceplayer.model.Acceleration
import kotlin.math.*

class Bpm(
    private val maxBpm: Int,
    private val minBpm: Int
) {

    // Get time interval
    private fun getTimeInterval(accelerations: List<Acceleration>): Float {
        Log.i("FFT", "Getting time interval")
        val fftAccTimestamps = mutableListOf<Long>()
        // Put timestamps into array
        for (acceleration in accelerations) {
            fftAccTimestamps.add(acceleration.timestamp.toLong())
        }

        // Get difference between max and min timestamps for total time interval
        return (fftAccTimestamps.maxOrNull()?.minus(fftAccTimestamps.minOrNull()!!))?.toFloat()
            ?.div(1000)!!
    }

    // Get fft intensities array
    private fun getFftArray(accelerations: List<Acceleration>): List<Double> {
        Log.i("FFT", "Getting FFT array, accelerationsSize = ${accelerations.size}")
        // Initialise accelerations and times arrays
        val fftArray = mutableListOf<Double>()

        for (acceleration in accelerations) {
            // Combine all accelerometer axes, add to acceleration array and add times to array
            val accelerationX = acceleration.accelerationX
            val accelerationY = acceleration.accelerationY
            val accelerationZ = acceleration.accelerationZ

            val accelerationOverall =
                sqrt(
                    (accelerationX!!.pow(2)).plus((accelerationY!!.pow(2)))
                        .plus((accelerationZ!!.pow(2))).plus((9.807.pow(2)))
                )

            fftArray.add(accelerationOverall)
        }

        // Cast accelerations as DoubleArray
        val fftAccDoubleArray = fftArray.toDoubleArray()
        val fftArraySize = fftAccDoubleArray.size

        // Carry out fft to convert the accelerations into bpm intensities
        Log.i("FFT", "bpm array size = $fftArraySize")

        // Get absolute fft
        return fftAccDoubleArray.map { n -> abs(n) }
    }

    private fun getTimesArray(accelerations: List<Acceleration>): Array<Float?> {
        Log.i("FFT", "Getting times array")
        val absoluteFft = getFftArray(accelerations)

        val fftTimesArray = arrayOfNulls<Float>(absoluteFft.size)

        val totalTimeInterval = getTimeInterval(accelerations)

        // Loop through each index of bpm array and convert to beats per minute
        for (i in fftTimesArray.indices) {
            fftTimesArray[i] = i.times(60).div(totalTimeInterval)
        }

        return fftTimesArray
    }

    private fun getIndicesAtMinMaxBpm(fftTimesArray: Array<Float?>): Indices {
        Log.i("FFT", "Getting indices")

        return Indices(fftTimesArray.indexOfFirst { it!! > minBpm }, fftTimesArray.indexOfFirst { it!! > maxBpm })
    }

    // Get the fft times up to the max bpm wanted
    private fun getSlicedTimesArray(accelerations: List<Acceleration>): List<Float?> {
        Log.i("FFT", "Getting sliced time array")
        val fftTimesArray = getTimesArray(accelerations)
        val indices = getIndicesAtMinMaxBpm(fftTimesArray)
        val indexAtMaxBpm = indices.maxIndex
        val indexAtMinBpm = indices.minIndex
        return fftTimesArray.slice(indexAtMinBpm until indexAtMaxBpm)
    }

    // Get the fft intensities up to the max bpm wanted
    private fun getSlicedFftArray(accelerations: List<Acceleration>): List<Double> {
        Log.i("FFT", "Getting sliced FFT array")
        val fftArray = getFftArray(accelerations)
        val fftTimesArray = getTimesArray(accelerations)
        val indices = getIndicesAtMinMaxBpm(fftTimesArray)
        val indexAtMaxBpm = indices.maxIndex
        val indexAtMinBpm = indices.minIndex
        return fftArray.slice(indexAtMinBpm until indexAtMaxBpm)
    }

    // Calculate the bpm based off the two sliced arrays
    fun calculateBpm(accelerations: List<Acceleration>): Float? {
        Log.i("FFT", "Calculating bpm")
        val fftArray = getSlicedFftArray(accelerations)
        // Get the value of highest intensity reading as this is the most probable bpm
        val fftPeak =
            if (fftArray.size > 2 && fftArray.indexOfFirst { it == fftArray.maxOrNull()!! } < 3) {
                // If the peak is in the first two indices, choose the third highest value
                fftArray.sortedByDescending { it.absoluteValue }[2]
            } else {
                fftArray.maxOrNull()!!
            }

        val fftTimes = getSlicedTimesArray(accelerations)

        // Get index of highest intensity value
        val fftPeakIndex = fftArray.indexOfFirst { it == fftPeak }

        // Get time axis value of peak intensity
        return fftTimes[fftPeakIndex]
    }

    inner class Indices (
        val minIndex: Int,
        val maxIndex: Int
    )
//
//    // Create the graph
//    fun createFftGraph(fftChart: LineChart) {
//        val fftAccEntries = mutableListOf<Entry>()
//        val fftTimes = getSlicedTimesArray()
//        val fftArray = getSlicedFftArray()
//        val totalTimeInterval = getTimeInterval()
//
//        // Loop through values and add entries to array for graph
//        for (i in fftTimes.indices) {
//            fftAccEntries.add(
//                Entry(
//                    i.times(60).div(totalTimeInterval),
//                    fftArray[i].toFloat()
//                )
//            )
//        }
//
//        val indexAtMinBpm = getIndexAtMinBpm()
//
//        val entriesUpToMaxBpm = fftAccEntries.slice(indexAtMinBpm until fftAccEntries.size)
//
//        // Must be sorted by x axis for graph to work
//        Collections.sort(entriesUpToMaxBpm, EntryXComparator())
//
//        // Update graph
//        val fftChartAccDataSet = LineDataSet(entriesUpToMaxBpm, "FFT")
//        val fftChartData = LineData(fftChartAccDataSet)
//        fftChart.data = fftChartData
////        fftChart.xAxis.axisMinimum = 0F
////        fftChart.axisLeft.axisMinimum = 0F
////        fftChart.axisLeft.axisMaximum = 1000F
////        fftChart.axisRight.axisMinimum = 0F
////        fftChart.axisRight.axisMaximum = 1000F
//        fftChart.invalidate()
//    }
}