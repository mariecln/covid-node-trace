package com.covid.nodetrace

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.covid.nodetrace.bluetooth.BleScanner
import com.covid.nodetrace.bluetooth.OnAdvertisementFound
import com.covid.nodetrace.bluetooth.ScanActive
import com.trace.api.data.BleAdvertiser
import kotlinx.coroutines.*
import no.nordicsemi.android.support.v18.scanner.ScanResult
import java.util.*
import kotlin.coroutines.CoroutineContext


public class ContactService() : Service(), CoroutineScope {
    private val TAG = "ContactService"
    val CHANNEL_ID = "ForegroundServiceChannel"

    companion object {
        // Consider the device out of range if no advertisements are found for 8 seconds
        val CONTACT_OUT_OF_RANGE_TIMEOUT : Int = 8

        public final val NODE_IDENTIFIER = 0xFFFF

        val NODE_FOUND = "com.covid.nodetrace.ContactService.NODE_FOUND"
        val NODE_LOST = "com.covid.nodetrace.ContactService.NODE_LOST"
        val UPDATE_RSSI = "com.covid.nodetrace.ContactService.DISTANCE_UPDATED"
    }

    var mService : ContactService.LocalBinder? = null

    var mBound : Boolean = false
    var mActivityIsChangingConfiguration : Boolean = false
    var communicationType = CommunicationType.NONE

    var backgroundScanner : BleScanner? = null
    var bleAdvertiser : BleAdvertiser? = null
    var deviceInRangeTask : Timer? = null
    var foundDevices : HashMap<String, ScanResult> = hashMapOf()

    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()

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

        stopAdvertisingAndScanning()
        coroutineContext[Job]!!.cancel()
    }

    /**
     * Advertises (BLE term for sending/transmitting data) a unique ID to devices in the area
     */
    fun advertiseUniqueID () {

        val adapter : BluetoothAdapter? =  BluetoothAdapter.getDefaultAdapter()
        adapter?.setName("NODE")

        bleAdvertiser = BleAdvertiser()

        val randomUUID = UUID.randomUUID().toString().toByteArray().copyOfRange(0, 16)

        val advertisement = AdvertiseData.Builder()
            .addManufacturerData(NODE_IDENTIFIER, randomUUID)
            .setIncludeDeviceName(true)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        bleAdvertiser?.advertiseData(advertisement, settings)
    }

    /**
     * Scans for devices in the area that advertise UUIDs
     */
    private fun scanForNearbyDevices () {
        //Scan in the background for the device address that was fetched from the cloud
        backgroundScanner = BleScanner(applicationContext, object : ScanActive {
            override fun isBleScannerActive(isActive: Boolean) {

            }
        })

        deviceInRangeTask = Timer()
        deviceInRangeTask?.schedule(object : TimerTask() {
            override fun run() {
                checkDevicesInRangeTask()
            }
        }, 1, 1000)

        backgroundScanner?.scanLeDevice(object : OnAdvertisementFound {
            override fun onAdvertisementFound(result: ScanResult) {
                val device = result.device
                val data = result.scanRecord?.getManufacturerSpecificData(NODE_IDENTIFIER)

                val newDeviceFound = hasNewDeviceBeenFound(result)
                foundDevices.set(result.device.address, result)

                if (newDeviceFound) {
                    val nodeID = result.scanRecord?.getManufacturerSpecificData(NODE_IDENTIFIER)?.let {
                        byteArrayToHexString(it)
                    }
                    val broadcast: Intent = Intent(ContactService.NODE_FOUND)
                        .putExtra("FOUND_ID", nodeID)

                    LocalBroadcastManager.getInstance(baseContext).sendBroadcast(broadcast)

                    Toast.makeText(
                        applicationContext,
                        "Found device:  ${nodeID}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else {
                    val nodeID = result.scanRecord?.getManufacturerSpecificData(NODE_IDENTIFIER)?.let {
                        byteArrayToHexString(it)
                    }

                    val broadcast: Intent = Intent(ContactService.UPDATE_RSSI)
                        .putExtra("ID", nodeID)
                        .putExtra("RSSI", result.rssi)

                    LocalBroadcastManager.getInstance(baseContext).sendBroadcast(broadcast)
                }
            }
        })
    }

    private fun hasNewDeviceBeenFound(result: ScanResult) : Boolean  {

        val iterator = foundDevices.iterator()
        while(iterator.hasNext()){
            val item = iterator.next()
            if(item.key == result.device.address){
                return false
            }
        }
        return true
    }

    private fun checkDevicesInRangeTask() {
        val iterator: MutableIterator<MutableMap.MutableEntry<String, ScanResult>> = foundDevices.iterator()

        while (iterator.hasNext()) {
            val device = iterator.next()

            val currentTime = SystemClock.elapsedRealtime() / 1000
            val storedTime = device.value.timestampNanos / 1000000000
            val millisecondDifference =  (currentTime - storedTime).toLong()

            if (millisecondDifference > CONTACT_OUT_OF_RANGE_TIMEOUT) {
                val nodeID = device.value.scanRecord?.getManufacturerSpecificData(NODE_IDENTIFIER)?.let {
                    byteArrayToHexString(it)
                }
                this.launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Lost device:  ${nodeID}", Toast.LENGTH_LONG).show()
                }

                val broadcast: Intent = Intent(ContactService.NODE_LOST)
                    .putExtra("LOST_ID", nodeID)

                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcast)

                removeDeviceFromFoundList(device.key)
            }
        }
    }

    private fun removeDeviceFromFoundList(deviceAddress: String) {
        foundDevices.remove(deviceAddress)
    }
    /**
     * Updates the communication by first stopping all previous settings and then
     * calling calling advertising/scanning methods based on the chosen communication type
     */
    fun updateCommunicationType(newCommunicationType: CommunicationType) {
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
        deviceInRangeTask?.cancel()
        backgroundScanner?.stopScan()
        bleAdvertiser?.stopAdvertising()
    }

    /**
     * Converts byte array to hex string
     *
     * @param bytes The data
     * @return String represents the data in HEX string
     */
    fun byteArrayToHexString(bytes: ByteArray): String? {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }


}