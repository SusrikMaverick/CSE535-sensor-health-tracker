package com.example.assignmentapp.sensor

data class AccelerometerSample(
    val timestampNanos: Long,
    val x: Float,
    val y: Float,
    val z: Float
)
