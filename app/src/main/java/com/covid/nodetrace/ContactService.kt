package com.covid.nodetrace

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.messages.Message
import com.google.android.gms.nearby.messages.MessageListener
import java.util.*


public class ContactService () : Service() {
    private val TAG = "ContactService"
    val CHANNEL_ID = "ForegroundServiceChannel"

    /**
     * A unique 128-bit UUID is generated and send and used as the main
     * identifier when communicating messages
     */
    private lateinit var uniqueMessage: Message

    /**
     * A listener that is called by Google's Nearby Messages API when a message is received
     */
    private lateinit var messageListener: MessageListener

    override fun onCreate() {
        super.onCreate()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    /**
     * The app creates a 'foreground' service. This is a process that can run in the background of the app
     * when the user is not actively interacting with the app.
     *
     * More information about foreground services can be found here:
     * https://developer.android.com/guide/components/foreground-services
     *
     * If the system kills the service when running low on memory 'START_STICKY' tells the
     * system to restart the service when enough resources are available again
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        advertiseUniqueID()
        scanForNearbyDevices()

        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Advertising unique ID")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        return START_STICKY
    }


    /**
     * Here we create a notification channel for the foreground service
     * This allows the user to see that the app is active when they see the app
     * running in the status bar of their phone
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * When the app is shut down (not running in the background) we have to release
     * the resources that are advertising / scanning for messages in the area
     */
    override fun onDestroy() {
        super.onDestroy()
        Nearby.getMessagesClient(this).unpublish(uniqueMessage)
        Nearby.getMessagesClient(this).unsubscribe(messageListener)
    }

    /**
     * Advertises (BLE term for sending/transmitting data) a unique ID to devices in the area
     */
    fun advertiseUniqueID () {
        val prefs = getSharedPreferences(getString(R.string.shared_preferences), MODE_PRIVATE)
        val uniqueID = UUID.randomUUID().toString()
        uniqueMessage = Message(uniqueID.toByteArray())

        Nearby.getMessagesClient(this).publish(uniqueMessage)
    }

    /**
     * Scans for devices in the area that advertise UUIDs
     */
    fun scanForNearbyDevices () {
        messageListener = object : MessageListener() {
            override fun onFound(message: Message) {
                val metUserUID = String(message.content)
                Log.d(TAG, "Found user: $metUserUID")
            }

            override fun onLost(message: Message) {
                val lostUserUID = String(message.content)
                Log.d(TAG, "Lost sight of user: $lostUserUID")
            }
        }

        Nearby.getMessagesClient(this).subscribe(messageListener)
    }


}