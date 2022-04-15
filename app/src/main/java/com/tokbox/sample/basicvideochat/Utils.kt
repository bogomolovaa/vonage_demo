package com.tokbox.sample.basicvideochat

internal class AudioBuffer(private val capacity: Int) {
    private var array: FloatArray = FloatArray(capacity) { 0f }
    var size = 0

    fun add(value: Float) {
        shift()
        array[0] = value
        size++
    }

    fun getSum() = array.sum()

    private fun shift(){
        array = FloatArray(capacity) { if (it == 0) 0f else array[it - 1] }
    }
}

