package com.covid.nodetrace.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.covid.nodetrace.Contact
import com.covid.nodetrace.HealthStatus
import com.covid.nodetrace.R
import com.covid.nodetrace.util.DataFormatter
import java.net.CookieHandler


class ContactHistoryAdapter(context: Context) : BaseAdapter() {

    private val mInflator: LayoutInflater
    private val mContext = context

    private var mContacts : List<Contact> = listOf()

    init {
        mInflator = LayoutInflater.from(context)
    }

    /**
     * Update the UI with contacts supplied in the list
     */
    fun updateValues(contacts: List<Contact>) {
        mContacts = contacts
        rerenderList()
    }

    /**
     * Gets the Contact item based on it's position in the list
     */
    override fun getItem(position: Int): Contact {
        return mContacts.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun isEnabled(position: Int): Boolean {
        return true
    }

    override fun getCount(): Int {
        return mContacts.size
    }

    fun rerenderList () {
        notifyDataSetChanged()
    }


    /**
     * Called by the Adapter when updates to the list occur
     * Data is converted in the right format before displaying it on the UI
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val view: View?
        val row: ContactRow
        if (convertView == null) {
            view = mInflator.inflate(R.layout.contact_history_row, parent, false)
            row = ContactRow(view)
            view.tag = row
        } else {
            view = convertView
            row = view.tag as ContactRow
        }

        val contact : Contact = mContacts.get(position)
        val status : HealthStatus = HealthStatus.valueOf(contact.healthStatus)

        row.contactHealthStatus.text = DataFormatter.createHealthStatusFormat(status)
        row.contactDate.text = DataFormatter.createShortDateFormat(contact.date)
        row.contactDuration.text = DataFormatter.createDurationFormat(contact.duration)
        row.contactDistance.text = DataFormatter.createDistanceFormat(contact.distance)
        row.contactLocation.text = DataFormatter.createLocationFormat(contact.latitude, contact.longitude)

        return view
    }
}

public enum class TimeRange {
    SEC,
    MIN,
    HOURS
}


/**
 * Specifies every row in the list that shows the contact database entries
 */
private class ContactRow(row: View?) {
    public val contactHealthStatus: TextView
    public val contactDate: TextView
    public val contactDuration: TextView
    public val contactDistance: TextView
    public val contactLocation: TextView

    init {
        contactHealthStatus = row?.findViewById(R.id.row_contact_health_status) as TextView
        contactDate = row?.findViewById(R.id.row_contact_date) as TextView
        contactDuration = row?.findViewById(R.id.row_contact_duration) as TextView
        contactDistance = row?.findViewById(R.id.row_contact_distance) as TextView
        contactLocation = row?.findViewById(R.id.row_contact_location) as TextView
    }
}