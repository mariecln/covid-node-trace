package com.covid.nodetrace.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.covid.nodetrace.Contact
import com.covid.nodetrace.R
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.database.DatabaseFactory
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/**
 * An overview of the health status of the user.
 * The application gives options for setting their health status.
 */
class HealthStatusFragment : Fragment(), CoroutineScope {
    private val TAG: String = HealthStatusFragment::class.java.getSimpleName()

    private val statuses = arrayOf("Healthy", "Diagnosed with COVID-19")
    private val statusDescription = arrayOf("I am healthy", "I have been diagnosed with COVID-19")
    private lateinit var healthStatus: NumberPicker
    private lateinit var updateHealthStatusButton: Button

    override val coroutineContext: CoroutineContext = Dispatchers.Main + SupervisorJob()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.health_status_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        updateHealthStatusButton = view.findViewById(R.id.update_health_status_button)
        healthStatus = view.findViewById(R.id.health_status_picker)

        healthStatus?.setMinValue(0)
        healthStatus?.setMaxValue(statuses.size - 1)
        healthStatus?.setDisplayedValues(statusDescription)

        val healthStatusFromStorage: Int = sharedPref.getInt(getString(R.string.health_status), 0)
        //checkButtonState(healthStatusFromStorage)

/*        healthStatus.setOnValueChangedListener { picker, oldVal, newVal ->
            val newStatus = statuses[newVal]
            val currentStatus: Int = sharedPref.getInt(getString(R.string.health_status), 0)

            checkButtonState(currentStatus)

            if (newStatus == statuses[1]) {
                postUpdatedHealthStatusToDatabase()
            }

            with(requireActivity().getPreferences(Context.MODE_PRIVATE).edit()) {
                putInt(resources.getString(R.string.health_status), newVal)
                apply()
            }
        }*/

        updateHealthStatusButton.setOnClickListener{
            val newStatus = healthStatus.value

            if (statuses[newStatus] == statuses.elementAt(1)) {
                postUpdatedHealthStatusToDatabase()
            }

            Toast.makeText(
                context,
                "Your status is :  ${statuses[newStatus]}",
                Toast.LENGTH_LONG
            ).show()

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext[Job]!!.cancel()
    }

    /**
     * This function POSTs all contact IDs from the last fourteen days to the remote firebase database
     * @Note: Only the Contact IDs are send, not private data such as location.
     */
    fun postUpdatedHealthStatusToDatabase () {
        this.launch(Dispatchers.IO) {
            val appDatabase = AppDatabase.getInstance(requireContext())

            val today = System.currentTimeMillis()
            val fourteenDaysAgo = today - TimeUnit.DAYS.toMillis(14)
            val contacts : List<Contact> = appDatabase.contactDao().getContactsWithinTimePeriod(fourteenDaysAgo, today)
            val contactIDs : List<String> = contacts.map { contact -> contact.ID }

            if (contacts.size == 0)
                return@launch

            this.launch(Dispatchers.Default) {
                var uploadedIDs : MutableList<String> = mutableListOf()
                contactIDs.forEach{contactID ->
                    val fileUploaded = DatabaseFactory.getFirebaseDatabase().create(requireContext(), contactID)

                    if (fileUploaded)
                        uploadedIDs.add(contactID)
                }

            }
        }
    }

    /**
     * Checks the state of the health status UI component
     */
    fun checkButtonState (currentStatus : Int) {
        if (currentStatus == (healthStatus?.getValue() ?: 0)) {
            updateHealthStatusButton.setEnabled(false)
        }
        else {
            updateHealthStatusButton.setEnabled(true)
        }
    }
}