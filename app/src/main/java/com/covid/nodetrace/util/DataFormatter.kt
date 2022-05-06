package com.covid.nodetrace.util

import com.covid.nodetrace.HealthStatus
import com.covid.nodetrace.ui.TimeRange
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DataFormatter {

    /**
     * Formats the {@see HealthStatus} enum into a descriptive string
     */
    fun createHealthStatusFormat(healthStatus: HealthStatus): String {
        when(healthStatus) {
            HealthStatus.HEALTHY -> {
                return "Safe"
            }
            HealthStatus.SICK -> {
                return "Risk"
            }
        }
    }

    /**
     * Formats the duration in milliseconds into a string with format: [00:00:00]
     */


fun createDurationFormat(durationInMilliseconds : Long) : String {
        /*val seconds : Long = TimeUnit.MILLISECONDS.toSeconds(durationInMilliseconds) % 60
    val minutes : Long = TimeUnit.MILLISECONDS.toMinutes(durationInMilliseconds) % 60
    val hours : Long = TimeUnit.MILLISECONDS.toHours(durationInMilliseconds) % 60*/

    val hours = TimeUnit.MILLISECONDS.toHours(durationInMilliseconds)
    val durationWithoutHours = durationInMilliseconds - TimeUnit.HOURS.toMillis(hours)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationWithoutHours)
    val durationWithoutMinutes = durationWithoutHours- TimeUnit.MINUTES.toMillis(minutes)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationWithoutMinutes)

    val timeRange : TimeRange = if (minutes < 1L) TimeRange.SEC else if (minutes >= 1L && hours < 1L) TimeRange.MIN else TimeRange.HOURS

    when(timeRange) {
        TimeRange.SEC -> {
            return seconds.toString() + " " + timeRange.toString().toLowerCase(Locale.ROOT)
        }
        TimeRange.MIN -> {
            return if (seconds > 9)
                minutes.toString() + ":" + seconds.toString() + " " +timeRange.toString().toLowerCase(
                    Locale.ROOT)
            else
                minutes.toString() + ":0" + seconds.toString() + " " + timeRange.toString().toLowerCase(
                    Locale.ROOT)
        }
        TimeRange.HOURS -> {
            return if (seconds > 9 && minutes <= 9)
                hours.toString() + "0:" + minutes.toString() + ":" + seconds.toString() + " " + timeRange.toString().toLowerCase(
                    Locale.ROOT)
            else if (seconds <= 9 && minutes > 9)
                hours.toString() + ":" + minutes.toString() + ":0" + seconds.toString() + " " + timeRange.toString().toLowerCase(
                    Locale.ROOT)
            else
                hours.toString() + ":" + minutes.toString() + ":" + seconds.toString() + " " + timeRange.toString().toLowerCase(
                    Locale.ROOT)
        }
    }

}


/**
 * Converts distance in meters to a string and returns nothing if distance was not recorded
 */
fun createDistanceFormat(distanceInMeters : Double) : String {
    if (distanceInMeters == -1.0)
        return "-"
    else return distanceInMeters.toString() + " m"
}

/**
 * Create a string with longitude and latitude of the location
 */
fun createLocationFormat (latitude : Double, longitude : Double) : String {
    if (latitude == 0.0 || longitude == 0.0)
        return "- , -"
    else return latitude.toBigDecimal().setScale(2, RoundingMode.UP).toString() +", " +
            longitude.toBigDecimal().setScale(2, RoundingMode.UP).toString()
}

/**
 * Creates a short date notation [dd-MMM-yy] from a Unix timestamp
 */
fun createShortDateFormat(unixTimeStamp: Long) : String? {
    try {
        val date = SimpleDateFormat("dd-MMM-yy")
        val localDate = Date(unixTimeStamp)
        return date.format(localDate)
    } catch (e: Exception) {
        return e.toString()
    }
}

/**
 * Creates a date notation [dd-MMM-yy hh:mm] from a Unix timestamp
 */

fun createDateFormat(unixTimeStamp: Long) : String? {
    try {
        val date = SimpleDateFormat("dd-MMM-yy hh:mm")
        val localDate = Date(unixTimeStamp)
        return date.format(localDate)
    } catch (e: Exception) {
        return e.toString()
    }
}
}