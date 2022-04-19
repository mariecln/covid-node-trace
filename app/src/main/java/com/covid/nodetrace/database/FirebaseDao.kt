package com.covid.nodetrace.database

import android.content.Context

interface FirebaseDao {

    /**
     * POSTs a contact ID to the database
     * @return if the POST request was successfull or not
     */
    suspend fun create(context: Context, contactID : String) : Boolean

    /**
     * POSTs a contact ID to the database
     * @return if the POST request was successfull or not
     */
    suspend fun addBroadcast(Contact : String, ContactName : String, Date : String, Location : String, Duration : String, Rssi : String, Name : String) : Boolean

    /**
     * GETs a list of contact IDs from the database
     * @return if the GET request was successfull or not
     */
    suspend fun read(context: Context) : List<String>

    /**
     * POSTs an update to a contact ID in the database
     * @note: currently not in use because we want to handle most of the contact updates locally on the app
     *
     * @return if the POST request was successfull or not
     */
    suspend fun update(context: Context, contactID: String) : Boolean

    /**
     * DELETEs a contact ID from the database
     * @return if the DELETE request was successfull or not
     */
    suspend fun delete(context: Context, contactID: String) : Boolean
}