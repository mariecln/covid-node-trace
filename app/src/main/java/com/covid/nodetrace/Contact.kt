package com.covid.nodetrace

import android.graphics.Color
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.covid.nodetrace.ui.HealthStatusFragment

public enum class HealthStatus {
    HEALTHY,
    SICK
}

/**
 * A Contact is defined as an interaction between two devices
 * One of the devices advertises/sends a contact ID (a unique 128-bit UUID), which is scanned/received by the other device.
 *
 * When a contact is started we log metrics such as the date, location, duration, estimated distance.
 * @Note: This data is all kept in the Room database and not shared with a remote database
 */
@Entity
open class Contact (
    @PrimaryKey open var ID : String,
    @ColumnInfo(name = "name") open val name : String,
    @ColumnInfo(name = "date") open val date : Long,
    @ColumnInfo(name = "latitude") open var latitude : Double = 0.0,
    @ColumnInfo(name = "longitude") open var longitude : Double = 0.0,
    @ColumnInfo(name = "duration") open var duration : Long = -1,
    @ColumnInfo(name = "rssi") open var rssi : Int = 0,
    @ColumnInfo(name = "health_status") open var healthStatus : String = HealthStatus.HEALTHY.toString()) {
}
