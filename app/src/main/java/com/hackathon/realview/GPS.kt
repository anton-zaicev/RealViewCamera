package com.hackathon.realview

object GPS {
    private val sb = StringBuilder(20)

    fun latitudeRef(latitude: Double): String {
        return if (latitude < 0.0) "S" else "N"
    }

    fun longitudeRef(longitude: Double): String {
        return if (longitude < 0.0) "W" else "E"
    }

    @Synchronized
    fun convert(input: Double): String {
        var output = input
        output = Math.abs(output)
        val degree = output.toInt()
        output *= 60.0
        output -= degree * 60.0
        val minute = output.toInt()
        output *= 60.0
        output -= minute * 60.0
        val second = (output * 1000.0).toInt()
        sb.setLength(0)
        sb.append(degree)
        sb.append("/1,")
        sb.append(minute)
        sb.append("/1,")
        sb.append(second)
        sb.append("/1000")
        return sb.toString()
    }
}