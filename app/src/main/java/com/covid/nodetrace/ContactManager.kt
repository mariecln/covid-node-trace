package com.covid.nodetrace

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.covid.nodetrace.ContactService.Companion.NODE_FOUND
import com.covid.nodetrace.ContactService.Companion.NODE_LOST
import com.covid.nodetrace.ContactService.Companion.UPDATE_RSSI
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.database.DatabaseFactory
import com.covid.nodetrace.permissions.Permissions
import com.covid.nodetrace.ui.AppViewModel
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext


class ContactManager(context: Context, lifecycle: Lifecycle, viewModel: AppViewModel) : LifecycleObserver, CoroutineScope {
    private val TAG = "ContactManager"

    private val model : AppViewModel = viewModel
    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()
    private lateinit var appDatabase : AppDatabase
    private var mContext : Context? = context
    private lateinit var contacts : HashSet<Contact>
    private lateinit var mDataBroadcastReceiver: BroadcastReceiver
    private var rssiEntries : HashMap<String, MutableList<Int>> = hashMapOf<String, MutableList<Int>>()



    init {
        lifecycle.addObserver(this)
        initializeBroadcastReceiver()
        LocalBroadcastManager.getInstance(context).registerReceiver(
            mDataBroadcastReceiver,
            makeBroadcastFilter()
        )
        contacts = HashSet<Contact>()

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart () {
        updateUserInterfaceWithContactHistory()
    }

    /**
     * Broadcast receiver that receives data from the background service.
     * Must be initialized before registering the LocalBroadcastManager receiver
     */
    private fun initializeBroadcastReceiver () {
        mDataBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                when (action) {
                    NODE_FOUND -> {
                        /*val foundID: String? = intent.getStringExtra("FOUND_ID")*/

                        val extras = intent.extras
                        val foundID = extras!!.getString("FOUND_ID")
                        val name = extras!!.getString("NAME")
                        if (foundID == null)
                            return
                        if (name == null)
                            return

                        val contact = createNewContact(foundID, name)
                        contacts.add(contact)
                    }
                    UPDATE_RSSI -> {
                        val ID = intent.getStringExtra("ID")
                        val rssi: Int = intent.getIntExtra("RSSI", 0)

                        if (rssi == 0)
                            return

                        if (ID != null) {
                            updateContactSignalStrength(ID, rssi)
                        }
                    }
                    NODE_LOST -> {
                        val lostID = intent.getStringExtra("LOST_ID") ?: return

                        val contact: Contact =
                            updateContactDuration(lostID, Date()) ?: return
                        Log.d("Database", "contact lost is "+contact)

                        if (rssiEntries[lostID] != null) {
                            var rssiValue : Int = 0;

                            for (storedRssiValue : Int in rssiEntries[lostID]!!) {
                                rssiValue += storedRssiValue / (rssiEntries[lostID]?.size!!)
                            }

                            contact.rssi = rssiValue
                        }
                        insertContact(contact)
                        updateUserInterfaceWithContactHistory()
                        contacts.remove(contact)
                    }
                }
            }
        }
    }


    /**
     * Fetches all the found contacts from the local database and displays them within the
     * Contact page of the app
     */
    fun updateUserInterfaceWithContactHistory () {
        Log.d("Database", "Update Interface")
        this.launch(Dispatchers.IO) {
            val allContacts : List<Contact> = appDatabase.contactDao().getAll()

            this.launch(Dispatchers.Main) {
                model.contacts.value = allContacts
            }
        }
    }

    fun createDatabase(activity: Activity) {
        this.launch(Dispatchers.IO) {
            appDatabase = AppDatabase.getInstance(activity)
            val allContacts : List<Contact> = appDatabase.contactDao().getAll()
            allContacts.forEach { contact ->
                println(
                    "Contact -> " +
                            " ID: ${contact.ID}" +
                            " contact_name: ${contact.name}" +
                            " date: ${contact.date}" +
                            " duration: " + "${contact.duration / 1000f} sec" +
                            " rssi: " + "${contact.rssi} dB" +
                            " location: " + " {lat: ${contact.latitude}" + "," + " long: ${contact.longitude}}"
                )
            }
        }
    }


    /**
     * Retrieves all the contact IDs of the last 14 days and then compares
     * them to contact IDs stored in the local database
     */
    fun checkForRiskContacts () {
        this.launch(Dispatchers.Default) {
            if (mContext == null)
                return@launch

            val contactIDsRetrieved = DatabaseFactory.getFirebaseDatabase().read(mContext!!)

            compareDatabaseIDsToLocalIDs(contactIDsRetrieved)
        }
    }

    /**
     * Retrieves all contacts from the local database and compares them with the IDs
     * retrieved from the remote database
     */
    suspend fun compareDatabaseIDsToLocalIDs (databaseContactIDs : List<String>) {
        withContext(Dispatchers.IO) {
            val localContacts : List<Contact> = appDatabase.contactDao().getAll()
            val localContactIDs : List<String> = localContacts.map { contact -> contact.ID }

            val riskContactIDs = localContactIDs.filter { localContact -> databaseContactIDs.contains(localContact) }
            updateContactRiskLevel(riskContactIDs)
        }
    }

    /**
     * Updates the Risk level in existing contact entries in the local database
     */
    suspend fun updateContactRiskLevel (riskContactIDs : List<String>) {
        withContext(Dispatchers.IO){
            riskContactIDs.forEach{ contactID ->
                appDatabase.contactDao().updateHealthStatus(contactID, HealthStatus.SICK.toString())
            }
        }
    }

    /**
     * Creates a new Contact based on a 128-bit UUID. The moment the function is
     * called it logs the current Unix date and attempts to get the location (if permission is given)
     */
    fun createNewContact(ID: String, name : String) : Contact {
        val date = getCurrentUnixDate()
        val location : Location? = getCurrentLocation()

        if (location != null)
        {
            return Contact(ID, name, date, location.latitude, location.longitude)
        }
        else {
            return Contact(ID, name, date)
        }
    }

    /**
     * Updates the distance the contact was at if it's less than the previous known contact distance
     */
    fun updateContactSignalStrength(ID: String, rssi: Int) {
        for (contact in contacts) {
            if (contact.ID == ID) {
                if (rssiEntries[ID] == null) {
                    rssiEntries[ID] = mutableListOf(rssi)
                }
                else {
                    rssiEntries[ID]?.add(rssi)
                }
            }
        }
    }

    /**
     * Updates the contact's duration. The contact's duration will usually be updated when the device is out of range
     * or when the BLE signal is lost.
     */
    fun updateContactDuration(ID: String, contactEnd: Date) : Contact? {
        for (contact in contacts) {
            if (contact.ID == ID) {
                val contactStart = Date(contact.date)
                contact.duration = contactEnd.time - contactStart.time
                return contact
            }
        }
        return null
    }

    /**
     * Insert a contact into the local database
     */
    fun insertContact(contact: Contact) {
        Log.d("Database", "Insert to database")
        val uuid = UUID.randomUUID().toString().toByteArray().copyOfRange(0, 16)
        contact.ID = uuid.toString()
        this.launch(Dispatchers.IO) {
            appDatabase.contactDao().insert(contact)
        }
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        if (mContext != null)
            LocalBroadcastManager.getInstance(mContext!!).unregisterReceiver(mDataBroadcastReceiver)

        coroutineContext[Job]!!.cancel()
        mContext = null
    }

    /**
     * Listen for broadcast messages sent from the Android Service that's running in the background listening
     * for BLE messages. The filter listens for NODE lost & found messages, as well as distance updates
     */
    private fun makeBroadcastFilter(): IntentFilter {
        val intentFilter = IntentFilter()
        intentFilter.addAction(ContactService.NODE_FOUND)
        intentFilter.addAction(ContactService.NODE_LOST)
        intentFilter.addAction(ContactService.UPDATE_RSSI)
        return intentFilter
    }

    /**
     * Gets the current time in milliseconds since 1970.
     * This format is used because a numerical date is easier to compare and is not influenced by timezones.
     * It can be converted to local time to make it easy to interpret.
     */
    fun getCurrentUnixDate() : Long {
        return System.currentTimeMillis()
    }

    /**
     * Gets the user's current location if permission has been granted
     */
    fun getCurrentLocation() : Location? {
        //If context is not available then stop execution
        if (mContext == null)
            return null

        //If location permission has not been granted have to stop as we can't request permission while the app is in the background
        if (!Permissions.hasPermissions(
                mContext!!,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )) {
            return null
        }

        val lastKnownLocation: Location? = getLastKnownLocation()

        //Lastly if the GPS can't retrieve the location values we stop execution
        if (lastKnownLocation == null) {
            Log.e(TAG, "Can't retrieve location")
            return null
        }

        return lastKnownLocation
    }

    /**
     * This function checks for the last known location of the user
     * Using a custom permission checker therefore we suppress the "MissingPermission" flag
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation () : Location? {
        val locationManager : LocationManager = mContext?.getSystemService(LOCATION_SERVICE) as LocationManager
        val providers: List<String> = locationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val lastKnownLocation: Location = locationManager.getLastKnownLocation(provider)
                ?: continue
            if (bestLocation == null || lastKnownLocation.accuracy < bestLocation.accuracy) {
                // Found best last known location: %s", l);
                bestLocation = lastKnownLocation
            }
        }
        return bestLocation
    }
}

