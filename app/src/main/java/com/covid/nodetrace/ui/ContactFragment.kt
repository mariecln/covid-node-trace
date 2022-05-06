package com.covid.nodetrace.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.covid.nodetrace.Contact
import com.covid.nodetrace.ContactManager
import com.covid.nodetrace.HealthStatus
import com.covid.nodetrace.R
import com.covid.nodetrace.database.AppDatabase
import com.covid.nodetrace.database.DatabaseFactory
import com.covid.nodetrace.util.DataFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext


/**
 * The contact screen of the app indicates all the contacts that the user has had with people that
 * were in the same vicinity and time period as them. It contains a list of encounters the user has had in the past
 */
class ContactFragment : Fragment() {
    private val model: AppViewModel by activityViewModels()

    private lateinit var contactHistoryListView : ListView
    private lateinit var contactHistoryAdapter : ContactHistoryAdapter

    private lateinit var bottom_sheet : NestedScrollView
    private lateinit var sheetBehavior : BottomSheetBehavior<NestedScrollView>

    private lateinit var healthStatusTitle : TextView
    private lateinit var nameTitle : TextView
    private lateinit var dateTitle : TextView
    private lateinit var durationTitle : TextView
    private lateinit var rssiTitle : TextView

    private lateinit var contactHealthStatus : TextView
    private lateinit var contactName : TextView
    private lateinit var contactDate : TextView
    private lateinit var contactDuration : TextView
    private lateinit var contactRssi : TextView
    private var mContacts : List<Contact> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.contact_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        contactHistoryListView = view.findViewById(R.id.contact_history_list_view) as ListView

        healthStatusTitle = view.findViewById(R.id.health_title) as TextView
        nameTitle = view.findViewById(R.id.name_title) as TextView
        dateTitle = view.findViewById(R.id.date_title) as TextView
        durationTitle = view.findViewById(R.id.duration_title) as TextView
        rssiTitle = view.findViewById(R.id.rssi_title) as TextView

        contactHealthStatus = view.findViewById(R.id.contact_health_status) as TextView
        contactName = view.findViewById(R.id.contact_name) as TextView
        contactDate = view.findViewById(R.id.contact_date) as TextView
        contactDuration = view.findViewById(R.id.contact_duration) as TextView
        contactRssi = view.findViewById(R.id.contact_rssi) as TextView

        initializeBottomSheet()
        listenForContactUpdates()

    }

    /**
     * Sets listeners for UI changes and initializes components
     */
    private fun initializeBottomSheet() {
        contactHistoryAdapter = ContactHistoryAdapter(requireActivity())
        contactHistoryListView.adapter = contactHistoryAdapter
        requireActivity().registerForContextMenu(contactHistoryListView)
        contactHistoryAdapter.renderList(contactHistoryListView)

        bottom_sheet = requireActivity().findViewById(R.id.bottom_sheet)
        sheetBehavior = BottomSheetBehavior.from(bottom_sheet);

        //Sheet
        bottom_sheet.setOnClickListener {
            if (sheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED)
            } else {
                sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED)
            }
        }

        sheetBehavior.setHideable(false);
        sheetBehavior.setPeekHeight(300);
        sheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);


        contactHistoryListView.setOnItemClickListener { adapterView: AdapterView<*>, view: View, position: Int, id: Long ->
            view.setSelected(true)
            view.setBackgroundColor(Color.GRAY)
            val contact : Contact = contactHistoryAdapter.getItem(position)
            displayContactFromList(contact)
        }
    }

    /**
     * Observers @see AppViewModel for new changes regarding the list of registered contacts
     */
    private fun listenForContactUpdates() {
        model.contacts.observe(
            requireActivity(),
            androidx.lifecycle.Observer<List<Contact>> { contacts ->
                mContacts = contacts
                contactHistoryAdapter.updateValues(contacts, contactHistoryListView)
                displayLatestContactFromDatabase(contacts)
            }
        )
    }

    /**
     * Displays a contact on the UI
     */
    private fun displayContactFromList(contact: Contact) {
        displayContactData(contact)
    }

    /**
     * Picks the latest contact from the local database to be displayed in the app UI
     */
    private fun displayLatestContactFromDatabase(contacts: List<Contact>) {
        if (isContactListEmpty())
            return

        val latestContact : Contact = getLatestContact(contacts)
        displayContactData(latestContact)
    }

    /**
     * Sets all the UI component data from the supplied contact
     */
    private fun displayContactData(contact : Contact) {
        healthStatusTitle.setVisibility(View.VISIBLE)

        nameTitle.setVisibility(View.VISIBLE)
        dateTitle.setVisibility(View.VISIBLE)
        durationTitle.setVisibility(View.VISIBLE)
        rssiTitle.setVisibility(View.VISIBLE)

        var textColor = Color.GRAY
        val status : HealthStatus = HealthStatus.valueOf(contact.healthStatus)

        when (status) {
            HealthStatus.HEALTHY -> {
                textColor = Color.rgb(79, 165, 85)
            }
            HealthStatus.SICK -> {
                textColor = Color.rgb(235, 64, 52)
            }
        }

        contactHealthStatus.setTextColor(textColor)
        contactHealthStatus.text = DataFormatter.createHealthStatusFormat(status)
        contactName.text =contact.name.toString()
        contactDate.text =DataFormatter.createDateFormat(contact.date)
        contactDuration.text = DataFormatter.createDurationFormat(contact.duration)
        contactRssi.text = contact.rssi.toString()
    }

    /**
     * Gets the latest known contact from a list of contact
     * The latest known contact is the one with the most recent recorded date
     */
    private fun getLatestContact(contacts: List<Contact>) : Contact {
        var latestDate : Long = Long.MIN_VALUE
        var latestContact : Contact = contacts.first()
        contacts.forEach{ contact ->
            if (contact.date > latestDate){
                latestDate = contact.date
                latestContact = contact
            }
        }
        return latestContact
    }

    /**
     * Gets all contacts that have valid latitude and longitude
     */
    private fun getLatestContactWithValidLocation(
        contacts: List<Contact>,
        latestValidLocationContact: (Contact) -> Unit
    ) {
        var latestDate : Long = Long.MIN_VALUE
        var latestContact : Contact? = null
        contacts.forEach{ contact ->
            if (contact.date > latestDate && contact.latitude != 0.0 && contact.longitude != 0.0){
                latestDate = contact.date
                latestContact = contact
            }
        }
        if (latestContact != null)
            latestValidLocationContact.invoke(latestContact!!)
    }

    /**
     * Checks if the latitude and longitude are valid
     */
    private fun isLocationValid(latitude: Double, longitude: Double) : Boolean {
        return latitude != 0.0 && longitude != 0.0
    }

    /**
     * Filters all locations that are valid
     */
    private fun filterValidLocations(contacts: List<Contact>) : List<Contact> {
        return contacts.filter { contact -> contact.latitude != 0.0 && contact.longitude != 0.0 }
    }

    /**
     * @return if contact list is empty or not
     */
    private fun isContactListEmpty() : Boolean {
        return mContacts.size == 0
    }
}