package com.covid.nodetrace

import android.app.AlertDialog
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.permissions.PermissionHelper
import com.covid.nodetrace.permissions.PermissionRationale
import com.covid.nodetrace.permissions.Permissions
import com.covid.nodetrace.permissions.Permissions.requiredPermissions
import com.covid.nodetrace.ui.AppViewModel
import com.covid.nodetrace.util.NetworkHelper
import com.covid.nodetrace.workmanager.RefreshDataWorker
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext


/**
 * The app's main activity is the entry point of the app and
 * keeps track of the different screens in the forms of multiple [Fragments]
 *
 * @see AppViewModel for contact data that is displayed in the UI and the communication type (Node or User) that is chosen
 * @see ContactService which starts a the background service that is actively scanning for / advertising to nearby devices.
 * @see ContactManager which handles all the data that is found when a contact between two devices with the app is found
 *
 */
class MainActivity : AppCompatActivity(), CoroutineScope {
    private val TAG: String = MainActivity::class.java.getSimpleName()

    private val model: AppViewModel by viewModels()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()
    private lateinit var auth: FirebaseAuth
    var resetBLE : Timer? = null

    private lateinit var contactManager : ContactManager
    private var contactService : ContactService? = null
    private var mService: ContactService.LocalBinder? = null
    private var mServiceBonded : Boolean = false
    private lateinit var communicationType : ContactService.CommunicationType

    enum class Screens {
        WELCOME,
        HEALTH_STATUS,
        CONTACT,
        SETTINGS
    }


    //================================================================================
    // Service logic
    //================================================================================

    /**
     * Called when a connection between the Activity and Service is created or disconnected
     */
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mService = service as ContactService.LocalBinder

            if (mService == null) {
                Log.e(TAG, "Service is null")
                return
            }

            onServiceBound(mService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            // Note: this method is called only when the service is killed by the system,
            // not when it stops itself or is stopped by the activity.
            // It will be called only when there is critically low memory, in practice never
            // when the activity is in foreground.
            auth.signOut()
            mService = null
            onServiceUnbound()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contactManager = ContactManager(this, lifecycle,model)
        contactManager.createDatabase(this)

        auth = FirebaseAuth.getInstance()
        authenticateUser(auth)

        val sharedPref = getPreferences(MODE_PRIVATE)
        communicationType = ContactService.CommunicationType.values()[sharedPref.getInt(getString(R.string.communication_type_state), 2)]

        //Starts the Contact Trace Service
        Intent(this, ContactService::class.java).also { intent ->
            bindService(intent, mServiceConnection, BIND_AUTO_CREATE)
        }

        //Listen for changes in the communication type set by the user in the app
        model.communicationType.observe(this, Observer<ContactService.CommunicationType> { communicationType ->
            mService?.setCommunicationType(communicationType)
        })

        val isConnected = NetworkHelper.isConnectedToNetwork(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NetworkHelper.registerNetworkCallback(this)
        }
    }

    /**
     * When the app starts/resumes we check if we have received all the needed permissions
     *
     * @see requiredPermissions for the permissions that the app needs
     */
    override fun onStart() {
        super.onStart()

        if (!Permissions.hasPermissions(this, requiredPermissions)) {
            val permissionRationale : PermissionRationale = PermissionRationale()
            permissionRationale.showRationale(this, PermissionHelper.Companion.PERMISSION_REQUEST_CODE)
        }
        else
        {
            val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            mBluetoothAdapter.enable();
            val locationManager: LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            var GpsStatus: Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!GpsStatus)
            {
                val dialog: AlertDialog.Builder = AlertDialog.Builder(this)
                dialog.setMessage("Please activate your location and reopen the app \n If you want to be redirect into the settings page, select YES")
                dialog.setTitle("Information")
                dialog.setPositiveButton("YES",
                    DialogInterface.OnClickListener { dialog, which ->
                        val intent1:Intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent1);
                    })
                dialog.setNegativeButton("cancel",
                    DialogInterface.OnClickListener { dialog, which ->
                        Toast.makeText(
                            applicationContext,
                            "App can´t be use properly",
                            Toast.LENGTH_LONG
                        ).show()
                    })
                val alertDialog: AlertDialog = dialog.create()
                alertDialog.show()
            }
        }
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if(currentUser != null){
            //reload();
        }

        //Load screen that was open the previous time the app was closed
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val storedScreenState : Int = sharedPref.getInt(getString(R.string.screen_state), 0)
        showScreen(Screens.values()[storedScreenState])

        contactManager.checkForRiskContacts()
        setPeriodicWorkRequest()
    }

    private fun setPeriodicWorkRequest() {

        val constraints= androidx.work.Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        var periodicWorkRequest =
            PeriodicWorkRequest.Builder(RefreshDataWorker::class.java, 1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(applicationContext).enqueue(periodicWorkRequest)
    }
/*    private fun restartCommunication()
    {
        resetBLE = Timer()
        resetBLE?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread(object : Runnable
                {
                    override fun run() {
                        onServiceUnbound()
                        onServiceBound(mService)
                        Log.d("my task : ", "used")
                    }

                });

            }
        }, 1, 600000)
    }*/


    /**
     * Inflate the menu; this adds items to the action bar if it is present.
     * Allows the user to navigate between screens
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * Navigate to different fragments based on the chosen item in the menu
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when (item.itemId) {
            R.id.welcome_menu -> {
                showScreen(Screens.WELCOME)
            }
            /*R.id.health_status_menu -> {
                showScreen(Screens.HEALTH_STATUS)
            }*/
            R.id.contact_menu -> {
                showScreen(Screens.CONTACT)
            }
            /*R.id.settings_menu -> {
                showScreen(Screens.SETTINGS)
            }*/
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
        return true
    }

    /**
     * Reinitialize the bluetooth communication type after permissions have been granted so
     * that the contact service works with the correct permissions
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        communicationType = ContactService.CommunicationType.values()[sharedPref.getInt(getString(R.string.communication_type_state), 2)]
        mService?.setCommunicationType(communicationType)

    }
    override fun onDestroy() {
        super.onDestroy()
        coroutineContext[Job]!!.cancel()
    }


    /**
     * Called when activity binds to the service. The parameter is the object returned in [Service.onBind] method in your service.
     */
    fun onServiceBound(binder: ContactService.LocalBinder?) {
        mService = binder
        mService?.startForegroundService(this, communicationType)
        mServiceBonded = true
    }


    /**
     * Called when activity unbinds from the service.
     */
    fun onServiceUnbound() {
        mServiceBonded = false
        mService?.stopForegroundService()
        mService = null
    }

    /**
     * Authenticates an anonymous user of the app. The user doesn't need to sign up or sign in
     */
    private fun authenticateUser(firebaseAuth: FirebaseAuth) {
        /*firebaseAuth.signInAnonymously().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "signInAnonymously:success")
                val user = auth.currentUser
            } else {
                Log.w(TAG, "signInAnonymously:failure", task.exception)
            }
        }*/


         firebaseAuth.signInAnonymously()
            .addOnCompleteListener(this@MainActivity,
                OnCompleteListener<AuthResult?> { task ->
                    Log.d("FirebaseAuth", "signInAnonymously:onComplete:" + task.isSuccessful)

                    // If sign in fails, display a message to the user. If sign in succeeds
                    // the auth state listener will be notified and logic to handle the
                    // signed in user can be handled in the listener.
                    if (!task.isSuccessful) {
                        Log.w("FirebaseAuth", "signInAnonymously", task.exception)
                        Toast.makeText(
                            this@MainActivity, "Authentication failed.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // ...
                })

    }

    /**
     * Handles the navigation between screens and stores the last chosen screen in
     * local storage so that the next time the app is used it will start on that screen
     */
    fun showScreen(screen: Screens) {
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putInt(resources.getString(R.string.screen_state), screen.ordinal)
            apply()
        }

        when (screen) {
            Screens.WELCOME -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.welcome_fragment)
            }
            /*Screens.HEALTH_STATUS -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.health_status_fragment)
            }*/
            Screens.CONTACT -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.contact_fragment)
            }
            /*Screens.SETTINGS -> {
                findNavController(R.id.nav_host_fragment).navigate(R.id.settings_fragment)
            }*/
        }
    }
}