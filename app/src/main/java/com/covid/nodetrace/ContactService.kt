package com.covid.nodetrace

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.nearby.messages.*
import java.util.*


public class ContactService() : Service() {
    private val TAG = "ContactService"
    val CHANNEL_ID = "ForegroundServiceChannel"

    var mService : ContactService.LocalBinder? = null

    var mBound : Boolean = false
    var mActivityIsChangingConfiguration : Boolean = false
    var communicationType = CommunicationType.NONE

    /**
     * The communication type defines how the communication between devices with the app is done
     * There's currently two types in the app:
     * - NODE: Only sends IDs to devices with the app in the area
     * - USER: Only scans for contact IDs in the area
     */
    enum class CommunicationType {
        SCAN,
        ADVERTISE,
        SCAN_AND_ADVERTISE,
        NONE
    }
    companion object {
        val NODE_FOUND = "com.covid.nodetrace.ContactService.NODE_FOUND"
        val NODE_LOST = "com.covid.nodetrace.ContactService.NODE_LOST"
        val DISTANCE_UPDATED = "com.covid.nodetrace.ContactService.DISTANCE_UPDATED"
    }

    /**
     * A unique 128-bit UUID is generated and send and used as the main
     * identifier when communicating messages
     */
    private var uniqueMessage: Message? = null

    /**
     * A listener that is called by Google's Nearby Messages API when a message is received
     */
    private var messageListener: MessageListener? = null

    inner class LocalBinder : Binder() {

        /**
         * Changes the communication type
         */
        fun setCommunicationType(type: CommunicationType) {
            updateCommunicationType(type)
        }

        /**
         * The app must call this method within 5 secs from creating this service, else it will crash
         * See foreground service documentation for more info
         *
         * @param activity: The activity is needed to display a notification for the user
         */
        fun startForegroundService(activity: Activity, communicationType: CommunicationType) {
            createForegroundService(activity, communicationType)
        }

        fun stopForegroundService() {
            stopForeground(true)
        }
    }

    /**
     * Returns the binder implementation. This must return class implementing the additional manager interface that may be used in the bound activity.
     *
     * @return the service binder
     */
    protected fun getBinder(): LocalBinder? {
        return LocalBinder()
    }

    /**
     * Called when the connected between the activity and the service is established
     */
    override fun onBind(intent: Intent?): IBinder? {
        mBound = true
        return getBinder()
    }

    /**
     * The app creates a 'foreground' service. This is a process that can run in the background of the app
     * when the user is not actively interacting with the app.
     *
     * More information about foreground services can be found here:
     * https://developer.android.com/guide/components/foreground-services
     */
    fun createForegroundService(activity: Activity, communicationType: CommunicationType) {
        createNotificationChannel()
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(activity, 0, notificationIntent, 0)
        var notification : Notification? = null

        when(communicationType) {
            CommunicationType.SCAN -> {
                scanForNearbyDevices()

                notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Scanning for IDs")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .build()
            }
            CommunicationType.ADVERTISE -> {
                advertiseUniqueID()

                notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Advertising unique ID")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .build()
            }
            CommunicationType.SCAN_AND_ADVERTISE -> {
                advertiseUniqueID()
                scanForNearbyDevices()

                notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Scanning for IDs and Advertising unique ID")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .build()
            }
        }
        if (notification == null) {
            Log.e(TAG, "Communication type not set")
            return
        }

        startForeground(1, notification)
    }

    /**
     * Called when the service is started
     *
     * If the system kills the service when running low on memory 'START_STICKY' tells the
     * system to restart the service when enough resources are available again
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        if (uniqueMessage != null)
            Bluetooth.getBluetoothAccess(this)?.unpublish(uniqueMessage!!)

        if (messageListener != null)
            Bluetooth.getBluetoothAccess(this)?.unsubscribe(messageListener!!)
    }

    /**
     * Advertises (BLE term for sending/transmitting data) a unique ID to devices in the area
     */
    fun advertiseUniqueID () {
        val prefs = getSharedPreferences(getString(R.string.shared_preferences), MODE_PRIVATE)
        val uniqueID = UUID.randomUUID().toString()
        uniqueMessage = Message(uniqueID.toByteArray())

        Bluetooth.getBluetoothAccess(this)?.publish(uniqueMessage!!)
    }

    /**
     * Scans for devices in the area that advertise UUIDs
     */
    fun scanForNearbyDevices () {
        messageListener = object : MessageListener() {
            override fun onFound(message: Message) {
                val foundID = String(message.content)
                Log.d(TAG, "Found ID: $foundID")

                val broadcast: Intent = Intent(ContactService.NODE_FOUND)
                    .putExtra("FOUND_ID", foundID)

                LocalBroadcastManager.getInstance(baseContext).sendBroadcast(broadcast)
                Toast.makeText(applicationContext, "Found device:  + ${foundID}", Toast.LENGTH_LONG).show()
            }

            override fun onLost(message: Message) {
                val lostID = String(message.content)
                Log.d(TAG, "Lost sight of ID: $lostID")

                val broadcast: Intent = Intent(ContactService.NODE_LOST)
                    .putExtra("LOST_ID", lostID)

                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcast)
                Toast.makeText(applicationContext, "Lost device:  + ${lostID}", Toast.LENGTH_LONG).show()
            }

            override fun onDistanceChanged(message: Message?, distance: Distance?) {
                super.onDistanceChanged(message, distance)

                val content : ByteArray? = message?.content

                if (content == null)
                    return

                val ID = String(content)

                val broadcast: Intent = Intent(ContactService.DISTANCE_UPDATED)
                    .putExtra("ID", ID)
                    .putExtra("Distance", distance?.meters)

                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcast)


            }
        }

        Bluetooth.getBluetoothAccess(this)?.subscribe(messageListener!!)
    }

    /**
     * Updates the communication by first stopping all previous settings and then
     * calling calling advertising/scanning methods based on the chosen communication type
     */
    fun updateCommunicationType (newCommunicationType: CommunicationType) {
        if (newCommunicationType == communicationType)
            return

        stopAdvertisingAndScanning()

        when(newCommunicationType) {
            CommunicationType.SCAN -> {
                scanForNearbyDevices()
            }
            CommunicationType.ADVERTISE -> {
                advertiseUniqueID()
            }
            CommunicationType.SCAN_AND_ADVERTISE -> {
                scanForNearbyDevices()
                advertiseUniqueID()
            }
            else -> {
                Log.e(TAG, "Communication type not set")
            }
        }
    }

    /**
     * Stops both advertising IDs and scanning for IDs in the area by unsubscribing/unpublishing
     */
    fun stopAdvertisingAndScanning() {
        if (uniqueMessage != null)
            Bluetooth.getBluetoothAccess(this)?.unpublish(uniqueMessage!!)

        if (messageListener != null)
            Bluetooth.getBluetoothAccess(this)?.unsubscribe(messageListener!!)
    }


}